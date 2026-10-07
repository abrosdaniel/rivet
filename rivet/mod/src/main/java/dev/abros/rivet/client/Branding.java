package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
public final class Branding {

 public static void icon(GuiGraphics graphics,int x,int y,int size){
  int pixels=(int)Math.ceil(size*Minecraft.getInstance().getWindow().getGuiScale());
  int resolution=pixels<=24?24:pixels<=32?32:pixels<=64?64:256;
  var icon=ResourceLocation.fromNamespaceAndPath("rivet","textures/gui/icon_"+resolution+".png");
  graphics.blit(icon,x,y,size,size,0f,0f,resolution,resolution,resolution,resolution);
 }
}
