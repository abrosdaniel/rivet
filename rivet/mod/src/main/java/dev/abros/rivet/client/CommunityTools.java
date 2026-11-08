package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
final class CommunityTools {
 static void groupItems(CommunityScreen host,JsonObject group){
  boolean manage=group.get("manage").getAsBoolean();host.text(Client.text("flow.groupMarkersHint"));
  if(manage){host.actionRow(List.of(new CommunityScreen.Row(Client.text("ui.waypoint_acdea915"),()->edit(host,null))));host.text("");}
  int found=0;
  if(group.has("groupItems"))for(var element:group.getAsJsonArray("groupItems")){
   var item=element.getAsJsonObject();if(!Json.str(item,"kind").equals("place"))continue;
   found++;int card=host.beginCard();var controls=new ArrayList<CommunityScreen.Row>();
   host.text(Json.str(item,"title"));String description=Json.opt(item,"description","");if(!description.isBlank())host.text(description);
   LocationActions.render(host,item);
   if(manage){controls.add(new CommunityScreen.Row(Client.text("map.tool.edit"),()->edit(host,item)));controls.add(new CommunityScreen.Row(Client.text("map.tool.delete"),()->host.confirm(Client.text("ui.delete_bf072c65")+Client.text("ui.location_3397c0b3")+"?","plusItemDelete",identity(item))));}
   host.actionRow(controls);host.endCard(card);
  }
  if(found==0)host.text(Client.text("ui.no_internal_waypoints_yet_they_are_98efa59e"));
 }
 private static JsonObject identity(JsonObject item){var b=new JsonObject();b.addProperty("itemId",Json.str(item,"id"));b.add("itemRevision",item.get("revision"));return b;}
 private static void edit(CommunityScreen host,JsonObject item){
  var preset=new JsonObject();if(item!=null){for(String key:List.of("title","description","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());preset.addProperty("itemId",Json.str(item,"id"));preset.add("itemRevision",item.get("revision"));}
  preset.addProperty("kind","place");
  var fields=List.of(new CommunityScreen.Field("title",Client.text("map.name"),80),new CommunityScreen.Field("description",Client.text("ui.description_f5441f6a"),1000));
  host.form(item==null?Client.text("ui.new_group_waypoint_0975249d"):Client.text("ui.edit_901beb5f"),"plusItemSave",fields,preset);
 }
}
