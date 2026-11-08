package dev.abros.rivet.server;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.*;
/** Opt-in black-box checks against the unmodified Indexor JAR, through live game actions. */
@EventBusSubscriber(modid="rivet")
public final class IndexorFixture {
 private static final BlockPos PLACE=new BlockPos(16,100,16),BROKEN=new BlockPos(18,100,16),CHEST=new BlockPos(20,100,16),COPYCAT=new BlockPos(24,100,16);
 private static final BlockPos CANCELLED_PLACE=new BlockPos(26,100,16);
 private static final Set<BlockPos> CANCELLED=Set.of(new BlockPos(22,100,16));
 private static int checks;
 private static net.minecraft.nbt.CompoundTag copycatBefore;
 @SubscribeEvent public static void cancel(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event){if(System.getenv("RIVET_INDEXOR_TEST")!=null&&CANCELLED.contains(event.getPos()))event.setCanceled(true);}
 @SubscribeEvent public static void cancelPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event){if(System.getenv("RIVET_INDEXOR_TEST")!=null&&event.getPos().equals(CANCELLED_PLACE))event.setCanceled(true);}
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(System.getenv("RIVET_INDEXOR_TEST")==null)return;
  event.getDispatcher().register(Commands.literal("indexorFixture").executes(c->{
   var player=c.getSource().getPlayerOrException();if(!player.getGameProfile().getName().startsWith("IdxTest")||!AuthServer.authenticated(player))return 0;
   CompletableFuture.runAsync(()->{try{run(player);}catch(Throwable failure){System.out.println("RIVET_INDEXOR_FAILED");failure.printStackTrace();player.server.execute(()->player.sendSystemMessage(Component.literal("INDEXOR_FAILED "+failure.getClass().getSimpleName())));}});return 1;
  }));
 }
 private static void setPermission(ServerPlayer p,boolean value)throws Exception{
  var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
  var manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
  var user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());
  var ut=Class.forName("net.luckperms.api.model.user.User");var data=ut.getMethod("transientData").invoke(user);
  var nt=Class.forName("net.luckperms.api.node.Node");var map=Class.forName("net.luckperms.api.model.data.NodeMap");map.getMethod("clear").invoke(data);
  var builder=nt.getMethod("builder",String.class).invoke(null,"rivet.indexor.admin");var bt=Class.forName("net.luckperms.api.node.NodeBuilder");bt.getMethod("value",boolean.class).invoke(builder,value);
  map.getMethod("add",nt).invoke(data,bt.getMethod("build").invoke(builder));
  Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(ut.getMethod("getCachedData").invoke(user));
 }
 private static Object storage()throws Exception {var manager=Class.forName("ledger.core.LedgerManager");return manager.getMethod("getStorage").invoke(manager.getMethod("getInstance").invoke(null));}
 private static List<?> query(String method,String value)throws Exception{return (List<?>)Class.forName("ledger.storage.ILogStorage").getMethod(method,String.class,int.class).invoke(storage(),value,1000);}
 private static Object get(Object entry,String name)throws Exception{return entry.getClass().getMethod(name).invoke(entry);}
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void run(ServerPlayer p)throws Exception {
  var server=p.server;String name=p.getGameProfile().getName();String uuid=p.getUUID().toString();
  check(AuthServer.enabled(),"Auth must be enabled");
  var marker=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("indexor-test-uuid.txt");
  boolean renamed=name.endsWith("After");
  if(renamed){check(java.nio.file.Files.readString(marker).equals(uuid),"Rename changed UUID");check(!query("queryPlayer",uuid).isEmpty(),"History absent after rename/restart");}
  else java.nio.file.Files.writeString(marker,uuid);
  int existingRows=query("queryPlayer",uuid).size();
  server.submit(()->{
   var level=p.serverLevel();server.getPlayerList().op(p.getGameProfile());p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(17,101,18);
   p.getInventory().clearContent();
   level.setBlock(PLACE.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(PLACE,Blocks.AIR.defaultBlockState(),3);
   level.setBlock(BROKEN,Blocks.GOLD_BLOCK.defaultBlockState(),3);level.setBlock(CHEST,Blocks.CHEST.defaultBlockState(),3);
   ((ChestBlockEntity)level.getBlockEntity(CHEST)).setItem(0,new ItemStack(Items.DIAMOND,7));
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));
   p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(PLACE.below()).add(0,.5,0),Direction.UP,PLACE.below(),false));
   p.gameMode.destroyBlock(BROKEN);
   try {
    var copycat=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("copycats:copycat_block"));
    check(copycat!=Blocks.AIR,"Copycats block missing");level.setBlock(COPYCAT,copycat.defaultBlockState(),3);
    var be=level.getBlockEntity(COPYCAT);var type=Class.forName("com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity");
    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_WOOL));
    p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(COPYCAT),Direction.UP,COPYCAT,false));
    check(type.getMethod("getMaterial").invoke(be).equals(Blocks.RED_WOOL.defaultBlockState()),"Real material application failed");
    copycatBefore=be.saveWithoutMetadata(level.registryAccess()).copy();
    p.gameMode.destroyBlock(COPYCAT);
   } catch(Exception failure){throw new RuntimeException(failure);}
   p.openMenu((ChestBlockEntity)level.getBlockEntity(CHEST));p.containerMenu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,p);p.closeContainer();
   for(var pos:CANCELLED){level.setBlock(pos,Blocks.EMERALD_BLOCK.defaultBlockState(),3);p.gameMode.destroyBlock(pos);check(level.getBlockState(pos).is(Blocks.EMERALD_BLOCK),"Cancelled break mutated world");}
   level.setBlock(CANCELLED_PLACE.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(CANCELLED_PLACE,Blocks.AIR.defaultBlockState(),3);
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE));p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(CANCELLED_PLACE.below()).add(0,.5,0),Direction.UP,CANCELLED_PLACE.below(),false));
   check(level.getBlockState(CANCELLED_PLACE).isAir(),"Cancelled placement mutated world");
   var root=server.getCommands().getDispatcher().getRoot().getChild("ledger");check(root!=null,"Ledger command missing");
   check(root.canUse(p.createCommandSourceStack()),"OP cannot use ledger");
   check(!root.canUse(p.createCommandSourceStack().withPermission(0)),"Permission zero can use ledger");
   try {
    p.server.getPlayerList().deop(p.getGameProfile());setPermission(p,true);
    check(root.canUse(p.createCommandSourceStack()),"LuckPerms grant did not permit ledger");
    setPermission(p,false);check(!root.canUse(p.createCommandSourceStack()),"Revoked permission still permits ledger");
    setPermission(p,true);check(!p.hasPermissions(2),"LuckPerms test became OP");
   }catch(Exception e){throw new RuntimeException(e);}
   server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ledger inspect");
   server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ledger inspect");
   return null;
  }).get(15,TimeUnit.SECONDS);
  List<?> rows=List.of();long deadline=System.currentTimeMillis()+15000;
  while(System.currentTimeMillis()<deadline){rows=query("queryPlayer",uuid);if(rows.size()>=existingRows+4)break;Thread.sleep(200);}
  check(rows.size()>=existingRows+4,"Indexor did not persist all new actions");
  Set<String> actions=new HashSet<>();for(var row:rows){check(get(row,"getPlayerUuid").equals(uuid),"Foreign UUID");actions.add(get(row,"getActionType").toString());}
  check(rows.stream().anyMatch(e->{try{return BROKEN.equals(get(e,"getPos"));}catch(Exception ex){throw new RuntimeException(ex);}}),"Real block break not recorded");
  long cancelled=0;for(var row:rows)if(CANCELLED.contains(get(row,"getPos"))||CANCELLED_PLACE.equals(get(row,"getPos")))cancelled++;
  check(cancelled==0,"Cancelled break was logged");
  System.out.println("RIVET_INDEXOR_OBSERVATION cancelledBreakRows="+cancelled+" actions="+actions+" rows="+rows.size());
  check(rows.stream().anyMatch(e->{try{return PLACE.equals(get(e,"getPos"));}catch(Exception ex){throw new RuntimeException(ex);}}),"Placement not recorded");
  check(rows.stream().anyMatch(e->{try{return CHEST.equals(get(e,"getPos"));}catch(Exception ex){throw new RuntimeException(ex);}}),"Container transaction not recorded");
  server.submit(()->{server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ledger rollback "+name+" from 10m radius 16");return null;}).get(5,TimeUnit.SECONDS);
  var commandType=Class.forName("ledger.commands.InspectCommand");var pending=commandType.getDeclaredField("pendingRollbacks");pending.setAccessible(true);
  long until=System.currentTimeMillis()+15000;boolean ready=false;
  while(System.currentTimeMillis()<until){ready=server.submit(()->{try{return ((Map<?,?>)pending.get(null)).containsKey(p.getUUID());}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);if(ready)break;Thread.sleep(100);}
  check(ready,"Rollback preview not prepared");
  server.submit(()->{server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ledger rollback confirm");return null;}).get(5,TimeUnit.SECONDS);
  until=System.currentTimeMillis()+15000;boolean restored=false;
  while(System.currentTimeMillis()<until){restored=server.submit(()->p.serverLevel().getBlockState(BROKEN).is(Blocks.GOLD_BLOCK)).get(5,TimeUnit.SECONDS);if(restored)break;Thread.sleep(100);}
  check(restored,"Gold block rollback failed");
  server.submit(()->{
   try {var be=p.serverLevel().getBlockEntity(COPYCAT);var type=Class.forName("com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity");
    boolean material=be!=null&&type.isInstance(be)&&type.getMethod("getMaterial").invoke(be).equals(Blocks.RED_WOOL.defaultBlockState());
    check(material,"Copycat material was lost during rollback");
    check(!((ItemStack)type.getMethod("getConsumedItem").invoke(be)).isEmpty(),"Copycat consumed item lost");
    check(copycatBefore.equals(be.saveWithoutMetadata(p.serverLevel().registryAccess())),"Copycat NBT changed during rollback");
    System.out.println("RIVET_INDEXOR_COPYCAT restoredMaterial="+material+" nbtExact=true");
   }catch(Exception ex){throw new RuntimeException(ex);}return null;
  }).get(5,TimeUnit.SECONDS);
  if(renamed){
   check(query("queryPlayerByName",name).size()==rows.size(),"Current name did not include old UUID history");
   server.submit(()->{dev.abros.rivet.compat.IndexorBindings.prepare("ledger rollback IdxTestBefore from 10m radius 16");return null;}).get(5,TimeUnit.SECONDS);
   for(var row:rows)check(dev.abros.rivet.compat.IndexorBindings.name(row).equals(name),"Old stored name leaked into rollback filter");
  }
  if(renamed)System.out.println("RIVET_INDEXOR_RENAME uuidRows="+rows.size()+" oldNameRows="+query("queryPlayerByName","IdxTestBefore").size()+" newNameRows="+query("queryPlayerByName",name).size());
  server.submit(()->{server.getCommands().performPrefixedCommand(p.createCommandSourceStack(),"ledger player "+name);p.sendSystemMessage(Component.literal("INDEXOR_DONE checks="+checks));return null;}).get(5,TimeUnit.SECONDS);
  System.out.println("RIVET_INDEXOR_SERVER_OK name="+name+" checks="+checks);
 }
}
