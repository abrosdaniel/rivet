package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VoiceMutesTest {
 @TempDir Path root;
 @Test void expiresAtDeadlineAndSurvivesRestartWithoutExtendingIt()throws Exception{
  var file=root.resolve("mutes.json");var player=UUID.randomUUID();var store=new VoiceMutes(file,1000);
  store.mute(player,1,1000);assertTrue(store.muted(player,60999));assertFalse(store.muted(UUID.randomUUID(),1000));
  var reloaded=new VoiceMutes(file,30000);assertTrue(reloaded.muted(player,60999));assertFalse(reloaded.muted(player,61000));
  assertFalse(new VoiceMutes(file,61000).muted(player,61000));
 }
 @Test void existingMuteCannotBeShortenedOrExtendedByAnotherVote()throws Exception{
  var store=new VoiceMutes(root.resolve("mutes.json"),1000);var player=UUID.randomUUID();store.mute(player,2,1000);
  assertThrows(IllegalArgumentException.class,()->store.mute(player,1,2000));assertTrue(store.muted(player,120999));assertFalse(store.muted(player,121000));
 }
 @Test void failedDurableWriteDoesNotPublishUnconfirmedPunishment()throws Exception{
  var parent=root.resolve("blocked");Files.writeString(parent,"not a directory");var store=new VoiceMutes(parent.resolve("mutes.json"),1000);var player=UUID.randomUUID();
  assertThrows(java.io.IOException.class,()->store.mute(player,1,1000));assertFalse(store.muted(player,1001));
 }
 @Test void corruptStoreIsNotSilentlyReplaced()throws Exception{
  var file=root.resolve("mutes.json");Files.writeString(file,"broken");assertThrows(java.io.IOException.class,()->new VoiceMutes(file,1000));assertEquals("broken",Files.readString(file));
 }
 @Test void nextWritePrunesExpiredEntries()throws Exception{
  var file=root.resolve("mutes.json");var first=UUID.randomUUID();var second=UUID.randomUUID();var store=new VoiceMutes(file,1000);store.mute(first,1,1000);store.mute(second,1,62000);
  var players=Json.read(file).getAsJsonObject("players");assertFalse(players.has(first.toString()));assertTrue(players.has(second.toString()));
 }
}
