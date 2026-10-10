package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapTerritoriesHarness {
 private static final String ADDRESS=System.getenv("RIVET_TERRITORIES_ADDRESS");
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(12,0,2,4,10);
 private static boolean connected,done,reportedScreen;private static int tick,frame,checks;private static long deadline;
 private static JsonObject state;private static java.util.function.Consumer<JsonObject> fallback;private static final String GROUP=new UUID(0,800).toString();private static long revision=1;private static int peerRequests;private static boolean initialized;private static com.google.gson.JsonObject layers;private static MapTerritory fixture;private static WorldMapScreen map;private static boolean round;
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;}
 private static void press(String key){((Button)Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Client.tr(key).getString())).findFirst().orElseThrow(()->new IllegalStateException("No button "+key))).onPress();}
 private static void capture(){String path=System.getenv("RIVET_TERRITORIES_SCREENSHOTS");if(path!=null){var dir=new java.io.File(path);dir.mkdirs();UiCaptureHarness.grab(dir,"territories-"+frame+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}}
 private static void flow(){var mc=Minecraft.getInstance();map=new WorldMapScreen(null);mc.setScreen(map);int x=mc.player.getBlockX(),z=mc.player.getBlockZ();map.view.center(x,z);map.view.zoomAt(4,map.width/2d,map.height/2d,map.width/2d,map.height/2d);
  var target=DirectionCue.target();DirectionCue.clear();map.tick();var expected=java.util.List.of("map.center","map.add","map.markers","map.zoomIn","map.zoomOut","map.layers","map.territories","map.ruler").stream().map(k->Client.tr(k).getString()).toList();check(map.children().stream().filter(c->c instanceof Button).limit(8).map(c->((Button)c).getMessage().getString()).toList().equals(expected),"Wrong toolbar order");check(map.children().stream().noneMatch(c->c instanceof Button button&&button.getMessage().equals(Client.tr("map.stop"))),"Inactive stop visible");DirectionCue.start(new dev.abros.rivet.core.CommunityLocation("test",map.selectedDimension(),x+100,64,z+100,false));map.tick();check(((Button)map.children().getFirst()).getMessage().equals(Client.tr("map.stop")),"Active stop not first");press("map.stop");check(!DirectionCue.hasTarget()&&((Button)map.children().getFirst()).getMessage().equals(Client.tr("map.center")),"Stop leaves gap");if(target!=null)DirectionCue.start(target);
  var card=new CommunityScreen(null,"groups",GROUP);mc.setScreen(card);card.data.add("detail",groupCard());card.loaded=true;card.refreshUi();
  var create=card.rows.stream().filter(r->r.label().equals("Создать территорию…")).findFirst().orElseThrow(()->new IllegalStateException("Group creation action absent"));
  card.revealRow(card.rows.indexOf(create));card.rebuildWidgets();var createButton=(Button)card.children().stream().filter(w->w instanceof Button b&&b.getMessage().getString().startsWith("Создать территорию")).findFirst().orElseThrow();createButton.onPress();check(mc.screen instanceof WorldMapScreen,"Group button did not open map");map=(WorldMapScreen)mc.screen;map.tick();map.view.zoomAt(4,map.width/2d,map.height/2d,map.width/2d,map.height/2d);

  for(int[] offset:new int[][]{{-6,-6},{6,-6},{6,6},{-6,6}}){double sx=map.view.screenX(x+offset[0],map.width/2d),sy=map.view.screenZ(z+offset[1],map.height/2d);check(map.mouseClicked(sx,sy,0),"Canvas click rejected");map.mouseReleased(sx,sy,0);if(offset[0]==6&&offset[1]==-6){double fx=map.view.screenX(x-6,map.width/2d),fy=map.view.screenZ(z-6,map.height/2d);map.mouseClicked(fx,fy,0);map.mouseReleased(fx,fy,0);check(mc.screen==map,"Two vertices closed territory");}}
  map.keyPressed(257,0,0);check(mc.screen==map,"Enter closed contour");double fx=map.view.screenX(x-6,map.width/2d),fy=map.view.screenZ(z-6,map.height/2d);map.mouseClicked(fx,fy,0);map.mouseReleased(fx,fy,0);check(mc.screen instanceof MapTerritoryScreen,"First vertex did not close contour");check(mc.screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.EditBox b&&b.getValue().equals("Тестовое объединение")),"Territory name editable");check(mc.screen.children().stream().filter(c->c instanceof UiColorSwatch).count()==MapMarkerPanel.COLOURS.length,"Missing marker palette");((UiColorSwatch)mc.screen.children().stream().filter(c->c instanceof UiColorSwatch).skip(1).findFirst().orElseThrow()).onPress();press("map.save");check(mc.screen==map,"Save did not return to map");fixture=MapGroupClient.territories().getLast();check(fixture.points().size()==4&&fixture.contains(x,z)&&fixture.color()==MapMarkerPanel.COLOURS[1],"Canvas polygon incorrect");
  for(String section:List.of("home","groups","events")){mc.setScreen(new CommunityScreen(null,section,""));ServerMenuClient.receive(dev.abros.rivet.core.MenuChangeHint.subscription(section,""));check(MapGroupClient.territories().contains(fixture),"Menu subscription erased territory: "+section);mc.setScreen(map);check(MapGroupClient.territories().contains(fixture),"Menu close erased territory");}
  var changed=new JsonObject();changed.addProperty("kind","changed");changed.addProperty("section","home");ServerMenuClient.receive(changed);check(MapGroupClient.territories().contains(fixture),"Personal task update erased territory");changed.addProperty("section","groups");ServerMenuClient.receive(changed);check(MapGroupClient.territories().isEmpty(),"Real group change did not invalidate access");MapGroupClient.tick();check(MapGroupClient.territories().contains(fixture),"Territory failed to reload");
  var draft=draft();draft.color=0xff123456;mc.setScreen(new MapTerritoryScreen(map,draft));mc.screen.onClose();check(MapGroupClient.territories().contains(fixture),"Cancel changed saved polygon");
  draft=draft();draft.color=0xffabcdef;mc.setScreen(new MapTerritoryScreen(map,draft));press("map.save");fixture=MapGroupClient.territories().stream().filter(t->t.id().equals(draftId())).findFirst().orElseThrow();check(fixture.name().equals("Тестовое объединение")&&fixture.color()==0xffabcdef,"Group name or color incorrect");
  var invalid=draft();invalid.points.clear();mc.setScreen(new MapTerritoryScreen(map,invalid));press("map.save");check(mc.screen instanceof MapTerritoryScreen&&MapGroupClient.territories().contains(fixture),"Invalid draft overwritten saved polygon");mc.screen.onClose();
  var marker=new MapMarker(UUID.randomUUID(),fixture.dimension(),"test",x,64,z,0,"none");MapSettings.INSTANCE.layers.set(MapLayers.Layer.MARKERS,false,false);check(!WorldMapClient.visible(marker,false)&&!WorldMapClient.visible(marker,true),"Layer surfaces differ");MapSettings.INSTANCE.layers.load(layers);
  mc.setScreen(new MapLayersScreen(map));boolean prior=MapLayers.visible(MapLayers.Layer.TERRITORIES,false);press("map.layer.territories");check(MapLayers.visible(MapLayers.Layer.TERRITORIES,false)!=prior,"Layer toggle did not act");check(MapLayers.visible(MapLayers.Layer.TERRITORIES,true)!=prior,"Minimap layer did not follow world");press("map.layer.territories");mc.screen.onClose();check(mc.screen==map,"Layers lost parent");
  var r=MapRenderSettings.INSTANCE;var display=r.json();r.radarWorld=false;r.radar=false;r.players=false;MapLayers.toggle(MapLayers.Layer.PLAYERS,false);check(MapLayers.visible(MapLayers.Layer.PLAYERS,false)&&MapLayers.visible(MapLayers.Layer.PLAYERS,true)&&!MapLayers.visible(MapLayers.Layer.MOBS,false),"Releasing old radar master enabled unrelated surfaces");r.load(display);MapSettings.INSTANCE.layers.load(layers);
  var legacy=new com.google.gson.JsonObject();var old=new com.google.gson.JsonObject();old.addProperty("map",true);old.addProperty("mini",false);legacy.add("MARKERS",old);var migrated=new MapLayers();migrated.load(legacy);check(!migrated.shown(MapLayers.Layer.MARKERS,false)&&!migrated.shown(MapLayers.Layer.MARKERS,true),"Split legacy visibility was not merged");
  var copy=new MapLayers();copy.load(MapSettings.INSTANCE.layers.json());for(var layer:MapLayers.Layer.values())for(boolean mini:new boolean[]{false,true})check(copy.shown(layer,mini)==MapSettings.INSTANCE.layers.shown(layer,mini),"Layer preferences not round-tripped");
 }
 private static JsonObject groupCard(){try{var method=CommunityUiHarness.class.getDeclaredMethod("entry",String.class,int.class);method.setAccessible(true);var j=(JsonObject)method.invoke(null,"groups",0);j.addProperty("id",GROUP);j.addProperty("title","Тестовое объединение");j.addProperty("revision",revision);j.addProperty("isMember",true);j.remove("location");return j;}catch(Exception ex){throw new IllegalStateException(ex);}}

 private static MapTerritories.Draft draft(){return new MapTerritories.Draft(new MapGroupClient.Group(GROUP,"Тестовое объединение",revision,true,fixture),fixture.dimension());}
 private static void setup(){state=ServerMenuClient.state.deepCopy();CommunityUiHarness.open("normal");ServerMenuClient.state.add("map",state.get("map").deepCopy());var features=ServerMenuClient.state.getAsJsonArray("features");features.add("group-map");features.add("map-activities");ServerMenuClient.state.add("modules",new JsonObject());fallback=ServerMenuClient.previewTransport;ServerMenuClient.previewTransport=MapTerritoriesHarness::request;MapGroupClient.reset();MapGroupClient.tick();}
 private static void request(JsonObject q){String op=Json.opt(q,"op","");var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","groups");out.add("request",q.get("request"));
  if(op.equals("detail")&&Json.opt(q,"section","").equals("groups")){out.add("detail",groupCard());out.add("config",dev.abros.rivet.core.CommunityStore.defaults());out.add("groups",new JsonArray());out.add("names",new JsonObject());out.add("related",new JsonArray());out.addProperty("nextCursor","");out.addProperty("unread",0);out.add("preferences",new JsonObject());}
  else if(op.equals("groupMap")){var row=new JsonObject();row.addProperty("id",GROUP);row.addProperty("title","Тестовое объединение");row.addProperty("revision",revision);row.addProperty("manage",true);if(fixture!=null)row.add("territory",MapTerritoryJson.write(fixture));var rows=new JsonArray();rows.add(row);out.add("territories",rows);}
  else if(op.equals("plusTerritorySave")){check(q.get("revision").getAsLong()==revision,"Stale editor revision");fixture=q.get("clearTerritory").getAsBoolean()?null:MapTerritoryJson.read(q.getAsJsonObject("territory"),UUID.fromString(GROUP));revision++;}
  else if(op.equals("mapActivities")){out.addProperty("source",Json.str(q,"source"));out.add("activities",new JsonArray());}
  else {if(op.equals("toolsMapPeers"))peerRequests++;fallback.accept(q);return;}
  ServerMenuClient.receive(out);if(op.equals("plusTerritorySave"))MapGroupClient.tick();
 }
 private static void gates(){var mc=Minecraft.getInstance();var modules=ServerMenuClient.state.getAsJsonObject("modules");modules.addProperty("groups",false);modules.addProperty("events",false);modules.addProperty("tasks",false);map.tick();check(!MapLayers.available(MapLayers.Layer.PEERS)&&!MapLayers.available(MapLayers.Layer.TERRITORIES)&&!MapLayers.available(MapLayers.Layer.GROUPMARKERS),"Disabled group layers remain");check(MapGroupClient.territories().isEmpty(),"Disabled territory remains");int before=peerRequests;MapLayerClient.tick();MapGroupClient.tick();check(before==peerRequests,"Disabled peer polling");check(map.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().equals(Client.tr("map.territories"))),"Disabled territory tool remains");mc.setScreen(new MapLayersScreen(map));for(String key:List.of("peers","territories","groupmarkers","tasks","events"))check(mc.screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().equals(Client.tr("map.layer."+key))),"Disabled switch remains: "+key);modules.addProperty("groups",true);modules.addProperty("events",true);modules.addProperty("tasks",true);MapGroupClient.reset();MapGroupClient.tick();}
 private static UUID draftId(){return fixture.id();}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(mc.level==null){if(!reportedScreen&&mc.screen!=null){reportedScreen=true;System.out.println("RIVET_TERRITORIES_START_SCREEN "+mc.screen.getClass().getSimpleName());}if(mc.screen instanceof TitleScreen&&!connected){connected=true;deadline=System.currentTimeMillis()+240000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Territories",ADDRESS,ServerData.Type.OTHER),false,null);}return;}
  if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Territory harness timeout");
  if(!WorldMapClient.markersReady()||++tick%8!=0)return;
  if(!initialized){initialized=true;setup();layers=MapSettings.INSTANCE.layers.json();round=MapSettings.INSTANCE.round;flow();}
  if(frame>0){UiGeometryHarness.verify(mc.screen);capture();}
  if(frame==FRAMES.size()){for(int scale=1;scale<=3;scale++){mc.options.guiScale().set(scale);mc.resizeDisplay();fixture=null;MapGroupClient.reset();MapGroupClient.tick();flow();}gates();restore();done=true;System.out.println("RIVET_TERRITORIES_OK checks="+checks+" frames="+frame);mc.stop();return;}
  var f=FRAMES.get(frame++);f.apply();map=new WorldMapScreen(null);mc.setScreen(map);map.focusTerritory(fixture);
  switch(f.scene()){
   case 0->mc.setScreen(new MapLayersScreen(map));
   case 1->{var layersScreen=new MapLayersScreen(map);mc.setScreen(layersScreen);layersScreen.revealRow(7);layersScreen.rebuildWidgets();}
   case 2->mc.setScreen(new MapTerritoriesScreen(map));
   case 3->{var editor=new MapTerritoryScreen(map,draft());mc.setScreen(editor);editor.revealRow(7);editor.rebuildWidgets();}
   case 4->{}
   case 5->{MapSettings.INSTANCE.round=true;mc.setScreen(new HudInteractionScreen(false,null));}
   default->mc.setScreen(new MapSettingsScreen(map,f.scene()-6));
  }
  UiGeometryHarness.verify(mc.screen);checks++;
 }catch(Throwable ex){done=true;System.out.println("RIVET_TERRITORIES_FAILED frame="+frame);ex.printStackTrace();restore();mc.stop();}}
 private static void restore(){ServerMenuClient.previewTransport=null;if(state!=null)ServerMenuClient.state=state;MapGroupClient.reset();if(initialized){MapSettings.INSTANCE.layers.load(layers);MapSettings.INSTANCE.round=round;MapSettings.INSTANCE.save();}UiPalette.preview(0);try{var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,false);}catch(Exception ignored){}var mc=Minecraft.getInstance();mc.options.guiScale().set(2);mc.resizeDisplay();}
}
