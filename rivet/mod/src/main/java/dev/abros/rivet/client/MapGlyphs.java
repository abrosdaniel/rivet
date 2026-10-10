package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
/** Multicolour sprites; source attribution is recorded under licenses/. Marker colour belongs to its frame, never tints artwork. */
final class MapGlyphs {
 static final List<String> MARKERS=List.of("pin","pin_blue","pin_green","pin_yellow","pin_purple","pin_cyan","pin_pink","pin_orange","home","castle","mine","portal","eye","skull","sword","star","flag","chest","diamond","emerald","cross","tree","mountain","wheat","anchor","boat","bed","campfire","heart","question","exclamation","tower","gate","well","barrel","cart","ladder","bridge","stairs","door","locked_door","key","pickaxe","shovel","bow","arrow","dagger","axe","hammer","spear","shield","helmet","armour","potion","healing","mana","fire_potion","coins","bag","book","scroll","crystal","bones","mushroom","flower","pine","bush","rock","shop","guard","ruins","trophy","torch","candle","lantern","cauldron","anvil","tent","leaf","green_flag","bucket","mortar","cup","ruby","amethyst","sapphire","jade","topaz","ring","necklace","feather","bowl","watchtower","statue","stone_arch","stone_tower","fortress","village","farm","waterfall","fishing","minecart","logpile");

 static final List<String> CHOICES=java.util.stream.Stream.concat(java.util.stream.Stream.of("none"),MARKERS.stream().filter(key->!key.equals("skull"))).toList();
 private record Bounds(int x,int y,int width,int height){}
 private record LetterBounds(float left,float right,float top,float bottom){}
 private static final java.util.Map<com.mojang.blaze3d.font.GlyphInfo,LetterBounds> letters=new java.util.IdentityHashMap<>();
 private static final java.util.Map<String,Bounds> bounds=new java.util.HashMap<>();
 static void clear(){bounds.clear();letters.clear();}
 static void initial(GuiGraphics g,String name,int cx,int cy,int size,int color){
  String initial=dev.abros.rivet.core.map.MapMarker.initial(name);if(initial.isEmpty())return;
  var font=net.minecraft.client.Minecraft.getInstance().font;
  var set=((dev.abros.rivet.mixin.MapFontAccessor)(Object)font).rivet$fontSet(net.minecraft.network.chat.Style.DEFAULT_FONT);
  float left=Float.POSITIVE_INFINITY,right=Float.NEGATIVE_INFINITY,top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY,cursor=0;
  for(int cp:initial.codePoints().toArray()){
   var info=set.getGlyphInfo(cp,false);var glyph=(dev.abros.rivet.mixin.MapGlyphAccessor)(Object)set.getGlyph(cp);
   var visible=letters.computeIfAbsent(info,ignored->letterBounds(info,glyph));
   if(visible.right()>visible.left()&&visible.bottom()>visible.top()){
    left=Math.min(left,cursor+visible.left());right=Math.max(right,cursor+visible.right());top=Math.min(top,visible.top());bottom=Math.max(bottom,visible.bottom());
   }
   cursor+=info.getAdvance(false);
  }
  if(!Float.isFinite(left))return;
  float scale=(Math.max(1,size-2))/Math.max(right-left,bottom-top);
  g.pose().pushPose();g.pose().translate(cx,cy,0);g.pose().scale(scale,scale,1);g.pose().translate(-(left+right)/2f,-(top+bottom)/2f,0);g.drawString(font,initial,0,0,color,false);g.pose().popPose();
 }
 private static LetterBounds letterBounds(com.mojang.blaze3d.font.GlyphInfo info,dev.abros.rivet.mixin.MapGlyphAccessor glyph){
  int width=0,height=0,left=Integer.MAX_VALUE,right=-1,top=Integer.MAX_VALUE,bottom=-1;
  if(info instanceof dev.abros.rivet.mixin.MapBitmapGlyphAccessor bitmap){
   width=bitmap.rivet$width();height=bitmap.rivet$height();
   for(int y=0;y<height;y++)for(int x=0;x<width;x++)if((bitmap.rivet$image().getLuminanceOrAlpha(bitmap.rivet$x()+x,bitmap.rivet$y()+y)&255)>10){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
  }else if(info instanceof dev.abros.rivet.mixin.MapUnihexGlyphAccessor bitmap){
   width=bitmap.rivet$right()-bitmap.rivet$left()+1;height=16;
   for(int y=0;y<height;y++)for(int x=0;x<width;x++){int bit=31-bitmap.rivet$left()-x;if(bit>=0&&bit<32&&(bitmap.rivet$contents().line(y)&1<<bit)!=0){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}
  }
  if(width>0&&right>=left){float sx=(glyph.rivet$right()-glyph.rivet$left())/width,sy=(glyph.rivet$down()-glyph.rivet$up())/height;return new LetterBounds(glyph.rivet$left()+left*sx,glyph.rivet$left()+(right+1)*sx,glyph.rivet$up()+top*sy,glyph.rivet$up()+(bottom+1)*sy);}
  return new LetterBounds(glyph.rivet$left(),glyph.rivet$right(),glyph.rivet$up(),glyph.rivet$down());
 }
 private static Bounds bounds(String key){
  var ready=bounds.get(key);if(ready!=null)return ready;
  try(var input=net.minecraft.client.Minecraft.getInstance().getResourceManager().open(ATLAS);var image=com.mojang.blaze3d.platform.NativeImage.read(input)){
   for(int i=0;i<MARKERS.size();i++){
    int left=16,right=-1,top=16,bottom=-1;
    for(int y=0;y<16;y++)for(int x=0;x<16;x++)if((image.getPixelRGBA(i%8*16+x,i/8*16+y)>>>24)>10){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
    bounds.put(MARKERS.get(i),right<left?new Bounds(0,0,16,16):new Bounds(left,top,right-left+1,bottom-top+1));
   }
  }catch(java.io.IOException ex){for(String marker:MARKERS)bounds.put(marker,new Bounds(0,0,16,16));}
  return bounds.get(key);
 }
 static void marker(GuiGraphics g,String key,int x,int y,int size){
  int i=MARKERS.indexOf(key);if(i<0)return;var b=bounds(key);float scale=size/(float)Math.max(b.width(),b.height());
  g.pose().pushPose();g.pose().translate(x+(size-b.width()*scale)/2f,y+(size-b.height()*scale)/2f,0);g.pose().scale(scale,scale,1);
  g.blit(ATLAS,0,0,(float)(i%8*16+b.x()),(float)(i/8*16+b.y()),b.width(),b.height(),128,((MARKERS.size()+7)/8)*16);g.pose().popPose();
 }
 private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("rivet","textures/gui/map_icons.png");
 static boolean supports(String icon){return icon.startsWith("map_")&&(icon.equals("map_none")||MARKERS.contains(icon.substring(4))||MapToolIcons.KEYS.contains(icon.substring(4)));}
 static String icon(String key){return (key.equals("none")||MARKERS.contains(key)||MapToolIcons.KEYS.contains(key))?"map_"+key:key;}
 static void draw(GuiGraphics g,String icon,int x,int y,int color){String key=icon.substring(4);if(MapToolIcons.KEYS.contains(key)){MapToolIcons.draw(g,key,x,y,color);return;}marker(g,key,x,y,16);}
 private MapGlyphs(){}
}
