package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
/** One settings workspace; section changes retain the original parent rather than stacking dialogs. */
final class UiSettingsShell {
 static final List<String> SECTIONS=List.of("Оформление","Виджет","Уведомления","TAB","Чат","Навигация","Редактор HUD");
 static UiDialog build(int width,int height,int section,Screen parent,Screen owner,Consumer<AbstractWidget> add,Runnable reset,Runnable close){
  var outer=UiDialog.fit(width,height,570,350);var b=outer.body();int sidebar=Math.max(80,Math.min(112,b.width()/3));
  add.accept(UiActions.button(Component.literal("Поиск…"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new SettingsSearchScreen(parent))).bounds(b.x(),b.y(),sidebar-8,20).build());
  int stride=Math.min(26,Math.max(4,(b.height()-52)/(SECTIONS.size()-1)));for(int n=0;n<SECTIONS.size()-1;n++){int index=n;var button=UiActions.button(Component.literal(SECTIONS.get(n)),section==n?UiActions.Tone.PRIMARY:UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(open(parent,index))).bounds(b.x(),b.y()+26+n*stride,sidebar-8,stride-2).build();add.accept(button);}
  add.accept(UiActions.button(Component.literal("Редактор HUD…"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new HudInteractionScreen(true,owner))).bounds(b.x(),b.bottom()-20,sidebar-8,20).build());
  var body=new NativeLayout.Box(b.x()+sidebar,b.y(),Math.max(0,b.width()-sidebar),b.height());var footer=outer.footer();
  add.accept(UiActions.button(Component.literal("Сбросить раздел"),UiActions.Tone.NORMAL,"",v->reset.run()).bounds(body.x(),footer.bottom()-20,Math.min(112,Math.max(0,body.width()-102)),20).build());
  UiActions.close(new NativeLayout.Box(footer.right()-96,footer.bottom()-20,96,20),add,close);
  return new UiDialog(outer.frame(),outer.header(),body,footer);
 }
 static boolean owns(Screen screen){return screen instanceof AccessibilityScreen||screen instanceof HudSettingsScreen||screen instanceof SocialSettingsScreen||screen instanceof NavigationSettingsScreen||screen instanceof SettingsSearchScreen;}
 static Screen open(Screen parent,int section){return switch(section){case 0->new AccessibilityScreen(parent);case 1,2->new HudSettingsScreen(parent,section==1?0:3);case 5->new NavigationSettingsScreen(parent);case 6->new HudInteractionScreen(true,parent);default->new SocialSettingsScreen(parent,section-3);};}
 static void confirm(Screen screen,Runnable reset){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new UiConfirmDialog(yes->{if(yes)reset.run();mc.setScreen(screen);},Component.literal("Сбросить раздел?"),Component.literal("Будут восстановлены стандартные значения только текущего раздела.")));}
 private UiSettingsShell(){}
}
