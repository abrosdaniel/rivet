package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Local editor: validation and discard warning precede the parent's receipt-protected mutation. */
final class UiRecordForm extends ScrollScreen {
 private final Screen parent;private final List<CommunityScreen.Field> fields;private final JsonObject original,values;private final Consumer<JsonObject> submit;private String error="",invalidField="";private boolean validationDirty;private final Map<String,Integer> ys=new HashMap<>();
 UiRecordForm(Screen parent,String title,List<CommunityScreen.Field> fields,JsonObject preset,Consumer<JsonObject> submit){super(Component.literal(title));this.parent=parent;this.fields=fields;this.values=preset.deepCopy();for(var f:fields){var value=values.get(f.key());values.addProperty(f.key(),value!=null&&value.isJsonPrimitive()?value.getAsString():"");}this.original=values.deepCopy();this.submit=submit;}
 private java.util.List<UiFormGrid.Cell> grid(){return UiFormGrid.layout(fields.stream().map(f->new UiFormGrid.Field(f,true,Set.of("description","text").contains(f.key()),"",invalidField.equals(f.key())&&height-98>=88)).toList(),Math.max(0,Math.min(440,width-40)-10));}
 private UiDialog dialog(){return UiDialog.fit(width,height,440,28+UiFormGrid.units(grid())*22+54);}
 private int w(){return dialog().body().width();}private int x(){return dialog().body().x();}private int top(){return dialog().frame().y();}private int bottom(){return dialog().frame().bottom();}
 private void editValue(String key,String value){values.addProperty(key,value);if(invalidField.equals(key)){invalidField="";error="";validationDirty=true;}}
 private void save(){invalidField="";error="";var missing=fields.stream().filter(f->!Set.of("description","text").contains(f.key())&&Json.str(values,f.key()).isBlank()).findFirst();if(missing.isPresent()){invalidField=missing.get().key();error=Client.text("ui.fill_in_f1e4ad2f")+missing.get().label()+"»";for(var cell:grid())if(cell.field().key().equals(invalidField))restoreScroll(cell.row());rebuildWidgets();for(var child:children())if(child instanceof AbstractWidget widget&&widget.getMessage().getString().equals(missing.get().label())){setFocused(child);break;}return;}minecraft.setScreen(parent);submit.accept(values.deepCopy());}

 boolean leavePage(String key){if(values.equals(original))return true;minecraft.setScreen(new UiConfirmDialog(yes->{if(yes){UiNavigation.back(minecraft.screen,parent);UiNavigation.open(parent,key);}else minecraft.setScreen(this);},Client.tr("ui.discard_changes_338fb3b7"),Client.tr("ui.the_text_you_entered_will_not_42577dde")));return false;}
 @Override protected void init(){ys.clear();scrollArea(UiFormGrid.units(grid()),new dev.abros.rivet.core.NativeLayout.Box(x(),top()+28,Math.max(0,w()),Math.max(0,(bottom()-54)-(top()+28))),22);
  for(var cell:grid()){if(!cell.visible(firstRow,visibleRows))continue;var f=cell.field();var area=cell.bounds(x(),top()+28,scrollLayout().content().width(),firstRow);ys.put(f.key(),area.y());
   if(Set.of("description","text").contains(f.key()))addRenderableWidget(UiFields.multiline(font,area.x(),area.y()+13,area.width(),38,f.label(),f.label(),f.limit(),Json.str(values,f.key()),v->editValue(f.key(),v)));
   else addRenderableWidget(UiFields.text(font,area.x(),area.y()+13,area.width(),20,f.label(),f.label(),f.limit(),Json.str(values,f.key()),v->editValue(f.key(),v)));
  }
  for(var child:children())if(child instanceof AbstractWidget input){if(fields.stream().anyMatch(f->f.key().equals(invalidField)&&f.label().equals(input.getMessage().getString())))UiFields.issue(input,error);}
  UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(x(),bottom()-28,scrollLayout().content().width(),20),this::addRenderableWidget,
   UiActions.primary(Client.text("map.tool.save"),this::save,true).withIcon(UiIcons.SAVE),UiActions.action(Client.text("cancel"),this::onClose,true));}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());for(var f:fields)if(ys.containsKey(f.key()))Ui.text(g,font,f.label(),x(),ys.get(f.key()),UiPalette.color(0xD7E2EC));for(var cell:grid())if(bottom()-top()-82>=88&&cell.field().key().equals(invalidField)&&cell.visible(firstRow,visibleRows))Ui.text(g,font,UiKit.fit(font,error,w()),x(),top()+28+(cell.row()-firstRow)*22+(Set.of("description","text").contains(cell.field().key())?68:36),UiPalette.color(0xFFEF7777),false);if(invalidField.isEmpty()||bottom()-top()-82<88)Ui.status(g,font,error,x(),bottom()-62,w(),bottom()-30);});}
 @Override public void onClose(){if(values.equals(original)){minecraft.setScreen(parent);return;}minecraft.setScreen(new UiConfirmDialog(yes->minecraft.setScreen(yes?parent:this),Client.tr("ui.discard_changes_338fb3b7"),Client.tr("ui.the_text_you_entered_will_not_42577dde")));}
 @Override public void tick(){if(validationDirty){validationDirty=false;rebuildWidgets();}}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(UiKeyboard.submit(this,key,modifiers)){save();return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public boolean isPauseScreen(){return false;}
}
