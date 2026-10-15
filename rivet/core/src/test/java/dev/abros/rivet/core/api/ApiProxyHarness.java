package dev.abros.rivet.core.api;

import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.concurrent.*;
import javax.net.ssl.*;
import static dev.abros.rivet.core.api.ApiLoadBrowserHarness.*;

/** Real TLS termination and WSS upgrade against an external disposable Nginx. */
public final class ApiProxyHarness {
 static final String ORIGIN="https://localhost:24483";
 static HttpRequest.Builder request(String path){return HttpRequest.newBuilder(URI.create(ORIGIN+"/api/v1/"+path)).timeout(Duration.ofSeconds(10));}
 static void rejectCertificate(HttpClient client,String url)throws Exception{
  try{client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.discarding());throw new AssertionError("Invalid certificate accepted");}
  catch(SSLHandshakeException expected){/* Verification must fail, not a connection error. */}
 }
 static String ticket(HttpClient client,ApiLoadBrowserHarness h)throws Exception{
  var response=client.send(request("websocket/tickets").header("Authorization","Bearer "+h.secret).header("X-Rivet-Session",h.tokens.getFirst()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"origin\":\""+ORIGIN+"\"}")).build(),HttpResponse.BodyHandlers.ofString());
  check(response.statusCode()==201||response.statusCode()==200,"Ticket issuance: "+response.statusCode());
  return JsonParser.parseString(response.body()).getAsJsonObject().get("ticket").getAsString();
 }
 static WebSocket connect(HttpClient client,String ticket,String origin,Listener listener)throws Exception{
  return client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("Origin",origin).subprotocols("rivet.v1","rivet.ticket."+ticket).buildAsync(URI.create("wss://localhost:24483/api/v1/ws"),listener).get(8,TimeUnit.SECONDS);
 }
 static void denied(HttpClient client,String ticket,String origin)throws Exception{
  try{var socket=connect(client,ticket,origin,new Listener());socket.abort();throw new AssertionError("Invalid ticket/origin accepted");}
  catch(ExecutionException expected){check(expected.getCause() instanceof WebSocketHandshakeException,"Expected HTTP rejection, not transport failure");
   int status=((WebSocketHandshakeException)expected.getCause()).getResponse().statusCode();// Java-WebSocket maps rejected handshakes to 404 (internal failures to 500).
   check(status==404,"Unexpected rejection status "+status);}
 }
 public static void main(String[] args)throws Exception{
  var trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);
  try(var input=Files.newInputStream(Path.of(args[1]))){trust.setCertificateEntry("fixture",CertificateFactory.getInstance("X.509").generateCertificate(input));}
  var managers=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());managers.init(trust);
  var tls=SSLContext.getInstance("TLS");tls.init(null,managers.getTrustManagers(),null);
  var h=new ApiLoadBrowserHarness(Path.of(args[0]),ORIGIN);
  try(var client=HttpClient.newBuilder().sslContext(tls).version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();var untrusted=HttpClient.newHttpClient()){
   rejectCertificate(untrusted,ORIGIN+"/api/v1/tasks");
   rejectCertificate(client,"https://127.0.0.1:24483/api/v1/tasks");
   for(String protocol:new String[]{"TLSv1.2","TLSv1.3"}){
    var parameters=new SSLParameters();parameters.setProtocols(new String[]{protocol});
    try(var versioned=HttpClient.newBuilder().sslContext(tls).sslParameters(parameters).build()){
     var versionResponse=versioned.send(request("tasks").GET().build(),HttpResponse.BodyHandlers.discarding());
     check(versionResponse.sslSession().orElseThrow().getProtocol().equals(protocol),"TLS protocol mismatch");
     check(versionResponse.statusCode()==401,"TLS request reached wrong handler");
    }
   }
   var redirect=client.send(HttpRequest.newBuilder(URI.create("http://localhost:24484/api/v1/tasks")).GET().build(),HttpResponse.BodyHandlers.discarding());
   check(redirect.statusCode()==308&&redirect.headers().firstValue("location").orElse("").equals(ORIGIN+"/api/v1/tasks"),"HTTPS redirect");
   var response=client.send(request("tasks").header("Authorization","Bearer "+h.secret).header("X-Rivet-Session",h.tokens.getFirst()).GET().build(),HttpResponse.BodyHandlers.ofString());
   check(response.statusCode()==200,"HTTPS tasks: "+response.statusCode());owned(response.body(),0);
   check(response.headers().firstValue("cache-control").orElse("").contains("no-store"),"Private response cache policy lost");
   var cors=client.send(request("tasks").header("Origin","https://evil.invalid").GET().build(),HttpResponse.BodyHandlers.discarding());check(cors.statusCode()==403,"Foreign Origin allowed");
   var browserKey=client.send(request("tasks").header("Origin",ORIGIN).header("Authorization","Bearer "+h.secret).GET().build(),HttpResponse.BodyHandlers.discarding());check(browserKey.statusCode()==403,"Browser bearer permitted");
   String expired=ticket(client,h);Thread.sleep(5300);denied(client,expired,ORIGIN);
   String ticket=ticket(client,h);var listener=new Listener();var socket=connect(client,ticket,ORIGIN,listener);
   try{
    check(socket.getSubprotocol().equals("rivet.v1"),"Ticket leaked as selected protocol");
    socket.sendText("{\"type\":\"watch\",\"id\":\"mine\",\"path\":\"tasks\",\"query\":{},\"session\":null,\"cursor\":null}",true).join();
    var snapshot=listener.messages.poll(10,TimeUnit.SECONDS);check(snapshot!=null&&snapshot.get("type").getAsString().equals("resource.snapshot"),"Missing WSS snapshot");owned(snapshot.getAsJsonObject("data").get("resource").toString(),0);
    denied(client,ticket,ORIGIN);denied(client,ticket(client,h),"https://evil.invalid");
    h.sessions.revokePlayer(h.players.getFirst());listener.closed.get(8,TimeUnit.SECONDS);
   }finally{socket.abort();}
   h.tokens.set(0,h.link(h.players.getFirst(),"Load0"));
   var keyListener=new Listener();var keySocket=connect(client,ticket(client,h),ORIGIN,keyListener);
   try{h.keys.state(h.grant.keyId(),"revoke");keyListener.closed.get(8,TimeUnit.SECONDS);}finally{keySocket.abort();}
   Files.writeString(Path.of(args[0]).resolve("proxy.json"),"{\"ok\":true,\"checks\":[\"untrusted-ca\",\"hostname\",\"redirect\",\"https-private-read\",\"no-store\",\"origin\",\"wss-watch\",\"single-use\",\"wrong-origin\",\"session-revoke\",\"key-revoke\",\"expired-ticket\",\"browser-bearer\",\"TLS1.2\",\"TLS1.3\"]}");
   System.out.println("RIVET_API_PROXY_OK");
  }finally{h.ws.close();h.http.close();h.javaDomains.close();h.tickets.close();h.sessions.close();h.keys.close();h.feed.close();h.client.close();}
 }
}
