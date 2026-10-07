package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
/** Exercise the real pre-key event; direct Screen.keyPressed calls bypass navigation. */
final class UiInputHarness {
 static void escape(Screen screen){var event=new ScreenEvent.KeyPressed.Pre(screen,256,0,0);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);if(!event.isCanceled())throw new IllegalStateException("Escape was not handled by the input pipeline");}
 static void cancelDrag(Screen screen){escape(screen);if(net.minecraft.client.Minecraft.getInstance().screen!=screen)throw new IllegalStateException("Escape closed the screen during a drag");}
 private UiInputHarness(){}
}
