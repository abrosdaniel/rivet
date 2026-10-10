package dev.abros.rivet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Native-resolution pixel navigator, with a dark outline and a rear notch. */
final class MapPlayerArrow {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("rivet", "textures/gui/map_player.png");

    static void draw(GuiGraphics g, int x, int y, float yaw) {
        draw(g, x, y, yaw, 16);
    }

    static void minimap(GuiGraphics g, int x, int y, float yaw, int side) {
        draw(g, x, y, yaw, Math.clamp(Math.round(side / 9f), 8, 16));
    }

    private static void draw(GuiGraphics g, int x, int y, float yaw, int size) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(new org.joml.Quaternionf().rotationZ((float) Math.toRadians(yaw + 180)));
        g.pose().scale(size / 24f, size / 24f, 1);
        g.blit(TEXTURE, -12, -12, 0, 0, 24, 24, 24, 24);
        g.pose().popPose();
    }

    private MapPlayerArrow() {}
}
