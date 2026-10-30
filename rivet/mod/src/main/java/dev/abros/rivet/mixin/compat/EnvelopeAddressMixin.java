package dev.abros.rivet.mixin.compat;
import com.mojang.serialization.MapCodec;
import dev.abros.rivet.compat.*;
import java.util.UUID;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="io.github.mortuusars.envelope.world.mail.address.type.PlayerAddress",remap=false)
public abstract class EnvelopeAddressMixin implements EnvelopeAddressOwner {
 @Shadow @Final @Mutable public static MapCodec<Object> CODEC;
 @Unique private UUID rivet$owner;
 @Unique private boolean rivet$legacy;
 public UUID rivet$owner(){return rivet$owner;}
 public void rivet$owner(UUID owner){rivet$owner=owner;}
 public boolean rivet$legacy(){return rivet$legacy;}
 public void rivet$legacy(boolean value){rivet$legacy=value;}
 @Inject(method="<init>(Ljava/lang/String;)V",at=@At("TAIL"),require=1)
 private void rivet$bind(String name,CallbackInfo callback){EnvelopeBindings.owner(this);}
 @Inject(method="<clinit>",at=@At("TAIL"),require=1)
 private static void rivet$codec(CallbackInfo callback){CODEC=new EnvelopeAddressCodec(CODEC);}
}
