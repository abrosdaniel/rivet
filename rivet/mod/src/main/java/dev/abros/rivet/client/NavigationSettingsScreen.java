package dev.abros.rivet.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Navigation uses the same bounded settings workspace and controls as the HUD. */
final class NavigationSettingsScreen extends ScrollScreen {
 private long adapterRevision=-1;
 private final Screen parent;private UiDialog dialog;
 NavigationSettingsScreen(Screen parent){super(Component.literal("Настройки Rivet"));this.parent=parent;}
 private void change(Runnable action){action.run();HudSettings.INSTANCE.save();rebuildWidgets();}
 @Override protected void init(){
  dialog=UiSettingsShell.build(width,height,5,parent,this,this::addRenderableWidget,()->UiSettingsShell.confirm(this,this::reset),this::onClose);
  var body=dialog.body();var s=HudSettings.INSTANCE;var geometry=scrollArea(4,body,32);
  for(int row=firstRow;row<Math.min(4,firstRow+visibleRows);row++){
   int y=body.y()+(row-firstRow)*32,w=geometry.content().width();
   if(row==0)addRenderableWidget(new UiToggle("Указатель точки",body.x(),y,w,s.directionEnabled,()->change(()->s.directionEnabled=!s.directionEnabled)));
   else if(row==1)addRenderableWidget(UiActions.button(Component.literal("Прозрачность фона: "+Math.round((1-s.directionOpacity)*100)+"%"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Прозрачность указателя",List.of("0%","15% (по умолчанию)","35%","50%","75%","100%"),n->{s.directionOpacity=new float[]{1,.85f,.65f,.5f,.25f,0}[n];s.save();minecraft.setScreen(this);},b))).bounds(body.x(),y,w,24).build());
   else if(row==2)addRenderableWidget(new UiToggle("Координаты в указателе",body.x(),y,w,s.directionCoordinates,()->change(()->s.directionCoordinates=!s.directionCoordinates)));
   else if(row==3){var toggle=addRenderableWidget(new UiToggle("Метка маршрута в Xaero’s",body.x(),y,w,s.directionXaero,()->change(()->s.directionXaero=!s.directionXaero)));toggle.active=ClientCompatibilityRegistry.xaeroAvailable();if(!toggle.active)toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Слой Xaero недоступен. Указатель Rivet работает самостоятельно.")));}
  }
 }
 @Override public void tick(){long revision=ClientCompatibilityRegistry.revision();if(adapterRevision!=revision){adapterRevision=revision;rebuildWidgets();}}
 private void reset(){var s=HudSettings.INSTANCE;s.directionEnabled=true;s.directionCoordinates=true;s.directionXaero=true;s.directionOpacity=.85f;s.save();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
