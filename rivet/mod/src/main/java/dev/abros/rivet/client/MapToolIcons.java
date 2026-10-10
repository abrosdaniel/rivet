package dev.abros.rivet.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Shared 16px tools adapted from Nikoichu's CC0 1-bit Pixel Icons. */
final class MapToolIcons {
    static final List<String> KEYS = List.of("target","zoom_in","zoom_out","dimension","plus","clear","edit","save","delete","search","stop","navigate","teleport","markers","settings","layers","surface","cave","grid","ruler","territory","select","pan","fullscreen","compass","reset","visibility","hide","lock","unlock","share","import","export","copy","paste","undo","redo","download","upload","refresh","filter","sort","info","help","warning","screenshot","player","players","deaths","waystone");
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("rivet", "textures/gui/map_tools.png");

    static void draw(GuiGraphics g, String key, int x, int y, int colour) {
        int index = KEYS.indexOf(key);
        if (index < 0) return;
        if ((colour >>> 24) == 0) colour |= 0xff000000;
        if (UiPalette.light()) colour = 0xffe8edf2;
        g.flush();
        float[] previous = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f, (colour >>> 24) / 255f);
            g.blit(ATLAS, x, y, (index % 8) * 16, (index / 8) * 16, 16, 16, 128, 112);
            g.flush();
        } finally {
            RenderSystem.setShaderColor(previous[0], previous[1], previous[2], previous[3]);
        }
    }

    private MapToolIcons() {}
}
