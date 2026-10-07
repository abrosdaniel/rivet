package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PackDownloadFallbackTest {
 @TempDir Path root;
 PackPublisher publisher;PackManifest manifest;PackManifest.Entry file;
 @BeforeEach void setup()throws Exception{
  root=root.toRealPath();publisher=new PackPublisher(root.resolve("source"),root.resolve("data"));Files.createDirectories(root.resolve("source/resourcepacks"));Files.writeString(root.resolve("source/resourcepacks/test.zip"),"abcdefgh");
  var original=publisher.prepare("1.21.1","21.1.250");var first=original.files().getFirst();file=new PackManifest.Entry(first.path(),first.hash(),first.size(),first.component(),first.policy(),"https://cdn.modrinth.com/data/test/versions/test/test.jar");manifest=new PackManifest(original.minecraft(),original.loader(),original.components(),List.of(file));Files.write(root.resolve("data/publications/"+manifest.hash()+".json"),manifest.bytes());publisher.activate(manifest,false);
 }
 Path object(){return root.resolve("cache").resolve(file.hash());}
 @Test void sourceSuccessCacheHitAndServerReconnectRemainUsable()throws Exception{
  var calls=new AtomicInteger();try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint(),(url,target,size,cancel)->{calls.incrementAndGet();Files.writeString(target,"abcdefgh");})){
   client.download(manifest.hash(),file,object(),new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(object()));assertEquals(1,calls.get());client.download(manifest.hash(),file,object(),new AtomicBoolean(),s->{});assertEquals(1,calls.get());assertEquals(manifest.hash(),client.manifest().hash());
   var local=new PackManifest.Entry(file.path(),file.hash(),file.size(),"","replace");client.download(manifest.hash(),local,root.resolve("second"),new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(root.resolve("second")));assertEquals(1,calls.get());
  }
 }
 @Test void unavailableCorruptAndIncompleteSourcesFallBackToOriginalPublication()throws Exception{
  for(int mode=0;mode<4;mode++){int failure=mode;var folder=root.resolve("cache"+mode);var object=folder.resolve(file.hash());Files.createDirectories(folder);Files.writeString(object.resolveSibling(file.hash()+".pack.part"),"abc");var progress=new ArrayList<String>();
   try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint(),(url,target,size,cancel)->{switch(failure){case 0->throw new IOException("HTTP 503");case 1->Files.writeString(target,"12345678");case 2->Files.writeString(target,"abc");default->Files.writeString(target,"abcdefgh-extra");}})){
    client.download(manifest.hash(),file,object,new AtomicBoolean(),progress::add);assertEquals("abcdefgh",Files.readString(object));assertTrue(progress.stream().anyMatch(s->s.contains("Загрузка с сервера")));assertFalse(Files.exists(object.resolveSibling(file.hash()+".source.part")));
   }
  }
 }
 @Test void slowUnavailableSourceFallsBackAfterServerIdleTimeout()throws Exception{
  try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint(),(url,target,size,cancel)->{Thread.sleep(16000);throw new IOException("source timed out");})){
   client.download(manifest.hash(),file,object(),new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(object()));
  }
 }
 @Test void cancellationNeverFallsBackAndDoesNotPoisonConnection()throws Exception{
  var cancel=new AtomicBoolean();var progress=new ArrayList<String>();try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint(),(url,target,size,token)->{Files.writeString(target,"abc");token.set(true);throw new IOException("cancelled");})){
   assertThrows(InterruptedIOException.class,()->client.download(manifest.hash(),file,object(),cancel,progress::add));assertFalse(Files.exists(object()));assertFalse(progress.stream().anyMatch(s->s.contains("Загрузка с сервера")));assertFalse(Files.exists(object().resolveSibling(file.hash()+".source.part")));
   var local=new PackManifest.Entry(file.path(),file.hash(),file.size(),"","replace");client.download(manifest.hash(),local,object(),new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(object()));
  }
 }
 @Test void urlRoundTripIsBoundToPublicationAndLegacyBytesStayUnchanged()throws Exception{
  assertEquals(manifest,PackManifest.parse(manifest.bytes()));var old=publisher.prepare("1.21.1","21.1.250");assertFalse(new String(old.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("\"url\""));assertEquals(old.hash(),PackManifest.parse(old.bytes()).hash());assertNotEquals(old.hash(),manifest.hash());
  for(String url:List.of("http://cdn.modrinth.com/test","https://127.0.0.1/test","https://user:secret@cdn.modrinth.com/test","https://api.curseforge.com/test?key=secret","https://cdn.modrinth.com/test?key=secret"))assertThrows(IllegalArgumentException.class,()->new PackManifest.Entry(file.path(),file.hash(),file.size(),"","replace",url));
 }
}
