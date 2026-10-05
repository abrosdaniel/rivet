package dev.abros.rivet.server.compat;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
/** Each optional integration owns its supported versions, operations and permissions. */
public interface CompatibilityAdapter {
 int API_VERSION=1;
 default int apiVersion(){return API_VERSION;}
 dev.abros.rivet.core.OptionalIntegration lifecycle();
 String id();
 String status();
 JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception;
 default com.google.gson.JsonObject diagnostics(){var row=lifecycle().diagnostics();row.addProperty("id",id());return row;}
 default void clear(){}
}
