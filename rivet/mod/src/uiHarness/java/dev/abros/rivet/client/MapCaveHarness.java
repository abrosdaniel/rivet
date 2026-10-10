package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Actual client chunks, sampler, layered disk/cache/GPU and modal interactions. No server world edits. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapCaveHarness {
 private static final String ADDRESS=System.getenv("RIVET_CAVE_ADDRESS");
 private static MapExportScreen exportScreen;private static MapExport.Job exportJob;
 private static boolean connected,done;private static int tick,phase,checks,frame;private static long deadline;private static int renderDistance=-1;private static int logged=-1;
 private static boolean originalFlying;
 private static void position(int y){var mc=Minecraft.getInstance();mc.player.connection.sendCommand("tp @s "+(x+8.5)+" "+y+" "+(z+8.5));mc.player.setPos(x+8.5,y,z+8.5);}
 private static String dim;private static int x,z;private static net.minecraft.world.phys.Vec3 originalPosition;private static MapCaves.View originalView;private static MapCaves.Options originalOptions;
 private static final Map<BlockPos,BlockState> blocks=new HashMap<>();private static Object originalRepository;private static int litPixel,savedBase;
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(2,0);
 private static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void setField(Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 private static void fixture()throws Exception{
  var mc=Minecraft.getInstance();dim=mc.level.dimension().location().toString();originalPosition=mc.player.position();originalFlying=mc.player.getAbilities().flying;check(mc.player.getAbilities().mayfly,"Cave test requires a creative test player");mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();originalView=MapCaves.view(dim);originalOptions=MapCaves.options();MapCaves.configure(new MapCaves.Options(-1,2,1000,1000,2,true,1));x=(mc.player.getBlockX()>>4)*16;z=(mc.player.getBlockZ()>>4)*16;
  WorldMapClient.flush();originalRepository=field(WorldMapClient.class,"repository");setField(WorldMapClient.class,"repository",new MapRepository(java.nio.file.Files.createTempDirectory("rivet-cave-test-"),UUID.randomUUID(),mc.player.getUUID()));
  for(String key:List.of("tiles","known","windows","loading","dirty","indexing","indexed","changedChunks","retries")){Object v=field(WorldMapClient.class,key);if(v instanceof Map<?,?> m)m.clear();else ((Set<?>)v).clear();}
  MapTerrainCache.reset();MapRegionTextures.reset();Minimap.reset();
  for(int yy=200;yy<=250;yy++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){var p=new BlockPos(x+dx,yy,z+dz);blocks.put(p,mc.level.getBlockState(p));BlockState state=yy<=210?Blocks.DEEPSLATE.defaultBlockState():yy>=219&&yy<=224||yy>=231?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState();if(yy==224)state=Blocks.GOLD_BLOCK.defaultBlockState();mc.level.setBlock(p,state,16);}
  position(211);for(var p:blocks.keySet())mc.level.getLightEngine().checkBlock(p);mc.level.getLightEngine().runLightUpdates();WorldMapClient.resample();
 }
 private static void restore()throws Exception{
  var mc=Minecraft.getInstance();if(mc.level!=null){for(var e:blocks.entrySet())mc.level.setBlock(e.getKey(),e.getValue(),16);blocks.clear();if(originalPosition!=null){mc.player.connection.sendCommand("tp @s "+originalPosition.x+" "+originalPosition.y+" "+originalPosition.z);mc.player.setPos(originalPosition);mc.player.getAbilities().flying=originalFlying;mc.player.onUpdateAbilities();}}
  if(renderDistance>=0)mc.options.renderDistance().set(renderDistance);if(originalView!=null)MapCaves.set(dim,originalView);if(originalOptions!=null)MapCaves.configure(originalOptions);if(originalRepository!=null){WorldMapClient.flush();setField(WorldMapClient.class,"repository",originalRepository);}
 }
 private static void sampled(){var mc=Minecraft.getInstance();var chunk=mc.level.getChunk(x>>4,z>>4);var p=new BlockPos.MutableBlockPos();
  var surface=MapSurfaceSampler.sample(mc.level,chunk,x+8,z+8,p);var lower=MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,215,64);var upper=MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,228,64);var cut=MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,222,64);var full=MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,MapLayer.FULL,16);
  check(surface!=null&&surface.top()==250,"Surface fixture missing");check(lower!=null&&lower.top()==210,"Lower floor wrong");check(upper!=null&&upper.top()==224,"Upper floor wrong");check(cut!=null&&cut.top()==210,"Cut ceiling wasn't removed");check(full!=null&&full.top()==224,"Full-depth roof removal wrong");check(MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,250,16).base()==0xff010101,"Depth limit ignored");
  check(MapCaves.detect()==215,"Automatic ceiling detection wrong");check(MapCaves.detect(0)==MapLayer.SURFACE,"Off roof mode ignored");check(MapCaves.detect(1)==215&&MapCaves.detect(3)==215,"Selectable roof sizes ignored");
  var defaults=MapCaves.view("rivet:unvisited");check(defaults.depth()==30&&!defaults.legible()&&defaults.mode()==1&&!defaults.full(),"Reference cave defaults differ");
  var roof=new BlockPos(x+7,231,z+7);var saved=new ArrayList<BlockState>();for(int yy=219;yy<=250;yy++){var at=new BlockPos(x+7,yy,z+7);saved.add(mc.level.getBlockState(at));mc.level.setBlock(at,Blocks.OAK_LEAVES.defaultBlockState(),16);}check(MapCaves.detect()==MapLayer.SURFACE,"Leaves triggered cave detection");check(MapCaves.detect(1)==215,"1x1 detector used larger roof");for(int yy=219;yy<=250;yy++)mc.level.setBlock(new BlockPos(x+7,yy,z+7),saved.get(yy-219),16);
  check(MapColors.caveDepth(210,215,30)==25f/30,"Inclusive depth brightness wrong");
  var solid=new BlockPos(x+8,222,z+8);var old=mc.level.getBlockState(solid);mc.level.setBlock(solid,Blocks.OAK_SLAB.defaultBlockState(),16);check(MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,222,30).top()==210,"Non-solid ceiling mistaken for cave air");mc.level.setBlock(solid,old,16);
  check(MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,215,5).base()==0xff010101,"Depth includes extra block");check(MapSurfaceSampler.cave(mc.level,chunk,x+8,z+8,p,215,6).top()==210,"Depth excludes lower boundary");

 }
 private static void manual(int y){MapCaves.set(dim,new MapCaves.View(2,false,y,64,true));}
 private static boolean rendered(int top)throws Exception{
  var layer=MapCaves.layer(dim);var key=new WorldMapClient.TileKey(layer,x>>4,z>>4);var tile=WorldMapClient.loaded(key);if(tile==null||tile.height(8,8)!=top)return false;
  var image=MapTerrainCache.image(key);var region=MapRegionTextures.texture(new MapRegionTextures.Key(layer,Math.floorDiv(x>>4,4),Math.floorDiv(z>>4,4),0),0);if(image==null||region==null)return false;
  int color=image.levels()[0][8*16+8],rgba=color&0xff00ff00|(color&255)<<16|(color>>>16&255);if(region.texture().getPixels().getPixelRGBA(Math.floorMod(x>>4,4)*16+8,Math.floorMod(z>>4,4)*16+8)!=rgba)return false;
  return true;
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+180000;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);renderDistance=mc.options.renderDistance().get();mc.options.renderDistance().set(3);mc.resizeDisplay();var data=new ServerData("Cave check",ADDRESS,ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),data,false,null);}
  if(connected&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Cave check timed out phase="+phase);
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;if(logged!=phase){logged=phase;System.out.println("RIVET_CAVE_PHASE "+phase);}
  if(phase>0){mc.player.setPos(x+8.5,phase==5||phase==13||phase==14?252:211,z+8.5);MapCaves.tick();}
  if(phase==0){if(++tick<60)return;fixture();phase=10;tick=0;}
  else if(phase==10){if(++tick<20)return;sampled();manual(215);mc.setScreen(new WorldMapScreen(null));phase=1;tick=0;}
  else if(phase==1&&rendered(210)){check(MapCaves.layer(dim).band()==13,"Lower band wrong");manual(228);phase=2;}
  else if(phase==2&&rendered(224)){check(WorldMapClient.loaded(new WorldMapClient.TileKey(MapLayer.cave(dim,215),x>>4,z>>4)).height(8,8)==210,"Upper overwrote lower");var map=(WorldMapScreen)mc.screen;map.view.center(x+8,z+8);exportScreen=new MapExportScreen(map);mc.setScreen(exportScreen);manual(215);((net.minecraft.client.gui.components.Button)exportScreen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Экспортировать видимую область")).findFirst().orElseThrow()).onPress();var job=MapExportScreen.class.getDeclaredField("job");job.setAccessible(true);exportJob=(MapExport.Job)job.get(exportScreen);phase=15;}
  else if(phase==15){if(!exportJob.done)return;check(exportJob.file!=null,"Cave export failed");var f=MapExportScreen.class.getDeclaredField("area");f.setAccessible(true);var area=(MapExport.Area)f.get(exportScreen);var tile=WorldMapClient.loaded(new WorldMapClient.TileKey(MapLayer.cave(dim,215),x>>4,z>>4));int expected=MapColors.render(tile,8,8,tile.groundHeight(8,7),tile.groundHeight(7,7),215,MapCaves.view(dim).depth(),mc.level.dimensionType().logicalHeight(),MapCaves.view(dim).legible(),MapRenderSettings.INSTANCE.style(dim));try(var image=com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(exportJob.file))){check(area.step()==1,"Cave export fixture must use native resolution");check(image.getPixelRGBA(x+8-area.x(),z+8-area.z())==(expected&0xff00ff00|(expected&255)<<16|(expected>>>16&255)),"Export retained layer selected when dialog opened");}mc.setScreen(new WorldMapScreen(null));phase=3;}
  else if(phase==3&&rendered(210)){var map=(WorldMapScreen)mc.screen;check(map.point(x+8,z+8).y()==211,"Cave marker uses surface Y");
   MapCaves.set(dim,new MapCaves.View(1,false,215,64,true));MapCaves.reset();MapCaves.tick();var transition=(MapCaveTransition)field(MapCaves.class,"miniTransition");int detected=MapCaves.detect();transition.update(detected,1000);transition.update(detected,1500);check(MapCaves.top(dim)==215,"Automatic selection does not use detected ceiling");
   mc.player.setPos(x+8.5,252,z+8.5);check(MapCaves.detect()==MapLayer.SURFACE,"Open sky detected as cave");transition.update(MapLayer.SURFACE,1600);check(MapCaves.layer(dim).cave(),"Cave exit has no debounce");transition.update(MapLayer.SURFACE,2100);check(!MapCaves.layer(dim).cave(),"Automatic exit did not select surface");
   mc.player.setPos(x+8.5,211,z+8.5);MapCaves.set(dim,new MapCaves.View(1,false,215,64,true));MapCaves.reset();mc.setScreen(new WorldMapScreen(null));phase=11;tick=0;}
  else if(phase==11){if(++tick<35)return;check(MapCaves.layer(dim).cave()&&MapCaves.top(dim)==215,"Real-time cave entry failed");mc.setScreen(null);phase=12;tick=0;}
  else if(phase==12){if(++tick<40)return;var displayed=field(Minimap.class,"displayed");check(displayed!=null,"Automatic minimap cave missing");var band=displayed.getClass().getDeclaredMethod("band");band.setAccessible(true);check((int)band.invoke(displayed)==13,"Automatic minimap uses wrong band: shown="+band.invoke(displayed)+" current="+MapCaves.layer(dim)+" detected="+MapCaves.detect()+" player="+mc.player.position());position(252);phase=13;tick=0;}
  else if(phase==13){if(++tick==1)check(MapCaves.layer(dim).cave(),"Real-time cave exit ignored delay");if(tick<40)return;check(!MapCaves.layer(dim).cave(),"Real-time surface return failed");var displayed=field(Minimap.class,"displayed");check(displayed!=null,"Surface minimap missing");var band=displayed.getClass().getDeclaredMethod("band");band.setAccessible(true);check((int)band.invoke(displayed)==MapLayer.SURFACE,"Minimap retained cave after exit");mc.setScreen(new WorldMapScreen(null));phase=14;tick=0;}
  else if(phase==14&&rendered(250)){check(!MapCaves.layer(dim).cave(),"World map retained cave after exit");position(211);manual(215);MapCaves.manualMini=true;mc.setScreen(null);phase=6;tick=0;}
  else if(phase==6){if(++tick<40)return;var displayed=field(Minimap.class,"displayed");check(displayed!=null,"Minimap did not compose cave layer");var band=displayed.getClass().getDeclaredMethod("band");band.setAccessible(true);check((int)band.invoke(displayed)==13,"Minimap uses wrong cave layer");var key=new WorldMapClient.TileKey(MapCaves.layer(dim),x>>4,z>>4);litPixel=MapTerrainCache.image(key).levels()[0][136];savedBase=WorldMapClient.loaded(key).color(8,8);MapCaves.set(dim,new MapCaves.View(2,false,215,64,false));phase=9;tick=0;}
  else if(phase==9&&rendered(210)){var key=new WorldMapClient.TileKey(MapCaves.layer(dim),x>>4,z>>4);check(WorldMapClient.loaded(key).color(8,8)==savedBase,"Lighting changed saved block-light sample");check(MapTerrainCache.image(key).levels()[0][136]!=litPixel,"Stored cave did not respond to lighting switch");manual(215);phase=7;tick=0;}
  else if(phase==7){if(++tick%12!=0)return;if(frame>0){UiGeometryHarness.verify(mc.screen);capture("cave-ui-"+frame);mc.screen.onClose();}if(frame<FRAMES.size()){var f=FRAMES.get(frame++);f.apply();manual(215);var map=new WorldMapScreen(null);mc.setScreen(map);if(f.scene()==0)mc.setScreen(new MapCaveScreen(map,dim));else{var options=new MapCaveScreen(map,dim);mc.setScreen(options);var input=(net.minecraft.client.gui.components.EditBox)options.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).findFirst().orElseThrow();input.setValue("9999");check(MapCaves.top(dim)==215,"Invalid input changed cave height");
    input.setValue("Auto");check(MapCaves.view(dim).mode()==1,"Auto text ignored");input.setValue("228");check(MapCaves.top(dim)==228,"Apply did not select manual height");choose("map.caveType","map.caveFull");check(MapCaves.top(dim)==MapLayer.FULL,"Full mode still uses manual height");check(mc.screen==options,"Choice lost modal parent");
    var saved=MapCaves.view(dim);var json=MapCaves.json();MapCaves.load(json);check(MapCaves.view(dim).equals(saved),"Cave preferences did not round-trip");}return;}
   mc.getWindow().setWindowed(640,480);mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(new MapCaveScreen(new WorldMapScreen(null),dim));UiGeometryHarness.verify(mc.screen);phase=8;tick=0;
  }else if(phase==8){if(++tick<15)return;capture("cave-compact");var options=(MapCaveScreen)mc.screen;var close=(net.minecraft.client.gui.components.Button)options.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.tr("close").getString())).findFirst().orElseThrow();check(close.getX()+close.getWidth()>=options.scrollLayout().content().right()&&close.getX()+close.getWidth()-options.scrollLayout().content().right()<=12,"Compact cave footer is misaligned");options.revealRow(10);options.rebuildWidgets();UiGeometryHarness.verify(options);var policy=ServerMenuClient.state.getAsJsonObject("map");var before=policy.get("caves");policy.addProperty("caves",false);check(MapCaves.layer(dim).band()==MapLayer.SURFACE,"Server denial ignored");if(before==null)policy.remove("caves");else policy.add("caves",before);restore();done=true;System.out.println("RIVET_CAVE_OK checks="+checks+" frames="+frame+" auto manual floors depth cache GPU minimap modal compact policy");mc.stop();}
 }catch(Throwable ex){done=true;System.out.println("RIVET_CAVE_FAILED phase="+phase);ex.printStackTrace();try{restore();}catch(Exception ignored){}mc.stop();}}
 private static void choose(String label,String value){var mc=Minecraft.getInstance();((net.minecraft.client.gui.components.Button)mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith(Client.tr(label).getString()+":" )).findFirst().orElseThrow()).onPress();UiGeometryHarness.verify(mc.screen);check(MapCaves.top(dim)==228,"Opening choice changed selected cave layer");press(value);}
 private static void press(String key){var screen=Minecraft.getInstance().screen;((net.minecraft.client.gui.components.Button)screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.tr(key).getString())).findFirst().orElseThrow()).onPress();}
 private static void capture(String name){String out=System.getenv("RIVET_CAVE_SCREENSHOTS");if(out!=null){var dir=new java.io.File(out);dir.mkdirs();UiCaptureHarness.grab(dir,name+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}}
}
