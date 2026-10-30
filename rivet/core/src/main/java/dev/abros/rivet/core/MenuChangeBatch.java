package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;

/** Coalesce within a topic; a burst of notifications must not invalidate unrelated map layers. */
public final class MenuChangeBatch {
 private MenuChangeBatch(){}
 public static List<JsonObject> hints(JsonArray events,String recipient,String section,String target){
  var topics=new LinkedHashMap<String,Set<String>>();
  for(var value:events){var event=value.getAsJsonObject();String owner=Json.str(event,"recipient"),topic=Json.str(event,"topic"),id=Json.str(event,"entity");
   if(!owner.isEmpty()&&!owner.equals(recipient))continue;
   if(!section.equals(topic)&&!(section.equals("home")&&Set.of("home","groups","events","players").contains(topic)))continue;
   if(!target.isEmpty()&&!id.isEmpty()&&!target.equals(id))continue;
   topics.computeIfAbsent(topic,key->new HashSet<>()).add(id);
  }
  var result=new ArrayList<JsonObject>();topics.forEach((topic,ids)->{var hint=new JsonObject();hint.addProperty("kind","changed");hint.addProperty("section",topic);hint.addProperty("id",ids.size()==1?ids.iterator().next():"");result.add(hint);});return List.copyOf(result);
 }
}
