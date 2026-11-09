package dev.abros.rivet.client;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL33C.*;

/** GPU implementation behind UiCanvas. No screen-specific geometry or input handling. */
final class SkiaUiBackend implements AutoCloseable {
    static {SkiaNative.load();}
    private final DirectContext context;
    private final Typeface typeface;
    private final Font font;
    private final Paint paint=new Paint().setAntiAlias(true);
    private final MaskFilter shadow=MaskFilter.makeBlur(FilterBlurMode.NORMAL,3);
    private final ColorSpace colorSpace=ColorSpace.getSRGB();
    private final java.util.Map<Integer,Boolean> supported=boundedCache();
    private final java.util.Map<String,Float> advances=boundedCache();
    private static <K,V> java.util.Map<K,V> boundedCache(){return new java.util.LinkedHashMap<K,V>(256,.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<K,V> entry){return size()>4096;}
    };}
    private BackendRenderTarget target;
    private Surface surface;
    private Canvas canvas;
    private int framebuffer=-1,texture=-1,pixelWidth,pixelHeight;
    SkiaUiBackend() throws java.io.IOException {
        try(State ignored=new State();var in=SkiaUiBackend.class.getResourceAsStream("/assets/rivet/font/notosans.ttf")) {
            if(in==null)throw new java.io.IOException("Missing Rivet UI font");
            try(var data=Data.makeFromBytes(in.readAllBytes())){typeface=java.util.Objects.requireNonNull(FontMgr.getDefault().makeFromData(data));}
            font=new Font(typeface,10).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS);
            context=DirectContext.makeGL();
        }
    }
    void draw(java.util.List<UiCanvas.Command> commands) {
        try(State saved=new State()) {
            for(int i=0;i<12;i++)glBindSampler(i,0);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);glPixelStorei(GL_UNPACK_SKIP_ROWS,0);
            context.resetAll();
            var output=Minecraft.getInstance().getMainRenderTarget();
            if(framebuffer!=output.frameBufferId||texture!=output.getColorTextureId()||pixelWidth!=output.width||pixelHeight!=output.height){
                if(surface!=null)surface.close();if(target!=null)target.close();
                framebuffer=output.frameBufferId;texture=output.getColorTextureId();pixelWidth=output.width;pixelHeight=output.height;
                target=BackendRenderTarget.makeGL(pixelWidth,pixelHeight,0,8,framebuffer,FramebufferFormat.GR_GL_RGBA8);
                surface=Surface.wrapBackendRenderTarget(context,target,SurfaceOrigin.BOTTOM_LEFT,ColorType.RGBA_8888,colorSpace);
                if(surface==null)throw new IllegalStateException("Cannot wrap Minecraft framebuffer");canvas=surface.getCanvas();
            }
            canvas.save();
            if(saved.enabled[3])canvas.clipRect(Rect.makeXYWH(saved.scissor[0],pixelHeight-saved.scissor[1]-saved.scissor[3],saved.scissor[2],saved.scissor[3]));
            float scale=(float)Minecraft.getInstance().getWindow().getGuiScale();canvas.scale(scale,scale);
            // Consecutive glyphs/shapes usually share a transform. Apply it once per run,
            // preserving order and every native/scissor boundary.
            canvas.save();
            org.joml.Matrix4f previous=null;
            try{for(var command:commands){
                var m=command.pose();
                if(previous==null||!previous.equals(m)){
                    canvas.restore();canvas.save();
                    canvas.concat(new Matrix33(m.m00(),m.m10(),m.m30(),m.m01(),m.m11(),m.m31(),0,0,1));previous=m;
                }
                command.draw(this);
            }}finally{canvas.restore();canvas.restore();context.flush();context.submit(false);}
        }
    }
    void rounded(float x,float y,float w,float h,float radius,int color,boolean stroke){
        paint.setColor(color).setMode(stroke?PaintMode.STROKE:PaintMode.FILL).setStrokeWidth(1);
        canvas.drawRRect(RRect.makeXYWH(x+(stroke?.5f:0),y+(stroke?.5f:0),w-(stroke?1:0),h-(stroke?1:0),radius),paint);
    }
    void sector(float x,float y,float inner,float outer,float start,float sweep,int color){
        paint.setColor(color).setMode(PaintMode.FILL);
        try(var builder=new PathBuilder()){
            builder.arcTo(Rect.makeXYWH(x-outer,y-outer,outer*2,outer*2),start,sweep,true);
            builder.arcTo(Rect.makeXYWH(x-inner,y-inner,inner*2,inner*2),start+sweep,-sweep,false).closePath();
            try(var path=builder.detach()){canvas.drawPath(path,paint);}
        }
    }
    void shadow(float x,float y,float w,float h,float radius,int color){
        paint.setMaskFilter(shadow);
        try{rounded(x,y,w,h,radius,color,false);}finally{paint.setMaskFilter(null);}
    }
    void line(float x,float y,float x2,float y2,int color){paint.setColor(color).setMode(PaintMode.STROKE).setStrokeWidth(1);canvas.drawLine(x+.5f,y+.5f,x2+.5f,y2+.5f,paint);}
    boolean supports(int point){return supported.computeIfAbsent(point,p->font.getUTF32Glyph(p)!=0);}
    void glyph(float x,float y,String text,float advance,int color,boolean bold,boolean italic){
        font.setEmboldened(bold).setSkewX(italic?-.2f:0);paint.setColor(color).setMode(PaintMode.FILL);
        // Keep Minecraft's advances: caret, selection, wrapping and click hitboxes remain identical.
        String key=(bold?"b":"n")+(italic?"i":"n")+text;
        float measured=advances.computeIfAbsent(key,k->font.measureTextWidth(text));canvas.save();canvas.translate(x,y);
        if(measured>advance&&advance>0)canvas.scale(advance/measured,1);
        canvas.drawString(text,0,8,font,paint);canvas.restore();
    }
    public void close(){try(State ignored=new State()){if(surface!=null)surface.close();if(target!=null)target.close();font.close();typeface.close();paint.close();shadow.close();colorSpace.close();context.close();}}

    private static final class State implements AutoCloseable {
        final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING);
        final int array=glGetInteger(GL_ARRAY_BUFFER_BINDING),uniform=glGetInteger(GL_UNIFORM_BUFFER_BINDING);
        final int active=glGetInteger(GL_ACTIVE_TEXTURE);
        final int[] textures=new int[12],samplers=new int[12],viewport=new int[4],scissor=new int[4];
        final int drawFbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),readFbo=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int equation=glGetInteger(GL_BLEND_EQUATION_RGB),equationAlpha=glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final int pixelUnpack=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int depthFunc=glGetInteger(GL_DEPTH_FUNC),renderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING);
        final boolean depthMask=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB);
        final int srcAlpha=glGetInteger(GL_BLEND_SRC_ALPHA),dstAlpha=glGetInteger(GL_BLEND_DST_ALPHA);
        final int cull=glGetInteger(GL_CULL_FACE_MODE),front=glGetInteger(GL_FRONT_FACE);
        final int clear=glGetInteger(GL_STENCIL_CLEAR_VALUE);
        final int[] caps={GL_BLEND,GL_CULL_FACE,GL_DEPTH_TEST,GL_SCISSOR_TEST,GL_STENCIL_TEST,GL_FRAMEBUFFER_SRGB,GL_POLYGON_OFFSET_FILL,GL_RASTERIZER_DISCARD};
        final boolean[] enabled=new boolean[caps.length];
        final int[] unpack={GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS};
        final int[] unpackValues=new int[4];
        final int[][] stencil=new int[2][7];
        final byte[] mask=new byte[4];
        State(){
            for(int i=0;i<textures.length;i++){glActiveTexture(GL_TEXTURE0+i);textures[i]=glGetInteger(GL_TEXTURE_BINDING_2D);samplers[i]=glGetInteger(GL_SAMPLER_BINDING);}
            glActiveTexture(active);glGetIntegerv(GL_VIEWPORT,viewport);glGetIntegerv(GL_SCISSOR_BOX,scissor);
            for(int i=0;i<caps.length;i++)enabled[i]=glIsEnabled(caps[i]);
            for(int i=0;i<4;i++)unpackValues[i]=glGetInteger(unpack[i]);
            int[][] keys={{GL_STENCIL_FUNC,GL_STENCIL_REF,GL_STENCIL_VALUE_MASK,GL_STENCIL_WRITEMASK,GL_STENCIL_FAIL,GL_STENCIL_PASS_DEPTH_FAIL,GL_STENCIL_PASS_DEPTH_PASS},
                {GL_STENCIL_BACK_FUNC,GL_STENCIL_BACK_REF,GL_STENCIL_BACK_VALUE_MASK,GL_STENCIL_BACK_WRITEMASK,GL_STENCIL_BACK_FAIL,GL_STENCIL_BACK_PASS_DEPTH_FAIL,GL_STENCIL_BACK_PASS_DEPTH_PASS}};
            for(int i=0;i<2;i++)for(int j=0;j<7;j++)stencil[i][j]=glGetInteger(keys[i][j]);
            try(var s=MemoryStack.stackPush()){var b=s.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,b);b.get(mask);}
        }
        public void close(){
            glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,array);glBindBuffer(GL_UNIFORM_BUFFER,uniform);
            for(int i=0;i<textures.length;i++){glActiveTexture(GL_TEXTURE0+i);glBindTexture(GL_TEXTURE_2D,textures[i]);glBindSampler(i,samplers[i]);}glActiveTexture(active);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo);glBindFramebuffer(GL_READ_FRAMEBUFFER,readFbo);glBindRenderbuffer(GL_RENDERBUFFER,renderbuffer);
            glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glScissor(scissor[0],scissor[1],scissor[2],scissor[3]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pixelUnpack);glBlendEquationSeparate(equation,equationAlpha);glDepthFunc(depthFunc);glDepthMask(depthMask);
            glBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);glCullFace(cull);glFrontFace(front);
            glColorMask(mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);glClearStencil(clear);
            for(int i=0;i<2;i++){int face=i==0?GL_FRONT:GL_BACK;int[] s=stencil[i];glStencilFuncSeparate(face,s[0],s[1],s[2]);glStencilMaskSeparate(face,s[3]);glStencilOpSeparate(face,s[4],s[5],s[6]);}
            for(int i=0;i<4;i++)glPixelStorei(unpack[i],unpackValues[i]);
            for(int i=0;i<caps.length;i++)if(enabled[i])glEnable(caps[i]);else glDisable(caps[i]);
        }
    }
}
