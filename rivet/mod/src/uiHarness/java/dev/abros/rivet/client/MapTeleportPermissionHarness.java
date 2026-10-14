package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.UUID;

/** Checks real command-tree refresh and map actions with live LuckPerms grants. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapTeleportPermissionHarness {
 private static int phase=-1,ticks;private static boolean done;private static long deadline;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  String address=System.getenv("RIVET_MAP_PERMISSION_TEST_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;var data=new ServerData("Permission regression",address,ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),data,false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Permission timeout: "+phase);
   if(mc.player==null||!WorldMapClient.ready())return;
   mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();
   if(++ticks%60!=0)return;
   switch(phase++){
    case 0->{check(mc,false);command(mc,"grant");}
    case 1->{check(mc,true);var root=mc.getConnection().getCommands().getRoot();if(root.getChild("tp")!=null||root.getChild("teleport")!=null)throw new IllegalStateException("Map grant exposed vanilla teleport");teleport(mc,"minecraft:overworld",14,100,-35);}
    case 2->{landing(mc,"minecraft:overworld",14.5,100.5,-34.5);command(mc,"revoke");}
    case 3->{check(mc,false);landing(mc,"minecraft:overworld",14.5,100.5,-34.5);command(mc,"wildcard");}
    case 4->{check(mc,true);teleport(mc,"minecraft:the_end",1024,201,1024);}
    case 5->{landing(mc,"minecraft:the_end",1024.5,201.5,1024.5);command(mc,"deny");}
    case 6->{check(mc,false);command(mc,"grant");}
    case 7->{check(mc,true);teleport(mc,"minecraft:overworld",14,100,-35);}
    case 8->{landing(mc,"minecraft:overworld",14.5,100.5,-34.5);command(mc,"revoke");}
    case 9->{check(mc,false);System.out.println("RIVET_MAP_PERMISSION_OK grant, revoke, wildcard, explicit deny, no OP, no vanilla tp, cross-dimension");done=true;mc.stop();}
   }
  }catch(Throwable error){System.out.println("RIVET_MAP_PERMISSION_FAILED phase="+phase);error.printStackTrace();done=true;mc.stop();}
 }
 private static void command(Minecraft mc,String mode){mc.player.connection.sendCommand("mapPermissionFixture "+mode);}
 private static void check(Minecraft mc,boolean expected){var map=new WorldMapScreen(null);mc.setScreen(map);if(map.canTeleport(mc.level.dimension().location().toString())!=expected)throw new IllegalStateException("Incorrect action visibility: "+expected);mc.setScreen(null);}
 private static void teleport(Minecraft mc,String dimension,int x,int y,int z){var map=new WorldMapScreen(null);mc.setScreen(map);if(!map.canTeleport(dimension))throw new IllegalStateException("Map action missing");map.teleport(new MapMarker(UUID.randomUUID(),dimension,"Permission test",x,y,z,0xffffff,"pin"));if(mc.screen!=null)throw new IllegalStateException("Map stayed open");}
 private static void landing(Minecraft mc,String dimension,double x,double y,double z){if(!mc.level.dimension().location().toString().equals(dimension)||mc.player.position().distanceToSqr(x,y,z)>.01)throw new IllegalStateException("Wrong destination: "+mc.player.position());}
}
