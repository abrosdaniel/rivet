package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.network.TradeWire;
import dev.abros.rivet.server.TradeMenu;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
public final class TradeClient {
 private static JsonObject pending;
 private static java.util.List<TradeWire.Change> pendingChanges=java.util.List.of();
 public static void install(IEventBus bus){bus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e)->e.register(TradeMenu.TYPE.get(),TradeScreen::new));TradeWire.receive=TradeClient::receive;
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{if(Minecraft.getInstance().player==null){pending=null;return;}if(pending!=null&&Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.containerMenu instanceof TradeMenu m&&m.containerId==pending.get("container").getAsInt()){apply(m,pending);pending=null;}});
 }
 private static void receive(TradeWire.State state){var j=JsonParser.parseString(state.json()).getAsJsonObject();if(j.get("kind").getAsString().equals("tradeAdmin")){var mc=Minecraft.getInstance();if(mc.screen instanceof TradeAdminScreen admin&&admin.accepts(j))admin.append(j);else mc.setScreen(new TradeAdminScreen(j,state.changes()));return;}if(j.get("kind").getAsString().equals("list")){var mc=Minecraft.getInstance();if(mc.screen instanceof TradeListScreen list&&list.accepts(j))list.append(j);else if(j.get("page").getAsInt()==0)mc.setScreen(new TradeListScreen(j));return;}pendingChanges=state.changes();pending=j;}
 private static void apply(TradeMenu m,JsonObject j){m.frozen=j.has("frozen")&&j.get("frozen").getAsBoolean();m.dirty=j.get("dirty").getAsBoolean();m.otherDirty=j.get("otherDirty").getAsBoolean();m.changes.clear();for(var change:pendingChanges)m.changes.put(change.slot(),new TradeMenu.Change(change.before()==0?net.minecraft.world.item.ItemStack.EMPTY:change.item().copyWithCount(change.before()),change.after()==0?net.minecraft.world.item.ItemStack.EMPTY:change.item().copyWithCount(change.after())));m.revision=j.get("revision").getAsLong();m.status=j.get("status").getAsString();m.accepted=j.get("accepted").getAsBoolean();m.otherAccepted=j.get("otherAccepted").getAsBoolean();m.otherName=j.get("otherName").getAsString();m.otherId=j.get("otherId").getAsString();m.otherOnline=j.get("otherOnline").getAsBoolean();m.canCollect=j.get("canCollect").getAsBoolean();m.ownEmpty=j.get("ownEmpty").getAsBoolean();m.otherEmpty=j.get("otherEmpty").getAsBoolean();m.gift=j.get("gift").getAsBoolean();m.ownPage=j.get("ownPage").getAsInt();m.otherPage=j.get("otherPage").getAsInt();m.ownPages=j.get("ownPages").getAsInt();m.otherPages=j.get("otherPages").getAsInt();if(Minecraft.getInstance().screen instanceof TradeScreen s)s.update();}
 static boolean available(){var connection=Minecraft.getInstance().getConnection();return connection!=null&&connection.hasChannel(TradeWire.State.TYPE);}
 static void command(String command){var mc=Minecraft.getInstance();if(mc.getConnection()!=null)mc.getConnection().sendCommand(command);}
}
