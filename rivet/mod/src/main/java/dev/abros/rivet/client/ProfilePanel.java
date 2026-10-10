package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
final class ProfilePanel {
 private ProfilePanel(){}
 static boolean playTimeVisible(com.google.gson.JsonObject data){return !data.has("entries")||data.has("selfStatistics")&&data.getAsJsonObject("selfStatistics").has("totalMillis");}
 static int actionsTop(com.google.gson.JsonObject data){return playTimeVisible(data)?108:90;}
 static void render(GuiGraphics g,Font font,int x,int y,int w,boolean full,com.google.gson.JsonObject data){
  var mc=Minecraft.getInstance();var state=ServerMenuClient.state;
  int h=full?(AuthClient.available()?160:134)-(playTimeVisible(data)?0:18):66;UiKit.material(g,x,y,w,h);
  if(full){
   if(mc.player!=null)net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,mc.player.getSkin(),x+10,y+12,30);
   Ui.text(g,font,UiKit.fit(font,Json.opt(state,"name",Client.text("map.tool.player")),w-62),x+50,y+14,UiKit.text(),false);
   Ui.text(g,font,Client.text("ui.online_e858f4e3"),x+50,y+29,UiPalette.color(0x79CBA6),false);
   UiKit.divider(g,x+10,y+50,w-20);
   String total=Client.text("server.loading");if(data.has("selfStatistics")&&data.getAsJsonObject("selfStatistics").has("totalMillis")){long minutes=data.getAsJsonObject("selfStatistics").get("totalMillis").getAsLong()/60000;total=minutes/60+Client.text("ui.h_1589166c")+minutes%60+Client.text("ui.m_248f3fa6");}
   if(playTimeVisible(data))metric(g,font,x+10,y+61,w-20,Client.text("ui.play_time_c35bc939"),total);
   metric(g,font,x+10,y+(playTimeVisible(data)?79:61),w-20,Client.text("ui.online_76e9fb6a"),DisplayCounts.text(state,"online","—")+" / "+DisplayCounts.text(state,"maximum","—"));
  }else{Ui.text(g,font,UiKit.fit(font,Json.opt(state,"name",Client.text("map.tool.player")),w-20),x+10,y+12,UiKit.text(),false);}
 }
 private static void metric(GuiGraphics g,Font font,int x,int y,int w,String label,String value){Ui.text(g,font,label,x,y,UiKit.muted(),false);Ui.text(g,font,UiKit.fit(font,value,w/2),x+w-font.width(UiKit.fit(font,value,w/2)),y,UiKit.text(),false);}
}
