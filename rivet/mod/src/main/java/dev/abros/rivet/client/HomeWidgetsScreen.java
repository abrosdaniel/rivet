package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class HomeWidgetsScreen extends ScrollScreen {
 private final Screen parent;private String notice="",dragging="";private int target=-1;private double pointerY;private long nextScroll;
 HomeWidgetsScreen(Screen parent){super(Component.literal("Главная: виджеты"));this.parent=parent;}
 private int w(){return Math.min(380,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,250);}private int end(){return height-top();}
 private void change(String key){try{HomeLayout.toggle(key);notice="";}catch(Exception failure){notice="Не удалось сохранить настройки";}rebuildWidgets();}
 @Override protected void init(){var keys=HomeLayout.order();scrollArea(keys.size(),new dev.abros.rivet.core.NativeLayout.Box(x()+12,top()+48,w()-24,Math.max(0,end()-top()-96)),32);for(int i=firstRow;i<Math.min(keys.size(),firstRow+visibleRows);i++){String key=keys.get(i);int y=top()+48+(i-firstRow)*32;addRenderableWidget(new UiToggle(HomeLayout.name(key),x()+12,y,scrollLayout().content().width()-UiDragHandle.WIDTH-6,HomeLayout.visible(key),()->change(key)));}addRenderableWidget(UiActions.button(Component.literal("По умолчанию"),UiActions.Tone.NORMAL,"",b->{try{HomeLayout.reset();notice="";}catch(Exception failure){notice="Не удалось сохранить";}rebuildWidgets();}).bounds(x()+12,end()-28,120,20).build());addRenderableWidget(UiActions.button(Component.literal("Готово"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x()+w()-92,end()-28,80,20).build());}
 private int row(double x,double y){var b=scrollLayout().content();int n=firstRow+(int)((y-b.y())/32);return b.contains(x,y)&&y<b.y()+visibleRows*32&&n<HomeLayout.order().size()?n:-1;}
 @Override public boolean mouseClicked(double x,double y,int button){int n=row(x,y);if(button==0&&n>=0&&x>=scrollLayout().content().right()-UiDragHandle.WIDTH){dragging=HomeLayout.order().get(n);target=n;pointerY=y;return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&!dragging.isEmpty()){pointerY=y;int n=row(x,y);if(n>=0)target=n;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){int n=row(x,y);try{if(n>=0)HomeLayout.moveTo(dragging,n);notice="";}catch(Exception failure){notice="Не удалось сохранить настройки";}dragging="";target=-1;rebuildWidgets();return true;}return super.mouseReleased(x,y,button);}
 @Override boolean cancelInteraction(){if(!dragging.isEmpty()){dragging="";target=-1;return true;}return super.cancelInteraction();}
 @Override public void tick(){if(dragging.isEmpty()||net.minecraft.Util.getMillis()<nextScroll)return;var b=scrollLayout().content();int delta=pointerY<b.y()+8?-1:pointerY>b.bottom()-8?1:0;if(delta!=0){restoreScroll(Math.max(0,Math.min(HomeLayout.order().size()-visibleRows,firstRow+delta)));rebuildWidgets();target=delta<0?firstRow:Math.min(HomeLayout.order().size()-1,firstRow+visibleRows-1);}nextScroll=net.minecraft.Util.getMillis()+180;}
 @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){dragging="";target=-1;super.resize(mc,width,height);}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),end());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x()+12,top(),w()-24);var b=scrollLayout().content();var keys=HomeLayout.order();for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++)UiDragHandle.draw(g,b.right()-UiDragHandle.WIDTH,b.y()+(n-firstRow)*32,keys.get(n).equals(dragging));if(!dragging.isEmpty()&&target>=firstRow&&target<firstRow+visibleRows){int yy=b.y()+(target-firstRow)*32;g.fill(b.x(),yy,b.right(),yy+2,UiKit.accent());}if(height>=280||!notice.isEmpty())Ui.status(g,font,notice.isEmpty()?"Перетащите за маркер справа. Срочные записи остаются выше.":notice,x()+12,Math.max(top()+180,end()-68),w()-24,end()-34);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
