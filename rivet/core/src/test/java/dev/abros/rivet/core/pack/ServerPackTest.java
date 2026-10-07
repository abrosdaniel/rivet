package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class ServerPackTest {
 @TempDir Path root;
 @org.junit.jupiter.api.BeforeEach void canonicalRoot()throws Exception{root=root.toRealPath();}
 PackPublisher publisher()throws Exception{return new PackPublisher(root.resolve("source"),root.resolve("published"));}
 void source(String path,String contents)throws Exception{var file=root.resolve("source").resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,contents);}
 @Test void publicationsStayImmutableAndPendingActivationDoesNotOverrideImmediatePublish()throws Exception{
  var p=publisher();source("config/example.toml","one");var first=p.prepare("1.21.1","21.1.250");assertNull(p.current());p.activate(first,false);source("config/example.toml","two");var next=p.prepare("1.21.1","21.1.250");assertEquals(first.hash(),p.current().hash());assertEquals("one",Files.readString(p.object(first.files().getFirst().hash())));p.activate(next,true);p.activate(first,false);p.startup();assertEquals(first.hash(),p.current().hash());
 }
 @Test void sourceLinksAndUnknownFilesAreRejected()throws Exception{var p=publisher();source("config/example.toml","one");Files.createSymbolicLink(root.resolve("source/config/link.toml"),root.resolve("source/config/example.toml"));assertThrows(Exception.class,()->p.prepare("1.21.1","21.1.250"));Files.delete(root.resolve("source/config/link.toml"));source("secrets.txt","private");assertThrows(Exception.class,()->p.prepare("1.21.1","21.1.250"));}
 @Test void tlsTransferResumesAndOlderPublicationCanFinishAfterSwitch()throws Exception{
  var p=publisher();source("resourcepacks/test.zip","abcdefgh");var first=p.prepare("1.21.1","21.1.250");p.activate(first,false);
  try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,1,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   assertEquals(first.hash(),client.manifest().hash());var file=first.files().getFirst();var object=root.resolve("cache").resolve(file.hash());Files.createDirectories(object.getParent());Files.writeString(object.resolveSibling(file.hash()+".pack.part"),"abc");source("resourcepacks/test.zip","new");p.activate(p.prepare("1.21.1","21.1.250"),false);client.download(first.hash(),file,object,new AtomicBoolean(),s->{});assertEquals("abcdefgh",Files.readString(object));
  }
 }
 @Test void wrongCertificateAndUnpublishedRequiredPackFail()throws Exception{var p=publisher();try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0)){assertThrows(Exception.class,()->new PackClient("127.0.0.1",server.port(),"a".repeat(64)));try(var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){assertThrows(java.io.IOException.class,client::manifest);}}}
 @Test void choicesOwnedRemovalAndPersonalConfigsSurviveInstall()throws Exception{
  var p=publisher();source("config/example.toml","server");source("resourcepacks/optional.zip","optional");source("resourcepacks/required.zip","required");source("pack.toml","[[components]]\nid = 'visual'\nname = 'Visual'\noptional = true\nselected = false\nfiles = ['resourcepacks/optional.zip']\n");var manifest=p.prepare("1.21.1","21.1.250");p.activate(manifest,false);
  var game=root.resolve("game");Files.createDirectories(game.resolve("config"));Files.writeString(game.resolve("config/example.toml"),"personal");Files.createDirectories(game.resolve("mods"));try(var jar=new java.util.jar.JarOutputStream(Files.newOutputStream(game.resolve("mods/personal.jar")))){jar.putNextEntry(new java.util.jar.JarEntry("note.txt"));jar.write("mine".getBytes());jar.closeEntry();}byte[] personal=Files.readAllBytes(game.resolve("mods/personal.jar"));var cache=new Cache(game,new Remote());var installer=new PackInstaller(game,cache);assertEquals(Set.of(),installer.choices("server",manifest));
  try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){var review=installer.review("server",manifest,Set.of("visual"));assertTrue(review.plan().conflicts().isEmpty());var id=installer.stage(review,client,new AtomicBoolean(),s->{});new Transactions(game).apply(id);assertEquals(manifest.hash(),installer.installedHash());assertEquals(Set.of("visual"),installer.choices("server",manifest));assertEquals("personal",Files.readString(game.resolve("config/example.toml")));assertArrayEquals(personal,Files.readAllBytes(game.resolve("mods/personal.jar")));assertTrue(Files.exists(game.resolve("resourcepacks/optional.zip")));var deselect=installer.review("server",manifest,Set.of());assertTrue(deselect.plan().changes().stream().anyMatch(c->c.path().equals("resourcepacks/optional.zip")&&c.after()==null));}
 }
 @Test void unownedCollisionNeedsExplicitReviewAndStaleReviewCannotApply()throws Exception{var p=publisher();source("resourcepacks/a.zip","server");var m=p.prepare("1.21.1","21.1.250");var game=root.resolve("game");Files.createDirectories(game.resolve("resourcepacks"));Files.writeString(game.resolve("resourcepacks/a.zip"),"personal");var installer=new PackInstaller(game,new Cache(game,new Remote()));var review=installer.review("server",m,Set.of());assertEquals(1,review.plan().conflicts().size());Files.writeString(game.resolve("resourcepacks/a.zip"),"changed");assertThrows(java.io.IOException.class,()->installer.stage(review,null,new AtomicBoolean(),s->{}));}
 @Test void discoveryRejectsOversizedFrames()throws Exception{try(var server=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())){var thread=new Thread(()->{try(var socket=server.accept()){socket.getOutputStream().write(new byte[]{(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,7});}catch(Exception ignored){}});thread.start();assertThrows(java.io.IOException.class,()->PackDiscovery.query(new java.net.InetSocketAddress("127.0.0.1",server.getLocalPort()),"localhost",25565,"localhost"));thread.join(2000);assertFalse(thread.isAlive());}}
 @Test void trustIsSeparatedByServer()throws Exception{var trust=new PackTrust(root);trust.accept("one","a".repeat(64));assertEquals("a".repeat(64),trust.fingerprint("one"));assertEquals("",trust.fingerprint("two"));}
 @Test void modifiedOwnedFileRemovalIsHighlightedForReview()throws Exception{
  var p=publisher();source("resourcepacks/a.zip","server");var m=p.prepare("1.21.1","21.1.250");p.activate(m,false);Path game=root.resolve("game");Files.createDirectories(game);var installer=new PackInstaller(game,new Cache(game,new Remote()));
  try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){String id=installer.stage(installer.review("server",m,Set.of()),client,new AtomicBoolean(),s->{});new Transactions(game).apply(id);}
  Files.writeString(game.resolve("resourcepacks/a.zip"),"personal changes");Files.delete(root.resolve("source/resourcepacks/a.zip"));var review=installer.review("server",p.prepare("1.21.1","21.1.250"),Set.of());assertEquals(1,review.plan().conflicts().size());assertTrue(review.plan().conflicts().getFirst().contains("Удалить изменённый файл"));
 }

 void install(PackInstaller installer,String address,PackPublisher publisher,PackManifest manifest,Set<String> choices,Path game)throws Exception{
  publisher.activate(manifest,false);
  try(var server=new PackServer(publisher,root.resolve("identity-"+address),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   new Transactions(game).apply(installer.stage(installer.review(address,manifest,choices),client,new AtomicBoolean(),s->{}));
  }
 }
 @Test void switchingServersRestoresTheirConfigsAndChoicesWithoutOwningPersonalDefaults()throws Exception{
  var a=new PackPublisher(root.resolve("a"),root.resolve("published-a"));var b=new PackPublisher(root.resolve("b"),root.resolve("published-b"));
  for(String server:List.of("a","b")){Path source=root.resolve(server);Files.createDirectories(source.resolve("config"));Files.createDirectories(source.resolve("resourcepacks"));Files.writeString(source.resolve("config/shared.toml"),server);Files.writeString(source.resolve("config/personal.toml"),"server default");Files.writeString(source.resolve("config/"+server+".toml"),server);Files.writeString(source.resolve("resourcepacks/"+server+".zip"),server);Files.writeString(source.resolve("pack.toml"),"[[components]]\nid='extra'\nname='Extra'\noptional=true\nselected=false\nfiles=['resourcepacks/"+server+".zip']\n");}
  var ma=a.prepare("1.21.1","21.1.250");var mb=b.prepare("1.21.1","21.1.250");Path game=root.resolve("game");Files.createDirectories(game.resolve("config"));Files.writeString(game.resolve("config/personal.toml"),"personal");var installer=new PackInstaller(game,new Cache(game,new Remote()));
  install(installer,"a",a,ma,Set.of("extra"),game);Files.writeString(game.resolve("config/shared.toml"),"A preferences");
  install(installer,"b",b,mb,Set.of(),game);assertEquals("b",Files.readString(game.resolve("config/shared.toml")));assertFalse(Files.exists(game.resolve("config/a.toml")));assertFalse(Files.exists(game.resolve("resourcepacks/a.zip")));Files.writeString(game.resolve("config/shared.toml"),"B preferences");
  install(installer,"a",a,ma,installer.choices("a",ma),game);assertEquals("A preferences",Files.readString(game.resolve("config/shared.toml")));assertFalse(Files.exists(game.resolve("config/b.toml")));assertTrue(Files.exists(game.resolve("resourcepacks/a.zip")));assertEquals("personal",Files.readString(game.resolve("config/personal.toml")));assertFalse(Json.read(game.resolve("rivet/state.json")).getAsJsonObject("ownership").has("config/personal.toml"));
  install(installer,"b",b,mb,installer.choices("b",mb),game);assertEquals("B preferences",Files.readString(game.resolve("config/shared.toml")));assertFalse(Files.exists(game.resolve("resourcepacks/b.zip")));assertEquals("personal",Files.readString(game.resolve("config/personal.toml")));
 }
 @Test void changedConfigAfterReviewCannotBeSilentlySavedOrReplaced()throws Exception{
  var p=publisher();source("config/a.toml","a");var manifest=p.prepare("1.21.1","21.1.250");Path game=root.resolve("game");Files.createDirectories(game);var installer=new PackInstaller(game,new Cache(game,new Remote()));install(installer,"a",p,manifest,Set.of(),game);
  var review=installer.review("a",manifest,Set.of());Files.writeString(game.resolve("config/a.toml"),"edited after review");assertThrows(java.io.IOException.class,()->installer.stage(review,null,new AtomicBoolean(),s->{}));assertFalse(Files.exists(game.resolve("rivet/pending.json")));assertEquals("edited after review",Files.readString(game.resolve("config/a.toml")));
 }
 @Test void damagedSavedConfigStopsSwitchBeforeAnyFilesChange()throws Exception{
  var p=publisher();source("config/a.toml","a");var manifest=p.prepare("1.21.1","21.1.250");Path game=root.resolve("game");Files.createDirectories(game);var installer=new PackInstaller(game,new Cache(game,new Remote()));install(installer,"a",p,manifest,Set.of(),game);Files.writeString(game.resolve("config/a.toml"),"saved A");install(installer,"b",p,manifest,Set.of(),game);
  Files.writeString(game.resolve("rivet/pack-settings/"+Hashes.sha256("saved A".getBytes(java.nio.charset.StandardCharsets.UTF_8))),"corrupted");assertThrows(java.io.IOException.class,()->installer.review("a",manifest,Set.of()));assertEquals("a",Files.readString(game.resolve("config/a.toml")));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
 }

 @Test void togglingOptionalConfigRestoresPreferencesAndNeverTouchesPersonalConfig()throws Exception{
  var p=publisher();source("config/extra.toml","default");source("pack.toml","[[components]]\nid='extra'\nname='Extra'\noptional=true\nselected=false\nfiles=['config/extra.toml']\n");var manifest=p.prepare("1.21.1","21.1.250");Path game=root.resolve("game");Files.createDirectories(game);var installer=new PackInstaller(game,new Cache(game,new Remote()));
  install(installer,"a",p,manifest,Set.of("extra"),game);Files.writeString(game.resolve("config/extra.toml"),"my preferences");install(installer,"a",p,manifest,Set.of(),game);assertFalse(Files.exists(game.resolve("config/extra.toml")));install(installer,"a",p,manifest,Set.of("extra"),game);assertEquals("my preferences",Files.readString(game.resolve("config/extra.toml")));
 }

}
