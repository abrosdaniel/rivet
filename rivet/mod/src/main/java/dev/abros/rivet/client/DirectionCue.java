package dev.abros.rivet.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.CommunityLocation;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** One temporary destination shared by chat, Rivet places and the optional Xaero layer. */
final class DirectionCue {
 private static CommunityLocation target;
 static void clear(){target=null;bounds=new dev.abros.rivet.core.NativeLayout.Box(0,0,0,0);}
 static void start(CommunityLocation place){target=place;}
 static void receive(JsonObject j){
  var mc=Minecraft.getInstance();
  start(new CommunityLocation(Json.opt(j,"name","Место"),Json.str(j,"dimension"),j.get("x").getAsInt(),j.has("y")?j.get("y").getAsInt():mc.player==null?0:mc.player.getBlockY(),j.get("z").getAsInt(),false));
 }
 private static CommunityLocation active(){
  var mc=Minecraft.getInstance();
  if(target!=null&&mc.player!=null&&mc.level!=null&&mc.level.dimension().location().toString().equals(target.dimension())&&Math.hypot(mc.player.getX()-(target.x()+.5),mc.player.getZ()-(target.z()+.5))<=3&&Math.abs(mc.player.getY()-target.y())<=4)clear();
  return target;
 }
 static boolean hasTarget(){return active()!=null;}
 static JsonArray mapRows(){
  var rows=new JsonArray();var point=active();var s=HudSettings.INSTANCE;
  if(point!=null&&s.directionEnabled&&s.directionXaero){var row=new JsonObject();row.addProperty("id","active-navigation");row.addProperty("title",point.name());row.add("location",point.json());rows.add(row);}return rows;
 }
 static dev.abros.rivet.core.NativeLayout.Box bounds=new dev.abros.rivet.core.NativeLayout.Box(0,0,0,0);
 static void draw(GuiGraphics g){draw(g,false);}
 static void draw(GuiGraphics g,boolean preview){
  var mc=Minecraft.getInstance();var point=active();var settings=HudSettings.INSTANCE;
  if(!preview&&(!settings.directionEnabled||point==null||mc.player==null||mc.screen!=null&&!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)&&!(mc.screen instanceof HudInteractionScreen)||mc.options.hideGui))return;if(point==null)point=new CommunityLocation("Точка маршрута","minecraft:overworld",10,64,10,false);
  String text;
  if(preview)text="↑  120 м · 10, 10";else if(!mc.level.dimension().location().toString().equals(point.dimension()))text="Другое измерение: "+dev.abros.rivet.network.DimensionLabels.name(point.dimension()).getString();
  else{
   double dx=point.x()-mc.player.getX(),dz=point.z()-mc.player.getZ();
   double angle=net.minecraft.util.Mth.wrapDegrees((float)(Math.toDegrees(Math.atan2(-dx,dz))-mc.player.getYRot()));
   String arrow=Math.abs(angle)<22?"↑":angle>=22&&angle<67?"↗":angle>=67&&angle<112?"→":angle>=112&&angle<157?"↘":angle>=157||angle<=-157?"↓":angle<=-112?"↙":angle<=-67?"←":"↖";
   text=arrow+"  "+Math.round(Math.hypot(dx,dz))+" м"+(settings.directionCoordinates?" · "+point.x()+", "+point.z():"");
  }
  int maxWidth=Math.max(24,g.guiWidth()-16);
  String name=UiKit.fit(mc.font,point.name(),maxWidth-32);text=UiKit.fit(mc.font,text,maxWidth-32);
  int w=Math.min(maxWidth,Math.max(mc.font.width(text),mc.font.width(name))+32),h=32;bounds=HudPlacement.fit(g.guiWidth(),g.guiHeight(),w,h,settings.directionAnchorX,settings.directionAnchorY,settings.directionOffsetX,settings.directionOffsetY);int px=bounds.x(),py=bounds.y();
  // Background opacity is independent of text contrast and does not add a hidden opaque shadow.
  UiKit.surface(g,px,py,w,h,(UiKit.surface()&0xFFFFFF)|(Math.round(settings.directionOpacity*255)<<24));
  Ui.text(g,mc.font,name,px+8,py+6,UiKit.accent(),false);
  Ui.text(g,mc.font,text,px+8,py+18,UiKit.text(),false);
  if(!preview)Ui.text(g,mc.font,"×",px+w-12,py+6,UiKit.muted(),false);
 }
 static boolean stopHit(double x,double y){return HudSettings.INSTANCE.directionEnabled&&hasTarget()&&bounds.width()>0&&x>=bounds.right()-18&&x<bounds.right()&&y>=bounds.y()&&y<bounds.y()+17;}
 static boolean click(double x,double y){if(!stopHit(x,y))return false;clear();return true;}
 static void tooltip(GuiGraphics g,int x,int y){if(stopHit(x,y))g.renderTooltip(Minecraft.getInstance().font,net.minecraft.network.chat.Component.literal("Остановить маршрут"),x,y);}
 private DirectionCue(){}
}
