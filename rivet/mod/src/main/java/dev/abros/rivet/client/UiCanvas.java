package dev.abros.rivet.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Reusable ordered UI drawing. Native boundaries flush the batch; input stays in Minecraft. */
public final class UiCanvas {
    interface Command { Matrix4f pose(); void draw(SkiaUiBackend backend); }
    private record Shape(Matrix4f pose,float x,float y,float w,float h,float radius,int color,boolean stroke) implements Command {
        public void draw(SkiaUiBackend b){b.rounded(x,y,w,h,radius,color,stroke);}
    }
    private record Shadow(Matrix4f pose,float x,float y,float w,float h,float radius,int color) implements Command {
        public void draw(SkiaUiBackend b){b.shadow(x,y,w,h,radius,color);}
    }
    private record Line(Matrix4f pose,float x,float y,float x2,float y2,int color) implements Command {
        public void draw(SkiaUiBackend b){b.line(x,y,x2,y2,color);}
    }
    private record Sector(Matrix4f pose,float x,float y,float inner,float outer,float start,float sweep,int color) implements Command {
        public void draw(SkiaUiBackend b){b.sector(x,y,inner,outer,start,sweep,color);}
    }
    private record Glyph(Matrix4f pose,float x,float y,String value,float advance,int color,boolean bold,boolean italic) implements Command {
        public void draw(SkiaUiBackend b){b.glyph(x,y,value,advance,color,bold,italic);}
    }
    private static final ArrayList<Command> pending=new ArrayList<>();
    private static SkiaUiBackend backend;
    private static GuiGraphics owner;
    private static boolean flushing,failed,nativeText;
    private static int scope;
    private static final boolean PROFILE="true".equals(System.getenv("RIVET_UI_PROFILE"));
    private static final long[] timings=PROFILE?new long[240]:null;
    private static long frameNanos;private static int profileFrames,profileSamples,frameBatches,maxBatches;
    private UiCanvas(){}
    public static boolean active(){var s=Minecraft.getInstance().screen;return scope>0||UiTheme.owns(s)||s instanceof TradeScreen t&&t.rivetStyle();}
    public static void scoped(GuiGraphics g,Runnable action){scope++;try{action.run();}finally{flush(g);scope--;}}
    private static boolean ready(GuiGraphics g){
        if(flushing||failed||scope==0&&Minecraft.getInstance().screen instanceof TradeScreen t&&!t.rivetStyle())return false;
        try{
            if(backend==null){
                // Enabling stencil recreates the framebuffer: do this before the first UI frame.
                var target=Minecraft.getInstance().getMainRenderTarget();
                if(!target.isStencilEnabled()){target.enableStencil();return false;}
                backend=new SkiaUiBackend();
            }
            if(owner!=g){if(owner!=null)flush(owner);owner=g;}
            if(pending.isEmpty()){flushing=true;try{g.flush();}finally{flushing=false;}}
            return true;
        }catch(Exception|LinkageError ex){disable(ex);return false;}
    }
    private static void disable(Throwable ex){failed=true;pending.clear();com.mojang.logging.LogUtils.getLogger().error("Rivet UI renderer unavailable; using native UI",ex);if("true".equals(System.getenv("RIVET_UI_RENDER_STRICT")))throw new AssertionError("Skia UI fallback in strict verification",ex);}
    public static void flush(GuiGraphics g){
        if(flushing||pending.isEmpty())return;
        flushing=true;
        try{g.flush();long start=PROFILE?System.nanoTime():0;backend.draw(pending);if(PROFILE){frameNanos+=System.nanoTime()-start;frameBatches++;}}catch(Exception|LinkageError ex){disable(ex);}finally{pending.clear();flushing=false;}
    }
    public static void endFrame(){
        if(owner!=null)flush(owner);
        if(PROFILE&&frameBatches>0){
            if(++profileFrames>120){timings[profileSamples++]=frameNanos;maxBatches=Math.max(maxBatches,frameBatches);}
            if(profileSamples==timings.length){
                Arrays.sort(timings);
                System.out.println("RIVET_UI_RENDER_CPU samples=240 p50Ms="+timings[120]/1e6+" p95Ms="+timings[228]/1e6+" maxMs="+timings[239]/1e6+" maxBatches="+maxBatches);
                profileSamples=0;maxBatches=0;
            }
            frameNanos=0;frameBatches=0;
        }
    }
    public static void close(){if(backend!=null){backend.close();backend=null;}pending.clear();owner=null;}
    private static int tint(int color){
        float[] rgba=com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        return Math.round((color>>>24)*rgba[3])<<24|Math.round((color>>16&255)*rgba[0])<<16
            |Math.round((color>>8&255)*rgba[1])<<8|Math.round((color&255)*rgba[2]);
    }
    public static boolean selection(GuiGraphics g,int x,int y,int x2,int y2){
        int color=(UiKit.accent()&0x00ffffff)|(AccessibilityScreen.highContrast()?0x70000000:0x48000000);
        return rounded(g,Math.min(x,x2),Math.min(y,y2),Math.abs(x2-x),Math.abs(y2-y),1,color,false);
    }
    public static boolean rounded(GuiGraphics g,float x,float y,float w,float h,float radius,int color,boolean stroke){
        if(w<=0||h<=0)return true;if(!ready(g))return false;
        pending.add(new Shape(new Matrix4f(g.pose().last().pose()),x,y,w,h,radius,tint(color),stroke));return true;
    }
    static boolean sector(GuiGraphics g,float x,float y,float inner,float outer,float start,float sweep,int color){
        if(!ready(g))return false;
        pending.add(new Sector(new Matrix4f(g.pose().last().pose()),x,y,inner,outer,start,sweep,tint(color)));return true;
    }
    static boolean shadow(GuiGraphics g,float x,float y,float w,float h,float radius,int color){
        if(!ready(g))return false;
        pending.add(new Shadow(new Matrix4f(g.pose().last().pose()),x,y,w,h,radius,tint(color)));return true;
    }
    static boolean line(GuiGraphics g,float x,float y,float x2,float y2,int color){if(!ready(g))return false;pending.add(new Line(new Matrix4f(g.pose().last().pose()),x,y,x2,y2,tint(color)));return true;}
    public static boolean drawText(GuiGraphics g,Font nativeFont,FormattedCharSequence value,float x,float y,int color,boolean shadow){
        if(nativeText||!ready(g))return false;
        int base=(color&0xfc000000)==0?color|0xff000000:color;
        var pose=new Matrix4f(g.pose().last().pose());float[] cursor={x};
        var fallback=new ArrayList<Runnable>();
        value.accept((index,style,point)->{
            String glyph=new String(Character.toChars(point));float advance=nativeFont.width(FormattedCharSequence.forward(glyph,style));
            if(style.isObfuscated()||!style.getFont().equals(Style.DEFAULT_FONT)||!backend.supports(point)&&!Character.isWhitespace(point)){
                float at=cursor[0];fallback.add(()->g.drawString(nativeFont,FormattedCharSequence.forward(glyph,style),at,y,base,shadow));
                cursor[0]+=advance;return true;
            }
            int tint=tint(style.getColor()==null?base:(base&0xff000000)|style.getColor().getValue());
            if(!Character.isWhitespace(point)){
                if(shadow)pending.add(new Glyph(pose,cursor[0]+1,y+1,glyph,advance,(tint&0xff000000)|((tint&0xfcfcfc)>>2),style.isBold(),style.isItalic()));
                pending.add(new Glyph(pose,cursor[0],y,glyph,advance,tint,style.isBold(),style.isItalic()));
            }
            if(style.isUnderlined())pending.add(new Line(pose,cursor[0],y+8,cursor[0]+advance,y+8,tint));
            if(style.isStrikethrough())pending.add(new Line(pose,cursor[0],y+4,cursor[0]+advance,y+4,tint));
            cursor[0]+=advance;return true;
        });
        if(!fallback.isEmpty()){
            flush(g);nativeText=true;
            try{fallback.forEach(Runnable::run);}finally{nativeText=false;}
        }
        return true;
    }
    public static void tooltipText(GuiGraphics g,Font font,FormattedCharSequence value,int x,int y){
        text(g,font,value,x,y,UiKit.text(),false);
    }
    public static boolean tooltipBackground(GuiGraphics g,int x,int y,int w,int h){
        if(!ready(g))return false;
        UiKit.material(g,x-4,y-4,w+8,h+8);return true;
    }
    public static FormattedCharSequence sequence(Font f,String s){String value=f.isBidirectional()?f.bidirectionalShaping(s):s;return sink->net.minecraft.util.StringDecomposer.iterateFormatted(value,Style.EMPTY,sink);}
    public static int text(GuiGraphics g,Font f,String s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
    public static int text(GuiGraphics g,Font f,Component s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
    public static int text(GuiGraphics g,Font f,FormattedCharSequence s,int x,int y,int c){return text(g,f,s,x,y,c,true);}
    public static int text(GuiGraphics g,Font f,String s,int x,int y,int c,boolean shadow){return text(g,f,s==null?FormattedCharSequence.EMPTY:sequence(f,s),x,y,c,shadow);}
    public static int text(GuiGraphics g,Font f,Component s,int x,int y,int c,boolean shadow){return text(g,f,s.getVisualOrderText(),x,y,c,shadow);}
    public static int text(GuiGraphics g,Font f,FormattedCharSequence s,int x,int y,int c,boolean shadow){if(drawText(g,f,s,x,y,c,shadow))return x+f.width(s)+(shadow?1:0);return g.drawString(f,s,x,y,c,shadow);}
}
