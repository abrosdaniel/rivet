package dev.abros.rivet.compattests;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import dev.abros.rivet.core.modules.ModuleRuntime;
import java.lang.reflect.*;
import java.util.concurrent.ExecutorService;

/** Runs only in the isolated developer server. Never included in the release JAR. */
@EventBusSubscriber(modid="rivet_compat_tests")
public final class ModuleLifecycleHarness {
 private static final java.util.List<ExecutorService> workers=new java.util.ArrayList<>();
 private static int ticks;
 private static boolean enabled(){return System.getenv("RIVET_MODULE_SMOKE")!=null;}
 private static Object field(String owner,String name)throws Exception{var f=Class.forName("dev.abros.rivet.server."+owner).getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
  if(!enabled())return;
  var runtime=(ModuleRuntime<?>)field("ServerModules","runtime");
  check(runtime.active().containsAll(java.util.Set.of("base","social","statistics","votes","display")),"Required module missing");
  check(field("ServerFeatures","community")!=null,"Community store missing");
  check(field("ServerPlayerStatistics","store")!=null,"Statistics store missing");
  if("disabled".equals(System.getenv("RIVET_MODULE_SMOKE"))){
   check(!runtime.active().contains("chat")&&!runtime.active().contains("auth"),"Disabled module was started");
   check(field("ServerSkins","store")==null&&field("ServerSkins","WORK")==null,"Disabled skins allocated resources");
   check(field("AuthServer","WORK")==null,"Disabled auth allocated workers");
  }else{
   check(runtime.active().contains("chat"),"Chat did not start");
   check(field("ServerSkins","store")!=null,"Skin store missing");
  }
  if("base".equals(System.getenv("RIVET_MODULE_SMOKE")))check(runtime.active().contains("auth")&&field("AuthServer","WORK")!=null,"Base auth did not allocate its workers");
  for(String[] row:new String[][]{{"ServerFeatures","STORAGE"},{"ServerFeatures","CONTROL"},{"ServerFeatures","READS"},{"ServerSkins","WORK"},{"ServerSkins","FALLBACK"},{"AuthServer","WORK"},{"AuthServer","OFFICIAL"},{"AuthServer","DEADLINES"},{"AuthServer","VALIDATION"},{"ServerPlayerStatistics","writer"}}){var worker=field(row[0],row[1]);if(worker instanceof ExecutorService service)workers.add(service);}
  System.out.println("RIVET_MODULE_START_OK "+runtime.active());
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(enabled()&&++ticks==20)event.getServer().halt(false);}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void stopped(ServerStoppedEvent event)throws Exception{
  if(!enabled())return;
  check(((ModuleRuntime<?>)field("ServerModules","runtime")).active().isEmpty(),"Module survives server shutdown");
  for(String[] row:new String[][]{{"ServerFeatures","STORAGE"},{"ServerFeatures","CONTROL"},{"ServerFeatures","READS"},{"ServerSkins","WORK"},{"ServerSkins","FALLBACK"},{"AuthServer","WORK"},{"AuthServer","OFFICIAL"},{"AuthServer","DEADLINES"},{"AuthServer","VALIDATION"},{"ServerPlayerStatistics","writer"}})
   check(field(row[0],row[1])==null,"Worker retained: "+row[0]+"."+row[1]);
  check(field("ServerFeatures","community")==null&&field("ServerPlayerStatistics","store")==null&&field("ServerSkins","store")==null,"Database-backed feature retained");
  check(workers.stream().allMatch(ExecutorService::isTerminated),"Worker threads survived shutdown");workers.clear();
  System.out.println("RIVET_MODULE_STOP_OK");
 }
}
