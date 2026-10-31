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
 public static final String NAME="_ToT_KeKs_";
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
    var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);var store=new SkinStore(ServerDatabase.get(),ServerDatabase.settings().skins());store.upload(OWNER,"Offline fixture",false,bytes.toByteArray());store.upload(player.getUUID(),"Online fixture",false,bytes.toByteArray());String hash=Json.str(store.appearance(OWNER),"hash");var own=store.appearance(player.getUUID());own.addProperty("kind","appearance");
    player.server.execute(()->{net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new dev.abros.rivet.network.SkinWire.Response(Json.GSON.toJson(own)));player.server.getProfileCache().add(new GameProfile(OWNER,NAME));
     player.getInventory().clearContent();
     var command=player.server.createCommandSourceStack().withEntity(player).withPosition(player.position());player.server.getCommands().performPrefixedCommand(command,"give "+player.getGameProfile().getName()+" minecraft:player_head[minecraft:profile={name:\""+NAME+"\"}]");
     player.server.getCommands().performPrefixedCommand(command,"give "+player.getGameProfile().getName()+" minecraft:player_head[minecraft:profile={name:\""+player.getGameProfile().getName()+"\"}]");
     var ownPos=new net.minecraft.core.BlockPos(2,100,0);player.serverLevel().setBlockAndUpdate(ownPos,net.minecraft.world.level.block.Blocks.PLAYER_HEAD.defaultBlockState());
     ((net.minecraft.world.level.block.entity.SkullBlockEntity)player.serverLevel().getBlockEntity(ownPos)).setOwner(new net.minecraft.world.item.component.ResolvableProfile(player.getGameProfile()));
     player.serverLevel().sendBlockUpdated(ownPos,player.serverLevel().getBlockState(ownPos),player.serverLevel().getBlockState(ownPos),3);
     player.teleportTo(0.5,101,0.5);
     var pos=new net.minecraft.core.BlockPos(1,100,0);
     if(player.serverLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity saved&&saved.getOwnerProfile()!=null){
      System.out.println("RIVET_HEAD_SAVED_BLOCK_REUSED");
     }else{
      player.serverLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.PLAYER_HEAD.defaultBlockState());
      ((net.minecraft.world.level.block.entity.SkullBlockEntity)player.serverLevel().getBlockEntity(pos)).setOwner(new net.minecraft.world.item.component.ResolvableProfile(new GameProfile(net.minecraft.Util.NIL_UUID,NAME.toLowerCase(java.util.Locale.ROOT))));
      player.serverLevel().sendBlockUpdated(pos,player.serverLevel().getBlockState(pos),player.serverLevel().getBlockState(pos),3);
     }
     player.sendSystemMessage(Component.literal("HEAD_READY "+hash));});
   }catch(Throwable failure){System.out.println("RIVET_OFFLINE_HEAD_SERVER_FAILED");failure.printStackTrace();}});return 1;
  }));
 }
}
