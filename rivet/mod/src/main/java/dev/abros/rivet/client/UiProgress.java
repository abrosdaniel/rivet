package dev.abros.rivet.client;

import dev.abros.rivet.core.InstallProgress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.Locale;

/** Shared 40px progress presentation; the current phase always remains readable. */
final class UiProgress {
 static void draw(GuiGraphics g,Font font,int x,int y,int width,InstallProgress value){
  double fraction=value.fraction();
  String title=Client.text("progress."+value.phase());
  if(fraction>=0)title+=" · "+(int)(fraction*100)+"%";
  g.drawString(font,UiKit.fit(font,title,width),x,y,UiKit.text(),false);
  String detail=value.total()>=0?String.format(Locale.ROOT,"%.1f / %.1f MiB",value.bytes()/1048576d,value.total()/1048576d):Client.text("progress.wait");
  if(value.count()>0)detail+=" · "+value.files()+" / "+value.count()+" "+Client.text("progress.files");
  g.drawString(font,UiKit.fit(font,detail,width),x,y+12,UiKit.muted(),false);
  g.fill(x,y+28,x+width,y+34,UiKit.surface(UiKit.Surface.INPUT));
  if(fraction>=0)g.fill(x,y+28,x+(int)(width*fraction),y+34,UiKit.accent());
  else if(width>0){int segment=Math.max(1,width/4),travel=width-segment;int offset=AccessibilityScreen.animations()?(int)((System.nanoTime()/1_000_000L%1600)*travel/1600):travel/2;g.fill(x+offset,y+28,x+offset+segment,y+34,UiKit.accent());}
 }
 private UiProgress(){}
}
