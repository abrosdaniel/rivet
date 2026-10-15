package dev.abros.rivet.server.compat;

import com.google.gson.JsonObject;
import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.compat.NumismaticsBindings;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.compat.AccountIdentity;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Public identity reads only. Balances, authorization and account IDs remain Numismatics-owned. */
final class NumismaticsAdapter implements CompatibilityAdapter {
 public String id(){return "numismatics";}
 public dev.abros.rivet.core.OptionalIntegration lifecycle(){return NumismaticsBindings.CAPABILITY;}
 public String status(){return lifecycle().status();}
 public void clear(){lifecycle().reset();}
 public JsonObject execute(ServerPlayer actor,JsonObject request,IntegrationApi.Identity identities){
  if(!Json.str(request,"op").equals("lookup"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_adapter_action_8c0ca841"));
  var player=AccountIdentity.find(identities,UUID.fromString(Json.str(request,"uuid"))).orElseThrow(()->new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_not_found_on_this_server_7c3ea4bb")));
  var reply=new JsonObject();reply.addProperty("uuid",player.uuid().toString());reply.addProperty("name",player.name());return reply;
 }
}
