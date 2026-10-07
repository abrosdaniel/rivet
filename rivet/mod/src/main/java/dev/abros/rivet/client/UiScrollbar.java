package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Same thumb geometry and colours for independent lists and scrolling screens. */
final class UiScrollbar {
 private UiScrollbar(){}
 private static dev.abros.rivet.core.ScrollLayout geometry(int top,int bottom){
  var track=new dev.abros.rivet.core.NativeLayout.Box(0,top,6,Math.max(0,bottom-top));
  return new dev.abros.rivet.core.ScrollLayout(track,new dev.abros.rivet.core.NativeLayout.Box(0,top,0,track.height()),track);
 }
 static int thumb(int top,int bottom,int visible,int count){return geometry(top,bottom).thumb(visible,count,0).height();}
 static int thumbTop(int top,int bottom,int visible,int count,double position){return geometry(top,bottom).thumb(visible,count,position).y();}
 static void draw(GuiGraphics g,dev.abros.rivet.core.NativeLayout.Box track,int visible,int count,double position){
  if(count<=visible||visible==0||track.height()==0||track.width()==0)return;
  var geometry=new dev.abros.rivet.core.ScrollLayout(track,new dev.abros.rivet.core.NativeLayout.Box(track.x(),track.y(),0,track.height()),track);
  var thumb=geometry.thumb(visible,count,position);
  draw(g,track,thumb);
 }
 static void draw(GuiGraphics g,dev.abros.rivet.core.NativeLayout.Box track,dev.abros.rivet.core.NativeLayout.Box thumb){
  if(thumb.x()<track.x()||thumb.right()>track.right()||thumb.y()<track.y()||thumb.bottom()>track.bottom())throw new IllegalArgumentException("Scrollbar thumb escaped track");
  g.fill(track.x(),track.y(),track.right(),track.bottom(),UiPalette.scrollTrack());
  int inset=track.width()>3?1:0;g.fill(thumb.x()+inset,thumb.y(),thumb.right()-inset,thumb.bottom(),UiTheme.mix(UiKit.surface(),UiKit.muted(),0.65f));
 }
}
