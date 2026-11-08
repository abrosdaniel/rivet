package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class CommunityPlusUiHarness {
 private static JsonObject submitted;private static java.util.function.Consumer<JsonObject> originalTransport;
 private static int step;private static long at;private static boolean started;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){String output=System.getenv("RIVET_PLUS_UI");if(output==null||step<0)return;var mc=Minecraft.getInstance();if(!started){if(!(event.getScreen() instanceof TitleScreen))return;started=true;if(System.getenv("RIVET_PLUS_FROM")!=null){step=Integer.parseInt(System.getenv("RIVET_PLUS_FROM"));CommunityUiHarness.open("normal");}at=System.currentTimeMillis()+1500;return;}if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+900;
  try{if(step>0){var dir=new java.io.File(output);dir.mkdirs();UiCaptureHarness.grab(dir,String.format("plus-%02d.png",step),mc.getMainRenderTarget(),message->{});}
   switch(step++){
    case 0->{mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 1->{bounds();var group=new CommunityScreen(mc.screen,"groups",new UUID(0,11).toString());group.groupTab="members";mc.setScreen(group);}
    case 2->{bounds();((CommunityScreen)mc.screen).groupTab="places";((CommunityScreen)mc.screen).refreshUi();}
    case 3->{bounds();groupForm();}
    case 4->{bounds();mc.screen.mouseScrolled(mc.screen.width/2,mc.screen.height/2,0,-10);}
    case 5->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new DateTimeScreen(mc.screen,"",value->{}));}
    case 6->{bounds();overlaps();mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new PersonalProfileScreen(mc.screen));}
    case 7->{bounds();overlaps();PersonalProfileScreen.ignores(mc.screen,"Example");}
    case 8->{bounds();mc.options.guiScale().set(3);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 9->{bounds();mc.options.guiScale().set(4);mc.resizeDisplay();CommunityUiHarness.open("long");}
    case 10->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();CommunityUiHarness.open("normal");mc.setScreen(new CommunityPreferences(mc.screen,new JsonObject(),java.util.List.of("groups","example:mail")));}
    case 11->{bounds();overlaps();}
    case 12->{mc.options.guiScale().set(1);mc.resizeDisplay();var method=CommunityUiHarness.class.getDeclaredMethod("player");method.setAccessible(true);var player=(JsonObject)method.invoke(null);player.addProperty("uuid",new UUID(1,123).toString());mc.setScreen(new PlayerActionsScreen(null,player,ServerMenuClient.state.getAsJsonArray("actions")));}
    case 13->{bounds();overlaps();for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals("Модерация…")){b.onPress();break;}}
    case 14->{bounds();overlaps();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new PersonalProfileScreen(null));}
    case 15->{bounds();overlaps();mc.setScreen(new CoreVersionsPopup(new TitleScreen()));}
    case 16->{bounds();overlaps();if(!(mc.screen instanceof CoreVersionsPopup))throw new IllegalStateException("Version dialog missing");for(var child:mc.screen.children())if(child instanceof UiChoiceRow row&&row.getMessage().getString().equals(dev.abros.rivet.Rivet.VERSION))throw new IllegalStateException("Installed version offered for installation");}
    case 17->{mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}
    case 18->{var home=(CommunityScreen)mc.screen;var groups=home.data.getAsJsonArray("groups");if(groups==null||groups.isEmpty())throw new IllegalStateException("Home groups fixture missing");String target=Json.str(groups.get(0).getAsJsonObject(),"value");boolean opened=false;for(int attempt=0;attempt<30&&!opened;attempt++){for(var child:mc.screen.children())if(child instanceof CommunityCard card&&card.targetId().equals(target)){card.onPress();opened=true;break;}if(!opened)mc.screen.mouseScrolled(home.width/2,home.height/2,0,-1);}if(!opened)throw new IllegalStateException("Home group card missing");}
    case 19->{bounds();overlaps();var screen=(CommunityScreen)mc.screen;screen.groupTab="places";screen.refreshUi();}
    case 20->{bounds();overlaps();var screen=(CommunityScreen)mc.screen;var items=screen.data.getAsJsonObject("detail").getAsJsonArray("groupItems");if(!items.isEmpty()){var copy=items.get(0).deepCopy().getAsJsonObject();copy.addProperty("id",UUID.randomUUID().toString());copy.addProperty("title","Вторая метка");items.add(copy);}screen.refreshUi();}
    case 21->{bounds();overlaps();mc.screen.mouseScrolled(mc.screen.width-60,mc.screen.height/2,0,-4);}
    case 22->{bounds();mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new AccessibilityScreen(mc.screen));}
    case 23->{bounds();overlaps();var settings=(AccessibilityScreen)mc.screen;settings.revealSetting("opacity");boolean found=false;for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().startsWith("Прозрачные панели:")){String original=b.getMessage().getString();b.onPress();if(!(mc.screen instanceof ChoicePopup))throw new IllegalStateException("Transparency choices missing");click(original.endsWith("Выключены")?"Включены":"Выключены");settings.revealSetting("opacity");boolean changed=false;for(var row:settings.children())if(row instanceof Button control&&control.getMessage().getString().startsWith("Прозрачные панели:")){changed=!control.getMessage().getString().equals(original);control.onPress();break;}if(!changed)throw new IllegalStateException("Transparency choice did not apply");click(original.endsWith("Выключены")?"Выключены":"Включены");found=true;break;}if(!found)throw new IllegalStateException("Transparency control missing");}
    case 24->{mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new ServerMenuScreen(null,"admin"));}
    case 25->{bounds();overlaps();for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().equals("Сообщество"))throw new IllegalStateException("Duplicate admin navigation remains");}
    case 26->{bounds();overlaps();var group=new CommunityScreen(null,"groups",new UUID(0,11).toString());group.groupTab="requests";mc.setScreen(group);}
    case 27->{bounds();overlaps();var notice=new JsonObject();notice.addProperty("kind","moderationVoteStatus");var vote=new JsonObject();vote.addProperty("id","test-notice");vote.addProperty("status","open");vote.addProperty("name","Example Player");vote.addProperty("reason","Нарушение правил сервера");vote.addProperty("endsAt",System.currentTimeMillis()+120000);notice.add("vote",vote);ServerMenuClient.receive(notice);mc.setScreen(new NotificationPopup(new CommunityScreen(null,"home","")));}
    case 28->{bounds();overlaps();boolean found=false;for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().contains("Example Player")){found=true;break;}if(!found)throw new IllegalStateException("Active vote notification missing");mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 29->{bounds();overlaps();mc.setScreen(new ModerationVoteScreen(null,null));}
    case 30->{bounds();overlaps();ServerMenuClient.moderationVote=new JsonObject();}
    case 31->{mc.options.guiScale().set(1);mc.resizeDisplay();var report=new JsonObject();report.addProperty("id","test-report");report.addProperty("player","Example Player");report.addProperty("message","Прошу проверить проблему. ".repeat(15));report.addProperty("status","open");report.addProperty("revision",1);mc.setScreen(new ReportDetailScreen(null,report,true));}
    case 32->{bounds();overlaps();mc.options.guiScale().set(2);mc.resizeDisplay();}
    case 33->{bounds();overlaps();}
    case 34->{CommunityUiHarness.open("normal");mc.setScreen(new CommunityScreen(null,"groups",""));}
    case 35->{var screen=(CommunityScreen)mc.screen;var saved=screen.data.deepCopy();var widgets=new ArrayList<>(screen.children());var transport=ServerMenuClient.previewTransport;try{ServerMenuClient.previewTransport=q->{var response=saved.deepCopy();response.addProperty("request",Json.str(q,"request"));ServerMenuClient.receive(response);};for(int i=0;i<10;i++)screen.send("list",new JsonObject());if(!widgets.equals(screen.children()))throw new IllegalStateException("Unchanged responses rebuilt UI");screen.invalidate("groups","");saved.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("title","Изменённая запись");screen.send("list",new JsonObject());if(widgets.equals(screen.children()))throw new IllegalStateException("Changed response did not refresh UI");var pending=new JsonObject[1];ServerMenuClient.previewTransport=q->pending[0]=q;screen.send("list",new JsonObject());screen.refreshUi();var waitingWidgets=new ArrayList<>(screen.children());var ready=saved.deepCopy();ready.addProperty("request",Json.str(pending[0],"request"));ServerMenuClient.receive(ready);if(waitingWidgets.equals(screen.children()))throw new IllegalStateException("Busy controls not restored after unchanged response");System.out.println("RIVET_UNCHANGED_UI_OK: 10 identical responses, 0 rebuilds; changed response rebuilt");}finally{ServerMenuClient.previewTransport=transport;}}
    case 36->{mc.options.guiScale().set(1);mc.resizeDisplay();FeatureListScreen.open(null,"players");}
    case 37->{bounds();overlaps();var method=FeatureListScreen.class.getDeclaredMethod("splitPlayers");method.setAccessible(true);if(mc.screen.height>=320&&!(boolean)method.invoke(mc.screen))throw new IllegalStateException("Player side card missing");var group=fixtureGroup();mc.setScreen(group);}
    case 38->{var group=(CommunityScreen)mc.screen;originalTransport=ServerMenuClient.previewTransport;submitted=null;ServerMenuClient.previewTransport=q->{if(Json.opt(q,"op","").equals("plusItemSave"))submitted=q.deepCopy();originalTransport.accept(q);};editItem(group);}
    case 39->{bounds();overlaps();for(var child:mc.screen.children())if(child instanceof EditBox e&&e.getMessage().getString().contains("Ответственный"))throw new IllegalStateException("Marker exposes assignee");fill("Название","");click("Создать");}
    case 40->{bounds();overlaps();if(!(mc.screen instanceof CommunityForm)||submitted!=null)throw new IllegalStateException("Invalid marker form was submitted or closed");}
    case 41->{fill("Название","Проверка метки");fill("Описание","Проверка отправки формы");fill("X","100");fill("Y","70");fill("Z","-200");click("Создать");}
    case 42->{if(mc.screen instanceof CommunityForm)throw new IllegalStateException("Marker submission did not close the form");if(submitted==null||!Json.opt(submitted,"kind","").equals("place")||!submitted.has("location"))throw new IllegalStateException("Marker submission lost kind/location");var group=fixtureGroup();mc.setScreen(group);}
    case 43->{editItem((CommunityScreen)mc.screen);}
    case 44->{bounds();overlaps();fill("Название","Новое место");fill("Описание","Описание места");fill("X","100");fill("Y","70");fill("Z","-200");for(var child:mc.screen.children())if(child instanceof Button b&&b.getMessage().getString().contains("Добавить место"))throw new IllegalStateException("Nested location form remains");click("Создать");}
    case 45->{if(mc.screen instanceof CommunityForm)throw new IllegalStateException("Place submission did not close the form");if(submitted==null||!submitted.has("location")||!Json.str(submitted,"title").equals(Json.str(submitted.getAsJsonObject("location"),"name")))throw new IllegalStateException("Place title and coordinates did not submit together");ServerMenuClient.previewTransport=originalTransport;submitted=null;FeatureListScreen.pick(fixtureGroup(),player->submitted=player.deepCopy());}
    case 46->{bounds();overlaps();if(!(mc.screen instanceof FeatureListScreen))throw new IllegalStateException("Group invite player picker missing");var row=mc.screen.children().stream().filter(c->c instanceof PlayerRow).map(c->(PlayerRow)c).findFirst().orElseThrow();row.onPress();}
    case 47->{if(submitted==null||Json.opt(submitted,"uuid","").isEmpty())throw new IllegalStateException("Player picker lost UUID");if(!(mc.screen instanceof CommunityScreen))throw new IllegalStateException("Player picker lost group parent");}
    case 48->{groupForm();}
    case 49->{bounds();overlaps();click("Команда");}
    case 50->{click("Поселение");}
    case 51->{fill("Название","Новое объединение");fill("Описание","Описание объединения");click("Создать");}
    case 52->{if(submitted==null||!Json.opt(submitted,"type","").equals("Поселение"))throw new IllegalStateException("Group type selection was not submitted");mc.options.guiScale().set(3);mc.resizeDisplay();}
    case 53->{bounds();overlaps();System.out.println("RIVET_FORM_FLOWS_OK: marker validation and submission, group invite picker, type selection");}
    case 54->{mc.options.guiScale().set(1);mc.resizeDisplay();var group=fixtureGroup();submitted=null;mc.setScreen(new LocationEditor(group,new CommunityLocation("База","minecraft:overworld",1,64,2,false).json(),true,value->{submitted=new JsonObject();submitted.addProperty("cleared",value==null||value.isJsonNull());}));}
    case 55->{bounds();overlaps();if(!(mc.screen instanceof LocationEditor))throw new IllegalStateException("Location editor missing");var values=mc.screen.children().stream().filter(c->c instanceof EditBox).map(c->((EditBox)c).getValue()).toList();if(!values.containsAll(List.of("База","1","64","2")))throw new IllegalStateException("Location preset lost");}
    case 56->{click("Убрать место");}
    case 57->{if(submitted==null||!submitted.get("cleared").getAsBoolean())throw new IllegalStateException("Location removal did not reach callback");if(!(mc.screen instanceof CommunityScreen))throw new IllegalStateException("Location editor did not restore group parent");}
    case 58->{bounds();overlaps();System.out.println("RIVET_LOCATION_FORM_OK: existing coordinates, remove, parent restored");}
    default->{System.out.println("RIVET_PLUS_UI_COMPLETE");if(System.getenv("RIVET_UI_KEEP_OPEN")!=null){step=-1;mc.options.guiScale().set(1);mc.resizeDisplay();CommunityUiHarness.open("normal");}else mc.stop();}
   }
  }catch(Throwable failure){System.out.println("RIVET_COMMUNITY_PLUS_FAILED step="+step);failure.printStackTrace();step=-1;mc.stop();}
 }
 private static CommunityScreen fixtureGroup()throws Exception{var group=new CommunityScreen(null,"groups",UUID.randomUUID().toString());var entry=CommunityUiHarness.class.getDeclaredMethod("entry",String.class,int.class);entry.setAccessible(true);group.data.add("detail",(JsonObject)entry.invoke(null,"groups",0));group.data.add("config",CommunityStore.defaults());group.data.add("names",new JsonObject());group.loaded=true;return group;}
 private static void editItem(CommunityScreen group)throws Exception{var edit=CommunityTools.class.getDeclaredMethod("edit",CommunityScreen.class,JsonObject.class);edit.setAccessible(true);edit.invoke(null,group,null);}
 private static void groupForm()throws Exception{
  var mc=Minecraft.getInstance();var parent=fixtureGroup();var options=new JsonArray();options.add("Команда");options.add("Поселение");submitted=null;
  mc.setScreen(new CommunityForm(parent,"Новое объединение","create",List.of(new CommunityScreen.Field("title","Название",100),new CommunityScreen.Field("description","Описание",1500),new CommunityScreen.Field("type","Тип",40,options)),new JsonObject(),body->{submitted=body.deepCopy();var reply=new JsonObject();reply.addProperty("request",Json.str(body,"request"));((CommunityForm)mc.screen).receiveCommunity(reply);}));
 }
 private static void scrollForm(double delta){var screen=Minecraft.getInstance().screen;if(screen instanceof CommunityForm form&&form.scrollLayout()!=null){var area=form.scrollLayout().viewport();screen.mouseScrolled(form.scrollLayout().track().x(),area.y()+area.height()/2.0,0,delta);}}
 private static void click(String label){for(int pass=0;pass<16;pass++){for(var child:Minecraft.getInstance().screen.children())if(child instanceof Button b&&(b.getMessage().getString().equals(label)||b.getMessage().getString().equals(label+" ▾"))){if(!b.active)throw new IllegalStateException("Inactive: "+label);b.onPress();return;}scrollForm(pass==0?100:-1);}throw new IllegalStateException("Missing button: "+label);}
 private static void fill(String label,String value){for(int pass=0;pass<16;pass++){for(var child:Minecraft.getInstance().screen.children())if(child instanceof AbstractWidget w&&w.getMessage().getString().equals(label)){if(child instanceof EditBox box){box.setValue(value);return;}if(child instanceof MultiLineEditBox box){box.setValue(value);return;}}scrollForm(pass==0?100:-1);}throw new IllegalStateException("Missing field: "+label);}
 private static void bounds(){var screen=Minecraft.getInstance().screen;for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){if(w.getX()<0||w.getY()<0||w.getRight()>screen.width||w.getBottom()>screen.height)throw new IllegalStateException("Widget outside screen: "+w.getMessage().getString()+" / "+screen.width+"x"+screen.height);}}
 private static void overlaps(){var screen=Minecraft.getInstance().screen;var boxes=new ArrayList<UiLayout.Rect>();for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible){var box=new UiLayout.Rect(w.getX(),w.getY(),w.getWidth(),w.getHeight());for(var prior:boxes)if(box.overlaps(prior))throw new IllegalStateException("Overlapping widget: "+w.getMessage().getString());boxes.add(box);}}
}
