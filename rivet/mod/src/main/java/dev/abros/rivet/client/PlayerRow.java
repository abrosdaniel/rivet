package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Compact selectable player card; standard Button keeps keyboard and narration support. */
final class PlayerRow extends Button {
 private final JsonObject player;private final boolean selected;
 PlayerRow(int x,int y,int width,JsonObject player,Runnable action,boolean selected){super(x,y,width,32,PlayerText.name(player),b->action.run(),DEFAULT_NARRATION);this.player=player;this.selected=selected;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
  var font=Minecraft.getInstance().font;boolean online=player.has("online")&&player.get("online").getAsBoolean();
  UiTheme.panel(g,getX(),getY(),width,height,selected?UiKit.surface(UiKit.Surface.SELECTED):UiTheme.mix(UiKit.surface(),UiKit.surface(UiKit.Surface.HOVER),UiTheme.hover(this)));
  if(selected)g.fill(getX(),getY()+4,getX()+2,getY()+height-4,UiKit.accent());
  net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(player,"uuid"))),getX()+8,getY()+6,20);
  boolean wide=width>=280;String state=online?Client.text("ui.online_e858f4e3"):Client.text("ui.offline_67b99cc9");int stateWidth=wide?font.width(state)+20:0;
  Ui.text(g,font,net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(),Math.max(1,width-48-stateWidth))),getX()+36,getY()+(wide?12:5),UiKit.text(),false);
  Ui.text(g,font,state,wide?getX()+width-font.width(state)-12:getX()+36,getY()+(wide?12:19),online?UiPalette.color(0x79CBA6):UiKit.muted(),false);
  if(isFocused())g.renderOutline(getX(),getY(),width,height,UiKit.accent());
 }
}
