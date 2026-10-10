package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
/** Client preferences that are independent of a particular HUD element or appearance. */
final class GeneralSettingsScreen extends ScrollScreen implements SettingsTarget {
 private final Screen parent;private UiDialog dialog;private String error="";
 GeneralSettingsScreen(Screen parent){super(Client.tr("server.settings"));this.parent=parent;}
 Screen parentScreen(){return parent;}
 private void change(Runnable action){try{action.run();error="";}catch(Exception ex){error=Client.text("ui.could_not_save_settings_8e531638");}rebuildWidgets();}
 @Override protected void init(){
  dialog=UiSettingsShell.build(width,height,8,parent,this,this::addRenderableWidget,()->UiSettingsShell.confirm(this,this::reset),this::onClose);
  var b=dialog.body();var area=scrollForm(3,b,28);
  for(int n=firstRow;n<Math.min(3,firstRow+visibleRows);n++){int y=b.y()+(n-firstRow)*28,w=area.content().width();switch(n){
   case 0->addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.time_zone_a826de28")+(AccessibilityScreen.serverTime()?Client.text("ui.server_08882375"):Client.text("ui.local_5a769340"))+" ▾"),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.time_zone_47947a0c"),List.of(Client.text("ui.local_time_93ffcb62"),Client.text("ui.server_time_19ffb0e5")),i->change(()->AccessibilityScreen.serverTime(i==1)),button).current(AccessibilityScreen.serverTime()?1:0))).bounds(b.x(),y,w,24).build());
   case 1->addRenderableWidget(new UiToggle(Client.text("ui.linked_storage_holograms_9ddfa7a1"),b.x(),y,w,HudSettings.INSTANCE.holograms,()->change(()->{var s=HudSettings.INSTANCE;s.holograms=!s.holograms;s.save();if(!s.error.isEmpty())throw new IllegalStateException(s.error);})));
   case 2->addRenderableWidget(UiActions.button(Client.tr("ui.home_widgets_fd71daeb"),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new HomeWidgetsScreen(this))).bounds(b.x(),y,w,24).build());
  }}
 }
 private void reset(){change(()->{AccessibilityScreen.serverTime(false);HudSettings.INSTANCE.holograms=true;HudSettings.INSTANCE.save();if(!HudSettings.INSTANCE.error.isEmpty())throw new IllegalStateException(HudSettings.INSTANCE.error);});}
 public void revealSetting(String id){revealRow(SettingsCatalog.row(8,id));rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(!error.isEmpty())Ui.status(g,font,error,dialog.body().x(),dialog.footer().y(),dialog.body().width(),dialog.footer().y()+20);});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
