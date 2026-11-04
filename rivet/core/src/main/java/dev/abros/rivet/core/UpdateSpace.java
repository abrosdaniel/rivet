package dev.abros.rivet.core;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
/** Conservative peak: downloads, preimages, installed growth and a replacement/rollback temporary file. */
public final class UpdateSpace {
 private UpdateSpace(){}
 public static long required(Path game,Planner.Plan plan,Map<String,Long> sizes,long downloads)throws IOException{
  try{
   long backups=0,growth=0,temporary=0;
   for(var change:plan.changes()){
    Path path=SafePaths.resolve(game,change.path());long before=Files.isRegularFile(path)?Files.size(path):0;
    Long target=change.after()==null?Long.valueOf(0):sizes.get(change.after());if(target==null||target<0)throw new IOException("Missing update object size");
    backups=Math.addExact(backups,before);growth=Math.addExact(growth,Math.max(0,target-before));temporary=Math.max(temporary,Math.max(before,target));
   }
   return Math.addExact(16L*1024*1024,Math.addExact(downloads,Math.addExact(backups,Math.addExact(growth,temporary))));
  }catch(ArithmeticException overflow){throw new IOException("Update size overflow",overflow);}
 }
 public static void check(Path game,Planner.Plan plan,Map<String,Long> sizes,long downloads)throws IOException{
  long required=required(game,plan,sizes,downloads);if(Files.getFileStore(game).getUsableSpace()<required)throw new IOException("INSUFFICIENT_DISK_SPACE");
 }
}
