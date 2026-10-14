package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Server-owned publication state; an author submits, moderators approve the exact revision. */
final class MapPublicationsScreen extends ScrollScreen implements CommunityScreen.Receiver, UiParentScreen {
 @Override public net.minecraft.client.gui.screens.Screen parentScreen(){return parent;}
 private final Screen parent;private final UUID target;private final String type;private final JsonObject draft;
 private final RequestSession session=new RequestSession();private final List<JsonObject> rows=new ArrayList<>();
 private UiDialog dialog;private boolean busy,admin,loaded,loadFailed;private String notice="";
 MapPublicationsScreen(Screen parent){this(parent,null,"",null);}
 MapPublicationsScreen(Screen parent,MapMarker m){this(parent,m.id(),"marker",MapMarkerLink.json(m));}
 MapPublicationsScreen(Screen parent,MapTerritory t){this(parent,t.id(),"territory",MapTerritoryJson.write(t));}
 private MapPublicationsScreen(Screen parent,UUID id,String type,JsonObject draft){super(Client.tr("map.publications"));this.parent=parent;target=id;this.type=type;this.draft=draft;}
 private void request(String op,JsonObject body){if(busy)return;body.addProperty("action","community");body.addProperty("section","home");body.addProperty("op",op);busy=true;ServerMenuClient.request(session.begin(body,!op.equals("mapObjects"),System.currentTimeMillis()));}
 private void load(String cursor){var j=new JsonObject();j.addProperty("manage",true);j.addProperty("cursor",cursor);if(target!=null)j.addProperty("id",target.toString());request("mapObjects",j);}
 @Override protected void init(){dialog=UiDialog.fit(width,height,410,350);var b=dialog.body();var selected=target==null?rows:rows.stream().filter(r->Json.str(r,"id").equals(target.toString())).toList();int count=selected.size()+(target==null?0:1);var area=scrollForm(count,b,80);
  for(int n=firstRow;n<Math.min(count,firstRow+visibleRows);n++){int y=b.y()+(n-firstRow)*80,w=area.content().width();
   if(target!=null&&n==0){var old=selected.isEmpty()?null:selected.getFirst();var button=addRenderableWidget(UiActions.button(Client.tr("map.submitPublication"),UiActions.Tone.PRIMARY,MapGlyphs.icon("share"),ignored->{var j=new JsonObject();j.addProperty("id",target.toString());j.addProperty("type",type);j.add("draft",draft);j.addProperty("revision",old==null?0:old.get("revision").getAsLong());request("mapObjectSubmit",j);rebuildWidgets();}).bounds(b.x(),y,w,20).build());button.active=loaded&&!busy&&!loadFailed;continue;}
   var row=selected.get(n-(target==null?0:1));var payload=row.has("draft")?row.getAsJsonObject("draft"):row.has("published")?row.getAsJsonObject("published"):null;if(payload==null)continue;
   String state=Client.tr(row.has("draft")?"map.awaitingApproval":"map.published").getString();String label=Json.str(payload,"name")+" · "+state;
   addRenderableWidget(UiActions.button(Component.literal(UiKit.fit(font,label,w-12)),UiActions.Tone.NORMAL,"",ignored->details(row)).bounds(b.x(),y,w,20).build()).active=!busy;
   int half=(w-6)/2;if(admin&&row.has("draft")){addRenderableWidget(UiActions.button(Client.tr("map.approve"),UiActions.Tone.PRIMARY,"",ignored->confirm(row,"mapObjectApprove")).bounds(b.x(),y+24,half,20).build()).active=!busy;addRenderableWidget(UiActions.button(Client.tr("map.reject"),UiActions.Tone.DANGER,"",ignored->confirm(row,"mapObjectReject")).bounds(b.x()+half+6,y+24,w-half-6,20).build()).active=!busy;}
   addRenderableWidget(UiActions.button(Client.tr("map.withdraw"),UiActions.Tone.NORMAL,"",ignored->confirm(row,"mapObjectWithdraw")).bounds(b.x(),y+(admin&&row.has("draft")?48:24),w,20).build()).active=!busy;
  }
  UiActions.command(UiActions.Command.REFRESH,new NativeLayout.Box(b.x(),dialog.footer().bottom()-20,UiActions.COMMAND_WIDTH,20),this::addRenderableWidget,()->{rows.clear();loaded=loadFailed=false;notice="";resetScroll();load("");rebuildWidgets();}).active=!busy;
  UiActions.close(new NativeLayout.Box(b.x(),dialog.footer().bottom()-20,b.width(),20),this::addRenderableWidget,this::onClose);
 }
 private String description(JsonObject row){var body=row.has("draft")?row.getAsJsonObject("draft"):row.getAsJsonObject("published");String text=Json.str(body,"name")+"\n"+Json.str(row,"author")+"\n"+Json.str(body,"dimension");if(Json.str(row,"type").equals("marker"))return text+"\n"+body.get("x")+", "+body.get("y")+", "+body.get("z");var t=MapTerritoryJson.read(body,UUID.fromString(Json.str(row,"id")));return text+"\n"+Client.tr("map.vertices").getString()+": "+t.points().size()+"\n"+t.points().stream().map(p->p.x()+", "+p.z()).collect(java.util.stream.Collectors.joining("; "));}
 private void details(JsonObject row){minecraft.setScreen(new TextScreen(this,Client.tr("map.publications"),description(row)));}
 private void confirm(JsonObject row,String operation){minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var j=new JsonObject();j.add("id",row.get("id"));j.add("revision",row.get("revision"));request(operation,j);rebuildWidgets();}},Client.tr(switch(operation){case "mapObjectApprove"->"map.approve";case "mapObjectReject"->"map.reject";default->"map.withdraw";}),Component.literal(description(row))));}
 @Override public void receiveCommunity(JsonObject packet){if(!session.receive(packet))return;busy=false;if(packet.has("error")){loadFailed=true;loaded=true;notice=Json.opt(packet,"text",Client.tr("map.saveError").getString());rebuildWidgets();return;}if(packet.has("saved")){MapPublications.refresh();rows.clear();loaded=loadFailed=false;notice="";load("");}else if(packet.has("mapObjects")){admin=packet.get("admin").getAsBoolean();for(var e:packet.getAsJsonArray("mapObjects"))rows.add(e.getAsJsonObject());String cursor=Json.opt(packet,"nextCursor","");if(!cursor.isEmpty()&&rows.size()<MapPublicationLimits.TOTAL)load(cursor);else loaded=true;}rebuildWidgets();}
 @Override public void tick(){if(!MapPublications.available()){onClose();return;}if(session.timeout(System.currentTimeMillis())){busy=false;loaded=true;loadFailed=true;notice=Client.tr("map.filesError").getString();rebuildWidgets();}if(!loaded&&!busy)load("");}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float dt){UiDialog.render(parent,this,g,dt,()->{super.render(g,x,y,dt);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(rows.isEmpty()&&target==null)Ui.text(g,font,Client.tr(busy?"map.loading":"map.noPublications").getString(),dialog.body().x(),dialog.body().y(),UiKit.muted(),false);Ui.text(g,font,UiKit.fit(font,notice,dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.muted(),false);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
