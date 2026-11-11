package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

/** Checks actual installed mixins and text metrics on the render thread. */
final class UiFontContracts {
 static void verify(GuiGraphics g){
  var mc=Minecraft.getInstance();var vanilla=mc.font;int width=vanilla.width("oreshek228_ Не в сети");
  var ui=(UiFont)UiFont.get();
  for(String text:new String[]{"oreshek228_","@abrosdaniel","Не в сети","Игроки","iiiiWWWW 3000"}){
   float sum=0;for(int p:text.codePoints().toArray())sum+=UiTextMetrics.advance(p,false,false);
   check(Math.abs(ui.getSplitter().stringWidth(text)-sum)<.001,"Measured text uses foreign advances: "+text);
   check(ui.width(text)==(int)Math.ceil(sum),"Whole string rounding");
   String clipped=ui.plainSubstrByWidth(text,ui.width(text)/2);
   check(ui.width(clipped)<=ui.width(text)/2,"Clipping exceeds measured width");
  }
  var styled=Component.literal("ore").append(Component.literal("shek").withStyle(net.minecraft.ChatFormatting.BOLD)).append("228_");
  float[] sum={0};styled.getVisualOrderText().accept((i,s,p)->{sum[0]+=ui.advance(p,s);return true;});
  check(ui.width(styled)==(int)Math.ceil(sum[0]),"Rich text metrics diverge");
  byte[] baseline=nativeSample(g);
  var old=mc.screen;var rivet=new AccessibilityScreen(null);rivet.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
  try{
   mc.screen=rivet;
   check(!UiCanvas.active(),"Opening a Rivet screen changes the global rendering mode");
   check(java.util.Arrays.equals(baseline,nativeSample(g)),"Native framebuffer text changes while Rivet screen is open");
   UiCanvas.screen(g,rivet,()->{
    check(UiCanvas.active(),"Rivet screen scope missing");
    UiCanvas.screen(g,new TitleScreen(),()->check(!UiCanvas.active(),"Vanilla parent inherits Rivet text"));
    check(UiCanvas.active(),"Nested scope not restored");
   });
   try{UiCanvas.screen(g,rivet,()->{throw new IllegalArgumentException("fixture");});}catch(IllegalArgumentException expected){}
   check(!UiCanvas.active(),"Exceptional screen exit leaks scope");
   check(java.util.Arrays.equals(baseline,nativeSample(g)),"Native framebuffer text changes after Rivet render");
   check(mc.font==vanilla&&vanilla.width("oreshek228_ Не в сети")==width,"Global font or metrics changed");
  }finally{mc.screen=old;}
  System.out.println("RIVET_FONT_OK: isolated screen scopes, fractional metrics, rich text and clipping");
 }
 private static byte[] nativeSample(GuiGraphics g){
  var mc=Minecraft.getInstance();g.fill(8,120,240,138,0xff000000);
  g.drawString(mc.font,"Chat: oreshek228_ Не в сети",8,122,0xffffffff,false);g.flush();
  int scale=(int)mc.getWindow().getGuiScale(),w=232*scale,h=18*scale;
  var buffer=org.lwjgl.system.MemoryUtil.memAlloc(w*h*4);
  try{org.lwjgl.opengl.GL33C.glReadPixels(8*scale,mc.getMainRenderTarget().height-138*scale,w,h,org.lwjgl.opengl.GL33C.GL_RGBA,org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE,buffer);byte[] result=new byte[w*h*4];buffer.get(result);return result;}finally{org.lwjgl.system.MemoryUtil.memFree(buffer);}
 }
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
