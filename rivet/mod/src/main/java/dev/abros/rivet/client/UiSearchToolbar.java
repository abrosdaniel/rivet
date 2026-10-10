package dev.abros.rivet.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.*;
/** Shared search, ownership filter, chronological ordering and explicit removable filter summary. */
final class UiSearchToolbar {
 static int contentWidth(int width){return Math.max(1,width-dev.abros.rivet.core.ScrollLayout.TRACK_WIDTH-dev.abros.rivet.core.ScrollLayout.GAP);}
 static int height(int width,int screenHeight){return height(width,screenHeight,true);}
 static int height(int width,int screenHeight,boolean conditions){return screenHeight<300?24:conditions?78:52;}
 static UiEditBox build(Screen screen,Font font,int x,int y,int w,String query,int filter,String sort,boolean archived,Consumer<AbstractWidget> add,Consumer<String> change,Runnable search,IntConsumer select,Consumer<String> order,Runnable clear,boolean enabled){
  if(screen.height<300){
   var input=query(font,new dev.abros.rivet.core.NativeLayout.Box(x,y,w-30,20),Client.text("map.tool.search"),query,add,change,search,enabled);
   var choices=List.of(Client.text("ui.all_entries_46f54e27"),Client.text("ui.my_entries_56d47171"),Client.text("ui.joined_e0604805"),Client.text("ui.default_469631cb"),Client.text("ui.newest_first_6fb5cb39"),Client.text("ui.oldest_first_90392e19"),Client.text("ui.reset_conditions_a1c558ec"));
   var options=UiActions.button(Component.literal("⋯"),UiActions.Tone.NORMAL,"",b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,Client.text("ui.filters_and_entry_order_eb17ffc6"),choices,n->{if(n<3)select.accept(n);else if(n<6)order.accept(List.of("default","recent","oldest").get(n-3));else clear.run();},b))).bounds(x+w-24,y,24,20).tooltip(Tooltip.create(Client.tr("ui.filters_and_entry_order_25afc179"))).build();options.active=enabled;add.accept(options);return input;
  }
  boolean stacked=true;int fw=stacked?Math.max(60,(w-6)/2):Math.min(90,Math.max(60,w/4));var input=query(font,new dev.abros.rivet.core.NativeLayout.Box(x,y,stacked?w:w-fw-6,20),Client.text("map.tool.search"),query,add,change,search,enabled);
  var labels=List.of(Client.text("ui.all_fd08da7a"),Client.text("ui.mine_61edc217"),Client.text("ui.joined_e0604805"));var f=UiActions.button(Component.literal(labels.get(filter)+" ▾"),UiActions.Tone.NORMAL,"",b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,Client.text("map.tool.visibility"),labels,n->select.accept(n),b).current(filter))).bounds(x+w-fw,y+(stacked?26:0),fw,20).build();f.active=enabled;add.accept(f);
  var sorts=List.of(Client.text("ui.default_469631cb"),Client.text("ui.newest_first_6fb5cb39"),Client.text("ui.oldest_first_90392e19"));var keys=List.of("default","recent","oldest");int selected=Math.max(0,keys.indexOf(sort));int sw=stacked?w-fw-6:Math.min(134,w/2);
  var sorting=UiActions.button(Component.literal(sorts.get(selected)+" ▾"),UiActions.Tone.NORMAL,"",b->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,Client.text("ui.entry_order_e629fc16"),sorts,n->order.accept(keys.get(n)),b).current(selected))).bounds(x,y+26,sw,20).build();sorting.active=enabled;add.accept(sorting);
  if(!query.isBlank()||filter>0||archived||selected>0){String label=(query.isBlank()?"":query+" · ")+(filter>0?labels.get(filter)+" · ":"")+(archived?Client.text("ui.archive_b097ec00"):"")+Client.text("ui.reset_48d87152");var chip=UiActions.button(Component.literal(UiKit.fit(font,label,Math.max(12,w-sw-16))),UiActions.Tone.NORMAL,"",b->{var names=new java.util.ArrayList<String>();var resets=new java.util.ArrayList<Runnable>();if(!query.isBlank()){names.add(Client.text("ui.clear_search_b44a0b21")+query);resets.add(()->{change.accept("");search.run();});}if(filter>0){names.add(Client.text("ui.show_all_entries_5a073f1d"));resets.add(()->select.accept(0));}if(selected>0){names.add(Client.text("ui.reset_order_fcbe7cff"));resets.add(()->order.accept("default"));}names.add(Client.text("ui.reset_all_conditions_837a4b68"));resets.add(clear);net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(screen,Client.text("ui.active_conditions_2de23263"),names,n->resets.get(n).run(),b));}).bounds(stacked?x:x+sw+6,y+(stacked?52:26),stacked?w:Math.max(24,w-sw-6),20).tooltip(Tooltip.create(Component.literal(label))).build();chip.active=enabled;add.accept(chip);}
  return input;
 }
 static UiEditBox query(Font font,dev.abros.rivet.core.NativeLayout.Box area,String label,String value,Consumer<AbstractWidget> add,Consumer<String> change,Runnable submit,boolean enabled){
  int find=Math.min(48,area.width()/3);var input=UiFields.text(font,area.x(),area.y(),Math.max(1,area.width()-find-6),area.height(),label,label,100,value,change);input.active=enabled;add.accept(input);
  var go=UiActions.button(Client.tr("ui.find_ed6e1d03"),UiActions.Tone.NORMAL,UiIcons.SEARCH,b->submit.run()).bounds(area.right()-find,area.y(),find,area.height()).build();go.active=enabled;add.accept(go);return input;
 }

}
