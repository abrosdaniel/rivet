package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Behavioural regression flow for the project audit; production widgets, fixture transport. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class AuditFixesUiHarness {
 private static int stage,scale=1,settingsChecked,reports;private static long next,deadline;
 private static boolean started,done;private static ReportScreen report;
 private static JsonObject submitted;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
  if(System.getenv("RIVET_AUDIT_FIXES_UI")==null||done)return;var mc=Minecraft.getInstance();
  if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;next=System.currentTimeMillis()+1200;deadline=next+90000;}
  if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+150;
  try{if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Audit UI timeout at stage "+stage);
   switch(stage){
    case 0->{CommunityUiHarness.open("normal");mc.options.guiScale().set(scale);mc.resizeDisplay();settings(mc);review(mc);home(mc);scopedAdministration(mc);presets();mc.setScreen(new ReportScreen(null));report=(ReportScreen)mc.screen;for(var c:report.children())if(c instanceof MultiLineEditBox edit)edit.setValue("Проверка обращения "+scale);var original=ServerMenuClient.previewTransport;ServerMenuClient.previewTransport=q->{if(Json.opt(q,"action","").equals("report")){reports++;submitted=q.deepCopy();var reply=new JsonObject();reply.addProperty("request",Json.str(q,"request"));reply.addProperty("kind","result");report.receiveCommunity(reply);}else original.accept(q);};press(Client.tr("server.reviewReport").getString());check(reports==scale-1,"Report sent before review");stage=1;}
    case 1->{if(!(mc.screen instanceof ReviewScreen))return;check(reports==scale-1,"Report sent before confirmation");mc.screen.onClose();check(mc.screen==report,"Cancel lost report draft");check(report.children().stream().anyMatch(c->c instanceof MultiLineEditBox e&&e.getValue().equals("Проверка обращения "+scale)),"Report draft lost");press(Client.tr("server.reviewReport").getString());stage=2;}
    case 2->{if(!(mc.screen instanceof ReviewScreen))return;press(Client.tr("server.send").getString());check(reports==scale,"Confirmation did not send exactly once");check(Json.str(submitted,"message").equals("Проверка обращения "+scale)&&submitted.has("audit")&&submitted.has("coreVersion")&&submitted.has("packHash"),"Reviewed report payload differs");helpLinks(mc);stage=3;}
    case 3->{System.out.println("RIVET_AUDIT_FIXES_SCALE_OK GUI="+scale);if(scale++<3){stage=0;break;}checkLanguage(mc);System.out.println("RIVET_AUDIT_FIXES_UI_OK: frames=12 settings="+settingsChecked+", GUI 1/2/3, reorder, tasks disabled, profile actions, statistics, presets, review scrolling, report cancel/confirm, help links, scoped administration, RU/EN actions");done=true;mc.stop();}
   }
  }catch(Throwable failure){System.out.println("RIVET_AUDIT_FIXES_UI_FAILED stage="+stage+" scale="+scale);failure.printStackTrace();done=true;mc.stop();}
 }
 private static void settings(Minecraft mc)throws Exception{
  var saved=new ArrayList<>(HudSettings.INSTANCE.order);Collections.reverse(HudSettings.INSTANCE.order);
  try{for(var entry:SettingsCatalog.entries()){if(entry.section()==6)continue;var screen=UiSettingsShell.open(null,entry.section());mc.setScreen(screen);((SettingsTarget)screen).revealSetting(entry.id());var scroll=(ScrollScreen)screen;check(scroll.scrollLayout()!=null,"Missing settings viewport: "+entry);int row;
    if(screen instanceof AccessibilityScreen||screen instanceof HudSettingsScreen){var field=screen.getClass().getDeclaredField(screen instanceof AccessibilityScreen?"settings":"rows");field.setAccessible(true);var rows=(List<?>)field.get(screen);row=-1;for(int n=0;n<rows.size();n++){var key=rows.get(n).getClass().getDeclaredMethod("id");key.setAccessible(true);if(key.invoke(rows.get(n)).equals(entry.id())){row=n;break;}}}else row=SettingsCatalog.row(entry.section(),entry.id());
    check(row>=scroll.firstRow&&row<scroll.firstRow+scroll.visibleRows,"Search misses target: "+entry+" row="+row+" first="+scroll.firstRow);UiGeometryHarness.verify(screen);settingsChecked++;
   }}finally{HudSettings.INSTANCE.order.clear();HudSettings.INSTANCE.order.addAll(saved);}
  check(SettingsSearchScreen.search("Детали оформления").isEmpty(),"Removed setting returned");mc.setScreen(new SettingsSearchScreen(null));check(mc.screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().equals("Сбросить раздел")),"Search pretends to reset settings");
 }
 private static void review(Minecraft mc){var screen=new ReviewScreen(null,Component.literal("Проверка"),"mods/example.jar: заменить файл\n".repeat(100),Component.literal("Установить"),()->{},true);mc.setScreen(screen);var viewport=screen.scrollLayout().viewport();check(screen.mouseScrolled(viewport.x()+viewport.width()/2,viewport.y()+viewport.height()/2,0,-4)&&screen.firstRow>0,"Review wheel scroll failed");screen.keyPressed(267,0,0);check(screen.firstRow>12,"Review PageDown failed");var track=screen.scrollLayout().track();screen.mouseClicked(track.x()+2,track.bottom()-5,0);screen.mouseDragged(track.x()+2,track.bottom()-3,0,0,2);screen.mouseReleased(track.x()+2,track.bottom()-3,0);check(screen.firstRow>12,"Review scrollbar failed");UiGeometryHarness.verify(screen);}
 private static void home(Minecraft mc)throws Exception{
  var modules=new JsonObject();modules.addProperty("tasks",false);ServerMenuClient.state.add("modules",modules);ServerMenuClient.state.remove("sessionSeconds");var screen=new CommunityScreen(null,"home","");mc.setScreen(screen);press("Поиск по серверу");check(mc.screen instanceof GlobalSearchScreen,"Global search unavailable with tasks disabled");mc.setScreen(screen);press("⋯");check(mc.screen instanceof ChoicePopup,"Profile menu changed by width");var popup=(ChoicePopup)mc.screen;var labels=ChoicePopup.class.getDeclaredField("labels");labels.setAccessible(true);var values=(List<?>)labels.get(popup);check(values.contains("Организаторы событий")&&values.contains("Общедоступные места")&&values.contains("Карта объединения")&&values.contains("Виджеты главной"),"Profile actions missing");
  var data=new JsonObject();data.add("entries",new JsonArray());data.add("selfStatistics",new JsonObject());check(!ProfilePanel.playTimeVisible(data)&&ProfilePanel.actionsTop(data)==90,"Disabled statistics still reserve a row");
 }
 private static void scopedAdministration(Minecraft mc)throws Exception{
  var saved=ServerMenuClient.state.deepCopy();try{CommunityUiHarness.open("normal");var screen=new ServerMenuScreen(null,"admin");mc.setScreen(screen);CommunityUiHarness.adminTab("Сервер");ServerMenuClient.state.addProperty("admin",false);ServerMenuClient.state.addProperty("staff",true);var rights=new JsonObject();rights.addProperty("rivet.reports",true);ServerMenuClient.state.add("capabilities",rights);screen.refreshPermissions();var group=ServerMenuScreen.class.getDeclaredField("adminGroup");group.setAccessible(true);check(group.get(screen).equals("overview"),"Revoked server permissions retained active tab");var tabs=screen.children().stream().filter(c->c instanceof TabButton).map(c->((TabButton)c).getMessage().getString()).toList();check(tabs.equals(List.of("Обзор","Обращения")),"Limited moderator sees empty privileged tabs: "+tabs);UiGeometryHarness.verify(screen);}finally{ServerMenuClient.state=saved;}
 }
 private static void presets()throws Exception{var contrast=AccessibilityScreen.class.getDeclaredField("contrast");var opaque=AccessibilityScreen.class.getDeclaredField("opaque");contrast.setAccessible(true);opaque.setAccessible(true);contrast.setBoolean(null,true);opaque.setBoolean(null,true);for(int preset=0;preset<3;preset++){UiPresentationProfiles.apply(preset);check(AccessibilityScreen.highContrast()&&opaque.getBoolean(null),"Preset reset accessibility");}contrast.setBoolean(null,false);opaque.setBoolean(null,false);AccessibilityScreen.applyProfile(0);}
 private static void helpLinks(Minecraft mc){
  CommunityUiHarness.open("normal");check(!MenuSidebar.sections().contains("info"),"Removed server page remains in navigation");
  ServerMenuClient.state.addProperty("hasLinks",false);var empty=new ServerMenuScreen(null,"help");mc.setScreen(empty);check(empty.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().equals(Client.tr("server.links").getString())),"Empty server links shown");UiGeometryHarness.verify(empty);
  ServerMenuClient.state.addProperty("hasLinks",true);empty.tick();check(empty.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().equals(Client.tr("server.links").getString())),"Applied menu links not shown live");ServerMenuClient.state.addProperty("hasLinks",false);empty.tick();check(empty.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().equals(Client.tr("server.links").getString())),"Removed menu links remain live");ServerMenuClient.state.addProperty("hasLinks",true);var help=new ServerMenuScreen(null,"help");mc.setScreen(help);UiGeometryHarness.verify(help);press(Client.tr("server.links").getString());check(mc.screen instanceof FeatureListScreen list&&list.kind.equals("links"),"Help links did not open");mc.screen.onClose();check(mc.screen==help,"Links lost Help parent");
 }
 private static void checkLanguage(Minecraft mc){for(String language:List.of("ru_ru","en_us")){var old=mc.getLanguageManager().getSelected();mc.getLanguageManager().setSelected(language);net.minecraft.client.resources.language.ClientLanguage loaded=net.minecraft.client.resources.language.ClientLanguage.loadFrom(mc.getResourceManager(),List.of("en_us",language),false);net.minecraft.locale.Language.inject(loaded);var screen=new TextScreen(null,Component.literal("Test"),"Text");mc.setScreen(screen);String expected=Client.tr("close").getString();check(screen.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().equals(expected)),"Semantic action bypasses translation");mc.getLanguageManager().setSelected(old);}}
 private static void press(String label){var screen=Minecraft.getInstance().screen;for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label)){button.onPress();return;}throw new IllegalStateException("Button missing: "+label+" in "+screen.getClass().getSimpleName());}
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
