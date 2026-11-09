package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.TradeWire;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
/** Staff inspection never opens a writable container or exposes inventory click handlers. */
final class TradeAdminScreen extends ScrollScreen {
 private final JsonObject data;private final java.util.List<TradeWire.Change> icons;private UiWorkspace workspace;private String reason="";private Button freeze,cancel;private boolean loading;private long requestedAt;
 TradeAdminScreen(JsonObject data,java.util.List<TradeWire.Change> icons){super(Client.tr("trade.admin.title"));this.data=data;this.icons=icons;}
 boolean accepts(JsonObject next){return mode().equals("list")&&Json.str(next,"mode").equals("list")&&loading&&next.get("page").getAsInt()==data.get("page").getAsInt()+1;}
 void append(JsonObject next){var rows=data.getAsJsonArray("rows");for(var row:next.getAsJsonArray("rows")){boolean exists=false;for(var old:rows)if(Json.str(old.getAsJsonObject(),"id").equals(Json.str(row.getAsJsonObject(),"id"))){exists=true;break;}if(!exists)rows.add(row);}data.add("page",next.get("page"));data.add("more",next.get("more"));loading=false;rebuildWidgets();}
 @Override protected void onScrollEnd(){if(mode().equals("list")&&data.get("more").getAsBoolean()&&!loading){loading=true;requestedAt=System.currentTimeMillis();load(data.get("page").getAsInt()+1);}}
 private String mode(){return Json.str(data,"mode");}private String id(){return Json.opt(data,"id","");}
 private void load(int page){TradeClient.command(mode().equals("list")?"rivet tradeadmin list "+page:"rivet tradeadmin inspect "+id()+" "+data.get("event").getAsInt()+" "+page);}
 private void inspect(int event){TradeClient.command("rivet tradeadmin inspect "+id()+" "+event+" 0");}
 private void change(String operation){if(reason.isBlank())return;String captured=reason.trim();minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)TradeClient.command("rivet tradeadmin change "+id()+" "+data.get("revision").getAsLong()+" "+operation+" "+captured);},Client.tr("trade.admin."+operation),Component.literal(data.get("name").getAsString()+"\n"+captured)).compact());}
 @Override protected void init(){workspace=UiWorkspace.fit(width,height);new MenuSidebar(this).build("admin",this::addRenderableWidget);var area=workspace.page();int x=area.x(),y=area.y(),w=area.width();boolean detail=!mode().equals("list");int toolbar=detail?48:24;
  addRenderableWidget(UiActions.button(Client.tr("trade.admin.refresh"),UiActions.Tone.NORMAL,"",b->load(mode().equals("list")?0:data.get("page").getAsInt())).bounds(x,y,90,20).build());
  if(detail){addRenderableWidget(UiActions.button(Client.tr("trade.admin.current"),UiActions.Tone.NORMAL,"",b->inspect(-1)).bounds(x,y+24,(w-4)/2,20).build());addRenderableWidget(UiActions.button(Client.tr("trade.admin.history"),UiActions.Tone.NORMAL,"",b->inspect(-2)).bounds(x+(w+4)/2,y+24,(w-4)/2,20).build());}
  int controls=area.bottom();boolean actions=detail&&data.get("active").getAsBoolean()&&(data.get("freeze").getAsBoolean()||data.get("cancel").getAsBoolean());if(actions){controls-=52;var field=UiFields.text(font,x,controls,w,20,Client.text("trade.reason"),Client.text("trade.reason"),300,reason,v->{reason=v;enableActions();});addRenderableWidget(field);int half=(w-4)/2;freeze=addRenderableWidget(UiActions.button(Client.tr(data.get("frozen").getAsBoolean()?"trade.admin.unfreeze":"trade.admin.freeze"),UiActions.Tone.NORMAL,"",b->change(data.get("frozen").getAsBoolean()?"unfreeze":"freeze")).bounds(x,controls+24,half,20).build());cancel=addRenderableWidget(UiActions.button(Client.tr("trade.admin.cancel"),UiActions.Tone.DANGER,"",b->change("cancel")).bounds(x+half+4,controls+24,half,20).build());enableActions();}
  var rows=data.getAsJsonArray("rows");int stride=mode().equals("list")?88:28;var layout=scrollArea(mode().equals("detail")?0:rows.size(),new dev.abros.rivet.core.NativeLayout.Box(x,y+toolbar+4,w,Math.max(0,controls-y-toolbar-8)),stride);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){var row=rows.get(i).getAsJsonObject();int rowY=y+toolbar+4+(i-firstRow)*stride;String label;
   if(mode().equals("list")){addRenderableWidget(new TradeCard(x,rowY,layout.content().width(),row,()->TradeClient.command("rivet tradeadmin inspect "+Json.str(row,"id")+" -1 0")));}
   else if(mode().equals("history")){String when=java.time.Instant.ofEpochMilli(row.get("at").getAsLong()).atZone(AccessibilityScreen.zone()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));label=when+" · "+Client.text("trade.audit."+Json.str(row,"action"))+" · "+Json.str(row,"actor").split(" \\(",2)[0];addRenderableWidget(UiActions.leading(UiActions.button(Component.literal(label),UiActions.Tone.NORMAL,"",b->inspect(row.get("event").getAsInt())).bounds(x,rowY,layout.content().width(),24).tooltip(Tooltip.create(Component.literal(label+"\n"+Json.str(row,"actor")+"\n"+Json.str(row,"reason")))).build()));}
  }
  if(detail&&data.get("pages").getAsInt()>1){int page=data.get("page").getAsInt(),pager=workspace.footer().y();addRenderableWidget(UiActions.button(Component.literal("‹"),UiActions.Tone.NORMAL,"",b->load(page-1)).bounds(x,pager,20,20).tooltip(Tooltip.create(Client.tr("trade.page.previous"))).build()).active=page>0;addRenderableWidget(UiActions.button(Component.literal("›"),UiActions.Tone.NORMAL,"",b->load(page+1)).bounds(x+124,pager,20,20).tooltip(Tooltip.create(Client.tr("trade.page.next"))).build()).active=data.get("more").getAsBoolean();}
  var footer=workspace.footer();UiActions.command(UiActions.Command.BACK,new dev.abros.rivet.core.NativeLayout.Box(footer.right()-80,footer.y(),80,20),this::addRenderableWidget,this::onClose);
 }
 private void enableActions(){if(freeze!=null)freeze.active=!reason.isBlank()&&data.get("freeze").getAsBoolean();if(cancel!=null)cancel.active=!reason.isBlank()&&data.get("cancel").getAsBoolean();}
 @Override public void tick(){if(!ServerMenuClient.may("rivet.trade.view"))onClose();if(loading&&System.currentTimeMillis()-requestedAt>10000)loading=false;}
 @Override public void onClose(){if(!mode().equals("list"))TradeClient.command("rivet tradeadmin");else minecraft.setScreen(new ServerMenuScreen(null,"admin"));}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){workspace.draw(g);}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){super.render(g,mx,my,dt);workspace.heading(g,font,id().isEmpty()?title:Component.literal(Json.str(data,"name")));var area=workspace.page();UiCanvas.text(g,font,UiKit.fit(font,id().isEmpty()?Client.text("trade.admin.title"):Json.str(data,"name"),Math.max(1,area.width()-100)),area.x()+100,area.y()+1,UiKit.text(),false);if(!mode().equals("list"))UiCanvas.text(g,font,UiKit.fit(font,TradeCard.created(data),Math.max(1,area.width()-100)),area.x()+100,area.y()+13,UiKit.muted(),false);
  if(!mode().equals("list")&&data.get("pages").getAsInt()>1){UiCanvas.text(g,font,UiKit.fit(font,Client.tr("trade.admin.page",data.get("page").getAsInt()+1,data.get("pages").getAsInt()).getString(),100),area.x()+22,workspace.footer().y()+6,UiKit.muted(),false);}
  if(mode().equals("detail")){
   int gap=16,trayWidth=(area.width()-gap)/2,top=area.y()+54;
   int cell=18,columns=6;
   for(int side=0;side<2;side++){int tx=area.x()+side*(trayWidth+gap);String name=Json.str(data,side==0?"nameA":"nameB");
    net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(Json.str(data,side==0?"uuidA":"uuidB"))),tx,top,16);
    UiCanvas.text(g,font,UiKit.fit(font,name,trayWidth-22),tx+21,top+4,UiKit.text(),false);
    int sx=tx+(trayWidth-cell*columns)/2,sy=top+22;
    for(int slot=0;slot<12;slot++){int px=sx+(slot%columns)*cell,py=sy+(slot/columns)*cell;g.fill(px,py,px+cell-1,py+cell-1,UiKit.surface(UiKit.Surface.CONTROL));if(mx>=px&&mx<px+cell-1&&my>=py&&my<py+cell-1)g.fill(px,py,px+cell-1,py+cell-1,UiKit.surface(UiKit.Surface.HOVER));}
    for(var icon:icons){if(icon.slot()/24!=side)continue;int slot=icon.slot()%24,px=sx+(slot%columns)*cell,py=sy+(slot/columns)*cell;var item=icon.item();g.renderItem(item,px+(cell-16)/2,py+(cell-16)/2);String count=TradeScreen.compactCount(icon.after());g.pose().pushPose();g.pose().translate(0,0,200);UiCanvas.text(g,font,count,px+cell-2-font.width(count),py+cell-9,0xFFFFFF,true);g.pose().popPose();if(mx>=px&&mx<px+cell&&my>=py&&my<py+cell){var tooltip=new java.util.ArrayList<>(getTooltipFromItem(minecraft,item));tooltip.add(Component.literal("× "+String.format(java.util.Locale.ROOT,"%,d",icon.after()).replace(',',' ')));g.renderTooltip(font,tooltip,item.getTooltipImage(),mx,my);}}
   }
  }
  if(!mode().equals("detail")&&data.getAsJsonArray("rows").isEmpty())UiCanvas.text(g,font,Client.tr("trade.admin.empty"),area.x(),area.y()+(mode().equals("list")?32:56),UiKit.muted(),false);
 }
 @Override public boolean isPauseScreen(){return false;}
}
