package dev.abros.rivet.server;

import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;

/** Real LuckPerms transient grants on an explicitly enabled, isolated test server only. */
@EventBusSubscriber(modid="rivet",value=Dist.DEDICATED_SERVER)
public final class MapTeleportPermissionFixture {
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
  if(!"true".equals(System.getenv("RIVET_MAP_PERMISSION_FIXTURE")))return;
  e.getDispatcher().register(Commands.literal("mapPermissionFixture").then(Commands.argument("mode",StringArgumentType.word()).executes(c->{
   var p=c.getSource().getPlayerOrException();
   if(!p.getGameProfile().getName().equals("RivetMapGuest")||p.hasPermissions(2))throw new IllegalStateException("Fixture requires non-OP guest");
   String mode=StringArgumentType.getString(c,"mode");
   try{
    var parsed=e.getDispatcher().parse("rivet mapTeleport minecraft:overworld 400 100 400",p.createCommandSourceStack());
    var before=p.position();
    var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
    var manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
    var user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());
    var userType=Class.forName("net.luckperms.api.model.user.User");
    var nodes=userType.getMethod("transientData").invoke(user);
    var nodeMap=Class.forName("net.luckperms.api.model.data.NodeMap");nodeMap.getMethod("clear").invoke(nodes);
    if(mode.equals("grant"))add(nodes,"rivet.map.teleport",true);
    if(mode.equals("wildcard")||mode.equals("deny"))add(nodes,"*",true);
    if(mode.equals("deny"))add(nodes,"rivet.map.teleport",false);
    Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(userType.getMethod("getCachedData").invoke(user));
    boolean expected=mode.equals("grant")||mode.equals("wildcard");
    if(ServerNavigation.canTeleport(p)!=expected)throw new IllegalStateException("Wrong contextual permission: "+mode);
    if(mode.equals("revoke")){
     if(e.getDispatcher().execute(parsed)!=0||!p.position().equals(before))throw new IllegalStateException("Previously parsed command retained revoked permission");
    }
    if(expected){
     for(String target:new String[]{"rivet:missing 14 100 -35","minecraft:overworld 14 2000 -35"}){
      int result=e.getDispatcher().execute("rivet mapTeleport "+target,p.createCommandSourceStack());
      if(result!=0||!p.position().equals(before))throw new IllegalStateException("Invalid destination accepted");
     }
    }
    System.out.println("RIVET_MAP_PERMISSION_SERVER_OK "+mode);
    return 1;
   }catch(Exception error){System.out.println("RIVET_MAP_PERMISSION_SERVER_FAILED "+mode);throw new IllegalStateException(error);}
  })));
 }
 private static void add(Object nodes,String permission,boolean value)throws Exception{
  var nodeType=Class.forName("net.luckperms.api.node.Node");
  var builder=nodeType.getMethod("builder",String.class).invoke(null,permission);
  var builderType=Class.forName("net.luckperms.api.node.NodeBuilder");
  builderType.getMethod("value",boolean.class).invoke(builder,value);
  Class.forName("net.luckperms.api.model.data.NodeMap").getMethod("add",nodeType).invoke(nodes,builderType.getMethod("build").invoke(builder));
 }
}
