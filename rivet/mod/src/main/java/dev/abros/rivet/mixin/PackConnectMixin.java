package dev.abros.rivet.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ConnectScreen.class)
public abstract class PackConnectMixin {
 @Inject(method="startConnecting",at=@At("HEAD"),cancellable=true)
 private static void pack(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,TransferState transfer,CallbackInfo ci){if(dev.abros.rivet.client.ServerPackClient.intercept(parent,mc,address,data,quick,transfer))ci.cancel();}
}
