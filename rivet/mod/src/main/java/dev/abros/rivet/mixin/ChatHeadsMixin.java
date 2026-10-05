package dev.abros.rivet.mixin;
import dev.abros.rivet.client.SocialClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Adds heads only to Rivet-marked first lines, retaining Minecraft wrapping, fade and signed history. */
@Mixin(ChatComponent.class)
public abstract class ChatHeadsMixin {
 @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"),require=0)
 private int rivet$head(GuiGraphics g,Font font,FormattedCharSequence text,int x,int y,int color){SocialClient.drawChatHead(g,font,text,x,y);return g.drawString(font,text,x,y,color);}
}
