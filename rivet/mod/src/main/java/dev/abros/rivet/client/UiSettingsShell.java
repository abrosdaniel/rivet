package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
/** One settings workspace; section changes retain the original parent rather than stacking dialogs. */
final class UiSettingsShell {
 static final List<String> SECTIONS=Client.labels(()->List.of(Client.text("ui.appearance_d206f1be"),Client.text("ui.widget_92a5d7dc"),Client.text("ui.notifications_ee3c35f3"),"TAB",Client.text("ui.chat_8c77e458"),Client.text("map.tool.navigate"),Client.text("ui.hud_editor_fe822f17"),Client.text("map.visibleMapShort"),Client.text("ui.general_fdd17d17")));
 private static final List<Integer> VISIBLE=List.of(8,0,1,2,3,4);
 static UiDialog build(int width,int height,int section,Screen parent,Screen owner,Consumer<AbstractWidget> add,Runnable reset,Runnable close){
  var outer=UiDialog.fit(width,height,570,350);var b=outer.body();int sidebar=Math.max(80,Math.min(128,b.width()/3));
  NativeLayout.Box body;
  if(b.height()<216||b.width()<430){
   UiSettingsSidebar.compact(owner);int searchWidth=64;
   add.accept(UiActions.button(Component.literal((section<0?Client.text("ui.section_99d406ee"):SECTIONS.get(section))+" ▾"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(owner,Client.text("ui.settings_section_7b9fdd7a"),generalChoices(),n->{if(n<VISIBLE.size())net.minecraft.client.Minecraft.getInstance().setScreen(open(parent,VISIBLE.get(n)));else if(n==VISIBLE.size())controls(owner);else net.minecraft.client.Minecraft.getInstance().setScreen(new HudInteractionScreen(true,owner));},v))).bounds(b.x(),b.y(),Math.max(1,b.width()-searchWidth-6),20).build());
   add.accept(UiActions.button(Client.tr("ui.search_a970ea76"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new SettingsSearchScreen(owner))).bounds(b.right()-searchWidth,b.y(),searchWidth,20).build());
   body=new NativeLayout.Box(b.x(),b.y()+28,b.width(),Math.max(0,b.height()-28));
  }else{
   add.accept(UiActions.button(Client.tr("ui.search_a970ea76"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new SettingsSearchScreen(owner))).bounds(b.x(),b.y(),sidebar-8,20).build());
   UiSettingsSidebar.build(owner,new NativeLayout.Box(b.x(),b.y()+26,sidebar-8,Math.max(0,b.height()-76)),VISIBLE.stream().map(SECTIONS::get).toList(),VISIBLE.indexOf(section),n->net.minecraft.client.Minecraft.getInstance().setScreen(open(parent,VISIBLE.get(n))),add);
   add.accept(UiActions.button(Client.tr("ui.controls_6344f50f"),UiActions.Tone.NORMAL,"",v->controls(owner)).bounds(b.x(),b.bottom()-44,sidebar-8,20).build());
   add.accept(UiActions.button(Client.tr("ui.hud_editor_e18f4872"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new HudInteractionScreen(true,owner))).bounds(b.x(),b.bottom()-20,sidebar-8,20).build());
   body=new NativeLayout.Box(b.x()+sidebar,b.y(),Math.max(0,b.width()-sidebar),b.height());
  }
  var footer=outer.footer();
  add.accept(UiActions.button(Component.literal(section<0?Client.text("ui.clear_search_a736131f"):Client.text("ui.reset_section_3a46364b")),UiActions.Tone.NORMAL,"",v->reset.run()).bounds(body.x(),footer.bottom()-20,Math.min(112,Math.max(0,body.width()-102)),20).build());
  UiActions.close(new NativeLayout.Box(footer.right()-96,footer.bottom()-20,96,20),add,close);
  return new UiDialog(outer.frame(),outer.header(),body,footer);
 }
 static UiDialog module(int width,int height,int section,Screen owner,Consumer<AbstractWidget> add,List<String> sections,java.util.function.IntConsumer select,Runnable search,Runnable reset,Runnable close){
  var outer=UiDialog.fit(width,height,570,350);var b=outer.body();int sidebar=Math.max(80,Math.min(128,b.width()/3));NativeLayout.Box body;
  if(b.height()<216||b.width()<430){
   UiSettingsSidebar.compact(owner);add.accept(UiActions.button(Component.literal((section<0?Client.text("ui.section_99d406ee"):sections.get(section))+" ▾"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(owner,Client.text("ui.settings_section_7b9fdd7a"),moduleChoices(sections),n->{if(n<sections.size())select.accept(n);else if(n==sections.size())net.minecraft.client.Minecraft.getInstance().setScreen(new HudInteractionScreen(true,owner));else controls(owner);},v))).bounds(b.x(),b.y(),Math.max(1,b.width()-70),20).build());
   add.accept(UiActions.button(Client.tr("ui.search_a970ea76"),UiActions.Tone.NORMAL,"",v->search.run()).bounds(b.right()-64,b.y(),64,20).build());body=new NativeLayout.Box(b.x(),b.y()+28,b.width(),Math.max(0,b.height()-28));
  }else{
   add.accept(UiActions.button(Client.tr("ui.search_a970ea76"),UiActions.Tone.NORMAL,"",v->search.run()).bounds(b.x(),b.y(),sidebar-8,20).build());
   UiSettingsSidebar.build(owner,new NativeLayout.Box(b.x(),b.y()+26,sidebar-8,Math.max(0,b.height()-76)),sections,section,select,add);
   add.accept(UiActions.button(Client.tr("ui.controls_6344f50f"),UiActions.Tone.NORMAL,"",v->controls(owner)).bounds(b.x(),b.bottom()-44,sidebar-8,20).build());
   add.accept(UiActions.button(Client.tr("ui.hud_editor_e18f4872"),UiActions.Tone.NORMAL,"",v->net.minecraft.client.Minecraft.getInstance().setScreen(new HudInteractionScreen(true,owner))).bounds(b.x(),b.bottom()-20,sidebar-8,20).build());body=new NativeLayout.Box(b.x()+sidebar,b.y(),Math.max(0,b.width()-sidebar),b.height());
  }
  var footer=outer.footer();add.accept(UiActions.button(Component.literal(section<0?Client.text("ui.clear_search_a736131f"):Client.text("ui.reset_section_3a46364b")),UiActions.Tone.NORMAL,"",v->reset.run()).bounds(body.x(),footer.bottom()-20,Math.min(112,Math.max(0,body.width()-102)),20).build());UiActions.close(new NativeLayout.Box(footer.right()-96,footer.bottom()-20,96,20),add,close);return new UiDialog(outer.frame(),outer.header(),body,footer);
 }
 private static List<String> moduleChoices(List<String> sections){var choices=new java.util.ArrayList<>(sections);choices.add(Client.text("ui.hud_editor_e18f4872"));choices.add(Client.text("ui.controls_6344f50f"));return choices;}
 private static void controls(Screen owner){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(owner,mc.options));}
 private static List<String> generalChoices(){var choices=new java.util.ArrayList<>(VISIBLE.stream().map(SECTIONS::get).toList());choices.add(Client.text("ui.controls_6344f50f"));choices.add(Client.text("ui.hud_editor_e18f4872"));return choices;}
 static boolean owns(Screen screen){return screen instanceof GeneralSettingsScreen||screen instanceof AccessibilityScreen||screen instanceof HudSettingsScreen||screen instanceof SocialSettingsScreen||screen instanceof SettingsSearchScreen;}
 static Screen open(Screen parent,int section){return switch(section){case 8->new GeneralSettingsScreen(parent);case 0->new AccessibilityScreen(parent);case 1,2->new HudSettingsScreen(parent,section==1?0:3);case 5->new NavigationSettingsScreen(parent);case 6->new HudInteractionScreen(true,parent);case 7->new MapSettingsScreen(parent);default->new SocialSettingsScreen(parent,section-3);};}
 static void confirm(Screen screen,Runnable reset){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new UiConfirmDialog(yes->{if(yes)reset.run();mc.setScreen(screen);},Client.tr("ui.reset_section_4f5266eb"),Client.tr("ui.only_the_current_section_will_be_d537cede")));}
 private UiSettingsShell(){}
}
