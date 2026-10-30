package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
final class UiTextLayoutCheck {
 static void run(){
  var font=Minecraft.getInstance().font;var text=Component.literal("Длинное название события — Long community event title ".repeat(3)).withStyle(net.minecraft.ChatFormatting.BOLD);var cache=new UiTitleLayout();
  var first=cache.lines(font,text,180);if(cache.lines(font,text,180)!=first)throw new IllegalStateException("Title layout was not reused");
  var narrow=cache.lines(font,text,80);if(narrow.size()<=first.size())throw new IllegalStateException("Title width did not invalidate layout");
  UiTitleLayout.reload();if(cache.lines(font,text,80)==narrow)throw new IllegalStateException("Resource reload kept stale layout");
  var changed=Component.literal("New title");if(cache.lines(font,changed,180).size()!=font.split(changed,180).size())throw new IllegalStateException("Changed title not applied");
  for(int i=0;i<2000;i++){font.split(text,180);cache.lines(font,text,180);}
  long before=System.nanoTime();for(int i=0;i<10000;i++)font.split(text,180);long direct=System.nanoTime()-before;
  before=System.nanoTime();for(int i=0;i<10000;i++)cache.lines(font,text,180);long cached=System.nanoTime()-before;
  System.out.println("RIVET_TEXT_LAYOUT_OK iterations=10000 directMs="+direct/1e6+" cachedMs="+cached/1e6+" resize=true reload=true");
 }
}
