package dev.abros.rivet.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Real widgets in both languages, including language changes without restarting Minecraft. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class LocalizationUiHarness {
    private static List<VisualMatrixHarness.Frame> frames;
    private static int step,language,checked;
    private static long next;
    private static boolean started,done;
    private static Language original;
    private static String originalLanguage;
    private static final int[] SECTIONS={8,0,1,2,3,4};

    @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if(System.getenv("RIVET_LOCALIZATION_UI")==null||done)return;
        var mc=Minecraft.getInstance();
        try {
            if(!started){
                if(!(mc.screen instanceof TitleScreen))return;
                started=true;original=Language.getInstance();originalLanguage=mc.getLanguageManager().getSelected();
                CommunityUiHarness.open("normal");frames=VisualMatrixHarness.samples(14,0,6);
                selectLanguage(mc,"ru_ru");next=System.currentTimeMillis()+300;return;
            }
            if(System.currentTimeMillis()<next)return;
            next=System.currentTimeMillis()+100;
            if(step>0)verify(mc);
            if(step==frames.size()){
                if(language++==0){step=0;selectLanguage(mc,"en_us");}
                else {
                    // A second switch verifies that cached labels are not stuck in English.
                    selectLanguage(mc,"ru_ru");
                    if(!MapSettingsScreen.CATEGORIES.getFirst().equals(Client.text("ui.rendering_b735d754")))throw new IllegalStateException("Stale map categories");
                    System.out.println("RIVET_LOCALIZATION_UI_OK states="+checked+" RU/EN, GUI 1/2/3, default/light/high contrast, all theme samples");
                    finish(mc);return;
                }
            }
            var frame=frames.get(step++);frame.apply();int scene=frame.scene();
            if(scene<6)mc.setScreen(new MapSettingsScreen(null,scene));
            else if(scene<12)mc.setScreen(UiSettingsShell.open(null,SECTIONS[scene-6]));
            else if(scene==12)mc.setScreen(new SettingsSearchScreen(null,true));
            else mc.setScreen(new ReportScreen(null));
        } catch(Throwable failure){
            System.out.println("RIVET_LOCALIZATION_UI_FAILED language="+language+" step="+step);failure.printStackTrace();finish(mc);
        }
    }

    private static void selectLanguage(Minecraft mc,String locale){
        mc.getLanguageManager().setSelected(locale);
        Language.inject(ClientLanguage.loadFrom(mc.getResourceManager(),List.of("en_us",locale),false));
        String label=MapSettingsScreen.CATEGORIES.getFirst();
        if(!label.equals(Client.text("ui.rendering_b735d754")))throw new IllegalStateException("Stale category labels");
        if(locale.equals("en_us")&&!label.equals("Rendering"))throw new IllegalStateException("English catalog not loaded: "+label);
    }

    private static void verify(Minecraft mc){
        UiGeometryHarness.verify(mc.screen);
        for(var child:mc.screen.children())if(child instanceof AbstractWidget widget&&widget.visible){
            String label=widget.getMessage().getString();
            if(label.contains("rivet.ui.")||label.contains("rivet.core."))throw new IllegalStateException("Missing translation: "+label);
            if(language==1&&label.matches("(?s).*[А-Яа-яЁё].*"))throw new IllegalStateException("Russian UI label in English: "+label);
        }
        checked++;
    }

    private static void finish(Minecraft mc){
        done=true;if(original!=null)Language.inject(original);
        if(originalLanguage!=null)mc.getLanguageManager().setSelected(originalLanguage);
        mc.stop();
    }
}
