package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.map.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapImportHarness {
 private static final String ADDRESS=System.getenv("RIVET_IMPORT_ADDRESS");
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(1,0);
 private static int stage,ticks,frame,checks;private static boolean connected,done;private static long deadline;
 private static JsonObject original;private static java.util.function.Consumer<JsonObject> network;private static MapImportScreen screen;private static Path files;private static UUID target;
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 private static Button find(String label){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().startsWith(label)).map(c->(Button)c).findFirst().orElseThrow(()->new IllegalStateException("Missing "+label));}
 private static void press(String label){var b=find(label);check(b.active,"Inactive "+label);b.onPress();}
 private static Object field(String name)throws Exception {var f=MapImportScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
 private static void fixtures()throws Exception {
  files=Files.createTempDirectory("rivet-import-fixtures-");StringBuilder txt=new StringBuilder("#Xaero waypoints\nsets:gui.xaero_default\n");for(int n=0;n<18;n++)txt.append("waypoint:Метка ").append(n).append(":М:").append(n).append(":64:2:12:false:0:gui.xaero_default:false:0:0:false\n");txt.append("waypoint:Метка 0:М:0:64:2:12:false:0:gui.xaero_default:false:0:0:false\n");Files.writeString(files.resolve("waypoints.txt"),txt);
  var root=new CompoundTag();var points=new CompoundTag();for(int n=0;n<2;n++){var p=new CompoundTag();p.putString("version","1");p.putString("name",n==0?"DAT":"Метка 0");var pos=new CompoundTag();pos.putInt("x",0);pos.putInt("y",64);pos.putInt("z",2);pos.putString("dimension","minecraft:overworld");p.put("pos",pos);p.putInt("color",0x123456);var settings=new CompoundTag();settings.putBoolean("enable",true);settings.putBoolean("showOnMap",true);p.put("settings",settings);points.put(UUID.randomUUID().toString(),p);}root.put("waypoints",points);root.put("groups",new CompoundTag());NbtIo.write(root,files.resolve("WaypointData.dat"));
  check(MapImportScreen.readDat(Files.readAllBytes(files.resolve("WaypointData.dat"))).getAsJsonObject("waypoints").size()==2,"NBT bridge");
  try{MapImportScreen.readDat(new byte[]{10,0,0,7,0,1,120,127,-1,-1,-1});throw new IllegalStateException("Unbounded NBT array accepted");}catch(java.io.IOException|net.minecraft.nbt.NbtAccounterException expected){checks++;}
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+240000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Import",ADDRESS,ServerData.Type.OTHER),false,null);}
  if(connected&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Timeout stage="+stage+" frame="+frame);
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady()||++ticks%5!=0)return;
  if(stage==0){original=ServerMenuClient.state.deepCopy();network=dev.abros.rivet.network.Protocol.featureState;dev.abros.rivet.network.Protocol.featureState=j->{};fixtures();stage=1;}
  if(stage==1){FRAMES.get(frame).apply();target=UUID.randomUUID();ServerMenuClient.state.getAsJsonObject("map").addProperty("world",target.toString());WorldMapClient.reset();stage=2;return;}
  if(stage==2){check(WorldMapClient.worldId().equals(target),"Fixture isolation");var settings=new MapSettingsScreen(new WorldMapScreen(null));mc.setScreen(settings);settings.revealSetting("markerImport");press("Импорт меток");check(mc.screen instanceof MapImportScreen,"Settings entry");screen=(MapImportScreen)mc.screen;screen.load(List.of(files));stage=3;return;}
  if(stage==3){if((boolean)field("busy"))return;check(((MapWaypointImport.Preview)field("preview")).entries().size()==21,"Files missing");check(!find("Импортировать").active,"Unknown dimension silently mapped");press("Без измерения");press(dev.abros.rivet.network.DimensionLabels.name("minecraft:overworld").getString());check(find("Импортировать").active,"Mapped batch blocked");UiGeometryHarness.verify(screen);screen.onClose();check(WorldMapClient.markers().isEmpty(),"Preview cancellation saved data");mc.setScreen(screen);stage=4;return;}
  if(stage==4){var b=screen.scrollLayout().viewport();screen.mouseScrolled(b.x()+3,b.y()+3,0,-30);check(screen.firstRow>0,"Preview cannot scroll");UiGeometryHarness.verify(screen);screen.mouseScrolled(b.x()+3,b.y()+3,0,30);press("Метка 0");press("Метка 0");String output=System.getenv("RIVET_IMPORT_SCREENSHOTS");if(output!=null){Files.createDirectories(Path.of(output));UiCaptureHarness.grab(new java.io.File(output),"import-"+frame+".png",mc.getMainRenderTarget(),m->{});}press("Импортировать");stage=5;return;}
  if(stage==5){if((boolean)field("busy"))return;check(WorldMapClient.markers().size()==19,"Duplicate filtering or commit failed: "+field("status"));check(WorldMapClient.ownRepository().markers().equals(WorldMapClient.markers()),"Commit not durable");screen.load(List.of(files));stage=6;return;}
  if(stage==6){if((boolean)field("busy"))return;press("Без измерения");press(dev.abros.rivet.network.DimensionLabels.name("minecraft:overworld").getString());check(!find("Импортировать").active,"Repeat imported duplicates");screen.onClose();check(mc.screen instanceof MapSettingsScreen,"Cancel lost parent");if(++frame<FRAMES.size()){stage=1;return;}done=true;restore();System.out.println("RIVET_IMPORT_OK checks="+checks+" frames="+frame);mc.stop();}
 }catch(Throwable ex){done=true;System.out.println("RIVET_IMPORT_FAILED stage="+stage+" frame="+frame);ex.printStackTrace();restore();mc.stop();}}
 private static void restore(){if(original!=null)ServerMenuClient.state=original;if(network!=null)dev.abros.rivet.network.Protocol.featureState=network;WorldMapClient.reset();UiPalette.preview(0);Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();}
}
