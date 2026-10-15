package dev.abros.rivet.server;

import dev.abros.rivet.core.api.*;
import dev.abros.rivet.core.auth.AuthStore;
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
    static void start()throws java.io.IOException{
        var mapLayers=ServerApi.layers();var config=ServerDatabase.settings();var settings=RestSettings.from(config);var keys=ServerApiKeys.authorization();
        sessions=new PlayerSessions(keys,new AuthStore(ServerDatabase.get(),config.number("auth.minPasswordLength")),!config.text("auth.mode").equals("false"),Clock.systemUTC(),config.number("api.sessionHours"),config.number("api.sessionLimit"));
        var domains=new DomainRest(keys,sessions,ServerFeatures.communityStore(),new dev.abros.rivet.core.CommunityApiResources(ServerDatabase.get(),ServerFeatures.communityStore(),config).access(ServerDomainAccess::allowed).pack(ServerPack::apiManifest).live(ServerLiveMap.ACCESS).layers(mapLayers)).commands(new dev.abros.rivet.core.CommunityApiCommands(ServerDatabase.get(),ServerFeatures.communityStore(),config).access(ServerDomainAccess::allowed).local(ServerDomainAccess::local));
        var ws=WebSocketSettings.from(config);if(ws.enabled())tickets=new BrowserTickets(keys,sessions,Clock.systemUTC(),BrowserTickets.Limits.from(config),settings.origins());
        var api=new RestApi(ServerApi.runtime(),RestApi.authorization(keys),settings,new BrowserTicketRest(keys,tickets,new TerrainRest(RestApi.authorization(keys),ServerTerrain::api,domains)));
        try{
            if(settings.enabled()){listener=new RestHttpServer(settings,api);listener.start();}
            if(ws.enabled()){websocket=new ApiWebSocketServer(ws,api,ServerApi.runtime(),keys,new DomainSubscriptions(domains,()->ServerDatabase.get().communityRevision()+mapLayers.revision()),ServerTerrain::api,tickets);websocket.start();}
        }catch(java.io.IOException|RuntimeException failure){stop();throw failure;}
    }
    static void stop(){var ws=websocket;websocket=null;if(ws!=null)ws.close();var previous=listener;listener=null;if(previous!=null)previous.close();var oldTickets=tickets;tickets=null;if(oldTickets!=null)oldTickets.close();var current=sessions;sessions=null;if(current!=null)current.close();}
}
