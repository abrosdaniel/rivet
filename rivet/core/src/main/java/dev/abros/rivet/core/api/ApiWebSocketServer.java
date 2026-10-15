package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.WorkerShutdown;
import org.java_websocket.*;
import org.java_websocket.drafts.*;
import org.java_websocket.exceptions.*;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Key-authenticated backends and ticket-authenticated browser subscriptions. One bounded worker per socket, no unbounded application queue. */
public final class ApiWebSocketServer implements AutoCloseable {
 private final WebSocketSettings settings;private final RestApi rest;private final RivetApi api;private final ApiKeys keys;private final DomainSubscriptions domains;private final java.util.function.Supplier<TerrainApi> terrain;
 private final BrowserTickets tickets;
 private final Set<Socket> clients=ConcurrentHashMap.newKeySet();private final RestIngress ingress;
 private ServerSocket listener;private ExecutorService acceptor,workers;private ScheduledExecutorService timer;private volatile boolean active;
 public ApiWebSocketServer(WebSocketSettings settings,RestApi rest,RivetApi api,ApiKeys keys){this(settings,rest,api,keys,null);}
 public ApiWebSocketServer(WebSocketSettings settings,RestApi rest,RivetApi api,ApiKeys keys,DomainSubscriptions domains){this(settings,rest,api,keys,domains,null);}
 public ApiWebSocketServer(WebSocketSettings settings,RestApi rest,RivetApi api,ApiKeys keys,DomainSubscriptions domains,java.util.function.Supplier<TerrainApi> terrain){this(settings,rest,api,keys,domains,terrain,null);}
 public ApiWebSocketServer(WebSocketSettings settings,RestApi rest,RivetApi api,ApiKeys keys,DomainSubscriptions domains,java.util.function.Supplier<TerrainApi> terrain,BrowserTickets tickets){this.tickets=tickets;this.terrain=terrain;this.domains=domains;this.settings=settings;this.rest=rest;this.api=api;this.keys=keys;ingress=new RestIngress(settings.requestsPerMinute(),settings.requestsPerMinute(),System::nanoTime);}
 public void start()throws IOException{
  if(listener!=null)throw new IllegalStateException("Already started");
  try{listener=new ServerSocket();listener.bind(new InetSocketAddress(settings.bind(),settings.port()),settings.connections());active=true;
   workers=Executors.newFixedThreadPool(settings.connections(),factory("Rivet WS connection"));acceptor=Executors.newSingleThreadExecutor(factory("Rivet WS accept"));
   var deadlines=new ScheduledThreadPoolExecutor(1,factory("Rivet WS deadline"));deadlines.setRemoveOnCancelPolicy(true);timer=deadlines;
   acceptor.execute(()->{while(active){try{Socket socket=listener.accept();if(clients.size()>=settings.connections()||!ingress.take(socket.getInetAddress().getHostAddress())){close(socket);continue;}clients.add(socket);workers.execute(()->serve(socket));}catch(IOException|RejectedExecutionException stopped){break;}}});
  }catch(IOException|RuntimeException failure){close();throw failure;}
 }
 public int port(){return listener.getLocalPort();}
 private static ThreadFactory factory(String name){return r->{var t=new Thread(r,name);t.setDaemon(true);return t;};}
 /** Guard allocations as well as decoded payload size; copyInstance must retain this guard. */
 static final class BoundedDraft extends Draft_6455 {
  private int fragments,frames;private long window=System.nanoTime();
  BoundedDraft(){super(List.of(),List.of(new org.java_websocket.protocols.Protocol("rivet.v1"),new org.java_websocket.protocols.Protocol("")),32768);}
  @Override public int checkAlloc(int count)throws InvalidDataException{if(count>32782)throw new LimitExceededException(32768);return super.checkAlloc(count);}
  @Override public Draft copyInstance(){return new BoundedDraft();}
  @Override public void processFrame(WebSocketImpl socket,Framedata frame)throws InvalidDataException{
   long now=System.nanoTime();if(now-window>=TimeUnit.MINUTES.toNanos(1)){window=now;frames=0;}
   if(++frames>512)throw new InvalidDataException(1008,"Frame rate exceeded");
   switch(frame.getOpcode()){
    case TEXT,BINARY,CONTINUOUS->{if(++fragments>128)throw new LimitExceededException("Too many fragments",128);if(frame.isFin())fragments=0;}
    default->{}
   }
   super.processFrame(socket,frame);
  }
 }
 private void serve(Socket socket){
  var peer=new Peer(socket);var engine=new WebSocketImpl(peer,List.of(new BoundedDraft()));peer.engine=engine;
  var handshake=timer.schedule(()->close(socket),10,TimeUnit.SECONDS);int headerBytes=0;long checked=0,lastPing=System.nanoTime();
  try{socket.setSoTimeout(250);socket.setTcpNoDelay(true);byte[] input=new byte[8192];
   while(active&&!socket.isClosed()&&!engine.isClosed()){
    int size=0;try{size=socket.getInputStream().read(input);}catch(SocketTimeoutException idle){}
    if(size<0)break;
    if(size>0){if(!engine.isOpen()){headerBytes+=size;if(headerBytes>8192)break;}engine.decode(ByteBuffer.wrap(input,0,size));}
    if(engine.isOpen()){
     handshake.cancel(false);long now=System.nanoTime();
     if(now-checked>=TimeUnit.SECONDS.toNanos(1)){checked=now;peer.authorize();peer.publish();}
     if(now-lastPing>=TimeUnit.SECONDS.toNanos(15)){lastPing=now;engine.sendPing();}
     if(now-peer.pong>TimeUnit.SECONDS.toNanos(45))break;
    }
    // Even synchronous writes have an absolute deadline, so a slow reader cannot keep a worker forever.
    long bytes=0;for(var b:engine.outQueue)bytes+=b.remaining();if(bytes>131072||engine.outQueue.size()>32)break;
    if(!engine.outQueue.isEmpty()){
     var deadline=timer.schedule(()->close(socket),10,TimeUnit.SECONDS);
     try{ByteBuffer b;while((b=engine.outQueue.poll())!=null){byte[] output=new byte[b.remaining()];b.get(output);socket.getOutputStream().write(output);}socket.getOutputStream().flush();}finally{deadline.cancel(false);}
    }
    if(engine.isFlushAndClose()||engine.isClosing())break;
   }
  }catch(Exception rejected){/* Never log credentials, command bodies or library exception messages. */}
  finally{handshake.cancel(false);close(socket);clients.remove(socket);engine.closeConnection(1001,"Connection closed");}
 }
 private final class Peer extends WebSocketAdapter {
  final Socket socket;WebSocketImpl engine;ApiKeys.Grant grant;String secret;BrowserTickets.Admission browser;ApiCursor cursor,terrainCursor;String terrainProfile="surface";long pong=System.nanoTime();
  final Map<String,Watch> watches=new LinkedHashMap<>();long domainChecked,domainRevision;
  final class Watch {final DomainSubscriptions.Target target;JsonObject cursor;Watch(DomainSubscriptions.Target target,JsonObject cursor){this.target=target;this.cursor=cursor;}}
  final RestIngress budget=new RestIngress(120,120,System::nanoTime);
  Peer(Socket socket){this.socket=socket;}
  @Override public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket c,Draft d,ClientHandshake h)throws InvalidDataException{
   try{if(!h.getResourceDescriptor().equals("/api/v1/ws")||h.hasFieldValue("Content-Length")||h.hasFieldValue("Transfer-Encoding"))throw ApiJson.invalid();
    if(h.hasFieldValue("Origin")){
     if(tickets==null||h.hasFieldValue("Authorization"))throw ApiJson.invalid();
     String protocols=h.getFieldValue("Sec-WebSocket-Protocol");
     if(!protocols.matches("rivet\\.v1, *rivet\\.ticket\\.[A-Za-z0-9_-]{43}"))throw ApiJson.invalid();
     String token=protocols.substring(protocols.lastIndexOf('.')+1);
     browser=tickets.consume(token,h.getFieldValue("Origin"));grant=browser.grant();
     // Browsers offer compression; this draft has no compression extension and never negotiates it.
    }else{
     if(h.hasFieldValue("Sec-WebSocket-Extensions")||h.hasFieldValue("Sec-WebSocket-Protocol"))throw ApiJson.invalid();
     String auth=h.getFieldValue("Authorization");if(!auth.startsWith("Bearer ")||auth.length()>128)throw ApiJson.invalid();secret=auth.substring(7);grant=keys.authorize(secret,"websocket.connect");
    }
    return super.onWebsocketHandshakeReceivedAsServer(c,d,h);
   }catch(Exception denied){throw new InvalidDataException(1008,"Access denied");}
  }
  void authorize()throws Exception{keys.revalidate(grant,"websocket.connect");if(browser!=null)tickets.validate(browser);}
  @Override public void onWebsocketMessage(WebSocket c,String message){
   try{
    if(socket.isClosed()||!engine.isOpen())return;
    if(!budget.take("connection")||!ingress.take(socket.getInetAddress().getHostAddress())){c.close(1008,"Rate limited");return;}
    authorize();var in=ApiJson.object(message);String type=ApiJson.string(in,"type");
    if(type.equals("watch")){
     ApiJson.fields(in,"type","id","path","query","session","cursor");
     String id=ApiJson.string(in,"id");if(!id.matches("[A-Za-z0-9_-]{1,64}")||domains==null)throw ApiJson.invalid();
     if(watches.containsKey(id)||watches.size()>=8)throw new ApiException(ApiException.Code.CONFLICT,"Subscription limit or duplicate ID");
     var query=new HashMap<String,String>();for(var entry:in.getAsJsonObject("query").entrySet())query.put(entry.getKey(),ApiJson.string(in.getAsJsonObject("query"),entry.getKey()));
     if(browser!=null&&!in.get("session").isJsonNull())throw new ApiException(ApiException.Code.FORBIDDEN,"Browser session is fixed");
     var target=new DomainSubscriptions.Target(ApiJson.string(in,"path"),query,browser!=null?browser.session():in.get("session").isJsonNull()?null:ApiJson.string(in,"session"));
     var watch=new Watch(target,in.get("cursor").isJsonNull()?null:in.getAsJsonObject("cursor"));
     try{deliver(id,watch,true);watches.put(id,watch);}catch(ApiException denied){watchClosed(id,denied.code());}return;
    }
    if(type.equals("unwatch")){ApiJson.fields(in,"type","id");String id=ApiJson.string(in,"id");watches.remove(id);var out=new JsonObject();out.addProperty("id",id);send("unwatched",out);return;}
    if(type.equals("subscribe")){
     if(in.has("profile"))ApiJson.fields(in,"type","topic","cursor","profile");else ApiJson.fields(in,"type","topic","cursor");
     if(Set.of("map.tiles","map.tiles.isometric").contains(ApiJson.string(in,"topic"))){
      terrainProfile=in.has("profile")?ApiJson.string(in,"profile"):ApiJson.string(in,"topic").equals("map.tiles")?"surface":"isometric";
      terrainAccess();
      keys.revalidate(grant,"map.tiles.read");if(terrain==null)throw new ApiException(ApiException.Code.MODULE_DISABLED,"Terrain disabled");var map=terrain.get().profile(terrainProfile);
      if(in.get("cursor").isJsonNull()){terrainCursor=map.cursor();var out=Json.GSON.toJsonTree(map.metadata()).getAsJsonObject();out.addProperty("profile",terrainProfile);out.add("cursor",Json.GSON.toJsonTree(terrainCursor));out.add("projection",Json.GSON.toJsonTree(map.projection()));map.slice().ifPresent(slice->out.add("slice",Json.GSON.toJsonTree(slice)));send("terrain.snapshot",out);}
      else{var from=in.getAsJsonObject("cursor");ApiJson.fields(from,"stream","sequence");terrainCursor=new ApiCursor(UUID.fromString(ApiJson.string(from,"stream")),from.get("sequence").getAsBigDecimal().longValueExact());publishTerrain();}return;
     }
     if(in.has("profile")||!ApiJson.string(in,"topic").equals("server.status"))throw ApiJson.invalid();keys.revalidate(grant,"server.status.subscribe");
     if(in.get("cursor").isJsonNull()){var snapshot=api.serverStatus();cursor=snapshot.cursor();send("snapshot",RestApi.snapshot(snapshot));}
     else {var from=in.getAsJsonObject("cursor");ApiJson.fields(from,"stream","sequence");cursor=new ApiCursor(UUID.fromString(ApiJson.string(from,"stream")),from.get("sequence").getAsBigDecimal().longValueExact());publish();}return;
    }
    if(type.equals("unsubscribe")){ApiJson.fields(in,"type");cursor=null;terrainCursor=null;send("unsubscribed",new JsonObject());return;}
    if(browser!=null)throw new ApiException(ApiException.Code.FORBIDDEN,"Browser connections support subscriptions only");
    ApiJson.fields(in,"type","id","method","path","session","ifMatch","body");if(!type.equals("request"))throw ApiJson.invalid();
    String id=ApiJson.string(in,"id");if(!id.matches("[A-Za-z0-9_-]{1,64}"))throw ApiJson.invalid();
    var headers=new HashMap<String,List<String>>();headers.put("authorization",List.of("Bearer "+secret));
    if(!in.get("session").isJsonNull())headers.put("x-rivet-session",List.of(ApiJson.string(in,"session")));
    if(!in.get("ifMatch").isJsonNull())headers.put("if-match",List.of(ApiJson.string(in,"ifMatch")));
    String path=ApiJson.string(in,"path");if(path.startsWith("/api/v1/sessions/")||path.startsWith("/api/v1/websocket/")||path.contains("/tile.png"))throw ApiJson.invalid();
    var result=rest.handle(new RestApi.Request(ApiJson.string(in,"method"),path,headers,in.get("body").isJsonNull()?"":in.get("body").toString()));
    var reply=new JsonObject();reply.addProperty("id",id);reply.addProperty("status",result.status());reply.add("body",JsonParser.parseString(result.body()));send("response",reply);
   }catch(ApiException rejected){var out=new JsonObject();out.addProperty("code",rejected.code().name());send("error",out);if(rejected.code()==ApiException.Code.RESYNC_REQUIRED)cursor=null;else c.close(1008,"Request rejected");}
   catch(Exception rejected){c.close(1008,"Invalid request");}
  }
  void deliver(String id,Watch watch,boolean initial)throws Exception{
   var value=domains.snapshot(grant,watch.target,watch.cursor);var next=value.getAsJsonObject("cursor");
   if(initial||!next.equals(watch.cursor)){var out=new JsonObject();out.addProperty("id",id);out.addProperty("path",watch.target.path());out.add("cursor",next);if(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>48000){send("resource.invalidated",out);}else{out.add("resource",value.get("resource"));send("resource.snapshot",out);}watch.cursor=next;}
  }
  void watchClosed(String id,ApiException.Code code){var out=new JsonObject();out.addProperty("id",id);out.addProperty("code",code.name());send("watch.closed",out);}
  void terrainAccess()throws Exception{if(!Set.of("surface","isometric").contains(terrainProfile))keys.revalidate(grant,TerrainProfiles.scope(terrainProfile));}
  void publishTerrain()throws Exception{
   if(terrainCursor==null)return;keys.revalidate(grant,"map.tiles.read");terrainAccess();
   try{var changes=terrain.get().profile(terrainProfile).changes(terrainCursor);if(!changes.items().isEmpty()){
    // Bound a frame even with long dimension IDs; resume the remaining replay next tick.
    var items=changes.items().subList(0,Math.min(64,changes.items().size()));var next=items.getLast().cursor();send("terrain.changed",Json.GSON.toJsonTree(new TerrainApi.Changes(items,next)).getAsJsonObject());terrainCursor=next;
   }else terrainCursor=changes.nextCursor();}
   catch(ApiException e){terrainCursor=null;var out=new JsonObject();out.addProperty("code",e.code().name());send("terrain.closed",out);}
  }
  void publish()throws Exception{
   publishTerrain();
   long now=System.nanoTime();if(domains!=null&&(domains.revision()!=domainRevision||now-domainChecked>=TimeUnit.SECONDS.toNanos(5))){domainChecked=now;domainRevision=domains.revision();var iterator=watches.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();try{deliver(entry.getKey(),entry.getValue(),false);}catch(ApiException denied){watchClosed(entry.getKey(),denied.code());iterator.remove();}}}
   if(cursor==null)return;keys.revalidate(grant,"server.status.subscribe");try{var events=api.serverStatusEvents(cursor,16);for(var event:events.changes()){send("event",RestApi.snapshot(event));cursor=event.cursor();}}catch(ApiException e){if(e.code()!=ApiException.Code.RESYNC_REQUIRED)throw e;cursor=null;var out=new JsonObject();out.addProperty("code","RESYNC_REQUIRED");send("error",out);}}
  void send(String type,JsonObject body){var envelope=new JsonObject();envelope.addProperty("type",type);envelope.add("data",body);String text=envelope.toString();if(text.length()>65536||engine.outQueue.size()>=16){close(socket);return;}engine.send(text);}
  @Override public void onWebsocketMessage(WebSocket c,ByteBuffer b){c.close(1003,"Text required");}
  @Override public void onWebsocketPong(WebSocket c,Framedata f){pong=System.nanoTime();}
  @Override public void onWebsocketOpen(WebSocket c,Handshakedata d){}
  @Override public void onWebsocketClose(WebSocket c,int code,String reason,boolean remote){}
  @Override public void onWebsocketClosing(WebSocket c,int code,String reason,boolean remote){}
  @Override public void onWebsocketCloseInitiated(WebSocket c,int code,String reason){}
  @Override public void onWebsocketError(WebSocket c,Exception e){close(socket);}
  @Override public void onWriteDemand(WebSocket c){if(engine!=null&&engine.outQueue.size()>32)close(socket);}
  @Override public InetSocketAddress getLocalSocketAddress(WebSocket c){return (InetSocketAddress)socket.getLocalSocketAddress();}
  @Override public InetSocketAddress getRemoteSocketAddress(WebSocket c){return (InetSocketAddress)socket.getRemoteSocketAddress();}
 }
 private static void close(Socket socket){try{socket.close();}catch(IOException ignored){}}
 @Override public void close(){active=false;if(listener!=null)try{listener.close();}catch(IOException ignored){}for(var socket:clients)close(socket);WorkerShutdown.stop(Duration.ofSeconds(1),Duration.ofSeconds(3),acceptor,workers,timer);if(domains!=null)domains.clear();}
}
