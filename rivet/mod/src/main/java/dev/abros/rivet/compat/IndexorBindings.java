package dev.abros.rivet.compat;

import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.server.AuthServer;
import dev.abros.rivet.server.ServerProviders;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Indexor owns storage and rollback. Rivet supplies identity and access through Java API. */
public final class IndexorBindings {
 public static final dev.abros.rivet.core.OptionalIntegration CAPABILITY=IntegrationSupport.capability("ledger","history",()->{
  if(!CompatibilityMixinPlugin.indexorSupported())throw new IllegalStateException("Unsupported Indexor implementation");
  for(var type:List.of("ledger.events.LedgerEventHandler","ledger.core.LedgerManager","ledger.storage.SQLiteStorage","ledger.storage.JsonStorage","ledger.commands.InspectCommand"))if(!IndexorBridge.class.isAssignableFrom(Class.forName(type,false,IndexorBindings.class.getClassLoader())))throw new IllegalStateException("Indexor hook unavailable: "+type);
  Class.forName("ledger.storage.ILogStorage").getMethod("queryPlayer",String.class,int.class);
  Class.forName("ledger.core.LedgerManager").getMethod("log",Class.forName("ledger.core.LogEntry"));
  return "Indexor UUID lookup, authenticated access and cancelled block events";
 });
 private static final java.util.concurrent.ConcurrentMap<String,String> names=new java.util.concurrent.ConcurrentHashMap<>();
 private static final ThreadLocal<BlockEvent> event=new ThreadLocal<>();
 private record Pending(BlockEvent event,Object entry){}
 private static final List<Pending> pending=new ArrayList<>();
 public static BlockEvent begin(BlockEvent value){var old=event.get();event.set(value);return old;}
 public static void end(BlockEvent old){if(old==null)event.remove();else event.set(old);}
 public static boolean defer(Object entry){var current=event.get();if(current==null)return false;pending.add(new Pending(current,entry));return true;}
 public static void flush(){
  if(pending.isEmpty())return;
  var batch=new ArrayList<>(pending);pending.clear();
  try {
   var manager=Class.forName("ledger.core.LedgerManager");var target=manager.getMethod("getInstance").invoke(null);
   var log=manager.getMethod("log",Class.forName("ledger.core.LogEntry"));
   for(var row:batch)if(!(row.event() instanceof net.neoforged.bus.api.ICancellableEvent cancelled)||!cancelled.isCanceled())log.invoke(target,row.entry());
  }catch(ReflectiveOperationException failure){throw new IllegalStateException("Indexor journal delivery failed",failure);}
 }
 public static void clear(){pending.clear();event.remove();names.clear();CAPABILITY.reset();}
 private static <T>T identity(Supplier<T> action){
  var server=ServerLifecycleHooks.getCurrentServer();if(server==null)throw new IllegalStateException("Server unavailable");
  if(server.isSameThread())return action.get();
  try{return server.submit(action).get(5,TimeUnit.SECONDS);}catch(Exception failure){throw new IllegalStateException("Indexor identity lookup failed",failure);}
 }
 public static String uuid(String name){return identity(()->ServerProviders.find(IntegrationApi.IDENTITY,"rivet").flatMap(p->p.byName(name)).map(p->{
  if(names.size()>4096)names.clear();names.put(p.uuid().toString(),p.name());return p.uuid().toString();
 }).orElse(null));}
 /** Snapshot requested identities before async filtering; never schedule one server task per row. */
 public static void prepare(String command){
  names.clear();
  for(var word:command.split("\\s+"))if(word.matches("[A-Za-z0-9_]{1,16}"))uuid(word);
 }
 public static String name(Object entry){try{
  String original=(String)entry.getClass().getMethod("getPlayerName").invoke(entry);
  String uuid=(String)entry.getClass().getMethod("getPlayerUuid").invoke(entry);
  UUID id;try{id=UUID.fromString(uuid);}catch(IllegalArgumentException systemActor){return original;}
  String cached=names.get(uuid);if(cached!=null)return cached;
  String current=identity(()->ServerProviders.find(IntegrationApi.IDENTITY,"rivet").flatMap(p->p.byId(id)).map(IntegrationApi.Player::name).orElse(uuid));
  if(names.size()>4096)names.clear();names.put(uuid,current);return current;
 }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
 public static boolean allowed(CommandSourceStack source){
  if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player))return source.hasPermission(2);
  if(!AuthServer.authenticated(player))return false;
  if(player.server.getPlayerList().getPlayer(player.getUUID())!=player)return source.hasPermission(2);
  return source.hasPermission(2)||ServerProviders.find(IntegrationApi.PERMISSIONS,"luckperms").map(p->p.allowed(player.getUUID(),"rivet.indexor.admin")).orElse(false);
 }
 private IndexorBindings(){}
}
