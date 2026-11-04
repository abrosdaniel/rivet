package dev.abros.rivet.mixin;
import dev.abros.rivet.server.ServerPack;
import dev.abros.rivet.core.Json;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.status.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Adds a status extension ignored by vanilla clients; no login or mod handshake is needed. */
@Mixin(ClientboundStatusResponsePacket.class)
abstract class PackStatusMixin {
 @Inject(method="write",at=@At("HEAD"),cancellable=true)
 private void rivet$packStatus(FriendlyByteBuf buffer,CallbackInfo ci){var pack=ServerPack.advertisement();var packet=(ClientboundStatusResponsePacket)(Object)this;JsonObject status=ServerStatus.CODEC.encodeStart(JsonOps.INSTANCE,packet.status()).getOrThrow().getAsJsonObject();if(pack!=null)status.add("rivetPack",pack);var rivet=new JsonObject();rivet.addProperty("preflight",1);rivet.addProperty("version",dev.abros.rivet.Rivet.VERSION);rivet.add("protocols",dev.abros.rivet.core.WireProtocols.current());status.add("rivet",rivet);buffer.writeUtf(Json.GSON.toJson(status));ci.cancel();}
}
