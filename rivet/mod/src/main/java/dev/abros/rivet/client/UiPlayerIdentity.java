package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import java.util.UUID;
/** Shared, content-sized identity; styled metadata never clips into adjacent controls. */
final class UiPlayerIdentity {
 static int height(Font font,JsonObject player,int width){return Math.max(32,Math.min(3,font.split(PlayerText.name(player),Math.max(40,width-44)).size())*font.lineHeight+14);}
 static void draw(GuiGraphics g,Font font,JsonObject player,int x,int y,int width){
  PlayerFaceRenderer.draw(g,SkinClient.skin(UUID.fromString(dev.abros.rivet.core.Json.str(player,"uuid"))),x,y,28);
  var lines=font.split(PlayerText.name(player),Math.max(40,width-44));int shown=Math.min(3,lines.size());
  for(int n=0;n<shown;n++)Ui.text(g,font,lines.get(n),x+38,y+n*font.lineHeight,UiKit.text(),false);
  Ui.text(g,font,PlayerStatisticsText.activity(player),x+38,y+shown*font.lineHeight+4,player.has("online")&&player.get("online").getAsBoolean()?UiPalette.color(0x79CBA6):UiKit.muted(),false);
 }
 private UiPlayerIdentity(){}
}
