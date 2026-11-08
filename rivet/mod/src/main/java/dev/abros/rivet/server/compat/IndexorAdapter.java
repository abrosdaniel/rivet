package dev.abros.rivet.server.compat;
import dev.abros.rivet.compat.IndexorBindings;
import dev.abros.rivet.api.IntegrationApi;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
/** Indexor commands are the UI; no duplicate history endpoint or storage. */
final class IndexorAdapter implements CompatibilityAdapter {
 public String id(){return "indexor";}
 public dev.abros.rivet.core.OptionalIntegration lifecycle(){return IndexorBindings.CAPABILITY;}
 public String status(){return lifecycle().status();}
 public void clear(){IndexorBindings.clear();}
 public JsonObject execute(ServerPlayer actor,JsonObject request,IntegrationApi.Identity identity){throw new IllegalArgumentException("Use /ledger");}
}
