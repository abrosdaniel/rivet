package dev.abros.rivet.server;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import java.util.*;
import java.util.concurrent.*;
import static dev.abros.rivet.server.ServerFeatures.*;
/** Server-control state and timers; disabled control never loads persisted plans. */
final class ServerManagement {
 static boolean maintenance;static String maintenanceReason="",restartReason="";
 static long maintenanceUntil,restartAt,pinnedUntil;static String pinnedText="";
 private static volatile boolean active;private static boolean timerPending;static String originalMotd="";
 static void start(ServerStartingEvent e)throws Exception{stop();originalMotd=e.getServer().getMotd();
 var store=communityStore();var pin=new CommunityAdministration(ServerDatabase.get(),store).pin();pinnedUntil=pin==null||Json.opt(pin,"text","").isBlank()?0:pin.get("until").getAsLong();pinnedText=pin==null?"":Json.opt(pin,"text","");
 var j=store.record("state","maintenance");store.deleteRecord("state","restart");if(j!=null){maintenance=j.get("maintenance").getAsBoolean();maintenanceReason=Json.opt(j,"reason","");maintenanceUntil=j.get("until").getAsLong();if(maintenanceUntil>0&&maintenanceUntil<=System.currentTimeMillis()){maintenance=false;maintenanceUntil=0;store.deleteRecord("state","maintenance");}}
 active=true;
 }
 static void stop(){active=false;timerPending=false;maintenance=false;maintenanceUntil=0;restartAt=0;pinnedUntil=0;pinnedText="";maintenanceReason="";restartReason="";originalMotd="";}
 static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{if(!active)return;long now=System.currentTimeMillis();
         if(!timerPending&&(restartAt>0&&now>=restartAt||maintenance&&maintenanceUntil>0&&now>=maintenanceUntil)){
            boolean stop=restartAt>0&&now>=restartAt;long expected=stop?restartAt:maintenanceUntil;timerPending=true;var store=communityStore();
            try{control(()->{try{boolean consumed=store.inTransaction(()->{var saved=store.record("state",stop?"restart":"maintenance");if(saved==null||saved.get(stop?"at":"until").getAsLong()!=expected)return false;store.deleteRecord("state",stop?"restart":"maintenance");store.audit("server",stop?"scheduled shutdown":"maintenance expired",Long.toString(expected));return true;});if(!consumed){e.getServer().execute(dev.abros.rivet.core.Messages.capture(()->timerPending=false));return;}e.getServer().execute(dev.abros.rivet.core.Messages.capture(()->{timerPending=false;if(communityStore()!=store)return;if(stop&&restartAt==expected){restartAt=0;urgentNotice(e.getServer(),dev.abros.rivet.core.LocalizedText.key("rivet.core.server_is_saving_and_shutting_down_4d6ef4ad"));e.getServer().halt(false);}else if(!stop&&maintenanceUntil==expected){maintenance=false;maintenanceUntil=0;e.getServer().setMotd(originalMotd);notice(e.getServer(),dev.abros.rivet.core.LocalizedText.key("rivet.core.maintenance_completed_011be41e"));}}));}catch(Exception ex){e.getServer().execute(dev.abros.rivet.core.Messages.capture(()->timerPending=false));com.mojang.logging.LogUtils.getLogger().warn("Rivet: cannot complete scheduled action; will retry");}});}catch(RejectedExecutionException busy){timerPending=false;}
        }
        if(maintenance)e.getServer().setMotd("[Maintenance] "+maintenanceReason);

 });}
 static void publish(long now,List<UUID> players)throws Exception{if(active)new ScheduledAnnouncements(communityStore()).publish(now,players.stream().map(UUID::toString).toList());}
 static Component maintenanceMessage(){return Component.translatable("rivet.message.maintenance_prefix").append(maintenanceReason).append(maintenanceUntil>0?Component.translatable("rivet.message.maintenance_until").append(java.time.Instant.ofEpochMilli(maintenanceUntil).toString()):Component.empty());}
     static void maintenance(MinecraftServer server,boolean enabled,int minutes,String reason,CommandSourceStack source)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException(Messages.text("rivet.message.reason_too_long"));
        String nextReason=enabled?reason:"";long nextUntil=enabled&&minutes>0?System.currentTimeMillis()+minutes*60000L:0;
        var data=Json.GSON.toJsonTree(Map.of("maintenance",enabled,"reason",nextReason,"until",nextUntil)).getAsJsonObject();
        persistState(server,"maintenance",data,source,()->{if(enabled&&!maintenance)originalMotd=server.getMotd();maintenance=enabled;maintenanceReason=nextReason;maintenanceUntil=nextUntil;server.setMotd(enabled?"[Maintenance] "+maintenanceReason:originalMotd);notice(server,enabled?LocalizedText.parts(LocalizedText.key("rivet.message.maintenance_prefix"),LocalizedText.literal(maintenanceReason),maintenanceUntil>0?LocalizedText.parts(LocalizedText.key("rivet.message.maintenance_until"),LocalizedText.literal(java.time.Instant.ofEpochMilli(maintenanceUntil).toString())):LocalizedText.literal("")):LocalizedText.key("rivet.core.maintenance_completed_011be41e"));});
    }
    static void restart(MinecraftServer server,int seconds,String reason,CommandSourceStack source)throws Exception{
        if(reason.length()>500)throw new IllegalArgumentException(Messages.text("rivet.message.reason_too_long"));long at=seconds==0?0:System.currentTimeMillis()+seconds*1000L;
        persistState(server,"restart",Json.GSON.toJsonTree(Map.of("at",at,"reason",reason)).getAsJsonObject(),source,()->{restartAt=at;restartReason=reason;notice(server,seconds==0?dev.abros.rivet.core.LocalizedText.key("rivet.core.scheduled_shutdown_cancelled_24f52381"):dev.abros.rivet.core.LocalizedText.parts(dev.abros.rivet.core.LocalizedText.key("rivet.core.shutting_down_in_04e92dc3"),dev.abros.rivet.core.LocalizedText.literal(Integer.toString(seconds)),dev.abros.rivet.core.LocalizedText.key("rivet.core.since_05912f4e"),dev.abros.rivet.core.LocalizedText.literal(reason),dev.abros.rivet.core.LocalizedText.key("rivet.core.the_control_panel_handles_restarting_f13d3ee8")));});
    }
    private static void persistState(MinecraftServer server,String key,JsonObject data,CommandSourceStack source,Runnable apply){var store=communityStore();control(()->{try{
        if(!server.submit(()->ServerCommands.allowed(source,"rivet."+key)).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.permissions_changed_980f6f62"));
        store.inTransaction(()->{store.record("state",key,data);store.audit(source.getTextName(),key,Json.GSON.toJson(data));return null;});
        server.execute(dev.abros.rivet.core.Messages.capture(()->{if(communityStore()==store){apply.run();ServerCommands.reply(source,dev.abros.rivet.core.Messages.text("rivet.ui.saved_f0dff5ab"));for(var player:server.getPlayerList().getPlayers())sendState(player,false);}}));
    }catch(Exception ex){server.execute(dev.abros.rivet.core.Messages.capture(()->ServerCommands.error(source,ex)));}});}

}
