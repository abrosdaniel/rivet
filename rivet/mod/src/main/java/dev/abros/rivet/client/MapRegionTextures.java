package dev.abros.rivet.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Fixed-size texture pages cover progressively larger world regions at overview scales. */
final class MapRegionTextures {
    record Key(String dimension,int x,int z,int level,int band){
        Key(String dimension,int x,int z,int level){this(dimension,x,z,level,dev.abros.rivet.core.map.MapLayer.SURFACE);}
        Key(dev.abros.rivet.core.map.MapLayer layer,int x,int z,int level){this(layer.dimension(),x,z,level,layer.band());}
        dev.abros.rivet.core.map.MapLayer layer(){return new dev.abros.rivet.core.map.MapLayer(dimension,band);}
        Key(String dimension,int x,int z){this(dimension,x,z,0);}
        Key{if(level<0||level>4)throw new IllegalArgumentException("Invalid region level");}
        int chunks(){return 4<<level;}
        int blocks(){return 64<<level;}
    }
    record Texture(ResourceLocation id,DynamicTexture texture,int level,long signature,boolean complete){}
    private record Prepared(NativeImage image,int level,long signature,boolean complete,long styleEpoch){}
    private static final Map<Key,Texture> textures=new LinkedHashMap<>(128,.75f,true);
    private static final Map<Key,Prepared> prepared=new LinkedHashMap<>();
    private static final Map<Key,Long> checked=new HashMap<>();
    private static final Set<Key> pending=new HashSet<>();
    private static long generation;
    static Texture texture(Key key,int level){
        if(key.level()!=level)throw new IllegalArgumentException("Region level mismatch");
        var existing=textures.get(key);var ready=prepared.get(key);
        if(ready!=null&&ready.styleEpoch()!=MapTerrainCache.styleEpoch()){prepared.remove(key);ready.image().close();checked.remove(key);ready=null;}
        if(ready!=null&&MapTerrainCache.uploadAllowed()){
            prepared.remove(key);
            if(existing!=null&&existing.level()!=ready.level()){Minecraft.getInstance().getTextureManager().release(existing.id());existing=null;}
            if(existing==null){var id=ResourceLocation.fromNamespaceAndPath("rivet","map/region/"+UUID.randomUUID());var dynamic=new DynamicTexture(ready.image());dynamic.setFilter(false,false);clamp(dynamic);Minecraft.getInstance().getTextureManager().register(id,dynamic);existing=new Texture(id,dynamic,ready.level(),ready.signature(),ready.complete());}
            else{existing.texture().setPixels(ready.image());existing.texture().upload();clamp(existing.texture());existing=new Texture(existing.id(),existing.texture(),ready.level(),ready.signature(),ready.complete());}
            MapTerrainCache.uploaded();textures.put(key,existing);
        }
        if(pending.contains(key)||prepared.containsKey(key))return existing;
        long stamp=MapTerrainCache.stateRevision()+WorldMapClient.terrainIndexRevision();
        if(existing!=null&&Objects.equals(checked.get(key),stamp))return existing;
        int chunks=key.chunks();
        var images=new MapTerrainCache.Image[chunks*chunks];long signature=level;boolean any=false,complete=true;
        for(int z=0;z<chunks;z++)for(int x=0;x<chunks;x++){
            var tileKey=new WorldMapClient.TileKey(key.layer(),key.x()*chunks+x,key.z()*chunks+z);
            var image=MapTerrainCache.image(tileKey,level);images[z*chunks+x]=image;
            if(image==null&&WorldMapClient.explored(tileKey)||MapTerrainCache.waiting(tileKey))complete=false;
            signature=signature*31+(image==null?0:image.revision());any|=image!=null;
        }
        if(!any)return existing;
        if(existing!=null&&existing.signature()==signature&&existing.level()==level){if(complete){checked.put(key,stamp);if(!existing.complete()){existing=new Texture(existing.id(),existing.texture(),existing.level(),existing.signature(),true);textures.put(key,existing);}}return existing;}
        if(!MapTerrainCache.canWork())return existing;
        // Missing source pages during a disk/cache refill must not erase already drawn terrain.
        int[] previous=new int[64*64];
        if(existing!=null){var pixels=existing.texture().getPixels();for(int z=0;z<64;z++)for(int x=0;x<64;x++)previous[z*64+x]=pixels.getPixelRGBA(x,z);}
        long owner=generation,targetSignature=signature,styleEpoch=MapTerrainCache.styleEpoch();boolean targetComplete=complete;pending.add(key);
        if(!MapTerrainCache.work(()->{
            int tileSide=16>>level,side=64;var image=new NativeImage(side,side,false);
            for(int z=0;z<side;z++)for(int x=0;x<side;x++){
                var source=images[z/tileSide*chunks+x/tileSide];int color=source==null?0:source.levels()[level][z%tileSide*tileSide+x%tileSide];
                image.setPixelRGBA(x,z,source==null?previous[z*64+x]:color&0xff00ff00|(color&255)<<16|(color>>>16&255));
            }
            Minecraft.getInstance().execute(()->{if(generation!=owner){image.close();return;}pending.remove(key);if(styleEpoch!=MapTerrainCache.styleEpoch()){image.close();checked.remove(key);return;}var old=prepared.put(key,new Prepared(image,level,targetSignature,targetComplete,styleEpoch));if(old!=null)old.image().close();while(prepared.size()>512){var oldest=prepared.keySet().iterator().next();prepared.remove(oldest).image().close();checked.remove(oldest);}});
        })){pending.remove(key);}else if(complete)checked.put(key,stamp);else checked.remove(key);
        while(textures.size()>2048){var oldest=textures.keySet().iterator().next();var removed=textures.remove(oldest);checked.remove(oldest);Minecraft.getInstance().getTextureManager().release(removed.id());}
        return existing;
    }
    private static void clamp(DynamicTexture texture){texture.bind();com.mojang.blaze3d.platform.GlStateManager._texParameter(3553,10242,33071);com.mojang.blaze3d.platform.GlStateManager._texParameter(3553,10243,33071);}
    static boolean fallback(net.minecraft.client.gui.GuiGraphics g,Key key,float left,float top,float right,float bottom){
        for(int level=key.level()+1;level<=4;level++){
            int scale=1<<(level-key.level());var parent=textures.get(new Key(key.layer(),Math.floorDiv(key.x(),scale),Math.floorDiv(key.z(),scale),level));
            if(parent==null)continue;
            float u=Math.floorMod(key.x(),scale)/(float)scale,v=Math.floorMod(key.z(),scale)/(float)scale;
            draw(g,parent,left,top,right,bottom,u,v,u+1f/scale,v+1f/scale);return true;
        }
        for(int level=key.level()-1;level>=0;level--){
            int scale=1<<(key.level()-level);boolean any=false;
            for(int z=0;z<scale;z++)for(int x=0;x<scale;x++){
                var child=textures.get(new Key(key.layer(),key.x()*scale+x,key.z()*scale+z,level));if(child==null)continue;any=true;
                draw(g,child,left+(right-left)*x/scale,top+(bottom-top)*z/scale,left+(right-left)*(x+1)/scale,top+(bottom-top)*(z+1)/scale);
            }
            if(any)return true;
        }
        return false;
    }
    static void draw(net.minecraft.client.gui.GuiGraphics g,Texture texture,float left,float top,float right,float bottom){
        draw(g,texture,left,top,right,bottom,0,0,1,1);
    }
    private static void draw(net.minecraft.client.gui.GuiGraphics g,Texture texture,float left,float top,float right,float bottom,float u0,float v0,float u1,float v1){
        g.flush();com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,texture.id());com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        var matrix=g.pose().last().pose();var vertices=com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX);
        vertices.addVertex(matrix,left,top,0).setUv(u0,v0);vertices.addVertex(matrix,left,bottom,0).setUv(u0,v1);vertices.addVertex(matrix,right,bottom,0).setUv(u1,v1);vertices.addVertex(matrix,right,top,0).setUv(u1,v0);com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertices.buildOrThrow());com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    static void reset(){generation++;for(var texture:textures.values())Minecraft.getInstance().getTextureManager().release(texture.id());textures.clear();for(var image:prepared.values())image.image().close();prepared.clear();pending.clear();checked.clear();}
    private MapRegionTextures(){}
}
