package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.biome.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.*;
import java.util.concurrent.*;

/** Real saved NBT fixtures, never loaded into the server's chunk map. */
@EventBusSubscriber(modid="rivet")
public final class SavedTerrainHarness {
 private static final java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger();
 @SubscribeEvent public static void loaded(net.neoforged.neoforge.event.level.ChunkEvent.Load event){if(enabled()&&event.getChunk().getPos().z==64&&(event.getChunk().getPos().x==64||event.getChunk().getPos().x==65||event.getChunk().getPos().x==-65))loads.incrementAndGet();}
 private static ExecutorService worker;private static Future<?> checks;private static long deadline;
 private static boolean enabled(){return "true".equals(System.getenv("RIVET_SAVED_TERRAIN_SMOKE"));}
 @SubscribeEvent public static void started(ServerStartedEvent event)throws Exception{
  if(!enabled())return;var server=event.getServer();var overworld=server.overworld();var nether=server.getLevel(Level.NETHER);
  var a=overworld.getChunkSource().chunkMap.write(new ChunkPos(64,64),fixture(overworld,64,Blocks.GOLD_BLOCK,"minecraft:full"));
  var b=nether.getChunkSource().chunkMap.write(new ChunkPos(64,64),fixture(nether,64,Blocks.DIAMOND_BLOCK,"minecraft:full"));
  var c=overworld.getChunkSource().chunkMap.write(new ChunkPos(65,64),fixture(overworld,65,Blocks.STONE,"minecraft:noise"));
  var d=overworld.getChunkSource().chunkMap.write(new ChunkPos(-65,64),fixture(overworld,-65,Blocks.GOLD_BLOCK,"minecraft:full"));
  deadline=System.currentTimeMillis()+45000;worker=Executors.newSingleThreadExecutor();
  checks=worker.submit(()->{try{
   CompletableFuture.allOf(a,b,c,d).get(10,TimeUnit.SECONDS);var api=RivetApi.connect("rivet").orElseThrow().terrain();
   var beforeTag=overworld.getChunkSource().chunkMap.read(new ChunkPos(64,64)).get().orElseThrow().copy();
   var job=await(api,api.renderSaved().get());if(!beforeTag.equals(overworld.getChunkSource().chunkMap.read(new ChunkPos(64,64)).get().orElseThrow()))throw new IllegalStateException("Saved NBT mutated");if(job.rendered()!=3||job.skipped()!=2||job.total()!=5)throw new IllegalStateException("Wrong saved scan counters: "+job);
   var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(api.tile(new TerrainApi.Tile("minecraft:overworld",0,64,64)).get().png()));
   if((image.getRGB(24,24)>>>24)!=255||(image.getRGB(24,24)>>>16&255)<=(image.getRGB(24,24)&255))throw new IllegalStateException("Saved surface missing");
   if(image.getRGB(0,0)!=0)throw new IllegalStateException("Saved region mask missing");
   if(api.tile(new TerrainApi.Tile("minecraft:the_nether",0,64,64)).get().png().length==0)throw new IllegalStateException("Dimension not rendered");
   if(api.tile(new TerrainApi.Tile("minecraft:overworld",0,-65,64)).get().png().length==0)throw new IllegalStateException("Negative region missing");
   var cave=api.profile("caves");var caveImage=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(cave.tile(new TerrainApi.Tile("minecraft:overworld",0,64,64)).get().png()));
   if(caveImage.getRGB(0,0)!=0||caveImage.getRGB(1,1)!=0||(caveImage.getRGB(2,2)>>>24)!=255)throw new IllegalStateException("Saved cave floors/masks missing");
   if(!cave.slice().orElseThrow().equals(new TerrainApi.Slice(64,79)))throw new IllegalStateException("Saved cave band missing");System.out.println("RIVET_CAVE_SAVED_OK floor=true globalMask=true profileMask=true");
   var iso=api.profile("isometric");if(iso.metadata().tilePixels()!=256||!iso.projection().profile().equals("isometric"))throw new IllegalStateException("Isometric profile unavailable");
   var isoImage=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(iso.tile(new TerrainApi.Tile("minecraft:overworld",0,0,30)).get().png()));
   boolean visible=false;for(int py=0;py<256;py++)for(int px=0;px<256;px++)visible|=(isoImage.getRGB(px,py)>>>24)>0;if(!visible)throw new IllegalStateException("Saved isometric image empty");
   if(!iso.materials().active()||iso.materials().resolved()==0)throw new IllegalStateException("Saved materials not resolved");System.out.println("RIVET_MATERIALS_SAVED_OK resources=true geometry=true");
   System.out.println("RIVET_ISOMETRIC_SAVED_OK profile=true geometry=true");
   String before=api.tile(new TerrainApi.Tile("minecraft:overworld",0,64,64)).get().etag();
   var corrupt=fixture(overworld,64,Blocks.GOLD_BLOCK,"minecraft:full");corrupt.putInt("xPos",123);overworld.getChunkSource().chunkMap.write(new ChunkPos(64,64),corrupt).get();
   var failed=await(api,api.renderSaved().get());if(!failed.state().equals("FAILED")||!before.equals(api.tile(new TerrainApi.Tile("minecraft:overworld",0,64,64)).get().etag()))throw new IllegalStateException("Corrupt snapshot replaced previous image");
   overworld.getChunkSource().chunkMap.write(new ChunkPos(64,64),fixture(overworld,64,Blocks.GOLD_BLOCK,"minecraft:full")).get();
   var cancel=api.renderSaved().get();if(!api.cancel(cancel.id()).get().state().equals("CANCELLED"))throw new IllegalStateException("Saved cancellation failed");
   if(!await(api,api.renderSaved().get()).state().equals("COMPLETE"))throw new IllegalStateException("Restart after cancel failed");
   System.out.println("RIVET_SAVED_TERRAIN_WORKER_OK palettes=true dimensions=true mask=true partialSkipped=true corruptPreserved=true cancel=true");
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 private static TerrainApi.Job await(TerrainApi api,TerrainApi.Job job)throws Exception{do{if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Saved render timed out");Thread.sleep(25);job=api.job(job.id()).get(3,TimeUnit.SECONDS);}while(!java.util.Set.of("COMPLETE","FAILED","CANCELLED").contains(job.state()));return job;}
 static CompoundTag fixture(net.minecraft.server.level.ServerLevel level,int x,Block block,String status){
  var registry=level.registryAccess().registryOrThrow(Registries.BIOME);var section=new LevelChunkSection(registry);for(int z=0;z<16;z++)for(int dx=0;dx<16;dx++)section.setBlockState(dx,0,z,block.defaultBlockState());
  if(enabled())for(int z=0;z<16;z++)for(int dx=0;dx<16;dx++)section.setBlockState(dx,8,z,block.defaultBlockState());
  var states=PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY,net.minecraft.world.level.block.state.BlockState.CODEC,PalettedContainer.Strategy.SECTION_STATES,Blocks.AIR.defaultBlockState());
  var biomes=PalettedContainer.codecRO(registry.asHolderIdMap(),registry.holderByNameCodec(),PalettedContainer.Strategy.SECTION_BIOMES,registry.getHolderOrThrow(Biomes.PLAINS));
  var s=new CompoundTag();s.putByte("Y",(byte)4);s.put("block_states",states.encodeStart(NbtOps.INSTANCE,section.getStates()).getOrThrow());s.put("biomes",biomes.encodeStart(NbtOps.INSTANCE,section.getBiomes()).getOrThrow());
  var tag=new CompoundTag();tag.putInt("xPos",x);tag.putInt("zPos",64);tag.putString("Status",status);NbtUtils.addCurrentDataVersion(tag);var sections=new ListTag();sections.add(s);tag.put("sections",sections);return tag;
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)throws Exception{
  if(!enabled()||checks==null)return;if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Saved harness timeout");if(!checks.isDone())return;checks.get();checks=null;
  for(var level:event.getServer().getAllLevels())for(int x:new int[]{64,65,-65})if(level.getChunkSource().getChunkNow(x,64)!=null)throw new IllegalStateException("Saved rendering loaded a chunk");
  if(loads.get()!=0)throw new IllegalStateException("Saved render triggered chunk load event");
  worker.shutdown();System.out.println("RIVET_SAVED_TERRAIN_NATIVE_OK noChunkLoads=true");event.getServer().halt(false);
 }
 @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(enabled()&&worker!=null)worker.shutdownNow();}
}
