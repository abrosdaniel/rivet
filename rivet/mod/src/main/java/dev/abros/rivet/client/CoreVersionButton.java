package dev.abros.rivet.client;

import dev.abros.rivet.Rivet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native title-menu icon, aligned with Minecraft's multiplayer action. */
final class CoreVersionButton extends Button {
    CoreVersionButton(int x,int y,Screen parent){
        super(x,y,20,20,Component.empty(),button->
                net.minecraft.client.Minecraft.getInstance().setScreen(Client.pending.isEmpty()?new CoreVersionsPopup(parent):new RestartScreen(parent,Client.pending)),DEFAULT_NARRATION);
        refreshTooltip();
    }
    Component description(){
        var text=Component.literal("Rivet "+Rivet.VERSION);
        if(Client.offeredUpdate!=null)text.append("\n").append(Client.tr("update.availableVersion",Client.offeredUpdate.version()));
        return text;
    }
    private Component lastDescription;
    private void refreshTooltip(){
        var description=description();
        if(!description.equals(lastDescription)){lastDescription=description;setTooltip(Tooltip.create(description));}
    }
    @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage(){return description().copy();}
    @Override protected void renderWidget(GuiGraphics graphics,int x,int y,float delta){
        refreshTooltip();
        super.renderWidget(graphics,x,y,delta);
        Branding.icon(graphics,getX()+3,getY()+3,14);
        if(Client.offeredUpdate!=null){
            float brightness=AccessibilityScreen.animations()?(float)(0.72+0.28*Math.cos((System.nanoTime()%2_400_000_000L)/2_400_000_000.0*Math.PI*2)):1;
            int gold=0xFF000000|((int)(226*brightness)<<16)|((int)(190*brightness)<<8)|(int)(117*brightness);
            graphics.renderOutline(getX(),getY(),getWidth(),getHeight(),gold);
        }
    }
}
