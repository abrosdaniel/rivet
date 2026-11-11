package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.locale.Language;
import dev.abros.rivet.mixin.MapFontAccessor;
import java.util.List;

/** Rivet-owned metrics. Never replaces Minecraft's global font or splitter. */
public final class UiFont extends Font {
    private static UiFont instance;
    private final Font vanilla;
    private final StringSplitter metrics;
    private UiFont(Font vanilla) {
        super(id -> ((MapFontAccessor)vanilla).rivet$fontSet(id), false);
        this.vanilla=vanilla;
        metrics=new StringSplitter(this::advance);
    }
    public static Font get(){Font vanilla=Minecraft.getInstance().font;if(instance==null||instance.vanilla!=vanilla)instance=new UiFont(vanilla);return instance;}
    public static Font of(Font font){return font instanceof UiFont?font:get();}
    public static Font nativeFont(Font font){return font instanceof UiFont ui?ui.vanilla:font;}
    public float advance(int point,Style style){
        if(!style.isObfuscated()&&style.getFont().equals(Style.DEFAULT_FONT)&&UiTextMetrics.supports(point))return UiTextMetrics.advance(point,style.isBold(),style.isItalic());
        return vanilla.getSplitter().stringWidth(FormattedCharSequence.codepoint(point,style));
    }
    @Override public int width(String text){return (int)Math.ceil(metrics.stringWidth(text));}
    @Override public int width(FormattedText text){return (int)Math.ceil(metrics.stringWidth(text));}
    @Override public int width(FormattedCharSequence text){return (int)Math.ceil(metrics.stringWidth(text));}
    @Override public String plainSubstrByWidth(String text,int width,boolean tail){return tail?metrics.plainTailByWidth(text,width,Style.EMPTY):metrics.plainHeadByWidth(text,width,Style.EMPTY);}
    @Override public String plainSubstrByWidth(String text,int width){return plainSubstrByWidth(text,width,false);}
    @Override public FormattedText substrByWidth(FormattedText text,int width){return metrics.headByWidth(text,width,Style.EMPTY);}
    @Override public int wordWrapHeight(String text,int width){return lineHeight*metrics.splitLines(text,width,Style.EMPTY).size();}
    @Override public int wordWrapHeight(FormattedText text,int width){return lineHeight*metrics.splitLines(text,width,Style.EMPTY).size();}
    @Override public List<FormattedCharSequence> split(FormattedText text,int width){return Language.getInstance().getVisualOrder(metrics.splitLines(text,width,Style.EMPTY));}
    @Override public StringSplitter getSplitter(){return metrics;}
}
