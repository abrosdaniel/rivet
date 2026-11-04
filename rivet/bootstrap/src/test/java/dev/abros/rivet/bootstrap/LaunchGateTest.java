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
 @Test void updateCompletedBeforeJvmStartsAllowsImmediateLaunch()throws Exception{
  Files.createDirectories(game.resolve("rivet"));
  String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
  String classes=Path.of(LaunchGate.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  String tests=Path.of(LaunchGateProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  for(int attempt=0;attempt<20;attempt++){
   Files.writeString(game.resolve("rivet/mutation.epoch"),"committed-"+attempt);
   var log=game.resolve("launch-"+attempt+".log");
   var process=new ProcessBuilder(executable,"-cp",classes+java.io.File.pathSeparator+tests,LaunchGateProbe.class.getName(),game.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
   try{assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS),"Child JVM timed out");assertEquals(0,process.exitValue(),Files.readString(log));}
   finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
  }
 }
 @Test void updateAfterJvmStartsStillRejectsAndReleasesLock()throws Exception{
  Files.createDirectories(game.resolve("rivet"));Files.writeString(game.resolve("rivet/mutation.epoch"),"committed-during-launch");
  var gate=new LaunchGate();var failure=assertThrows(java.io.IOException.class,()->gate.enter(game));
  assertTrue(failure.getMessage().contains("while this launch was starting"));
  try(var c=FileChannel.open(game.resolve("rivet/apply.lock"),StandardOpenOption.WRITE);var l=c.tryLock()){assertNotNull(l);}
 }
}
