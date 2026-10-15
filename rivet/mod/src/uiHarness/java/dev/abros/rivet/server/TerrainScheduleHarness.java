package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.TerrainJournal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Two actual JVMs: first halts without shutdown hooks, second resumes durable checkpoints. */
@EventBusSubscriber(modid="rivet")
public final class TerrainScheduleHarness {
 private static ExecutorService worker;private static Future<?> check;private static long deadline;
 private static String phase(){return System.getenv("RIVET_TERRAIN_SCHEDULE_PHASE");}
 @SubscribeEvent public static void start(ServerStartedEvent event)throws Exception{
  if(phase()==null||phase().isEmpty())return;var server=event.getServer();var level=server.overworld();Path root=server.getWorldPath(LevelResource.ROOT).resolve("rivet-terrain"),ids=root.resolve("harness-ids.txt");deadline=System.currentTimeMillis()+60000;worker=Executors.newSingleThreadExecutor();
  check=worker.submit(()->{try{var api=RivetApi.connect("rivet").orElseThrow().terrain();
   if(phase().equals("1")){
    for(int x:new int[]{-65,64,65})level.getChunkSource().chunkMap.write(new ChunkPos(x,64),SavedTerrainHarness.fixture(level,x,Blocks.GOLD_BLOCK,"minecraft:full")).get();
    var first=api.renderSaved().get();var cancelled=api.renderSaved().get();var queued=api.render(new TerrainApi.Region("minecraft:overworld",64,64,65,64)).get();api.cancel(cancelled.id()).get();
    if(!api.job(cancelled.id()).get().state().equals("CANCELLED"))throw new IllegalStateException("Queued cancellation failed");
    ServerTerrain.changed(level,64,64);
    while(System.currentTimeMillis()<deadline){Thread.sleep(20);try(var journal=new TerrainJournal(root)){var state=journal.load();var job=state.jobs().stream().filter(j->j.id().equals(first.id())).findFirst().orElseThrow();if(job.rendered()>0&&!job.state().equals("COMPLETE")&&job.cursor()!=null&&!state.dirty().isEmpty()){
      Files.writeString(ids,first.id()+"\n"+cancelled.id()+"\n"+queued.id()+"\n"+job.rendered());System.out.println("RIVET_TERRAIN_CRASH_CHECKPOINT_OK");System.out.flush();Runtime.getRuntime().halt(0);
     }}}
    throw new IllegalStateException("No resumable checkpoint reached");
   }
   var lines=Files.readAllLines(ids);UUID first=UUID.fromString(lines.get(0)),cancelled=UUID.fromString(lines.get(1)),queued=UUID.fromString(lines.get(2));int before=Integer.parseInt(lines.get(3));if(api.job(first).get().rendered()<before)throw new IllegalStateException("Checkpoint progress lost");
   TerrainApi.Job result;do{Thread.sleep(50);result=api.job(first).get();}while(!Set.of("COMPLETE","FAILED").contains(result.state())&&System.currentTimeMillis()<deadline);
   if(!result.state().equals("COMPLETE")||result.rendered()!=3)throw new IllegalStateException("Saved job did not resume exactly: "+result);
   do{Thread.sleep(50);result=api.job(queued).get();}while(!Set.of("COMPLETE","FAILED").contains(result.state())&&System.currentTimeMillis()<deadline);
   if(!result.state().equals("COMPLETE")||result.skipped()!=2||!api.job(cancelled).get().state().equals("CANCELLED"))throw new IllegalStateException("Queue/cancellation recovery failed");
   boolean drained=false;while(System.currentTimeMillis()<deadline){Thread.sleep(100);try(var journal=new TerrainJournal(root)){if(journal.load().dirty().isEmpty()){drained=true;break;}}}if(!drained)throw new IllegalStateException("Recovered changes not drained");
   for(int x:new int[]{-65,64,65})if(level.getChunkSource().getChunkNow(x,64)!=null)throw new IllegalStateException("Recovery loaded chunks");
   if(api.jobs().get().stream().noneMatch(j->j.id().equals(first)))throw new IllegalStateException("Recovered jobs not discoverable");
   server.submit(()->{try{ServerTerrain.stop();Path file=root.resolve("schedule.json");byte[] original=Files.readAllBytes(file);Files.writeString(file,"{broken");boolean rejected=false;try{ServerTerrain.start(server);}catch(Exception expected){rejected=true;}finally{ServerTerrain.stop();}if(!rejected||!Files.readString(file).equals("{broken"))throw new IllegalStateException("Failed startup overwrote damaged journal");Files.write(file,original);ServerTerrain.start(server);return null;}catch(Exception failure){throw new CompletionException(failure);}}).get(10,TimeUnit.SECONDS);
   System.out.println("RIVET_TERRAIN_JOURNAL_GUARD_OK");
   System.out.println("RIVET_TERRAIN_RECOVERY_OK cursor=true queue=true cancelled=true dirty=true noChunkLoads=true");
  }catch(Exception failure){throw new RuntimeException(failure);}});
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)throws Exception{if(check==null)return;if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Scheduler harness timeout");if(check.isDone()){check.get();check=null;worker.shutdown();event.getServer().halt(false);}}
 @SubscribeEvent public static void stop(ServerStoppedEvent event){if(worker!=null)worker.shutdownNow();}
}
