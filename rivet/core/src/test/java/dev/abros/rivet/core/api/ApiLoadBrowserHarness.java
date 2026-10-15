package dev.abros.rivet.core.api;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.AuthStore;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in isolated PostgreSQL/real socket workload. No Minecraft clients or production data. */
public final class ApiLoadBrowserHarness {
 static final String ORIGIN="http://127.0.0.1:24480";
 static final int HTTP=24481,WS=24482;
 final Path output;final PgDatabase db;final CommunityStore store;final ServerSettings cfg;
 final ApiKeys keys;final ApiKeys.Grant grant;final String secret;
 final PlayerSessions sessions;final BrowserTickets tickets;final StatusFeed feed;
 final RestHttpServer http;final ApiWebSocketServer ws;final JavaDomains javaDomains;
 final List<String> players=new ArrayList<>(),tokens=new ArrayList<>();
 final JsonObject result=new JsonObject();final CompletableFuture<JsonObject> browser=new CompletableFuture<>();
 final HttpClient client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
 ApiLoadBrowserHarness(Path output)throws Exception{this(output,ORIGIN);}
 ApiLoadBrowserHarness(Path output,String origin)throws Exception{
  this.output=output;Files.createDirectories(output);db=TestDatabase.database(output);cfg=ServerSettings.parse(ServerSettings.template());store=new CommunityStore(db,CommunityStore.defaults());
  keys=new ApiKeys(db,new ApiKeys.Limits(100,8,100000,30,30,1000),Clock.systemUTC());
  var issued=keys.create(output,"load_browser",Set.of("sessions.link","tasks.read","tasks.write","notifications.read","chat.read","events.read","websocket.connect","websocket.ticket","server.status.read"),1,100000);
  secret=Files.readString(issued.deliveryFile()).trim();Files.delete(issued.deliveryFile());grant=keys.authorize(secret,"tasks.read");
  sessions=new PlayerSessions(keys,new AuthStore(db),false,Clock.systemUTC(),1,1000);
  // Loopback HTTP is a test-only injection, not a change to production HTTPS origin validation.
  tickets=new BrowserTickets(keys,sessions,Clock.systemUTC(),new BrowserTickets.Limits(5,128,128),Set.of(origin));
  var resources=new CommunityApiResources(db,store,cfg);var domains=new DomainRest(keys,sessions,store,resources).commands(new CommunityApiCommands(db,store,cfg));
  feed=new StatusFeed(256,Clock.systemUTC(),new ServerStatus(100,100,false,0,Set.of("server.status")));
  var limits=new RestSettings(true,"127.0.0.1",HTTP,128,128,8,128,15,100000,100000,origin.startsWith("https:")?Set.of(origin):Set.of(),Set.of());
  var rest=new RestApi(feed,RestApi.authorization(keys),limits,new BrowserTicketRest(keys,tickets,domains));
  http=new RestHttpServer(limits,rest);ws=new ApiWebSocketServer(new WebSocketSettings(true,"127.0.0.1",WS,16,6000),rest,feed,keys,new DomainSubscriptions(domains,db::communityRevision),null,tickets);
  javaDomains=new JavaDomains(db,store,cfg);http.start();ws.start();
  for(int i=0;i<100;i++){
   String id=UUID.randomUUID().toString();players.add(id);var actor=new CommunityStore.Actor(id,"Load"+i,false,false);store.seen(actor);
   var q=new JsonObject();q.addProperty("section","home");q.addProperty("op","workSave");q.addProperty("title","Private-"+i);q.addProperty("description","Owned by "+id);store.request(actor,q);
   store.externalNotice(id,"Notice-"+i);tokens.add(link(id,"Load"+i));
  }
 }
 String link(String id,String name)throws Exception{var c=sessions.challenge(grant);sessions.confirm(c.code(),grant.application(),id,name);return sessions.exchange(grant,c.code(),c.pollToken()).token();}
 HttpResponse<String> get(String path,int player)throws Exception{return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+HTTP+"/api/v1/"+path)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+secret).header("X-Rivet-Session",tokens.get(player)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 static void check(boolean condition,String description){if(!condition)throw new AssertionError(description);}
 static void owned(String body,int player){var items=JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("items");check(items.size()==1,"Unexpected private task count");check(items.get(0).getAsJsonObject().get("title").getAsString().equals("Private-"+player),"Private data crossed player boundary");}
 static JsonObject timing(Collection<Long> samples,long elapsed){long[] values=samples.stream().mapToLong(Long::longValue).sorted().toArray();var j=new JsonObject();j.addProperty("count",values.length);j.addProperty("p50Ms",values[values.length/2]/1e6);j.addProperty("p95Ms",values[Math.min(values.length-1,values.length*95/100)]/1e6);j.addProperty("maxMs",values[values.length-1]/1e6);j.addProperty("elapsedSeconds",elapsed/1e9);return j;}
 static final class Listener implements WebSocket.Listener{
  final BlockingQueue<JsonObject> messages=new LinkedBlockingQueue<>();final CompletableFuture<Integer> closed=new CompletableFuture<>();final StringBuilder buffer=new StringBuilder();
  public void onOpen(WebSocket socket){socket.request(1);}public CompletionStage<?> onText(WebSocket socket,CharSequence data,boolean last){buffer.append(data);if(last){messages.add(JsonParser.parseString(buffer.toString()).getAsJsonObject());buffer.setLength(0);}socket.request(1);return null;}
  public CompletionStage<?> onClose(WebSocket socket,int code,String reason){closed.complete(code);return null;}public void onError(WebSocket socket,Throwable failure){closed.completeExceptionally(failure);}
 }
 void load()throws Exception{
  long begin=System.nanoTime();var samples=new ConcurrentLinkedQueue<Long>();var gate=new CountDownLatch(1);
  try(var workers=Executors.newFixedThreadPool(100)){
   var futures=new ArrayList<Future<?>>();for(int p=0;p<100;p++){final int index=p;futures.add(workers.submit(()->{gate.await();for(int n=0;n<20;n++){long t=System.nanoTime();var reply=get(n%2==0?"tasks":"me/notifications",index);check(reply.statusCode()==200,"REST load status "+reply.statusCode());if(n%2==0)owned(reply.body(),index);samples.add(System.nanoTime()-t);}return null;}));}
   gate.countDown();for(var f:futures)f.get(120,TimeUnit.SECONDS);
  }
  result.add("rest100",timing(samples,System.nanoTime()-begin));System.out.println("RIVET_API_LOAD_REST "+result.get("rest100"));
  samples.clear();begin=System.nanoTime();
  for(int round=0;round<10;round++){
   var pending=new ArrayList<CompletableFuture<?>>();for(int p=0;p<100;p++){int index=p;long t=System.nanoTime();pending.add(javaDomains.connect("load_test",UUID.fromString(players.get(p))).tasks(Optional.empty(),"").handle((page,error)->{if(error!=null){check((error instanceof ApiException a&&a.code()==ApiException.Code.BUSY)||(error.getCause() instanceof ApiException nested&&nested.code()==ApiException.Code.BUSY),"Unexpected Java error "+error.getClass().getSimpleName());}else{check(page.items().size()==1&&page.items().getFirst().title().equals("Private-"+index),"Java isolation failed");samples.add(System.nanoTime()-t);}return null;}));}
   CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).get(30,TimeUnit.SECONDS);
  }
  result.add("javaAccepted",timing(samples,System.nanoTime()-begin));result.addProperty("javaBusy",1000-samples.size());
  var sockets=new ArrayList<WebSocket>();var listeners=new ArrayList<Listener>();
  try{
   for(int i=0;i<13;i++){var listener=new Listener();listeners.add(listener);sockets.add(client.newWebSocketBuilder().header("Authorization","Bearer "+secret).buildAsync(URI.create("ws://127.0.0.1:"+WS+"/api/v1/ws"),listener).get(5,TimeUnit.SECONDS));}
   begin=System.nanoTime();for(int p=0;p<100;p++){var watch=new JsonObject();watch.addProperty("type","watch");watch.addProperty("id","p"+p);watch.addProperty("path","tasks");watch.add("query",new JsonObject());watch.addProperty("session",tokens.get(p));watch.add("cursor",JsonNull.INSTANCE);sockets.get(p/8).sendText(watch.toString(),true).join();}
   int count=0;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(count<100&&System.nanoTime()<deadline){for(var listener:listeners){var msg=listener.messages.poll(100,TimeUnit.MILLISECONDS);if(msg==null)continue;check(msg.get("type").getAsString().equals("resource.snapshot"),"Subscription failed: "+msg.get("type"));var data=msg.getAsJsonObject("data");owned(data.get("resource").toString(),Integer.parseInt(data.get("id").getAsString().substring(1)));count++;}}
   check(count==100,"Missing subscriptions");result.addProperty("wsInitialMs",(System.nanoTime()-begin)/1e6);
   // Sustained 100 requests/second alongside 100 independently authorized subscriptions.
   var sustained=new ConcurrentLinkedQueue<Long>();long steadyStart=System.nanoTime(),peakHeap=0;
   var cpu=(com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean();long cpuStart=cpu.getProcessCpuTime();
   try(var requestWorkers=Executors.newFixedThreadPool(16)){
    for(int second=0;second<60;second++){
     var requests=new ArrayList<Future<?>>();for(int p=0;p<100;p++){int index=p;long queued=System.nanoTime();requests.add(requestWorkers.submit(()->{var response=get("tasks",index);check(response.statusCode()==200,"Sustained HTTP failure");owned(response.body(),index);sustained.add(System.nanoTime()-queued);return null;}));}
     for(var request:requests)request.get(15,TimeUnit.SECONDS);
     for(var listener:listeners)check(!listener.closed.isDone(),"Unexpected connection loss under load");
     peakHeap=Math.max(peakHeap,java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
     long remaining=steadyStart+TimeUnit.SECONDS.toNanos(second+1)-System.nanoTime();if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining);
    }
   }
   long steadyElapsed=System.nanoTime()-steadyStart;var steady=timing(sustained,steadyElapsed);steady.addProperty("processCpuSeconds",(cpu.getProcessCpuTime()-cpuStart)/1e9);steady.addProperty("sampledPeakHeapMiB",peakHeap/1048576.0);result.add("sustained100",steady);
   System.out.println("RIVET_API_SUSTAINED "+steady);
   begin=System.nanoTime();for(String player:players)sessions.revokePlayer(player);count=0;deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
   while(count<100&&System.nanoTime()<deadline){for(var listener:listeners){var msg=listener.messages.poll(100,TimeUnit.MILLISECONDS);if(msg==null)continue;check(msg.get("type").getAsString().equals("watch.closed"),"Stale payload after revoke");count++;}}
   check(count==100,"Revoked subscriptions stayed open");result.addProperty("wsRevokeMs",(System.nanoTime()-begin)/1e6);result.addProperty("wsUsers",100);result.addProperty("wsConnections",13);
  }finally{for(var socket:sockets)socket.abort();}
  for(int p=0;p<100;p++)tokens.set(p,link(players.get(p),"Load"+p));
  check(get("tasks",0).statusCode()==200,"Listener did not recover");
  var memory=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();result.addProperty("heapUsedMiB",memory.getUsed()/1048576.0);result.addProperty("heapMaxMiB",memory.getMax()/1048576.0);
  Files.writeString(output.resolve("load.json"),result.toString());System.out.println("RIVET_API_LOAD_OK "+result);
 }
 void serveBrowser(Path html)throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",24480),16);var workers=Executors.newFixedThreadPool(4);server.setExecutor(workers);
  server.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();String response;int status=200;
   if(path.equals("/")){check(exchange.getRequestMethod().equals("GET"),"GET required");response=Files.readString(html);exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");}
   else{if(!exchange.getRequestMethod().equals("POST")||!ORIGIN.equals(exchange.getRequestHeaders().getFirst("Origin")))throw new IllegalArgumentException("Local browser only");
    switch(path){
     case "/ticket"->{String body="{\"origin\":\""+ORIGIN+"\"}";var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+HTTP+"/api/v1/websocket/tickets")).header("Authorization","Bearer "+secret).header("X-Rivet-Session",tokens.get(0)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();var reply=client.send(req,HttpResponse.BodyHandlers.ofString());status=reply.statusCode();response=reply.body();}
     case "/revoke-key"->{keys.state(grant.keyId(),"revoke");response="{}";}
     case "/revoke"->{sessions.revokePlayer(players.get(0));response="{}";}
     case "/relink"->{tokens.set(0,link(players.get(0),"Load0"));response="{}";}
     case "/report"->{var report=JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(8192),StandardCharsets.UTF_8)).getAsJsonObject();browser.complete(report);response="{}";}
     default->{status=404;response="{}";}
    }
    exchange.getResponseHeaders().set("Content-Type","application/json");
   }
   exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
  }catch(Exception failure){exchange.sendResponseHeaders(500,-1);}finally{exchange.close();}});
  server.start();System.out.println("RIVET_BROWSER_READY "+ORIGIN);
  try{var report=browser.get(15,TimeUnit.MINUTES);Files.writeString(output.resolve("browser.json"),report.toString());check(report.get("ok").getAsBoolean(),"Browser reported failure");System.out.println("RIVET_BROWSER_OK "+report);Thread.sleep(3000);}finally{server.stop(0);workers.shutdownNow();}
 }
 public static void main(String[] args)throws Exception{
  var harness=new ApiLoadBrowserHarness(Path.of(args[0]));try{harness.load();if(args.length<3||!args[2].equals("load-only"))harness.serveBrowser(Path.of(args[1]));}finally{harness.ws.close();harness.http.close();harness.javaDomains.close();harness.tickets.close();harness.sessions.close();harness.keys.close();harness.feed.close();harness.client.close();}
 }
}
