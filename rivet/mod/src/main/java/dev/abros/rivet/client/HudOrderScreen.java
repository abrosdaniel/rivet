package dev.abros.rivet.client;

import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.abros.rivet.core.NativeLayout;

/** Drag handles and drop targets use the same bounded row geometry as skin ordering. */
final class HudOrderScreen extends ScrollScreen {
 private final Screen parent;
 private UiDialog dialog;
 private String dragging="",notice="";
 private int target=-1;
 private double pointerY;
 private long nextScroll;
 HudOrderScreen(Screen parent){super(Client.tr("ui.widget_block_order_e8293abd"));this.parent=parent;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,360,300);scrollArea(HudSettings.INSTANCE.order.size(),dialog.body(),26);UiActions.close(new NativeLayout.Box(dialog.footer().right()-96,dialog.footer().bottom()-20,96,20),this::addRenderableWidget,this::onClose);}
 private int row(double x,double y){var b=scrollLayout().content();if(x<b.x()||x>=b.right()||y<b.y()||y>=b.y()+visibleRows*26)return -1;int index=firstRow+(int)((y-b.y())/26);return index<HudSettings.INSTANCE.order.size()?index:-1;}
 @Override public boolean mouseClicked(double x,double y,int button){int index=row(x,y);if(button==0&&index>=0){dragging=HudSettings.INSTANCE.order.get(index);target=index;pointerY=y;return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&!dragging.isEmpty()){pointerY=y;int index=row(x,y);if(index>=0)target=index;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public void tick(){if(dragging.isEmpty()||net.minecraft.Util.getMillis()<nextScroll)return;var b=scrollLayout().content();int delta=pointerY<b.y()+8?-1:pointerY>b.bottom()-8?1:0;if(delta!=0){int next=Math.max(0,Math.min(HudSettings.INSTANCE.order.size()-visibleRows,firstRow+delta));if(next!=firstRow){restoreScroll(next);rebuildWidgets();target=delta<0?next:Math.min(HudSettings.INSTANCE.order.size()-1,next+visibleRows-1);}nextScroll=net.minecraft.Util.getMillis()+180;}}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){int index=row(x,y);if(index>=0){var s=HudSettings.INSTANCE;var previous=new ArrayList<>(s.order);s.order.remove(dragging);s.order.add(index,dragging);if(!previous.equals(s.order)){s.save();if(!s.error.isEmpty()){s.order.clear();s.order.addAll(previous);notice=Client.text("ui.could_not_save_hud_order_5423d1a5");}else{notice="";RivetHud.refresh();}}}dragging="";target=-1;return true;}return super.mouseReleased(x,y,button);}
 @Override boolean cancelInteraction(){if(!dragging.isEmpty()){dragging="";target=-1;return true;}return super.cancelInteraction();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());var b=scrollLayout().content();var order=HudSettings.INSTANCE.order;for(int n=firstRow;n<Math.min(order.size(),firstRow+visibleRows);n++){int yy=b.y()+(n-firstRow)*26;String key=order.get(n);boolean active=key.equals(dragging);UiKit.surface(g,b.x(),yy,b.width(),23,active?UiTheme.mix(UiKit.surface(),UiKit.accent(),.15f):UiKit.surface());UiDragHandle.draw(g,b.right()-UiDragHandle.WIDTH,yy,active);Ui.text(g,font,UiKit.fit(font,HudSettings.label(key),b.width()-42),b.x()+10,yy+7,UiKit.text(),false);}if(!dragging.isEmpty()&&target>=firstRow&&target<firstRow+visibleRows){int yy=b.y()+(target-firstRow)*26;g.fill(b.x(),yy,b.right(),yy+2,UiKit.accent());}if(notice.isEmpty())Ui.text(g,font,UiKit.fit(font,Client.text("ui.drag_the_row_into_position_390772f9"),dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.muted(),false);else UiFeedback.draw(g,font,notice,dialog.footer().x(),dialog.footer().y(),dialog.footer().width(),UiFeedback.Kind.ERROR);});}
 @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){dragging="";target=-1;super.resize(mc,width,height);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
