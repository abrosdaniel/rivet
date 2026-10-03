package dev.abros.rivet.network;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.ConnectionCompatibility;
import dev.abros.rivet.core.WireProtocols;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.function.*;
public final class Protocol {
    private static final java.util.concurrent.atomic.AtomicLong lastWarning=new java.util.concurrent.atomic.AtomicLong();
    private static void failedPacket(Exception error){long now=System.nanoTime(),previous=lastWarning.get();if((previous==0||now-previous>java.util.concurrent.TimeUnit.MINUTES.toNanos(1))&&lastWarning.compareAndSet(previous,now))System.getLogger(Protocol.class.getName()).log(System.Logger.Level.WARNING,"Rivet menu packet rejected: "+error.getClass().getSimpleName());}

    public static Consumer<JsonObject> serverHello=j->{};
    public static Consumer<String> incompatible=version->{};
    public static volatile java.util.Set<String> supportedFeatures=java.util.Set.of();
    public static Consumer<JsonObject> featureState=j->{};
    public static BiConsumer<JsonObject,IPayloadContext> featureRequest=(j,c)->{};
    public static volatile String expectedServerId="";
    public static Supplier<JsonObject> clientState=JsonObject::new;
    public static BiConsumer<JsonObject,IPayloadContext> serverReply=(j,c)->c.disconnect(net.minecraft.network.chat.Component.literal("Rivet server not configured"));
    public static volatile JsonObject profile=new JsonObject();
    public record Hello(String json) implements CustomPacketPayload {
        public static final Type<Hello> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","hello"));
        public static final StreamCodec<FriendlyByteBuf,Hello> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,4096),b->new Hello(b.readUtf(4096)));
        public Type<Hello> type(){return TYPE;}
    }
    public record ClientState(String json) implements CustomPacketPayload {
        public static final Type<ClientState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","client_state"));
        public static final StreamCodec<FriendlyByteBuf,ClientState> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,4096),b->new ClientState(b.readUtf(4096)));
        public Type<ClientState> type(){return TYPE;}
    }
    public record FeatureRequest(String json) implements CustomPacketPayload {
        public static final Type<FeatureRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","feature_request"));
        public static final StreamCodec<FriendlyByteBuf,FeatureRequest> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new FeatureRequest(b.readUtf(8192)));
        public Type<FeatureRequest> type(){return TYPE;}
    }
    public record FeatureState(String json) implements CustomPacketPayload {
        public static final Type<FeatureState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","feature_state"));
        public static final StreamCodec<FriendlyByteBuf,FeatureState> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32767),b->new FeatureState(b.readUtf(32767)));
        public Type<FeatureState> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var handshake=event.registrar("rivet-handshake-1").optional();
        var registrar=event.registrar(Integer.toString(WireProtocols.version("pack"))).optional();
        handshake.configurationToClient(Hello.TYPE,Hello.CODEC,(payload,context)->{
            try{var hello=Json.parse(payload.json());String serverVersion=Json.str(hello,"coreVersion");
                String failure=ConnectionCompatibility.failure(serverVersion,dev.abros.rivet.Rivet.VERSION,hello.getAsJsonObject("protocols"));
                if(!failure.isEmpty()){incompatible.accept(ConnectionCompatibility.branch(serverVersion));context.disconnect(net.minecraft.network.chat.Component.literal(failure));return;}
                supportedFeatures=ConnectionCompatibility.common(hello.get("features"));
                if(!expectedServerId.isEmpty()&&!Json.opt(hello,"serverId","").isEmpty()&&!expectedServerId.equals(Json.str(hello,"serverId"))){context.disconnect(net.minecraft.network.chat.Component.literal("Rivet: SERVER_MISMATCH"));return;}
                expectedServerId="";serverHello.accept(hello);var state=clientState.get();state.addProperty("nonce",Json.str(hello,"nonce"));state.addProperty("protocolVersion",WireProtocols.version("pack"));state.add("protocols",WireProtocols.current());state.add("features",ConnectionCompatibility.features());context.reply(new ClientState(Json.GSON.toJson(state)));}
            catch(Exception e){context.disconnect(net.minecraft.network.chat.Component.literal("Invalid Rivet handshake"));}
        });
        handshake.configurationToServer(ClientState.TYPE,ClientState.CODEC,(payload,context)->{try{serverReply.accept(Json.parse(payload.json()),context);}catch(Exception e){context.disconnect(net.minecraft.network.chat.Component.literal("Invalid Rivet client state"));}});
        registrar.playToServer(FeatureRequest.TYPE,FeatureRequest.CODEC,(payload,context)->{try{featureRequest.accept(Json.parse(payload.json()),context);}catch(Exception error){failedPacket(error);}});
        registrar.playToClient(FeatureState.TYPE,FeatureState.CODEC,(payload,context)->{try{featureState.accept(Json.parse(payload.json()));}catch(Exception error){failedPacket(error);}});
    }
}
