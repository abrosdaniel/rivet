package dev.abros.rivet.client;

import dev.abros.rivet.Rivet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CoreVersionButton extends Button {
    CoreVersionButton(int x,int y,Screen parent){
        super(x,y,80,20,Component.literal(Rivet.VERSION+"…"),button->
                net.minecraft.client.Minecraft.getInstance().setScreen(new CoreVersionsPopup(parent)),DEFAULT_NARRATION);
    }
    @Override protected void renderWidget(GuiGraphics graphics,int x,int y,float delta){
        boolean update=Client.offeredUpdate!=null;
        setMessage(Component.literal(Rivet.VERSION+" ").append(Component.literal(update?"↑":"…").withStyle(s->s.withColor(update?UiPalette.color(0xFF3333):0xFFFFFF))));
        setTooltip(update?net.minecraft.client.gui.components.Tooltip.create(Component.literal("Доступна новая версия Rivet")):null);
        super.renderWidget(graphics,x,y,delta);
        if(update)graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),UiPalette.color(0xFF29033F));
    }
}
