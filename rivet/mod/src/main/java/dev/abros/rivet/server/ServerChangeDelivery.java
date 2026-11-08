package dev.abros.rivet.server;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Game-thread delivery state; database work shares the bounded control lane. */
final class ServerChangeDelivery {
    private static boolean running;
    static void install(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->{if(running)tick(event.getServer(),System.currentTimeMillis());});}
    static void start(){reset();running=true;}
    static void stop(){running=false;reset();}
    private static final Map<UUID,Integer> unread = new HashMap<>();
    private static final Map<UUID,Long> popupSequence = new HashMap<>();
    private static AtomicBoolean delivering = new AtomicBoolean();
    private static ChangeDeliverySchedule changeDelivery = new ChangeDeliverySchedule();
    private static NotificationRefresh refresh=new NotificationRefresh();

    static void reset(){unread.clear();popupSequence.clear();delivering=new AtomicBoolean();changeDelivery=new ChangeDeliverySchedule();refresh.invalidate();}
    static void forget(UUID player){unread.remove(player);popupSequence.remove(player);refresh.invalidate();changeDelivery.backlog();}
    static int unread(UUID player){return unread.getOrDefault(player,0);}
    static long sequence(UUID player){return popupSequence.getOrDefault(player,0L);}
    static void tick(MinecraftServer server,long now){if(changeDelivery.due(ServerDatabase.get().communityRevision(),now))deliverChanges(server);}
    private static void deliverChanges(MinecraftServer server){
        var delivering=ServerChangeDelivery.delivering;
        var database=ServerDatabase.get();if(!delivering.compareAndSet(false,true))return;
        var deliverySessions=ServerDataRuntime.sessions().snapshot();
        var recipients=server.getPlayerList().getPlayers().stream().filter(AuthServer::authenticated).map(ServerPlayer::getUUID).filter(deliverySessions::containsKey).toList();
        long attemptAt=System.currentTimeMillis();var refreshAttempt=refresh.begin(attemptAt);boolean refreshNotices=refreshAttempt.required();
        changeDelivery.started(ServerDatabase.get().communityRevision(),attemptAt);
        long queuedAt=PerformanceMetrics.start();
        try{ServerDataRuntime.workers().execute(ServerWorkQueues.Lane.CONTROL,()->{try{PerformanceMetrics.end("changes.queue",queuedAt);long readAt=PerformanceMetrics.start();var events=ChangeOutbox.pending(database);
            var noticeRecipients=new HashSet<String>();for(var element:events){var event=element.getAsJsonObject();if(Json.str(event,"topic").equals("notifications"))noticeRecipients.add(Json.str(event,"recipient"));}
            var refreshRecipients=refreshNotices||noticeRecipients.contains("")?recipients:recipients.stream().filter(id->noticeRecipients.contains(id.toString())).toList();
            Map<UUID,NotificationStore.Summary> summaries=refreshRecipients.isEmpty()?Map.of():ServerNotifications.summary(refreshRecipients);
            PerformanceMetrics.end("changes.read",readAt);long applyAt=PerformanceMetrics.start();server.execute(dev.abros.rivet.core.Messages.capture(()->{
            if(!running||delivering!=ServerChangeDelivery.delivering||ServerDatabase.get()!=database){delivering.set(false);return;}
            try{
                long now=System.currentTimeMillis();
                refresh.complete(refreshAttempt);
                PerformanceMetrics.end("changes.applyQueue",applyAt);
                if(events.size()==256)changeDelivery.backlog();
                for(var player:server.getPlayerList().getPlayers()){
                    var summary=summaries.get(player.getUUID());if(summary==null||!ServerDataRuntime.sessions().current(deliverySessions.get(player.getUUID()))||!AuthServer.authenticated(player))continue;
                    UUID owner=player.getUUID();boolean updated=unread.getOrDefault(owner,0)!=summary.unread()||popupSequence.getOrDefault(owner,0L)!=summary.sequence();
                    unread.put(owner,summary.unread());popupSequence.put(owner,summary.sequence());
                    if(updated)ServerNotifications.sendSummary(player,summary);
                }
                for(var player:server.getPlayerList().getPlayers()){
                    var sub=ServerClientDelivery.subscription(player,now);if(!ServerDataRuntime.sessions().current(deliverySessions.get(player.getUUID()))||!AuthServer.authenticated(player))continue;
                    var hints=new ArrayList<JsonObject>();if(sub!=null)hints.addAll(MenuChangeBatch.hints(events,player.getUUID().toString(),sub.section(),sub.id()));
                    if(ServerIntegration.supports(player,"hud"))for(var hint:MenuChangeBatch.hudHints(events,player.getUUID().toString()))if(MenuChangeHint.hud(hint)&&!hints.contains(hint))hints.add(hint);
                    for(var hint:hints)ServerClientDelivery.send(player,hint);
                }
                ServerDataRuntime.workers().execute(ServerWorkQueues.Lane.CONTROL,()->{try{ChangeOutbox.acknowledge(database,events);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot acknowledge Rivet changes; delivery will retry",ex);}finally{delivering.set(false);}});
            }catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot deliver Rivet changes; delivery will retry",ex);}
        }));}catch(Exception ex){delivering.set(false);com.mojang.logging.LogUtils.getLogger().warn("Cannot read Rivet changes; delivery will retry",ex);}});}catch(RejectedExecutionException busy){delivering.set(false);}
    }
    private ServerChangeDelivery(){}
}
