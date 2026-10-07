package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.jar.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class PackProjectedModsTest {
 @TempDir Path game;
 Path jar(String path,String id,String version,String dependencies)throws Exception{
  Path file=game.resolve(path);Files.createDirectories(file.getParent());
  try(var out=new JarOutputStream(Files.newOutputStream(file))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(("[[mods]]\nmodId='"+id+"'\nversion='"+version+"'\n"+dependencies).getBytes());out.closeEntry();}return file;
 }
 String dependency(String owner,String id,String type,String range,String side){return "[[dependencies."+owner+"]]\nmodId='"+id+"'\ntype='"+type+"'\nversionRange='"+range+"'\nside='"+side+"'\n";}
 PackManifest manifest(List<PackManifest.Component> components,List<PackManifest.Entry> files){return new PackManifest("1.21.1","21.1.250",components,files);}
 void own(String path)throws Exception{Json.write(game.resolve("rivet/state.json"),Map.of("packServer","old-server","ownership",Map.of(path,new Planner.Owned(Hashes.sha256(game.resolve(path)),"enforce",""))));}
 PackInstaller installer(Cache cache){return new PackInstaller(game,cache,"1.3.2");}
 PackManifest.Entry cached(Cache cache,Path source,String destination,String component)throws Exception{
  String hash=Hashes.sha256(source);Path object=cache.path(hash);Files.createDirectories(object.getParent());Files.copy(source,object);return new PackManifest.Entry(destination,hash,Files.size(source),component,"replace");
 }
 @Test void switchingCannotRemoveLibraryRequiredByPersonalMod()throws Exception{
  var cache=new Cache(game,new Remote());var library=jar("mods/library.jar","library","1.0","");var personal=jar("mods/personal.jar","personal","1.0",dependency("personal","library","required","[1,2)","CLIENT"));own("mods/library.jar");
  var before=Json.read(game.resolve("rivet/state.json"));var installer=installer(cache);var review=installer.review("new-server",manifest(List.of(),List.of()),Set.of());
  var failure=assertThrows(IOException.class,()->installer.stage(review,null,new AtomicBoolean(),s->{}));assertTrue(failure.getMessage().contains("mods/personal.jar"));assertTrue(failure.getMessage().contains("library"));assertFalse(Files.exists(game.resolve("rivet/pending.json")));assertEquals(before,Json.read(game.resolve("rivet/state.json")));assertTrue(Files.exists(library));assertTrue(Files.exists(personal));
 }
 @Test void replacementMustSatisfyPersonalVersionRange()throws Exception{
  var cache=new Cache(game,new Remote());jar("mods/library.jar","library","1.0","");jar("mods/personal.jar","personal","1.0",dependency("personal","library","required","[1,2)","BOTH"));own("mods/library.jar");
  var next=cached(cache,jar("new.jar","library","2.0",""),"mods/library.jar","");var installer=installer(cache);
  var review=installer.review("new-server",manifest(List.of(),List.of(next)),Set.of());assertTrue(assertThrows(IOException.class,()->installer.validateMods(review)).getMessage().contains("найдена 2.0"));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
 }
 @Test void newModCannotConflictWithPersonalMod()throws Exception{
  var cache=new Cache(game,new Remote());jar("mods/personal.jar","personal","1.0",dependency("personal","extra","incompatible","[1,2)","CLIENT"));
  var file=cached(cache,jar("extra.jar","extra","1.0",""),"mods/extra.jar","extra");var installer=installer(cache);var m=manifest(List.of(new PackManifest.Component("extra","Extra","",true)),List.of(file));
  assertTrue(assertThrows(IOException.class,()->installer.validateMods(installer.review("server",m,Set.of("extra")))).getMessage().contains("несовместим с extra"));
  assertDoesNotThrow(()->installer.validateMods(installer.review("server",m,Set.of())));
 }
 @Test void disablingOptionalLibraryMustPreservePersonalDependencies()throws Exception{
  var cache=new Cache(game,new Remote());var lib=jar("mods/library.jar","library","1.0","");jar("mods/personal.jar","personal","1.0",dependency("personal","library","required","*","CLIENT"));own("mods/library.jar");
  var m=manifest(List.of(new PackManifest.Component("lib","Library","",true)),List.of(new PackManifest.Entry("mods/library.jar",Hashes.sha256(lib),Files.size(lib),"lib","replace")));var installer=installer(cache);
  assertDoesNotThrow(()->installer.validateMods(installer.review("server",m,Set.of("lib"))));
  assertThrows(IOException.class,()->installer.stage(installer.review("server",m,Set.of()),null,new AtomicBoolean(),s->{}));assertTrue(Files.exists(lib));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
 }
 @Test void validSwitchKeepsPersonalModsAndIgnoresServerOnlyDependencies()throws Exception{
  var cache=new Cache(game,new Remote());jar("mods/old.jar","oldmod","1.0","");own("mods/old.jar");var personal=jar("mods/personal.jar","personal","1.0",dependency("personal","server_only","required","*","SERVER"));
  byte[] before=Files.readAllBytes(personal);var installer=installer(cache);String id=installer.stage(installer.review("new-server",manifest(List.of(),List.of()),Set.of()),null,new AtomicBoolean(),s->{});new Transactions(game).apply(id);
  assertFalse(Files.exists(game.resolve("mods/old.jar")));assertArrayEquals(before,Files.readAllBytes(personal));assertEquals("new-server",Json.str(Json.read(game.resolve("rivet/state.json")),"packServer"));
 }
 @Test void rivetDependenciesWorkWithRuntimeAndWithPackagedGameModule()throws Exception{
  var cache=new Cache(game,new Remote());jar("mods/personal.jar","personal","1.0",dependency("personal","rivet","required","[1.3,2)","CLIENT"));var installer=installer(cache);
  assertDoesNotThrow(()->installer.validateMods(installer.review("server",manifest(List.of(),List.of()),Set.of())));
  Path inner=jar("game.jar","rivet","1.3.2",dependency("rivet","minecraft","required","[1.21,1.22)","CLIENT"));var mf=new Manifest();mf.getMainAttributes().putValue("Manifest-Version","1.0");mf.getMainAttributes().putValue("Automatic-Module-Name","dev.abros.rivet.bootstrap");
  try(var out=new JarOutputStream(Files.newOutputStream(game.resolve("mods/rivet.jar")),mf)){out.putNextEntry(new JarEntry("rivet/game.jar"));Files.copy(inner,out);out.closeEntry();}
  assertDoesNotThrow(()->installer.validateMods(installer.review("server",manifest(List.of(),List.of()),Set.of())));
 }
 @Test void sharedJarJarDependenciesRemainValidInProjectedSet()throws Exception{
  var cache=new Cache(game,new Remote());var fixtures=new PackJarJarTest();fixtures.root=game;var lib=fixtures.jar("v1.jar","library","1.0","",null,"");
  var a=fixtures.jar("a.jar","first","1.0",fixtures.dependency("first","library","[1,2)"),lib,"[1,2)");var b=fixtures.jar("b.jar","second","1.0","",lib,"[1,2)");
  Files.createDirectories(game.resolve("mods"));Files.copy(a,game.resolve("mods/a.jar"));Files.copy(b,game.resolve("mods/b.jar"));var installer=installer(cache);
  assertDoesNotThrow(()->installer.validateMods(installer.review("server",manifest(List.of(),List.of()),Set.of())));
 }
}
