package dev.abros.rivet.client;

/** Display options are separate from sampled terrain and can recolour saved maps. */
final class MapRenderSettings {
 boolean lighting=true,depth=true,hoverBiome=true,radar=true,radarWorld=true,players=true,hostile=true,friendly=true,items=false,other=false,radarNames=false,radarIcons=true;
 boolean biomeBlend=true,biomesVanilla=false,flowers=true,stainedGlass=true,shortBlocks=true,transparency=true,redstone=true;
 int blockColors=0;
 int slopes=2,radarHeight=16;float markerScale=1,markerMinZoom=0;
 static final MapRenderSettings INSTANCE=new MapRenderSettings();
 void copy(MapRenderSettings value){blockColors=Math.clamp(value.blockColors,0,1);biomeBlend=value.biomeBlend;biomesVanilla=value.biomesVanilla;flowers=value.flowers;stainedGlass=value.stainedGlass;shortBlocks=value.shortBlocks;transparency=value.transparency;redstone=value.redstone;lighting=value.lighting;depth=value.depth;hoverBiome=value.hoverBiome;slopes=Math.clamp(value.slopes,0,3);markerScale=Float.isFinite(value.markerScale)?Math.clamp(value.markerScale,.5f,2):1;markerMinZoom=Float.isFinite(value.markerMinZoom)?Math.clamp(value.markerMinZoom,0,3):0;radar=value.radar;radarWorld=value.radarWorld;players=value.players;hostile=value.hostile;friendly=value.friendly;items=value.items;other=value.other;radarNames=value.radarNames;radarIcons=value.radarIcons;radarHeight=Math.clamp(value.radarHeight,1,128);}
 dev.abros.rivet.core.map.MapColors.Style style(){return style(net.minecraft.client.Minecraft.getInstance().level==null?"":net.minecraft.client.Minecraft.getInstance().level.dimension().location().toString());}
 dev.abros.rivet.core.map.MapColors.Style style(String dimension){
  var level=net.minecraft.client.Minecraft.getInstance().level;int dark=0;float ambient=0;
  // Client Level.getSkyDarken() is cached at construction; the float overload evaluates live time/weather.
  if(level!=null&&level.dimension().location().toString().equals(dimension)){dark=level.dimensionType().hasSkyLight()?Math.clamp(Math.round((1-level.getSkyDarken(1f))*13.75f),0,11):15;ambient=level.dimensionType().ambientLight();}
  return new dev.abros.rivet.core.map.MapColors.Style(lighting,depth,slopes,0,lighting?dark:0,lighting?ambient:0);
 }
 private static dev.abros.rivet.core.map.MapColors.Style lastStyle;
 static void tickLight(){var style=INSTANCE.style();if(lastStyle!=null&&!lastStyle.equals(style)){MapTerrainCache.relightAll();Minimap.invalidate();}lastStyle=style;}
 void samplingChanged(){MapBlockColors.clear();WorldMapClient.resample();changed();}
 void load(com.google.gson.JsonObject j){try{copy(j==null?new MapRenderSettings():new com.google.gson.Gson().fromJson(j,MapRenderSettings.class));}catch(RuntimeException ex){copy(new MapRenderSettings());}}
 com.google.gson.JsonObject json(){return new com.google.gson.Gson().toJsonTree(this).getAsJsonObject();}
 void changed(){MapSettings.INSTANCE.save();MapTerrainCache.relightAll();Minimap.invalidate();}
}
