package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** One focusable card, with section-specific hierarchy rather than a wall of buttons. */
final class CommunityCard extends Button {
 private final UiTitleLayout titleLayout=new UiTitleLayout();
 private final Component heading;
 boolean selected;private final JsonObject item;private final String section;private final net.minecraft.client.gui.components.Tooltip titleTooltip;
 CommunityCard(int x,int y,int w,int h,JsonObject item,Runnable action){super(x,y,w,h,Component.literal(Json.opt(item,"title","")+". "+Json.opt(item,"preview","")),b->action.run(),DEFAULT_NARRATION);this.item=item;heading=Component.literal(Json.opt(item,"title","")).withStyle(net.minecraft.ChatFormatting.BOLD);titleTooltip=net.minecraft.client.gui.components.Tooltip.create(Component.literal(Json.opt(item,"title","")));setTooltipDelay(java.time.Duration.ofMillis(400));section=Json.opt(item,"section","home");}
 static int height(net.minecraft.client.gui.Font font,JsonObject item,int width,int minimum){
  int textWidth=Math.max(40,width-36);
  int lines=Math.min(2,font.split(Component.literal(Json.opt(item,"title","")).withStyle(net.minecraft.ChatFormatting.BOLD),textWidth).size());
  return Math.max(minimum,lines>1?84:minimum);
 }
 String targetId(){return Json.opt(item,"id","");}
 private String value(String key){return Json.opt(item,key,"");}
 private String count(String key){return dev.abros.rivet.core.DisplayCounts.text(item,key,"0");}
 private int color(){return switch(section){case "moderation"->UiPalette.color(0xFFEF7777);case "groups"->UiPalette.color(0xFF79CBA6);default->UiPalette.color(0xFFE2BE75);};}
 private void line(GuiGraphics g,String text,int x,int y,int w,int color){var font=Minecraft.getInstance().font;String line=font.width(text)>w?font.plainSubstrByWidth(text,Math.max(4,w-font.width("…")))+"…":text;Ui.text(g,font,line,x,y,color,false);}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight(),left=x+10;boolean focus=selected||UiTheme.highlighted(this);
  float hover=UiTheme.hover(this);int surface=UiTheme.mix(UiKit.surface(UiKit.Surface.CONTROL),UiKit.surface(selected?UiKit.Surface.SELECTED:UiKit.Surface.HOVER),selected?1:hover);
  UiKit.plate(g,x,y,w,h,surface);
  if(selected){UiKit.ring(g,x,y,w,h,6,UiTheme.mix(UiKit.border(),color(),.5f));g.fill(x,y+8,x+2,y+h-8,color());}
  if(UiTheme.keyboardFocus(this))UiKit.focus(g,x,y,w,h);

  g.enableScissor(x+4,y+3,x+w-4,y+h-3);
  String eyebrow=section.equals("groups")?value("type")+" · "+(item.has("recruiting")&&item.get("recruiting").getAsBoolean()?Client.text("ui.registration_open_4bd5a5ce"):Client.text("ui.registration_closed_a0d3acee")):CommunityScreen.name(section);
  var font=Minecraft.getInstance().font;var titleLines=titleLayout.lines(font,heading,Math.max(1,x+w-left-26));int lineCount=Math.min(h>=78?2:1,titleLines.size());
  var tooltip=titleLines.size()>lineCount?titleTooltip:null;if(getTooltip()!=tooltip)setTooltip(tooltip);
  for(int n=0;n<lineCount;n++)UiCanvas.text(g,font,titleLines.get(n),left,y+8+n*11,UiKit.text(),false);
  int extra=Math.max(0,lineCount-1)*11;
  if(h>=54){String badge=item.has("attention")?value("attention"):eyebrow;int bw=Math.min(x+w-left-12,Minecraft.getInstance().font.width(badge)+10);if(bw>8){if(item.has("attention")||section.equals("moderation"))UiBadge.draw(g,Minecraft.getInstance().font,badge,left-2,y+20+extra,bw,color());else line(g,badge,left,y+23+extra,x+w-left-12,UiKit.muted());}}
  if(selected||UiTheme.highlighted(this))UiIcons.draw(g,UiIcons.RIGHT,x+w-22,y+6,color());
  String preview=value("preview");
  line(g,preview,left,y+(h>=54?38:23)+extra,x+w-left-12,UiKit.muted());
  if(h>=68)line(g,item.has("footer")?value("footer"):section.equals("groups")?count("membersCount")+Client.text("ui.members_acb4ce67"):CommunityScreen.statusLabel(value("status")),left,y+54+extra,x+w-left-12,UiKit.muted());
  g.disableScissor();
 }
}
