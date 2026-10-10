package dev.abros.rivet.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Read-only check of both actual server endpoints, including negotiated capability and reply correlation. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapActivitiesWireHarness {
 private static boolean done;private static long started;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_ACTIVITIES_WIRE")==null||done||!WorldMapClient.ready())return;
  if(started==0)started=System.currentTimeMillis();
  try{
   if(ServerMenuClient.previewTransport!=null)throw new IllegalStateException("Wire check must use a real server");
   boolean ready=MapActivities.available("tasks")&&MapActivities.available("events")&&MapActivities.available("groupmarkers")&&MapGroupClient.available();var groupExpiry=MapGroupClient.class.getDeclaredField("expires");groupExpiry.setAccessible(true);ready&=groupExpiry.getLong(null)>System.currentTimeMillis();
   var f=MapActivities.class.getDeclaredField("SOURCES");f.setAccessible(true);
   for(var source:(java.util.List<?>)f.get(null)){var expires=source.getClass().getDeclaredField("expires");expires.setAccessible(true);ready&=expires.getLong(source)>System.currentTimeMillis();}
   if(ready){done=true;System.out.println("RIVET_ACTIVITIES_WIRE_OK tasks/events/groupmarkers/territories server snapshots received");}
   else if(System.currentTimeMillis()-started>30000)throw new IllegalStateException("Actual map snapshots unavailable");
  }catch(Exception ex){done=true;System.out.println("RIVET_ACTIVITIES_WIRE_FAILED");ex.printStackTrace();}
 }
}
