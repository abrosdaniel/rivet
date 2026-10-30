package dev.abros.rivet.server;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Real committed notifications; only present in the isolated harness source set. */
@EventBusSubscriber(modid="rivet")
public final class RealtimeFixture {
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(!"true".equals(System.getenv("RIVET_REALTIME_FIXTURE")))return;
  event.getDispatcher().register(Commands.literal("realtimeFixture").requires(s->s.hasPermission(4))
   .then(Commands.literal("seed").executes(c->write(c.getSource().getPlayerOrException(),20)))
   .then(Commands.literal("change").executes(c->write(c.getSource().getPlayerOrException(),1))));
 }
 private static int write(net.minecraft.server.level.ServerPlayer player,int count){
  var store=ServerFeatures.communityStore();ServerFeatures.storage(()->{try{
   for(int i=0;i<count;i++)store.externalNotice(player.getUUID().toString(),"Realtime "+System.nanoTime()+" "+"fixture text ".repeat(35));
   store.externalNotice(java.util.UUID.randomUUID().toString(),"OTHER_RECIPIENT_SECRET");
   player.server.execute(()->player.sendSystemMessage(Component.literal("REALTIME_READY "+count)));
  }catch(Exception failure){System.out.println("RIVET_REALTIME_SERVER_FAILED");failure.printStackTrace();}});return 1;
 }
}
