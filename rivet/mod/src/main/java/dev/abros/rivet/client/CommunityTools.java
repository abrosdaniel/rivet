package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import net.minecraft.client.Minecraft;
final class CommunityTools {
 static JsonArray options(String...pairs){var out=new JsonArray();for(int i=0;i<pairs.length;i+=2){var row=new JsonObject();row.addProperty("value",pairs[i]);row.addProperty("label",pairs[i+1]);out.add(row);}return out;}
 static void creation(String section,List<CommunityScreen.Field> fields,JsonObject preset){
  if(section.equals("events")){
   fields.add(new CommunityScreen.Field("visibility",Client.text("ui.who_can_see_the_event_8de69624"),20,options("public",Client.text("ui.all_fd08da7a"),"group",Client.text("map.layer.peers"),"invited",Client.text("ui.invited_fd8ee09f"))));
   fields.add(new CommunityScreen.Field("invitees",Client.text("ui.invited_players_09bcaf81"),1200));
   fields.add(new CommunityScreen.Field("repeat",Client.text("ui.recurrence_107232ba"),20,options("none",Client.text("ui.once_bfe9562a"),"weekly",Client.text("ui.weekly_5d3b2f5f"),"fortnightly",Client.text("ui.every_two_weeks_dc123b88"),"monthly",Client.text("ui.monthly_2c789c20"))));
   fields.add(new CommunityScreen.Field("occurrences",Client.text("ui.events_in_series_2_26_82aa8c10"),2));preset.addProperty("occurrences","8");preset.addProperty("timezone",AccessibilityScreen.zone().getId());
  }
  if(section.equals("board")){
   fields.add(new CommunityScreen.Field("trade",Client.text("ui.notice_type_da3b65e7"),20,options("none",Client.text("ui.normal_64962a2f"),"buy",Client.text("ui.buying_d5030427"),"sell",Client.text("ui.selling_1105ce7b"),"exchange",Client.text("ui.trading_9eb8da1b"))));
   fields.add(new CommunityScreen.Field("item",Client.text("ui.item_5d27dc44"),100));fields.add(new CommunityScreen.Field("quantity",Client.text("ui.quantity_576698c4"),7));preset.addProperty("quantity","1");fields.add(new CommunityScreen.Field("terms",Client.text("ui.trade_payment_terms_6989abc6"),500));
  }
 }
 static void groupItems(CommunityScreen host,JsonObject group){
  String kind=host.groupTab.equals("tasks")?"task":"place";boolean manage=group.get("manage").getAsBoolean();
  if(manage){host.actionRow(List.of(new CommunityScreen.Row(kind.equals("task")?Client.text("ui.task_df308174"):Client.text("ui.waypoint_acdea915"),()->edit(host,kind,null))));host.text("");}
  int found=0;
  if(group.has("groupItems"))for(var element:group.getAsJsonArray("groupItems")){var item=element.getAsJsonObject();if(!Json.str(item,"kind").equals(kind))continue;found++;int card=host.beginCard();var controls=new ArrayList<CommunityScreen.Row>();
   host.text(Json.str(item,"title"));if(kind.equals("task")&&TaskScreen.available())controls.add(new CommunityScreen.Row(Client.text("ui.open_task_60587630"),()->Minecraft.getInstance().setScreen(new TaskScreen(host.surface(),Json.str(group,"id"),Json.str(item,"id")))));String description=Json.opt(item,"description","");if(!description.isBlank())host.text(description);
   if(kind.equals("task")){
    host.text(Client.text("ui.status_156979c7")+switch(Json.str(item,"status")){case "done"->Client.text("done");case "working"->Client.text("ui.in_progress_9db2ea4b");default->Client.text("ui.open_87c42edb");});
    String assignee=Json.opt(item,"assignee","");host.text(assignee.isBlank()?Client.text("ui.no_assignee_b69f1a51"):Client.text("ui.assigned_to_31372c54")+host.shortName(assignee));long due=item.get("dueAt").getAsLong();if(due>0)host.text((due<System.currentTimeMillis()&&!Json.str(item,"status").equals("done")?Client.text("ui.overdue_474c16a8"):Client.text("ui.due_79fd6d0c"))+CommunityScreen.local(due));
    if(manage||assignee.equals(CommunityScreen.me()))controls.add(new CommunityScreen.Row(Client.text("ui.status_53f1ff16"),()->Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Json.str(item,"title"),List.of(Client.text("ui.open_87c42edb"),Client.text("ui.in_progress_9db2ea4b"),Client.text("done")),i->{var b=identity(item);b.addProperty("status",List.of("open","working","done").get(i));host.send("plusTaskStatus",b);}))));
   }else LocationActions.render(host,item);
   if(manage){controls.add(new CommunityScreen.Row(Client.text("map.tool.edit"),()->edit(host,kind,item)));controls.add(new CommunityScreen.Row(Client.text("map.tool.delete"),()->host.confirm(Client.text("ui.delete_bf072c65")+(kind.equals("task")?Client.text("ui.task_08797aef"):Client.text("ui.location_3397c0b3"))+"?","plusItemDelete",identity(item))));}
   host.actionRow(controls);
   host.endCard(card);
  }
  if(found==0)host.text(kind.equals("task")?Client.text("ui.no_tasks_yet_they_are_available_62993739"):Client.text("ui.no_internal_waypoints_yet_they_are_98efa59e"));
 }
 private static JsonObject identity(JsonObject item){var b=new JsonObject();b.addProperty("itemId",Json.str(item,"id"));b.add("itemRevision",item.get("revision"));return b;}
 private static void edit(CommunityScreen host,String kind,JsonObject item){
  var preset=new JsonObject();if(item!=null)for(String key:List.of("title","description","assignee","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());if(item!=null){preset.addProperty("itemId",Json.str(item,"id"));preset.add("itemRevision",item.get("revision"));long due=item.has("dueAt")?item.get("dueAt").getAsLong():0;preset.addProperty("dueAt",due==0?"":java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(due).atZone(AccessibilityScreen.zone())));}
  preset.addProperty("kind",kind);var fields=new ArrayList<CommunityScreen.Field>();fields.add(new CommunityScreen.Field("title",Client.text("map.name"),kind.equals("place")?80:100));fields.add(new CommunityScreen.Field("description",Client.text("ui.description_f5441f6a"),1000));
  if(kind.equals("task")){fields.add(new CommunityScreen.Field("assignee",Client.text("ui.assignee_46c1c6b4"),100));fields.add(new CommunityScreen.Field("dueAt",Client.text("ui.deadline_optional_fdcd77a3"),30));}
  host.form(item==null?kind.equals("task")?Client.text("ui.new_task_bcfbe789"):Client.text("ui.new_group_waypoint_0975249d"):Client.text("ui.edit_901beb5f"), "plusItemSave",fields,preset);
 }
}
