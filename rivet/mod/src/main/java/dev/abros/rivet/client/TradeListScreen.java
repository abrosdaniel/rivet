package dev.abros.rivet.client;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/** Newest-first offers, with bounded server pages appended to the shared scrolling list. */
final class TradeListScreen extends ScrollScreen {
 private final JsonArray rows=new JsonArray();private final int tab;private int serverPage;private boolean more,inbox,enabled,loading;private long requestedAt;
 private UiWorkspace workspace;
 TradeListScreen(JsonObject data){super(Client.tr("trade.list"));tab=data.get("tab").getAsInt();append(data);}
 boolean accepts(JsonObject data){return data.get("tab").getAsInt()==tab&&data.get("page").getAsInt()==serverPage+1&&loading;}
 void append(JsonObject data){for(var value:data.getAsJsonArray("rows")){String id=value.getAsJsonObject().get("id").getAsString();boolean exists=false;for(var old:rows)if(old.getAsJsonObject().get("id").getAsString().equals(id)){exists=true;break;}if(!exists)rows.add(value);}serverPage=data.get("page").getAsInt();more=data.get("more").getAsBoolean();inbox=data.get("inbox").getAsBoolean();enabled=data.get("enabled").getAsBoolean();loading=false;if(minecraft!=null)rebuildWidgets();}
 private void next(){if(more&&!loading){loading=true;requestedAt=System.currentTimeMillis();TradeClient.command("rivet trades "+tab+" "+(serverPage+1));}}
 @Override protected void onScrollEnd(){next();}
 @Override public void tick(){if(loading&&System.currentTimeMillis()-requestedAt>10000)loading=false;}
 @Override protected void init(){workspace=UiWorkspace.fit(width,height);new MenuSidebar(this).build("trades",this::addRenderableWidget);
  var area=workspace.page();int x=area.x(),w=area.width(),y=area.y(),tabWidth=(w-8)/3;
  for(int i=0;i<3;i++){final int t=i;addRenderableWidget(UiActions.button(Client.tr("trade.tab."+i),i==tab?UiActions.Tone.PRIMARY:UiActions.Tone.NORMAL,"",b->TradeClient.command("rivet trades "+t+" 0")).bounds(x+i*(tabWidth+4),y,tabWidth,20).build());}
  int controls=area.bottom()-48,half=(w-4)/2;
  var layout=scrollArea(rows.size(),new dev.abros.rivet.core.NativeLayout.Box(x,y+28,w,Math.max(0,controls-y-32)),88);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){var row=rows.get(i).getAsJsonObject();addRenderableWidget(new TradeCard(layout.content().x(),y+28+(i-firstRow)*88,layout.content().width(),row,()->TradeClient.command("rivet tradeopen "+row.get("id").getAsString())));}
  addRenderableWidget(UiActions.button(Client.tr("trade.refresh"),UiActions.Tone.NORMAL,"",b->TradeClient.command("rivet trades "+tab+" 0")).bounds(x,controls,half,20).build());
  addRenderableWidget(UiActions.button(Client.tr(inbox?"trade.inbox.disable":"trade.inbox.enable"),UiActions.Tone.NORMAL,"",b->TradeClient.command("rivet tradeinbox")).bounds(x+half+4,controls,half,20).build()).active=enabled;
  addRenderableWidget(UiActions.button(Client.tr("trade.new"),UiActions.Tone.PRIMARY,"",b->FeatureListScreen.pick(this,p->TradeClient.command("rivet trade "+p.get("name").getAsString()))).bounds(x,controls+24,w,20).build()).active=enabled;
  UiActions.close(workspace.footer(),this::addRenderableWidget,this::onClose);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){workspace.draw(g);}
 @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);workspace.heading(g,font,title);if(rows.isEmpty())g.drawCenteredString(font,Client.tr(enabled?"trade.list.empty":"trade.disabled"),workspace.page().x()+workspace.page().width()/2,workspace.page().y()+34,UiKit.muted());}
 @Override public boolean isPauseScreen(){return false;}
}
