package dev.abros.rivet.compat;
import dev.abros.rivet.core.OptionalIntegration;
/** Loader metadata never requires loading an optional mod's implementation classes. */
public final class IntegrationSupport {
 public static String version(String mod){return net.neoforged.fml.ModList.get().getModContainerById(mod).map(c->c.getModInfo().getVersion().toString()).orElse("");}
 public static OptionalIntegration capability(String mod,String capability,OptionalIntegration.Operation<String> probe){return new OptionalIntegration(mod+":"+capability,()->net.neoforged.fml.ModList.get().isLoaded(mod),()->true,()->version(mod),probe,(name,error)->com.mojang.logging.LogUtils.getLogger().warn("Rivet adapter {} failed",name,error));}
 private IntegrationSupport(){}
}
