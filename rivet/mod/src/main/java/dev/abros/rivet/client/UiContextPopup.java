package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** An anchored menu stays above its parent without dimming or moving that parent. */
final class UiContextPopup extends ScrollScreen {
    record Item(Component label,Runnable run,boolean danger) {}
    private final Screen parent;private final List<Item> items;private final int anchorX,anchorY;
    private NativeLayout.Box panel;
    private java.util.function.BooleanSupplier valid=()->true;
    UiContextPopup validWhile(java.util.function.BooleanSupplier condition){valid=condition;return this;}
    @Override public void tick(){parent.tick();if(!valid.getAsBoolean())onClose();}
    UiContextPopup(Screen parent,int x,int y,List<Item> items){super(Component.empty());this.parent=parent;anchorX=x;anchorY=y;this.items=List.copyOf(items);}
    @Override protected void init(){
        int w=Math.min(width-16,Math.max(176,items.stream().mapToInt(i->font.width(i.label())+40).max().orElse(176)));
        int h=Math.min(height-16,items.size()*24+8);
        panel=new NativeLayout.Box(Math.clamp(anchorX,8,Math.max(8,width-w-8)),Math.clamp(anchorY,8,Math.max(8,height-h-8)),w,h);
        scrollArea(items.size(),new NativeLayout.Box(panel.x()+4,panel.y()+4,w-8,h-8),24);
        for(int n=firstRow;n<Math.min(items.size(),firstRow+visibleRows);n++){var item=items.get(n);addRenderableWidget(new UiMenuItem(item.label().getString(),panel.x()+4,panel.y()+4+(n-firstRow)*24,w-12,false,item.danger(),()->{minecraft.setScreen(parent);item.run().run();}));}
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){UiKit.material(g,panel.x(),panel.y(),panel.width(),panel.height());g.renderOutline(panel.x(),panel.y(),panel.width(),panel.height(),UiKit.border());}
    @Override public void render(GuiGraphics g,int x,int y,float delta){ModalLayer.render(parent,this,g,delta,()->super.render(g,x,y,delta));}
    @Override public boolean mouseClicked(double x,double y,int button){if(x<panel.x()||x>=panel.right()||y<panel.y()||y>=panel.bottom()){onClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
