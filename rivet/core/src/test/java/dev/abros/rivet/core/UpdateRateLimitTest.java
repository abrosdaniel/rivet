package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class UpdateRateLimitTest {
 @TempDir Path root;
 @SuppressWarnings("unchecked") static HttpResponse<InputStream> response(int status,Map<String,List<String>> headers){return (HttpResponse<InputStream>)java.lang.reflect.Proxy.newProxyInstance(UpdateRateLimitTest.class.getClassLoader(),new Class[]{HttpResponse.class},(proxy,method,args)->switch(method.getName()){case "statusCode"->status;case "headers"->HttpHeaders.of(headers,(a,b)->true);case "body"->new ByteArrayInputStream("[]".getBytes());default->throw new UnsupportedOperationException(method.getName());});}
 @Test void forcedRetryAndRestartRespectServerResetWithoutBlockingOtherHosts()throws Exception{
  var calls=new AtomicInteger();long reset=(System.currentTimeMillis()+3600_000)/1000;
  var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest request){calls.incrementAndGet();return response(403,Map.of("X-RateLimit-Remaining",List.of("0"),"X-RateLimit-Reset",List.of(Long.toString(reset))));}};remote.cacheMetadata(root);
  var failure=assertThrows(Remote.HttpFailure.class,()->remote.bytes(CoreUpdater.RELEASES,1024));assertTrue(failure.retryAt>=reset*1000);
  assertThrows(Remote.HttpFailure.class,()->remote.bytes(CoreUpdater.RELEASES,1024));assertEquals(1,calls.get());
  var restarted=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest request){calls.incrementAndGet();return response(200,Map.of());}};restarted.cacheMetadata(root);assertThrows(Remote.HttpFailure.class,()->restarted.bytes(CoreUpdater.RELEASES,1024));assertEquals(1,calls.get());assertEquals("[]",new String(restarted.bytes(SignedUpdates.SOURCES.getFirst()+"catalog.json",1024)));assertEquals(2,calls.get());
 }
 @Test void retryAfterIsNotClampedToOneMinute(){long now=System.currentTimeMillis();assertEquals(now+600_000,Remote.limitUntil(response(429,Map.of("Retry-After",List.of("600"))),now));assertEquals(0,Remote.limitUntil(response(403,Map.of()),now));assertEquals(now+1000,Remote.limitUntil(response(429,Map.of("Retry-After",List.of("0"))),now));}
}
