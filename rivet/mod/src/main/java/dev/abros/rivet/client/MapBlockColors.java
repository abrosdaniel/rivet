package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.*;

/** Average visible pixels of the largest top face, preserve translucent alpha, then apply biome/model tint. */
final class MapBlockColors {
    private record Face(int color,int tint) {}
    private static final Map<BlockState,List<Face>> states=new IdentityHashMap<>();
    private static final Map<BlockState,Boolean> translucency=new IdentityHashMap<>();
    private static final Map<TextureAtlasSprite,Integer> sprites=new IdentityHashMap<>();
    static void clear(){MapGlyphs.clear();states.clear();sprites.clear();translucency.clear();WorldMapScreen.releaseTextures();}
    static int color(ClientLevel level,BlockPos pos,BlockState state) {
        if(state.isAir())return 0;
        var mc=Minecraft.getInstance();var settings=MapRenderSettings.INSTANCE;
        if(settings.blockColors==1){int rgb=state.getMapColor(level,pos).col;
            if(settings.biomesVanilla){int tint=vegetationTint(level,pos,state);if(tint!=-1)rgb=tint;}
            int alpha=state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)?191:state.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock?127:255;return rgb==0?0:alpha<<24|rgb;
        }
        if(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock&&state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
            var sprite=mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(net.minecraft.resources.ResourceLocation.parse("minecraft:block/water_still"));
            return MapColors.tint(average(sprite),settings.biomeBlend?net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level,pos):level.getBiome(pos).value().getWaterColor());
        }
        var faces=states.computeIfAbsent(state,s->{
            try {
                var model=mc.getBlockRenderer().getBlockModel(s);var rand=RandomSource.create(0);
                var quads=new ArrayList<net.minecraft.client.renderer.block.model.BakedQuad>();
                for(var type:model.getRenderTypes(s,rand,ModelData.EMPTY)){
                    quads.addAll(model.getQuads(s,Direction.UP,rand,ModelData.EMPTY,type));
                    for(var q:model.getQuads(s,null,rand,ModelData.EMPTY,type))if(q.getDirection()==Direction.UP)quads.add(q);
                }
                if(quads.isEmpty())return List.of(new Face(average(model.getParticleIcon(ModelData.EMPTY)),mc.getBlockColors().getColor(s,level,pos,0)==-1?-1:0));
                var q=quads.stream().max(Comparator.comparingDouble(MapBlockColors::area)).orElseThrow();return List.of(new Face(average(q.getSprite()),q.isTinted()?q.getTintIndex():-1));
            }catch(RuntimeException|LinkageError ex){return List.of();}
        });
        if(faces.isEmpty())return 0xff000000|state.getMapColor(level,pos).col;
        long a=0,r=0,g=0,b=0;
        for(var face:faces){int tint=face.tint()<0?-1:mc.getBlockColors().getColor(state,level,pos,face.tint());if(tint!=-1&&!settings.biomeBlend){int raw=vegetationTint(level,pos,state);if(raw!=-1)tint=raw;}int c=tint==-1?face.color():MapColors.tint(face.color(),tint);int alpha=c>>>24;a+=alpha;r+=(c>>>16&255)*alpha;g+=(c>>>8&255)*alpha;b+=(c&255)*alpha;}
        if(a==0)return 0;int color=(int)(a/faces.size())<<24|(int)(r/a)<<16|(int)(g/a)<<8|(int)(b/a);
        if((color&0xffffff)==0&&state.getMapColor(level,pos).col!=0)return color&0xff000000|state.getMapColor(level,pos).col;
        return color;
    }
    private static int vegetationTint(ClientLevel level,BlockPos pos,BlockState state){
        var block=state.getBlock();var biome=level.getBiome(pos).value();
        if(block instanceof net.minecraft.world.level.block.GrassBlock||block instanceof net.minecraft.world.level.block.TallGrassBlock||block==net.minecraft.world.level.block.Blocks.FERN||block==net.minecraft.world.level.block.Blocks.LARGE_FERN)return biome.getGrassColor(pos.getX(),pos.getZ());
        if(block==net.minecraft.world.level.block.Blocks.OAK_LEAVES||block==net.minecraft.world.level.block.Blocks.JUNGLE_LEAVES||block==net.minecraft.world.level.block.Blocks.ACACIA_LEAVES||block==net.minecraft.world.level.block.Blocks.DARK_OAK_LEAVES||block==net.minecraft.world.level.block.Blocks.VINE)return biome.getFoliageColor();
        if(state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))return biome.getWaterColor();return -1;
    }
    static boolean translucent(BlockState state){
        return translucency.computeIfAbsent(state,s->{
            try{
                var model=Minecraft.getInstance().getBlockRenderer().getBlockModel(s);var random=RandomSource.create(0);double largest=0;boolean translucent=false;int alpha=255;
                for(var type:model.getRenderTypes(s,random,ModelData.EMPTY)){
                    var quads=new ArrayList<>(model.getQuads(s,Direction.UP,random,ModelData.EMPTY,type));
                    for(var q:model.getQuads(s,null,random,ModelData.EMPTY,type))if(q.getDirection()==Direction.UP)quads.add(q);
                    for(var quad:quads){double size=area(quad);boolean transparent=type==net.minecraft.client.renderer.RenderType.translucent()||type.sortOnUpload();
                        if(size>largest||size==largest&&translucent&&!transparent){largest=size;translucent=transparent;alpha=average(quad.getSprite())>>>24;}
                    }
                }
                return translucent&&alpha<=240;
            }catch(RuntimeException|LinkageError ex){return false;}
        });
    }
    private static double area(net.minecraft.client.renderer.block.model.BakedQuad quad){
        int[] v=quad.getVertices();int stride=v.length/4;double[] a=new double[3],b=new double[3];
        for(int n=0;n<3;n++){double origin=Float.intBitsToFloat(v[n]);a[n]=Float.intBitsToFloat(v[stride+n])-origin;b[n]=Float.intBitsToFloat(v[3*stride+n])-origin;}
        double x=a[1]*b[2]-a[2]*b[1],y=a[2]*b[0]-a[0]*b[2],z=a[0]*b[1]-a[1]*b[0];return x*x+y*y+z*z;
    }
    private static int average(TextureAtlasSprite sprite) {
        return sprites.computeIfAbsent(sprite,s->{
            var image=s.contents().getOriginalImage();int width=Math.min(s.contents().width(),image.getWidth()),height=Math.min(s.contents().height(),image.getHeight());
            int extent=Math.min(width,height),stride=Math.max(1,Math.min(4,extent/8));long a=0,r=0,g=0,b=0,count=0;
            for(int y=0;y<extent/stride*stride;y+=stride)for(int x=0;x<extent/stride*stride;x+=stride){int c=image.getPixelRGBA(x,y),alpha=c>>>24;if(alpha<=10)continue;count++;a+=alpha;r+=c&255;g+=c>>>8&255;b+=c>>>16&255;}
            return a==0?0:(int)(a/count)<<24|(int)(r/count)<<16|(int)(g/count)<<8|(int)(b/count);
        });
    }
    private MapBlockColors() {}
}
