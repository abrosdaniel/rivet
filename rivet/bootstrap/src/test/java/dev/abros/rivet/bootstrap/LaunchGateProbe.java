package dev.abros.rivet.bootstrap;
import java.nio.file.Path;
/** Fresh JVM: catches OS start-time rounding that an in-process test cannot model. */
public final class LaunchGateProbe {
 public static void main(String[] args)throws Exception{
  var game=Path.of(args[0]);var gate=new LaunchGate();
  System.out.println("epoch="+java.nio.file.Files.getLastModifiedTime(game.resolve("rivet/mutation.epoch"))+" os="+ProcessHandle.current().info().startInstant()+" jvm="+java.time.Instant.ofEpochMilli(java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime()));
  try{gate.enter(game);}finally{gate.close();}
 }
}
