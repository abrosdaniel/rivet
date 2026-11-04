package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PackRoutesTest {
 @TempDir Path root;
 @org.junit.jupiter.api.BeforeEach void realRoot()throws Exception{root=root.toRealPath();}
 @Test void proxyPreferenceLegacyFallbackAndMalformedStatus()throws Exception{
  var j=Json.parse("{\"rivetPack\":{\"protocol\":2,\"host\":\"direct.example.org\",\"port\":25567,\"fingerprint\":\""+"a".repeat(64)+"\",\"required\":true,\"routes\":[{\"host\":\"tunnel.example.org\",\"port\":31278,\"gameHost\":\"proxy.example.org\"}]}}");
  var proxy=PackDiscovery.parseStatus(j,"PROXY.example.org");assertEquals("tunnel.example.org",proxy.routes().getFirst().host());assertEquals("direct.example.org",proxy.pack().host());assertEquals("direct.example.org",PackDiscovery.parseStatus(j,"other.example.org").routes().getFirst().host());
  j.getAsJsonObject("rivetPack").getAsJsonArray("routes").get(0).getAsJsonObject().addProperty("port",1.5);assertThrows(java.io.IOException.class,()->PackDiscovery.parseStatus(j,"proxy.example.org"));
 }
 @Test void configurationMigrationPreservesOwnerSettingsAndComments()throws Exception{
  Path config=root.resolve("config/rivet-server.toml");Files.createDirectories(config.getParent());String old=ServerSettings.template().replace("routes = []", "").replace("publicPort = 25567", "publicPort = 32000 # owner");Files.writeString(config,old);
  var settings=ServerSettings.load(root);assertEquals(32000,settings.number("pack.network.publicPort"));assertTrue(settings.packRoutes().isEmpty());assertTrue(Files.readString(config).contains("publicPort = 32000 # owner"));assertTrue(Files.readString(config).contains("routes = []"));
  var custom=ServerSettings.parse(ServerSettings.template().replace("routes = []","routes = [{host = \"tunnel.example.org\", port = 12345, gameHost = \"play.example.org\"}]"));assertEquals(12345,custom.packRoutes().getFirst().port());
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("routes = []","routes = [{host = \"https://bad\", port = 1}]")));
 }
 @Test void firstProxyVisitAfterUpgradeRetainsExistingDirectChoices()throws Exception{
  var trust=new PackTrust(root);String key="a".repeat(64);trust.accept("direct:25565",key);Json.write(root.resolve("rivet/state.json"),java.util.Map.of("packServer","direct:25565"));trust.accept("proxy:25565",key);assertEquals("direct:25565",trust.canonical("proxy:25565",key));
 }
 @Test void pinnedIdentitySharesChoicesAcrossAliasesOnlyAfterAuthentication()throws Exception{
  var trust=new PackTrust(root);String key="a".repeat(64);trust.accept("direct:25565",key);assertEquals("direct:25565",trust.canonical("direct:25565",key));trust.accept("proxy:25565",key);assertEquals("direct:25565",trust.canonical("proxy:25565",key));assertEquals("other:25565",trust.canonical("other:25565","b".repeat(64)));
 }
 private PackPublisher publisher()throws Exception{root=root.toRealPath();var p=new PackPublisher(root.resolve("source"),root.resolve("data"));Files.createDirectories(root.resolve("source/resourcepacks"));Files.writeString(root.resolve("source/resourcepacks/test.zip"),"abcdefgh");var m=p.prepare("1.21.1","21.1.250");p.activate(m,false);return p;}
 private PackDiscovery.Endpoint endpoint(int port,String fingerprint){return new PackDiscovery.Endpoint("127.0.0.1",port,fingerprint,true);}
 @Test void stalledPrimaryDoesNotDelayBackupHandshakeAndDownload()throws Exception{
  var p=publisher();try(var stalled=new ServerSocket(0);var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0)){
   long start=System.nanoTime();try(var client=new PackClient(List.of(endpoint(stalled.getLocalPort(),server.fingerprint()),endpoint(server.port(),server.fingerprint())),new AtomicBoolean())){
    var offer=client.manifest();assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)<5,"backup must not wait for primary TLS timeout");var file=offer.manifest().files().getFirst();var target=root.resolve("cache/object");Files.createDirectories(target.getParent());Files.writeString(target.resolveSibling("object.pack.part"),"abc");client.download(offer.hash(),file,target,new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(target));
   }
  }
 }
 @Test void lostConnectionResumesPartialFileUsingSamePublicationOnBackup()throws Exception{
  var p=publisher();var primary=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);
  try(var backup=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient(List.of(endpoint(primary.port(),primary.fingerprint()),endpoint(backup.port(),backup.fingerprint())),new AtomicBoolean())){
   var offer=client.manifest();primary.close();var target=root.resolve("cache/object");Files.createDirectories(target.getParent());Files.writeString(target.resolveSibling("object.pack.part"),"abc");var progress=new ArrayList<String>();
   client.download(offer.hash(),offer.manifest().files().getFirst(),target,new AtomicBoolean(),progress::add);assertEquals("abcdefgh",Files.readString(target));assertFalse(progress.isEmpty());
  }finally{primary.close();}
 }
 @Test void actualMidTransferEofResumesAtSavedOffsetOnBackup()throws Exception{
  var p=publisher();var manifest=p.prepare("1.21.1","21.1.250");p.activate(manifest,false);
  var identity=dev.abros.rivet.core.auth.AuthTls.identity(root.resolve("identity"));
  try(var primary=(javax.net.ssl.SSLServerSocket)identity.context().getServerSocketFactory().createServerSocket(0);var backup=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var serving=executor.submit(()->{try(var socket=primary.accept()){
    socket.setSoTimeout(5000);var in=new java.io.DataInputStream(socket.getInputStream());var out=new java.io.DataOutputStream(socket.getOutputStream());
    var request=PackWire.read(in,16384);assertEquals("file",Json.str(request,"action"));assertEquals(0,request.get("offset").getAsLong());
    PackWire.write(out,Json.parse("{\"bytes\":8}"));out.writeBytes("abc");out.flush();
   }return null;});
   try(var client=new PackClient(List.of(endpoint(primary.getLocalPort(),identity.fingerprint()),endpoint(backup.port(),identity.fingerprint())),new AtomicBoolean())){
    var positions=new ArrayList<Long>();Path target=root.resolve("cache/object");client.download(manifest.hash(),manifest.files().getFirst(),target,new AtomicBoolean(),t->{},(position,received)->positions.add(position));
    assertEquals("abcdefgh",Files.readString(target));assertTrue(positions.stream().filter(n->n==3).count()>=2,"partial data must be received and then reused as offset");assertEquals(8L,positions.getLast());
   }serving.get(5,TimeUnit.SECONDS);
  }
 }
 @Test void backupCannotSubstituteAnotherPublication()throws Exception{
  var original=publisher();var other=new PackPublisher(root.resolve("other-source"),root.resolve("other-data"));
  Files.createDirectories(root.resolve("other-source/resourcepacks"));Files.writeString(root.resolve("other-source/resourcepacks/test.zip"),"different");other.activate(other.prepare("1.21.1","21.1.250"),false);
  var primary=new PackServer(original,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);
  try(var backup=new PackServer(other,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient(List.of(endpoint(primary.port(),primary.fingerprint()),endpoint(backup.port(),backup.fingerprint())),new AtomicBoolean())){
   var offer=client.manifest();primary.close();Path target=root.resolve("cache/object");
   assertThrows(java.io.IOException.class,()->client.download(offer.hash(),offer.manifest().files().getFirst(),target,new AtomicBoolean(),message->{}));
   assertFalse(Files.exists(target),"another publication must never replace the reviewed file");
  }finally{primary.close();}
 }
 @Test void allUnavailableIncludesAddresses()throws Exception{
  int port;try(var reserved=new ServerSocket(0)){port=reserved.getLocalPort();}
  var failure=assertThrows(java.io.IOException.class,()->new PackClient(List.of(endpoint(port,"a".repeat(64))),new AtomicBoolean()));assertTrue(failure.getMessage().contains("127.0.0.1:"+port));
 }
 @Test void wrongKeyCannotWinRace()throws Exception{
  var p=publisher();try(var wrong=new PackServer(p,root.resolve("wrong"),"127.0.0.1",0,true,0,0,0);var right=new PackServer(p,root.resolve("right"),"127.0.0.1",0,true,0,0,0);var client=new PackClient(List.of(endpoint(wrong.port(),right.fingerprint()),endpoint(right.port(),right.fingerprint())),new AtomicBoolean())){assertNotNull(client.manifest());}
 }
 @Test void cancellationInterruptsStalledHandshake()throws Exception{
  var token=new AtomicBoolean();try(var stalled=new ServerSocket(0);var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var future=executor.submit(()->{assertThrows(java.io.InterruptedIOException.class,()->new PackClient(List.of(endpoint(stalled.getLocalPort(),"a".repeat(64))),token));});Thread.sleep(250);token.set(true);future.get(3,TimeUnit.SECONDS);
  }
 }
}
