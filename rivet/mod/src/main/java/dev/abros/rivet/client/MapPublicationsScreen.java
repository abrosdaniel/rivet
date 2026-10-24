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
 private boolean mine;
 static MapPublicationsScreen mine(Screen parent){var screen=new MapPublicationsScreen(parent);screen.mine=true;return screen;}
 static MapPublicationsScreen moderation(Screen parent){var screen=new MapPublicationsScreen(parent);screen.filter=1;return screen;}
 private UiDialog dialog;private boolean busy,admin,loaded,loadFailed;private String notice="";
 MapPublicationsScreen(Screen parent){this(parent,null,"",null);}
 MapPublicationsScreen(Screen parent,MapMarker m){this(parent,m.id(),"marker",MapMarkerLink.json(m));}
 MapPublicationsScreen(Screen parent,MapTerritory t){this(parent,t.id(),"territory",MapTerritoryJson.write(t));}
 private MapPublicationsScreen(Screen parent,UUID id,String type,JsonObject draft){super(Client.tr("map.publications"));this.parent=parent;target=id;this.type=type;this.draft=draft;}
 private void request(String op,JsonObject body){if(busy)return;body.addProperty("action","community");body.addProperty("section","home");body.addProperty("op",op);busy=true;ServerMenuClient.request(session.begin(body,!op.equals("mapObjects"),System.currentTimeMillis()));}
 private void load(String cursor){var j=new JsonObject();j.addProperty("manage",true);j.addProperty("mine",mine);j.addProperty("cursor",cursor);if(target!=null)j.addProperty("id",target.toString());request("mapObjects",j);}
 private int filter;private String query="";private final List<Runnable> textRows=new ArrayList<>();private GuiGraphics drawing;
 private List<JsonObject> selected(){return rows.stream().filter(r->target==null||Json.str(r,"id").equals(target.toString())).filter(r->target!=null||filter==0||filter==1&&r.has("draft")||filter==2&&r.has("published")).filter(r->target!=null||Json.str(payload(r),"name").toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();}
 private JsonObject payload(JsonObject row){return row.has("draft")?row.getAsJsonObject("draft"):row.getAsJsonObject("published");}
 private String status(JsonObject row){return row==null?"map.privateObject":row.has("draft")?(row.has("published")?"map.pendingChanges":"map.awaitingApproval"):"map.published";}
 private String hint(JsonObject row){return row==null?"map.publicationPrivateHint":row.has("draft")?(row.has("published")?"map.publicationChangesHint":"map.publicationPendingHint"):"map.publicationLiveHint";}
 private String location(JsonObject body,String type){return Client.tr(type.equals("marker")?"map.markerType":"map.territory").getString()+" · "+Json.str(body,"dimension")+(type.equals("marker")?" · "+body.get("x")+", "+body.get("y")+", "+body.get("z"):"");}
 private void action(NativeLayout.Box box,String key,UiActions.Tone tone,Runnable run){addRenderableWidget(UiActions.button(Client.tr(key),tone,"",ignored->run.run()).bounds(box.x(),box.y(),box.width(),20).build()).active=loaded&&!busy&&!loadFailed;}
 private void line(List<java.util.function.Consumer<NativeLayout.Box>> content,Component text,int color){
  for(var part:font.split(text,Math.max(40,dialog.body().width()-16)))content.add(box->textRows.add(()->drawing.drawString(font,part,box.x(),box.y()+5,color,false)));
 }
 @Override protected void init(){dialog=UiDialog.fit(width,height,440,390);textRows.clear();var b=dialog.body();var selected=selected();
  if(target==null){
   int half=(b.width()-8)/2;
   addRenderableWidget(UiFields.text(font,b.x(),b.y(),half,20,Client.tr("map.publicationSearch").getString(),Client.tr("map.publicationSearch").getString(),80,query,v->{query=v;resetScroll();rebuildWidgets();}));
   String[] filters={"map.publicationAll","map.awaitingApproval","map.published"};
   addRenderableWidget(UiActions.button(Client.tr(filters[filter]),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,Client.tr("map.publications").getString(),Arrays.stream(filters).map(key->Client.tr(key).getString()).toList(),selectedFilter->{filter=selectedFilter;resetScroll();rebuildWidgets();},button).current(filter))).bounds(b.x()+half+8,b.y(),b.width()-half-8,20).build());
   var viewport=new NativeLayout.Box(b.x(),b.y()+28,b.width(),Math.max(0,b.height()-28));var area=scrollForm(selected.size(),viewport,68);
   for(int n=firstRow;n<Math.min(selected.size(),firstRow+visibleRows);n++){var row=selected.get(n);var body=payload(row);addRenderableWidget(UiSummaryCard.compact(b.x(),viewport.y()+(n-firstRow)*68,area.content().width(),Json.str(body,"name"),Client.tr(status(row)).getString(),List.of(location(body,Json.str(row,"type"))),UiKit.accent(),()->details(row))).active=!busy;}
   if(selected.isEmpty()){String key=busy?"map.loading":loadFailed?"map.filesError":rows.isEmpty()?"map.publicationEmptyHint":"map.publicationNoResults";var kind=busy?UiState.Kind.LOADING:loadFailed?UiState.Kind.ERROR:rows.isEmpty()?UiState.Kind.EMPTY:UiState.Kind.FILTERED;textRows.add(()->UiState.draw(drawing,font,kind,Client.tr("map.publications").getString(),Client.tr(key).getString(),viewport.x(),viewport.y(),viewport.width(),viewport.bottom()));}
  }else{
   var old=selected.isEmpty()?null:selected.getFirst();var body=draft!=null?draft:old==null?null:payload(old);
   var content=new ArrayList<java.util.function.Consumer<NativeLayout.Box>>();
   if(body!=null){line(content,Component.literal(Json.str(body,"name")).withStyle(net.minecraft.ChatFormatting.BOLD),UiKit.text());line(content,Component.literal(location(body,type)),UiKit.muted());}
   line(content,Client.tr(!loaded?"map.loading":loadFailed?"map.filesError":status(old)),UiKit.accent());
   if(loaded&&!loadFailed)line(content,Client.tr(hint(old)),UiKit.muted());
   if(draft!=null){boolean unchanged=old!=null&&draft.equals(payload(old));String key=old!=null&&old.has("published")?"map.submitChanges":"map.submitPublication";
    content.add(box->{action(box,key,UiActions.Tone.PRIMARY,()->{var j=new JsonObject();j.addProperty("id",target.toString());j.addProperty("type",type);j.add("draft",draft);j.addProperty("revision",old==null?0:old.get("revision").getAsLong());request("mapObjectSubmit",j);rebuildWidgets();});if(unchanged){var widget=(net.minecraft.client.gui.components.AbstractWidget)children().getLast();widget.active=false;widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Client.tr("map.publicationUnchanged")));}});
   }
   if(old!=null){
    content.add(box->action(box,"map.publicationDetails",UiActions.Tone.NORMAL,()->minecraft.setScreen(new TextScreen(this,Client.tr("map.publications"),description(old)))));
    if(old.has("draft")&&old.has("published"))content.add(box->action(box,"map.publicationPrevious",UiActions.Tone.NORMAL,()->{var previous=old.deepCopy();previous.remove("draft");minecraft.setScreen(new TextScreen(this,Client.tr("map.publicationPrevious"),description(previous)));}));
    if(admin&&old.has("draft"))content.add(box->{int half=(box.width()-6)/2;action(new NativeLayout.Box(box.x(),box.y(),half,20),"map.approve",UiActions.Tone.PRIMARY,()->confirm(old,"mapObjectApprove"));action(new NativeLayout.Box(box.x()+half+6,box.y(),box.width()-half-6,20),"map.reject",UiActions.Tone.DANGER,()->confirm(old,"mapObjectReject"));});
    content.add(box->action(box,old.has("published")?"map.withdraw":"map.cancelPublication",UiActions.Tone.DANGER,()->confirm(old,"mapObjectWithdraw")));
   }
   var area=scrollForm(content.size(),b,24);for(int n=firstRow;n<Math.min(content.size(),firstRow+visibleRows);n++)content.get(n).accept(new NativeLayout.Box(b.x(),b.y()+(n-firstRow)*24,area.content().width(),24));
  }
  UiActions.command(UiActions.Command.REFRESH,new NativeLayout.Box(b.x(),dialog.footer().bottom()-20,UiActions.COMMAND_WIDTH,20),this::addRenderableWidget,()->{rows.clear();loaded=loadFailed=false;notice="";resetScroll();load("");rebuildWidgets();}).active=!busy;
  UiActions.close(new NativeLayout.Box(b.x(),dialog.footer().bottom()-20,b.width(),20),this::addRenderableWidget,this::onClose);
 }
 private String description(JsonObject row){var body=row.has("draft")?row.getAsJsonObject("draft"):row.getAsJsonObject("published");String text=Json.str(body,"name")+"\n"+Json.str(row,"author")+"\n"+Json.str(body,"dimension");if(Json.str(row,"type").equals("marker"))return text+"\n"+body.get("x")+", "+body.get("y")+", "+body.get("z");var t=MapTerritoryJson.read(body,UUID.fromString(Json.str(row,"id")));return text+"\n"+Client.tr("map.vertices").getString()+": "+t.points().size()+"\n"+t.points().stream().map(p->p.x()+", "+p.z()).collect(java.util.stream.Collectors.joining("; "));}
 private void details(JsonObject row){minecraft.setScreen(new MapPublicationsScreen(this,UUID.fromString(Json.str(row,"id")),Json.str(row,"type"),null));}
 private void confirm(JsonObject row,String operation){minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var j=new JsonObject();j.add("id",row.get("id"));j.add("revision",row.get("revision"));request(operation,j);rebuildWidgets();}},Client.tr(switch(operation){case "mapObjectApprove"->"map.approve";case "mapObjectReject"->"map.reject";default->row.has("published")?"map.withdraw":"map.cancelPublication";}),Component.literal(description(row)+"\n\n"+Client.tr(operation.equals("mapObjectWithdraw")?(row.has("published")?"map.publicationWithdrawHint":"map.publicationCancelHint"):operation.equals("mapObjectReject")?"map.publicationRejectHint":"map.publicationApproveHint").getString())));}
 @Override public void receiveCommunity(JsonObject packet){if(!session.receive(packet))return;busy=false;if(packet.has("error")){loadFailed=true;loaded=true;notice=Json.opt(packet,"text",Client.tr("map.saveError").getString());rebuildWidgets();return;}if(packet.has("saved")){if(parent instanceof MapPublicationsScreen list){list.rows.clear();list.loaded=false;list.loadFailed=false;}MapPublications.refresh();rows.clear();loaded=loadFailed=false;notice=Client.tr("map.publicationSaved").getString();load("");}else if(packet.has("mapObjects")){admin=packet.get("admin").getAsBoolean();for(var e:packet.getAsJsonArray("mapObjects"))rows.add(e.getAsJsonObject());String cursor=Json.opt(packet,"nextCursor","");if(!cursor.isEmpty()&&rows.size()<MapPublicationLimits.TOTAL)load(cursor);else loaded=true;}rebuildWidgets();}
 @Override public void tick(){if(!MapPublications.available()){session.cancel();minecraft.setScreen(parent);return;}if(session.timeout(System.currentTimeMillis())){busy=false;loaded=true;loadFailed=true;notice=Client.tr("map.filesError").getString();rebuildWidgets();}if(!loaded&&!busy)load("");}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float dt){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float dt){UiDialog.render(parent,this,g,dt,()->{super.render(g,x,y,dt);UiHeading.dialog(g,font,target==null?Client.tr(admin&&!mine?"map.publicationModeration":"map.myPublications"):title,dialog.header().x(),dialog.frame().y(),dialog.header().width());drawing=g;textRows.forEach(Runnable::run);drawing=null;Ui.text(g,font,UiKit.fit(font,notice,dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.muted(),false);});}
 @Override public void onClose(){if(busy&&session.command()!=null&&MenuRequests.mutation(session.command())){notice=Client.text("ui.wait_for_the_server_to_respond_43e44ea8");return;}session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
