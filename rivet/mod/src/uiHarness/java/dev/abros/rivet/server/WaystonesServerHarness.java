package dev.abros.rivet.server;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import dev.abros.rivet.server.compat.WaystonesAdapter;
/** Runs against an installed Waystones mod in an isolated development world. */
@EventBusSubscriber(modid="rivet",value=Dist.DEDICATED_SERVER)
public final class WaystonesServerHarness {
 private static int tick,stage;private static Object known,global,hidden,remote,manager;private static Class<?> stone,mutable,visibility;private static final List<Object> created=new ArrayList<>();
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 @SuppressWarnings({"unchecked","rawtypes"})
 private static Object value(Class<?> type,String key){return Enum.valueOf((Class)type,key);}
 private static void visibility(ServerPlayer player,Object waystone,String from,String to)throws Exception{
  mutable.getMethod("setVisibility",visibility).invoke(waystone,value(visibility,to));
  Class.forName("net.blay09.mods.waystones.core.WaystoneIndexManager").getMethod("visibilityChanged",net.minecraft.server.MinecraftServer.class,stone,visibility).invoke(null,player.server,waystone,value(visibility,from));
 }
 private static Object create(ServerPlayer p,String name,ResourceKey<Level> dimension,int x,String access,boolean activate)throws Exception{
  var impl=Class.forName("net.blay09.mods.waystones.core.WaystoneImpl");var origin=Class.forName("net.blay09.mods.waystones.api.WaystoneOrigin");
  var result=impl.getConstructor(ResourceLocation.class,UUID.class,ResourceKey.class,BlockPos.class,origin,UUID.class).newInstance(ResourceLocation.parse("waystones:waystone"),UUID.randomUUID(),dimension,new BlockPos(x,70,0),value(origin,"PLAYER"),UUID.randomUUID());
  mutable.getMethod("setName",Component.class).invoke(result,Component.literal(name));mutable.getMethod("setVisibility",visibility).invoke(result,value(visibility,access));
  manager.getClass().getMethod("addWaystone",stone).invoke(manager,result);created.add(result);
  Class.forName("net.blay09.mods.waystones.core.WaystoneIndexManager").getMethod("visibilityChanged",net.minecraft.server.MinecraftServer.class,stone,visibility).invoke(null,p.server,result,value(visibility,"ACTIVATION"));
  if(activate)Class.forName("net.blay09.mods.waystones.api.WaystonesAPI").getMethod("activateWaystone",ServerPlayer.class,stone).invoke(null,p,result);
  return result;
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(System.getenv("RIVET_WAYSTONES_TEST")==null||stage==5||event.getServer().getPlayerList().getPlayers().isEmpty())return;
  var p=event.getServer().getPlayerList().getPlayers().getFirst();
  try{
   if(stage==0){p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);if(++tick<60)return;stone=Class.forName("net.blay09.mods.waystones.api.Waystone");mutable=Class.forName("net.blay09.mods.waystones.api.MutableWaystone");visibility=Class.forName("net.blay09.mods.waystones.api.WaystoneVisibility");manager=Class.forName("net.blay09.mods.waystones.core.WaystoneManagerImpl").getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,p.server);
    known=create(p,"Rivet Known",Level.OVERWORLD,8,"ACTIVATION",true);global=create(p,"Rivet Global",Level.OVERWORLD,24,"GLOBAL",false);hidden=create(p,"Rivet Hidden",Level.OVERWORLD,40,"ACTIVATION",false);remote=create(p,"Rivet Nether",Level.NETHER,8,"GLOBAL",false);
    visibility(p,known,"ACTIVATION","GLOBAL");visibility(p,known,"GLOBAL","ACTIVATION");var points=WaystonesAdapter.points(p);check(points.size()==3,"Expected known/global/cross-dimension only: "+points);check(points.stream().noneMatch(s->s.name().contains("Hidden")),"Hidden stone disclosed");
    check(!(boolean)Class.forName("net.blay09.mods.waystones.api.WaystonesAPI").getMethod("isWaystoneActivated",net.minecraft.world.entity.player.Player.class,stone).invoke(null,p,hidden),"Adapter activated hidden stone");
    System.out.println("RIVET_WAYSTONES_SERVER_PHASE1_OK");stage=1;tick=0;
   }else if(stage==1&&++tick>=300){
    mutable.getMethod("setName",Component.class).invoke(global,Component.literal("Rivet Renamed"));manager.getClass().getMethod("removeWaystone",stone).invoke(manager,known);
    var points=WaystonesAdapter.points(p);check(points.size()==2&&points.stream().anyMatch(s->s.name().equals("Rivet Renamed")),"Rename/removal not reflected");
    System.out.println("RIVET_WAYSTONES_SERVER_PHASE2_OK");stage=2;tick=0;
   }else if(stage==2&&++tick>=160){
    visibility(p,global,"GLOBAL","ACTIVATION");check(WaystonesAdapter.points(p).size()==1,"Revoked public access retained");stage=3;tick=0;System.out.println("RIVET_WAYSTONES_SERVER_PRIVATE_OK");
   }else if(stage==3&&++tick>=160){
    visibility(p,global,"ACTIVATION","GLOBAL");check(WaystonesAdapter.points(p).size()==2,"Public access not restored");stage=4;tick=0;System.out.println("RIVET_WAYSTONES_SERVER_PUBLIC_OK");
   }else if(stage==4&&++tick>=160){
    for(Object item:created)manager.getClass().getMethod("removeWaystone",stone).invoke(manager,item);
    check(WaystonesAdapter.points(p).isEmpty(),"Removed stones retained");
    System.out.println("RIVET_WAYSTONES_SERVER_OK hidden/global/activated/rename/removal/dimensions/visibility");stage=5;
   }
  }catch(Throwable failure){stage=5;System.out.println("RIVET_WAYSTONES_SERVER_FAILED");failure.printStackTrace();}
 }
}
