package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** Real publication, TLS download and helper transaction for mod-defined asset folders. */
class PackFoldersTest {
 @org.junit.jupiter.api.io.TempDir Path root;
 @org.junit.jupiter.api.BeforeEach void canonicalRoot()throws Exception{root=root.toRealPath();}
 PackPublisher publisher()throws Exception{return new PackPublisher(root.resolve("source"),root.resolve("published"));}
 void source(String path,String contents)throws Exception{var file=root.resolve("source").resolve(path);Files.createDirectories(file.getParent());Files.writeString(file,contents);}
 void install(PackInstaller installer,String address,PackPublisher publisher,PackManifest manifest,Set<String> choices,Path game)throws Exception{
  publisher.activate(manifest,false);
  try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   new Transactions(game).apply(installer.stage(installer.review(address,manifest,choices),client,new AtomicBoolean(),s->{}));
  }
 }
 @Test void customFoldersInstallUpdateAndRemoveOnlyOwnedFiles()throws Exception{
  var publisher=publisher();String emote="emotes/Apologize on your knees.emotecraft";
  source(emote,"first");source("custom-assets/nested/текст.json","asset");
  var first=publisher.prepare("1.21.1","21.1.250");var game=root.resolve("game");
  Files.createDirectories(game.resolve("emotes"));Files.writeString(game.resolve("emotes/personal.emotecraft"),"personal");
  var installer=new PackInstaller(game,new Cache(game,new Remote()));
  install(installer,"server",publisher,first,Set.of(),game);
  assertEquals("first",Files.readString(game.resolve(emote)));
  assertEquals("asset",Files.readString(game.resolve("custom-assets/nested/текст.json")));
  assertTrue(installer.review("server",first,Set.of()).plan().changes().isEmpty());
  source(emote,"second");Files.delete(root.resolve("source/custom-assets/nested/текст.json"));
  var second=publisher.prepare("1.21.1","21.1.250");
  install(installer,"server",publisher,second,Set.of(),game);
  assertEquals("second",Files.readString(game.resolve(emote)));
  assertFalse(Files.exists(game.resolve("custom-assets/nested/текст.json")));
  assertEquals("personal",Files.readString(game.resolve("emotes/personal.emotecraft")));
  // A new installer sees persisted ownership and does not reinstall after reconnect.
  assertTrue(new PackInstaller(game,new Cache(game,new Remote())).review("server",second,Set.of()).plan().changes().isEmpty());
 }
 @Test void customFolderConflictsAndEditsAfterReviewArePreserved()throws Exception{
  var publisher=publisher();String path="emotes/wave.emotecraft";source(path,"server");
  var manifest=publisher.prepare("1.21.1","21.1.250");Path game=root.resolve("game");
  Files.createDirectories(game.resolve("emotes"));Files.writeString(game.resolve(path),"personal");
  var installer=new PackInstaller(game,new Cache(game,new Remote()));var review=installer.review("server",manifest,Set.of());
  assertEquals(1,review.plan().conflicts().size());assertEquals("personal",Files.readString(game.resolve(path)));
  Files.writeString(game.resolve(path),"new personal edit");
  assertThrows(java.io.IOException.class,()->installer.stage(review,null,new AtomicBoolean(),s->{}));
  assertEquals("new personal edit",Files.readString(game.resolve(path)));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
  install(installer,"server",publisher,manifest,Set.of(),game); // Explicitly accepted review.
  Files.writeString(game.resolve(path),"modified owned");Files.delete(root.resolve("source").resolve(path));
  var removal=installer.review("server",publisher.prepare("1.21.1","21.1.250"),Set.of());
  assertEquals(1,removal.plan().conflicts().size());assertEquals("modified owned",Files.readString(game.resolve(path)));
 }
 @Test void protectedFoldersAreRejectedByPublisherAndUntrustedManifest()throws Exception{
  var publisher=publisher();
  for(String path:List.of("saves/world/level.dat","RIVET/state.json","Config/Rivet-client.toml","logs/latest.log",
    "screenshots/personal.png","libraries/code.jar","runtime/java","Xaero/waypoints.txt",".ssh/id_rsa",
    "emotes/../rivet/state.json","emotes/.git/config","options.txt","servers.dat","emotes/CON.emotecraft","Mods/a.jar","CONFIG/private.toml")){
   assertThrows(IllegalArgumentException.class,()->SafePaths.validate(path),path);
   assertThrows(IllegalArgumentException.class,()->new PackManifest("1.21.1","21.1.250",List.of(),List.of(new PackManifest.Entry(path,"a".repeat(64),1,"","replace",""))),path);
  }
  source("saves/world/level.dat","personal");assertThrows(IllegalArgumentException.class,()->publisher.prepare("1.21.1","21.1.250"));
 }
 @Test void interruptedCustomFolderUpdateRestoresOriginalFiles()throws Exception{
  var publisher=publisher();String path="emotes/wave.emotecraft";source(path,"original");
  var first=publisher.prepare("1.21.1","21.1.250");Path game=root.resolve("game");Files.createDirectories(game);
  var installer=new PackInstaller(game,new Cache(game,new Remote()));install(installer,"server",publisher,first,Set.of(),game);
  source(path,"updated");var next=publisher.prepare("1.21.1","21.1.250");publisher.activate(next,false);
  try(var server=new PackServer(publisher,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   String id=installer.stage(installer.review("server",next,Set.of()),client,new AtomicBoolean(),s->{});
   System.setProperty("rivet.test.failAfter","1");
   try{assertThrows(java.io.IOException.class,()->new Transactions(game).apply(id));}
   finally{System.clearProperty("rivet.test.failAfter");}
   assertEquals("original",Files.readString(game.resolve(path)));assertEquals(first.hash(),installer.installedHash());
  }
  install(installer,"server",publisher,next,Set.of(),game);assertEquals("updated",Files.readString(game.resolve(path)));
 }
 @Test void customFolderSymlinkCannotEscapeGame()throws Exception{
  Path game=root.resolve("game"),outside=root.resolve("outside");Files.createDirectories(game);Files.createDirectories(outside);
  Files.createSymbolicLink(game.resolve("emotes"),outside);
  assertThrows(java.io.IOException.class,()->SafePaths.resolve(game,"emotes/wave.emotecraft"));
 }
}
