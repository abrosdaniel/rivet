package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import dev.abros.rivet.core.NativeLayout;
/** One adaptive application frame; pages consume named slots rather than screen coordinates. */
record UiWorkspace(NativeLayout.Box frame,NativeLayout.Box header,NativeLayout.Box sidebar,NativeLayout.Box page,NativeLayout.Box footer) {
 static final int OUTER_X=24,OUTER_Y=8;
 static UiWorkspace fit(int width,int height){
  boolean compact=width<560||height<280;
  int left=compact?OUTER_X+8:Math.min(146,Math.max(96,width/4))+12+OUTER_X;
  var frame=new NativeLayout.Box(OUTER_X,OUTER_Y,Math.max(0,width-2*OUTER_X),Math.max(0,height-2*OUTER_Y));
  var header=new NativeLayout.Box(OUTER_X+8,12,Math.max(0,width-2*(OUTER_X+8)),20);
  var sidebar=new NativeLayout.Box(OUTER_X+4,42,compact?0:Math.max(0,left-OUTER_X-20),Math.max(0,height-112));
  var page=new NativeLayout.Box(left,42,Math.max(0,width-left-OUTER_X-20),Math.max(0,height-84));
  var footer=new NativeLayout.Box(left,Math.max(42,height-32),Math.max(0,width-left-OUTER_X-20),20);
  return new UiWorkspace(frame,header,sidebar,page,footer);
 }
 void draw(GuiGraphics g){
  UiTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),UiKit.surface(UiKit.Surface.CANVAS));
  g.fill(header.x(),header.bottom()+3,header.right(),header.bottom()+4,UiKit.border());
  if(!compact())g.fill(sidebar.right()+5,sidebar.y(),sidebar.right()+6,sidebar.bottom(),UiKit.border());
 }
 boolean compact(){return sidebar.width()==0;}
 void heading(GuiGraphics g,Font font,Component title){UiBrand.draw(g,font,header);int titleWidth=Math.max(0,UiBrand.left(font,header)-header.x()-12);if(!compact()&&titleWidth>12)UiTypography.draw(g,font,title.getString(),header.x()+4,header.y()+5,titleWidth,UiTypography.Role.PAGE);}
}
