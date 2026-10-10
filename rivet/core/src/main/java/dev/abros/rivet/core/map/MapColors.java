package dev.abros.rivet.core.map;

/** Texture colour arithmetic independent of Minecraft and resource packs. */
public final class MapColors {
    private MapColors() {}
    public static int tint(int argb,int rgb) {
        return argb&0xff000000|((argb>>>16&255)*(rgb>>>16&255)/255)<<16|((argb>>>8&255)*(rgb>>>8&255)/255)<<8|(argb&255)*(rgb&255)/255;
    }
    public static int over(int top,int bottom) {
        int a=top>>>24,b=bottom>>>24,out=a+b*(255-a)/255;if(out==0)return 0;
        int rgb=0;for(int shift:new int[]{16,8,0})rgb|=(((top>>>shift&255)*a+(bottom>>>shift&255)*b*(255-a)/255)/out)<<shift;
        return out<<24|rgb;
    }
    /** Daylight on a block surface: north-facing light, quantized direct light and altitude. */
    public static int shade(int color,int height,int north,int northWest,boolean glowing) {
        if(color==0)return 0;
        float brightness=brightness(height,north,northWest,glowing);int result=color&0xff000000;
        for(int shift:new int[]{16,8,0})result|=Math.clamp((int)((color>>>shift&255)*brightness),0,255)<<shift;
        return result;
    }
    private static float brightness(int height,int north,int northWest,boolean glowing){
        float vertical=(float)Math.clamp((long)height-north,-128,127);
        float diagonal=(float)Math.clamp((long)height-northWest,-128,127);
        float direct=0;
        if(vertical>-1){
            if(vertical==1&&diagonal==1)direct=1;
            else direct=(float)(((1+vertical)/(float)Math.sqrt((vertical-diagonal)*(vertical-diagonal)+1+vertical*vertical))/Math.sqrt(2));
        }
        float maximum=glowing?.22222224f:.6666667f;
        float directional=direct==1?maximum:direct>0?(float)Math.ceil(direct*10)/10*maximum*.88388f:0;
        float brightness=(glowing?1:.7f)+directional;
        if(!glowing)brightness*=Math.clamp(height/63f,.9f,1f);
        return brightness;
    }
    /** Overlay RGB is already lit; alpha stores the remaining contribution of the ground. */
    public static int surface(int base,int overlay,int height,int north,int northWest,boolean glowing){
        if(base==0)return 0;
        int result=0xff000000;
        float remaining=(overlay>>>24)/255f;
        float illumination=brightness(height,north,northWest,glowing)*(glowing?1:(9f+(base>>>24&15))/24f);
        for(int shift:new int[]{16,8,0})result|=Math.min(255,(int)((base>>>shift&255)*illumination*remaining+(overlay>>>shift&255)))<<shift;
        return result;
    }
    /** Cave depth uses the selected top, not the end of its sixteen-block storage band. */
    public static int cave(int base,int overlay,int height,int topHeight,int north,int northWest,int ceiling,int depth,int worldHeight,boolean glowing,boolean legible){
        if(base==0)return 0;
        if((base&0xffffff)==0x010101&&overlay==0xff000000)return 0xff010101;
        float directional=brightness(height,north,northWest,glowing)/(glowing?1:Math.clamp(height/63f,.9f,1f));
        float altitude=glowing||legible?1:Math.clamp(ceiling==MapLayer.FULL?.7f+.3f*height/Math.max(1,worldHeight):.7f+.3f*(height-ceiling+depth)/depth,.9f,1f);
        float ground=directional*altitude*(glowing?1:legible?caveDepth(height,ceiling,depth):(9f+(base>>>24&15))/24f);
        float water=legible?caveDepth(topHeight,ceiling,depth):1;
        float remaining=(overlay>>>24)/255f;int result=0xff000000;
        for(int shift:new int[]{16,8,0})result|=Math.clamp((int)((base>>>shift&255)*ground*remaining+(overlay>>>shift&255)*water),0,255)<<shift;
        return result;
    }
    public record Style(boolean lighting,boolean depth,int slopes,int view,int skyDarken,float ambient){
        public Style {if(skyDarken<0||skyDarken>15||!Float.isFinite(ambient)||ambient<0||ambient>1)throw new IllegalArgumentException("Invalid environment light");if(view<0||view>3)throw new IllegalArgumentException("Invalid map view");if(slopes<0||slopes>3)throw new IllegalArgumentException("Invalid slope mode");}
        public Style(boolean lighting,boolean depth,int slopes){this(lighting,depth,slopes,0,0,0);}
        public Style(boolean lighting,boolean depth,int slopes,int view){this(lighting,depth,slopes,view,0,0);}
        public static final Style DEFAULT=new Style(true,true,2);
    }
    /** Rebuild colour from original materials so display changes also affect saved water/glass. */
    public static int render(MapTile tile,int x,int z,int north,int northWest,int ceiling,int depth,int worldHeight,boolean legible,Style style){
        if(tile.color(x,z)==0)return 0;
        legible=legible&&style.lighting();
        // Empty cave samples stay empty rather than becoming coloured biome/height cells.
        if(ceiling!=MapLayer.SURFACE&&(tile.color(x,z)&0xffffff)==0x010101&&(tile.layers(x,z)==null||tile.layers(x,z).isEmpty())&&tile.overlay(x,z)==0xff000000)return 0xff010101;
        if(style.view()!=0){int terrain=style.view()==MapViews.NIGHT?render(tile,x,z,north,northWest,ceiling,depth,worldHeight,legible,new Style(style.lighting(),style.depth(),style.slopes())):0;
            return MapViews.color(tile,x,z,terrain,north,northWest,style.view());}
        if(style.skyDarken()!=0||style.ambient()!=0||tile.skyLight(x,z)>=0)return environmental(tile,x,z,north,northWest,ceiling,depth,worldHeight,legible,style);
        int base=tile.color(x,z);if(base==0)return 0;
        int h=tile.groundHeight(x,z),top=tile.height(x,z);boolean glow=tile.glowing(x,z),cave=ceiling!=MapLayer.SURFACE;
        var layers=tile.layers(x,z);
        if(layers==null){
            if(style.equals(Style.DEFAULT))return cave?cave(base,tile.overlay(x,z),h,top,north,northWest,ceiling,depth,worldHeight,glow,legible):surface(base,tile.overlay(x,z),h,north,northWest,glow);
            // Older files contain a baked overlay; retain it while applying new ground settings.
            float altitude=1;if(!glow&&style.depth()&&!(cave&&legible))altitude=Math.clamp(ceiling==MapLayer.SURFACE?h/63f:ceiling==MapLayer.FULL?.7f+.3f*h/Math.max(1,worldHeight):.7f+.3f*(h-ceiling+depth)/depth,style.slopes()>=2?.9f:.7f,style.slopes()>=2?1:1.15f);
            float factor=direction(h,north,northWest,glow,style.slopes())*altitude*(glow?1:cave&&legible?caveDepth(h,ceiling,depth):style.lighting()?(9f+(base>>>24&15))/24f:1);
            int overlay=tile.overlay(x,z),result=0xff000000;float remaining=(overlay>>>24)/255f,overlayDepth=cave&&legible?caveDepth(top,ceiling,depth):1;
            for(int shift:new int[]{16,8,0})result|=Math.clamp((int)((base>>>shift&255)*factor*remaining+(overlay>>>shift&255)*overlayDepth),0,255)<<shift;
            return result;
        }
        boolean empty=(base&0xffffff)==0x010101&&cave;
        if(empty&&layers.isEmpty())return 0xff010101;
        float remaining=1;int sun=15,r=0,g=0,b=0;
        for(var layer:layers){
            float alpha=(layer.color()>>>24)/255f;
            float amount=alpha*remaining*(style.lighting()?(9f+Math.max(sun,layer.light()))/24f:1);
            r=(int)(r+(layer.color()>>>16&255)*amount);g=(int)(g+(layer.color()>>>8&255)*amount);b=(int)(b+(layer.color()&255)*amount);
            remaining*=1-alpha;sun=Math.max(0,sun-layer.opacity());
        }
        float altitude=1;
        if(!glow&&style.depth()&&!(cave&&legible)){
            altitude=ceiling==MapLayer.SURFACE?h/63f:ceiling==MapLayer.FULL?.7f+.3f*h/Math.max(1,worldHeight):.7f+.3f*(h-ceiling+depth)/depth;
            altitude=Math.clamp(altitude,style.slopes()>=2?.9f:.7f,style.slopes()>=2?1:1.15f);
        }
        float ground=empty?0:direction(h,north,northWest,glow,style.slopes())*altitude;
        if(cave&&legible){if(!glow)ground*=caveDepth(h,ceiling,depth);float factor=caveDepth(top,ceiling,depth);r=(int)(r*factor);g=(int)(g*factor);b=(int)(b*factor);}
        else if(!glow&&style.lighting()&&!layers.isEmpty())ground*=(9f+Math.max(sun,base>>>24&15))/24f;
        int topLight=glow?15:layers.isEmpty()?base>>>24&15:layers.getFirst().light();
        float light=style.lighting()&&cave&&!legible?(9f+topLight)/24f:1;
        int red=Math.clamp((int)(((base>>>16&255)*ground*remaining+r)*light),0,255);
        int green=Math.clamp((int)(((base>>>8&255)*ground*remaining+g)*light),0,255);
        int blue=Math.clamp((int)(((base&255)*ground*remaining+b)*light),0,255);
        return 0xff000000|red<<16|green<<8|blue;
    }
    private static float light(int block,int sky,Style style){
        if(!style.lighting())return 1;
        int level=Math.max(Math.max(0,block),Math.max(0,sky-style.skyDarken()));
        float value=(9f+level)/24f;
        return value+(1-value)*style.ambient();
    }
    private static int environmental(MapTile tile,int x,int z,int north,int northWest,int ceiling,int depth,int worldHeight,boolean legible,Style style){
        boolean cave=ceiling!=MapLayer.SURFACE,glow=tile.glowing(x,z);
        int h=tile.groundHeight(x,z),base=tile.color(x,z),sky=tile.skyLight(x,z);
        if(sky<0)sky=cave?0:base>>>24&15;
        int block=tile.blockLight(x,z);if(block<0)block=cave?base>>>24&15:0;
        float altitude=1;
        if(!glow&&style.depth()&&!(cave&&legible))altitude=Math.clamp(cave?(ceiling==MapLayer.FULL?.7f+.3f*h/Math.max(1,worldHeight):.7f+.3f*(h-ceiling+depth)/depth):h/63f,style.slopes()>=2?.9f:.7f,style.slopes()>=2?1:1.15f);
        float ground=direction(h,north,northWest,glow,style.slopes())*altitude*(glow?1:cave&&legible?caveDepth(h,ceiling,depth):light(block,sky,style));
        float remaining=1;float[] rgb=new float[3];var layers=tile.layers(x,z);
        if(layers==null){int overlay=tile.overlay(x,z);remaining=(overlay>>>24)/255f;float factor=cave&&legible?caveDepth(tile.height(x,z),ceiling,depth):light(block,sky,style);for(int n=0;n<3;n++)rgb[n]=(overlay>>>(16-n*8)&255)*factor;}
        else for(var layer:layers){float a=(layer.color()>>>24)/255f;int ls=layer.skyLight()<0?(cave?0:15):layer.skyLight();float factor=cave&&legible?caveDepth(tile.height(x,z),ceiling,depth):light(layer.light(),ls,style);for(int n=0;n<3;n++)rgb[n]+=(layer.color()>>>(16-n*8)&255)*a*remaining*factor;remaining*=1-a;}
        int result=0xff000000;for(int n=0;n<3;n++)result|=Math.clamp((int)((base>>>(16-n*8)&255)*ground*remaining+rgb[n]),0,255)<<(16-n*8);return result;
    }
    private static float direction(int height,int north,int northWest,boolean glow,int mode){
        if(mode==0)return 1;
        float vertical=Math.clamp((long)height-north,-128,127),diagonal=Math.clamp((long)height-northWest,-128,127);
        if(mode==1)return vertical>0?1.15f:vertical<0?.85f:1;
        float cosine=0;
        if(vertical>=0){if(vertical==1&&(mode==3||diagonal==1))cosine=1;else{float horizontal=mode==3?0:vertical-diagonal;cosine=(float)((1+vertical)/Math.sqrt(horizontal*horizontal+1+vertical*vertical)/Math.sqrt(2));}}
        float maximum=glow?.22222224f:.6666667f;
        return (glow?1:.7f)+(cosine==1?maximum:cosine>0?(float)Math.ceil(cosine*10)/10*maximum*.88388f:0);
    }
    public static float caveDepth(int height,int ceiling,int depth){
        if(ceiling==MapLayer.FULL){int period=Math.floorMod(height,128);int folded=period<64?period:127-period;return (17f+folded)/80f;}
        return Math.clamp((height-ceiling+depth)/(float)Math.max(1,depth),0,1);
    }
}
