package dev.abros.rivet.mixin;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChatScreen.class)
public interface ChatInputAccessor {@Accessor("input") EditBox rivet$input(); @Accessor("commandSuggestions") net.minecraft.client.gui.components.CommandSuggestions rivet$suggestions();}
