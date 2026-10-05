package dev.abros.rivet.mixin;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Changes only the suggestion data; Minecraft owns rendering, navigation and insertion. */
@Mixin(CommandSuggestions.class)
public abstract class ChatGroupSuggestionsMixin {
 @Shadow @Final private EditBox input;
 @Shadow @Final private net.minecraft.client.gui.screens.Screen screen;
 @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
 @Shadow private boolean keepSuggestions;
 @Shadow public abstract void showSuggestions(boolean narrate);
 @Inject(method="updateCommandInfo",at=@At("TAIL"))
 private void rivet$groups(CallbackInfo ci){if(keepSuggestions||!(screen instanceof net.minecraft.client.gui.screens.ChatScreen))return;var groups=dev.abros.rivet.client.SocialChatControls.groupSuggestions(input.getValue(),input.getCursorPosition());if(groups==null)return;pendingSuggestions=CompletableFuture.completedFuture(groups);showSuggestions(false);}
}
