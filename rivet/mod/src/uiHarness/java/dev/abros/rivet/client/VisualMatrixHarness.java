package dev.abros.rivet.client;
import java.util.*;
/** Full flows in the default, light and high-contrast profiles; theme samples at GUI 3. */
final class VisualMatrixHarness {
 record Frame(int theme,int scale,boolean contrast,int scene){
  void apply(){UiPalette.preview(theme);AccessibilityScreen.foreground(0xFFFFFF);try{var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,contrast);}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}var mc=net.minecraft.client.Minecraft.getInstance();if(mc.options.guiScale().get()!=scale){mc.options.guiScale().set(scale);mc.resizeDisplay();}NativeUiHarness.verifyContrast();}
 }
 private static int lightTheme(){int selected=UiPalette.selected();try{for(int n=0;n<UiPalette.names().size();n++){UiPalette.preview(n);if(UiPalette.light())return n;}throw new IllegalStateException("No light theme in visual coverage");}finally{UiPalette.preview(selected);}}
 static List<Frame> flows(int scenes,int firstScale){var out=new ArrayList<Frame>();int light=lightTheme();for(int profile=0;profile<3;profile++)for(int scale=firstScale;scale<firstScale+3;scale++)for(int scene=0;scene<scenes;scene++)out.add(new Frame(profile==1?light:0,scale,profile==2,scene));return List.copyOf(out);}
 static List<Frame> samples(int scenes,int...representative){var out=new ArrayList<>(flows(scenes,1));int light=lightTheme();for(int theme=1;theme<UiPalette.names().size();theme++)if(theme!=light)for(int scene:representative){if(scene<0||scene>=scenes)throw new IllegalArgumentException("Invalid representative scene");out.add(new Frame(theme,3,false,scene));}return List.copyOf(out);}
 private VisualMatrixHarness(){}
}
