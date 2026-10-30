package dev.abros.rivet.client;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
/** One layout per widget; no global retention of private page text. */
final class UiTitleLayout {
 private static volatile long revision;
 private Font font;private Component text;private int width;private long version=-1;
 private List<FormattedCharSequence> lines=List.of();
 static void reload(){revision++;}
 List<FormattedCharSequence> lines(Font font,Component text,int width){
  if(this.font!=font||this.width!=width||version!=revision||!text.equals(this.text)){
   lines=List.copyOf(font.split(text,width));this.font=font;this.text=text.copy();this.width=width;version=revision;
  }
  return lines;
 }
}
