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
 private static final String ADDRESS=System.getenv("RIVET_ACTIVITIES_ADDRESS"),TASK=new UUID(0,301).toString(),EVENT=new UUID(0,302).toString();
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(8,0,2,4,7);
 private static boolean connected,done;private static long reportAt;private static long deadline;private static int tick,stage,frame,checks,flowScale=1;private static JsonObject task,state,layers;private static CommunityLocation location,manual;private static WorldMapScreen map;private static java.util.function.Consumer<JsonObject> fallback;private static boolean removed;
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 private static void press(String label){var mc=Minecraft.getInstance();for(int pass=0;pass<2;pass++){var hit=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).findFirst();if(hit.isPresent()){((Button)hit.get()).onPress();return;}if(mc.screen instanceof ScrollScreen screen){screen.revealRow(1000);screen.rebuildWidgets();}}throw new IllegalStateException("Missing action: "+label);}
 private static void setup(){var mc=Minecraft.getInstance();state=ServerMenuClient.state.deepCopy();layers=MapSettings.INSTANCE.layers.json();CommunityUiHarness.open("normal");ServerMenuClient.state.add("map",state.get("map").deepCopy());fallback=ServerMenuClient.previewTransport;ServerMenuClient.previewTransport=MapActivitiesHarness::request;
  location=new CommunityLocation("Строительство дороги",mc.level.dimension().location().toString(),mc.player.getBlockX()+24,mc.player.getBlockY(),mc.player.getBlockZ()+12,false);
  task=new JsonObject();for(var e:Map.of("id",TASK,"group","","title",location.name(),"description","Задача может существовать без места назначения.","code","MAP1","owner",CommunityScreen.me(),"assignee",CommunityScreen.me(),"status","open").entrySet())task.addProperty(e.getKey(),e.getValue());task.addProperty("revision",1);task.addProperty("manage",true);task.addProperty("dueAt",0);for(String key:List.of("comments","subtasks","resources","stocks"))task.add(key,new JsonArray());task.add("location",location.json());MapActivities.invalidate();map=new WorldMapScreen(null);mc.setScreen(map);map.focusLocation(location);
 }
 private static void request(JsonObject q){String op=Json.opt(q,"op","");if(!op.equals("mapActivities")&&!op.startsWith("work")){fallback.accept(q);return;}var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.add("request",q.get("request"));
  if(op.equals("mapActivities")){String source=Json.str(q,"source");out.addProperty("source",source);var rows=new JsonArray();if(!source.equals("groupmarkers")&&!removed&&(source.equals("events")||task.has("location"))){var row=new JsonObject();row.addProperty("id",source.equals("tasks")?TASK:EVENT);row.addProperty("title",source.equals("tasks")?Json.str(task,"title"):"Встреча строителей");var p=source.equals("tasks")?task.getAsJsonObject("location").deepCopy():location.json();if(source.equals("events"))p.addProperty("x",location.x()+32);row.add("location",p);if(source.equals("events"))row.addProperty("endsAt",System.currentTimeMillis()+60000);else row.addProperty("group","");rows.add(row);}out.add("activities",rows);
  }else if(op.equals("workList")){var rows=new JsonArray();rows.add(task.deepCopy());out.add("tasks",rows);out.addProperty("canCreate",true);}
  else{if(op.equals("workLocation")){if(q.get("location").isJsonNull())task.remove("location");else task.add("location",q.get("location").deepCopy());task.addProperty("revision",task.get("revision").getAsLong()+1);MapActivities.invalidate();}out.add("task",task.deepCopy());out.addProperty("canCreate",true);}
  java.util.concurrent.CompletableFuture.delayedExecutor(30,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->Minecraft.getInstance().execute(()->ServerMenuClient.receive(out)));
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+240000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map activities",ADDRESS,ServerData.Type.OTHER),false,null);}
  if(connected&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Map activities timeout");
  if(System.currentTimeMillis()>reportAt){reportAt=System.currentTimeMillis()+10000;System.out.println("RIVET_ACTIVITIES_PROGRESS stage="+stage+" ready="+WorldMapClient.ready()+" points="+MapActivities.points().size()+" available="+MapActivities.available("tasks"));}
  if(!WorldMapClient.ready()||++tick%8!=0)return;
  if(stage==0){setup();mc.options.guiScale().set(1);mc.resizeDisplay();stage++;return;}
  if(stage==1){if(MapActivities.points().size()!=2)return;var p=MapActivities.points().stream().filter(v->v.source().equals("tasks")).findFirst().orElseThrow();MapActivities.navigate(p);check(DirectionCue.hasTarget(),"Activity route absent");var changed=task.getAsJsonObject("location");changed.addProperty("x",location.x()+40);MapActivities.invalidate();stage++;return;}
  if(stage==2){if(MapActivities.points().size()!=2||DirectionCue.target()==null||DirectionCue.target().x()!=location.x()+40)return;check(true,"Route did not follow destination");removed=true;MapActivities.invalidate();stage++;return;}
  if(stage==3){if(DirectionCue.hasTarget())return;check(MapActivities.points().isEmpty(),"Removed activities retained");removed=false;task.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==4){if(MapActivities.points().size()!=2)return;mc.setScreen(map);map.focusLocation(location);check(MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0),"Task hit absent");press("Открыть задачу");check(mc.screen instanceof TaskScreen,"Task card did not open");stage++;return;}
  if(stage==5){press("Изменить место назначения…");check(mc.screen instanceof LocationEditor,"Destination editor missing");press("Убрать место");stage++;return;}
  if(stage==6){if(task.has("location")||MapActivities.points().size()!=1)return;check(mc.screen instanceof TaskScreen,"Destination removal lost task");press("Добавить место назначения…");press("Моя позиция");press("Готово");stage++;return;}
  if(stage==7){if(!task.has("location"))return;check(true,"Optional destination save");task.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==8){if(MapActivities.points().size()!=2)return;MapLayers.toggle(MapLayers.Layer.TASKS,false);check(!MapLayers.visible(MapLayers.Layer.TASKS,true)&&MapLayers.visible(MapLayers.Layer.EVENTS,true),"Activity layers coupled");mc.setScreen(map);map.focusLocation(location);check(!MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0),"Hidden task remains clickable");MapLayers.toggle(MapLayers.Layer.TASKS,false);var point=MapActivities.points().stream().filter(p->p.source().equals("events")).findFirst().orElseThrow();map.focusLocation(point.location());check(MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,1),"Event hit absent");press("Открыть событие");check(mc.screen instanceof CommunityScreen,"Event card did not open");if(flowScale<3){mc.options.guiScale().set(++flowScale);mc.resizeDisplay();stage=1;task.add("location",location.json());MapActivities.invalidate();mc.setScreen(map);}else stage++;return;}
  if(stage==9){if(MapActivities.points().size()!=2)return;var point=MapActivities.points().stream().filter(p->p.source().equals("tasks")).findFirst().orElseThrow();MapActivities.navigate(point);manual=new CommunityLocation("Другая цель",location.dimension(),location.x()+1000,location.y(),location.z(),false);DirectionCue.start(manual);removed=true;MapActivities.invalidate();stage++;return;}
  if(stage==10){if(!MapActivities.points().isEmpty())return;check(manual.equals(DirectionCue.target()),"Unrelated route stopped");DirectionCue.clear();removed=false;task.add("location",location.json());MapActivities.invalidate();stage++;return;}
  if(stage==11){if(MapActivities.points().size()!=2)return;var features=ServerMenuClient.state.getAsJsonArray("features");features.remove(new JsonPrimitive("map-activities"));check(MapActivities.points().isEmpty(),"Unsupported source leaked points");features.add("map-activities");stage++;}
  if(frame>0){UiGeometryHarness.verify(mc.screen);capture();if(mc.screen instanceof HudInteractionScreen)miniClick();}
  if(frame==FRAMES.size()){restore();done=true;System.out.println("RIVET_ACTIVITIES_OK checks="+checks+" frames="+frame);mc.stop();return;}
  var f=FRAMES.get(frame++);f.apply();switch(f.scene()){
   case 0->{mc.setScreen(map);map.focusLocation(location);}
   case 1->mc.setScreen(new MapLayersScreen(map));
   case 2->{task.add("location",location.json());mc.setScreen(new TaskScreen(map,"",TASK));}
   case 3->mc.setScreen(new LocationEditor(map,location.json(),false,p->{}));
   case 4->{task.remove("location");mc.setScreen(new TaskScreen(map,"",TASK));}
   case 5->mc.setScreen(new CommunityScreen(map,"events",EVENT));
   case 7->mc.setScreen(new HudInteractionScreen(false,null));
   case 6->{task.add("location",location.json());mc.setScreen(map);map.focusLocation(location);MapSharedOverlay.clickWorld(map,location.dimension(),map.view,map.width/2d,map.height/2d,0);}
  }checks++;
 }catch(Throwable ex){System.out.println("RIVET_ACTIVITIES_FAILED stage="+stage+" frame="+frame);ex.printStackTrace();done=true;restore();mc.stop();}}
 private static void miniClick()throws Exception{var mc=Minecraft.getInstance();var f=MapSharedOverlay.class.getDeclaredField("miniHits");f.setAccessible(true);var rows=(java.util.List<?>)f.get(null);check(!rows.isEmpty(),"Minimap activity hit absent");var hit=rows.getFirst();var x=hit.getClass().getDeclaredMethod("x");var y=hit.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);check(Minimap.click(((Number)x.invoke(hit)).doubleValue(),((Number)y.invoke(hit)).doubleValue(),mc.screen),"Minimap activity click failed");check(mc.screen instanceof UiContextPopup,"Minimap activity menu absent");var action=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith("Открыть ")).findFirst().orElseThrow();((Button)action).onPress();check(mc.screen instanceof TaskScreen||mc.screen instanceof CommunityScreen,"Minimap card absent");}
 private static void capture(){String path=System.getenv("RIVET_ACTIVITIES_SCREENSHOTS");if(path!=null){var dir=new java.io.File(path);dir.mkdirs();UiCaptureHarness.grab(dir,"activities-"+frame+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}}
 private static void restore(){MapActivities.reset();DirectionCue.clear();if(state!=null)ServerMenuClient.state=state;ServerMenuClient.previewTransport=null;if(layers!=null)MapSettings.INSTANCE.layers.load(layers);UiPalette.preview(0);try{var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,false);}catch(Exception ignored){}var mc=Minecraft.getInstance();mc.options.guiScale().set(2);mc.resizeDisplay();}
}
