package dev.abros.rivet.client;
import com.google.gson.*;import dev.abros.rivet.core.Json;import java.util.*;
/** Task planning presentation, separate from workspace and request coordination. */
final class TaskPlanning {static void rows(TaskScreen host,JsonObject t){
  var deps=t.has("dependencyDetails")?t.getAsJsonArray("dependencyDetails"):new JsonArray();
  long blocked=java.util.stream.StreamSupport.stream(deps.spliterator(),false).filter(e->!e.getAsJsonObject().get("done").getAsBoolean()).count();if(blocked>0)host.paragraph(Client.text("ui.task_blocked_complete_e4d1aa30")+blocked+Client.text("ui.tasks_below_to_start_or_finish_216cb55c"));if(!deps.isEmpty())host.row(Client.text("ui.complete_first_70bb292d"),null);
  for(var e:deps){var d=e.getAsJsonObject();String code=Json.opt(d,"code","");host.paragraph((d.get("done").getAsBoolean()?Client.text("ui.completed_6d248a2a"):Client.text("ui.expected_656134df"))+code+" · "+Json.str(d,"title"));}
  int days=t.has("repeatDays")?t.get("repeatDays").getAsInt():0;var intervals=List.of(0,1,7,30);var labels=List.of(Client.text("ui.do_not_repeat_39b20e76"),Client.text("ui.daily_37cd1561"),Client.text("ui.every_7_days_4cd9d058"),Client.text("ui.every_30_days_2e7891c2"));
  if(days>0)host.row(Client.text("ui.repeat_4cb3196a")+labels.get(Math.max(0,intervals.indexOf(days))),null);
  if(days>0)host.paragraph(Client.text("ui.next_separate_task_f0110d72")+CommunityScreen.local(t.get("nextRepeatAt").getAsLong()));
 }
 private TaskPlanning(){}
}
