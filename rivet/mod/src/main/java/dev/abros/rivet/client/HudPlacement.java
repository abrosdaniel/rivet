package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
/** Anchors survive resolution changes. Only the user chooses placement. */
final class HudPlacement {
 static NativeLayout.Box fit(int width,int height,int w,int h,int ax,int ay,int ox,int oy){w=Math.max(0,Math.min(w,width-8));h=Math.max(0,Math.min(h,height-8));int x=ax==0?ox:ax==2?width-w-ox:(width-w)/2+ox;int y=ay==0?oy:ay==2?height-h-oy:(height-h)/2+oy;return new NativeLayout.Box(Math.max(4,Math.min(width-w-4,x)),Math.max(4,Math.min(height-h-4,y)),w,h);}
 static void move(String item,int x,int y,int w,int h,int width,int height){var s=HudSettings.INSTANCE;int ax=x+w/2<width/3?0:x+w/2>width*2/3?2:1,ay=y+h/2<height/3?0:y+h/2>height*2/3?2:1;int ox=ax==2?width-x-w:ax==1?x-(width-w)/2:x,oy=ay==2?height-y-h:ay==1?y-(height-h)/2:y;if(Math.abs(ox)<12)ox=ax==1?0:8;if(Math.abs(oy)<12)oy=ay==1?0:8;switch(item){case "chat"->{s.hintsPositioned=true;s.hintAnchorX=ax;s.hintAnchorY=ay;s.hintOffsetX=ox;s.hintOffsetY=oy;}case "direction"->{s.directionAnchorX=ax;s.directionAnchorY=ay;s.directionOffsetX=ox;s.directionOffsetY=oy;}default->{s.anchorX=ax;s.anchorY=ay;s.offsetX=ox;s.offsetY=oy;}}}
 static void reset(){var s=HudSettings.INSTANCE;s.anchorX=2;s.anchorY=1;s.offsetX=8;s.offsetY=0;s.hintsPositioned=false;s.hintAnchorX=2;s.hintAnchorY=2;s.hintOffsetX=8;s.hintOffsetY=48;s.directionAnchorX=1;s.directionAnchorY=1;s.directionOffsetX=0;s.directionOffsetY=28;s.save();}
 private HudPlacement(){}
}
