package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
final class AccessibilityScreen extends ScrollScreen implements SettingsTarget {
 private static boolean loaded,contrast,opaque,compact,serverTime,reducedMotion;private static int density=1,motion=1;private final Screen parent;private String error="";private CommunityCard preview;
 AccessibilityScreen(Screen parent){super(Component.literal("Настройки Rivet"));this.parent=parent;load();}
 private static Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/accessibility.json");}
 private static void load(){if(loaded)return;loaded=true;try{if(Files.exists(file())){var j=Json.read(file());contrast=j.has("contrast")&&j.get("contrast").getAsBoolean();opaque=j.has("opaque")&&j.get("opaque").getAsBoolean();compact=j.has("compact")&&j.get("compact").getAsBoolean();reducedMotion=j.has("reducedMotion")&&j.get("reducedMotion").getAsBoolean();serverTime=j.has("serverTime")&&j.get("serverTime").getAsBoolean();density=j.has("density")?Math.max(0,Math.min(2,j.get("density").getAsInt())):compact?0:1;motion=j.has("motion")?Math.max(0,Math.min(2,j.get("motion").getAsInt())):reducedMotion?0:1;}}catch(Exception ignored){}}
 static boolean animations(){load();return motion!=0;}
 static boolean compact(){load();return density==0;}
 static int density(){load();return density;}
 static int cardStride(){load();return new int[]{60,76,90}[density];}
 static int skinStride(){load();return new int[]{36,46,58}[density];}
 static int motionMillis(){load();return motion==2?240:120;}
 static java.time.ZoneId zone(){load();try{return serverTime?java.time.ZoneId.of(Json.opt(ServerMenuClient.state,"timezone",java.time.ZoneId.systemDefault().getId())):java.time.ZoneId.systemDefault();}catch(Exception ex){return java.time.ZoneId.systemDefault();}}
 static boolean highContrast(){load();return contrast;}
 static int background(int color){load();return opaque?color|0xFF000000:color;}
 static int foreground(int color){load();return contrast?(UiPalette.light()?0x24131D:0xFFFFFF):UiPalette.color(color);}

 private UiDialog layout;private final java.util.List<Setting> settings=new java.util.ArrayList<>();
 static void applyProfile(int profile){load();density=profile==0?0:profile==1?1:2;compact=density==0;motion=profile==1?0:1;reducedMotion=motion==0;try{Json.write(file(),java.util.Map.of("contrast",contrast,"opaque",opaque,"compact",compact,"serverTime",serverTime,"reducedMotion",reducedMotion,"density",density,"motion",motion));}catch(Exception ex){Client.failure(ex);}}
 private record Setting(String id,String label,String value,java.util.List<String> choices,int selected,java.util.function.IntConsumer change){}
 private void save(){try{Json.write(file(),java.util.Map.of("contrast",contrast,"opaque",opaque,"compact",compact,"serverTime",serverTime,"reducedMotion",motion==0,"density",density,"motion",motion));error="";}catch(Exception failure){error="Не удалось сохранить настройки";}rebuildWidgets();}
 private void setting(String label,String value,java.util.List<String> choices,int selected,java.util.function.IntConsumer change){settings.add(new Setting(SettingsCatalog.id(0,label),label,value,choices,selected,change));}
 @Override protected void init(){layout=UiSettingsShell.build(width,height,0,parent,this,this::addRenderableWidget,()->UiSettingsShell.confirm(this,this::resetSection),this::onClose);settings.clear();preview=null;
  {
   setting("Тема интерфейса",UiPalette.name(),UiPalette.names(),UiPalette.selected(),n->{try{UiPalette.select(n);save();}catch(Exception ex){error="Не удалось сохранить тему";}});
   setting("Прозрачные панели",opaque?"Выключены":"Включены",java.util.List.of("Включены","Выключены"),opaque?1:0,n->{opaque=n==1;save();});
   setting("Плотность интерфейса",java.util.List.of("Компактная","Сбалансированная","Просторная").get(density),java.util.List.of("Компактная","Сбалансированная","Просторная"),density,n->{density=n;compact=n==0;save();});


   setting("Масштаб GUI Minecraft",minecraft.options.guiScale().get()==0?"Автоматически":String.valueOf(minecraft.options.guiScale().get()),java.util.List.of("Автоматически","1","2","3","4"),minecraft.options.guiScale().get(),n->{minecraft.options.guiScale().set(n);minecraft.options.save();minecraft.resizeDisplay();});
   setting("Часовой пояс",serverTime?"Серверный":"Местный",java.util.List.of("Местное время","Время сервера"),serverTime?1:0,n->{serverTime=n==1;save();});
   setting("Контрастность",contrast?"Повышенная":"Обычная",java.util.List.of("Обычная","Повышенная"),contrast?1:0,n->{contrast=n==1;save();});
   setting("Анимации",java.util.List.of("Выключены","Лёгкие","Выразительные").get(motion),java.util.List.of("Выключены","Лёгкие","Выразительные"),motion,n->{motion=n;reducedMotion=n==0;save();});
  }
  setting("Профиль оформления","Выбрать…",java.util.List.of("Компактный","Минимальный","Подробный"),0,n->minecraft.setScreen(new UiConfirmDialog(yes->{if(yes)UiPresentationProfiles.apply(n);minecraft.setScreen(this);},Component.literal("Применить профиль?"),Component.literal("Изменятся плотность, состав виджета, TAB и подсказки. Тема, расположение и серверные настройки сохранятся."))));
  var body=layout.body();int stride=28;scrollArea(settings.size()+3,new dev.abros.rivet.core.NativeLayout.Box(body.x(),body.y(),body.width(),Math.max(0,body.height())),stride);
  for(int n=0;n<settings.size();n++){if(n<firstRow||n>=firstRow+visibleRows)continue;var setting=settings.get(n);int y=body.y()+(n-firstRow)*stride;addRenderableWidget(UiActions.button(Component.literal(setting.label()+": "+setting.value()+" ▾"),UiActions.Tone.NORMAL,"",b->{if(isThemePicker(setting))minecraft.setScreen(new UiThemePicker(this));else minecraft.setScreen(new ChoicePopup(this,setting.label(),setting.choices(),i->setting.change().accept(i),b).current(setting.selected()));}).bounds(body.x(),y,scrollLayout().content().width(),24).build());}
  int row=settings.size();if(row>=firstRow&&row+2<firstRow+visibleRows){var sample=new com.google.gson.JsonObject();sample.addProperty("section","task");sample.addProperty("title","Построить мост");sample.addProperty("attention","7K2P · В работе");sample.addProperty("preview","Подготовить материалы");sample.addProperty("footer","Ответственный: вы · Завтра");preview=new CommunityCard(body.x(),body.y()+(row-firstRow)*stride,scrollLayout().content().width(),62,sample,()->{});addRenderableWidget(preview);}

 }
 public void revealSetting(String id){for(int n=0;n<settings.size();n++)if(settings.get(n).id().equals(id)){revealRow(n);rebuildWidgets();return;}throw new IllegalArgumentException("Missing setting: "+id);}
 private boolean isThemePicker(Setting setting){return setting.label().equals("Тема интерфейса");}
 private void resetSection(){opaque=false;density=1;compact=false;contrast=false;serverTime=false;motion=1;reducedMotion=false;try{UiPalette.select(0);}catch(Exception ex){error="Не удалось восстановить тему";}minecraft.options.guiScale().set(3);minecraft.options.save();minecraft.resizeDisplay();save();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,layout.frame().x(),layout.frame().y(),layout.frame().width(),layout.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,layout.header().x(),layout.frame().y(),layout.header().width());if(!error.isEmpty())Ui.status(g,font,error,layout.footer().x(),layout.footer().y(),layout.footer().width(),layout.footer().y()+20);});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
