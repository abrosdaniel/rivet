package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Task planning rules, separate from transport and inventory scanning. Caller holds the workflow lock. */
final class TaskWorkflow {
 private final PgDatabase db;private final CommunityStore store;
 TaskWorkflow(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 private static void check(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
 static boolean sameScope(JsonObject a,JsonObject b){String group=Json.opt(a,"group","");return group.equals(Json.opt(b,"group",""))&&(!group.isEmpty()||Json.str(a,"owner").equals(Json.str(b,"owner")));}
 private JsonObject graphNode(String id,Map<String,JsonObject> nodes)throws Exception{
  var cached=nodes.get(id);if(cached!=null)return cached;long started=PerformanceMetrics.start();
  try(var q=db.connection().prepareStatement("SELECT body->'dependencies',group_id,owner FROM community_group_items WHERE id=? AND kind='task'")){q.setString(1,id);try(var rs=q.executeQuery()){
   if(!rs.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,dev.abros.rivet.core.Messages.text("rivet.core.task_not_found_43c5e77e"));var t=new JsonObject();if(rs.getString(1)!=null)t.add("dependencies",JsonParser.parseString(rs.getString(1)));t.addProperty("id",id);t.addProperty("group",Objects.toString(rs.getString(2),""));t.addProperty("owner",rs.getString(3));nodes.put(id,t);return t;
  }}finally{PerformanceMetrics.end("task.graph.query",started);}
 }

 String fromCode(String code)throws Exception{try(var q=db.connection().prepareStatement("SELECT task_id FROM community_task_codes WHERE code=?")){q.setString(1,TaskCodes.normalize(code));try(var rs=q.executeQuery()){check(rs.next(),dev.abros.rivet.core.Messages.text("rivet.core.task_code_not_found_d7ab0042"));return rs.getString(1);}}}
 void dependencies(JsonObject t,String codes)throws Exception{
  var ids=new LinkedHashSet<String>();for(String code:codes.strip().split("[\\s,;]+"))if(!code.isBlank())ids.add(fromCode(code));check(ids.size()<=10,dev.abros.rivet.core.Messages.text("rivet.core.at_most_10_dependencies_9527df10"));
  // Per-operation memo only: every root retains its own 200-node limit and cycle check.
  var nodes=new HashMap<String,JsonObject>();
  for(String id:ids){check(sameScope(t,graphNode(id,nodes)),dev.abros.rivet.core.Messages.text("rivet.core.dependencies_must_belong_to_the_same_235a610d"));var pending=new ArrayDeque<String>();var seen=new HashSet<String>();pending.add(id);while(!pending.isEmpty()){String next=pending.removeFirst();check(!next.equals(Json.str(t,"id")),dev.abros.rivet.core.Messages.text("rivet.core.dependencies_contain_a_cycle_de7c9a1b"));if(!seen.add(next))continue;check(seen.size()<=200,dev.abros.rivet.core.Messages.text("rivet.core.task_chain_is_too_long_c2ac2f8c"));var task=graphNode(next,nodes);if(task.has("dependencies"))for(var e:task.getAsJsonArray("dependencies"))pending.add(e.getAsString());}}
  var rows=new JsonArray();ids.forEach(rows::add);t.add("dependencies",rows);
 }
 JsonArray dependencies(JsonObject t)throws Exception{
  var rows=new JsonArray();if(!t.has("dependencies")||t.getAsJsonArray("dependencies").isEmpty())return rows;
  var ids=new LinkedHashSet<String>();for(var id:t.getAsJsonArray("dependencies"))ids.add(id.getAsString());
  var found=new HashMap<String,JsonObject>();long started=PerformanceMetrics.start();
  try(var q=db.connection().prepareStatement("SELECT t.id,t.body->>'title',coalesce(t.task_status,'open'),t.group_id,t.owner,c.code FROM community_group_items t LEFT JOIN community_task_codes c ON c.task_id=t.id WHERE t.kind='task' AND t.id=ANY(?)")){
   q.setArray(1,db.connection().createArrayOf("text",ids.toArray()));try(var rs=q.executeQuery()){while(rs.next()){
    var dep=new JsonObject();dep.addProperty("id",rs.getString(1));dep.addProperty("title",rs.getString(2));dep.addProperty("done",rs.getString(3).equals("done"));dep.addProperty("group",Objects.toString(rs.getString(4),""));dep.addProperty("owner",rs.getString(5));if(rs.getString(6)!=null)dep.addProperty("code",rs.getString(6));found.put(rs.getString(1),dep);
   }}
  }finally{PerformanceMetrics.end("task.dependencies.query",started);}
  // Preserve the stored order, missing-dependency blockers and scope filtering.
  for(var id:t.getAsJsonArray("dependencies")){var dep=found.get(id.getAsString());if(dep==null){var row=new JsonObject();row.addProperty("title",dev.abros.rivet.core.Messages.text("rivet.core.a_dependency_task_was_deleted_6887826f"));row.addProperty("done",false);rows.add(row);}else if(sameScope(t,dep)){var row=dep.deepCopy();row.remove("group");row.remove("owner");rows.add(row);}}
  return rows;
 }

 void start(JsonObject t)throws Exception{for(var e:dependencies(t))check(e.getAsJsonObject().get("done").getAsBoolean(),dev.abros.rivet.core.Messages.text("rivet.core.complete_blocking_tasks_first_5b1916a2"));}
 JsonObject stock(JsonObject t)throws Exception{
  var out=new JsonObject();if(!store.modules().enabled("storage"))return out;var wanted=new HashSet<String>();if(t.has("resources"))for(var e:t.getAsJsonArray("resources"))wanted.addAll(ResourceRequirements.items(e.getAsJsonObject()));if(wanted.isEmpty())return out;var inventories=new HashSet<String>();String group=Json.opt(t,"group","");long started=PerformanceMetrics.start();
  try(var q=db.connection().prepareStatement("SELECT s.body,s.location FROM community_task_stocks s JOIN community_group_items t ON t.id=s.task_id WHERE t.kind='task' AND ((?='' AND t.group_id IS NULL AND t.owner=?) OR t.group_id=?) ORDER BY s.location")){q.setString(1,group);q.setString(2,Json.str(t,"owner"));q.setString(3,group);try(var rs=q.executeQuery()){while(rs.next()){var s=Json.parse(rs.getString(1));if(!inventories.add(Json.opt(s,"inventory",rs.getString(2)))||!s.has("items"))continue;for(var e:s.getAsJsonObject("items").entrySet())if(wanted.contains(e.getKey()))out.addProperty(e.getKey(),(out.has(e.getKey())?out.get(e.getKey()).getAsLong():0)+e.getValue().getAsLong());}}}finally{PerformanceMetrics.end("task.stock.query",started);}return out;
 }
 JsonObject reserved(JsonObject t)throws Exception{var out=new JsonObject();if(!store.modules().enabled("storage"))return out;String group=Json.opt(t,"group","");long started=PerformanceMetrics.start();try(var q=db.connection().prepareStatement("SELECT body->'reservations' FROM community_group_items WHERE kind='task' AND id<>? AND coalesce(task_status,'open')<>'done' AND ((?='' AND group_id IS NULL AND owner=?) OR group_id=?) AND jsonb_exists(body,'reservations')")){q.setString(1,Json.str(t,"id"));q.setString(2,group);q.setString(3,Json.str(t,"owner"));q.setString(4,group);try(var rs=q.executeQuery()){while(rs.next()){var j=Json.parse(rs.getString(1));for(var e:j.entrySet())out.addProperty(e.getKey(),(out.has(e.getKey())?out.get(e.getKey()).getAsLong():0)+e.getValue().getAsLong());}}}finally{PerformanceMetrics.end("task.reserved.query",started);}return out;}
 record ReservationSnapshot(JsonObject stock,JsonObject reserved){}
 // Reuse only inside the caller's transaction, while workflow and stock-quota locks remain held.
 ReservationSnapshot reserve(JsonObject t,boolean release)throws Exception{store.modules().require("storage");db.lock("task-stock-quota");var rows=new JsonObject();ReservationSnapshot snapshot=null;if(!release){check(!Json.opt(t,"status","open").equals("done"),dev.abros.rivet.core.Messages.text("rivet.core.completed_tasks_do_not_need_reservations_bfba13cc"));var stock=stock(t);var used=reserved(t);snapshot=new ReservationSnapshot(stock,used);if(t.has("resources"))rows=ResourceRequirements.reserve(t.getAsJsonArray("resources"),stock,used);}t.add("reservations",rows);return snapshot;}
 static void repeat(JsonObject t,int days,long now){check(Set.of(0,1,7,30).contains(days),dev.abros.rivet.core.Messages.text("rivet.core.unsupported_interval_fdbbd82c"));t.addProperty("repeatDays",days);long due=t.has("dueAt")?t.get("dueAt").getAsLong():0;t.addProperty("nextRepeatAt",days==0?0:Math.max(due,now)+days*86400000L);}
 static void history(JsonObject t,CommunityStore.Actor a,String op){var rows=t.has("history")?t.getAsJsonArray("history"):new JsonArray();while(rows.size()>=60)rows.remove(0);var row=new JsonObject();row.addProperty("at",System.currentTimeMillis());row.addProperty("author",a.name());row.addProperty("actor",a.id());(op.equals("workArchiveApply")?LocalizedText.key("rivet.ui.completed_tasks_archive_c3ccdbf5"):op.equals("workRestore")?LocalizedText.key("rivet.core.task_restored_from_archive_0ffa083f"):LocalizedText.key("rivet.core.task_update_71382206")).put(row,"reason");row.addProperty("operation",op);for(String key:List.of("status","assignee","dueAt","repeatDays"))if(t.has(key))row.add(key,t.get(key));rows.add(row);t.add("history",rows);}
}
