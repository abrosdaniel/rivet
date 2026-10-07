package dev.abros.rivet.client;
import net.minecraft.client.gui.*;
final class UiParagraph {
 static void draw(GuiGraphics g,Font font,String text,int x,int y,int ink){int n=0;for(String line:text.split("\n",-1))Ui.text(g,font,line,x,y+n++*12,ink,false);}
 private UiParagraph(){}
}
