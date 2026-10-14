package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;import dev.abros.rivet.core.auth.AuthTls;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.io.*;import java.net.*;import javax.net.ssl.*;import com.google.gson.*;
import static org.junit.jupiter.api.Assertions.*;
class PackSecurityTest {
 @TempDir Path temp;
 PackPublisher publisher()throws Exception{temp=temp.toRealPath();Files.createDirectories(temp.resolve("source/resourcepacks"));Files.write(temp.resolve("source/resourcepacks/large.zip"),new byte[32*1024*1024]);Files.writeString(temp.resolve("source/resourcepacks/small.zip"),"small");var p=new PackPublisher(temp.resolve("source"),temp.resolve("published"));p.activate(p.prepare("1.21.1","21.1.250"),false);return p;}
 SSLSocket connect(PackServer server)throws Exception{var s=(SSLSocket)AuthTls.client(server.fingerprint()).getSocketFactory().createSocket();s.setReceiveBufferSize(1024);s.connect(new InetSocketAddress("127.0.0.1",server.port()),2000);s.setSoTimeout(4000);s.startHandshake();return s;}
 JsonObject request(PackManifest m,PackManifest.Entry f,long offset){var q=new JsonObject();q.addProperty("action","file");q.addProperty("publication",m.hash());q.addProperty("hash",f.hash());q.addProperty("offset",offset);return q;}
 @Test void slowReaderReleasesSlotAndNextClientCanDownload()throws Exception{
  var p=publisher();var m=p.current();var large=m.files().stream().filter(f->f.size()>100).findFirst().orElseThrow();var small=m.files().stream().filter(f->f.size()==5).findFirst().orElseThrow();
  try(var server=new PackServer(p,temp.resolve("identity"),"127.0.0.1",0,true,1,0,0,new PackServer.Protection(4,8,1,4));var stalled=connect(server)){
   PackWire.write(new DataOutputStream(stalled.getOutputStream()),request(m,large,0));var input=new DataInputStream(stalled.getInputStream());assertTrue(PackWire.read(input,16384).get("queued").getAsBoolean());assertEquals(large.size(),PackWire.read(input,16384).get("bytes").getAsLong());
   try(var next=connect(server)){PackWire.write(new DataOutputStream(next.getOutputStream()),request(m,small,0));var in=new DataInputStream(next.getInputStream());assertTrue(PackWire.read(in,16384).get("queued").getAsBoolean());assertEquals(5,PackWire.read(in,16384).get("bytes").getAsInt());assertEquals("small",new String(in.readNBytes(5),java.nio.charset.StandardCharsets.UTF_8));}
  }
 }
 @Test void eofRequestsAreRateLimitedAcrossConnections()throws Exception{
  var p=publisher();var m=p.current();var f=m.files().getFirst();long started=System.nanoTime();try(var server=new PackServer(p,temp.resolve("identity"),"127.0.0.1",0,true,0,0,0,new PackServer.Protection(4,1,3,3))){
   try(var first=connect(server)){var in=new DataInputStream(first.getInputStream());for(int i=0;i<2;i++){PackWire.write(new DataOutputStream(first.getOutputStream()),request(m,f,f.size()));assertEquals(0,PackWire.read(in,16384).get("bytes").getAsInt());}}
   try(var next=connect(server)){for(int n=0;n<2;n++){PackWire.write(new DataOutputStream(next.getOutputStream()),request(m,f,f.size()));assertEquals(0,PackWire.read(new DataInputStream(next.getInputStream()),16384).get("bytes").getAsInt());}}assertTrue(System.nanoTime()-started>=1_500_000_000L,"Reconnect bypassed the shared request budget");
  }
 }
 @Test void verifiedObjectsStillRejectCorruptionAndReplacement()throws Exception{
  temp=temp.toRealPath();var path=temp.resolve("object");Files.writeString(path,"good");String hash=Hashes.sha256(path);var verifier=new PackObjectVerifier();verifier.check(path,hash,4);verifier.check(path,hash,4);
  var replacement=temp.resolve("new");Files.writeString(replacement,"evil");Files.move(replacement,path,StandardCopyOption.REPLACE_EXISTING);assertThrows(IOException.class,()->verifier.check(path,hash,4));
 }
 @Test void sameSizeCorruptionWithRestoredModificationTimeIsRejected()throws Exception{
  temp=temp.toRealPath();var path=temp.resolve("object");Files.writeString(path,"good");
  var modified=Files.getLastModifiedTime(path);String hash=Hashes.sha256(path);var verifier=new PackObjectVerifier();verifier.check(path,hash,4);
  Files.writeString(path,"evil");Files.setLastModifiedTime(path,modified);
  assertThrows(IOException.class,()->verifier.check(path,hash,4));
 }
 @Test void filesystemWithoutChangeTimestampNeverReusesVerification()throws Exception{
  // ZIP supplies only basic timestamps, reproducing ambiguous metadata on every OS.
  try(var fs=FileSystems.newFileSystem(temp.resolve("objects.zip"),java.util.Map.of("create","true"))){
   var path=fs.getPath("/object");Files.writeString(path,"good");
   var attrs=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);
   var verifier=new PackObjectVerifier();String hash=Hashes.sha256(path);verifier.check(path,hash,4);verifier.check(path,hash,4);
   Files.writeString(path,"evil");
   Files.getFileAttributeView(path,java.nio.file.attribute.BasicFileAttributeView.class).setTimes(attrs.lastModifiedTime(),attrs.lastAccessTime(),attrs.creationTime());
   assertThrows(IOException.class,()->verifier.check(path,hash,4));
  }
 }
 @Test void blockedWriteTimesOutButTimeOutsideWritesDoesNot()throws Exception{
  try(var listener=new ServerSocket(0);var client=new Socket("127.0.0.1",listener.getLocalPort());var peer=listener.accept()){
   var timer=new java.util.concurrent.ScheduledThreadPoolExecutor(1);try(var guard=new PackTransferGuard(peer,timer,java.time.Duration.ofMillis(100))){Thread.sleep(150);assertFalse(peer.isClosed());guard.begin();Thread.sleep(250);assertTrue(peer.isClosed());assertThrows(IOException.class,guard::end);}finally{timer.shutdownNow();}
  }
 }
 @Test void addressConnectionQuotaRejectsExcessHandshake()throws Exception{
  var p=publisher();try(var server=new PackServer(p,temp.resolve("identity"),"127.0.0.1",0,true,1,0,0,new PackServer.Protection(1,8,2,2));var first=connect(server)){
   assertThrows(IOException.class,()->{try(var rejected=connect(server)){} });
   var q=new JsonObject();q.addProperty("action","manifest");PackWire.write(new DataOutputStream(first.getOutputStream()),q);assertTrue(PackWire.read(new DataInputStream(first.getInputStream()),8*1024*1024).get("ready").getAsBoolean());
  }
 }
 @Test void waitingForTransferSlotHasFiniteDeadline()throws Exception{
  var p=publisher();var m=p.current();var large=m.files().stream().filter(f->f.size()>100).findFirst().orElseThrow();
  try(var server=new PackServer(p,temp.resolve("identity"),"127.0.0.1",0,true,1,0,0,new PackServer.Protection(4,8,4,1));var stalled=connect(server)){
   PackWire.write(new DataOutputStream(stalled.getOutputStream()),request(m,large,0));var input=new DataInputStream(stalled.getInputStream());PackWire.read(input,16384);PackWire.read(input,16384);
   try(var next=connect(server)){PackWire.write(new DataOutputStream(next.getOutputStream()),request(m,large,0));var in=new DataInputStream(next.getInputStream());assertTrue(PackWire.read(in,16384).get("queued").getAsBoolean());assertThrows(EOFException.class,()->PackWire.read(in,16384));}
  }
 }
}
