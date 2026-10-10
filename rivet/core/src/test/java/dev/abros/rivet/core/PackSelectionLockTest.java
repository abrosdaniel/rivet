package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.pack.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PackSelectionLockTest {
 @TempDir Path game;
 private Planner.Plan empty(){return new Planner.Plan(UUID.randomUUID().toString(),"pack",List.of(),Map.of(),Set.of(),List.of(),0);}
 @Test void repeatedSelectionAndServerSwitchWorkWhileMinecraftHoldsLock()throws Exception{
  var installer=new PackInstaller(game,new Cache(game,null));
  var manifest=new PackManifest("1.21.1","21.1.250",List.of(new PackManifest.Component("extra","Extra","",true)),List.of());
  try(var channel=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);var lock=channel.lock(0,Long.MAX_VALUE,true)){
   installer.saveSelection(installer.review("first",manifest,Set.of("extra")));
   installer.saveSelection(installer.review("first",manifest,Set.of()));
   assertEquals(Set.of(),installer.choices("first",manifest));
   installer.saveSelection(installer.review("second",manifest,Set.of("extra")));
   assertEquals("second",Json.str(Json.read(game.resolve("rivet/state.json")),"packServer"));
   assertEquals(manifest.hash(),installer.installedHash());
   assertFalse(Files.exists(game.resolve("rivet/pending.json")));
   assertFalse(Files.exists(game.resolve("rivet/transactions")));
   assertFalse(Files.exists(game.resolve("rivet/mutation.epoch")));
  }
 }
 @Test void metadataCommitRejectsStaleStateAndPendingTransaction()throws Exception{
  var tx=new Transactions(game);var next=new JsonObject();next.addProperty("value",1);
  tx.commitState(empty(),new JsonObject(),next);
  assertThrows(IOException.class,()->tx.commitState(empty(),new JsonObject(),new JsonObject()));
  var plan=empty();tx.prepare(plan,new byte[0],next);
  assertThrows(IOException.class,()->tx.commitState(empty(),next,new JsonObject()));
  assertEquals(next,Json.read(game.resolve("rivet/state.json")));
  assertEquals(plan.id(),Json.str(Json.read(game.resolve("rivet/pending.json")),"id"));
 }
 @Test void fileChangesCannotUseMetadataPath()throws Exception{
  var tx=new Transactions(game);
  var plan=new Planner.Plan(UUID.randomUUID().toString(),"pack",List.of(new Planner.Change("mods/a.jar",null,"a".repeat(64))),Map.of(),Set.of(),List.of(),0);
  assertThrows(IOException.class,()->tx.commitState(plan,new JsonObject(),new JsonObject()));
  assertFalse(Files.exists(game.resolve("rivet/state.json")));
 }
}
