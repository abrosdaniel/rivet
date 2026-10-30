package dev.abros.rivet.server.compat;
import com.google.gson.JsonObject;
import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.compat.EnvelopeBindings;
import dev.abros.rivet.core.Json;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
/** Automatic mail identity adapter, sharing the public Rivet Java identity service. */
final class EnvelopeAdapter implements CompatibilityAdapter {
 public String id(){return "envelope";}
 public dev.abros.rivet.core.OptionalIntegration lifecycle(){return EnvelopeBindings.CAPABILITY;}
 public String status(){return lifecycle().status();}
 public void clear(){EnvelopeBindings.stop();}
 public JsonObject execute(ServerPlayer actor,JsonObject request,IntegrationApi.Identity identities){
  if(!Json.str(request,"op").equals("lookup"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_adapter_action_8c0ca841"));
  var player=identities.byId(UUID.fromString(Json.str(request,"uuid"))).orElseThrow(()->new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_not_found_on_this_server_7c3ea4bb")));
  var result=new JsonObject();result.addProperty("uuid",player.uuid().toString());result.addProperty("name",player.name());return result;
 }
}
