package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Shared cave selection for both maps. Detection reads only chunks already held by the client. */
final class MapCaves {
 record View(int mode,boolean full,int top,int depth,boolean legible){
  View{mode=Math.clamp(mode,0,2);top=Math.clamp(top,-2048,2047);depth=Math.clamp(depth,1,64);}
 }
 record Options(int worldAuto,int miniAuto,int worldDelay,int miniDelay,int zoom,boolean showTop,int defaultType){
  Options{worldAuto=Math.clamp(worldAuto,-1,3);miniAuto=Math.clamp(miniAuto,0,3);worldDelay=Math.clamp(worldDelay,0,10000)/100*100;miniDelay=Math.clamp(miniDelay,0,10000)/100*100;zoom=Math.clamp(zoom,1,4);defaultType=Math.clamp(defaultType,0,2);}
 }
 static final net.minecraft.client.KeyMapping MANUAL=new net.minecraft.client.KeyMapping("key.rivet.manualCave",org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN,"key.categories.rivet");
 static boolean manualMini;
 private static Options options=new Options(-1,2,1000,1000,2,true,1);
 static Options options(){return options;}
 static void configure(Options value){options=value;save();WorldMapClient.resample();Minimap.invalidate();}
 private static final dev.abros.rivet.core.map.MapCaveTransition miniTransition=new dev.abros.rivet.core.map.MapCaveTransition();
 private static final Map<String,View> views=new LinkedHashMap<>();
 private static String world="",dimension="";private static int tick;
 private static final dev.abros.rivet.core.map.MapCaveTransition transition=new dev.abros.rivet.core.map.MapCaveTransition();
 static void reset(){world=dimension="";transition.reset();miniTransition.reset();manualMini=false;tick=0;}
 private static String key(String dim){return WorldMapClient.worldId()+"|"+dim;}
 static View view(String dim){return views.getOrDefault(key(dim),new View(options.defaultType()==0?0:1,options.defaultType()==2,64,30,false));}
 static void set(String dim,View value){views.put(key(dim),value);while(views.size()>256)views.remove(views.keySet().iterator().next());save();WorldMapClient.resample();MapTerrainCache.relight(layer(dim));Minimap.invalidate();}
 static void clearPreferences(){views.clear();options=new Options(-1,2,1000,1000,2,true,1);WorldMapClient.resample();MapTerrainCache.reset();MapRegionTextures.reset();}
 static void load(com.google.gson.JsonObject json){views.clear();options=new Options(-1,2,1000,1000,2,true,1);if(json==null)return;
  if(json.has("options"))try{var o=new com.google.gson.Gson().fromJson(json.get("options"),Options.class);if(o!=null)options=o;}catch(RuntimeException ignored){}
 for(var e:json.entrySet()){if(views.size()>=256)break;try{var v=e.getValue().getAsJsonObject();views.put(e.getKey(),new View(v.get("mode").getAsInt(),v.get("full").getAsBoolean(),v.get("top").getAsInt(),v.get("depth").getAsInt(),v.get("legible").getAsBoolean()));}catch(RuntimeException ignored){}}}
 static com.google.gson.JsonObject json(){var out=new com.google.gson.JsonObject();out.addProperty("schema",2);out.add("options",new com.google.gson.Gson().toJsonTree(options));views.forEach((k,v)->{var j=new com.google.gson.JsonObject();j.addProperty("mode",v.mode());j.addProperty("full",v.full());j.addProperty("top",v.top());j.addProperty("depth",v.depth());j.addProperty("legible",v.legible());out.add(k,j);});return out;}
 private static void save(){MapSettings.INSTANCE.save();}
 static void tick(){
  var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
  // Ensure preferences are loaded before querying a view.
  var settings=MapSettings.INSTANCE;
  String dim=mc.level.dimension().location().toString(),id=String.valueOf(WorldMapClient.worldId());
  if(!world.equals(id)||!dimension.equals(dim)){reset();world=id;dimension=dim;}
  while(MANUAL.consumeClick())manualMini=!manualMini;
  if(++tick%5!=0)return;
  long now=net.minecraft.Util.getMillis();
  miniTransition.update(manualMini?view(dim).mode()==2?view(dim).top():mc.player.getBlockY()+4:detect(options.miniAuto()),now,options.miniDelay());
  transition.update(detect(options.worldAuto()<0?options.miniAuto():options.worldAuto()),now,options.worldDelay());
 }
 static int top(String dim){
  if(!WorldMapClient.cavesAllowed())return MapLayer.SURFACE;
  var v=view(dim);if(v.mode()==0)return MapLayer.SURFACE;
  var screen=Minecraft.getInstance().screen;while(true){if(screen instanceof ChoicePopup popup)screen=popup.parentScreen();else if(screen instanceof SettingsSearchScreen search)screen=search.parentScreen();else if(screen instanceof MapExportScreen export)screen=export.parentScreen();else if(screen instanceof MapSettingsScreen settings)screen=settings.parentScreen();else break;}
  boolean mapOpen=screen instanceof WorldMapScreen||screen instanceof MapCaveScreen;
  int top=MapLayer.SURFACE;
  if(mapOpen&&v.mode()==2)top=v.top();
  else if(dim.equals(dimension))top=!mapOpen||options.worldAuto()<0?miniTransition.current():transition.current();
  if(top==MapLayer.SURFACE)return top;
  var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.dimension().location().toString().equals(dim))top=Math.clamp(top,mc.level.getMinBuildHeight(),mc.level.getMaxBuildHeight()-1);
  return v.full()?MapLayer.FULL:top;
 }
 static MapLayer layer(String dim){int top=top(dim);return top==MapLayer.SURFACE?MapLayer.surface(dim):top==MapLayer.FULL?new MapLayer(dim,MapLayer.FULL):MapLayer.cave(dim,top);}
 static String label(String dim){int top=top(dim);return top==MapLayer.SURFACE?Client.tr("map.surface").getString():top==MapLayer.FULL?Client.tr("map.caveFull").getString():Client.tr("map.caveTop").getString()+": "+top;}
 static int detect(){return detect(options.worldAuto()<0?options.miniAuto():options.worldAuto());}
 static int detect(int roof){
  if(roof==0)return MapLayer.SURFACE;
  var mc=Minecraft.getInstance();var level=mc.level;var player=mc.player;if(level==null||player==null)return MapLayer.SURFACE;
  int x=player.getBlockX(),z=player.getBlockZ(),start=Math.max(level.getMinBuildHeight(),player.getBlockY()+1),center=level.getMaxBuildHeight();var at=new BlockPos.MutableBlockPos();
  int radius=Math.clamp(roof,1,3)-1;
  for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++){
   if(!level.hasChunk((x+dx)>>4,(z+dz)>>4))return MapLayer.SURFACE;
   var chunk=level.getChunk((x+dx)>>4,(z+dz)>>4);boolean found=false;
   at.set(x+dx,start,z+dz);if(level.getBrightness(net.minecraft.world.level.LightLayer.SKY,at)>=15)return MapLayer.SURFACE;
   int end=Math.min(level.getMaxBuildHeight()-1,chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,(x+dx)&15,(z+dz)&15));
   for(int y=start;y<=end;y++){at.set(x+dx,y,z+dz);var state=chunk.getBlockState(at);if(!state.isAir()&&state.getPistonPushReaction()!=net.minecraft.world.level.material.PushReaction.DESTROY&&!MapBlockColors.translucent(state)&&!(state.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock)&&!state.is(BlockTags.LEAVES)&&state.getBlock()!=Blocks.BARRIER&&!(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)){found=true;if(dx==0&&dz==0)center=y;break;}}
   if(!found)return MapLayer.SURFACE;
  }
  return Math.min(center,player.getBlockY()+4);
 }
 private MapCaves(){}
}
