package dev.abros.rivet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.opengl.GL33C.*;

/** Actual framebuffer checks: clipping, transforms, alpha and restored GL state. */
final class SkiaUiContracts {
    static void verify(GuiGraphics g){
        g.flush();int program=glGetInteger(GL_CURRENT_PROGRAM),active=glGetInteger(GL_ACTIVE_TEXTURE);
        boolean depth=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND);
        UiCanvas.scoped(g,()->{
            UiKit.surface(g,8,8,80,80,0xff203040);
            g.enableScissor(20,20,60,60);
            g.pose().pushPose();g.pose().translate(30,0,0);
            UiKit.surface(g,0,20,60,40,0x80ff0000);
            g.pose().popPose();g.disableScissor();
        });
        pixel(12,12,32,48,64);pixel(24,30,32,48,64);pixel(40,30,144,24,32);pixel(64,30,32,48,64);
        check(glGetInteger(GL_CURRENT_PROGRAM)==program,"shader program leaked");
        check(glGetInteger(GL_ACTIVE_TEXTURE)==active,"active texture leaked");
        check(glIsEnabled(GL_DEPTH_TEST)==depth&&glIsEnabled(GL_BLEND)==blend,"GL capabilities leaked");
        float[] before=RenderSystem.getShaderColor().clone();
        try{
            RenderSystem.setShaderColor(.5f,1,1,1);
            UiCanvas.scoped(g,()->UiKit.surface(g,10,10,8,8,0xff804020));
            pixel(12,12,64,64,32);
        }finally{RenderSystem.setShaderColor(before[0],before[1],before[2],before[3]);}
        UiCanvas.scoped(g,()->{
            UiKit.surface(g,100,20,80,80,0xff203040);
            UiKit.sector(g,140,60,10,30,0,Math.PI,0xff00ff00);
        });
        pixel(140,60,32,48,64);pixel(140,80,0,255,0);pixel(140,40,32,48,64);
        UiCanvas.scoped(g,()->{
            UiKit.surface(g,200,20,70,70,0xff000000);
            g.pose().pushPose();g.pose().translate(200,20,0);g.pose().scale(.73f,.73f,1);
            UiHudSurface.fill(g,0,0,80,80,6,0x80ffffff);g.pose().popPose();
        });
        for(int y=30;y<65;y++)pixel(220,y,128,128,128);
        check((UiTheme.hoverOverlay(0)>>>24)==0,"Quiet hover leaves opaque background");
        check((UiTheme.hoverOverlay(.5f)>>>24)==128,"Quiet hover opacity is not proportional");
        UiCanvas.scoped(g,()->{
            UiKit.surface(g,8,100,110,14,0xff000000);
            var mixed=net.minecraft.network.chat.Component.literal("Text ").append(net.minecraft.network.chat.Component.literal("x").withStyle(net.minecraft.ChatFormatting.OBFUSCATED)).append(" → Текст");
            check(UiCanvas.drawText(g,Minecraft.getInstance().font,mixed.getVisualOrderText(),8,100,0xffffffff,false),"One fallback glyph reverts the entire text line");
        });
        g.flush();ink(8+Minecraft.getInstance().font.width("Text x → "),100,25,10);
        var tabs=new java.util.ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
        UiTabs.build(Minecraft.getInstance().screen,Minecraft.getInstance().font,new dev.abros.rivet.core.NativeLayout.Box(10,10,200,28),java.util.List.of("One","Two"),0,tabs::add,i->{},true);
        check(tabs.size()==2&&tabs.stream().allMatch(t->t.getHeight()==28),"Tab height ignored layout");
        check(glGetError()==GL_NO_ERROR,"OpenGL error after mixed rendering");
        System.out.println("RIVET_SKIA_UI_OK: framebuffer alpha, clipping, pose, shader tint and GL state");
    }
    private static void pixel(int x,int y,int r,int g,int b){
        var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();
        try(var stack=MemoryStack.stackPush()){
            var bytes=stack.malloc(4);glReadPixels((int)((x+.5)*scale),mc.getMainRenderTarget().height-1-(int)((y+.5)*scale),1,1,GL_RGBA,GL_UNSIGNED_BYTE,bytes);
            check(Math.abs(Byte.toUnsignedInt(bytes.get(0))-r)<=3&&Math.abs(Byte.toUnsignedInt(bytes.get(1))-g)<=3&&Math.abs(Byte.toUnsignedInt(bytes.get(2))-b)<=3,"Unexpected framebuffer colour at "+x+","+y+": "+Byte.toUnsignedInt(bytes.get(0))+","+Byte.toUnsignedInt(bytes.get(1))+","+Byte.toUnsignedInt(bytes.get(2)));
        }
    }
    private static void ink(int x,int y,int w,int h){
        var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();int pw=(int)(w*scale),ph=(int)(h*scale);
        try(var stack=MemoryStack.stackPush()){
            var pixels=stack.malloc(pw*ph*4);glReadPixels((int)(x*scale),mc.getMainRenderTarget().height-(int)((y+h)*scale),pw,ph,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            int lit=0;for(int i=0;i<pw*ph;i++)if(Byte.toUnsignedInt(pixels.get(i*4))>100)lit++;
            check(lit>5,"Text after native fallback glyph disappeared");
        }
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
