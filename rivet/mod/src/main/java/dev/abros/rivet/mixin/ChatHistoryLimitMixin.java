package dev.abros.rivet.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Only received messages/display lines: command input history retains Minecraft's own limit. */
@Mixin(ChatComponent.class)
public abstract class ChatHistoryLimitMixin {
 @ModifyConstant(method={"addMessageToQueue","addMessageToDisplayQueue"},constant=@Constant(intValue=100),require=2)
 private int rivet$historyLimit(int vanilla){return dev.abros.rivet.client.ClientChat.historyLimit();}
}
