package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in integration check against the real Copycats+ model and block entity. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapMaterialsHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_MATERIALS_ADDRESS");
 private static int phase,phaseTicks,expectedBlue;private static long changedAt;private static net.minecraft.core.BlockPos samplePos;
 private static boolean started,done;private static int ticks;private static long deadline;
 @FunctionalInterface private interface Update {void run()throws Exception;}
 @SuppressWarnings("unchecked") private static void expectUpdate(net.minecraft.core.BlockPos pos,Update update,String description)throws Exception{
  var field=WorldMapClient.class.getDeclaredField("changedChunks");field.setAccessible(true);
  var pending=(java.util.Set<WorldMapClient.TileKey>)field.get(null);
  var key=new WorldMapClient.TileKey(MapCaves.layer(Minecraft.getInstance().level.dimension().location().toString()),pos.getX()>>4,pos.getZ()>>4);
  pending.remove(key);update.run();if(!pending.contains(key))throw new IllegalStateException("Map missed "+description);
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;deadline=System.currentTimeMillis()+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map material test",ADDRESS,ServerData.Type.OTHER),false,null);return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Material scenario timed out");
   if(mc.level==null||mc.player==null||++ticks<100)return;
   var level=mc.level;
   if(phase>0){
    var key=new WorldMapClient.TileKey(level.dimension().location().toString(),samplePos.getX()>>4,samplePos.getZ()>>4);
    var tile=WorldMapClient.loaded(key);int wanted=phase==1?MapBlockColors.color(level,samplePos,Blocks.RED_CONCRETE.defaultBlockState()):expectedBlue;
    if(tile==null||tile.color(samplePos.getX()&15,samplePos.getZ()&15)!=wanted){if(++phaseTicks>100)throw new IllegalStateException("Map sampler did not update promptly phase="+phase);return;}
    if(phase==1){var entity=level.getBlockEntity(samplePos);entity.getClass().getMethod("setMaterial",BlockState.class).invoke(entity,Blocks.BLUE_CONCRETE.defaultBlockState());phase=2;phaseTicks=0;changedAt=System.nanoTime();return;}
    System.out.println("RIVET_MAP_MATERIAL_UPDATE_MS="+(System.nanoTime()-changedAt)/1000000);
    level.removeBlock(samplePos,false);level.removeBlock(samplePos.east(),false);
    System.out.println("RIVET_MAP_MATERIALS_OK separate-fillers changed-filler transparent-filler update-signals sampled-update");done=true;mc.stop();return;
   }
   if(!WorldMapClient.ready())return;
   var pos=new net.minecraft.core.BlockPos(mc.player.getBlockX(),level.getMaxBuildHeight()-8,mc.player.getBlockZ());
   var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("copycats:copycat_block"));
   if(block==Blocks.AIR)throw new IllegalStateException("Copycats+ fixture missing");
   var state=block.defaultBlockState();level.setBlock(pos,state,3);level.setBlock(pos.east(),state,3);
   var first=level.getBlockEntity(pos);var second=level.getBlockEntity(pos.east());
   if(first==null||second==null)throw new IllegalStateException("Copycat block entity missing");
   var setter=first.getClass().getMethod("setMaterial",BlockState.class);
   setter.invoke(first,Blocks.RED_CONCRETE.defaultBlockState());setter.invoke(second,Blocks.BLUE_CONCRETE.defaultBlockState());
   first.requestModelDataUpdate();second.requestModelDataUpdate();
   MapRenderSettings.INSTANCE.blockColors=0;
   int red=MapBlockColors.color(level,pos,state),blue=MapBlockColors.color(level,pos.east(),state);
   int expectedRed=MapBlockColors.color(level,pos,Blocks.RED_CONCRETE.defaultBlockState());
   int expectedBlue=MapBlockColors.color(level,pos.east(),Blocks.BLUE_CONCRETE.defaultBlockState());
   if(red!=expectedRed||blue!=expectedBlue||red==blue)throw new IllegalStateException("Instance materials mismatch: "+Integer.toHexString(red)+"/"+Integer.toHexString(blue)+" expected "+Integer.toHexString(expectedRed)+"/"+Integer.toHexString(expectedBlue));
   expectUpdate(pos,()->setter.invoke(first,Blocks.BLUE_CONCRETE.defaultBlockState()),"Copycats material setter");
   expectUpdate(pos,first::requestModelDataUpdate,"model data refresh");
   expectUpdate(pos,()->level.sendBlockUpdated(pos,state,state,3),"same-state visual update");
   expectUpdate(pos,()->mc.getConnection().handleBlockEntityData(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(first)),"block entity packet");
   var normal=pos.west();
   expectUpdate(normal,()->level.setBlock(normal,Blocks.RED_CONCRETE.defaultBlockState(),3),"placement");
   expectUpdate(normal,()->level.setBlock(normal,Blocks.BLUE_CONCRETE.defaultBlockState(),3),"replacement");
   expectUpdate(normal,()->level.removeBlock(normal,false),"removal");
   expectUpdate(normal,()->level.setBlock(normal,Blocks.WATER.defaultBlockState(),3),"fluid update");
   expectUpdate(normal,()->level.setBlock(normal,Blocks.REDSTONE_LAMP.defaultBlockState(),3),"lamp placement");
   expectUpdate(normal,()->level.setBlock(normal,Blocks.REDSTONE_LAMP.defaultBlockState().setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT,true),3),"block property change");
   level.removeBlock(normal,false);
   if(MapBlockColors.color(level,pos,state)!=expectedBlue)throw new IllegalStateException("Changed filler kept stale colour");
   setter.invoke(first,Blocks.RED_STAINED_GLASS.defaultBlockState());first.requestModelDataUpdate();
   if(!MapBlockColors.translucent(level,pos,state))throw new IllegalStateException("Transparent filler treated as solid");
   setter.invoke(first,Blocks.RED_CONCRETE.defaultBlockState());first.requestModelDataUpdate();
   samplePos=pos;MapMaterialsHarness.expectedBlue=expectedBlue;phase=1;phaseTicks=0;
  }catch(Throwable error){done=true;System.err.println("RIVET_MAP_MATERIALS_FAILED");error.printStackTrace();mc.stop();}
 }
}
