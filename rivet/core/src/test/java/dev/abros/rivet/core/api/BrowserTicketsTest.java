package dev.abros.rivet.core.api;
import com.google.gson.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.AuthStore;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class BrowserTicketsTest {
 @TempDir Path root;ApiKeys keys;PlayerSessions sessions;BrowserTickets tickets;ApiKeys.Grant grant;String secret,session;RestApi rest;DomainRest domain;
 static final String ORIGIN="https://site.example";
 static final class Time extends Clock {long now=System.currentTimeMillis();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(now);}public long millis(){return now;}}
 Time clock=new Time();
 @BeforeEach void setup()throws Exception{
  var db=TestDatabase.database(root);keys=new ApiKeys(db,new ApiKeys.Limits(100,8,10000,30,30,1000),Clock.systemUTC());
  var key=keys.create(root,"website",Set.of("websocket.connect","websocket.ticket","sessions.link","events.read","server.status.subscribe"),1,10000);secret=Files.readString(key.deliveryFile()).trim();grant=keys.authorize(secret,"websocket.ticket");
  sessions=new PlayerSessions(keys,new AuthStore(db),false,Clock.systemUTC(),1,100);var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",UUID.randomUUID().toString(),"Player");session=sessions.exchange(grant,c.code(),c.pollToken()).token();
  tickets=new BrowserTickets(keys,sessions,clock,new BrowserTickets.Limits(30,8,2),Set.of(ORIGIN));
  domain=new DomainRest(keys,sessions,new CommunityStore(db,CommunityStore.defaults()));rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),new BrowserTicketRest(keys,tickets,domain));
 }
 @AfterEach void stop(){tickets.close();sessions.close();keys.close();}
 @Test void singleUseOriginExpiryAndNoCredentialsInRepresentations()throws Exception{
  var issued=tickets.issue(grant,session,ORIGIN);assertFalse(issued.toString().contains(issued.ticket()));var admission=tickets.consume(issued.ticket(),ORIGIN);assertFalse(admission.toString().contains(session));assertThrows(ApiException.class,()->tickets.consume(issued.ticket(),ORIGIN));
  var wrong=tickets.issue(grant,session,ORIGIN);assertThrows(ApiException.class,()->tickets.consume(wrong.ticket(),"https://evil.example"));assertThrows(ApiException.class,()->tickets.consume(wrong.ticket(),ORIGIN));
  var expired=tickets.issue(grant,session,ORIGIN);clock.now+=30000;assertThrows(ApiException.class,()->tickets.consume(expired.ticket(),ORIGIN));assertThrows(ApiException.class,()->tickets.issue(grant,session,"null"));
 }
 @Test void concurrentRedemptionHasExactlyOneWinner()throws Exception{
  var issued=tickets.issue(grant,session,ORIGIN);try(var executor=Executors.newFixedThreadPool(8)){var start=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();for(int i=0;i<8;i++)jobs.add(executor.submit(()->{start.await();try{tickets.consume(issued.ticket(),ORIGIN);return true;}catch(ApiException denied){return false;}}));start.countDown();int winners=0;for(var job:jobs)if(job.get(5,TimeUnit.SECONDS))winners++;assertEquals(1,winners);}
 }
 @Test void capacityReleasesAfterExpiryAndStopInvalidatesHandles()throws Exception{
  tickets.issue(grant,session,ORIGIN);tickets.issue(grant,session,ORIGIN);assertThrows(ApiException.class,()->tickets.issue(grant,session,ORIGIN));clock.now+=30000;var next=tickets.issue(grant,session,ORIGIN);var admission=tickets.consume(next.ticket(),ORIGIN);tickets.close();assertThrows(ApiException.class,()->tickets.validate(admission));
 }
 @Test void keyAndSessionRevocationApplyBeforeAndAfterConsumption()throws Exception{
  var pending=tickets.issue(grant,session,ORIGIN);var active=tickets.consume(tickets.issue(grant,session,ORIGIN).ticket(),ORIGIN);sessions.revoke(grant,session);assertThrows(ApiException.class,()->tickets.consume(pending.ticket(),ORIGIN));assertThrows(ApiException.class,()->tickets.validate(active));
 }
 @Test void disabledKeyCannotRedeemOrContinue()throws Exception{
  var pending=tickets.issue(grant,session,ORIGIN);var active=tickets.consume(tickets.issue(grant,session,ORIGIN).ticket(),ORIGIN);keys.state(grant.keyId(),"disable");assertThrows(ApiException.class,()->tickets.consume(pending.ticket(),ORIGIN));assertThrows(ApiException.class,()->tickets.validate(active));
 }
 @Test void issuanceIsBackendOnlyWithBoundPlayerSessionAndScope()throws Exception{
  var headers=new HashMap<String,List<String>>();headers.put("Authorization",List.of("Bearer "+secret));headers.put("X-Rivet-Session",List.of(session));String body="{\"origin\":\""+ORIGIN+"\"}";
  var response=rest.handle(new RestApi.Request("POST","/api/v1/websocket/tickets",headers,body));assertEquals(200,response.status(),response.body());assertEquals("no-store",response.headers().get("Cache-Control"));assertFalse(response.body().contains(session));assertFalse(response.body().contains(secret));
  headers.put("Origin",List.of(ORIGIN));assertEquals(403,rest.handle(new RestApi.Request("POST","/api/v1/websocket/tickets",headers,body)).status());headers.remove("Origin");headers.remove("X-Rivet-Session");assertEquals(401,rest.handle(new RestApi.Request("POST","/api/v1/websocket/tickets",headers,body)).status());
  var limited=keys.create(root,"limited",Set.of("websocket.connect"),1,100);headers.put("Authorization",List.of("Bearer "+Files.readString(limited.deliveryFile()).trim()));assertEquals(403,rest.handle(new RestApi.Request("POST","/api/v1/websocket/tickets",headers,body)).status());
 }
 static final class Listener implements WebSocket.Listener {
  final BlockingQueue<String> messages=new LinkedBlockingQueue<>();final CompletableFuture<Void> closed=new CompletableFuture<>();final StringBuilder text=new StringBuilder();
  public void onOpen(WebSocket ws){ws.request(1);}public CompletionStage<?> onText(WebSocket ws,CharSequence part,boolean last){text.append(part);if(last){messages.add(text.toString());text.setLength(0);}ws.request(1);return null;}
  public CompletionStage<?> onClose(WebSocket ws,int code,String reason){closed.complete(null);return null;}public void onError(WebSocket ws,Throwable error){closed.complete(null);}
 }
 ApiWebSocketServer server()throws Exception{int port;try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}var server=new ApiWebSocketServer(new WebSocketSettings(true,"127.0.0.1",port,4,1000),rest,RestApiTest.feed(),keys,new DomainSubscriptions(domain),null,tickets);server.start();return server;}
 WebSocket connect(HttpClient client,ApiWebSocketServer server,String ticket,Listener listener)throws Exception{return client.newWebSocketBuilder().header("Origin",ORIGIN).subprotocols("rivet.v1","rivet.ticket."+ticket).buildAsync(URI.create("ws://127.0.0.1:"+server.port()+"/api/v1/ws"),listener).get(5,TimeUnit.SECONDS);}
 @Test void browserHandshakeSubscribesWithoutKeyAndClosesOnSessionRevoke()throws Exception{
  try(var server=server();var client=HttpClient.newHttpClient()){
   var issued=tickets.issue(grant,session,ORIGIN);var listener=new Listener();var ws=connect(client,server,issued.ticket(),listener);assertEquals("rivet.v1",ws.getSubprotocol());
   ws.sendText("{\"type\":\"watch\",\"id\":\"events\",\"path\":\"events\",\"query\":{},\"session\":null,\"cursor\":null}",true).join();var message=listener.messages.poll(5,TimeUnit.SECONDS);assertNotNull(message);assertEquals("resource.snapshot",JsonParser.parseString(message).getAsJsonObject().get("type").getAsString());assertFalse(message.contains(session));assertFalse(message.contains(secret));
   assertThrows(Exception.class,()->connect(client,server,issued.ticket(),new Listener()));sessions.revoke(grant,session);listener.closed.get(5,TimeUnit.SECONDS);ws.abort();
  }
 }
 @Test void browserCompressionOfferIsDeclinedAndTicketIsNotEchoed()throws Exception{
  try(var server=server();var socket=new Socket("127.0.0.1",server.port())){socket.setSoTimeout(3000);String ticket=tickets.issue(grant,session,ORIGIN).ticket();
   String request="GET /api/v1/ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nOrigin: "+ORIGIN+"\r\nSec-WebSocket-Protocol: rivet.v1, rivet.ticket."+ticket+"\r\nSec-WebSocket-Extensions: permessage-deflate; client_max_window_bits\r\n\r\n";
   socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));var response=new StringBuilder();String line;while((line=reader.readLine())!=null&&!line.isEmpty())response.append(line).append('\n');String headers=response.toString();assertTrue(headers.startsWith("HTTP/1.1 101"),headers);assertTrue(headers.contains("rivet.v1"));assertFalse(headers.contains(ticket));assertFalse(headers.toLowerCase().contains("sec-websocket-extensions"));
  }
 }
 @Test void browserCannotSwapSessionOrTunnelMutations()throws Exception{
  try(var server=server();var client=HttpClient.newHttpClient()){
   for(String command:List.of("{\"type\":\"watch\",\"id\":\"events\",\"path\":\"events\",\"query\":{},\"session\":\""+session+"\",\"cursor\":null}","{\"type\":\"request\"}")){
    var listener=new Listener();var ws=connect(client,server,tickets.issue(grant,session,ORIGIN).ticket(),listener);ws.sendText(command,true).join();String message=listener.messages.poll(5,TimeUnit.SECONDS);assertNotNull(message);assertTrue(message.contains("FORBIDDEN"));listener.closed.get(5,TimeUnit.SECONDS);ws.abort();
   }
  }
 }
}
