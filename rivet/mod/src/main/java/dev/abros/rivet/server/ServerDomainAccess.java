package dev.abros.rivet.server;
import dev.abros.rivet.api.ApiException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
/** Offline staff checks use static LuckPerms context; live players use their current context. */
final class ServerDomainAccess {
 private static volatile MinecraftServer server;
 static void start(MinecraftServer value){server=value;}
 static void stop(){server=null;}
 static java.util.List<String> local(UUID id)throws Exception{var current=server;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");return await(current.submit(()->{var player=current.getPlayerList().getPlayer(id);if(current!=server||player==null||!AuthServer.authenticated(player))throw new ApiException(ApiException.Code.FORBIDDEN,"Online authenticated player required");int radius=ServerDatabase.settings().number("chat.localRadius");return current.getPlayerList().getPlayers().stream().filter(p->AuthServer.authenticated(p)&&p.level()==player.level()&&p.distanceToSqr(player)<=radius*(double)radius).map(p->p.getUUID().toString()).toList();}));}
 static MinecraftServer current(){var value=server;if(value==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");return value;}
 static <T> T await(java.util.concurrent.Future<T> future)throws Exception{try{return future.get(3,TimeUnit.SECONDS);}catch(java.util.concurrent.ExecutionException failure){if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}catch(java.util.concurrent.TimeoutException|InterruptedException failure){future.cancel(false);if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw failure;}}
 static boolean allowed(UUID id,String permission)throws Exception{
  var current=server;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");
  var probe=current.submit(()->{if(current!=server)return -1;var player=current.getPlayerList().getPlayer(id);if(player!=null)return ServerCommands.allowed(player,permission)?1:0;var entry=current.getPlayerList().getOps().get(new com.mojang.authlib.GameProfile(id,""));return entry!=null&&entry.getLevel()>=2?1:2;});
  int status=await(probe);if(status<0)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");if(status!=2)return status==1;
  boolean result=ServerIntegration.luckPermsEnabled()&&ServerPermissions.offlineAllowed(id,permission);
  if(current!=server)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");
  return await(current.submit(()->{if(current!=server)throw new ApiException(ApiException.Code.UNAVAILABLE,"Server stopped");var player=current.getPlayerList().getPlayer(id);return player==null?result:ServerCommands.allowed(player,permission);}));
 }
}
