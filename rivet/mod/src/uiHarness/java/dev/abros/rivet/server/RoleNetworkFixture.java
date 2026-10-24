package dev.abros.rivet.server;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.*;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.*;

/** Isolated fixture only. All assertions under test still travel through production packets/commands. */
@EventBusSubscriber(modid="rivet")
public final class RoleNetworkFixture {
 private static ServerSettings originalSettings;private static UUID renamed;private static boolean seeded;
 private static final String PASSWORD="role network test password";
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent event){
  if(!"true".equals(System.getenv("RIVET_ROLE_NETWORK_FIXTURE")))return;
  event.getDispatcher().register(Commands.literal("roleFixture").then(Commands.argument("mode",StringArgumentType.word()).executes(c->{
   var p=c.getSource().getPlayerOrException();if(!p.getGameProfile().getName().equals("RivetMapTest")||!AuthServer.authenticated(p))throw new IllegalStateException("Isolated authenticated actor required");
   String mode=StringArgumentType.getString(c,"mode");
   CompletableFuture.runAsync(()->{try{run(p,mode);}catch(Throwable failure){System.out.println("RIVET_ROLE_NETWORK_SERVER_FAILED "+mode);failure.printStackTrace();p.server.execute(()->p.sendSystemMessage(Component.literal("ROLE_FAILED "+mode)));}});return 1;
  })));
 }
 private static CommunityStore store()throws Exception{var f=ServerFeatures.class.getDeclaredField("community");f.setAccessible(true);return (CommunityStore)f.get(null);}
 private static JsonObject input(String op,String id){var j=new JsonObject();j.addProperty("section","groups");j.addProperty("op",op);j.addProperty("id",id);return j;}
 private static void run(ServerPlayer p,String mode)throws Exception{
  var auth=new AuthStore(ServerDatabase.get());var server=p.server;
  if(!seeded){seeded=true;renamed=UUID.randomUUID();auth.register("RenameBefore",renamed.toString(),PASSWORD.toCharArray(),System.currentTimeMillis());auth.register("RenameOccupied",UUID.randomUUID().toString(),PASSWORD.toCharArray(),System.currentTimeMillis());
   var f=AuthServer.class.getDeclaredField("identities");f.setAccessible(true);((ServerIdentities)f.get(null)).remember(new AuthStore.Profile("RenameBefore",renamed));
   server.submit(()->{var profile=new GameProfile(renamed,"RenameBefore");server.getProfileCache().add(profile);server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(profile));server.getPlayerList().getOps().add(new ServerOpListEntry(profile,2,true));var board=server.getScoreboard();if(board.getObjective("rename_live")!=null)board.removeObjective(board.getObjective("rename_live"));if(board.getPlayerTeam("rename_live")!=null)board.removePlayerTeam(board.getPlayerTeam("rename_live"));var objective=board.addObjective("rename_live",ObjectiveCriteria.DUMMY,Component.literal("Rename live"),ObjectiveCriteria.RenderType.INTEGER,false,null);board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("RenameBefore"),objective).set(42);board.addPlayerToTeam("RenameBefore",board.addPlayerTeam("rename_live"));}).get(5,TimeUnit.SECONDS);
  }
  if(mode.equals("verifyDenied")||mode.equals("verifyConflict")||mode.equals("verifyOnline")){
   check(auth.account("RenameBefore")!=null&&auth.account("RenameAfter")==null,"Rejected rename changed target");check(auth.account("RivetMapTest")!=null&&auth.account("OnlineRenamed")==null,"Online player renamed");ready(p,mode,"");return;
  }
  if(mode.equals("verifyRename")){
   check(auth.account("RenameBefore")==null,"Old account remains");check(auth.login("RenameAfter",renamed.toString(),PASSWORD.toCharArray(),System.currentTimeMillis()).uuid().equals(renamed.toString()),"Password/UUID changed");check(auth.pendingRenames().isEmpty(),"Rename not finalized");
   server.submit(()->{var profile=new GameProfile(renamed,"RenameAfter");var list=server.getPlayerList();check(Arrays.asList(list.getWhiteList().getUserList()).contains("RenameAfter"),"Whitelist");check(list.getOps().get(profile).getLevel()==2,"OP level");check(server.getProfileCache().get(renamed).orElseThrow().getName().equals("RenameAfter"),"Cache");check(AuthServer.knownIdentity("RenameBefore").isEmpty(),"Old alias");var board=server.getScoreboard();check(board.getPlayerScoreInfo(ScoreHolder.forNameOnly("RenameAfter"),board.getObjective("rename_live")).value()==42,"Score");check(board.getPlayersTeam("RenameAfter").getName().equals("rename_live"),"Team");check(board.listPlayerScores(ScoreHolder.forNameOnly("RenameBefore")).isEmpty(),"Old score");}).get(5,TimeUnit.SECONDS);
   System.out.println("RIVET_AUTH_RENAME_PLAYER_OK authenticated=true luckperms=true op=false uuid=true password=true whitelist=true scoreboard=true");ready(p,mode,"");return;
  }
  if(mode.equals("permissionSetup")||mode.equals("permissionCheck")){
   Object api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);Object manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getGroupManager").invoke(api);var managerType=Class.forName("net.luckperms.api.model.group.GroupManager");Object group=((CompletableFuture<?>)managerType.getMethod("createAndLoadGroup",String.class).invoke(manager,"rivet_test")).get(5,TimeUnit.SECONDS);
   if(mode.equals("permissionSetup")){Object map=Class.forName("net.luckperms.api.model.group.Group").getMethod("data").invoke(group);Class.forName("net.luckperms.api.model.data.NodeMap").getMethod("clear").invoke(map);((CompletableFuture<?>)managerType.getMethod("saveGroup",Class.forName("net.luckperms.api.model.group.Group")).invoke(manager,group)).get(5,TimeUnit.SECONDS);server.submit(()->{try{permissions(p,"admin");grant(p,"rivet.permissions.manage");ServerFeatures.sendState(p,false);}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);ready(p,mode,"");return;}
   var provider=ServerProviders.find(dev.abros.rivet.api.IntegrationApi.PERMISSIONS,"luckperms").orElseThrow();
   for(String[] change:new String[][]{{"true","false"},{"false","inherit"},{"inherit","true"}}){var q=permissionEdit(change[0],change[1]);server.submit(()->provider.editRole(p.getUUID(),ServerProviders.data(q))).get(5,TimeUnit.SECONDS).toCompletableFuture().get(5,TimeUnit.SECONDS);}
   for(String bad:List.of("conflict","foreign")){var q=permissionEdit("inherit","false");if(bad.equals("foreign"))q.addProperty("permission","minecraft.command.op");try{server.submit(()->provider.editRole(p.getUUID(),ServerProviders.data(q))).get(5,TimeUnit.SECONDS).toCompletableFuture().get(5,TimeUnit.SECONDS);throw new AssertionError("Unsafe permission edit accepted: "+bad);}catch(ExecutionException expected){}}
   server.submit(()->{try{permissions(p,"admin");}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
   try{server.submit(()->provider.editRole(p.getUUID(),ServerProviders.data(permissionEdit("true","false")))).get(5,TimeUnit.SECONDS).toCompletableFuture().get(5,TimeUnit.SECONDS);throw new AssertionError("Admin without management permission edited role");}catch(ExecutionException expected){}
   // Independent LuckPerms writer: the UI must observe this without reconnecting.
   server.submit(()->{try{var type=Class.forName("net.luckperms.api.node.Node");var builder=type.getMethod("builder",String.class).invoke(null,"rivet.map.seeHiddenPlayers");var bt=Class.forName("net.luckperms.api.node.NodeBuilder");Object map=Class.forName("net.luckperms.api.model.group.Group").getMethod("data").invoke(group);var mt=Class.forName("net.luckperms.api.model.data.NodeMap");mt.getMethod("remove",type).invoke(map,bt.getMethod("build").invoke(builder));bt.getMethod("value",boolean.class).invoke(builder,false);mt.getMethod("add",type).invoke(map,bt.getMethod("build").invoke(builder));grant(p,"rivet.permissions.manage");}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
   ((CompletableFuture<?>)managerType.getMethod("saveGroup",Class.forName("net.luckperms.api.model.group.Group")).invoke(manager,group)).get(5,TimeUnit.SECONDS);
   System.out.println("RIVET_PERMISSION_EDIT_OK allow=true deny=true inherit=true conflict=true foreignDenied=true managerRequired=true");ready(p,mode,"");return;
  }
  if(mode.equals("prepareModules")){server.submit(()->{try{permissions(p,"admin");ServerFeatures.sendState(p,false);}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);ready(p,mode,"");return;}
  if(mode.startsWith("disable_")||mode.equals("restoreModules")){
   if(originalSettings==null)originalSettings=ServerDatabase.settings();var next=originalSettings;
   if(!mode.equals("restoreModules")){String module=mode.substring(8);check(FeatureModules.OPTIONAL.contains(module),"Unknown module");String text=java.nio.file.Files.readString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("config/rivet-server.toml"));for(String id:module.equals("tasks")?List.of("tasks","storage"):List.of(module)){int start=text.indexOf("["+id+"]"),end=text.indexOf("\n[",start+1);if(end<0)end=text.length();text=text.substring(0,start)+text.substring(start,end).replace("enabled = true","enabled = false")+text.substring(end);}next=ServerSettings.parse(text);check(originalSettings.liveFrom(next).modules().enabled(module),"Module unexpectedly reloadable");}
   var applied=next;server.submit(()->{ServerDatabase.liveSettings(applied);ServerFeatures.sendState(p,false);}).get(5,TimeUnit.SECONDS);ready(p,mode,"");return;
  }
  if(mode.equals("eventVisitor")||mode.equals("eventOwner")){
   var db=store();var owner=new CommunityStore.Actor(mode.equals("eventOwner")?p.getUUID().toString():UUID.randomUUID().toString(),"EventOwner",true,false);db.seen(owner);
   var create=input("create","");create.addProperty("section","events");create.addProperty("title","Network event");create.addProperty("description","Live event roles");create.addProperty("startsAt",java.time.Instant.now().plusSeconds(3600).toString());create.addProperty("capacity",1);String id=Json.str(db.request(owner,create).getAsJsonObject("detail"),"id");
   var join=input("join",id);join.addProperty("section","events");db.request(new CommunityStore.Actor(UUID.randomUUID().toString(),"FirstParticipant",false,false),join);ready(p,mode,id);return;
  }
  if(mode.equals("revoke")){server.submit(()->{try{permissions(p,"");ServerFeatures.sendState(p,false);ServerFeatures.invalidate(server);}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);ready(p,mode,"");return;}
  check(Set.of("visitor","member","assistant","leader","moderator","admin").contains(mode),"Unknown role");
  var db=store();var self=new CommunityStore.Actor(p.getUUID().toString(),p.getGameProfile().getName(),false,false);var owner=mode.equals("leader")?self:new CommunityStore.Actor(UUID.randomUUID().toString(),"FixtureOwner",false,false);db.seen(owner);db.seen(self);
  var create=input("create","");create.addProperty("title","Network "+mode);create.addProperty("description","Role network fixture");create.addProperty("type","Команда");String id=Json.str(db.request(owner,create).getAsJsonObject("detail"),"id");
  if(mode.equals("member")||mode.equals("assistant")){var apply=input("apply",id);apply.addProperty("text","Join fixture");db.request(self,apply);var accept=input("application",id);accept.addProperty("target",self.id());accept.addProperty("accept",true);db.request(owner,accept);if(mode.equals("assistant")){var role=input("role",id);role.addProperty("target",self.id());role.addProperty("role","assistant");db.request(owner,role);}}
  server.submit(()->{try{permissions(p,mode);ServerFeatures.sendState(p,false);ServerFeatures.invalidate(server);}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);ready(p,mode,id);
 }
 private static void ready(ServerPlayer p,String mode,String id){p.server.execute(()->p.sendSystemMessage(Component.literal("ROLE_READY "+mode+" "+id)));}
 private static void permissions(ServerPlayer p,String role)throws Exception{
  p.server.getPlayerList().deop(p.getGameProfile());
  var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);var manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);var user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());var type=Class.forName("net.luckperms.api.model.user.User");var nodes=type.getMethod("transientData").invoke(user);var nodeMap=Class.forName("net.luckperms.api.model.data.NodeMap");nodeMap.getMethod("clear").invoke(nodes);
  if(role.equals("admin")||role.equals("moderator")){var nodeType=Class.forName("net.luckperms.api.node.Node");var builder=nodeType.getMethod("builder",String.class).invoke(null,role.equals("admin")?"rivet.admin":"rivet.reports");var builderType=Class.forName("net.luckperms.api.node.NodeBuilder");nodeMap.getMethod("add",nodeType).invoke(nodes,builderType.getMethod("build").invoke(builder));}
  Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(type.getMethod("getCachedData").invoke(user));check(!p.hasPermissions(2),"Actor unexpectedly OP");
 }
 private static JsonObject permissionEdit(String expected,String value){var q=new JsonObject();q.addProperty("role","rivet_test");q.addProperty("permission","rivet.map.seeHiddenPlayers");q.addProperty("expected",expected);q.addProperty("value",value);return q;}
 private static void grant(ServerPlayer p,String right)throws Exception{var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);var manager=Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);var user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());var type=Class.forName("net.luckperms.api.node.Node");var builder=type.getMethod("builder",String.class).invoke(null,right);var data=Class.forName("net.luckperms.api.model.user.User").getMethod("transientData").invoke(user);Class.forName("net.luckperms.api.model.data.NodeMap").getMethod("add",type).invoke(data,Class.forName("net.luckperms.api.node.NodeBuilder").getMethod("build").invoke(builder));}
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
