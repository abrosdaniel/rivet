package dev.abros.rivet.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
/** Shared graphite surfaces, brass focus and restrained motion for Rivet only. */
public final class UiTheme {
 private UiTheme(){} private static final class HoverState {float value;long at;HoverState(float value,long at){this.value=value;this.at=at;}}
 private static final Map<AbstractWidget,HoverState> motion=new WeakHashMap<>();
 private static final ThreadLocal<Screen> RENDER_SCREEN=new ThreadLocal<>();
 static void rendering(Screen screen,Runnable render){Screen previous=RENDER_SCREEN.get();RENDER_SCREEN.set(screen);try{render.run();}finally{if(previous==null)RENDER_SCREEN.remove();else RENDER_SCREEN.set(previous);}}
 public static boolean stylesButtons(Screen s,net.minecraft.client.gui.components.AbstractButton widget){if(widget instanceof CoreVersionButton)return false;if(RENDER_SCREEN.get()!=null)s=RENDER_SCREEN.get();return owns(s);}
 public static boolean owns(Screen s){return s!=null&&s.getClass().getPackageName().equals("dev.abros.rivet.client");}
 static float hover(AbstractWidget w){float target=w.isHoveredOrFocused()?1:0;if(!AccessibilityScreen.animations())return target;long now=System.nanoTime();var state=motion.get(w);if(state==null){motion.put(w,new HoverState(target,now));return target;}float step=Math.min(1,(now-state.at)/(AccessibilityScreen.motionMillis()*1000000f));state.at=now;state.value+=Math.copySign(Math.min(Math.abs(target-state.value),step),target-state.value);return state.value;}

 static int mix(int a,int b,float t){int out=0;for(int shift=0;shift<=24;shift+=8)out|=((int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return out;}
 static void panel(GuiGraphics g,int x,int y,int w,int h,int color){UiKit.surface(g,x,y,w,h,AccessibilityScreen.background(color));}

 public static void shell(Screen s,GuiGraphics g){if(s instanceof CommunityScreen||s instanceof TaskScreen||s instanceof ReportQueueScreen||s instanceof ServerMenuScreen||s instanceof FeatureListScreen f&&f.kind.equals("players"))UiWorkspace.fit(s.width,s.height).draw(g);}

 public static void button(AbstractButton b,GuiGraphics g){
  var font=Minecraft.getInstance().font;int x=b.getX(),y=b.getY(),w=b.getWidth(),h=b.getHeight();String text=b.getMessage().getString();var style=UiActions.style(b);
  int accent=style.tone()==UiActions.Tone.DANGER?UiPalette.color(0xFFDB7777):UiKit.accent();
  float hover=b.active?hover(b):0;
  int base=UiKit.surface(UiKit.Surface.CONTROL);
  if(style.tone()==UiActions.Tone.PRIMARY)base=accent;else if(style.tone()==UiActions.Tone.DANGER)base=mix(UiKit.surface(),accent,0.12f);
  UiKit.surface(g,x,y,w,h,b.active?mix(base,style.tone()==UiActions.Tone.PRIMARY?mix(accent,UiKit.onAccent(),.10f):UiKit.surface(UiKit.Surface.HOVER),hover):UiKit.surface());
  if(b.isFocused())UiKit.focus(g,x,y,w,h);
  if(text.equals("×")||text.equals("+")||text.equals("↑")||text.equals("↓")){UiIcons.draw(g,text.equals("×")?UiIcons.CLEAR:text.equals("↑")?UiIcons.UP:text.equals("↓")?UiIcons.DOWN:UiIcons.PLUS,x+(w-12)/2,y+(h-12)/2,b.active?UiKit.text():UiKit.muted());return;}
  boolean dropdown=text.endsWith(" ▾");if(dropdown)text=text.substring(0,text.length()-2);if(text.startsWith("+ "))text=text.substring(2);
  int foreground=b.active?(style.tone()==UiActions.Tone.PRIMARY?UiKit.onAccent():UiKit.text()):UiKit.muted(),ty=y+(h-8)/2;
  // A settings selector is a label/value row; its original message and native hit box stay intact.
  int split=text.indexOf(": ");
  if(dropdown&&split>0&&w>=180){
   String label=text.substring(0,split),value=text.substring(split+2);int valueWidth=Math.min(font.width(value),Math.max(24,w/2-24));
   Ui.text(g,font,UiKit.fit(font,label,w-valueWidth-40),x+UiKit.INSET,ty,foreground,false);
   String shown=UiKit.fit(font,value,valueWidth);Ui.text(g,font,shown,x+w-24-font.width(shown),ty,b.active?UiKit.accent():UiKit.muted(),false);
  }else{
   String icon=dropdown?"":style.icon();int trailing=dropdown?18:0;if(font.width(text)+30+trailing>w)icon="";int reserve=icon.isEmpty()?0:18;
   String shown=UiKit.fit(font,text,w-16-reserve-trailing);int tx=dropdown?x+UiKit.INSET:x+(w-font.width(shown)-reserve)/2;
   if(!icon.isEmpty())UiIcons.draw(g,icon,tx,y+(h-12)/2,b.active?(style.tone()==UiActions.Tone.PRIMARY?foreground:accent):UiKit.muted());
   if(style.tone()==UiActions.Tone.PRIMARY&&b.active)g.drawString(font,shown,tx+reserve,ty,foreground,false);else Ui.text(g,font,shown,tx+reserve,ty,foreground,false);
  }
  if(dropdown)UiIcons.draw(g,UiIcons.DOWN,x+w-16,y+(h-12)/2,b.active?UiKit.muted():UiPalette.color(0xFF687580));
 }
 public static boolean keyboard(Screen screen,int key){
  if(!owns(screen)||screen.getFocused() instanceof EditBox||screen.getFocused() instanceof MultiLineEditBox||key!=264&&key!=265)return false;
  var cards=screen.children().stream().filter(e->e instanceof AbstractWidget w&&w.active&&w.visible&&(e instanceof CommunityCard||e instanceof PlayerRow||e instanceof UiSummaryCard)).map(e->(AbstractWidget)e).toList();
  if(cards.isEmpty()||screen.getFocused()!=null&&!cards.contains(screen.getFocused()))return false;
  if(screen.getFocused()==null){screen.setFocused(cards.getFirst());return true;}
  var current=(AbstractWidget)screen.getFocused();AbstractWidget next=current;long distance=Long.MAX_VALUE;
  for(var card:cards){int dy=card.getY()-current.getY();if(key==264?dy<=0:dy>=0)continue;long score=Math.abs(dy)*1000L+Math.abs(card.getX()-current.getX());if(score<distance){distance=score;next=card;}}
  screen.setFocused(next);return true;
 }
}
