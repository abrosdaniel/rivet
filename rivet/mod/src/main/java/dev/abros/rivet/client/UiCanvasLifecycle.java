package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class UiCanvasLifecycle {
    @SubscribeEvent public static void prepare(RenderFrameEvent.Pre event){
        var target=Minecraft.getInstance().getMainRenderTarget();
        if(!target.isStencilEnabled())target.enableStencil();
    }
    @SubscribeEvent public static void finish(RenderFrameEvent.Post event){UiCanvas.endFrame();}
}
