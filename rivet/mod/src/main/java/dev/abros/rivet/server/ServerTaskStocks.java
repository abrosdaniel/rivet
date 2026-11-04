package dev.abros.rivet.server;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
/** Bounded checks on the game thread; database writes run on the storage worker. Never loads chunks. */
public final class ServerTaskStocks {
 private record ChunkKey(String dimension,int x,int z){}
 private static final Map<ChunkKey,Set<String>> chunks=new HashMap<>();
 private static final Map<String,Long> sampledAt=new HashMap<>();
 private static final StockScanQueue scanQueue=new StockScanQueue();private static int sampledSlots;
 private static ChunkKey chunk(JsonObject row){var pos=BlockPos.of(row.get("sign").getAsLong());return new ChunkKey(Json.str(row,"dimension"),pos.getX()>>4,pos.getZ()>>4);}
 private static void track(MinecraftServer server,String key,JsonObject row){bindings.put(key,row);var c=chunk(row);chunks.computeIfAbsent(c,k->new HashSet<>()).add(key);var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(c.dimension())));scanQueue.active(key,level!=null&&level.hasChunk(c.x(),c.z()));}
 private static void untrack(String key){var row=bindings.remove(key);sampledAt.remove(key);scanQueue.active(key,false);if(row!=null){var c=chunk(row);var keys=chunks.get(c);if(keys!=null){keys.remove(key);if(keys.isEmpty())chunks.remove(c);}}}
 public static void changed(ServerLevel level,BlockPos pos){if(tasks==null)return;for(int x=(pos.getX()-1)>>4;x<=(pos.getX()+1)>>4;x++)for(int z=(pos.getZ()-1)>>4;z<=(pos.getZ()+1)>>4;z++){var keys=chunks.get(new ChunkKey(level.dimension().location().toString(),x,z));if(keys!=null)for(var key:keys){sampledAt.remove(key);scanQueue.changed(key);}}}
 private static void loaded(ServerLevel level,int x,int z,boolean loaded){var keys=chunks.get(new ChunkKey(level.dimension().location().toString(),x,z));if(keys!=null)for(var key:keys){scanQueue.active(key,loaded);if(loaded){sampledAt.remove(key);scanQueue.changed(key);}}}
 private static final Map<String,JsonObject> bindings=new LinkedHashMap<>();private static final TaskStockWork work=new TaskStockWork(ServerFeatures::storage);private static CommunityTasks tasks;
 public static void install(){
 NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e)->{if(e.getLevel() instanceof ServerLevel level)loaded(level,e.getChunk().getPos().x,e.getChunk().getPos().z,true);});
 NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel level)loaded(level,e.getChunk().getPos().x,e.getChunk().getPos().z,false);});NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->tick(event.getServer()));}
 static void stop(){work.reset();tasks=null;bindings.clear();chunks.clear();scanQueue.clear();sampledAt.clear();}
 static void start(MinecraftServer server,CommunityStore store)throws Exception{tasks=new CommunityTasks(ServerDatabase.get(),store);bindings.clear();chunks.clear();scanQueue.clear();sampledAt.clear();work.reset();for(var e:tasks.bindings()){var row=e.getAsJsonObject();track(server,Json.str(row,"key"),row);}}
 public static void signEdited(ServerPlayer player,SignBlockEntity sign,boolean front){if(tasks==null||!AuthServer.authenticated(player))return;var text=sign.getText(front);String marker=text.getMessage(0,false).getString().strip();String key=player.serverLevel().dimension().location()+":"+sign.getBlockPos().asLong();if(!TaskCodes.isMarker(marker)){if(bindings.containsKey(key))remove(player.server,key);return;}
  String label=text.getMessage(1,false).getString().strip(),code=text.getMessage(2,false).getString().strip();var body=new JsonObject();body.addProperty("dimension",player.serverLevel().dimension().location().toString());body.addProperty("sign",sign.getBlockPos().asLong());body.addProperty("front",front);body.addProperty("label",label);body.addProperty("code",TaskCodes.normalize(code));var sample=sample(player.serverLevel(),body);if(sample==null){player.sendSystemMessage(Component.translatable("rivet.core.rivet_attach_a_sign_to_a_af17a65f"));return;}body.addProperty("inventory",sample.key());body.add("items",sample.items());body.addProperty("at",System.currentTimeMillis());
  if(bindings.values().stream().anyMatch(j->!Json.str(j,"key").equals(key)&&Json.opt(j,"inventory","").equals(sample.key()))){player.sendSystemMessage(Component.translatable("rivet.core.rivet_this_container_is_already_linked_8b2e6bd8"));return;}var store=tasks;long token=work.generation();var actor=new CommunityStore.Actor(player.getUUID().toString(),player.getGameProfile().getName(),ServerFeatures.admin(player),false);
  try{work.schedule(key,()->{try{String id=store.bind(actor,label,code,key,body);player.server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,sample.key(),()->{body.addProperty("key",key);body.addProperty("task",id);track(player.server,key,body);player.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.core.rivet_storage_linked_to_task_e34a8c79")+TaskCodes.normalize(code)));})));}catch(Exception ex){player.server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,sample.key(),()->{player.sendSystemMessage(Component.literal("Rivet: "+(ex instanceof CommunityFailure||ex instanceof IllegalArgumentException?ex.getMessage():dev.abros.rivet.core.Messages.text("rivet.core.could_not_connect_storage_e75ec3bc"))));})));}});}catch(RejectedExecutionException ex){work.cancel(sample.key());player.sendSystemMessage(Component.translatable("rivet.core.rivet_server_busy_try_again_later_64d662ec"));}
 }
 private record Sample(String key,JsonObject items){}
 private static Sample sample(ServerLevel level,JsonObject body){return sample(level,body,false);}
 private static Sample sample(ServerLevel level,JsonObject body,boolean budgeted){var pos=BlockPos.of(body.get("sign").getAsLong());if(!level.hasChunkAt(pos))return null;var state=level.getBlockState(pos);if(!(level.getBlockEntity(pos) instanceof SignBlockEntity))return null;if(!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return null;var container=pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());if(!level.hasChunkAt(container))return null;var target=level.getBlockState(container);String identity=level.dimension().location()+":"+container.asLong();if(target.getBlock() instanceof ChestBlock&&target.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){var other=container.relative(ChestBlock.getConnectedDirection(target));if(!level.hasChunkAt(other))return null;identity=level.dimension().location()+":"+Math.min(container.asLong(),other.asLong());}
  var inventory=level.getCapability(Capabilities.ItemHandler.BLOCK,container,null);if(inventory==null||inventory.getSlots()>4096)return null;if(budgeted){if(sampledSlots+inventory.getSlots()>8192)return null;sampledSlots+=inventory.getSlots();}var counts=new TreeMap<String,Long>();for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),(long)stack.getCount(),Long::sum);}var items=new JsonObject();counts.forEach(items::addProperty);return new Sample(identity,items);
 }
 static void tick(MinecraftServer server){if(tasks==null||bindings.isEmpty())return;sampledSlots=0;long deadline=System.nanoTime()+2000000L;for(int n=0,limit=Math.min(8,scanQueue.size());n<limit&&System.nanoTime()<deadline;n++){String key=scanQueue.poll();if(key==null)break;long now=System.currentTimeMillis();if(now-sampledAt.getOrDefault(key,0L)<1000)continue;sampledAt.put(key,now);var row=bindings.get(key);if(row==null)continue;var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(Json.str(row,"dimension"))));if(level==null)continue;var pos=BlockPos.of(row.get("sign").getAsLong());if(!level.hasChunkAt(pos)){scanQueue.active(key,false);continue;}if(!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)){remove(server,key);continue;}var text=sign.getText(row.get("front").getAsBoolean());if(!TaskCodes.isMarker(text.getMessage(0,false).getString())||!text.getMessage(1,false).getString().strip().equalsIgnoreCase(Json.str(row,"label"))||!TaskCodes.normalize(text.getMessage(2,false).getString()).equals(Json.str(row,"code"))){remove(server,key);continue;}var sample=sample(level,row,true);if(sample==null)continue;boolean changed=!sample.items().equals(row.get("items"));if(!changed&&System.currentTimeMillis()-row.get("at").getAsLong()<30000)continue;var updated=row.deepCopy();updated.add("items",sample.items());updated.addProperty("at",System.currentTimeMillis());updated.addProperty("inventory",sample.key());persist(server,key,updated,false);}

 }
 private static void remove(MinecraftServer server,String key){persist(server,key,new JsonObject(),true);}
 private static void persist(MinecraftServer server,String key,JsonObject body,boolean remove){if(!work.begin(key))return;var store=tasks;long token=work.generation();try{work.schedule(key,()->{try{boolean exists=store.stock(key,body,remove);server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,key,()->{if(remove||!exists)untrack(key);else track(server,key,body);})));}catch(Exception ex){server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,key,()->{})));com.mojang.logging.LogUtils.getLogger().warn("Cannot update task stock",ex);}});}catch(RejectedExecutionException ex){work.cancel(key);}}
}
