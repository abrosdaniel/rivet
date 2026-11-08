package dev.abros.rivet.server;

import dev.abros.rivet.core.api.*;
import java.time.Clock;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Owns the HTTP listener and its transient sessions; no installed profile or world access. */
final class ServerRest {
    private static RestHttpServer listener;
    private static ApiWebSocketServer websocket;
    private static BrowserTickets tickets;
    private static volatile PlayerSessions sessions;
    static void install(){NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event)->{
        event.getDispatcher().register(Commands.literal("rivet").then(Commands.literal("link")
            .requires(s->sessions!=null&&s.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&AuthServer.authenticated(p)&&!AuthServer.enabled())
            .then(Commands.argument("code",StringArgumentType.word()).then(Commands.argument("application",StringArgumentType.word()).executes(c->{
                try{var p=c.getSource().getPlayerOrException();sessions.confirm(StringArgumentType.getString(c,"code"),StringArgumentType.getString(c,"application"),p.getUUID().toString(),p.getGameProfile().getName());c.getSource().sendSuccess(()->Component.translatable("rivet.api.link.confirmed"),false);return 1;}
                catch(Exception rejected){c.getSource().sendFailure(Component.translatable("rivet.api.link.failed"));return 0;}
            })))));
        event.getDispatcher().register(Commands.literal("rivet").then(Commands.literal("unlink")
            .requires(s->sessions!=null&&s.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&AuthServer.authenticated(p))
            .executes(c->{sessions.revokePlayer(c.getSource().getPlayerOrException().getUUID().toString());c.getSource().sendSuccess(()->Component.translatable("rivet.api.link.revoked"),false);return 1;})));
    });}
    private static NotificationRest notifications;
    static void start()throws java.io.IOException{
        var mapLayers=ServerApi.layers();var config=ServerDatabase.settings();var settings=RestSettings.from(config);var keys=ServerApiKeys.authorization();
        sessions=new PlayerSessions(keys,AuthServer.apiStore(),!config.text("auth.mode").equals("false"),Clock.systemUTC(),config.number("api.sessionHours"),config.number("api.sessionLimit"));
        var domains=new DomainRest(keys,sessions,ServerFeatures.communityStore(),new dev.abros.rivet.core.CommunityApiResources(ServerDatabase.get(),ServerFeatures.communityStore(),config).access(ServerDomainAccess::allowed).pack(ServerPack::apiManifest).live(ServerLiveMap.ACCESS).layers(mapLayers)).commands(new dev.abros.rivet.core.CommunityApiCommands(ServerDatabase.get(),ServerFeatures.communityStore(),config).access(ServerDomainAccess::allowed).local(ServerDomainAccess::local));
        var ws=WebSocketSettings.from(config);if(ws.enabled())tickets=new BrowserTickets(keys,sessions,Clock.systemUTC(),BrowserTickets.Limits.from(config),settings.origins());
        notifications=new NotificationRest(keys,ServerNotifications.publisher(),domains);
        var api=new RestApi(ServerApi.runtime(),RestApi.authorization(keys),settings,new BrowserTicketRest(keys,tickets,new TerrainRest(RestApi.authorization(keys),ServerTerrain::api,notifications)));
        try{
            if(settings.enabled()){listener=new RestHttpServer(settings,api);listener.start();}
            if(ws.enabled()){websocket=new ApiWebSocketServer(ws,api,ServerApi.runtime(),keys,new DomainSubscriptions(domains,()->ServerDatabase.get().communityRevision()+mapLayers.revision()),ServerTerrain::api,tickets);websocket.start();}
        }catch(java.io.IOException|RuntimeException failure){stop();throw failure;}
    }
    static boolean sessionsAvailable(){return sessions!=null;}
    static com.google.gson.JsonObject playerSessions(String player,String cursor)throws Exception{var out=new com.google.gson.JsonObject();var items=new com.google.gson.JsonArray();var current=sessions;String next="";if(current!=null){var page=current.playerSessions(player,cursor);next=page.next();for(var session:page.items()){var item=new com.google.gson.JsonObject();item.addProperty("id",session.id().toString());item.addProperty("application",session.application());item.addProperty("created",session.created());item.addProperty("expires",session.expires());items.add(item);}}out.add("sessions",items);out.addProperty("next",next);return out;}
    static void revokePlayerSession(String player,String id){var current=sessions;if(current!=null){if(id.equals("all"))current.revokePlayer(player);else current.revokePlayerSession(player,java.util.UUID.fromString(id));}}
    static void stop(){var notices=notifications;notifications=null;if(notices!=null)notices.close();var ws=websocket;websocket=null;if(ws!=null)ws.close();var previous=listener;listener=null;if(previous!=null)previous.close();var oldTickets=tickets;tickets=null;if(oldTickets!=null)oldTickets.close();var current=sessions;sessions=null;if(current!=null)current.close();}
}
