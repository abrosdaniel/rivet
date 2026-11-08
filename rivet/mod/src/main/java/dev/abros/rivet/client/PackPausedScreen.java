package dev.abros.rivet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/** A paused transfer can resume only after the old worker has released its files. */
final class PackPausedScreen extends Screen {
 private final Screen parent;private final java.util.function.BooleanSupplier idle;private final Runnable resume;
 private UiDialog layout;private Button continueButton;
 PackPausedScreen(Screen parent,java.util.function.BooleanSupplier idle,Runnable resume){super(Client.tr("builder.paused"));this.parent=parent;this.idle=idle;this.resume=resume;}
 @Override protected void init(){
  layout=UiDialog.fit(width,height,380,180);var footer=layout.footer();int w=(footer.width()-8)/2;
  continueButton=addRenderableWidget(UiActions.button(Client.tr("builder.resume"),UiActions.Tone.PRIMARY,"",b->{if(idle.getAsBoolean())resume.run();}).bounds(footer.x(),footer.bottom()-20,w,20).build());
  addRenderableWidget(UiActions.button(Client.tr("close"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(footer.x()+w+8,footer.bottom()-20,w,20).build());tick();
 }
 @Override public void tick(){if(continueButton!=null)continueButton.active=idle.getAsBoolean();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){var f=layout.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var h=layout.header();UiHeading.dialog(g,font,title,h.x(),layout.frame().y(),h.width());var b=layout.body();int at=b.y();for(var line:font.split(Client.tr("builder.paused.details"),b.width())){g.drawString(font,line,b.x(),at,UiKit.muted(),false);at+=font.lineHeight+2;}});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
