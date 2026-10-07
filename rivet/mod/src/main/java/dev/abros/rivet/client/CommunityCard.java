package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
/** One focusable card, with section-specific hierarchy rather than a wall of buttons. */
final class CommunityCard extends Button {
 boolean selected;private final JsonObject item;private final String section;
 CommunityCard(int x,int y,int w,int h,JsonObject item,Runnable action){super(x,y,w,h,Component.literal(Json.opt(item,"title","")+". "+Json.opt(item,"preview","")),b->action.run(),DEFAULT_NARRATION);this.item=item;setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Json.opt(item,"title","")+"\n"+Json.opt(item,"preview",""))));section=Json.opt(item,"section","home");}
 static int height(net.minecraft.client.gui.Font font,JsonObject item,int width,int minimum){
  int textWidth=Math.max(40,width-36-((Json.opt(item,"section","").equals("events")||Json.opt(item,"section","").equals("ideas"))&&width>=230?56:0));
  int lines=Math.min(2,font.split(Component.literal(Json.opt(item,"title","")).withStyle(net.minecraft.ChatFormatting.BOLD),textWidth).size());
  return Math.max(minimum,lines>1?84:minimum);
 }
 String targetId(){return Json.opt(item,"id","");}
 private String value(String key){return Json.opt(item,key,"");}
 private String count(String key){return dev.abros.rivet.core.DisplayCounts.text(item,key,"0");}
 private String eventTime(){if(!item.has("startsAt"))return "Событие";long minutes=Math.max(0,(item.get("startsAt").getAsLong()-System.currentTimeMillis())/60000);return (item.has("rescheduledAt")?"Перенесено · ":"")+(minutes<60?"Через "+minutes+" мин":minutes<1440?"Через "+minutes/60+" ч":"Через "+minutes/1440+" дн");}
 private int color(){return switch(section){case "moderation"->UiPalette.color(0xFFEF7777);case "groups"->UiPalette.color(0xFF79CBA6);case "events"->UiPalette.color(0xFF82B6F2);case "ideas","task"->UiPalette.color(0xFFF0A77C);case "polls"->UiPalette.color(0xFFB49AE8);default->UiPalette.color(0xFFE2BE75);};}
 private void line(GuiGraphics g,String text,int x,int y,int w,int color){var font=Minecraft.getInstance().font;String line=font.width(text)>w?font.plainSubstrByWidth(text,Math.max(4,w-font.width("…")))+"…":text;Ui.text(g,font,line,x,y,color,false);}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight(),left=x+10;boolean focus=selected||isHoveredOrFocused();
  float hover=UiTheme.hover(this);int surface=UiTheme.mix(UiKit.surface(),UiKit.surface(selected?UiKit.Surface.SELECTED:UiKit.Surface.HOVER),selected?1:hover);
  UiKit.plate(g,x,y,w,h,surface);
  if(selected||focus)g.fill(x,y+5,x+2,y+h-5,color());
  if(isFocused())g.renderOutline(x,y,w,h,color());

  g.enableScissor(x+4,y+3,x+w-4,y+h-3);
  if(section.equals("events")&&w>=230){UiKit.surface(g,x+7,y+7,48,h-14,UiTheme.mix(surface,color(),0.08f));if(item.has("startsAt")){var time=Instant.ofEpochMilli(item.get("startsAt").getAsLong()).atZone(AccessibilityScreen.zone());line(g,time.format(DateTimeFormatter.ofPattern("dd.MM")),x+12,y+13,48,UiKit.text());line(g,time.format(DateTimeFormatter.ofPattern("HH:mm")),x+12,y+29,48,color());}left=x+64;}
  else if(section.equals("ideas")&&w>=230){UiKit.surface(g,x+7,y+7,48,h-14,UiTheme.mix(surface,color(),0.08f));line(g,count("supportersCount"),x+12,y+13,48,color());line(g,"гол.",x+12,y+29,48,UiKit.muted());left=x+64;}
  String eyebrow=switch(section){case "groups"->value("type")+" · "+(item.has("recruiting")&&item.get("recruiting").getAsBoolean()?"Набор открыт":"Набор закрыт");case "polls"->"Голосование · открыть варианты";case "board"->value("author")+" · "+count("responsesCount")+" откликов";case "events"->eventTime();case "ideas"->(w>=230?"":count("supportersCount")+" голосов · ")+CommunityScreen.statusLabel(value("status"));default->CommunityScreen.name(section);};
  var font=Minecraft.getInstance().font;var titleLines=font.split(Component.literal(value("title")).withStyle(net.minecraft.ChatFormatting.BOLD),Math.max(1,x+w-left-26));int lineCount=Math.min(h>=78?2:1,titleLines.size());
  for(int n=0;n<lineCount;n++)g.drawString(font,titleLines.get(n),left,y+8+n*11,UiKit.text(),false);
  int extra=Math.max(0,lineCount-1)*11;
  if(h>=54){String badge=section.equals("events")?eventTime():item.has("attention")?value("attention"):eyebrow;int bw=Math.min(x+w-left-12,Minecraft.getInstance().font.width(badge)+10);if(bw>8){if(item.has("attention")||section.equals("moderation"))UiBadge.draw(g,Minecraft.getInstance().font,badge,left-2,y+20+extra,bw,color());else line(g,badge,left,y+23+extra,x+w-left-12,section.equals("events")?color():UiKit.muted());}}
  if(selected||isHoveredOrFocused())UiIcons.draw(g,UiIcons.RIGHT,x+w-22,y+6,color());
  String preview=section.equals("ideas")&&!value("answerPreview").isBlank()?"Ответ: "+value("answerPreview"):value("preview");
  line(g,preview,left,y+(h>=54?38:23)+extra,x+w-left-12,UiKit.muted());
  if(h>=68&&!section.equals("ideas"))line(g,item.has("footer")?value("footer"):section.equals("groups")?count("membersCount")+" участников":section.equals("events")?(item.has("waitlistPosition")&&item.get("waitlistPosition").getAsInt()>0?"Очередь: "+item.get("waitlistPosition").getAsInt():item.has("isParticipant")&&item.get("isParticipant").getAsBoolean()?"Вы участвуете":item.has("isOwner")&&item.get("isOwner").getAsBoolean()?"Вы организатор":"Вы подписаны"):CommunityScreen.statusLabel(value("status")),left,y+54+extra,x+w-left-12,UiKit.muted());
  if(item.has("stagesTotal")&&item.get("stagesTotal").getAsInt()>0){int total=item.get("stagesTotal").getAsInt(),done=item.get("stagesDone").getAsInt();g.fill(left,y+h-5,x+w-12,y+h-3,UiPalette.color(0xFF394651));g.fill(left,y+h-5,left+(w-(left-x)-12)*Math.max(0,Math.min(done,total))/total,y+h-3,UiPalette.color(0xFF79CBA6));}
  g.disableScissor();
 }
}
