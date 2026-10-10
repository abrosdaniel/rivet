package dev.abros.rivet.bootstrap;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.nio.channels.*;
class LaunchGateTest {
 @TempDir Path game;
 @Test void failedLockClosesChannelAndCanBeRetried()throws Exception{
  Files.createDirectories(game.resolve("rivet"));var gate=new LaunchGate();
  try(var c=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var l=c.lock()){
   assertThrows(java.io.IOException.class,()->gate.enter(game));
   var field=LaunchGate.class.getDeclaredField("channel");field.setAccessible(true);
   var opened=(FileChannel)field.get(gate);assertTrue(opened==null||!opened.isOpen());
  }finally{gate.close();}
  gate.enter(game);gate.close();gate.close();
 }
 @Test void repeatedEnterDoesNotLoseOriginalLock()throws Exception{
  var gate=new LaunchGate();gate.enter(game);
  try{assertThrows(IllegalStateException.class,()->gate.enter(game));}finally{gate.close();}
  try(var c=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.WRITE);var lock=c.tryLock()){assertNotNull(lock);}
 }
 @Test void pendingStopsLaunchBeforeDiscovery()throws Exception{Files.createDirectories(game.resolve("rivet"));Files.writeString(game.resolve("rivet/pending.json"),"{}");assertThrows(java.io.IOException.class,()->new LaunchGate().enter(game));}
 @Test void runningGamePreventsUpdaterExclusiveLock()throws Exception{var gate=new LaunchGate();gate.enter(game);try(var c=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.WRITE)){assertThrows(OverlappingFileLockException.class,()->c.tryLock());}finally{gate.close();}}
 @Test void updaterStopsNewGame()throws Exception{Files.createDirectories(game.resolve("rivet"));try(var c=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var l=c.lock()){assertThrows(Exception.class,()->new LaunchGate().enter(game));}}
}
