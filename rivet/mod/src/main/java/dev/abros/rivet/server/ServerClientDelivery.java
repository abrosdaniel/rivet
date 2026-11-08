package dev.abros.rivet.server;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.PacketDistributor;

/** Connection-scoped transport and view leases; no screen or domain owns the connection. */
final class ServerClientDelivery {
    private static final MenuSubscriptions<PlayerSessions.Session<ServerGamePacketListenerImpl>> views = new MenuSubscriptions<>();
    static MenuSubscriptions<PlayerSessions.Session<ServerGamePacketListenerImpl>> views(){return views;}
    static MenuSubscriptions.View subscription(ServerPlayer player,long now){return views.current(player.getUUID(),ServerDataRuntime.sessions().current(player.getUUID()),now);}
    static boolean current(ServerPlayer player){return ServerDataRuntime.sessions().matches(player.getUUID(),player.connection)&&player.server.getPlayerList().getPlayer(player.getUUID())==player&&player.connection.isAcceptingMessages();}
    static void send(ServerPlayer player,JsonObject packet){
        try(var locale=Messages.locale(player.clientInformation().language())){sendLocalized(player,LocalizedText.localize(packet).getAsJsonObject());}
    }
    static void sendLocalized(ServerPlayer player,JsonObject packet){
        if(!current(player)||!player.connection.hasChannel(Protocol.FeatureState.TYPE)||!ServerIntegration.supports(player,"menu"))return;
        String encoded=Json.GSON.toJson(packet);
        if(encoded.length()>32767){
            var failure=new JsonObject();failure.addProperty("kind","result");failure.addProperty("text",Messages.text("rivet.core.response_is_too_large_narrow_the_e4986e4a"));
            failure.addProperty("request",Json.opt(packet,"request",""));failure.addProperty("error",true);failure.addProperty("code","UNAVAILABLE");failure.addProperty("recovery","retry");encoded=Json.GSON.toJson(failure);
        }
        PacketDistributor.sendToPlayer(player,new Protocol.FeatureState(encoded));
    }
    private ServerClientDelivery(){}
}
