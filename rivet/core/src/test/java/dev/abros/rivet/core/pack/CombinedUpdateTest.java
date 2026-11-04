package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;
class CombinedUpdateTest {
 @TempDir Path game;
 Path jar(String path,String id,String version,String dependency)throws Exception{
  Path file=game.resolve(path);Files.createDirectories(file.getParent());try(var out=new JarOutputStream(Files.newOutputStream(file))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(("[[mods]]\nmodId='"+id+"'\nversion='"+version+"'\n"+dependency).getBytes());out.closeEntry();}return file;
 }
 private record Fixture(Path old,Path target,PackInstaller installer,PackInstaller.Review review){}
 Fixture fixture()throws Exception{
  var cache=new Cache(game,new Remote());var old=jar("mods/rivet-old.jar","rivet","2.5.2","");
  var next=jar("fixtures/new.jar","rivet","2.5.3","");String hash=Hashes.sha256(next);Files.createDirectories(cache.path(hash).getParent());Files.copy(next,cache.path(hash));
  String path="mods/rivet-2.5.3-mc1.21.1-neoforge.jar";var update=new CoreUpdater.Update("2.5.3",new Cache.Artifact(path,List.of(CoreUpdater.REPOSITORY+"/releases/download/v2.5.3/rivet-2.5.3-mc1.21.1-neoforge.jar"),hash,Files.size(next)),true);
  var addon=jar("fixtures/addon.jar","addon","1.0.0","[[dependencies.addon]]\nmodId='rivet'\ntype='required'\nversionRange='[2.5.3,3)'\nside='CLIENT'\n");String addonHash=Hashes.sha256(addon);Files.createDirectories(cache.path(addonHash).getParent());Files.copy(addon,cache.path(addonHash));
  var manifest=new PackManifest("1.21.1","21.1.250",List.of(),List.of(new PackManifest.Entry("mods/addon.jar",addonHash,Files.size(addon),"","replace")));
  var installer=new PackInstaller(game,cache,"2.5.2","21.1.250");var base=installer.review("server",manifest,Set.of());assertThrows(java.io.IOException.class,()->installer.validateMods(base));
  return new Fixture(old,game.resolve(path),installer,installer.withCore(base,old,update));
 }
 @Test void appliesPackAndCoreTogetherAfterCheckingTargetVersion()throws Exception{
  var f=fixture();String id=f.installer.stage(f.review,null,new AtomicBoolean(),s->{});assertTrue(Files.exists(f.old));assertFalse(Files.exists(f.target));assertFalse(Files.exists(game.resolve("mods/addon.jar")));
  assertEquals(id,Json.str(Json.read(game.resolve("rivet/pending.json")),"id"));new Transactions(game).apply(id);
  assertFalse(Files.exists(f.old));assertTrue(Files.exists(f.target));assertTrue(Files.exists(game.resolve("mods/addon.jar")));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
  var state=Json.read(game.resolve("rivet/state.json"));assertEquals("2.5.3",Json.str(state,"coreVersion"));assertEquals(f.review.manifest().hash(),Json.str(state,"packHash"));assertFalse(state.getAsJsonObject("ownership").has("mods/rivet-2.5.3-mc1.21.1-neoforge.jar"));
 }
 @Test void interruptedCombinedApplyRollsBackBothParts()throws Exception{
  var f=fixture();String before=Hashes.sha256(f.old);String id=f.installer.stage(f.review,null,new AtomicBoolean(),s->{});
  System.setProperty("rivet.test.failAfter","2");try{assertThrows(java.io.IOException.class,()->new Transactions(game).apply(id));}finally{System.clearProperty("rivet.test.failAfter");}
  assertEquals(before,Hashes.sha256(f.old));assertFalse(Files.exists(f.target));assertFalse(Files.exists(game.resolve("mods/addon.jar")));assertFalse(Files.exists(game.resolve("rivet/state.json"))&&Json.read(game.resolve("rivet/state.json")).has("coreVersion"));
 }
 @Test void cancelledCombinedDownloadDoesNotPrepareOrChangeInstalledFiles()throws Exception{
  var f=fixture();assertThrows(java.io.IOException.class,()->f.installer.stage(f.review,null,new AtomicBoolean(true),s->{}));assertTrue(Files.exists(f.old));assertFalse(Files.exists(f.target));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
 }
 @Test void stateGuardRejectsConcurrentSelectionChange()throws Exception{
  var f=fixture();var changed=new JsonObject();changed.addProperty("otherSelection",true);Json.write(game.resolve("rivet/state.json"),changed);
  assertThrows(java.io.IOException.class,()->new Transactions(game).prepare(f.review.plan(),f.review.manifest().bytes(),new JsonObject(),f.review.state()));
  assertEquals(changed,Json.read(game.resolve("rivet/state.json")));assertFalse(Files.exists(game.resolve("rivet/pending.json")));
 }
}
