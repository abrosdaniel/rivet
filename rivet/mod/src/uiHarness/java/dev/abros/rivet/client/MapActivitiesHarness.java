package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Real widgets and map renderers against controlled protocol fixtures; DB authorization has its own suite. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapActivitiesHarness {
 private static final String ADDRESS=System.getenv("RIVET_ACTIVITIES_ADDRESS"),MARKER=new UUID(0,301).toString(),SECOND=new UUID(0,302).toString();
 private static final String GROUP=new UUID(0,11).toString();
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(8,0,2,4,7);
 private static boolean connected,done;private static long reportAt;private static long deadline;private static int tick,stage,frame,checks,flowScale=1;private static JsonObject marker,state,layers;private static CommunityLocation location,manual;private static WorldMapScreen map;private static java.util.function.Consumer<JsonObject> fallback;private static boolean removed;
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 private static void press(String label){var mc=Minecraft.getInstance();for(int pass=0;pass<2;pass++){var hit=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).findFirst();if(hit.isPresent()){((Button)hit.get()).onPress();return;}if(mc.screen instanceof ScrollScreen screen){screen.revealRow(1000);screen.rebuildWidgets();}}throw new IllegalStateException("Missing action: "+label);}
 private static void setup(){var mc=Minecraft.getInstance();state=ServerMenuClient.state.deepCopy();layers=MapSettings.INSTANCE.layers.json();CommunityUiHarness.open("normal");ServerMenuClient.state.add("map",state.get("map").deepCopy());fallback=ServerMenuClient.previewTransport;ServerMenuClient.previewTransport=MapActivitiesHarness::request;
  location=new CommunityLocation("Строительство дороги",mc.level.dimension().location().toString(),mc.player.getBlockX()+24,mc.player.getBlockY(),mc.player.getBlockZ()+12,false);
  marker=new JsonObject();for(var e:Map.of("id",MARKER,"group","","title",location.name(),"description","Задача может существовать без места назначения.","code","MAP1","owner",CommunityScreen.me(),"assignee",CommunityScreen.me(),"status","open").entrySet())marker.addProperty(e.getKey(),e.getValue());marker.addProperty("revision",1);marker.addProperty("manage",true);marker.addProperty("dueAt",0);for(String key:List.of("comments","submarkers","resources","stocks"))marker.add(key,new JsonArray());marker.add("location",location.json());MapActivities.invalidate();map=new WorldMapScreen(null);mc.setScreen(map);map.focusLocation(location);
 }
 private static void request(JsonObject q){
  if(!Json.opt(q,"op","").equals("mapActivities")){fallback.accept(q);return;}
  var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.add("request",q.get("request"));
  String source=Json.str(q,"source");check(source.equals("groupmarkers"),"Retired activity source requested: "+source);out.addProperty("source",source);
  var rows=new JsonArray();if(!removed){
   if(marker.has("location")){var row=new JsonObject();row.addProperty("id",MARKER);row.addProperty("title",Json.str(marker,"title"));row.addProperty("group",GROUP);row.add("location",marker.get("location").deepCopy());rows.add(row);}
   var row=new JsonObject();row.addProperty("id",SECOND);row.addProperty("title","Место встречи");row.addProperty("group",GROUP);var point=location.json();point.addProperty("x",location.x()+32);row.add("location",point);rows.add(row);
  }out.add("activities",rows);
  java.util.concurrent.CompletableFuture.delayedExecutor(30,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->Minecraft.getInstance().execute(()->ServerMenuClient.receive(out)));
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+240000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map activities",ADDRESS,ServerData.Type.OTHER),false,null);}
  if(connected&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Map activities timeout");
  if(System.currentTimeMillis()>reportAt){reportAt=System.currentTimeMillis()+10000;System.out.println("RIVET_ACTIVITIES_PROGRESS stage="+stage+" ready="+WorldMapClient.ready()+" points="+MapActivities.points().size()+" available="+MapActivities.available("groupmarkers"));}
  if(!WorldMapClient.ready()||++tick%8!=0)return;
  if(stage==0){setup();mc.options.guiScale().set(1);mc.resizeDisplay();stage++;return;}
  if(stage==1){if(MapActivities.points().size()!=2)return;var p=MapActivities.points().stream().filter(v->v.id().equals(MARKER)).findFirst().orElseThrow();MapActivities.navigate(p);check(DirectionCue.hasTarget(),"Activity route absent");var changed=marker.getAsJsonObject("location");changed.addProperty("x",location.x()+40);MapActivities.invalidate();stage++;return;}
  if(stage==2){if(MapActivities.points().size()!=2||DirectionCue.target()==null||DirectionCue.target().x()!=location.x()+40)return;check(true,"Route did not follow destination");removed=true;MapActivities.invalidate();stage++;return;}
  if(stage==3){if(DirectionCue.hasTarget())return;check(MapActivities.points().isEmpty(),"Removed activities retained");removed=false;marker.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==4){if(MapActivities.points().size()!=2)return;mc.setScreen(map);map.focusLocation(location);check(MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0),"Group marker hit absent");press(Client.text("ui.open_group_63d4261f"));check(mc.screen instanceof CommunityScreen,"Group card did not open");stage++;return;}
  if(stage==5){marker.remove("location");MapActivities.invalidate();stage++;return;}
  if(stage==6){if(MapActivities.points().size()!=1)return;check(mc.screen instanceof CommunityScreen,"Background marker removal lost group view");marker.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==7){if(MapActivities.points().size()!=2)return;check(MapActivities.points().stream().anyMatch(p->p.id().equals(MARKER)&&p.location().x()==location.x()),"Restored group marker not refreshed");stage++;return;}
  if(stage==8){if(MapActivities.points().size()!=2)return;MapLayers.toggle(MapLayers.Layer.GROUPMARKERS,false);check(!MapLayers.visible(MapLayers.Layer.GROUPMARKERS,true)&&MapLayers.visible(MapLayers.Layer.PLACES,true),"Activity layers coupled");mc.setScreen(map);map.focusLocation(location);check(!MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0),"Hidden group marker remains clickable");MapLayers.toggle(MapLayers.Layer.GROUPMARKERS,false);var point=MapActivities.points().stream().filter(p->p.id().equals(SECOND)).findFirst().orElseThrow();map.focusLocation(point.location());check(MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,1),"Second marker hit absent");press(Client.text("ui.open_group_63d4261f"));check(mc.screen instanceof CommunityScreen,"Group card did not open");if(flowScale<3){mc.options.guiScale().set(++flowScale);mc.resizeDisplay();stage=1;marker.add("location",location.json());MapActivities.invalidate();mc.setScreen(map);}else stage++;return;}
  if(stage==9){if(MapActivities.points().size()!=2)return;var point=MapActivities.points().stream().filter(p->p.id().equals(MARKER)).findFirst().orElseThrow();MapActivities.navigate(point);manual=new CommunityLocation("Другая цель",location.dimension(),location.x()+1000,location.y(),location.z(),false);DirectionCue.start(manual);removed=true;MapActivities.invalidate();stage++;return;}
  if(stage==10){if(!MapActivities.points().isEmpty())return;check(manual.equals(DirectionCue.target()),"Unrelated route stopped");DirectionCue.clear();removed=false;marker.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==11){if(MapActivities.points().size()!=2)return;var features=ServerMenuClient.state.getAsJsonArray("features");features.remove(new JsonPrimitive("map-activities"));check(MapActivities.points().isEmpty(),"Unsupported source leaked points");features.add("map-activities");stage++;}
  if(frame>0){UiGeometryHarness.verify(mc.screen);capture();if(mc.screen instanceof HudInteractionScreen)miniClick();}
  if(frame==FRAMES.size()){restore();done=true;System.out.println("RIVET_ACTIVITIES_OK checks="+checks+" frames="+frame);mc.stop();return;}
  var f=FRAMES.get(frame++);f.apply();switch(f.scene()){
   case 0->{mc.setScreen(map);map.focusLocation(location);}
   case 1->mc.setScreen(new MapLayersScreen(map));
   case 2->{marker.add("location",location.json());mc.setScreen(new CommunityScreen(map,"groups",GROUP));}
   case 3->mc.setScreen(new LocationEditor(map,location.json(),false,p->{}));
   case 4->{marker.remove("location");mc.setScreen(new CommunityScreen(map,"groups",GROUP));}
   case 5->mc.setScreen(new CommunityScreen(map,"groups",GROUP));
   case 7->mc.setScreen(new HudInteractionScreen(false,null));
   case 6->{marker.add("location",location.json());mc.setScreen(map);map.focusLocation(location);MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0);}
  }checks++;
 }catch(Throwable ex){System.out.println("RIVET_ACTIVITIES_FAILED stage="+stage+" frame="+frame);ex.printStackTrace();done=true;restore();mc.stop();}}
 private static void miniClick()throws Exception{var mc=Minecraft.getInstance();var f=MapSharedOverlay.class.getDeclaredField("miniHits");f.setAccessible(true);var rows=(java.util.List<?>)f.get(null);check(!rows.isEmpty(),"Minimap activity hit absent");var hit=rows.getFirst();var x=hit.getClass().getDeclaredMethod("x");var y=hit.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);check(Minimap.click(((Number)x.invoke(hit)).doubleValue(),((Number)y.invoke(hit)).doubleValue(),mc.screen),"Minimap activity click failed");check(mc.screen instanceof UiContextPopup,"Minimap activity menu absent");var action=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Открыть ")).findFirst().orElseThrow();((Button)action).onPress();check(mc.screen instanceof CommunityScreen,"Minimap card absent");}
 private static void capture(){String path=System.getenv("RIVET_ACTIVITIES_SCREENSHOTS");if(path!=null){var dir=new java.io.File(path);dir.mkdirs();UiCaptureHarness.grab(dir,"activities-"+frame+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}}
 private static void restore(){MapActivities.reset();DirectionCue.clear();if(state!=null)ServerMenuClient.state=state;ServerMenuClient.previewTransport=null;if(layers!=null)MapSettings.INSTANCE.layers.load(layers);UiPalette.preview(0);try{var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,false);}catch(Exception ignored){}var mc=Minecraft.getInstance();mc.options.guiScale().set(2);mc.resizeDisplay();}
}
