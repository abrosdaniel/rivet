package dev.abros.rivet.server;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.skins.*;
import dev.abros.rivet.network.SkinWire;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;
public final class ServerSkins {
 private static volatile SkinStore store;private static volatile SkinSettings settings;
 private static ExecutorService FALLBACK;
 private static ExecutorService WORK;
 private static KeyedSerialExecutor<UUID> requests;
 private static CoalescingExecutor<UUID> fallbackWork;
 private record Upload(Object connection,String request,String name,boolean slim,JsonObject metadata,int size,ByteArrayOutputStream bytes,long expires){}
 private static final Map<UUID,Upload> uploads=new HashMap<>();private static final Map<UUID,long[]> rates=new HashMap<>();private static final Map<UUID,Long> uploadTimes=new HashMap<>();
 private static Map<UUID,Long> fallbackAttempts=new ConcurrentHashMap<>();
 public static void install(){SkinWire.server=ServerSkins::request;

  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{UUID id=e.getEntity().getUUID();uploads.remove(id);rates.remove(id);uploadTimes.remove(id);});

 }
 static void start(){settings=ServerDatabase.settings().skins();if(settings.enabled()){FALLBACK=new dev.abros.rivet.core.LocalizedExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Rivet Mojang skins");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());WORK=new dev.abros.rivet.core.LocalizedExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Rivet skins");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}requests=settings.enabled()?new KeyedSerialExecutor<>(WORK,128):null;fallbackWork=settings.enabled()?new CoalescingExecutor<>(FALLBACK):null;fallbackAttempts=new ConcurrentHashMap<>();store=settings.enabled()?new SkinStore(ServerDatabase.get(),settings):null;uploads.clear();rates.clear();uploadTimes.clear();fallbackAttempts.clear();}
 static void stop(){store=null;if(requests!=null)requests.close();if(fallbackWork!=null)fallbackWork.close();ModuleWorkers.stop(WORK,FALLBACK);requests=null;fallbackWork=null;FALLBACK=null;WORK=null;settings=null;uploads.clear();rates.clear();uploadTimes.clear();fallbackAttempts.clear();}
 private static void send(ServerPlayer p,JsonObject j){if(p.connection.getConnection().isConnected()&&AuthServer.authenticated(p))PacketDistributor.sendToPlayer(p,new SkinWire.Response(Json.GSON.toJson(j)));}
 private static void error(ServerPlayer p,String request,String text){var j=new JsonObject();j.addProperty("kind","error");j.addProperty("request",request);j.addProperty("text",text);send(p,j);}
 private static JsonObject config(){var j=new JsonObject();j.addProperty("enabled",settings.enabled());j.addProperty("maxBytes",settings.maxBytes());j.addProperty("limit",settings.maxSkinsPerPlayer());return j;}
 private static void request(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext c){
  if(!(c.player() instanceof ServerPlayer player))return;
  try(var locale=dev.abros.rivet.core.Messages.locale(player.clientInformation().language())){requestLocalized(j,c);}
 }
 private static void requestLocalized(JsonObject j,net.neoforged.neoforge.network.handling.IPayloadContext c){
  if(!(c.player() instanceof ServerPlayer p)||settings==null||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"menu"))return;
  String request=Json.opt(j,"request",""),op=Json.opt(j,"op","");long now=System.currentTimeMillis();var rate=rates.computeIfAbsent(p.getUUID(),id->new long[]{now,0});if(now-rate[0]>1000){rate[0]=now;rate[1]=0;}if(++rate[1]>80){error(p,request,dev.abros.rivet.core.Messages.text("rivet.core.too_many_requests_f0874e09"));return;}
  if(!settings.enabled()){var out=config();out.addProperty("kind","disabled");out.addProperty("request",request);send(p,out);return;}
  final SkinStore current=store;final Object connection=p.connection.getConnection();final var server=p.server;
  final var configuration=settings;final var fallback=fallbackWork;final var database=ServerDatabase.get();final var attempts=fallbackAttempts;
  try{
   byte[] uploaded=null;
   if(op.equals("begin")){uploads.entrySet().removeIf(e->e.getValue().expires<now);int size=j.get("size").getAsInt();if(size<33||size>settings.maxBytes())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.png_exceeds_the_size_limit_611c557b"));if(now-uploadTimes.getOrDefault(p.getUUID(),0L)<3000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_before_uploading_f009da93"));int reserved=uploads.values().stream().mapToInt(Upload::size).sum();if(reserved+size>32*1024*1024)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.server_busy_with_downloads_b5bbc810"));String name=Json.str(j,"name");if(name.length()>40||name.isBlank())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.name_1_40_characters_2e10bd2e"));uploads.put(p.getUUID(),new Upload(connection,request,name,j.get("slim").getAsBoolean(),j.deepCopy(),size,new ByteArrayOutputStream(size),now+60000));uploadTimes.put(p.getUUID(),now);return;}
   if(op.equals("chunk")){var u=uploads.get(p.getUUID());if(u==null||u.connection!=connection||!u.request.equals(request)||now>u.expires)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.upload_expired_ee473c38"));byte[] part=Base64.getDecoder().decode(Json.str(j,"data"));if(part.length>4096||part.length==0||j.get("offset").getAsInt()!=u.bytes.size()||part.length>u.size-u.bytes.size()){uploads.remove(p.getUUID());throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_upload_chunk_04dd4c4b"));}u.bytes.writeBytes(part);if(u.bytes.size()<u.size)return;uploads.remove(p.getUUID());uploaded=u.bytes.toByteArray();j=u.metadata.deepCopy();j.addProperty("name",u.name);j.addProperty("slim",u.slim);op="upload";}
   final String operation=op;final JsonObject input=j;final byte[] bytes=uploaded;
   UUID lookup=op.equals("appearance")?UUID.fromString(Json.str(j,"owner")):p.getUUID();var online=server.getPlayerList().getPlayer(lookup);String nickname=online==null?null:online.getGameProfile().getName();boolean official=server.usesAuthentication();
   requests.execute(p.getUUID(),()->{try{
    if(store!=current||!server.submit(()->p.connection.getConnection()==connection&&p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)).get(2,TimeUnit.SECONDS))return;
    JsonObject receipt=null;
    switch(operation){
     case "blob"->{String hash=Json.str(input,"hash");byte[] png=current.bytes(hash);var parts=new ArrayList<JsonObject>();for(int offset=0;offset<png.length;offset+=4096){var out=new JsonObject();out.addProperty("kind","blob");out.addProperty("hash",hash);out.addProperty("offset",offset);out.addProperty("size",png.length);out.addProperty("data",Base64.getEncoder().encodeToString(Arrays.copyOfRange(png,offset,Math.min(png.length,offset+4096))));parts.add(out);}server.execute(dev.abros.rivet.core.Messages.capture(()->{if(store==current&&p.connection.getConnection()==connection)for(var part:parts)send(p,part);}));return;}
     case "appearance","list"->{try{fallback.execute(lookup,()->{try{if(store!=current)return;if(!refreshFallback(current,configuration,database,attempts,lookup,nickname,official))return;var updated=current.appearance(lookup);updated.addProperty("kind","appearance");server.execute(dev.abros.rivet.core.Messages.capture(()->{if(store==current)for(var other:server.getPlayerList().getPlayers())if(ServerIntegration.supports(other,"skins"))send(other,updated);}));}catch(Exception failure){if(store==current)SkinWire.failure(true,failure);}});}catch(RejectedExecutionException busy){/* Optional refresh must not hide the stored skin or library. */}}
     case "upload","rename","select","delete","model","moveUp","moveDown"->receipt=current.mutate(p.getUUID(),operation,input,bytes);
     default->throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_action_ea069893"));
    }
    boolean mutation=Set.of("upload","select","delete","model").contains(operation);var appearance=current.appearance(lookup);appearance.addProperty("kind","appearance");var out=operation.equals("appearance")?appearance:receipt!=null&&!receipt.has("replayed")?receipt:current.library(p.getUUID());if(!operation.equals("appearance"))out.addProperty("kind","library");out.addProperty("request",request);
    server.execute(dev.abros.rivet.core.Messages.capture(()->{if(store!=current)return;if(p.connection.getConnection()==connection)send(p,out);if(mutation)for(var other:server.getPlayerList().getPlayers())if(ServerIntegration.supports(other,"skins"))send(other,appearance);}));
   }catch(Exception failure){if(!(failure instanceof IllegalArgumentException||failure instanceof java.io.IOException))SkinWire.failure(true,failure);server.execute(dev.abros.rivet.core.Messages.capture(()->{if(store==current&&p.connection.getConnection()==connection)error(p,request,failure instanceof IllegalArgumentException||failure instanceof java.io.IOException?Objects.toString(failure.getMessage(),dev.abros.rivet.core.Messages.text("rivet.core.could_not_process_skin_667917de")):dev.abros.rivet.core.Messages.text("rivet.core.could_not_process_skin_try_again_cd61cc29"));}));}});
  }catch(Exception failure){if(!(failure instanceof IllegalArgumentException||failure instanceof RejectedExecutionException))SkinWire.failure(true,failure);error(p,request,failure instanceof RejectedExecutionException?dev.abros.rivet.core.Messages.text("rivet.core.server_busy_21edfff3"):Objects.toString(failure.getMessage(),dev.abros.rivet.core.Messages.text("rivet.core.skin_error_853ffbe4")));}
 }
 private static boolean refreshFallback(SkinStore s,SkinSettings configuration,PgDatabase database,Map<UUID,Long> attempts,UUID owner,String name,boolean onlineMode)throws Exception{
  String mode=configuration.mojangFallback();UUID official=null;if(mode.equals("UUID")){official=database.transaction(()->{try(var q=database.connection().prepareStatement("SELECT official FROM auth_accounts WHERE uuid=?")){q.setString(1,owner.toString());try(var r=q.executeQuery()){return r.next()&&r.getString(1)!=null?UUID.fromString(r.getString(1)):null;}}});if(official==null&&onlineMode)official=owner;}
  if(name==null&&!mode.equals("false"))return false;String source=mode+":"+(mode.equals("UUID")?official:name);long now=System.currentTimeMillis();if(now-s.checked(owner,source)<86400000)return false;
  if(mode.equals("false")||mode.equals("UUID")&&official==null){if(store!=s)return false;s.fallback(owner,source,null,false,now);return true;}
  if(now-attempts.getOrDefault(owner,0L)<60000)return false;attempts.put(owner,now);
  try{var result=MojangSkins.find(name,official);if(store!=s||Thread.currentThread().isInterrupted())return false;s.fallback(owner,source,result.png(),result.slim(),now);return true;}catch(Exception unavailable){com.mojang.logging.LogUtils.getLogger().debug("Rivet Mojang skin fallback unavailable ({})",unavailable.getClass().getSimpleName());return false;}
 }
}
