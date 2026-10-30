package dev.abros.rivet.compat;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.core.*;
import dev.abros.rivet.server.ServerProviders;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Optional Envelope boundary. Identity reads use Rivet Java API; mail contents stay Envelope-owned. */
public final class EnvelopeBindings {
 private static final String BASE="io.github.mortuusars.envelope.world.";
 private static final UUID UNKNOWN=new UUID(0,0);
 private static Access access;
 public static final OptionalIntegration CAPABILITY=IntegrationSupport.capability("envelope","identity",()->{
  access=new Access();return "PlayerAddress owner codec; KnownPlayers UUID routing";
 });
 private static final class Access {
  final Class<?> address=Class.forName(BASE+"mail.address.type.PlayerAddress"),known=Class.forName(BASE+"KnownPlayers"),data=Class.forName(BASE+"KnownPlayers$PlayerData"),service=Class.forName(BASE+"mail.MailService");
  final Method name=address.getMethod("getString"),of=service.getMethod("of",ServerLevel.class),players=service.getMethod("getKnownPlayers"),profile=data.getMethod("getProfile"),destination=data.getMethod("getDefaultAddress");
  final Constructor<?> playerData=data.getConstructor(GameProfile.class,Optional.class);
  final Field entries=known.getDeclaredField("data");
  Access()throws Exception{if(!EnvelopeAddressOwner.class.isAssignableFrom(address))throw new IllegalStateException("Envelope address mixin unavailable");entries.setAccessible(true);}
 }
 private static MinecraftServer server;
 private static ExecutorService io;
 private static long generation,next;
 private static boolean ready,busy,journalDirty,resolving;
 private static final Map<String,UUID> legacy=new LinkedHashMap<>();
 private static final Set<String> conflicts=new HashSet<>();
 private static Path file(){return server.getWorldPath(LevelResource.ROOT).resolve("rivet/envelope-identities.json");}
 public static boolean active(){var current=ServerLifecycleHooks.getCurrentServer();return current!=null&&current.isSameThread()&&CAPABILITY.available();}
 private static Optional<IntegrationApi.Identity> identities(){return ServerProviders.find(IntegrationApi.IDENTITY,"rivet");}
 @SuppressWarnings("unchecked") private static Map<String,Object> entries(Object known)throws Exception{return (Map<String,Object>)access.entries.get(known);}
 private static Object known()throws Exception{return access.players.invoke(access.of.invoke(null,server.overworld()));}
 public static UUID owner(Object address){
  if(!(address instanceof EnvelopeAddressOwner binding))return null;
  if(binding.rivet$owner()!=null)return binding.rivet$owner();
  if(!active()||!ready||resolving)return null;
  resolving=true;try{return CAPABILITY.call(()->{
   String name=(String)access.name.invoke(address);UUID id;
   if(binding.rivet$legacy()){
    id=legacy.get(name.toLowerCase(Locale.ROOT));
    if(id==null)for(var entry:entries(known()).entrySet())if(entry.getKey().equalsIgnoreCase(name)){UUID candidate=((GameProfile)access.profile.invoke(entry.getValue())).getId();if(id!=null&&!id.equals(candidate)){id=UNKNOWN;break;}id=candidate;}
   }
   else id=currentOwner(name);
   binding.rivet$owner(id==null?UNKNOWN:id);return binding.rivet$owner();
  },()->UNKNOWN);}finally{resolving=false;}
 }
 /** KnownPlayers supplies a UUID hint, never authority for an obsolete name. */
 private static UUID currentOwner(String name)throws Exception{
  var api=identities().orElse(null);if(api==null)return null;
  var direct=api.byName(name);if(direct.isPresent())return direct.get().uuid();
  UUID selected=null;
  for(Object value:entries(known()).values()){
   var saved=(GameProfile)access.profile.invoke(value);if(!name.equalsIgnoreCase(saved.getName()))continue;
   var current=api.byId(saved.getId());if(current.isEmpty()||!name.equalsIgnoreCase(current.get().name()))continue;
   UUID candidate=current.get().uuid();if(selected!=null&&!selected.equals(candidate))return null;selected=candidate;
  }
  return selected;
 }
 public static Optional<?> defaultAddress(Object known,Object address){
  UUID id=owner(address);if(id==null||id.equals(UNKNOWN))return Optional.empty();
  return CAPABILITY.call(()->{
   UUID canonical=identities().flatMap(api->api.verifiedAlias(id)).orElse(id);
   Object selected=null;for(Object value:entries(known).values()){
    var profile=(GameProfile)access.profile.invoke(value);
    if(profile.getId().equals(canonical)||profile.getId().equals(id)){
     if(selected!=null&&!access.destination.invoke(selected).equals(access.destination.invoke(value)))return Optional.empty();selected=value;
    }
   }
   return selected==null?Optional.empty():(Optional<?>)access.destination.invoke(selected);
  },Optional::empty);
 }
 public static boolean route(){var current=ServerLifecycleHooks.getCurrentServer();return current!=null&&current.isSameThread();}
 public static boolean mayUpdate(Object known,net.minecraft.world.entity.player.Player player){
  if(!active())return false;
  return CAPABILITY.call(()->{Object data=entries(known).get(player.getScoreboardName());return data==null||((GameProfile)access.profile.invoke(data)).getId().equals(player.getUUID());},()->false);
 }
 public static Optional<?> serviceAddress(Object service,Object address){return CAPABILITY.call(()->defaultAddress(access.players.invoke(service),address),Optional::empty);}
 public static void stop(){generation++;server=null;ready=busy=journalDirty=false;next=0;legacy.clear();conflicts.clear();if(io!=null)io.shutdownNow();io=null;access=null;CAPABILITY.reset();}
 /** Startup-only read, completed before Envelope begins ticking deliveries. */
 public static void start(MinecraftServer current){
  if(!CAPABILITY.available())return;if(io!=null)io.shutdownNow();server=current;generation++;ready=busy=journalDirty=false;legacy.clear();conflicts.clear();next=0;
  io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet Envelope storage");t.setDaemon(true);return t;});
  CAPABILITY.call(()->{Path path=file();if(Files.exists(path)){if(Files.size(path)>4*1024*1024)throw new IllegalStateException("Envelope identity journal too large");Json.parse(Files.readString(path)).entrySet().forEach(entry->legacy.put(entry.getKey(),UUID.fromString(entry.getValue().getAsString())));}ready=true;return true;},()->false);
 }
 public static void tick(MinecraftServer current){
  if(!CAPABILITY.available())return;
  if(server!=current){start(current);return;}
  long now=System.currentTimeMillis();if(!ready||busy||now<next)return;next=now+1000;
  CAPABILITY.call(()->{synchronize(current);return true;},()->false);
 }
 private record Rename(String from,String to,Object previous,Object replacement){}
 private static void synchronize(MinecraftServer current)throws Exception{
  var api=identities().orElse(null);if(api==null)return;Object known=known();var entries=entries(known);var changes=new ArrayList<Rename>();boolean added=false;
  var discovered=new LinkedHashMap<String,UUID>();
  for(var entry:entries.entrySet()){String key=entry.getKey().toLowerCase(Locale.ROOT);if(!legacy.containsKey(key)){UUID id=((GameProfile)access.profile.invoke(entry.getValue())).getId();discovered.merge(key,id,(first,second)->first.equals(second)?first:UNKNOWN);}}
  for(var entry:discovered.entrySet()){legacy.put(entry.getKey(),entry.getValue());added=true;}
  for(var entry:new ArrayList<>(entries.entrySet())){
   GameProfile before=(GameProfile)access.profile.invoke(entry.getValue());String key=entry.getKey().toLowerCase(Locale.ROOT);
   var identity=api.byId(before.getId());if(identity.isEmpty()||identity.get().name().equals(entry.getKey()))continue;
   var after=identity.get();Object occupied=entries.get(after.name());
   if(occupied!=null&&occupied!=entry.getValue()){
    if(conflicts.add(key))com.mojang.logging.LogUtils.getLogger().warn("Envelope address rename conflict for UUID {}; original records retained",before.getId());continue;
   }
   changes.add(new Rename(entry.getKey(),after.name(),entry.getValue(),access.playerData.newInstance(new GameProfile(after.uuid(),after.name()),access.destination.invoke(entry.getValue()))));
  }
  journalDirty|=added;if(!journalDirty&&changes.isEmpty())return;
  // Persist the legacy name bindings and a pre-change backup before editing Envelope's data.
  var journal=new JsonObject();legacy.forEach((name,id)->journal.addProperty(name,id.toString()));
  CompoundTag backup=changes.isEmpty()?null:((SavedData)known).save(new CompoundTag(),current.registryAccess()).copy();
  Path path=file();long epoch=generation;busy=true;
  io.execute(()->{try{
   Files.createDirectories(path.getParent());if(backup!=null){Path backups=path.getParent().resolve("envelope-backups");Files.createDirectories(backups);NbtIo.writeCompressed(backup,backups.resolve(System.currentTimeMillis()+"-"+UUID.randomUUID()+".nbt"));}
   Json.write(path,journal);
   current.execute(()->{if(epoch!=generation||server!=current)return;journalDirty=false;try{
    for(var change:changes){if(entries.get(change.from)!=change.previous||entries.containsKey(change.to))continue;var profile=(GameProfile)access.profile.invoke(change.previous);if(api.byId(profile.getId()).map(IntegrationApi.Player::name).filter(change.to::equals).isEmpty())continue;entries.remove(change.from);entries.put(change.to,change.replacement);((SavedData)known).setDirty();}
   }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot apply Envelope identity migration; original records retained",failure);}finally{busy=false;}});
  }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot persist Envelope identity migration; original records retained",failure);current.execute(()->{if(epoch==generation)busy=false;});}});
 }
 private EnvelopeBindings(){}
}
