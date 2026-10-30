package dev.abros.rivet.server;

import dev.abros.rivet.core.CommunityStore;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Isolated fixture: exercise post-commit delivery without forcing a state push. */
@EventBusSubscriber(modid="rivet")
public final class NotificationFixture {
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(!"true".equals(System.getenv("RIVET_NOTIFICATION_FIXTURE")))return;
  event.getDispatcher().register(Commands.literal("notificationFixture").requires(source->source.hasPermission(4)).executes(context->{
   var player=context.getSource().getPlayerOrException();var id=player.getUUID();CommunityStore store=ServerFeatures.communityStore();
   CompletableFuture.runAsync(()->{try{
    store.externalNotice(UUID.randomUUID().toString(),"Private notice for another player");
    store.externalNotice(id.toString(),"Realtime fixture notice");
   }catch(Exception failure){System.out.println("RIVET_NOTIFICATIONS_SERVER_FAILED");failure.printStackTrace();}});
   return 1;
  }));
 }
}
