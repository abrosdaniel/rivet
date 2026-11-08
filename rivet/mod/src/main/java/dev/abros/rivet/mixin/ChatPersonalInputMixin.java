package dev.abros.rivet.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla recent-input handling, then route a personal shortcut through signed commands. */
@Mixin(ChatScreen.class)
public abstract class ChatPersonalInputMixin {
 @Inject(method="handleChatInput",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChat(Ljava/lang/String;)V"),cancellable=true)
 private void rivet$personal(String text,boolean remember,CallbackInfo ci){
  if(dev.abros.rivet.client.ClientChat.sendPersonal(((ChatScreen)(Object)this).normalizeChatMessage(text)))ci.cancel();
 }
}
