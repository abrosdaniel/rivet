package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Group action availability shared by response models and command authorization. */
public final class CommunityPolicy {
 private CommunityPolicy(){}
 public static JsonArray actions(JsonObject item,CommunityStore.Actor actor,long now){return actions(item,actor,now,30);}
 public static JsonArray actions(JsonObject item,CommunityStore.Actor actor,long now,int trashDays){
  if(trashDays<1||trashDays>365)throw new IllegalArgumentException("Invalid trash retention");
  var out=new JsonArray();
  // Archived mechanics remain readable through export, never actionable even for their owner.
  if(!Json.str(item,"section").equals("groups"))return out;
  String status=Json.str(item,"status");boolean owner=actor.id().equals(Json.str(item,"owner"));
  var members=item.getAsJsonObject("members");boolean member=members.has(actor.id());
  boolean manager=actor.admin()||owner||member&&"assistant".equals(members.get(actor.id()).getAsString());
  if(status.equals("deleted")){
   if((owner||actor.admin())&&item.get("deletedAt").getAsLong()+(long)trashDays*86400000>now)out.add("restore");
   return out;
  }
  var actions=new LinkedHashSet<String>();
  if(owner||actor.admin())actions.add("delete");
  if(status.equals("hidden")){actions.forEach(out::add);return out;}
  if(owner||actor.admin())actions.add("edit");
  if(item.getAsJsonObject("applications").has(actor.id()))actions.add("withdrawApplication");
  if(manager&&!item.getAsJsonObject("invitations").isEmpty())actions.add("revokeInvitation");
  if(status.equals("open")){
   if(manager)Collections.addAll(actions,"application","invite","recruiting");
   if(owner)actions.add("role");
   if(member&&!owner)actions.add("leave");
   if(!member&&item.get("recruiting").getAsBoolean()&&!item.getAsJsonObject("applications").has(actor.id()))actions.add("apply");
   if(item.getAsJsonObject("invitations").has(actor.id()))actions.add("invitation");
  }
  if(actor.admin())actions.add("hide");actions.forEach(out::add);return out;
 }
 public static void check(JsonObject item,CommunityStore.Actor actor,String operation,long now){check(item,actor,operation,now,30);}
 public static void check(JsonObject item,CommunityStore.Actor actor,String operation,long now,int trashDays){if(!actions(item,actor,now,trashDays).contains(new JsonPrimitive(operation)))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.this_action_is_no_longer_available_da9d0ffd"));}
}
