package dev.abros.rivet.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;
/** Bounded metadata and read-only comparison icons; escrow travels through native slots. */
public final class TradeWire {
 public static Consumer<State> receive=value->{};
 public record Change(int slot,ItemStack item,int before,int after) {}
 public record State(String json,List<Change> changes) implements CustomPacketPayload {
  public State(String json){this(json,List.of());}
  public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","trades"));
  public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{
   b.writeUtf(p.json,30000);if(p.changes.size()>36)throw new IllegalArgumentException("Too many comparison slots");b.writeVarInt(p.changes.size());
   for(var change:p.changes){b.writeVarInt(change.slot);ItemStack.STREAM_CODEC.encode(b,change.item.copyWithCount(1));b.writeVarInt(change.before);b.writeVarInt(change.after);}
  },b->{String json=b.readUtf(30000);int count=b.readVarInt();if(count<0||count>36)throw new IllegalArgumentException("Too many comparison slots");var changes=new java.util.ArrayList<Change>();
   for(int i=0;i<count;i++){int slot=b.readVarInt();var item=ItemStack.STREAM_CODEC.decode(b);int before=b.readVarInt(),after=b.readVarInt();if(slot<0||slot>=48||slot%24>=18||before<0||after<0)throw new IllegalArgumentException("Invalid comparison");changes.add(new Change(slot,item,before,after));}return new State(json,List.copyOf(changes));});
  public Type<State> type(){return TYPE;}
 }
 public record Action(int container,long revision,int button) implements CustomPacketPayload {
  public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rivet","trade_action"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.container);b.writeLong(p.revision);b.writeVarInt(p.button);},b->new Action(b.readVarInt(),b.readLong(),b.readVarInt()));
  public Type<Action> type(){return TYPE;}
 }
 public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1").optional();r.playToClient(State.TYPE,State.CODEC,(p,c)->receive.accept(p));r.playToServer(Action.TYPE,Action.CODEC,(p,c)->{if(c.player() instanceof net.minecraft.server.level.ServerPlayer player&&player.containerMenu instanceof dev.abros.rivet.server.TradeMenu menu&&menu.containerId==p.container())menu.confirm(player,p.revision(),p.button());});}
}
