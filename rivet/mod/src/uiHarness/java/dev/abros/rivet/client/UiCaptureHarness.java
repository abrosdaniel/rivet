package dev.abros.rivet.client;

/** Local screenshots are optional; CI still renders and checks each UI scenario. */
final class UiCaptureHarness {
 static void grab(java.io.File directory, String name, com.mojang.blaze3d.pipeline.RenderTarget target,
                  java.util.function.Consumer<net.minecraft.network.chat.Component> callback) {
  if (!"false".equalsIgnoreCase(System.getenv("RIVET_UI_SCREENSHOTS")))
   net.minecraft.client.Screenshot.grab(directory, name, target, callback);
 }
 private UiCaptureHarness() {}
}
