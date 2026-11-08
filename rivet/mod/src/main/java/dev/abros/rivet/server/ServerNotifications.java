package dev.abros.rivet.server;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.NotificationPublisher;

/** Owns notification publication independently of API transports and social screens. */
final class ServerNotifications {
    private static volatile NotificationPublisher publisher;
    private static volatile dev.abros.rivet.core.NotificationStore store;
    private static java.util.concurrent.CompletableFuture<Integer> maintenance;
    private static long nextMaintenance;
    static void install() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->{
            var current=publisher;if(current==null)return;
            long now=System.currentTimeMillis();
            if(now>=nextMaintenance&&(maintenance==null||maintenance.isDone())) {
                nextMaintenance=now+1000;
                boolean report=maintenance==null||!maintenance.isCompletedExceptionally();
                maintenance=current.maintain();
                maintenance.exceptionally(error->{if(report)com.mojang.logging.LogUtils.getLogger().warn("Cannot expire Rivet notifications; maintenance will retry",error);return 0;});
            }
        });
    }
    static void start() {
        if(publisher!=null)throw new IllegalStateException("Rivet notifications already started");
        store=new dev.abros.rivet.core.NotificationStore(ServerDatabase.get());publisher=new NotificationPublisher(ServerDatabase.get());maintenance=null;nextMaintenance=0;
    }
    static NotificationPublisher publisher() {
        var current=publisher;
        if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Notifications stopped");
        return current;
    }
    static java.util.Map<java.util.UUID,dev.abros.rivet.core.NotificationStore.Summary> summary(java.util.Collection<java.util.UUID> players)throws Exception{var current=store;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Notifications stopped");return current.summary(players);}
    static void sendSummary(net.minecraft.server.level.ServerPlayer player,dev.abros.rivet.core.NotificationStore.Summary summary){
        if(!AuthServer.authenticated(player))return;
        if(!ServerIntegration.supports(player,"notification-state")){ServerFeatures.sendState(player,false);return;}
        var packet=new com.google.gson.JsonObject();packet.addProperty("kind","notificationState");
        packet.addProperty("unread",summary.unread());packet.addProperty("popupSequence",summary.sequence());
        ServerClientDelivery.send(player,packet);
    }
    static boolean available(){return publisher!=null;}
    static void stop() {
        var previous=publisher;publisher=null;store=null;
        if(previous!=null)previous.close();maintenance=null;
    }
    private ServerNotifications(){}
}
