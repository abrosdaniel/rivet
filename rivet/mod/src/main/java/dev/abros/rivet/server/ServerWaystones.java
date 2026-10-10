package dev.abros.rivet.server;

import java.util.*;
import dev.abros.rivet.network.WaystonesWire;
import dev.abros.rivet.server.compat.WaystonesAdapter;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** No teleports, mutations, or implicit activation; snapshots expire on the client. */
public final class ServerWaystones {
 private static final Map<ServerPlayer,Long> next=new WeakHashMap<>();
 public static void request(ServerPlayer player,int sequence){
  if(!player.connection.hasChannel(WaystonesWire.Snapshot.TYPE))return;
  long now=System.currentTimeMillis();if(now<next.getOrDefault(player,0L))return;next.put(player,now+1000);
  boolean permitted=ServerMap.worldId()!=null&&ServerDatabase.settings().flag("map.enabled")&&(!player.server.isDedicatedServer()||AuthServer.authenticated(player));
  var points=permitted?WaystonesAdapter.points(player):List.<WaystonesWire.Stone>of();
  int parts=Math.max(1,(points.size()+127)/128);
  for(int part=0;part<parts;part++)PacketDistributor.sendToPlayer(player,new WaystonesWire.Snapshot(sequence,part,parts,points.subList(part*128,Math.min(points.size(),(part+1)*128))));
 }
 public static void reset(){next.clear();WaystonesAdapter.CAPABILITY.reset();}
 private ServerWaystones(){}
}
