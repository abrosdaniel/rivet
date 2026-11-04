package dev.abros.rivet.server;

@net.neoforged.fml.common.EventBusSubscriber(modid="rivet")
public final class AuthTransportHarness {
 @net.neoforged.bus.api.SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(System.getenv("RIVET_TLS_CLOSE_CHECK")==null)return;
  event.getDispatcher().register(net.minecraft.commands.Commands.literal("rivet_test_tls_timeout").requires(s->s.hasPermission(4)).executes(c->{
   var player=c.getSource().getPlayerOrException();var channel=player.connection.getConnection().channel();
   if(channel.pipeline().get("rivet_auth_tls")==null)throw new IllegalStateException("TLS missing in regression");
   channel.eventLoop().execute(()->{channel.pipeline().fireExceptionCaught(io.netty.handler.timeout.ReadTimeoutException.INSTANCE);System.out.println("RIVET_TLS_CLOSE_SERVER_TRIGGERED");});return 1;
  }));
 }
}
