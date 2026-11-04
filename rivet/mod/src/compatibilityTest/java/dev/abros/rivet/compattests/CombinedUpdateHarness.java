package dev.abros.rivet.compattests;

import dev.abros.rivet.Rivet;
import dev.abros.rivet.client.Client;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.List;

/** Packaged process test. Only the unpublished release catalogue is supplied by the fixture. */
@EventBusSubscriber(modid="rivet_compat_tests",value=Dist.CLIENT)
public final class CombinedUpdateHarness {
 private static boolean started,done,reviewed,closing;private static long deadline;private static Screen last,observed;private static int settling;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String phase=System.getenv("RIVET_COMBINED_FLOW");if(phase==null||done)return;var mc=Minecraft.getInstance();
  try{
   String target=System.getenv("RIVET_COMBINED_VERSION");
   if(!started){
    if(!(mc.screen instanceof TitleScreen)||Client.hub==null)return;
    started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
    check(System.getProperty("rivet.launchGate","").equals("1"),"Packaged bootstrap not active");
    check(Client.loadedJar.startsWith(mc.gameDirectory.toPath().toRealPath()),"Not running isolated installed bundle: "+Client.loadedJar);
    mc.options.guiScale().set(Integer.parseInt(System.getenv().getOrDefault("RIVET_COMBINED_SCALE","2")));mc.resizeDisplay();
    if(phase.equals("install")){
     check(!Rivet.VERSION.equals(target),"Initial version already current");
     Path artifact=Path.of(System.getenv("RIVET_COMBINED_ARTIFACT"));String hash=Hashes.sha256(artifact);
     var cached=Client.hub.cache.path(hash);Files.createDirectories(cached.getParent());Files.copy(artifact,cached,StandardCopyOption.REPLACE_EXISTING);
     String name=artifact.getFileName().toString();var update=new CoreUpdater.Update(target,new Cache.Artifact("mods/"+name,List.of(CoreUpdater.REPOSITORY+"/releases/download/v"+target+"/"+name),hash,Files.size(artifact)),true);
     var offered=Client.class.getDeclaredField("offeredUpdate");offered.setAccessible(true);offered.set(null,update);
    }else check(Rivet.VERSION.equals(target),"Wrong running version after helper: "+Rivet.VERSION);
    String address=System.getenv("RIVET_COMBINED_ADDRESS");var parent=new TitleScreen();mc.setScreen(parent);
    ConnectScreen.startConnecting(parent,mc,ServerAddress.parseString(address),new ServerData("Combined update",address,ServerData.Type.OTHER),false,null);return;
   }
   if(mc.screen!=last){
    if(observed!=mc.screen){observed=mc.screen;settling=0;return;}if(++settling<3)return;
    last=mc.screen;String name=last==null?"world":last.getClass().getSimpleName();System.out.println("RIVET_COMBINED_SCREEN "+name);
    if(last!=null)for(var child:last.children())if(child instanceof AbstractWidget w)check(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=last.width&&w.getY()+w.getHeight()<=last.height,"Control outside viewport: "+name+" "+w.getMessage());
    if(name.equals("UiConfirmDialog")){check(phase.equals("install"),"Trust not retained");press("Подтвердить");}
    else if(name.equals("ServerPackScreen")){check(phase.equals("install"),"Repeated pack installation");press("Проверить изменения");}
    else if(name.equals("ReviewScreen")){
     check(phase.equals("install"),"Repeated update review");String text=text(last);
     check(text.contains("rivet-"+target)&&text.contains("combined-addon.jar"),"Review missing one of the updates: "+text);
     reviewed=true;System.out.println("RIVET_COMBINED_REVIEW_OK");press("Установить");
    }else if(name.equals("RestartScreen")){
     check(reviewed&&!closing,"Unexpected second restart");closing=true;
     var pending=Json.read(mc.gameDirectory.toPath().resolve("rivet/pending.json"));check(Json.str(pending,"id").equals(Client.pending),"Pending transaction mismatch");
     System.out.println("RIVET_COMBINED_INSTALL_OK transaction="+Client.pending);press(Client.tr("restart.close").getString());
    }else if(name.equals("TextScreen")||name.equals("DisconnectedScreen"))throw new IllegalStateException("Flow failed: "+name+" "+(name.equals("TextScreen")?text(last):last.getTitle().getString()));
   }
   if(phase.equals("connect")&&mc.level!=null&&mc.player!=null){
    check(net.neoforged.fml.ModList.get().isLoaded("combined_addon"),"Updated pack mod not loaded");
    check(Files.readString(mc.gameDirectory.toPath().resolve("config/preview.toml")).equals("personal=true\n"),"Personal config replaced");
    check(!Files.exists(mc.gameDirectory.toPath().resolve("rivet/pending.json")),"Update still pending");
    System.out.println("RIVET_COMBINED_RECONNECT_OK version="+Rivet.VERSION);done=true;mc.stop();
   }
   check(System.nanoTime()<deadline,"Timeout");
  }catch(Throwable failure){System.out.println("RIVET_COMBINED_FAILED");failure.printStackTrace();done=true;mc.stop();}
 }
 private static final java.util.Set<String> captured=new java.util.HashSet<>();
 @SubscribeEvent public static void capture(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
  if(System.getenv("RIVET_COMBINED_FLOW")==null||!"true".equals(System.getenv("RIVET_UI_SCREENSHOTS")))return;
  String name=event.getScreen().getClass().getSimpleName();if(!java.util.Set.of("UiConfirmDialog","ServerPackScreen","ReviewScreen","RestartScreen").contains(name)||!captured.add(name))return;
  net.minecraft.client.Screenshot.grab(Minecraft.getInstance().gameDirectory,name+".png",Minecraft.getInstance().getMainRenderTarget(),message->{});
 }
 private static String text(Screen screen)throws Exception{Class<?> type=screen.getClass();while(type!=null){try{var f=type.getDeclaredField("text");f.setAccessible(true);return f.get(screen).toString();}catch(NoSuchFieldException ignored){type=type.getSuperclass();}}throw new IllegalStateException("Screen text missing");}
 private static void press(String label){var screen=Minecraft.getInstance().screen;var button=(Button)screen.children().stream().filter(w->w instanceof Button b&&b.active&&b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing action: "+label));button.onPress();}
 private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
