package dev.abros.rivet.client;

import io.github.humbleui.skija.*;
import java.util.*;

/** CPU-only metrics use the same face, size and style as the Skia renderer. */
final class UiTextMetrics {
    static {SkiaNative.load();}
    private static Typeface face;
    private static Font font;
    private static final Map<Long,Float> advances=new LinkedHashMap<>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<Long,Float> e){return size()>4096;}};
    private static Font font(){
        if(font==null)try(var in=UiTextMetrics.class.getResourceAsStream("/assets/rivet/font/notosans.ttf")){
            try(var data=Data.makeFromBytes(Objects.requireNonNull(in).readAllBytes())){face=Objects.requireNonNull(FontMgr.getDefault().makeFromData(data));}
            font=new Font(face,10).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load Rivet font metrics",e);}
        return font;
    }
    static boolean supports(int point){return font().getUTF32Glyph(point)!=0;}
    static float advance(int point,boolean bold,boolean italic){long key=((long)point<<2)|(bold?1:0)|(italic?2:0);return advances.computeIfAbsent(key,k->font().setEmboldened(bold).setSkewX(italic?-.2f:0).measureTextWidth(new String(Character.toChars(point))));}
    static void close(){advances.clear();if(font!=null){font.close();face.close();font=null;face=null;}}
}
