package dev.abros.rivet.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.CommunityLocation;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** One temporary destination shared by chat, Rivet places and the Rivet map. */
final class DirectionCue {
 private static CommunityLocation target;
 private static java.util.UUID deathTarget;
 static void clear(){target=null;deathTarget=null;bounds=new dev.abros.rivet.core.NativeLayout.Box(0,0,0,0);}
 static CommunityLocation target(){return active();}
 static void start(dev.abros.rivet.core.map.MapMarker marker){start(new CommunityLocation(marker.name(),marker.dimension(),marker.x(),marker.y(),marker.z(),false));if(target!=null&&marker.death())deathTarget=marker.id();}
 static void start(CommunityLocation place){deathTarget=null;if(ServerMenuClient.state.has("map")&&!ServerMenuClient.state.getAsJsonObject("map").get("enabled").getAsBoolean()){clear();return;}target=place;}
 static void receive(JsonObject j){
  var mc=Minecraft.getInstance();
  start(new CommunityLocation(Json.opt(j,"name",Client.text("ui.location_8e2fe025")),Json.str(j,"dimension"),j.get("x").getAsInt(),j.has("y")?j.get("y").getAsInt():mc.player==null?0:mc.player.getBlockY(),j.get("z").getAsInt(),false));
 }
 private static CommunityLocation active(){
  var mc=Minecraft.getInstance();
  if(deathTarget!=null&&WorldMapClient.markersReady()&&WorldMapClient.markers().stream().noneMatch(m->m.id().equals(deathTarget)))clear();
  if(target!=null&&mc.player!=null&&mc.level!=null&&mc.level.dimension().location().toString().equals(target.dimension())&&Math.hypot(mc.player.getX()-(target.x()+.5),mc.player.getZ()-(target.z()+.5))<=3&&Math.abs(mc.player.getY()-target.y())<=4)clear();
  return target;
 }
 static boolean hasTarget(){return active()!=null;}
 static JsonArray mapRows(){
  var rows=new JsonArray();var point=active();var s=MapSettings.INSTANCE.hud;
  if(point!=null&&s.directionEnabled&&s.directionMap){var row=new JsonObject();row.addProperty("id","active-navigation");row.addProperty("title",point.name());row.add("location",point.json());rows.add(row);}return rows;
 }
 static dev.abros.rivet.core.NativeLayout.Box bounds=new dev.abros.rivet.core.NativeLayout.Box(0,0,0,0);
 static void draw(GuiGraphics g){draw(g,false);}
 static void draw(GuiGraphics g,boolean preview){
  var mc=Minecraft.getInstance();var point=active();var settings=MapSettings.INSTANCE.hud;
  if(!preview&&(!settings.directionEnabled||point==null||mc.player==null||mc.screen!=null&&!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)&&!(mc.screen instanceof HudInteractionScreen)||mc.options.hideGui))return;if(point==null)point=new CommunityLocation(Client.text("ui.navigation_destination_4c05dc8e"),"minecraft:overworld",10,64,10,false);
  String text;
  if(preview)text=Client.text("ui.120_m_10_10_125d85ca");else if(!mc.level.dimension().location().toString().equals(point.dimension()))text=Client.text("ui.another_dimension_abb94c4d")+dev.abros.rivet.network.DimensionLabels.name(point.dimension()).getString();
  else{
   double dx=point.x()-mc.player.getX(),dz=point.z()-mc.player.getZ();
   double angle=net.minecraft.util.Mth.wrapDegrees((float)(Math.toDegrees(Math.atan2(-dx,dz))-mc.player.getYRot()));
   String arrow=Math.abs(angle)<22?"↑":angle>=22&&angle<67?"↗":angle>=67&&angle<112?"→":angle>=112&&angle<157?"↘":angle>=157||angle<=-157?"↓":angle<=-112?"↙":angle<=-67?"←":"↖";
   text=arrow+"  "+Math.round(Math.hypot(dx,dz))+Client.text("ui.m_248f3fa6")+(settings.directionCoordinates?" · "+point.x()+", "+point.z():"");
  }
  float scale=Math.min(settings.directionScale,Math.min((g.guiWidth()-8f)/64,(g.guiHeight()-8f)/32));int maxWidth=Math.max(24,(int)((g.guiWidth()-16)/scale));
  String name=UiKit.fit(mc.font,point.name(),maxWidth-32);text=UiKit.fit(mc.font,text,maxWidth-32);
  int w=Math.min(maxWidth,Math.max(mc.font.width(text),mc.font.width(name))+32),h=32;bounds=HudPlacement.fit(g.guiWidth(),g.guiHeight(),(int)Math.ceil(w*scale),(int)Math.ceil(h*scale),settings.directionAnchorX,settings.directionAnchorY,settings.directionOffsetX,settings.directionOffsetY);g.pose().pushPose();g.pose().translate(bounds.x(),bounds.y(),0);g.pose().scale(scale,scale,1);int px=0,py=0;
  // Background opacity is independent of text contrast and does not add a hidden opaque shadow.
  UiKit.surface(g,px,py,w,h,(UiKit.surface()&0xFFFFFF)|(Math.round(settings.directionOpacity*255)<<24));
  Ui.text(g,mc.font,name,px+8,py+6,UiKit.accent(),false);
  Ui.text(g,mc.font,text,px+8,py+18,UiKit.text(),false);
  if(!preview)Ui.text(g,mc.font,"×",px+w-12,py+6,UiKit.muted(),false);
  g.pose().popPose();
 }
 static boolean stopHit(double x,double y){return MapSettings.INSTANCE.hud.directionEnabled&&hasTarget()&&bounds.width()>0&&x>=bounds.right()-18*MapSettings.INSTANCE.hud.directionScale&&x<bounds.right()&&y>=bounds.y()&&y<bounds.y()+17*MapSettings.INSTANCE.hud.directionScale;}
 static boolean click(double x,double y){if(!stopHit(x,y))return false;clear();return true;}
 static void tooltip(GuiGraphics g,int x,int y){if(stopHit(x,y))g.renderTooltip(Minecraft.getInstance().font,Client.tr("ui.stop_navigation_9dbd1ec9"),x,y);}
 private DirectionCue(){}
}
