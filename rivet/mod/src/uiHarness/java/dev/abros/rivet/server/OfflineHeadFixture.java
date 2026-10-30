package dev.abros.rivet.server;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.*;
import dev.abros.rivet.core.skins.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
@EventBusSubscriber(modid="rivet")
public final class OfflineHeadFixture {
 public static final UUID OWNER=UUID.fromString("958122a2-30e8-4e55-8990-f9ab38a03413");
 public static final String NAME="OfflineHeadTest";
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(!"true".equals(System.getenv("RIVET_OFFLINE_HEAD_FIXTURE")))return;
  event.getDispatcher().register(Commands.literal("offlineHeadFixture").executes(context->{
   var player=context.getSource().getPlayerOrException();if(!player.getGameProfile().getName().equals("RivetMapTest")||!AuthServer.authenticated(player))return 0;
   java.util.concurrent.CompletableFuture.runAsync(()->{try{
    if(AuthServer.enabled()){
     var auth=new AuthStore(ServerDatabase.get());if(auth.account(NAME)==null)auth.register(NAME,OWNER.toString(),"isolated head fixture password".toCharArray(),System.currentTimeMillis());
     var field=AuthServer.class.getDeclaredField("identities");field.setAccessible(true);((ServerIdentities)field.get(null)).remember(new AuthStore.Profile(NAME,OWNER));
    }
    var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff42af71);
    var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);var store=new SkinStore(ServerDatabase.get(),ServerDatabase.settings().skins());store.upload(OWNER,"Offline fixture",false,bytes.toByteArray());String hash=Json.str(store.appearance(OWNER),"hash");
    player.server.execute(()->{player.server.getProfileCache().add(new GameProfile(OWNER,NAME));
     var command=player.server.createCommandSourceStack().withEntity(player).withPosition(player.position());player.server.getCommands().performPrefixedCommand(command,"give "+player.getGameProfile().getName()+" minecraft:player_head[minecraft:profile={name:\""+NAME+"\"}]");
     player.sendSystemMessage(Component.literal("HEAD_READY "+hash));});
   }catch(Throwable failure){System.out.println("RIVET_OFFLINE_HEAD_SERVER_FAILED");failure.printStackTrace();}});return 1;
  }));
 }
}
