package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Enabled request services; document persistence and transactions remain shared. */
final class CommunityModules {
 @FunctionalInterface interface Handler { JsonObject request(CommunityStore.Actor actor,JsonObject input,String operation,String section)throws Exception; }
 private final Map<String,Handler> documents;
 private final CommunityTasks tasks;
 CommunityModules(PgDatabase database,CommunityStore store,FeatureModules policy){
  tasks=policy.enabled("tasks")?new CommunityTasks(database,store):null;
  var handlers=new LinkedHashMap<String,Handler>();
  for(String section:List.of("groups","board","events","polls","ideas"))if(policy.enabled(section)){
   var module=new DocumentModule(section,store);handlers.put(section,module::request);
  }
  documents=Map.copyOf(handlers);
 }
 JsonObject request(CommunityStore store,CommunityStore.Actor actor,JsonObject input,String operation,String section)throws Exception{
  if(operation.startsWith("work")){if(tasks==null)store.modules().require("tasks");return tasks.request(actor,input);}
  var handler=documents.get(section);
  if(handler!=null)return handler.request(actor,input,operation,section);
  store.modules().require(section);
  return store.documentOperation(actor,input,operation,section);
 }
 Set<String> active(){var ids=new HashSet<>(documents.keySet());if(tasks!=null)ids.add("tasks");return Set.copyOf(ids);}
 private record DocumentModule(String section,CommunityStore store){
  JsonObject request(CommunityStore.Actor actor,JsonObject input,String operation,String requested)throws Exception{
   if(!section.equals(requested))throw new IllegalArgumentException("Incorrect module route");
   store.modules().require(section);return store.documentOperation(actor,input,operation,section);
  }
 }
}
