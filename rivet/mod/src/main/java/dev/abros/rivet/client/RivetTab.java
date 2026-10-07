package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import java.util.*;
import net.minecraft.network.chat.Component;
/** One layout owns columns, clipping, row geometry and hit-testing for HUD and interactive TAB. */
final class RivetTab {
 static void install(){
  var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
  bus.addListener(RivetTab::layer);
  bus.addListener((net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent e)->{
   if(scrollTab(e.getScrollDeltaY(),SocialClient.visible()))e.setCanceled(true);
  });
 }
 static boolean scrollTab(double amount,boolean shown){if(!canScrollTab(shown,Minecraft.getInstance().screen!=null))return false;scroll(amount);return true;}
 static boolean canScrollTab(boolean shown,boolean screenOpen){return shown&&!screenOpen&&SocialSettings.INSTANCE.tab();}
 private static void layer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e){
  if(e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.TAB_LIST)&&SocialSettings.INSTANCE.tab()&&SocialClient.visible()&&Minecraft.getInstance().screen==null){e.setCanceled(true);draw(e.getGuiGraphics(),-1,-1);}
 }
 private static int offset;private static int capacity=1;static void reset(){offset=0;hits.clear();rowsViewport=null;}static void scroll(double amount){offset=Math.max(0,offset-(int)Math.signum(amount)*3);}
 record Hit(UUID player,int x,int y,int w,int h){}
 private static int alpha(int color,double opacity){return (color&0xFFFFFF)|((int)Math.round(255*opacity)<<24);}
 static final List<Hit> hits=new ArrayList<>();
 static dev.abros.rivet.core.NativeLayout.Box rowsViewport;
 private static String value(PlayerInfo p,String k){var meta=SocialClient.players.get(p.getProfile().getId());return meta==null?"":Json.opt(meta,k,"");}
 private static String group(PlayerInfo p){return switch(SocialSettings.INSTANCE.grouping){case 1->value(p,"primaryGroup");case 2->value(p,"group");default->"";};}
 static Component metadata(PlayerInfo player){
  var out=Component.empty();var seen=new HashSet<String>();
  for(var part:List.of(Component.literal(group(player)),PlayerText.text(value(player,"prefix")),PlayerText.text(value(player,"suffix")),Component.literal(value(player,"status")))){
   if(part.getString().isBlank()||!seen.add(part.getString()))continue;
   if(!out.getSiblings().isEmpty())out.append(Component.literal(" · "));
   out.append(part);
  }
  return out;
 }
 static void draw(GuiGraphics g,int mouseX,int mouseY){var mc=Minecraft.getInstance();if(mc.getConnection()!=null)draw(g,mouseX,mouseY,new ArrayList<>(mc.getConnection().getOnlinePlayers()));else reset();}
 static void draw(GuiGraphics g,int mouseX,int mouseY,List<PlayerInfo> source){var mc=Minecraft.getInstance();var font=mc.font;int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();var list=source.stream().sorted(Comparator.comparing((PlayerInfo p)->group(p).toLowerCase(Locale.ROOT)).thenComparing(p->p.getProfile().getName().toLowerCase(Locale.ROOT))).toList();int cols=Math.max(1,Math.min(3,(width-32)/SocialSettings.INSTANCE.columnWidth)),w=Math.min(width-32,cols*SocialSettings.INSTANCE.columnWidth+16),rowHeight=SocialSettings.INSTANCE.heads?new int[]{26,30,34}[SocialSettings.INSTANCE.density]:new int[]{24,26,30}[SocialSettings.INSTANCE.density];int rows=Math.max(1,Math.min(16,(height-100)/rowHeight));capacity=rows*cols;offset=Math.min(offset,Math.max(0,list.size()-capacity));int shown=Math.min(capacity,list.size()-offset),usedRows=(shown+cols-1)/cols;long restart=ServerMenuClient.state.has("restartAt")?ServerMenuClient.state.get("restartAt").getAsLong():0;String detail=restart>System.currentTimeMillis()?"Перезапуск · "+RivetHud.relative(restart):list.isEmpty()?"Список игроков обновляется…":list.size()>capacity?"Показаны "+(offset+1)+"–"+(offset+shown)+" из "+list.size():"";int header=detail.isEmpty()?27:44;String footer=footer(list.size()>capacity);int h=header+14+usedRows*rowHeight+(footer.isEmpty()?0:18),x=(width-w)/2,y=16,cw=(w-16)/cols;UiKit.surface(g,x,y,w,h,alpha(UiKit.surface(),SocialSettings.INSTANCE.tabOpacity));g.fill(x+3,y,x+w-3,y+1,alpha(UiKit.accent(),.4));String server=mc.getCurrentServer()==null?"Rivet":mc.getCurrentServer().name;String online=list.size()+" / "+(ServerMenuClient.state.has("maximum")?ServerMenuClient.state.get("maximum").getAsInt():list.size());Ui.text(g,font,UiKit.fit(font,server,w-font.width(online)-34),x+10,y+10,UiKit.text(),false);Ui.text(g,font,online,x+w-font.width(online)-10,y+10,UiKit.accent(),false);if(!detail.isEmpty())Ui.text(g,font,detail,x+10,y+27,UiKit.muted(),false);hits.clear();List<Component> tooltip=List.of();rowsViewport=new dev.abros.rivet.core.NativeLayout.Box(x+4,y+header-2,w-8,usedRows*rowHeight+2);g.enableScissor(rowsViewport.x(),rowsViewport.y(),rowsViewport.right(),rowsViewport.bottom());
  for(int i=0;i<shown;i++){var p=list.get(offset+i);int rx=x+8+(i/usedRows)*cw,ry=y+header+(i%usedRows)*rowHeight;boolean hovered=mouseX>=rx&&mouseX<rx+cw-4&&mouseY>=ry&&mouseY<ry+rowHeight-2;UiKit.surface(g,rx,ry,cw-4,rowHeight-2,alpha(hovered?UiTheme.mix(UiKit.surface(),UiKit.accent(),.18f):UiTheme.mix(UiKit.surface(),UiKit.text(),.025f),SocialSettings.INSTANCE.tabOpacity*(hovered?.6:.25)));int tx=rx+6;if(SocialSettings.INSTANCE.heads){PlayerFaceRenderer.draw(g,SkinClient.skin(p.getProfile().getId()),rx+5,ry+5,18);tx+=23;}int latency=p.getLatency();String quality=pingText(latency);int pingW=SocialSettings.INSTANCE.ping?font.width(quality)+10:14;String nick=p.getProfile().getName();Ui.text(g,font,UiKit.fit(font,nick,cw-(tx-rx)-pingW-8),tx,ry+5,UiKit.text(),false);var meta=metadata(p);if(hovered)tooltip=List.of(Component.literal(nick),meta);Ui.text(g,font,UiKit.fit(font,meta,cw-(tx-rx)-10),tx,ry+16,UiKit.muted(),false);int color=latency<100?0x83C6A3:latency<250?0xE0BB68:0xDC827F;if(SocialSettings.INSTANCE.ping){Ui.text(g,font,quality,rx+cw-font.width(quality)-10,ry+5,latency<0?UiKit.muted():UiPalette.color(color),false);}else drawPing(g,latency,rx+cw-20,ry+5);hits.add(new Hit(p.getProfile().getId(),rx,ry,cw-4,rowHeight-2));}
  g.disableScissor();UiScrollbar.draw(g,new dev.abros.rivet.core.NativeLayout.Box(x+w-7,y+header,3,usedRows*rowHeight),capacity,list.size(),list.size()<=capacity?0:offset/(double)(list.size()-capacity));if(!footer.isEmpty())Ui.text(g,font,UiKit.fit(font,footer,w-20),x+10,y+h-14,UiKit.muted(),false);
  if(!tooltip.isEmpty())g.renderComponentTooltip(font,tooltip,mouseX,mouseY);
 }
 private static String footer(boolean scrollable){if(!SocialSettings.INSTANCE.footer)return "";String pinned=Json.opt(ServerMenuClient.state,"pinnedText","");if(!pinned.isBlank())return pinned;if(RivetHud.snapshot.has("events")&&!RivetHud.snapshot.getAsJsonArray("events").isEmpty())return Json.opt(RivetHud.snapshot.getAsJsonArray("events").get(0).getAsJsonObject(),"title","");String hint=scrollable?"Колесо: прокрутка":"";if(!(Minecraft.getInstance().screen instanceof HudInteractionScreen)){String key="Режим курсора: "+(RivetHud.INTERACT.isUnbound()?"назначьте клавишу":RivetHud.INTERACT.getTranslatedKeyMessage().getString());hint=hint.isEmpty()?key:hint+" · "+key;}return hint;}
 static String pingText(int latency){return latency<0?"—":latency+" мс";}
 static void drawPing(GuiGraphics g,int latency,int x,int y){String icon=latency<0?"ping_unknown":latency<150?"ping_5":latency<300?"ping_4":latency<600?"ping_3":latency<1000?"ping_2":"ping_1";g.blitSprite(net.minecraft.resources.ResourceLocation.withDefaultNamespace("icon/"+icon),x,y,10,8);}

}
