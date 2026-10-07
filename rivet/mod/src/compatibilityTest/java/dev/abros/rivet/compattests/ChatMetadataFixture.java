package dev.abros.rivet.compattests;
import java.util.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
/** Temporary test metadata only; never saved to LuckPerms or included in Rivet. */
@EventBusSubscriber(modid="rivet_compat_tests",value=Dist.DEDICATED_SERVER)
public final class ChatMetadataFixture {
 private static final Set<UUID> applied=new HashSet<>();
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){applied.remove(event.getEntity().getUUID());}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(System.getenv("RIVET_CHAT_IDENTITY_REVIEW")==null)return;for(var player:e.getServer().getPlayerList().getPlayers()){if(applied.contains(player.getUUID()))continue;try{var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);var lp=Class.forName("net.luckperms.api.LuckPerms");var manager=lp.getMethod("getUserManager").invoke(api);var user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,player.getUUID());if(user==null)continue;var userType=Class.forName("net.luckperms.api.model.user.User");var nodes=userType.getMethod("transientData").invoke(user);var map=Class.forName("net.luckperms.api.model.data.NodeMap");for(String type:List.of("Prefix","Suffix")){var nodeType=Class.forName("net.luckperms.api.node.types."+type+"Node");var builder=nodeType.getMethod("builder",String.class,int.class).invoke(null,type.equals("Prefix")?"&a[ENGINEER]":"&b[TEST]",900);var node=Class.forName("net.luckperms.api.node.NodeBuilder").getMethod("build").invoke(builder);map.getMethod("add",Class.forName("net.luckperms.api.node.Node")).invoke(nodes,node);}Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(userType.getMethod("getCachedData").invoke(user));applied.add(player.getUUID());System.out.println("RIVET_CHAT_METADATA_FIXTURE_OK");}catch(Exception error){throw new IllegalStateException(error);}}}
}
