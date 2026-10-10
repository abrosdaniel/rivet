package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Native location entry replaces the removed optional map-camera adapter. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RivetWorldMapHarness {
 private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_WORLD_MAP_REVIEW")==null||done||!WorldMapClient.ready())return;done=true;var mc=Minecraft.getInstance();
  try{var parent=mc.screen;var point=new dev.abros.rivet.core.CommunityLocation("Map test",mc.level.dimension().location().toString(),mc.player.getBlockX()+16,mc.player.getBlockY(),mc.player.getBlockZ()+16,false);ClientMap.openLocation(parent,point,false);
   if(!(mc.screen instanceof WorldMapScreen map)||!map.selectedDimension().equals(point.dimension())||Math.abs(map.view.worldX(map.width/2d,map.width/2d)-point.x()-.5)>.001)throw new AssertionError("Native camera target mismatch");mc.screen.onClose();int before=WorldMapClient.markers().size();ClientMap.openLocation(parent,point,true);if(!(mc.screen instanceof WorldMapScreen editor)||!editor.markerOpen()||editor.draftMarker()==null)throw new AssertionError("Missing personal marker draft");mc.screen.onClose();if(WorldMapClient.markers().size()!=before)throw new AssertionError("Draft saved without confirmation");if(mc.screen!=parent)throw new AssertionError("Lost parent screen");checkLayers(point);System.out.println("RIVET_WORLD_MAP_REVIEW_OK native open/camera/restore, shared places/peers/expiry/filtering/storage");
  }catch(Throwable failure){System.out.println("RIVET_WORLD_MAP_REVIEW_FAILED");failure.printStackTrace();}
 }

 private static void checkLayers(dev.abros.rivet.core.CommunityLocation point)throws Exception{
  String[] names={"places","peers","showPlaces","showPeers","snapshot","peersExpire"};var saved=new java.util.HashMap<String,Object>();
  for(String name:names)saved.put(name,field(name).get(null));
  int markers=WorldMapClient.markers().size();
  try{
   var places=new com.google.gson.JsonArray();var place=new com.google.gson.JsonObject();place.addProperty("id","native-cutover-test");place.addProperty("title","Shared place");place.addProperty("status","open");var location=new com.google.gson.JsonObject();location.addProperty("dimension",point.dimension());location.addProperty("x",point.x());location.addProperty("y",point.y());location.addProperty("z",point.z());place.add("location",location);places.add(place);
   var peers=new com.google.gson.JsonArray();var peer=location.deepCopy();peer.addProperty("uuid",Minecraft.getInstance().player.getUUID().toString());peer.addProperty("name","Shared player");peers.add(peer);
   field("places").set(null,places);field("peers").set(null,peers);field("showPlaces").set(null,true);field("showPeers").set(null,true);field("peersExpire").set(null,System.currentTimeMillis()+7000);field("snapshot").set(null,null);
   if(MapLayerClient.points().size()!=2)throw new AssertionError("Native shared points absent");
   field("peersExpire").set(null,0L);if(MapLayerClient.points().size()!=1)throw new AssertionError("Expired position retained");
   place.addProperty("status","closed");field("snapshot").set(null,null);if(!MapLayerClient.points().isEmpty())throw new AssertionError("Closed place visible");
   place.addProperty("status","open");field("showPlaces").set(null,false);field("snapshot").set(null,null);if(!MapLayerClient.points().isEmpty())throw new AssertionError("Disabled places visible");
   if(WorldMapClient.markers().size()!=markers)throw new AssertionError("Shared points persisted as personal markers");
   if(ClientCompatibilityRegistry.status().toLowerCase().contains("xaero"))throw new AssertionError("Removed adapter registered");
  }finally{for(String name:names)field(name).set(null,saved.get(name));}
 }
 private static java.lang.reflect.Field field(String name)throws Exception{var f=MapLayerClient.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
