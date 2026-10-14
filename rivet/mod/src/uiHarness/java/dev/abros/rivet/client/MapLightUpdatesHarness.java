package dev.abros.rivet.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Exercise actual light-engine notifications without any accompanying block update. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapLightUpdatesHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_LIGHT_ADDRESS");
 private static final Map<SectionPos,DataLayer> saved=new HashMap<>();
 private static int phase=-1,ticks,x,z,original;private static long deadline;private static boolean done;
 private static WorldMapClient.TileKey key;
 private static void restore(){var mc=Minecraft.getInstance();if(mc.level!=null)for(var e:saved.entrySet())mc.level.getLightEngine().queueSectionData(LightLayer.SKY,e.getKey(),e.getValue().copy());saved.clear();}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+60000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map light regression",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Light timeout phase="+phase);
   if(!WorldMapClient.ready())return;
   if(phase==0){if(++ticks<100)return;String dim=mc.level.dimension().location().toString();MapCaves.set(dim,new MapCaves.View(0,false,64,30,false));x=mc.player.getBlockX();z=mc.player.getBlockZ();key=new WorldMapClient.TileKey(dim,x>>4,z>>4);var tile=WorldMapClient.loaded(key);if(tile==null)return;original=tile.skyLight(x&15,z&15);if(original<=0)throw new IllegalStateException("Fixture needs sky-lit surface");
    var engine=mc.level.getLightEngine();for(int sy=engine.getMinLightSection();sy<engine.getMaxLightSection();sy++){var section=SectionPos.of(x>>4,sy,z>>4);var data=engine.getLayerListener(LightLayer.SKY).getDataLayerData(section);if(data!=null){saved.put(section,data.copy());engine.queueSectionData(LightLayer.SKY,section,new DataLayer());}}
    phase=1;ticks=0;
   }else{var tile=WorldMapClient.loaded(key);if(tile==null)return;int sky=tile.skyLight(x&15,z&15);
    if(phase==1&&sky==0){restore();phase=2;ticks=0;}
    else if(phase==2&&sky==original){System.out.println("RIVET_MAP_LIGHT_OK darken=true restore=true blockUpdates=false");done=true;mc.stop();}
    else if(++ticks>100)throw new IllegalStateException("Light-only update did not refresh sample, phase="+phase+" sky="+sky);
   }
  }catch(Throwable ex){restore();done=true;System.out.println("RIVET_MAP_LIGHT_FAILED phase="+phase);ex.printStackTrace();mc.stop();}
 }
}
