package dev.abros.rivet.server;

import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.FluidState;

/** Detached palette view. Never constructs a LevelChunk or registers entities, light or POI. */
final class SavedTerrainSnapshot implements BlockGetter {
 private static final Codec<PalettedContainer<BlockState>> STATES=PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY,BlockState.CODEC,PalettedContainer.Strategy.SECTION_STATES,Blocks.AIR.defaultBlockState());
 private final LevelChunkSection[] sections;private final int minY,height,x,z;private final Biome fallback;private final int[] top=new int[256];
 SavedTerrainSnapshot(CompoundTag tag,int x,int z,int minY,int height,Registry<Biome> biomes){
  if(!tag.contains("xPos",Tag.TAG_INT)||!tag.contains("zPos",Tag.TAG_INT)||tag.getInt("xPos")!=x||tag.getInt("zPos")!=z||!tag.getString("Status").equals("minecraft:full"))throw new IllegalArgumentException("Not a complete chunk at requested coordinates");
  this.x=x;this.z=z;this.minY=minY;this.height=height;sections=new LevelChunkSection[height/16];fallback=biomes.getHolderOrThrow(Biomes.PLAINS).value();
  var codec=PalettedContainer.codecRO(biomes.asHolderIdMap(),biomes.holderByNameCodec(),PalettedContainer.Strategy.SECTION_BIOMES,biomes.getHolderOrThrow(Biomes.PLAINS));
  if(!tag.contains("sections",Tag.TAG_LIST))throw new IllegalArgumentException("Missing sections");
  var list=tag.getList("sections",Tag.TAG_COMPOUND);if(list.size()>sections.length+2)throw new IllegalArgumentException("Invalid section count");
  for(int i=0;i<list.size();i++){var section=list.getCompound(i);int index=section.getByte("Y")-Math.floorDiv(minY,16);if(index<0||index>=sections.length)continue;
   if(sections[index]!=null)throw new IllegalArgumentException("Duplicate section");
   if(!section.contains("block_states",Tag.TAG_COMPOUND))continue;
   var states=STATES.parse(NbtOps.INSTANCE,section.getCompound("block_states")).getOrThrow();
   var palette=codec.parse(NbtOps.INSTANCE,section.getCompound("biomes")).getOrThrow();sections[index]=new LevelChunkSection(states,palette);
  }
  var pos=new BlockPos.MutableBlockPos();for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){int y=minY+height-1;while(y>=minY&&getBlockState(pos.set(x*16+dx,y,z*16+dz)).isAir())y--;top[dz*16+dx]=y;}
 }
 int top(int lx,int lz){return top[lz*16+lx];}
 Biome biome(BlockPos p){int i=Math.floorDiv(p.getY()-minY,16);return i<0||i>=sections.length||sections[i]==null?fallback:sections[i].getNoiseBiome((p.getX()&15)>>2,(p.getY()&15)>>2,(p.getZ()&15)>>2).value();}
 @Override public BlockState getBlockState(BlockPos p){int i=Math.floorDiv(p.getY()-minY,16);if(Math.floorDiv(p.getX(),16)!=x||Math.floorDiv(p.getZ(),16)!=z||i<0||i>=sections.length||sections[i]==null)return Blocks.AIR.defaultBlockState();return sections[i].getBlockState(p.getX()&15,p.getY()&15,p.getZ()&15);}
 @Override public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
 @Override public BlockEntity getBlockEntity(BlockPos p){return null;}
 @Override public int getHeight(){return height;}
 @Override public int getMinBuildHeight(){return minY;}
}
