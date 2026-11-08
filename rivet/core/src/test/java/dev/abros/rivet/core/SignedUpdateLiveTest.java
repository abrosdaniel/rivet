package dev.abros.rivet.core;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/** Opt-in real HTTP acceptance using the signed candidate, never the installed client. */
class SignedUpdateLiveTest {
 @TempDir Path root;
 @Test void signedCatalogAndBinaryFailOverWithRateLimitAndCorruption()throws Exception{
  String source=System.getenv("RIVET_SIGNED_LIVE");assumeTrue(source!=null,"Signed candidate acceptance is opt-in");Path candidate=Path.of(source);
  byte[] catalog=Files.readAllBytes(candidate.resolve("catalog.json"));var proof=Json.read(candidate.resolve("core.signed.json"));var update=SignedUpdates.update(proof,"1.21.1","21.1.250").orElseThrow();byte[] jar=Files.readAllBytes(candidate.resolve(Path.of(update.artifact().path()).getFileName()));
  var requests=new java.util.concurrent.CopyOnWriteArrayList<String>();var corrupt=new AtomicBoolean();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{try{String url=URLDecoder.decode(exchange.getRequestURI().getRawQuery(),java.nio.charset.StandardCharsets.UTF_8);requests.add(url);int status=200;byte[] body;
   if(url.equals(SignedUpdates.SOURCES.get(0)+"catalog.json")){status=429;exchange.getResponseHeaders().set("Retry-After","600");body=new byte[0];}
   else if(url.equals(SignedUpdates.SOURCES.get(1)+"catalog.json"))body=catalog;
   else if(url.equals(SignedUpdates.SOURCES.get(1)+"versions/"+update.version()+".json"))body=proof.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
   else if(url.equals(update.artifact().urls().get(0))){status=403;exchange.getResponseHeaders().set("X-RateLimit-Remaining","0");exchange.getResponseHeaders().set("Retry-After","600");body=new byte[0];}
   else if(url.equals(update.artifact().urls().get(1))){status=502;body=new byte[0];}
   else if(url.equals(update.artifact().urls().get(2))){body=jar.clone();if(corrupt.get())body[0]^=1;}
   else {status=404;body=new byte[0];}
   exchange.sendResponseHeaders(status,body.length==0?-1:body.length);if(body.length>0)exchange.getResponseBody().write(body);
  }finally{exchange.close();}});server.start();
  try{
   var http=HttpClient.newHttpClient();var remote=new Remote(){@Override HttpResponse<InputStream> send(HttpRequest request)throws IOException,InterruptedException{
    var uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/?"+URLEncoder.encode(request.uri().toString(),java.nio.charset.StandardCharsets.UTF_8));var builder=HttpRequest.newBuilder(uri);request.headers().map().forEach((name,values)->values.forEach(v->builder.header(name,v)));return http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
   }};remote.cacheMetadata(root.resolve("http"));var updater=new CoreUpdater(remote,root.resolve("metadata"));
   var result=updater.catalog("2.9.0","1.21.1","21.1.250",100,false,true);assertFalse(result.partial());assertEquals(update.version(),result.updates().getFirst().version());
   assertEquals(update.version(),updater.signedRelease(update.version(),"1.21.1","21.1.250").orElseThrow().version());
   var cache=new Cache(root.resolve("cache"),remote);updater.obtain(result.updates().getFirst(),cache,new AtomicBoolean(),p->{});assertArrayEquals(jar,Files.readAllBytes(cache.path(update.artifact().sha256())));
   updater.catalog("2.9.0","1.21.1","21.1.250",100,false,true);assertEquals(1,requests.stream().filter(u->u.equals(SignedUpdates.SOURCES.get(0)+"catalog.json")).count());assertFalse(requests.contains(CoreUpdater.RELEASES));
   corrupt.set(true);assertThrows(IOException.class,()->updater.obtain(update,new Cache(root.resolve("corrupt"),remote),new AtomicBoolean(),p->{}));
   System.out.println("RIVET_SIGNED_SOURCES_OK real HTTP: catalog mirror, binary mirror, persisted rate limit, corrupt JAR rejected");
  }finally{server.stop(0);}
 }
}
