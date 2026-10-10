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
 private static final Map<String,JsonObject> bindings=new LinkedHashMap<>();private static final TaskStockWork work=new TaskStockWork(ServerFeatures::storage);private static CommunityTasks tasks;private static int tick,cursor;
 public static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->tick(event.getServer()));}
 static void stop(){work.reset();tasks=null;bindings.clear();tick=cursor=0;}
 static void start(MinecraftServer server,CommunityStore store)throws Exception{tasks=new CommunityTasks(ServerDatabase.get(),store);bindings.clear();work.reset();tick=cursor=0;for(var e:tasks.bindings()){var row=e.getAsJsonObject();bindings.put(Json.str(row,"key"),row);}}
 public static void signEdited(ServerPlayer player,SignBlockEntity sign,boolean front){if(tasks==null||!AuthServer.authenticated(player))return;var text=sign.getText(front);String marker=text.getMessage(0,false).getString().strip();String key=player.serverLevel().dimension().location()+":"+sign.getBlockPos().asLong();if(!TaskCodes.isMarker(marker)){if(bindings.containsKey(key))remove(player.server,key);return;}
  String label=text.getMessage(1,false).getString().strip(),code=text.getMessage(2,false).getString().strip();var body=new JsonObject();body.addProperty("dimension",player.serverLevel().dimension().location().toString());body.addProperty("sign",sign.getBlockPos().asLong());body.addProperty("front",front);body.addProperty("label",label);body.addProperty("code",TaskCodes.normalize(code));var sample=sample(player.serverLevel(),body);if(sample==null){player.sendSystemMessage(Component.translatable("rivet.core.rivet_attach_a_sign_to_a_af17a65f"));return;}body.addProperty("inventory",sample.key());body.add("items",sample.items());body.addProperty("at",System.currentTimeMillis());
  if(bindings.values().stream().anyMatch(j->!Json.str(j,"key").equals(key)&&Json.opt(j,"inventory","").equals(sample.key()))){player.sendSystemMessage(Component.translatable("rivet.core.rivet_this_container_is_already_linked_8b2e6bd8"));return;}var store=tasks;long token=work.generation();var actor=new CommunityStore.Actor(player.getUUID().toString(),player.getGameProfile().getName(),ServerFeatures.admin(player),false);
  try{work.schedule(key,()->{try{String id=store.bind(actor,label,code,key,body);player.server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,sample.key(),()->{body.addProperty("key",key);body.addProperty("task",id);bindings.put(key,body);player.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.core.rivet_storage_linked_to_task_e34a8c79")+TaskCodes.normalize(code)));})));}catch(Exception ex){player.server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,sample.key(),()->{player.sendSystemMessage(Component.literal("Rivet: "+(ex instanceof CommunityFailure||ex instanceof IllegalArgumentException?ex.getMessage():dev.abros.rivet.core.Messages.text("rivet.core.could_not_connect_storage_e75ec3bc"))));})));}});}catch(RejectedExecutionException ex){work.cancel(sample.key());player.sendSystemMessage(Component.translatable("rivet.core.rivet_server_busy_try_again_later_64d662ec"));}
 }
 private record Sample(String key,JsonObject items){}
 private static Sample sample(ServerLevel level,JsonObject body){var pos=BlockPos.of(body.get("sign").getAsLong());if(!level.hasChunkAt(pos))return null;var state=level.getBlockState(pos);if(!(level.getBlockEntity(pos) instanceof SignBlockEntity))return null;if(!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))return null;var container=pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());if(!level.hasChunkAt(container))return null;var target=level.getBlockState(container);String identity=level.dimension().location()+":"+container.asLong();if(target.getBlock() instanceof ChestBlock&&target.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){var other=container.relative(ChestBlock.getConnectedDirection(target));if(!level.hasChunkAt(other))return null;identity=level.dimension().location()+":"+Math.min(container.asLong(),other.asLong());}
  var inventory=level.getCapability(Capabilities.ItemHandler.BLOCK,container,null);if(inventory==null||inventory.getSlots()>4096)return null;var counts=new TreeMap<String,Long>();for(int slot=0;slot<inventory.getSlots();slot++){var stack=inventory.getStackInSlot(slot);if(!stack.isEmpty())counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),(long)stack.getCount(),Long::sum);}var items=new JsonObject();counts.forEach(items::addProperty);return new Sample(identity,items);
 }
 static void tick(MinecraftServer server){if(tasks==null||++tick%20!=0||bindings.isEmpty())return;var keys=new ArrayList<>(bindings.keySet());for(int n=0;n<Math.min(8,keys.size());n++){String key=keys.get((cursor+n)%keys.size());var row=bindings.get(key);var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(Json.str(row,"dimension"))));if(level==null)continue;var pos=BlockPos.of(row.get("sign").getAsLong());if(!level.hasChunkAt(pos))continue;if(!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)){remove(server,key);continue;}var text=sign.getText(row.get("front").getAsBoolean());if(!TaskCodes.isMarker(text.getMessage(0,false).getString())||!text.getMessage(1,false).getString().strip().equalsIgnoreCase(Json.str(row,"label"))||!TaskCodes.normalize(text.getMessage(2,false).getString()).equals(Json.str(row,"code"))){remove(server,key);continue;}var sample=sample(level,row);if(sample==null)continue;boolean changed=!sample.items().equals(row.get("items"));if(!changed&&System.currentTimeMillis()-row.get("at").getAsLong()<30000)continue;var updated=row.deepCopy();updated.add("items",sample.items());updated.addProperty("at",System.currentTimeMillis());updated.addProperty("inventory",sample.key());persist(server,key,updated,false);}
  cursor=(cursor+8)%Math.max(1,keys.size());
 }
 private static void remove(MinecraftServer server,String key){persist(server,key,new JsonObject(),true);}
 private static void persist(MinecraftServer server,String key,JsonObject body,boolean remove){if(!work.begin(key))return;var store=tasks;long token=work.generation();try{work.schedule(key,()->{try{boolean exists=store.stock(key,body,remove);server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,key,()->{if(remove||!exists)bindings.remove(key);else bindings.put(key,body);})));}catch(Exception ex){server.execute(dev.abros.rivet.core.Messages.capture(()->work.complete(token,key,()->{})));com.mojang.logging.LogUtils.getLogger().warn("Cannot update task stock",ex);}});}catch(RejectedExecutionException ex){work.cancel(key);}}
}
