package dev.abros.rivet.mixin;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
/** Only removes an unsigned duplicate at the newest history entry; signed player messages are untouched. */
@Mixin(ChatComponent.class)
public interface ChatHistoryAccessor {
 @Accessor("allMessages") List<GuiMessage> rivet$messages();
 @Invoker("refreshTrimmedMessages") void rivet$refresh();
}
