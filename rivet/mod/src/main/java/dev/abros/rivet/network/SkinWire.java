package dev.abros.rivet.network;
import dev.abros.rivet.core.Json;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public final class SkinWire {
 public static java.util.function.BiConsumer<JsonObject,IPayloadContext> server=(j,c)->{};
 public static java.util.function.Consumer<JsonObject> client=j->{};
 public record Request(String json) implements CustomPacketPayload{
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","skin_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new Request(b.readUtf(8192)));
  public Type<Request> type(){return TYPE;}
 }
 public record Response(String json) implements CustomPacketPayload{
  public static final Type<Response> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","skin_response"));
  public static final StreamCodec<FriendlyByteBuf,Response> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,32767),b->new Response(b.readUtf(32767)));
  public Type<Response> type(){return TYPE;}
 }
 private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
 private static long lastRequestFailure,lastResponseFailure;
 public static synchronized void failure(boolean request,Exception error){
  long now=System.currentTimeMillis(),previous=request?lastRequestFailure:lastResponseFailure;
  if(previous!=0&&now-previous<30000)return;
  if(request)lastRequestFailure=now;else lastResponseFailure=now;
  // Do not log packet contents or exception messages: they may contain private data.
  String location=java.util.Arrays.stream(error.getStackTrace()).limit(8).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining("\n  at "));
  LOG.warn("Rivet skin {} failed: {}\n  at {}\nFurther failures on this side are suppressed for 30 seconds.",request?"request":"response",error.getClass().getSimpleName(),location);
 }
 private static void request(String json,IPayloadContext context){try{server.accept(Json.parse(json),context);}catch(Exception error){failure(true,error);}}
 private static void response(String json){try{client.accept(Json.parse(json));}catch(Exception error){failure(false,error);}}
 public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("3").optional();r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->request(p.json,c)));r.playToClient(Response.TYPE,Response.CODEC,(p,c)->c.enqueueWork(()->response(p.json)));}
}
