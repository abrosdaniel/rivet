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
    private static final Map<Key,Long> saved=new HashMap<>(),saveRetry=new HashMap<>();
    private static final Set<Key> pending=new HashSet<>();
    private static final Set<Key> previewReads=new LinkedHashSet<>(),previewPending=new HashSet<>();
    private static long generation,scanStart;
    private static int scans;
    private static final Set<Key> scanQueue=new LinkedHashSet<>();
    private static Set<Key> visible=Set.of();
    private static int textureLimit=2048;
    /** Geometric coverage is independent of the asynchronously loaded exploration index. */
    static Set<Key> viewKeys(dev.abros.rivet.core.map.MapLayer layer,dev.abros.rivet.core.map.MapViewport view,int width,int height,double scale){
        int level=dev.abros.rivet.core.map.MapDetail.level(view.zoom(),scale),blocks=64<<level;
        int minX=(int)Math.floor(view.worldX(0,width/2d)/blocks),maxX=(int)Math.floor(view.worldX(width,width/2d)/blocks);
        int minZ=(int)Math.floor(view.worldZ(0,height/2d)/blocks),maxZ=(int)Math.floor(view.worldZ(height,height/2d)/blocks);
        var keys=new LinkedHashSet<Key>();
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++)keys.add(new Key(layer,x,z,level));
        return keys;
    }
    private static Set<Key> coverage(Set<Key> keys){
        var all=new LinkedHashSet<Key>(keys);
        for(var key:keys)for(int level=key.level()+1;level<=4;level++){
            int scale=1<<(level-key.level());all.add(new Key(key.layer(),Math.floorDiv(key.x(),scale),Math.floorDiv(key.z(),scale),level));
        }
        return all.stream().sorted(Comparator.comparingInt(Key::level).reversed()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    /** Read a view in one IO job and publish its saved pixels together, ahead of terrain work. */
    static void warm(Set<Key> keys){
        var repo=WorldMapClient.ownRepository();if(repo==null)return;
        int capacity=512-prepared.size()-previewPending.size();if(capacity<=0)return;
        var missing=coverage(keys).stream().filter(k->!textures.containsKey(k)&&!prepared.containsKey(k)&&!previewReads.contains(k)).limit(capacity).toList();
        if(missing.isEmpty())return;long owner=generation;
        if(!WorldMapClient.previewIo(()->{
            var read=new LinkedHashMap<Key,NativeImage>();
            for(var key:missing)try{repo.previews().read(key.layer(),key.x(),key.z(),key.level()).ifPresent(p->{
                var image=new NativeImage(64,64,false);
                for(int z=0;z<64;z++)for(int x=0;x<64;x++)image.setPixelRGBA(x,z,p[z*64+x]);
                read.put(key,image);
            });}catch(java.io.IOException ignored){}
            Minecraft.getInstance().execute(()->{
                if(owner!=generation){read.values().forEach(NativeImage::close);return;}
                previewPending.removeAll(missing);
                for(var entry:read.entrySet()){
                    var key=entry.getKey();var image=entry.getValue();
                    if(textures.containsKey(key)||prepared.containsKey(key)){image.close();continue;}
                    prepared.put(key,new Prepared(image,key.level(),Long.MIN_VALUE,false,MapTerrainCache.styleEpoch()));
                }
                while(prepared.size()>512){var oldest=prepared.keySet().iterator().next();prepared.remove(oldest).image().close();previewReads.remove(oldest);}
            });
        }))return;
        previewReads.addAll(missing);previewPending.addAll(missing);
        while(previewReads.size()>8192){var oldest=previewReads.iterator().next();previewReads.remove(oldest);}
    }
    static boolean restored(Set<Key> keys){
        for(var key:coverage(keys)){
            if(!textures.containsKey(key)&&!prepared.containsKey(key)&&!previewReads.contains(key))return false;
            if(previewPending.contains(key))return false;
            var ready=prepared.get(key);if(ready!=null&&ready.signature()==Long.MIN_VALUE)return false;
        }
        return true;
    }
    static void prepareView(Set<Key> keys){
        scanStart=System.nanoTime();scans=0;
        visible=coverage(keys);
        if(!keys.isEmpty())scanQueue.removeIf(k->!k.layer().equals(keys.iterator().next().layer()));
        // Retain a full high-resolution viewport; a 4K view can exceed 2048 small pages.
        textureLimit=Math.clamp(visible.size()+256,2048,8192);
        warm(keys);
        for(var queued:List.copyOf(scanQueue)){texture(queued,queued.level(),true);if(scans>=8||System.nanoTime()-scanStart>=2_000_000)break;}
        for(var key:coverage(keys))if((keys.contains(key)&&previewReads.contains(key))||textures.containsKey(key)||prepared.containsKey(key))texture(key,key.level(),keys.contains(key)||scanQueue.contains(key));
    }
    private static boolean preview(Key key){
        var repo=WorldMapClient.ownRepository();if(repo==null||previewReads.contains(key))return previewPending.contains(key);
        long owner=generation;
        if(!WorldMapClient.previewIo(()->{
            int[] pixels=null;try{pixels=repo.previews().read(key.layer(),key.x(),key.z(),key.level()).orElse(null);}catch(java.io.IOException ignored){/* A disposable preview never blocks the original terrain. */}
            int[] read=pixels;Minecraft.getInstance().execute(()->{
                if(owner!=generation)return;previewPending.remove(key);
                if(read==null||textures.containsKey(key)||prepared.containsKey(key))return;
                var image=new NativeImage(64,64,false);for(int z=0;z<64;z++)for(int x=0;x<64;x++)image.setPixelRGBA(x,z,read[z*64+x]);
                prepared.put(key,new Prepared(image,key.level(),Long.MIN_VALUE,false,MapTerrainCache.styleEpoch()));
                while(prepared.size()>512){var oldest=prepared.keySet().iterator().next();prepared.remove(oldest).image().close();checked.remove(oldest);}
            });
        }))return false;
        previewReads.add(key);previewPending.add(key);while(previewReads.size()>8192){var oldest=previewReads.iterator().next();previewReads.remove(oldest);}return true;
    }
    private static void persist(Key key,Texture texture){
        var repo=WorldMapClient.ownRepository();if(repo==null||!texture.complete()||Objects.equals(saved.get(key),texture.signature())||System.currentTimeMillis()<saveRetry.getOrDefault(key,0L))return;
        var image=texture.texture().getPixels();int[] pixels=new int[4096];for(int z=0;z<64;z++)for(int x=0;x<64;x++)pixels[z*64+x]=image.getPixelRGBA(x,z);
        long owner=generation,signature=texture.signature();
        if(WorldMapClient.previewIo(()->{
            try{repo.previews().writePyramid(key.layer(),key.x(),key.z(),key.level(),pixels);}
            catch(java.io.IOException failure){Minecraft.getInstance().execute(()->{
                if(owner!=generation||!Objects.equals(saved.get(key),signature))return;
                saved.remove(key);saveRetry.put(key,System.currentTimeMillis()+5000);
                com.mojang.logging.LogUtils.getLogger().warn("Could not save map preview; will retry",failure);
            });}
        }))saved.put(key,signature);
    }
    static Texture texture(Key key,int level){return texture(key,level,true);}
    private static Texture texture(Key key,int level,boolean compose){
        if(key.level()!=level)throw new IllegalArgumentException("Region level mismatch");
        var existing=textures.get(key);var ready=prepared.get(key);
        if(existing==null&&ready==null&&preview(key)){scanQueue.remove(key);return null;}
        // Disk previews intentionally show the previous view until fresh terrain is composed.
        if(ready!=null&&ready.signature()!=Long.MIN_VALUE&&ready.styleEpoch()!=MapTerrainCache.styleEpoch()){prepared.remove(key);ready.image().close();checked.remove(key);ready=null;}
        if(ready!=null&&MapTerrainCache.uploadAllowed()){
            prepared.remove(key);
            if(existing!=null&&existing.level()!=ready.level()){Minecraft.getInstance().getTextureManager().release(existing.id());existing=null;}
            if(existing==null){var id=ResourceLocation.fromNamespaceAndPath("rivet","map/region/"+UUID.randomUUID());var dynamic=new DynamicTexture(ready.image());dynamic.setFilter(false,false);clamp(dynamic);Minecraft.getInstance().getTextureManager().register(id,dynamic);existing=new Texture(id,dynamic,ready.level(),ready.signature(),ready.complete());}
            else{existing.texture().setPixels(ready.image());existing.texture().upload();clamp(existing.texture());existing=new Texture(existing.id(),existing.texture(),ready.level(),ready.signature(),ready.complete());}
            MapTerrainCache.uploaded();textures.put(key,existing);persist(key,existing);
    while(textures.size()>textureLimit){var oldest=textures.keySet().stream().filter(k->!visible.contains(k)).findFirst().orElseGet(()->textures.keySet().iterator().next());var removed=textures.remove(oldest);checked.remove(oldest);saved.remove(oldest);saveRetry.remove(oldest);Minecraft.getInstance().getTextureManager().release(removed.id());}
        }
        if(existing!=null)persist(key,existing);
        if(!compose||pending.contains(key)||prepared.containsKey(key)||!WorldMapClient.indexed(key.layer())){scanQueue.remove(key);return existing;}
        long stamp=MapTerrainCache.stateRevision()+WorldMapClient.terrainIndexRevision();
        if(existing!=null&&Objects.equals(checked.get(key),stamp)){scanQueue.remove(key);return existing;}
        // Bound source validation as well as uploads; overview pages span thousands of chunks.
        if(scanQueue.size()>=1024&&!scanQueue.contains(key))return existing;
        scanQueue.add(key);
        if(!scanQueue.iterator().next().equals(key)||!MapTerrainCache.canWork()||scans>=8||(scans>0&&System.nanoTime()-scanStart>=2_000_000))return existing;
        scanQueue.remove(key);scans++;
        int chunks=key.chunks();
        var images=new MapTerrainCache.Image[chunks*chunks];long signature=level;boolean any=false,complete=true;
        for(int z=0;z<chunks;z++)for(int x=0;x<chunks;x++){
            var tileKey=new WorldMapClient.TileKey(key.layer(),key.x()*chunks+x,key.z()*chunks+z);
            var image=MapTerrainCache.image(tileKey,level);images[z*chunks+x]=image;
            if(image==null&&WorldMapClient.explored(tileKey)||MapTerrainCache.waiting(tileKey))complete=false;
            signature=signature*31+(image==null?0:image.revision());any|=image!=null;
        }
        if(!any)return existing;
        // Keep the saved/previous page intact until its explored source tiles are all ready.
        if(existing!=null&&!complete)return existing;
        if(existing!=null&&existing.signature()==signature&&existing.level()==level){if(complete){checked.put(key,stamp);if(!existing.complete()){existing=new Texture(existing.id(),existing.texture(),existing.level(),existing.signature(),true);textures.put(key,existing);persist(key,existing);}}return existing;}
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
    static void reset(){generation++;scanQueue.clear();visible=Set.of();textureLimit=2048;for(var texture:textures.values())Minecraft.getInstance().getTextureManager().release(texture.id());textures.clear();for(var image:prepared.values())image.image().close();prepared.clear();pending.clear();checked.clear();saved.clear();saveRetry.clear();previewReads.clear();previewPending.clear();}
    private MapRegionTextures(){}
}
