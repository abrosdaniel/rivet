package dev.abros.rivet.server.compat;
import com.google.gson.*;
import dev.abros.rivet.compat.AccessDeniedBindings;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.compat.AccessRepairPlan;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

final class CreateAccessDeniedAdapter implements CompatibilityAdapter {
 private final dev.abros.rivet.core.OptionalIntegration lifecycle=dev.abros.rivet.compat.IntegrationSupport.capability(AccessDeniedBindings.MOD,"network-access",()->{if(!AccessDeniedBindings.supported())throw new NoSuchMethodException(dev.abros.rivet.core.Messages.text("rivet.core.version_verified_fd2bc829")+AccessDeniedBindings.VERSION);AccessDeniedBindings.type("extensions.LogisticNetworkExtensions").getMethod("accessDenied$getAllowedPlayers");return "Access Denied "+AccessDeniedBindings.VERSION;});
 public dev.abros.rivet.core.OptionalIntegration lifecycle(){return lifecycle;}
 private record Preview(UUID owner,UUID network,AccessRepairPlan plan,long expires){}
 private final Map<UUID,Preview> previews=new HashMap<>();
 public JsonObject diagnostics(){var row=CompatibilityAdapter.super.diagnostics();row.add("capabilities",dev.abros.rivet.core.Json.GSON.toJsonTree(java.util.List.of("network-access","verified-identity-repair")));return row;}
 public String id(){return AccessDeniedBindings.ID;}
 public String status(){return lifecycle.status();}
 public void clear(){previews.clear();lifecycle.reset();}
 public JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception{
  if(!AccessDeniedBindings.supported())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.access_denied_adapter_94acf0ff")+status());
  String op=Json.str(request,"op");var result=new JsonObject();
  if(op.equals("lookup")){
   var profile=request.has("name")?identities.byName(Json.str(request,"name")):identities.byId(UUID.fromString(Json.str(request,"uuid")));
   var p=profile.orElseThrow(()->new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_not_found_on_this_server_7c3ea4bb")));result.addProperty("name",p.getName());result.addProperty("uuid",p.getId().toString());return result;
  }
  var network=UUID.fromString(Json.str(request,"network"));
  if(!AccessDeniedBindings.mayAdministrate(network,actor))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.no_permission_to_manage_this_logistics_deed8389"));
  long now=System.currentTimeMillis();previews.values().removeIf(p->p.expires()<now);
  if(op.equals("add")){
   var p=identities.byName(Json.str(request,"name")).orElseThrow(()->new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_not_found_on_this_server_7c3ea4bb")));
   var allowed=AccessDeniedBindings.allowed(network);if(!allowed.contains(p.getId())&&allowed.size()>=AccessDeniedBindings.limit())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_limit_reached_536b9709"));
   AccessDeniedBindings.add(network,p.getId());AccessDeniedBindings.sync(network,actor);result.addProperty("name",p.getName());result.addProperty("uuid",p.getId().toString());result.addProperty("text",dev.abros.rivet.core.Messages.text("rivet.core.access_granted_43f8a9a8")+p.getName());return result;
  }
  if(op.equals("preview")){
   var plan=AccessRepairPlan.preview(AccessDeniedBindings.allowed(network),identities::verifiedAlias);
   var rows=new JsonArray();plan.replacements().forEach((from,to)->{var row=new JsonObject();row.addProperty("from",from.toString());row.addProperty("to",to.toString());row.addProperty("name",identities.byId(to).map(p->p.getName()).orElse(to.toString()));rows.add(row);});result.add("replacements",rows);
   result.addProperty("text",rows.size()+dev.abros.rivet.core.Messages.text("rivet.core.confirmed_replacements_other_entries_will_be_4db843f2"));
   if(!rows.isEmpty()){if(previews.size()>=128)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_previews_e98ec549"));previews.values().removeIf(p->p.owner().equals(actor.getUUID())&&p.network().equals(network));var token=UUID.randomUUID();previews.put(token,new Preview(actor.getUUID(),network,plan,now+120000));result.addProperty("token",token.toString());}return result;
  }
  if(op.equals("apply")){
   var token=UUID.fromString(Json.str(request,"token"));var preview=previews.get(token);
   if(preview==null||!preview.owner().equals(actor.getUUID())||!preview.network().equals(network))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.preview_expired_review_the_list_again_dc9dbe3f"));
   // A verified alias may have been unlinked since the preview.
   for(var entry:preview.plan().replacements().entrySet())if(!identities.verifiedAlias(entry.getKey()).filter(entry.getValue()::equals).isPresent())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.account_link_changed_preview_again_6f24059c"));
   AccessDeniedBindings.replace(network,preview.plan().before(),preview.plan().after());previews.remove(token);AccessDeniedBindings.sync(network,actor);result.addProperty("text",dev.abros.rivet.core.Messages.text("rivet.core.entries_repaired_f8decb38")+preview.plan().replacements().size());return result;
  }
  throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_adapter_action_8c0ca841"));
 }
}
