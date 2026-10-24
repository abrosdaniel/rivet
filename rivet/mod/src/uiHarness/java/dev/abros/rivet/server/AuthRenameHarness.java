package dev.abros.rivet.server;

import com.mojang.authlib.GameProfile;
import dev.abros.rivet.core.auth.*;
import net.minecraft.server.players.*;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in isolated server scenario; never included in the release artifact. */
@EventBusSubscriber(modid="rivet")
public final class AuthRenameHarness {
 private static boolean started;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(started||System.getenv("RIVET_AUTH_RENAME_TEST")==null)return;started=true;var server=event.getServer();
  CompletableFuture.runAsync(()->{try{
   var auth=new AuthStore(ServerDatabase.get());UUID id=UUID.randomUUID();String before="RenameBefore",after="RenameAfter";
   auth.register(before,id.toString(),"test rename password".toCharArray(),System.currentTimeMillis());
   var field=AuthServer.class.getDeclaredField("identities");field.setAccessible(true);((ServerIdentities)field.get(null)).remember(new AuthStore.Profile(before,id));
   server.submit(()->{
    var profile=new GameProfile(id,before);server.getProfileCache().add(profile);
    server.getPlayerList().getWhiteList().add(new UserWhiteListEntry(profile));server.getPlayerList().getOps().add(new ServerOpListEntry(profile,2,true));
    var board=server.getScoreboard();var objective=board.addObjective("rename_test",ObjectiveCriteria.DUMMY,net.minecraft.network.chat.Component.literal("Rename test"),ObjectiveCriteria.RenderType.INTEGER,false,null);
    board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(before),objective).set(42);board.addPlayerToTeam(before,board.addPlayerTeam("rename_team"));
    check(AuthServer.mayRename(server.createCommandSourceStack()),"Console permission");
    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"rivet auth rename "+before+" "+after);
   }).get(5,TimeUnit.SECONDS);
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
   while(auth.account(after)==null||!auth.pendingRenames().isEmpty()){if(System.nanoTime()>deadline)throw new AssertionError("Rename command did not finish");Thread.sleep(50);}
   check(auth.login(after,id.toString(),"test rename password".toCharArray(),System.currentTimeMillis()).uuid().equals(id.toString()),"Password/UUID");
   server.submit(()->{
    var profile=new GameProfile(id,after);var list=server.getPlayerList();check(Arrays.asList(list.getWhiteList().getUserList()).contains(after),"Whitelist name");
    var op=list.getOps().get(profile);check(Arrays.asList(list.getOps().getUserList()).contains(after)&&op.getLevel()==2&&op.getBypassesPlayerLimit(),"OP retained");
    check(server.getProfileCache().get(id).orElseThrow().getName().equals(after),"UUID cache");check(AuthServer.knownIdentity(before).isEmpty(),"Old alias removed");
    var board=server.getScoreboard();check(board.getPlayerScoreInfo(ScoreHolder.forNameOnly(after),board.getObjective("rename_test")).value()==42,"Score retained");
    check(board.getPlayersTeam(after).getName().equals("rename_team")&&board.getPlayersTeam(before)==null,"Team renamed");
    check(board.listPlayerScores(ScoreHolder.forNameOnly(before)).isEmpty(),"Old scores removed");
   }).get(5,TimeUnit.SECONDS);
   System.out.println("RIVET_AUTH_RENAME_OK");
  }catch(Throwable failure){System.out.println("RIVET_AUTH_RENAME_FAILED");failure.printStackTrace();}finally{server.execute(()->server.halt(false));}});
 }
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
