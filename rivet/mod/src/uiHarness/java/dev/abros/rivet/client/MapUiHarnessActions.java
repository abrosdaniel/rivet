package dev.abros.rivet.client;
/** Open retained secondary tools through the actual canvas context menu. */
final class MapUiHarnessActions {
 static void context(WorldMapScreen map){map.mouseClicked(map.width-60,map.height-60,1);var screen=net.minecraft.client.Minecraft.getInstance().screen;if(!(screen instanceof UiContextPopup popup))throw new IllegalStateException("Map context menu missing");popup.revealRow(100);popup.rebuildWidgets();}
}
