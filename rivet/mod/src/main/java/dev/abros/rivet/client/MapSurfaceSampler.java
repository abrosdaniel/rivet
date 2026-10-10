package dev.abros.rivet.client;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.ArrayList;

/** Samples one visible column. Repeated transparent material contributes once, with cumulative light absorption. */
final class MapSurfaceSampler {
    record Sample(int base,int top,int ground,int overlay,boolean glowing,java.util.List<dev.abros.rivet.core.map.MapOverlay> layers,String biome,int blockLight,int skyLight){}
    private static final class Layer {
        final BlockState state;final int color,light,sky;int opacity;
        Layer(BlockState state,int color,int light,int opacity,int sky){this.state=state;this.color=color;this.light=light;this.opacity=opacity;this.sky=sky;}
    }
    static Sample sample(ClientLevel level,LevelChunk chunk,int x,int z,BlockPos.MutableBlockPos at){
        return column(level,chunk,x,z,at,chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15),level.getMinBuildHeight(),false);
    }
    static Sample cave(ClientLevel level,LevelChunk chunk,int x,int z,BlockPos.MutableBlockPos at,int top,int depth){
        boolean full=top==dev.abros.rivet.core.map.MapLayer.FULL;
        int start=full?Math.min(level.getMaxBuildHeight()-1,chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)):Math.clamp(top,level.getMinBuildHeight(),level.getMaxBuildHeight()-1);
        int bottom=full?level.getMinBuildHeight():Math.max(level.getMinBuildHeight(),start+1-depth);
        // Full first enters the terrain, then both modes wait for actual air or fluid.
        // A plant, slab or glass block embedded in the ceiling is not an opening.
        boolean entered=!full;
        for(int y=start;y>=bottom;y--){
            at.set(x,y,z);var state=chunk.getBlockState(at);
            if(!entered){
                if(!state.isAir()&&!(state.getBlock() instanceof LiquidBlock)&&!state.ignitedByLava()&&!state.canBeReplaced()
                    &&state.getPistonPushReaction()!=net.minecraft.world.level.material.PushReaction.DESTROY
                    &&!(state.getBlock() instanceof TransparentBlock)&&!MapBlockColors.translucent(state))entered=true;
            }else if(state.isAir()||!state.getFluidState().isEmpty()){
                var sample=column(level,chunk,x,z,at,y,bottom,true);
                return sample==null?empty(level):sample;
            }
        }
        return empty(level);
    }
    private static Sample empty(ClientLevel level){return new Sample(0xff010101,level.getMinBuildHeight(),level.getMinBuildHeight(),0xff000000,false,java.util.List.of(),"",0,0);}
    private static Sample column(ClientLevel level,LevelChunk chunk,int x,int z,BlockPos.MutableBlockPos at,int top,int bottom,boolean cave){
        int visible=Integer.MIN_VALUE;
        var layers=new ArrayList<Layer>(10);
        for(int y=top;y>=bottom;y--){
            at.set(x,y,z);var state=chunk.getBlockState(at);
            // Fluid is an independent layer even inside a waterlogged solid block.
            var fluid=state.getFluidState();
            if(!fluid.isEmpty()){
                var fluidBlock=fluid.createLegacyBlock();
                if(MapRenderSettings.INSTANCE.transparency&&ItemBlockRenderTypes.getRenderLayer(fluid)==RenderType.translucent()){
                    if(visible==Integer.MIN_VALUE)visible=y;add(level,at,fluidBlock,layers,cave);
                }else{return finish(level,at,fluidBlock,visible==Integer.MIN_VALUE?y:visible,y,layers,cave);}
                if(state.getBlock() instanceof LiquidBlock)continue;
            }
            if(invisible(state))continue;
            if(MapRenderSettings.INSTANCE.transparency&&(state.getBlock() instanceof TransparentBlock||MapBlockColors.translucent(state))){if(visible==Integer.MIN_VALUE)visible=y;add(level,at,state,layers,cave);}
            else{int color=MapBlockColors.color(level,at,state);if(color==0)continue;if(visible==Integer.MIN_VALUE)visible=y;return finish(level,at,state,visible,y,layers,color,cave);}
        }
        if(cave&&!layers.isEmpty()){at.set(x,level.getMinBuildHeight(),z);return finish(level,at,Blocks.AIR.defaultBlockState(),visible,level.getMinBuildHeight(),layers,0x010101,true);}
        return null;
    }
    private static boolean invisible(BlockState state){
        var block=state.getBlock();
        boolean flower=block instanceof FlowerBlock||block instanceof TallFlowerBlock||block instanceof PitcherCropBlock||state.is(BlockTags.FLOWERS)&&!state.is(BlockTags.LEAVES);
        var settings=MapRenderSettings.INSTANCE;
        return !settings.flowers&&flower||!settings.redstone&&block instanceof RedStoneWireBlock||!settings.stainedGlass&&(block instanceof StainedGlassBlock||block instanceof StainedGlassPaneBlock)||state.isAir()||state.getRenderShape()==net.minecraft.world.level.block.RenderShape.INVISIBLE&&!(block instanceof LiquidBlock)
            ||block==Blocks.TORCH||block==Blocks.SHORT_GRASS||block==Blocks.GLASS||block==Blocks.GLASS_PANE
            ||block instanceof DoublePlantBlock&&!flower;
    }
    private static int litColour(int color,boolean glowing){
        if(!glowing)return color;
        int sum=(color>>>16&255)+(color>>>8&255)+(color&255);if(sum==0)return color;
        float factor=Math.max(1,407f/sum);int result=color&0xff000000;
        for(int shift:new int[]{16,8,0})result|=Math.min(255,(int)((color>>>shift&255)*factor))<<shift;
        return result;
    }
    private static void add(ClientLevel level,BlockPos.MutableBlockPos at,BlockState state,ArrayList<Layer> layers,boolean cave){
        if(!layers.isEmpty()&&layers.getLast().state==state){layers.getLast().opacity=Math.min(15,layers.getLast().opacity+state.getLightBlock(level,at));return;}
        int color=litColour(MapBlockColors.color(level,at,state),state.getLightEmission()>0);
        if(color==0)return;
        if((color>>>24)==0)color|=(state.getBlock() instanceof LiquidBlock?191:state.getBlock() instanceof IceBlock?216:127)<<24;
        int opacity=state.getLightBlock(level,at),light=state.getLightEmission()>0?15:level.getBrightness(LightLayer.BLOCK,at.above());
        if(!layers.isEmpty()&&layers.getLast().color==color)layers.getLast().opacity=Math.min(15,layers.getLast().opacity+opacity);
        else if(layers.size()<10)layers.add(new Layer(state,color,light,Math.min(15,opacity),level.getBrightness(LightLayer.SKY,at.above())));
        else layers.getLast().opacity=Math.min(15,layers.getLast().opacity+opacity);
    }
    private static Sample finish(ClientLevel level,BlockPos.MutableBlockPos at,BlockState state,int top,int ground,ArrayList<Layer> layers,boolean cave){
        return finish(level,at,state,top,ground,layers,MapBlockColors.color(level,at,state),cave);
    }
    private static Sample finish(ClientLevel level,BlockPos.MutableBlockPos at,BlockState state,int top,int ground,ArrayList<Layer> layers,int baseColor,boolean cave){
        boolean glow=state.getLightEmission()>0;int color=litColour(baseColor,glow);
        int r=0,g=0,b=0,sun=cave?0:15;float remaining=1;
        for(var layer:layers){
            float alpha=(layer.color>>>24)/255f,intensity=(9f+Math.max(layer.light,sun))/24f*alpha*remaining;
            r=(int)(r+(layer.color>>>16&255)*intensity);g=(int)(g+(layer.color>>>8&255)*intensity);b=(int)(b+(layer.color&255)*intensity);
            sun=Math.max(0,sun-layer.opacity);remaining*=1-alpha;
        }
        int light=glow?15:Math.max(level.getBrightness(LightLayer.BLOCK,at.above()),sun);
        if(cave&&layers.isEmpty())light=Math.max(light,level.getBrightness(LightLayer.SKY,at.above()));
        // Low bits of alpha retain the ground light; legacy opaque samples naturally mean daylight.
        int base=((0xf0|light)<<24)|(color&0xffffff);
        // Carpet-like surfaces should not introduce a whole block of slope shading.
        int effective=ground;try{if(MapRenderSettings.INSTANCE.shortBlocks&&state.getShape(level,at).max(net.minecraft.core.Direction.Axis.Y)<.25)effective--;}catch(RuntimeException ex){/* Keep the full height when a contextual model cannot provide its shape. */}
        return new Sample(base,top,effective,Math.clamp(Math.round(remaining*255),0,255)<<24|Math.min(r,255)<<16|Math.min(g,255)<<8|Math.min(b,255),glow,layers.stream().map(l->new dev.abros.rivet.core.map.MapOverlay(l.color,l.light,l.opacity,l.sky)).toList(),level.getBiome(new BlockPos(at.getX(),top,at.getZ())).unwrapKey().map(k->k.location().toString()).orElse(""),glow?15:level.getBrightness(LightLayer.BLOCK,at.above()),level.getBrightness(LightLayer.SKY,at.above()));
    }
    private MapSurfaceSampler(){}
}
