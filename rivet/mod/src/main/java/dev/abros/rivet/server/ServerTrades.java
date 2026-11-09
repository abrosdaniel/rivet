package dev.abros.rivet.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.TradeWire;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;

/** All state transitions are serialized on the game thread; journal I/O is isolated.
 * The inventory's durable receipt resolves a crash between two different save files. */
public final class ServerTrades {
 public static final String RECEIPT="rivetTradeReceipt";
 private static ServerTrades active;
 private final MinecraftServer server;
 private final TradeJournal journal;
 private final TradeNoticeDelivery notices;
 private final ExecutorService io=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet trade journal");t.setDaemon(true);return t;});
 TradeBook book;
 private boolean busy,failed,stopping;
 private long nextExpiry;
 private final Map<String,String> closedCursors=new LinkedHashMap<>();
 private ServerTrades(MinecraftServer server)throws Exception{
  this.server=server;journal=new TradeJournal(server.getWorldPath(LevelResource.ROOT).resolve("rivet/trades"));
  var value=journal.recover((id,receipt)->{
   Path path=playerFile(UUID.fromString(id));if(!Files.exists(path))return false;
   try{return NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap()).getCompound("NeoForgeData").getString(RECEIPT).equals(receipt);}
   catch(Exception error){throw new IllegalStateException("Cannot recover trade receipt",error);}
  });book=value.entrySet().isEmpty()?new TradeBook():Json.GSON.fromJson(value,TradeBook.class);
  notices=new TradeNoticeDelivery(server.getWorldPath(LevelResource.ROOT).resolve("rivet/trades/notice-receipts.json"));notices.offer(book.notices,book.noticeSequence);
  for(String who:book.cursors.keySet()){String id=book.cursorDeals.get(who);if(id==null)id=book.deals.values().stream().filter(d->d.visible(who)).reduce((a,b)->b).map(d->d.id).orElse(null);if(id!=null&&!book.require(id,who).active())closedCursors.put(who,id);}
 }
 static void start(MinecraftServer server)throws Exception{active=new ServerTrades(server);}
 static void stop(){var s=active;if(s==null)return;s.stopping=true;s.notices.close();s.io.shutdown();try{s.io.awaitTermination(30,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}active=null;}
 public static void install(){
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone e)->{if(e.getOriginal().getPersistentData().contains(RECEIPT))e.getEntity().getPersistentData().putString(RECEIPT,e.getOriginal().getPersistentData().getString(RECEIPT));});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{var s=active;if(s==null||s.busy||s.failed||s.stopping)return;s.notices.offer(s.book.notices,s.book.noticeSequence);if(s.restoreClosedCursor())return;if(System.currentTimeMillis()<s.nextExpiry)return;s.nextExpiry=System.currentTimeMillis()+30000;var next=s.copy();if(next.expire(System.currentTimeMillis()))s.commit(null,next,null,null,()->s.refresh());});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->{
   var root=Commands.literal("rivet").then(Commands.literal("trades").executes(c->{list(c.getSource().getPlayerOrException());return 1;}).then(Commands.argument("tab",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,2)).then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,1000000)).executes(c->{list(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"tab"),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"page"));return 1;}))))
    .then(Commands.literal("trade").then(Commands.argument("player",StringArgumentType.word()).executes(c->{create(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"player"),false);return 1;})))
    .then(Commands.literal("gift").then(Commands.argument("player",StringArgumentType.word()).executes(c->{create(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"player"),true);return 1;})))
    .then(Commands.literal("tradeopen").then(Commands.argument("id",StringArgumentType.word()).executes(c->{open(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"id"));return 1;})))
    .then(Commands.literal("tradeinbox").executes(c->{var p=c.getSource().getPlayerOrException();var s=require(p);if(!s.available()){error(p,new IllegalArgumentException("trade.busy"));return 0;}var next=s.copy();if(!next.closedInbox.add(p.getStringUUID()))next.closedInbox.remove(p.getStringUUID());s.commit(null,next,null,null,()->list(p));return 1;}));
   e.getDispatcher().register(root);TradeAdministration.register(e);
  });
 }
 static ServerTrades require(ServerPlayer p){var s=active;if(s==null||s.failed||s.stopping||!AuthServer.authenticated(p)||!p.isAlive())throw new IllegalArgumentException("trade.unavailable");return s;}
 public static void list(ServerPlayer p){list(p,0,0);}
 public static void list(ServerPlayer p,int tab,int page){try{
  if(tab<0||tab>2||page<0||page>100000)throw new IllegalArgumentException("trade.denied");
  var s=require(p);String who=p.getStringUUID();var result=new JsonObject();result.addProperty("kind","list");result.addProperty("enabled",ServerDatabase.settings().flag("trade.enabled"));result.addProperty("tab",tab);result.addProperty("page",page);result.addProperty("inbox",!s.book.closedInbox.contains(who));
  // Journal insertion order is creation order, independent of changed expiry settings.
  var ordered=new ArrayList<>(s.book.deals.values());Collections.reverse(ordered);
  var selected=ordered.stream().filter(d->d.visible(who)&&(tab==2?!d.active():d.active()&&d.b.equals(who)==(tab==0))).skip((long)page*10).limit(11).toList();
  var rows=new JsonArray();for(var d:selected.subList(0,Math.min(10,selected.size()))){
   String other=d.a.equals(who)?d.b:d.a;var r=new JsonObject();r.addProperty("id",d.id);r.addProperty("createdAt",d.createdAt);r.addProperty("uuid",other);r.addProperty("name",d.a.equals(who)?d.nameB:d.nameA);r.addProperty("online",p.server.getPlayerList().getPlayer(UUID.fromString(other))!=null);r.addProperty("status",d.status);r.addProperty("incoming",d.b.equals(who));r.addProperty("expires",d.expires);
   String attention=d.frozenAt>0?"frozen":!d.active()?(d.items(who).isEmpty()&&!d.id.equals(s.book.cursorDeals.get(who))?d.status:"collect"):d.status.equals("draft")||d.dirty(who)?"draft":d.dirty(other)?"editing":d.accepted(who)?"waiting":"decision";
   r.addProperty("attention",attention);
   // Never disclose the other player's unpublished draft or serialize full item components.
   var give=d.active()?d.items(who):d.published(who);var receive=d.published(other);
   r.addProperty("giveCount",give.size());r.addProperty("receiveCount",receive.size());r.add("give",previews(give,p));r.add("receive",previews(receive,p));rows.add(r);
  }
  result.addProperty("more",selected.size()>10);result.add("rows",rows);PacketDistributor.sendToPlayer(p,new TradeWire.State(result.toString()));
 }catch(Exception error){error(p,error);}}
 static JsonArray previews(List<TradeBook.Item> items,ServerPlayer p){var result=new JsonArray();for(var item:items.stream().limit(3).toList()){
  var stack=TradeItems.decode(item,p.registryAccess());var icon=new JsonObject();String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();icon.addProperty("id",id.length()<=128?id:"minecraft:air");String name=stack.getHoverName().getString();icon.addProperty("name",name.substring(0,Math.min(32,name.length())));icon.addProperty("count",item.count());result.add(icon);
 }return result;}

 public static void create(ServerPlayer p,String name,boolean gift){try{
  var s=require(p);if(!s.available())throw new IllegalArgumentException("trade.busy");if(!ServerDatabase.settings().flag("trade.enabled"))throw new IllegalArgumentException("trade.disabled");
  var identity=new PlayerDirectory(p.server).byName(name).orElseThrow(()->new IllegalArgumentException("trade.player"));
  UUID target=identity.getId();String canonical=identity.getName();
  var next=s.copy();var d=next.create(p.getStringUUID(),p.getGameProfile().getName(),target.toString(),canonical,false,System.currentTimeMillis(),ServerDatabase.settings().number("trade.days")*86400000L,ServerDatabase.settings().number("trade.maxOutgoing"));
  s.commit(null,next,null,null,()->open(p,d.id));
 }catch(Exception error){error(p,error);}}
 public static void open(ServerPlayer p,String id){try{var s=require(p);var d=s.book.require(id,p.getStringUUID());if(s.busy)throw new IllegalArgumentException("trade.busy");p.openMenu(new SimpleMenuProvider((container,inventory,player)->new TradeMenu(container,inventory,id),Component.translatable("rivet.trade.title",d.a.equals(p.getStringUUID())?d.nameB:d.nameA)),buffer->buffer.writeUtf(id));}catch(Exception error){error(p,error);}}
 TradeBook copy(){return Json.GSON.fromJson(Json.GSON.toJson(book),TradeBook.class);}
 boolean available(){return !busy&&!failed&&!stopping;}
 void action(ServerPlayer p,TradeMenu menu,String action){try{if(!available())throw new IllegalArgumentException("trade.busy");var next=copy();var d=next.require(menu.dealId,p.getStringUUID());if(!action.equals("cancel")&&!ServerDatabase.settings().flag("trade.enabled"))throw new IllegalArgumentException("trade.disabled");if(action.equals("accept")){if(!ServerDatabase.settings().flag("trade.enabled"))throw new IllegalArgumentException("trade.disabled");int radius=ServerDatabase.settings().number("trade.radius");var other=server.getPlayerList().getPlayer(UUID.fromString(d.a.equals(p.getStringUUID())?d.b:d.a));if(radius>0&&(other==null||other.level()!=p.level()||other.distanceToSqr(p)>((double)radius*radius)))throw new IllegalArgumentException("trade.distance");if(ServerDatabase.settings().flag("trade.sameDimension")&&(other==null||other.level()!=p.level()))throw new IllegalArgumentException("trade.distance");}
  next.action(d.id,p.getStringUUID(),menu.revision,action,System.currentTimeMillis());commit(null,next,null,null,this::refresh);
 }catch(Exception error){error(p,error);menu.reload();}}
 void transfer(ServerPlayer p,TradeMenu menu,List<ItemStack> before,List<ItemStack> after,List<TradeBook.Item> offer,ItemStack cursor){transfer(p,menu,before,after,offer,cursor,ItemStack.EMPTY);}
 void transfer(ServerPlayer p,TradeMenu menu,List<ItemStack> before,List<ItemStack> after,List<TradeBook.Item> offer,ItemStack cursor,ItemStack dropped){try{
  if(!available())throw new IllegalArgumentException("trade.busy");var next=copy();var d=next.require(menu.dealId,p.getStringUUID());
  var original=new ArrayList<TradeBook.Item>(d.items(p.getStringUUID()));for(var stack:before)original.add(TradeItems.encode(stack,server.registryAccess()));original.add(book.cursors.get(p.getStringUUID()));
  var proposed=new ArrayList<TradeBook.Item>(offer);for(var stack:after)proposed.add(TradeItems.encode(stack,server.registryAccess()));proposed.add(TradeItems.encode(cursor,server.registryAccess()));proposed.add(TradeItems.encode(dropped,server.registryAccess()));TradeBook.conserve(original,proposed);
  if(d.frozenAt>0)throw new IllegalArgumentException("trade.frozen");
  if(d.active()&&!ServerDatabase.settings().flag("trade.enabled"))throw new IllegalArgumentException("trade.disabled");
  if(!d.items(p.getStringUUID()).equals(offer))next.edit(d.id,p.getStringUUID(),menu.revision,offer,ServerDatabase.settings().number("trade.maxPositions"));
  if(!d.active()&&(!same(before,after)||!dropped.isEmpty()))next.record(d,p.getStringUUID(),dropped.isEmpty()?"collect":"drop","",System.currentTimeMillis());
  var item=TradeItems.encode(cursor,server.registryAccess());if(item==null){next.cursors.remove(p.getStringUUID());next.cursorDeals.remove(p.getStringUUID());}else{next.cursors.put(p.getStringUUID(),item);next.cursorDeals.put(p.getStringUUID(),menu.dealId);}
  net.minecraft.world.entity.item.ItemEntity entity=null;
  if(!dropped.isEmpty()){
   var captured=p.captureDrops(new ArrayList<>());try{entity=p.drop(dropped.copy(),false,false);}finally{p.captureDrops(captured);}
   if(entity==null){menu.reload();return;}entity.setThrower(p);
   if(!ItemStack.matches(entity.getItem(),dropped))throw new IllegalArgumentException("trade.conservation");
  }
  commit(p,next,before,after,this::refresh,entity);
 }catch(Exception error){error(p,error);menu.reload();}}
 static void closed(ServerPlayer p,String id){if(active!=null)active.closedCursors.put(p.getStringUUID(),id);}
 private boolean restoreClosedCursor(){
  for(var entry:new ArrayList<>(closedCursors.entrySet())){
   String who=entry.getKey(),id=entry.getValue();var item=book.cursors.get(who);var d=book.deals.get(id);
   if(item==null||d==null){closedCursors.remove(who);continue;}
   // Active drafts keep their cursor across reconnects; closed results must stay collectible.
   if(d.active()){closedCursors.remove(who);continue;}
   var p=server.getPlayerList().getPlayer(UUID.fromString(who));if(p!=null&&p.containerMenu instanceof TradeMenu)continue;
   var next=copy();boolean online=p!=null&&p.isAlive()&&AuthServer.authenticated(p);var before=online?inventory(p):null;List<ItemStack> after=null;
   if(online){after=new ArrayList<>();for(var stack:before)after.add(stack.copy());var cursor=TradeItems.decode(item,server.registryAccess());for(int pass=0;pass<2&&!cursor.isEmpty();pass++)for(int i=0;i<after.size()&&!cursor.isEmpty();i++){var slot=after.get(i);if(pass==0&&!slot.isEmpty()&&ItemStack.isSameItemSameComponents(slot,cursor)||pass==1&&slot.isEmpty()){int n=Math.min(cursor.getCount(),cursor.getMaxStackSize()-slot.getCount());if(n>0){after.set(i,cursor.copyWithCount(slot.getCount()+n));cursor.shrink(n);}}}if(cursor.isEmpty()){next.cursors.remove(who);next.cursorDeals.remove(who);}else next.cursors.put(who,TradeItems.encode(cursor,server.registryAccess()));}
   next.restoreCursor(id,who);if(!next.require(id,who).items(who).isEmpty())next.noticeFor(next.require(id,who),who,"returned");var totalsBefore=new ArrayList<>(d.items(who));totalsBefore.add(item);if(before!=null)for(var stack:before)totalsBefore.add(TradeItems.encode(stack,server.registryAccess()));var totalsAfter=new ArrayList<>(next.require(id,who).items(who));if(after!=null)for(var stack:after)totalsAfter.add(TradeItems.encode(stack,server.registryAccess()));TradeBook.conserve(totalsBefore,totalsAfter);
   commit(online?p:null,next,before,after,()->{closedCursors.remove(who);refresh();});return true;
  }return false;
 }
 void commit(ServerPlayer actor,TradeBook next,List<ItemStack> before,List<ItemStack> after,Runnable done){commit(actor,next,before,after,done,null);}
 private void commit(ServerPlayer actor,TradeBook next,List<ItemStack> before,List<ItemStack> after,Runnable done,net.minecraft.world.entity.item.ItemEntity drop){
  if(!available()){if(actor!=null)error(actor,new IllegalArgumentException("trade.busy"));return;}busy=true;
  String receipt=UUID.randomUUID().toString();boolean inventory=actor!=null&&(!same(before,after)||drop!=null);var snapshot=Json.GSON.toJsonTree(next).getAsJsonObject();
  io.execute(()->{try{journal.prepare(snapshot,inventory?actor.getStringUUID():"",receipt);server.execute(()->{
   // No mutation can occur between validation, applying items and saving the receipt.
   if(stopping||actor!=null&&(!actor.connection.getConnection().isConnected()||server.getPlayerList().getPlayer(actor.getUUID())!=actor||!actor.isAlive()||!AuthServer.authenticated(actor)||!same(before,inventory(actor)))){io.execute(()->{try{journal.abort();server.execute(()->{busy=false;refresh();});}catch(Exception error){fail(error);}});return;}
   try{if(inventory){for(int i=0;i<36;i++)actor.getInventory().setItem(i,after.get(i).copy());
    if(drop!=null){var expected=drop.getItem().copy();boolean rejected=NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.item.ItemTossEvent(drop,actor)).isCanceled()||!ItemStack.matches(expected,drop.getItem())||!same(after,inventory(actor));
     if(rejected||!actor.serverLevel().addFreshEntity(drop)){for(int i=0;i<36;i++)actor.getInventory().setItem(i,before.get(i).copy());io.execute(()->{try{journal.abort();server.execute(()->{busy=false;refresh();});}catch(Exception error){fail(error);}});return;}
    }
    actor.getPersistentData().putString(RECEIPT,receipt);((dev.abros.rivet.mixin.TradePlayerSaveInvoker)server.getPlayerList()).rivet$saveTradePlayer(actor);
    Path path=playerFile(actor.getUUID());try(var file=FileChannel.open(path,StandardOpenOption.WRITE)){file.force(true);}if(!System.getProperty("os.name","").startsWith("Windows"))try(var directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}var saved=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());if(!saved.getCompound("NeoForgeData").getString(RECEIPT).equals(receipt))throw new IllegalStateException("Trade receipt not saved");}
    io.execute(()->{try{journal.commit();server.execute(()->{book=next;notices.offer(next.notices,next.noticeSequence);busy=false;done.run();});}catch(Exception error){fail(error);}});
   }catch(Exception error){fail(error);}
  });}catch(Exception error){fail(error);}});
 }
 private void fail(Exception error){com.mojang.logging.LogUtils.getLogger().error("Rivet trade storage failed; transfers suspended until recovery",error);server.execute(()->{failed=true;busy=false;for(var p:server.getPlayerList().getPlayers())if(p.containerMenu instanceof TradeMenu){p.closeContainer();p.connection.disconnect(Component.translatable("rivet.trade.storage"));}});}
 void refresh(){for(var p:server.getPlayerList().getPlayers())if(p.containerMenu instanceof TradeMenu menu)menu.reload();}
 static List<ItemStack> inventory(ServerPlayer p){var out=new ArrayList<ItemStack>();for(int i=0;i<36;i++)out.add(p.getInventory().getItem(i).copy());return out;}
 static boolean same(List<ItemStack>a,List<ItemStack>b){if(a==null||b==null||a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
 private Path playerFile(UUID id){return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id+".dat");}
 static void error(ServerPlayer player,Exception error){String key=error.getMessage();player.sendSystemMessage(Component.translatable("rivet."+(key!=null&&key.startsWith("trade.")?key:"trade.unavailable")));}
}
