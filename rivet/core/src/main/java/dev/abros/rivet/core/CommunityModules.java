package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Enabled request services; document persistence and transactions remain shared. */
final class CommunityModules {
 @FunctionalInterface interface Handler { JsonObject request(CommunityStore.Actor actor,JsonObject input,String operation,String section)throws Exception; }
 private final Map<String,Handler> documents;
 CommunityModules(PgDatabase database,CommunityStore store,FeatureModules policy){
  var handlers=new LinkedHashMap<String,Handler>();
  for(String section:List.of("groups"))if(policy.enabled(section)){
   var module=new DocumentModule(section,store);handlers.put(section,module::request);
  }
  documents=Map.copyOf(handlers);
 }
 JsonObject request(CommunityStore store,CommunityStore.Actor actor,JsonObject input,String operation,String section)throws Exception{
  if(operation.startsWith("work"))RetiredFeatures.requireActive("tasks");
  var handler=documents.get(section);
  if(handler!=null)return handler.request(actor,input,operation,section);
  store.modules().require(section);
  return store.documentOperation(actor,input,operation,section);
 }
 Set<String> active(){return documents.keySet();}
 private record DocumentModule(String section,CommunityStore store){
  JsonObject request(CommunityStore.Actor actor,JsonObject input,String operation,String requested)throws Exception{
   if(!section.equals(requested))throw new IllegalArgumentException("Incorrect module route");
   store.modules().require(section);return store.documentOperation(actor,input,operation,section);
  }
 }
}
