package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/** Uses gameplay renderers at a simulated viewport, with isolated sample notifications. */
final class HudPreviewScreen extends Screen implements UiParentScreen {
 private static final int[][] SIZES={{1280,720},{1920,1080},{2560,1440},{3440,1440}};
 private final Screen parent;private UiDialog dialog;private int resolution=1,scale=2;
 HudPreviewScreen(Screen parent){this(parent,1,2);}
 HudPreviewScreen(Screen parent,int resolution,int scale){super(Client.tr("hud.preview.title"));if(resolution<0||resolution>=SIZES.length||scale<1||scale>3)throw new IllegalArgumentException("Invalid preview");this.parent=parent;this.resolution=resolution;this.scale=scale;}
 public Screen parentScreen(){return parent;}
 @Override protected void init(){
  dialog=UiDialog.fit(width,height,640,420);var b=dialog.body();int half=(b.width()-8)/2;
  var labels=java.util.Arrays.stream(SIZES).map(v->v[0]+" × "+v[1]).toList();
  addRenderableWidget(UiActions.button(net.minecraft.network.chat.Component.literal(labels.get(resolution)),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,Client.text("hud.preview.resolution"),labels,n->{resolution=n;rebuildWidgets();},button).current(resolution))).bounds(b.x(),b.y(),half,20).build());
  addRenderableWidget(UiActions.button(net.minecraft.network.chat.Component.literal("GUI "+scale),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,"GUI",List.of("1","2","3"),n->{scale=n+1;rebuildWidgets();},button).current(scale-1))).bounds(b.x()+half+8,b.y(),half,20).build());
  var f=dialog.footer();UiActions.close(new NativeLayout.Box(f.right()-96,f.bottom()-20,96,20),this::addRenderableWidget,this::onClose);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{
  super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());var b=dialog.body();
  int vw=(int)Math.ceil(SIZES[resolution][0]/(double)scale),vh=(int)Math.ceil(SIZES[resolution][1]/(double)scale);
  float fit=Math.min(b.width()/(float)vw,Math.max(1,b.height()-52)/(float)vh);int pw=Math.round(vw*fit),ph=Math.round(vh*fit),left=b.x()+(b.width()-pw)/2,top=b.y()+28;
  UiKit.plate(g,left,top,pw,ph,UiKit.surface(UiKit.Surface.INPUT));g.enableScissor(left,top,left+pw,top+ph);g.pose().pushPose();
  try{g.pose().translate(left,top,0);g.pose().scale(fit,fit,1);HudViewport.draw(vw,vh,scale,left,top,fit,()->{Minimap.draw(g,true);HudRenderer.render(g,true,true);ChatHints.draw(g,"",true);DirectionCue.draw(g,true);});}
  finally{g.pose().popPose();g.disableScissor();}
  Ui.text(g,font,UiKit.fit(font,Client.text("hud.preview.hint"),b.width()),b.x(),b.bottom()-12,UiKit.muted(),false);
 });}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
