package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.pack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.multiplayer.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;

/** Two real process launches: install through the dialogs, then reconnect after helper application. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ServerPackFlowHarness {
 private static boolean started,done,configured;private static long deadline;private static Screen last;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String phase=System.getenv("RIVET_PACK_FLOW");if(phase==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
    // Keep geometry checks independent of the live connection and file transfer.
    var manifest=new PackManifest("1.21.1","21.1.250",List.of(new PackManifest.Component("extra","Дополнительно","Описание",true)),List.of(new PackManifest.Entry("mods/required.jar","0".repeat(64),1,"","replace")));
    for(int scale:new int[]{1,2,3}){mc.options.guiScale().set(scale);mc.resizeDisplay();var screen=new ServerPackScreen(mc.screen,manifest,Set.of("extra"),s->{if(!s.equals(Set.of("extra")))throw new IllegalStateException("Required file leaked into component choices");});mc.setScreen(screen);for(var child:screen.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height))throw new IllegalStateException("Pack control outside viewport");
     var choices=screen.children().stream().filter(w->w instanceof UiChoiceRow).map(w->(UiChoiceRow)w).toList();
     if(choices.size()!=2||!choices.get(0).active||choices.get(1).active)throw new IllegalStateException("Optional/required choices incorrect");
     choices.get(0).onPress();
     var selection=ServerPackScreen.class.getDeclaredField("selected");selection.setAccessible(true);
     if(!((Set<?>)selection.get(screen)).isEmpty())throw new IllegalStateException("Optional component was not unchecked");
     choices.get(1).onPress();
     if(!((Set<?>)selection.get(screen)).isEmpty())throw new IllegalStateException("Required row changed selection");
     var optional=(UiChoiceRow)screen.children().stream().filter(w->w instanceof UiChoiceRow c&&c.active).findFirst().orElseThrow();
     optional.onPress();
     press(screen,"Проверить изменения");
     screen.onClose();}
    if(phase.equals("components")){System.out.println("RIVET_PACK_COMPONENTS_OK uiScales=1,2,3");done=true;mc.stop();return;}
    mc.options.guiScale().set(Integer.parseInt(System.getenv().getOrDefault("RIVET_PACK_FLOW_SCALE","3")));mc.resizeDisplay();
    var parent=new TitleScreen();mc.setScreen(parent);var address=ServerAddress.parseString(System.getenv().getOrDefault("RIVET_PACK_FLOW_ADDRESS","127.0.0.1:25579"));var data=new ServerData("Pack check",System.getenv().getOrDefault("RIVET_PACK_FLOW_ADDRESS","127.0.0.1:25579"),ServerData.Type.OTHER);if(phase.equals("disabled")&&new PackTrust(mc.gameDirectory.toPath()).fingerprint(address.getHost()+":"+address.getPort()).isEmpty())throw new IllegalStateException("Disabled test requires saved trust");if(phase.equals("configure")){
     var listScreen=new JoinMultiplayerScreen(parent);mc.setScreen(listScreen);var list=(ServerSelectionList)listScreen.children().stream().filter(w->w instanceof ServerSelectionList).findFirst().orElseThrow();
     listScreen.setSelected(null);if(button(listScreen,Client.tr("pack.manage").getString(),false).active)throw new IllegalStateException("Pack enabled without selection");
     listScreen.getServers().add(data,false);list.updateOnlineServers(listScreen.getServers());
     var row=list.children().stream().filter(w->w instanceof ServerSelectionList.OnlineServerEntry e&&e.getServerData().ip.equals(data.ip)).findFirst().orElseThrow();listScreen.setSelected(row);
     var buttons=listScreen.children().stream().filter(w->w instanceof Button).map(w->(Button)w).toList();
     for(var w:buttons){if(w.getX()<0||w.getX()+w.getWidth()>listScreen.width)throw new IllegalStateException("Server button outside viewport");for(var other:buttons)if(w!=other&&w.getX()<other.getX()+other.getWidth()&&other.getX()<w.getX()+w.getWidth()&&w.getY()<other.getY()+other.getHeight()&&other.getY()<w.getY()+w.getHeight())throw new IllegalStateException("Server buttons overlap");}
     configured=true;press(listScreen,Client.tr("pack.manage").getString());
    }else ConnectScreen.startConnecting(parent,mc,address,data,false,null);return;
   }
   if(mc.screen!=last){last=mc.screen;System.out.println("RIVET_PACK_FLOW_SCREEN "+(last==null?"world":last.getClass().getSimpleName()));
    if(last instanceof UiConfirmDialog){if(!phase.equals("install"))throw new IllegalStateException("Trust not retained");press(last,"Подтвердить");}
    else if(last instanceof ServerPackScreen){if(phase.equals("configure")&&configured){press(last,"Проверить изменения");return;}if(!phase.equals("install"))throw new IllegalStateException("Installed pack was not recognized");press(last,"Проверить изменения");}
    else if(last instanceof ReviewScreen){if(phase.equals("configure")){press(last,"Сохранить");return;}if(!phase.equals("install"))throw new IllegalStateException("Files need another install");press(last,"Установить");}
    else if(last instanceof RestartScreen){if(!phase.equals("install"))throw new IllegalStateException("Pending transaction not applied");System.out.println("RIVET_PACK_FLOW_INSTALL_READY id="+Client.pending);press(last,Client.tr("restart.close").getString());}
    else if(last instanceof TextScreen&&phase.equals("configure")){
     var field=TextScreen.class.getDeclaredField("text");field.setAccessible(true);if(!field.get(last).toString().contains("Выбор компонентов сохранён"))throw new IllegalStateException("Configuration failed: "+field.get(last));
     last.onClose();if(!(mc.screen instanceof JoinMultiplayerScreen)||mc.level!=null||!Client.pending.isEmpty())throw new IllegalStateException("Configuration must return to list without connecting or restart");System.out.println("RIVET_PACK_FLOW_CONFIGURE_OK");done=true;mc.stop();return;
    }
    else if(last instanceof DisconnectedScreen||last instanceof TextScreen){if(last instanceof TextScreen){var field=TextScreen.class.getDeclaredField("text");field.setAccessible(true);System.out.println(field.get(last));}throw new IllegalStateException("Connection failed: "+last.getTitle().getString());}
   }
   if((phase.equals("connect")||phase.equals("disabled"))&&mc.level!=null&&mc.player!=null){var game=mc.gameDirectory.toPath();var installer=new PackInstaller(game,Client.ensureHub().cache);if(installer.installedHash().equals("invalid")||installer.installedHash().isEmpty())throw new IllegalStateException("Pack state invalid");if(!Files.readString(game.resolve("config/preview.toml")).equals("personal=true\n"))throw new IllegalStateException("Personal config replaced");System.out.println(phase.equals("disabled")?"RIVET_PACK_FLOW_DISABLED_OK":"RIVET_PACK_FLOW_RECONNECT_OK uiScales=1,2,3");done=true;mc.stop();}
   if(System.nanoTime()>deadline)throw new IllegalStateException("Pack flow timed out");
  }catch(Throwable failure){System.out.println("RIVET_PACK_FLOW_FAILED");failure.printStackTrace();done=true;mc.stop();}
 }
 private static Button button(Screen screen,String label,boolean active){return (Button)screen.children().stream().filter(w->w instanceof Button b&&(!active||b.active)&&b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing action: "+label));}
 private static void press(Screen screen,String label){button(screen,label,true).onPress();}
}
