package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Optional read-only API inspection. Unknown fields remain unknown, never guessed from TCP. */
final class VoiceDiagnosticsScreen extends ScrollScreen {
 private final Screen parent;private List<String> lines=List.of();private long checked;
 VoiceDiagnosticsScreen(Screen parent){super(Component.literal("Голосовой чат"));this.parent=parent;}
 private int panelTop(){return UiDialog.top(height,240);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(320,width-24),panelTop(),panelBottom());}
 @Override protected void init(){scrollArea(lines.size(),new dev.abros.rivet.core.NativeLayout.Box(width/2-150,panelTop()+36,Math.max(0,300),Math.max(0,(panelBottom()-40)-(panelTop()+36))),24);UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(width/2-100,panelBottom()-28,200,20),this::addRenderableWidget,this::onClose);}
 @Override public void tick(){if(System.currentTimeMillis()-checked<1000)return;checked=System.currentTimeMillis();var next=ClientCompatibilityRegistry.voice();if(!next.equals(lines)){lines=next;rebuildWidgets();}}
 @Override public void render(GuiGraphics g,int x,int y,float delta){UiDialog.render(parent,this,g,delta,()->{super.render(g,x,y,delta);UiHeading.dialog(g,font,title,(width-Math.min(320,width-24))/2,panelTop(),Math.min(320,width-24));int left=Math.max(12,width/2-150);for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)Ui.text(g,font,font.plainSubstrByWidth(lines.get(i),Math.min(300,width-24)),left,panelTop()+36+(i-firstRow)*24,UiPalette.color(0xEEEEEE));});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
