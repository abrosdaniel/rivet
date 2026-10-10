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

/** Actual map actions and native command permission checks; excluded from release. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapTeleportHarness {
 private static int phase=-1,ticks;private static boolean done;private static long deadline;private static double guestX,guestZ;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  String address=System.getenv("RIVET_MAP_TELEPORT_TEST_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;var data=new ServerData("Teleport regression",address,ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),data,false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Teleport timeout: "+phase);
   if(mc.player==null||!WorldMapClient.ready())return;if(++ticks%60!=0)return;
   if("true".equals(System.getenv("RIVET_MAP_TEST_GUEST"))){
    if(phase==0){var map=new WorldMapScreen(null);mc.setScreen(map);if(map.canTeleport(mc.level.dimension().location().toString()))throw new IllegalStateException("Guest received teleport");mc.setScreen(null);guestX=mc.player.getX();guestZ=mc.player.getZ();mc.player.connection.sendCommand("tp @s 400.5 81.5 400.5");phase=1;return;}
    if(Math.abs(mc.player.getX()-guestX)>1||Math.abs(mc.player.getZ()-guestZ)>1)throw new IllegalStateException("Guest teleported without permission");finish(mc,"native guest denial");return;
   }
   switch(phase++){
    case 0->{mc.player.connection.sendCommand("gamemode creative");mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s 14 90 -35");}
    case 1->{mc.player.getAbilities().flying=true;mc.player.onUpdateAbilities();mc.player.connection.sendCommand("fill 14 80 -35 14 82 -35 minecraft:stone");mc.player.connection.sendCommand("fill 14 83 -35 14 87 -35 minecraft:air");}
    case 2->teleport(mc,"minecraft:overworld",14,83,-35);
    case 3->{landing(mc,14.5,83,-34.5);mc.player.connection.sendCommand("fill 14 81 -35 14 87 -35 minecraft:air");mc.player.connection.sendCommand("setblock 14 80 -35 minecraft:stone_slab[type=bottom]");}
    case 4->teleport(mc,"minecraft:overworld",14,81,-35);
    case 5->{landing(mc,14.5,80.5,-34.5);mc.player.connection.sendCommand("fill 48 76 -37 52 76 -33 minecraft:stone");mc.player.connection.sendCommand("fill 48 77 -37 52 80 -33 minecraft:water");mc.player.connection.sendCommand("fill 48 81 -37 52 85 -33 minecraft:air");}
    case 6->teleport(mc,"minecraft:overworld",50,81,-35);
    case 7->{waterLanding(mc);mc.player.connection.sendCommand("rivet map teleport minecraft:overworld 400 81 400");}
    case 8->{waterLanding(mc);mc.player.connection.sendCommand("execute in minecraft:the_end run forceload add 0 0");}
    case 9->{mc.player.connection.sendCommand("execute in minecraft:the_end run setblock 0 80 0 minecraft:stone");mc.player.connection.sendCommand("execute in minecraft:the_end run fill 0 81 0 0 85 0 minecraft:air");}
    case 10->teleport(mc,"minecraft:the_end",0,81,0);
    case 11->{if(!mc.level.dimension().location().toString().equals("minecraft:the_end"))throw new IllegalStateException("Dimension not changed");landing(mc,.5,81,.5);teleport(mc,"minecraft:overworld",14,81,-35);mc.player.connection.sendCommand("execute in minecraft:the_end run forceload remove 0 0");}
    case 12->finish(mc,"surface + half block, slab, deep water, dimensions, obsolete command absent");
   }
  }catch(Throwable error){System.out.println("RIVET_MAP_TELEPORT_FAILED phase="+phase);error.printStackTrace();done=true;mc.stop();}
 }
 private static void teleport(Minecraft mc,String dimension,int x,int y,int z){var map=new WorldMapScreen(null);mc.setScreen(map);if(!map.canTeleport(dimension))throw new IllegalStateException("Operator lacks native teleport");map.teleport(new MapMarker(UUID.randomUUID(),dimension,"Teleport test",x,y,z,0xffffff,"pin"));if(mc.screen!=null)throw new IllegalStateException("Map did not close");}
 private static void waterLanding(Minecraft mc){if(Math.abs(mc.player.getX()-50.5)>.01||Math.abs(mc.player.getZ()+34.5)>.01||mc.player.getY()<77||mc.player.getY()>81.5||!mc.level.noCollision(mc.player,mc.player.getBoundingBox()))throw new IllegalStateException("Water teleport failed: "+mc.player.position());}
 private static void landing(Minecraft mc,double x,double y,double z){if(Math.abs(mc.player.getX()-x)>.01||Math.abs(mc.player.getY()-y)>.01||Math.abs(mc.player.getZ()-z)>.01||!mc.level.noCollision(mc.player,mc.player.getBoundingBox()))throw new IllegalStateException("Unexpected teleport: "+mc.player.position());}
 private static void finish(Minecraft mc,String checked){System.out.println("RIVET_MAP_TELEPORT_OK "+checked);done=true;mc.stop();}
}
