package dev.abros.rivet.network;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Optional, bounded snapshots. Only the server chooses which stones enter a snapshot. */
public final class WaystonesWire {
 public record Stone(UUID id,String name,String dimension,int x,int y,int z) {}
 public record Request(int sequence) implements CustomPacketPayload {
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","waystones_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeVarInt(p.sequence),b->new Request(b.readVarInt()));
  public Type<Request> type(){return TYPE;}
 }
 public record Snapshot(int sequence,int part,int parts,List<Stone> stones) implements CustomPacketPayload {
  public Snapshot {stones=List.copyOf(stones);if(parts<1||parts>32||part<0||part>=parts||stones.size()>128)throw new IllegalArgumentException("Invalid waystone snapshot");}
  public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","waystones_snapshot"));
  public static final StreamCodec<FriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->{
   b.writeVarInt(p.sequence);b.writeVarInt(p.part);b.writeVarInt(p.parts);b.writeVarInt(p.stones.size());
   for(var s:p.stones){b.writeUUID(s.id);b.writeUtf(s.name,80);b.writeUtf(s.dimension,160);b.writeInt(s.x);b.writeInt(s.y);b.writeInt(s.z);}
  },b->{int sequence=b.readVarInt(),part=b.readVarInt(),parts=b.readVarInt(),size=b.readVarInt();if(size<0||size>128||parts<1||parts>32||part<0||part>=parts)throw new IllegalArgumentException("Invalid waystone snapshot");var rows=new ArrayList<Stone>(size);for(int i=0;i<size;i++)rows.add(new Stone(b.readUUID(),b.readUtf(80),b.readUtf(160),b.readInt(),b.readInt(),b.readInt()));return new Snapshot(sequence,part,parts,rows);});
  public Type<Snapshot> type(){return TYPE;}
 }
 public static java.util.function.Consumer<Snapshot> client=p->{};
 public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1").optional();
  r.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof net.minecraft.server.level.ServerPlayer player)dev.abros.rivet.server.ServerWaystones.request(player,p.sequence);}));
  r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->c.enqueueWork(()->client.accept(p)));
 }
 private WaystonesWire(){}
}
