package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.concurrent.*;

/** Owned by the chat module; one bounded lane orders persistence and login reads. */
final class ServerChatHistory {
 private static ExecutorService worker;private static ChatHistory store;
 private static final class Replay {final long before=System.currentTimeMillis();long cursor=Long.MAX_VALUE,next;int left;boolean pending;Replay(int count){left=count;}}
 private static final Map<ServerPlayer,Replay> joined=new IdentityHashMap<>();
 static void start(){var s=ServerDatabase.settings();store=new ChatHistory(ServerDatabase.get(),s.number("chat.history"),s.number("chat.historyDays"));worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"Rivet chat history");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());joined.clear();nextReplayPlayer=0;}
 static void stop(){var pool=worker;worker=null;store=null;joined.clear();ModuleWorkers.stop(pool);}
 static void joined(ServerPlayer player){if(store==null)return;joined.put(player,new Replay(ServerDatabase.settings().number("chat.history")));}
 static void left(ServerPlayer player){joined.remove(player);}
 static void remember(ServerPlayer sender,String channel,Component message,Collection<ServerPlayer> audience){
  var current=store;var pool=worker;if(current==null||pool==null||ServerDatabase.settings().number("chat.history")==0)return;
  String json=Component.Serializer.toJson(message,sender.registryAccess());long at=System.currentTimeMillis();var ids=audience.stream().map(p->p.getUUID().toString()).toList();
  try{pool.execute(()->{try{current.append(channel,at,json,ids);}catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot persist chat history ({})",ex.getClass().getSimpleName());}});}catch(RejectedExecutionException ex){com.mojang.logging.LogUtils.getLogger().warn("Chat history queue is full");}
 }
 private static int nextReplayPlayer;
 private record ReplayTarget(ServerPlayer player,Replay replay,ChatHistory.ReplayRequest request){}
 static void tick(MinecraftServer server){
  var current=store;var pool=worker;if(current==null||pool==null)return;
  var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;
  var settings=ServerDatabase.settings();boolean local=settings.flag("chat.local"),groups=settings.flag("chat.group")&&settings.modules().enabled("groups");
  long now=System.currentTimeMillis();int start=Math.floorMod(nextReplayPlayer,players.size());var targets=new ArrayList<ReplayTarget>();
  for(int i=0;i<players.size();i++){
   int index=(start+i)%players.size();var player=players.get(index);nextReplayPlayer=(index+1)%players.size();
   if(!AuthServer.authenticated(player)||!ServerIntegration.supports(player,"chat-history"))continue;
   var replay=joined.computeIfAbsent(player,p->new Replay(settings.number("chat.history")));
   if(replay.left<=0||replay.pending||now<replay.next)continue;
   replay.pending=true;targets.add(new ReplayTarget(player,replay,new ChatHistory.ReplayRequest(player.getUUID().toString(),replay.before,local,groups,replay.cursor,Math.min(4,replay.left))));
   if(targets.size()==16)break;
  }
  if(targets.isEmpty())return;
  try{pool.execute(()->{try{
   current.replayBatch(targets.stream().map(ReplayTarget::request).toList(),pages->{
    var packets=pages.stream().map(page->ChatHistoryReplay.packets(page.messages(),id->com.mojang.logging.LogUtils.getLogger().warn("Skipping invalid chat history entry {}",id)).stream().map(packet->new Protocol.FeatureState(packet.toString())).toList()).toList();
    BoundedDelivery.run(server,()->{
     for(int i=0;i<targets.size();i++){
      var target=targets.get(i);var player=target.player;var replay=target.replay;
      if(store!=current||joined.get(player)!=replay)continue;
      replay.pending=false;replay.next=System.currentTimeMillis()+200;
      if(!ServerFeatures.currentPlayer(player)||!AuthServer.authenticated(player))continue;
      for(var packet:packets.get(i))PacketDistributor.sendToPlayer(player,packet);
      var messages=pages.get(i).messages();replay.left=messages.size()<target.request.count()?0:replay.left-messages.size();if(!messages.isEmpty())replay.cursor=messages.getLast().id();
     }
    },1000);
   });
  }catch(Exception ex){server.execute(()->retry(current,targets));com.mojang.logging.LogUtils.getLogger().warn("Cannot read chat history ({})",ex.getClass().getSimpleName());}});}
  catch(RejectedExecutionException ex){retry(current,targets);}
 }
 private static void retry(ChatHistory current,List<ReplayTarget> targets){if(store!=current)return;for(var target:targets)if(joined.get(target.player)==target.replay){target.replay.pending=false;target.replay.next=System.currentTimeMillis()+5000;}}
}
