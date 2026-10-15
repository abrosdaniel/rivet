package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.api.IntegrationApi;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Core-facing permission service; absence never grants a capability. */
public final class ServerPermissions {
 private static Optional<IntegrationApi.Permissions> provider(){return ServerProviders.find(IntegrationApi.PERMISSIONS,"luckperms");}
 public static long revision(){return provider().map(IntegrationApi.Permissions::revision).orElse(0L);}
 public static JsonObject profile(ServerPlayer player){return ServerProviders.withPermissionActor(player,()->provider().map(p->ServerProviders.json(p.profile(player.getUUID(),true))).orElseGet(ServerPermissions::denied));}
 public static JsonObject profile(UUID id){return provider().map(p->ServerProviders.json(p.profile(id,false))).orElseGet(ServerPermissions::denied);}
 public static boolean allowed(ServerPlayer player,String right){return ServerProviders.withPermissionActor(player,()->provider().map(p->p.allowed(player.getUUID(),right)).orElse(false));}
 public static boolean offlineAllowed(UUID id,String right){return provider().map(p->p.offlineAllowed(id,right)).orElse(false);}
 public static JsonArray roles(){var result=new JsonArray();provider().ifPresent(p->p.roles().forEach(r->result.add(ServerProviders.json(r))));return result;}
 private static JsonObject denied(){var result=new JsonObject();var caps=new JsonObject();for(var right:ServerCommands.RIGHTS)caps.addProperty(right,false);for(var right:List.of("rivet.admin","rivet.events","rivet.auth.reset","rivet.map.teleport","rivet.vote.protected"))caps.addProperty(right,false);result.add("capabilities",caps);return result;}
 private ServerPermissions(){}
}
