package dev.abros.rivet.server;

import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Simulates abandoned prior-lifetime gates before normal startup, without changing server data. */
@EventBusSubscriber(modid="rivet")
public final class ServerLifecycleAuditHarness {
 private static final Map<String,AtomicBoolean> old=new LinkedHashMap<>();
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(ServerStartingEvent event)throws Exception{
  if(System.getenv("RIVET_LIFECYCLE_AUDIT")==null)return;
  for(String name:List.of("delivering","refreshing")){var field=ServerFeatures.class.getDeclaredField(name);field.setAccessible(true);var gate=(AtomicBoolean)field.get(null);gate.set(true);old.put(name,gate);}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(ServerStartingEvent event)throws Exception{
  if(old.isEmpty())return;
  for(var entry:old.entrySet()){
   var field=ServerFeatures.class.getDeclaredField(entry.getKey());field.setAccessible(true);var gate=(AtomicBoolean)field.get(null);
   if(gate==entry.getValue()||gate.get())throw new IllegalStateException("Stale work blocks startup: "+entry.getKey());
   gate.set(true);entry.getValue().set(false);if(!gate.get())throw new IllegalStateException("Old completion cleared current work");gate.set(false);
  }
  System.out.println("RIVET_LIFECYCLE_AUDIT_OK gates="+old.size());old.clear();
 }
}
