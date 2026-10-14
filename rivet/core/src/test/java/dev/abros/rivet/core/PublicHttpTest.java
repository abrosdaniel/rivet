package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;import java.net.*;import java.net.http.*;import static org.junit.jupiter.api.Assertions.*;
class PublicHttpTest {
 @Test void rejectsPrivateAndMixedDnsAnswers()throws Exception{
  for(String ip:new String[]{"127.0.0.1","10.0.0.1","192.168.0.1","169.254.169.254","100.64.0.1","0.0.0.0","224.0.0.1","::1","fc00::1","fe80::1","64:ff9b::7f00:1","2002:7f00:1::"})assertFalse(PublicHttp.publicAddress(InetAddress.getByName(ip)),ip);
  var publicIp=InetAddress.getByName("8.8.8.8");assertTrue(PublicHttp.publicAddress(publicIp));assertThrows(UnknownHostException.class,()->PublicHttp.checked(new InetAddress[]{publicIp,InetAddress.getByName("127.0.0.1")}));
 }
 @Test void privateLiteralNeverConnects()throws Exception{try(var listener=new java.net.ServerSocket(0)){listener.setSoTimeout(150);assertThrows(java.io.IOException.class,()->PublicHttp.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+listener.getLocalPort())).build()));assertThrows(SocketTimeoutException.class,listener::accept);}}
 @Test void privateHostnameNeverConnects()throws Exception{try(var listener=new java.net.ServerSocket(0)){listener.setSoTimeout(150);assertThrows(java.io.IOException.class,()->PublicHttp.send(HttpRequest.newBuilder(URI.create("https://localhost:"+listener.getLocalPort())).build()));assertThrows(SocketTimeoutException.class,listener::accept);}}
 @Test void changedDnsAnswerIsRejectedOnNextConnectionResolution()throws Exception{var calls=new java.util.concurrent.atomic.AtomicInteger();var resolver=PublicHttp.resolver(host->new InetAddress[]{InetAddress.getByName(calls.getAndIncrement()==0?"8.8.8.8":"127.0.0.1")});assertEquals("8.8.8.8",resolver.resolve("changing.example")[0].getHostAddress());assertThrows(UnknownHostException.class,()->resolver.resolve("changing.example"));assertEquals(2,calls.get());}
}
