package dev.abros.rivet.server.compat;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.Protocol;
import dev.abros.rivet.server.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;

/** Explicit allowlist of optional adapters; no arbitrary reflective operation dispatch. */
public final class CompatibilityRegistry {
 private static final SparkAdapter SPARK=new SparkAdapter();
 private static final Map<String,CompatibilityAdapter> ADAPTERS=Map.of(dev.abros.rivet.compat.AccessDeniedBindings.ID,new CreateAccessDeniedAdapter(),"spark",SPARK,"luckperms",new LuckPermsAdapter(),"plasmovoice",new PlasmoVoiceAdapter());
 private static void guard(String id,dev.abros.rivet.core.OptionalIntegration.Operation<Boolean> work){var adapter=ADAPTERS.get(id);adapter.lifecycle().call(work,()->{if(adapter==SPARK)SPARK.unavailable();return false;});}
 private static final Map<UUID,Window> rates=new HashMap<>();
 private record Window(long start,int count){}
 private static long observed;
 public static JsonArray diagnostics(){var rows=new JsonArray();ADAPTERS.values().stream().sorted(java.util.Comparator.comparing(CompatibilityAdapter::id)).forEach(a->{var row=a.diagnostics();rows.add(row);});return rows;}
 private CompatibilityRegistry(){}
 public static void install(){
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e)->{ADAPTERS.values().forEach(CompatibilityAdapter::clear);guard("spark",()->{SPARK.start(e.getServer());return true;});});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{if(SPARK.sampleDue())guard("spark",()->{SPARK.tick(e.getServer());return true;});long revision=dev.abros.rivet.core.OptionalIntegration.changes();if(revision!=observed){observed=revision;ServerFeatures.adapterStateChanged(e.getServer());}});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.CommandEvent e)->SPARK.command(e));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartedEvent e)->com.mojang.logging.LogUtils.getLogger().info("Rivet adapters: {}",status()));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->{rates.clear();ADAPTERS.values().forEach(CompatibilityAdapter::clear);});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{rates.remove(e.getEntity().getUUID());});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->{
   var root=Commands.literal("rivet").then(Commands.literal("compatibility").executes(c->{c.getSource().sendSuccess(()->Component.literal(status()),false);return 1;})
    .then(Commands.argument("adapter",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(ADAPTERS.keySet(),b))
     .then(Commands.literal("preview").then(Commands.argument("network",StringArgumentType.word()).executes(c->command(c.getSource(),StringArgumentType.getString(c,"adapter"),"preview",StringArgumentType.getString(c,"network"),""))))
     .then(Commands.literal("apply").then(Commands.argument("network",StringArgumentType.word()).then(Commands.argument("token",StringArgumentType.word()).executes(c->command(c.getSource(),StringArgumentType.getString(c,"adapter"),"apply",StringArgumentType.getString(c,"network"),StringArgumentType.getString(c,"token"))))))));e.getDispatcher().register(root);
  });
 }
 public static String status(){var lines=new ArrayList<String>();ADAPTERS.values().stream().sorted(java.util.Comparator.comparing(CompatibilityAdapter::id)).forEach(a->lines.add(a.id()+" (API "+a.apiVersion()+"): "+a.status()));return String.join("\n",lines);}
 private static JsonObject execute(ServerPlayer p,JsonObject j)throws Exception{
  if(!AuthServer.authenticated(p))throw new IllegalArgumentException("Сначала войдите в аккаунт");
  long now=System.currentTimeMillis();var w=rates.get(p.getUUID());if(w==null||now-w.start()>=1000)w=new Window(now,0);if(w.count()>=24)throw new IllegalArgumentException("Слишком много запросов адаптера");rates.put(p.getUUID(),new Window(w.start(),w.count()+1));
  var adapter=ADAPTERS.get(Json.str(j,"adapter"));if(adapter==null)throw new IllegalArgumentException("Адаптер не найден");if(adapter.apiVersion()!=CompatibilityAdapter.API_VERSION||j.has("adapterApi")&&j.get("adapterApi").getAsInt()!=adapter.apiVersion())throw new IllegalArgumentException("Несовместимый API адаптера");var operation=(dev.abros.rivet.core.OptionalIntegration.Operation<JsonObject>)()->adapter.execute(p,j,new ServerIdentityDirectory(p.server));
  boolean diagnostic=adapter==SPARK&&Set.of("snapshot","alerts").contains(Json.opt(j,"op","snapshot"));
  var result=diagnostic?operation.run():adapter.lifecycle().required(operation);result.addProperty("adapterApi",adapter.apiVersion());return result;
 }
 public static boolean handle(JsonObject j,IPayloadContext context){
  if(!Json.opt(j,"action","").equals("compatibility"))return false;
  if(!(context.player() instanceof ServerPlayer p)||!ServerIntegration.supports(p,"compatibility-adapters"))return true;
  String id=Json.opt(j,"request","");if(id.length()>36)return true;
  var result=new JsonObject();try{result=execute(p,j);}catch(Exception ex){result.addProperty("error",ex instanceof IllegalArgumentException?ex.getMessage():"Ошибка адаптера. Проверьте журнал сервера");if(!(ex instanceof IllegalArgumentException))com.mojang.logging.LogUtils.getLogger().warn("Rivet compatibility adapter failed",ex);}
  result.addProperty("kind","compatibility");result.addProperty("request",id);PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(Json.GSON.toJson(result)));return true;
 }
 private static int command(net.minecraft.commands.CommandSourceStack source,String adapter,String op,String network,String token){
  try{var p=source.getPlayerOrException();var j=new JsonObject();j.addProperty("adapter",adapter);j.addProperty("op",op);j.addProperty("network",network);if(!token.isEmpty())j.addProperty("token",token);var reply=execute(p,j);var text=new StringBuilder(Json.str(reply,"text"));if(reply.has("replacements"))for(var row:reply.getAsJsonArray("replacements")){var r=row.getAsJsonObject();text.append("\n").append(Json.str(r,"name")).append(": ").append(Json.str(r,"from")).append(" → ").append(Json.str(r,"to"));}if(reply.has("token"))text.append("\nПодтвердить в течение 2 минут: /rivet compatibility ").append(adapter).append(" apply ").append(network).append(" ").append(Json.str(reply,"token"));source.sendSuccess(()->Component.literal(text.toString()),false);return 1;}catch(Exception ex){source.sendFailure(Component.literal(ex instanceof IllegalArgumentException?ex.getMessage():"Не удалось выполнить действие адаптера"));return 0;}
 }
}
