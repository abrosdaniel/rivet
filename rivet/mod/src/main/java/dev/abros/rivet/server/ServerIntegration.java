package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ServerIntegration {
    private static final ConfigurationTask.Type TYPE=new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath("rivet","verify_pack"));
    private record Pending(String nonce,String hash){}
    private static final Map<Object,Pending> NONCES=Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<Object,Set<String>> FEATURES=Collections.synchronizedMap(new WeakHashMap<>());
    public static boolean supports(net.minecraft.server.level.ServerPlayer player,String feature){return FEATURES.getOrDefault(player.connection.getConnection(),Set.of()).contains(feature);}
    public static void install(IEventBus bus,ModContainer container){
        bus.addListener(ServerIntegration::tasks);
        dev.abros.rivet.server.compat.CompatibilityRegistry.install();
        ServerDatabase.install();
        ServerModules.install(bus,container);
        NeoForge.EVENT_BUS.addListener(ServerIntegration::starting);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event)->{NONCES.clear();FEATURES.clear();});
        Protocol.serverReply=ServerIntegration::reply;
    }
    private static void starting(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event){
        NONCES.clear();FEATURES.clear();
    }
    public static boolean luckPermsEnabled(){return net.neoforged.fml.ModList.get().isLoaded("luckperms");}
    public static String helpText(){return ServerDatabase.settings().text("menu.help");}
    public static String requiredHash(){return ServerPack.requiredHash();}

    private static void tasks(RegisterConfigurationTasksEvent event){
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server!=null&&event.getListener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener&&!ServerFeatures.mayJoin(listener.getOwner(),server)){event.getListener().disconnect(ServerFeatures.maintenanceMessage());return;}
        String current=requiredHash();
        boolean available=event.getListener().hasChannel(Protocol.Hello.TYPE)&&event.getListener().hasChannel(Protocol.ClientState.TYPE);
        if(!available){if(!current.isEmpty()||AuthServer.enabled())event.getListener().disconnect(Component.literal("Для этого сервера требуется Rivet "+ConnectionCompatibility.branch(dev.abros.rivet.Rivet.VERSION)));return;}
        var pending=new Pending(UUID.randomUUID().toString(),current);var listener=event.getListener();
        event.register(new ICustomConfigurationTask(){
            public ConfigurationTask.Type type(){return TYPE;}
            public void run(Consumer<CustomPacketPayload> sender){
                NONCES.put(listener,pending);JsonObject hello=new JsonObject();hello.addProperty("coreVersion",dev.abros.rivet.Rivet.VERSION);hello.add("protocols",WireProtocols.current());hello.add("features",ConnectionCompatibility.features());hello.addProperty("protocolVersion",WireProtocols.version("pack"));hello.addProperty("nonce",pending.nonce());hello.addProperty("requiredHash",current);sender.accept(new Protocol.Hello(Json.GSON.toJson(hello)));
                CompletableFuture.delayedExecutor(ServerDatabase.settings().number("connection.handshakeTimeout"),TimeUnit.SECONDS).execute(()->{if(server!=null)server.execute(()->{if(NONCES.remove(listener,pending))listener.disconnect(Component.literal("Rivet: handshake timeout"));});});
            }
        });
    }
    private static void reply(JsonObject state,net.neoforged.neoforge.network.handling.IPayloadContext context){
        var pending=NONCES.remove(context.listener());if(pending==null||!pending.nonce().equals(Json.opt(state,"nonce",""))){context.disconnect(Component.literal("Rivet: unexpected reply"));return;}
        String compatibility=ConnectionCompatibility.failure(dev.abros.rivet.Rivet.VERSION,Json.opt(state,"coreVersion",""),state.getAsJsonObject("protocols"));
        if(!compatibility.isEmpty()){com.mojang.logging.LogUtils.getLogger().warn("Rivet: rejected incompatible client {}, server {}",Json.opt(state,"coreVersion",""),dev.abros.rivet.Rivet.VERSION);context.disconnect(Component.literal(compatibility));return;}
        var features=ConnectionCompatibility.common(state.get("features"));
        if(AuthServer.enabled()&&!features.contains("auth")||!pending.hash().isEmpty()&&!features.contains("pack")){context.disconnect(Component.literal("В клиенте Rivet отсутствуют необходимые серверу функции"));return;}
        String failure=pending.hash().isEmpty()||pending.hash().equals(Json.opt(state,"packHash",""))?"":"Обновите файлы сборки перед подключением к серверу";if(!failure.isEmpty()){context.disconnect(Component.literal(failure));return;}
        if(context.listener() instanceof net.minecraft.server.network.ServerConfigurationPacketListenerImpl listener)ServerFeatures.record(listener.getOwner().getId(),state);
        FEATURES.put(context.connection(),features);
        context.finishCurrentTask(TYPE);
    }
}
