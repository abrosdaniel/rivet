package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
/** Resize the real native window; validate layout and editing state at every breakpoint. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class AdaptiveUiHarness {
 private static final int[][] SIZES={{320,240},{480,270},{560,320},{800,450},{1000,300},{1200,600}};
 private static final int SCENES=33,TOTAL=SIZES.length*SCENES;
 private static int step=-1,phase;private static long at;private static Screen editing;private static String draft;private static int scroll;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
  String output=System.getenv("RIVET_ADAPTIVE_UI");if(output==null||step>TOTAL)return;var mc=Minecraft.getInstance();
  if(step<0){if(!(mc.screen instanceof TitleScreen))return;CommunityUiHarness.open("normal");var original=ServerMenuClient.previewTransport;ServerMenuClient.previewTransport=q->{if(dev.abros.rivet.core.Json.opt(q,"action","").equals("adminDashboard"))NextUiHarness.request(q);else original.accept(q);};UiPalette.preview(0);new java.io.File(output).mkdirs();System.out.println("RIVET_ADAPTIVE_UI_PLAN: frames="+TOTAL);step=0;}
  if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+350;
  try{
   if(phase==0){if(step==TOTAL){System.out.println("RIVET_ADAPTIVE_UI_OK: frames="+TOTAL+", real window resize, six aspect ratios, stable drafts and focus");step++;mc.stop();return;}
    int[] size=SIZES[step/SCENES];int[] nw={0},nh={0};org.lwjgl.glfw.GLFW.glfwGetWindowSize(mc.getWindow().getWindow(),nw,nh);int scale=Math.max(1,Math.round(mc.getWindow().getWidth()/(float)Math.max(1,nw[0])));mc.options.guiScale().set(scale);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),size[0],size[1]);phase=1;return;}
   if(phase==1){mc.resizeDisplay();int scene=step%SCENES;switch(scene){
    case 0->mc.setScreen(new AccessibilityScreen(null));case 1->mc.setScreen(new SocialSettingsScreen(null,1));case 2->mc.setScreen(new HudSettingsScreen(null,0));case 3->mc.setScreen(new NavigationSettingsScreen(null));case 4->mc.setScreen(new CommunityScreen(null,"home",""));case 5->FeatureListScreen.open(null,"players");case 6->mc.setScreen(new TaskScreen(null,"",""));
    case 7->{mc.setScreen(new TaskScreen(null,"",""));}
    case 8->mc.setScreen(new CommunityScreen(null,"groups",""));case 9->mc.setScreen(new CommunityScreen(null,"board",""));case 10->mc.setScreen(new ServerInfoScreen(null));case 11->mc.setScreen(new ServerMenuScreen(null,"admin"));case 12->mc.setScreen(new HomeWidgetsScreen(null));case 13->mc.setScreen(new UiKitShowcaseScreen(null));case 14->mc.setScreen(new ReportQueueScreen(null,"new"));case 15->mc.setScreen(new GlobalSearchScreen(null));case 16->mc.setScreen(new HubScreen(null));case 17->mc.setScreen(new RegistryScreen(null,java.util.List.of()));case 18->mc.setScreen(new SkinsScreen(null));case 19->mc.setScreen(new UiThemePicker(null));case 20->mc.setScreen(new SparkDiagnosticsScreen(null));case 21->mc.setScreen(new ChoicePopup(new SocialSettingsScreen(null,1),"Раздел настроек",UiSettingsShell.SECTIONS,n->{}));case 22->{mc.setScreen(new UiKitShowcaseScreen(null));((ScrollScreen)mc.screen).revealRow(7);mc.screen.resize(mc,mc.screen.width,mc.screen.height);}case 23->{var skin=new SkinsScreen(null);try{var field=SkinsScreen.class.getDeclaredField("draft");field.setAccessible(true);field.set(skin,new byte[1]);}catch(Exception ex){throw new IllegalStateException(ex);}mc.setScreen(skin);if(skin.width<640||skin.height<340)press("Предпросмотр");}
    case 24->mc.setScreen(new SettingsSearchScreen(null));
    case 25->mc.setScreen(new PersonalProfileScreen(null));
    case 26->mc.setScreen(new PlayerActionsScreen(null,player(),new com.google.gson.JsonArray()));
    case 27->{var parent=new CommunityScreen(null,"board","");var preset=new com.google.gson.JsonObject();preset.addProperty("title","Черновик объявления");preset.addProperty("description","Описание объявления");mc.setScreen(new CommunityForm(parent,"Новое объявление",java.util.List.of(new CommunityScreen.Field("title","Название",100),new CommunityScreen.Field("description","Описание",1500)),preset,j->{}));}
    case 28->{mc.setScreen(new PlayerActionsScreen(null,player(),new com.google.gson.JsonArray()));press("Действия");}
    case 29->{mc.setScreen(new ServerMenuScreen(null,"admin"));if(mc.screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().contains("Диагностика")))press("Обзор");press("Диагностика");}
    case 30->{mc.setScreen(new UiKitShowcaseScreen(null));((ScrollScreen)mc.screen).revealRow(8);mc.screen.resize(mc,mc.screen.width,mc.screen.height);}
    case 31,32->{var preset=new com.google.gson.JsonObject();preset.addProperty("title",step%SCENES==32?"":"Черновик задачи");preset.addProperty("description","Описание задачи");mc.setScreen(new TaskEditScreen(new TaskScreen(null,"",""),"Редактировать задачу",java.util.List.of(new CommunityScreen.Field("title","Название",100),new CommunityScreen.Field("description","Описание",1500)),preset,j->{}));if(step%SCENES==32)press("Сохранить");}
   }
    phase=2;return;}
   if(phase==2&&(step%SCENES==8||step%SCENES==9)){for(var c:mc.screen.children())if(c instanceof CommunityCard card){card.onPress();phase=3;return;}}
   if(phase==2&&step%SCENES==31){var form=(ScrollScreen)mc.screen;form.revealRow(2);form.rebuildWidgets();if(mc.screen.children().stream().noneMatch(c->c instanceof UiMultiLineEditBox))throw new IllegalStateException("Description is inaccessible");phase=3;return;}
   if(phase==2&&step%SCENES==7){press("Новая задача");for(var c:mc.screen.children())if(c instanceof EditBox e&&e.getMessage().getString().equals("Название задачи")){draft="Черновик при изменении окна "+step;e.setValue(draft);e.setCursorPosition(4);mc.screen.setFocused(e);break;}editing=mc.screen;scroll=editing instanceof ScrollScreen s?s.firstRow:0;
    for(var c:editing.children())if(c instanceof UiMultiLineEditBox e){e.setValue("Описание задачи с выделенным текстом и переносами строк\nВторая строка");e.restoreEditing(new UiMultiLineEditBox.EditingState(9,2,0));editing.setFocused(e);break;}
    editing.resize(mc,editing.width+24,editing.height+20);
    if(!(editing.getFocused() instanceof UiMultiLineEditBox multi)||multi.editingState().cursor()!=9||multi.editingState().anchor()!=2||!multi.getValue().contains("Вторая строка"))throw new IllegalStateException("Resize lost multiline cursor/selection");
    for(var c:editing.children())if(c instanceof EditBox e&&e.getMessage().getString().equals("Название задачи")){editing.setFocused(e);e.setCursorPosition(4);break;}
    mc.screen.resize(mc,mc.screen.width+24,mc.screen.height+20);mc.screen.resize(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());phase=3;return;}
   verify();if(step%SCENES==7){if(mc.screen!=editing||!(mc.screen.getFocused() instanceof EditBox e)||!e.getValue().equals(draft)||e.getCursorPosition()!=4||editing instanceof ScrollScreen s&&s.firstRow!=scroll)throw new IllegalStateException("Resize lost editing state: focus="+mc.screen.getFocused()+", expected="+draft+", firstRow="+(editing instanceof ScrollScreen s?s.firstRow:-1));}
   net.minecraft.client.Screenshot.grab(new java.io.File(output),String.format("adaptive-%03d.png",step+1),mc.getMainRenderTarget(),m->{});System.out.println("RIVET_ADAPTIVE_UI_FRAME: "+(step+1)+" "+mc.screen.getClass().getSimpleName()+" "+mc.screen.width+"x"+mc.screen.height);step++;phase=0;
  }catch(Throwable ex){System.out.println("RIVET_ADAPTIVE_UI_FAILED: step="+step+", phase="+phase);ex.printStackTrace();step=TOTAL+1;mc.stop();}
 }
 private static com.google.gson.JsonObject player(){var p=new com.google.gson.JsonObject();p.addProperty("uuid","00000000-0000-0000-0000-000000000001");p.addProperty("name","ABR0Sxd");p.addProperty("prefix","&c[Root]");p.addProperty("suffix","&9@abrosdaniel");p.addProperty("online",true);return p;}
 private static void press(String text){for(var c:Minecraft.getInstance().screen.children())if(c instanceof Button b&&b.getMessage().getString().contains(text)){b.onPress();return;}throw new IllegalStateException("Missing action: "+text);}
 private static void verify(){var screen=Minecraft.getInstance().screen;UiGeometryHarness.verify(screen);
  if(MenuSidebar.sections().contains("profile")||MenuSidebar.sections().contains("settings"))throw new IllegalStateException("Unapproved root navigation entries returned");
  if(screen instanceof PersonalProfileScreen||screen instanceof PlayerActionsScreen||screen instanceof CommunityForm||screen instanceof TaskEditScreen||UiSettingsShell.owns(screen)){
   if(screen.children().stream().anyMatch(c->c instanceof SidebarButton button&&MenuSidebar.sections().stream().map(CommunityScreen::name).anyMatch(n->n.equals(button.getMessage().getString()))))throw new IllegalStateException("Modal was turned into a page");
  }
  var widgets=screen.children().stream().filter(c->c instanceof AbstractWidget).map(c->(AbstractWidget)c).filter(w->w.visible).toList();
  for(var w:widgets)if(w.getWidth()<1||w.getHeight()<1||w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)throw new IllegalStateException("Escaped widget: "+w.getMessage().getString());
  for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getX()+b.getWidth()&&b.getX()<a.getX()+a.getWidth()&&a.getY()<b.getY()+b.getHeight()&&b.getY()<a.getY()+a.getHeight())throw new IllegalStateException("Overlap: "+a.getMessage().getString()+" / "+b.getMessage().getString());}
 }
}
