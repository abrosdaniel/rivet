package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class WaystonesClientHarness {
 private static final String ADDRESS=System.getenv("RIVET_WAYSTONES_ADDRESS");private static boolean connected,done;private static int stage,before,frames;private static WorldMapScreen map;private static long deadline;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+120000;mc.options.pauseOnLostFocus=false;var data=new ServerData("Waystones test",ADDRESS,ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),data,false,null);}
   if(connected&&System.currentTimeMillis()>deadline)throw new AssertionError("Timeout stage "+stage);
   if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
   if(System.getenv("RIVET_WAYSTONES_ABSENT")!=null){
    if((long)field("expires").get(null)<=System.currentTimeMillis())return;
    check(MapWaystones.points().isEmpty()&&!MapWaystones.nativeIcon(),"Absent mod has waystones");
    System.out.println("RIVET_WAYSTONES_ABSENT_OK native map + empty server snapshot");done=true;mc.stop();return;
   }
   if(stage==1&&frames<120){
    int f=frames++;if(f%40==0){mc.options.guiScale().set(f/40+1);mc.resizeDisplay();mc.setScreen(map);}
    if(f%40==15)capture("world-"+mc.options.guiScale().get());
    if(f%40==20)mc.setScreen(null);
    if(f%40==35)capture("minimap-"+mc.options.guiScale().get());
   }
   var points=MapWaystones.points();check(points.stream().noneMatch(p->p.location().name().contains("Hidden")),"Hidden stone reached client");
   if(stage==0&&points.size()==3){before=WorldMapClient.markers().size();verifySnapshots();check(MapWaystones.nativeIcon(),"Native Waystones icon absent");map=new WorldMapScreen(null);mc.setScreen(map);var point=points.stream().filter(p->p.location().name().equals("Rivet Global")).findFirst().orElseThrow();map.focusLocation(point.location());
    check(MapSharedOverlay.clickWorld(map,map.selectedDimension(),map.view,map.width/2d,map.height/2d,1),"Waystone context unavailable");
    check(mc.screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&(b.getMessage().getString().contains("Телепорт")||b.getMessage().getString().contains("Сохранить"))),"Waystone offers mutation/teleport");
    mc.screen.onClose();stage=1;System.out.println("RIVET_WAYSTONES_CLIENT_PHASE1_OK");
   }else if(stage==1&&points.size()==2&&points.stream().anyMatch(p->p.location().name().equals("Rivet Renamed"))){stage=2;System.out.println("RIVET_WAYSTONES_CLIENT_PHASE2_OK");}
   else if(stage==2&&points.size()==1&&points.getFirst().location().name().equals("Rivet Nether")){stage=3;System.out.println("RIVET_WAYSTONES_CLIENT_PRIVATE_OK");}
   else if(stage==3&&points.size()==2){stage=4;System.out.println("RIVET_WAYSTONES_CLIENT_PUBLIC_OK");}
   else if(stage==4&&points.isEmpty()){check(WorldMapClient.markers().size()==before,"Waystones persisted as personal markers");System.out.println("RIVET_WAYSTONES_CLIENT_OK real server sync/rename/removal/context/no persistence");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_WAYSTONES_CLIENT_FAILED");failure.printStackTrace();mc.stop();}
 }

 private static void capture(String name){var path=System.getenv("RIVET_WAYSTONES_SCREENSHOTS");if(path==null)return;var folder=new java.io.File(path);folder.mkdirs();UiCaptureHarness.grab(folder,name+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}
 private static java.lang.reflect.Field field(String name)throws Exception{var f=MapWaystones.class.getDeclaredField(name);f.setAccessible(true);return f;}
 @SuppressWarnings("unchecked") private static void verifySnapshots()throws Exception{
  String[] keys={"sequence","parts","next","expires","points"};var saved=new java.util.HashMap<String,Object>();for(String key:keys)saved.put(key,field(key).get(null));
  var pending=(java.util.Map<Integer,java.util.List<dev.abros.rivet.network.WaystonesWire.Stone>>)field("pending").get(null);var oldPending=new java.util.HashMap<>(pending);
  var policy=ServerMenuClient.state.getAsJsonObject("map");boolean enabled=policy.get("enabled").getAsBoolean();
  try{
   int sequence=(int)saved.get("sequence")+100;field("sequence").set(null,sequence);field("parts").set(null,0);pending.clear();
   var a=new dev.abros.rivet.network.WaystonesWire.Stone(java.util.UUID.randomUUID(),"Packet A","minecraft:overworld",0,64,0);var b=new dev.abros.rivet.network.WaystonesWire.Stone(java.util.UUID.randomUUID(),"Packet B","minecraft:the_nether",1,64,1);
   var first=new dev.abros.rivet.network.WaystonesWire.Snapshot(sequence,1,2,java.util.List.of(b));
   var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{dev.abros.rivet.network.WaystonesWire.Snapshot.CODEC.encode(buffer,first);check(dev.abros.rivet.network.WaystonesWire.Snapshot.CODEC.decode(buffer).equals(first),"Wire round trip failed");}finally{buffer.release();}
   MapWaystones.receive(first);check(MapWaystones.points().size()==3,"Partial snapshot replaced complete view");
   MapWaystones.receive(new dev.abros.rivet.network.WaystonesWire.Snapshot(sequence,0,2,java.util.List.of(a)));check(MapWaystones.points().size()==2,"Snapshot failed assembly");
   MapWaystones.receive(new dev.abros.rivet.network.WaystonesWire.Snapshot(sequence-1,0,1,java.util.List.of()));check(MapWaystones.points().size()==2,"Stale snapshot applied");
   policy.addProperty("enabled",false);check(MapWaystones.points().isEmpty(),"Disabled module exposes points");policy.addProperty("enabled",enabled);
   field("expires").set(null,0L);check(MapWaystones.points().isEmpty(),"Expired snapshot visible");
   MapWaystones.reset();check(MapWaystones.points().isEmpty(),"Session reset retained points");
   System.out.println("RIVET_WAYSTONES_SNAPSHOT_OK codec/atomic/stale/policy/expiry/reset");
  }finally{for(String key:keys)field(key).set(null,saved.get(key));pending.clear();pending.putAll(oldPending);policy.addProperty("enabled",enabled);}
 }
}
