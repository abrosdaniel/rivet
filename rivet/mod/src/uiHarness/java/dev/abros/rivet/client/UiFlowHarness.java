package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Behavioral checks for typed actions, keyboard navigation, validation and repeated search responses. */
final class UiFlowHarness {
 static void verify(){
  var mc=Minecraft.getInstance();var previous=mc.screen;var transport=ServerMenuClient.previewTransport;
  try{
   UiGeometryHarness.verifyLayouts();
   verifyFavorites();
   var savedState=ServerMenuClient.state;
   try{ServerMenuClient.state=new JsonObject();if(MenuActions.available().containsKey("admin"))throw new IllegalStateException("Player search exposes administration");ServerMenuClient.state.addProperty("admin",true);if(!MenuActions.available().containsKey("admin"))throw new IllegalStateException("Admin action missing");ServerMenuClient.state.addProperty("admin",false);MenuActions.open(previous,"admin");if(mc.screen!=previous)throw new IllegalStateException("Revoked action opened administration");if(MenuActions.search("settings").isEmpty())throw new IllegalStateException("Local actions require server response");}finally{ServerMenuClient.state=savedState;}

   var typed=UiActions.button(Component.literal("Любое название"),UiActions.Tone.DANGER,UiIcons.DELETE,b->{}).bounds(0,0,80,20).build();typed.setMessage(Component.literal("Переименовано"));if(UiActions.style(typed).tone()!=UiActions.Tone.DANGER)throw new IllegalStateException("Action role depends on text");
   var preset=new JsonObject();preset.addProperty("title","");var editor=new UiRecordForm(previous,"Проверка формы",List.of(new CommunityScreen.Field("title","Название",100)),preset,q->{throw new IllegalStateException("Invalid form sent a request");});mc.setScreen(editor);
   var field=editor.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();editor.setFocused(field);
   UiKeyboard.tab(editor,258,0);if(editor.getFocused()==field)throw new IllegalStateException("Tab did not leave input");UiKeyboard.tab(editor,258,1);if(editor.getFocused()!=field)throw new IllegalStateException("Shift Tab did not return to input");
   field.setValue("Сохранённый текст");field.setCursorPosition(4);editor.setFocused(field);mc.setScreen(new ChoicePopup(editor,"Выбор",List.of("Один","Два"),n->{}));mc.screen.onClose();if(!(editor.getFocused() instanceof EditBox restored)||restored.getCursorPosition()!=4||!restored.getValue().equals("Сохранённый текст"))throw new IllegalStateException("Dialog return lost focus or cursor");
   ((EditBox)editor.getFocused()).setValue("");editor.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Client.text("map.tool.save"))).map(c->(Button)c).findFirst().orElseThrow(()->new IllegalStateException("Missing Save action")).onPress();if(!(editor.getFocused() instanceof EditBox))throw new IllegalStateException("Validation did not focus invalid field");
   var packet=new JsonObject[]{null};ServerMenuClient.previewTransport=q->packet[0]=q;var search=new GlobalSearchScreen(previous);mc.setScreen(search);
   for(int pass=0;pass<3;pass++){
    var input=search.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();input.setValue("Поиск");search.setFocused(input);search.keyPressed(257,0,0);
    if(packet[0]==null)throw new IllegalStateException("Search was not sent");var response=new JsonObject();response.addProperty("request",Json.str(packet[0],"request"));if(pass==2){response.addProperty("error",true);response.addProperty("text","Проверка ошибки");}else response.add("entries",new JsonArray());search.receiveCommunity(response);
    if(search.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Client.text("ui.find_ed6e1d03"))).map(c->(Button)c).noneMatch(b->b.active))throw new IllegalStateException("Search controls stayed disabled after repeated response");
   }
   var latest=new GlobalSearchScreen(previous);mc.setScreen(latest);var input=latest.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();input.setValue("Первый");latest.setFocused(input);latest.keyPressed(257,0,0);String old=Json.str(packet[0],"request");input.setValue("Второй");latest.keyPressed(257,0,0);String current=Json.str(packet[0],"request");if(old.equals(current))throw new IllegalStateException("New search reused cancelled correlation");var stale=new JsonObject();stale.addProperty("request",old);stale.addProperty("error",true);stale.addProperty("text","Stale response");latest.receiveCommunity(stale);var ready=new JsonObject();ready.addProperty("request",current);ready.add("entries",new JsonArray());latest.receiveCommunity(ready);if(latest.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(Client.text("ui.find_ed6e1d03"))).map(c->(Button)c).noneMatch(b->b.active))throw new IllegalStateException("Latest search failed after stale response");
   var clearing=new GlobalSearchScreen(previous);mc.setScreen(clearing);var clearInput=clearing.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();clearInput.setValue("Pending");clearing.setFocused(clearInput);clearing.keyPressed(257,0,0);
   var delayed=new JsonObject();delayed.addProperty("request",Json.str(packet[0],"request"));var results=new JsonArray();var row=new JsonObject();row.addProperty("section","players");row.addProperty("id",new java.util.UUID(0,1).toString());row.addProperty("title","StalePlayer");results.add(row);delayed.add("entries",results);
   clearInput=clearing.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();clearInput.setValue("");clearing.receiveCommunity(delayed);clearing.tick();
   if(clearing.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().contains("StalePlayer")))throw new IllegalStateException("Cleared search accepted delayed result during debounce");
   System.out.println("RIVET_FLOW_OK: typed actions, Tab, focus/cursor return, validation, repeated search/error responses");
  }finally{ServerMenuClient.previewTransport=transport;mc.setScreen(previous);}
 }
 private UiFlowHarness(){}
 private static void verifyFavorites(){
  HomeLayout.load();var savedState=ServerMenuClient.state;
  try{
   var field=HomeLayout.class.getDeclaredField("favorites");field.setAccessible(true);var saved=field.get(null);
   try{
    field.set(null,new HashSet<>(List.of("action:admin","action:settings")));
    ServerMenuClient.state=new JsonObject();ServerMenuClient.state.addProperty("admin",true);
    if(!HomeLayout.visible("action:admin")||HomeLayout.actions().size()!=2)throw new IllegalStateException("Favorite actions missing");
    ServerMenuClient.state.addProperty("admin",false);
    if(HomeLayout.visible("action:admin")||HomeLayout.order().contains("action:admin")||HomeLayout.actions().size()!=1)throw new IllegalStateException("Revoked favorite remained visible");
    ServerMenuClient.state.addProperty("admin",true);
    if(!HomeLayout.visible("action:admin"))throw new IllegalStateException("Temporary permission loss deleted favorite");
    var rows=HomeLayout.arrange(HomeLayout.actions());
    if(rows.size()!=2||!Json.str(rows.get(0).getAsJsonObject(),"id").startsWith("local:action:"))throw new IllegalStateException("Favorite home cards lost");
   }finally{field.set(null,saved);}
  }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}finally{ServerMenuClient.state=savedState;}
 }
}
