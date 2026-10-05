package dev.abros.rivet.core;
import com.google.gson.*;
/** Bounded task response pages. Database arrays and their indices remain unchanged. */
final class TaskDetailPage {
 static final int SIZE=20;
 static void apply(JsonObject task,JsonObject request){
  page(task,"subtasks","subtaskOffset",offset(request,"subtaskOffset",0));
  page(task,"comments","commentOffset",offset(request,"commentOffset",-1));
  if(task.has("history"))for(var event:task.getAsJsonArray("history"))if(event.getAsJsonObject().has("changes"))for(var e:event.getAsJsonObject().getAsJsonArray("changes")){
   var change=e.getAsJsonObject();for(String key:new String[]{"before","after"})if(change.has(key)){
    var value=change.get(key);if(value.isJsonArray()&&value.toString().length()>300)change.addProperty(key,"Записей: "+value.getAsJsonArray().size());
    else if(value.toString().length()>300)change.addProperty(key,ChangeSummary.text(value).substring(0,Math.min(250,ChangeSummary.text(value).length()))+"…");
   }
  }
  // Escape-heavy comments can be larger than plain text; budget the encoded response.
  while(task.toString().length()>28000){boolean trimmed=false;
   for(String key:new String[]{"comments","subtasks","history"})if(task.has(key)&&task.getAsJsonArray(key).size()>1){var rows=task.getAsJsonArray(key);rows.remove(rows.size()-1);trimmed=true;break;}
   if(!trimmed)break;
  }
 }
 private static int offset(JsonObject request,String key,int fallback){if(!request.has(key))return fallback;long n=request.get(key).getAsLong();if(n<0||n>Integer.MAX_VALUE)throw new IllegalArgumentException("Некорректная страница задачи");return (int)n;}
 private static void page(JsonObject task,String key,String offsetKey,int requested){
  var all=task.has(key)?task.getAsJsonArray(key):new JsonArray();int count=all.size();int start=requested<0?Math.max(0,count-SIZE):Math.min(requested,Math.max(0,count-1));
  var rows=new JsonArray();for(int n=start;n<Math.min(count,start+SIZE);n++)rows.add(all.get(n).deepCopy());
  task.add(key,rows);task.addProperty(offsetKey,start);task.addProperty(key.equals("comments")?"commentCount":"subtaskCount",count);
 }
 private TaskDetailPage(){}
}
