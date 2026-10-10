package dev.abros.rivet.client;

import java.util.*;
import dev.abros.rivet.core.CommunityLocation;
import dev.abros.rivet.network.WaystonesWire;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Atomic, ephemeral server snapshots, isolated from the player's saved markers. */
final class MapWaystones {
 private static Object connection;private static int sequence,parts;private static long next,expires;
 private static final Map<Integer,List<WaystonesWire.Stone>> pending=new HashMap<>();
 private static List<MapLayerClient.Point> points=List.of();
 static boolean nativeIcon(){return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.parse("waystones:waystone"));}
 static void drawIcon(net.minecraft.client.gui.GuiGraphics g,int x,int y,int size){
  g.pose().pushPose();try{g.pose().translate(x-size/2f,y-size/2f,0);g.pose().scale(size/16f,size/16f,1);
   if(nativeIcon())g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("waystones:waystone"))),0,0);
   else UiIcons.draw(g,MapGlyphs.icon("waystone"),0,0,UiKit.accent());
  }finally{g.pose().popPose();}
 }
 static void install(){WaystonesWire.client=MapWaystones::receive;}
 static void reset(){sequence++;pending.clear();parts=0;points=List.of();next=expires=0;}
 static void tick(){
  var mc=Minecraft.getInstance();var c=mc.getConnection();if(connection!=c){connection=c;reset();}
  if(c==null||mc.level==null||!WorldMapClient.allowed()||!c.hasChannel(WaystonesWire.Request.TYPE)){reset();return;}
  long now=System.currentTimeMillis();if(now>expires)points=List.of();
  if(now>=next){next=now+2000;sequence++;parts=0;pending.clear();PacketDistributor.sendToServer(new WaystonesWire.Request(sequence));}
 }
 static void receive(WaystonesWire.Snapshot snapshot){
  if(snapshot.sequence()!=sequence||!WorldMapClient.allowed())return;
  if(parts!=0&&parts!=snapshot.parts()){pending.clear();return;}parts=snapshot.parts();pending.put(snapshot.part(),snapshot.stones());
  if(pending.size()!=parts)return;
  var result=new LinkedHashMap<String,MapLayerClient.Point>();
  try{for(int i=0;i<parts;i++)for(var s:pending.get(i)){
   var location=CommunityLocation.read(new CommunityLocation(s.name(),s.dimension(),s.x(),s.y(),s.z(),false).json());
   result.put(s.id().toString(),new MapLayerClient.Point(s.id().toString(),location,false,true));
  }}catch(RuntimeException invalid){pending.clear();return;}
  points=List.copyOf(result.values());expires=System.currentTimeMillis()+7000;pending.clear();
 }
 static List<MapLayerClient.Point> points(){return WorldMapClient.allowed()&&System.currentTimeMillis()<=expires?points:List.of();}
 private MapWaystones(){}
}
