package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
/** Independent section viewport; pinned actions never share its scrolling area. */
final class UiSettingsSidebar extends AbstractWidget {
 private static final Map<Screen,State> states=new WeakHashMap<>();
 private static final class State {double position;int selected=-2;java.lang.ref.WeakReference<UiSettingsSidebar> widget;boolean dragging;double grab;}
 private final Screen owner;private final NativeLayout.Box viewport;private final State state;private final ScrollLayout layout;private final int count,shown;
 private UiSettingsSidebar(Screen owner,NativeLayout.Box area,int count,int selected){super(area.x(),area.y(),area.width(),area.height(),Client.tr("ui.settings_sections_9a4919e1"));this.owner=owner;this.viewport=area;this.count=count;shown=Math.max(1,area.height()/24);layout=ScrollLayout.fit(area,count>shown);setX(layout.track().x());setY(layout.track().y());setWidth(layout.track().width());setHeight(layout.track().height());state=states.computeIfAbsent(owner,s->new State());state.widget=new java.lang.ref.WeakReference<>(this);state.position=Math.clamp(state.position,0,Math.max(0,count-shown));if(state.selected!=selected){if(selected>=0&&(selected<state.position||selected>=state.position+shown))state.position=Math.clamp(selected-shown+1,0,Math.max(0,count-shown));state.selected=selected;}}
 static void build(Screen owner,NativeLayout.Box area,List<String> labels,int selected,IntConsumer select,Consumer<AbstractWidget> add){var list=new UiSettingsSidebar(owner,area,labels.size(),selected);int start=(int)list.state.position;for(int n=start;n<Math.min(labels.size(),start+list.shown);n++){int index=n;add.accept(new SidebarButton(area.x(),area.y()+(n-start)*24,list.layout.content().width(),labels.get(n),selected==n,()->select.accept(index)));}if(labels.size()>list.shown)add.accept(list);}
 static void detach(Screen owner){var s=states.get(owner);if(s!=null)s.widget=null;}
 static boolean dragFor(Screen owner,double y){var s=states.get(owner);var w=s==null||s.widget==null?null:s.widget.get();if(w==null||!s.dragging)return false;w.seek(y);return true;}
 static boolean releaseFor(Screen owner){var s=states.get(owner);if(s==null||!s.dragging)return false;s.dragging=false;return true;}
 static void compact(Screen owner){states.remove(owner);}
 static boolean scrollFor(Screen owner,double x,double y,double dy){var state=states.get(owner);var widget=state==null||state.widget==null?null:state.widget.get();if(widget==null||(x<widget.viewport.x()||x>=widget.viewport.right()||y<widget.viewport.y()||y>=widget.viewport.bottom())||dy==0)return false;widget.move(state.position-dy);return true;}
 private void move(double value){double next=Math.clamp(value,0,Math.max(0,count-shown));int before=(int)state.position;state.position=next;if(before!=(int)next)owner.resize(Minecraft.getInstance(),owner.width,owner.height);}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float d){UiScrollbar.draw(g,layout.track(),shown,count,state.position);}
 @Override public boolean mouseClicked(double x,double y,int button){var t=layout.track();if(button!=0||x<t.x()||x>=t.right()||y<t.y()||y>=t.bottom())return false;var thumb=layout.thumb(shown,count,state.position);state.grab=y>=thumb.y()&&y<thumb.bottom()?y-thumb.y():thumb.height()/2d;state.dragging=true;seek(y);return true;}
 private void seek(double y){var t=layout.track();move((y-t.y()-state.grab)/Math.max(1,t.height()-layout.thumb(shown,count,state.position).height())*(count-shown));}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(!state.dragging||button!=0)return false;seek(y);return true;}
 @Override public boolean mouseReleased(double x,double y,int button){state.dragging=false;return super.mouseReleased(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){switch(key){case 264,267->move(state.position+(key==267?shown:1));case 265,266->move(state.position-(key==266?shown:1));case 268->move(0);case 269->move(count);default->{return false;}}return true;}
 @Override protected void updateWidgetNarration(NarrationElementOutput out){out.add(NarratedElementType.TITLE,getMessage());}
}
