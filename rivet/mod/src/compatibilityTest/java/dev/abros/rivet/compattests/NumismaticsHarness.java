package dev.abros.rivet.compattests;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.auth.*;
import dev.abros.rivet.server.AuthServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Real optional mod, transformed mixins and Java API; isolated developer world only. */
@EventBusSubscriber(modid="rivet_compat_tests")
public final class NumismaticsHarness {
 private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(done||System.getenv("RIVET_NUMISMATICS_TEST")==null)return;
  done=true;
  try{if("absent".equals(System.getenv("RIVET_NUMISMATICS_TEST")))check(!dev.abros.rivet.compat.NumismaticsBindings.CAPABILITY.available(),"Absent mod enabled");else verify();System.out.println("RIVET_NUMISMATICS_TEST_OK");}
  catch(Throwable error){System.out.println("RIVET_NUMISMATICS_TEST_FAILED");error.printStackTrace();}
  finally{if("bridge".equals(System.getenv("RIVET_NUMISMATICS_TEST")))event.getServer().getProfileCache().add(new com.mojang.authlib.GameProfile(UUID.fromString("0a6e1c6b-f97c-46dd-8c83-81154e003c8d"),"OfflineBankOwner"));else event.getServer().halt(false);}
 }
 private static void verify()throws Exception{
  var field=AuthServer.class.getDeclaredField("identities");field.setAccessible(true);var previous=field.get(null);
  UUID id=UUID.randomUUID(),official=UUID.randomUUID();
  var index=new ServerIdentities(List.of(new AuthStore.Profile("RivetBankTest",id,official)));
  field.set(null,index);
  try{
   var api=RivetApi.connect("rivet_compat_tests").orElseThrow();
   var identities=api.integrations().find(IntegrationApi.IDENTITY,"rivet").orElseThrow();
   check(identities.byId(id).orElseThrow().name().equals("RivetBankTest"),"Identity provider");
   check(api.integrations().find(IntegrationApi.ACCESS,"numismatics").isPresent(),"Numismatics provider");
   check(dev.abros.rivet.compat.NumismaticsBindings.CAPABILITY.available(),"Optional API contract");
   var usernames=Class.forName("dev.ithundxr.createnumismatics.util.UsernameUtils");var instance=usernames.getField("INSTANCE").get(null);var get=usernames.getMethod("getName",UUID.class,String.class);
   check("RivetBankTest".equals(get.invoke(instance,id,"fallback")),"Transformed server name lookup");
   check("fallback".equals(get.invoke(instance,official,"fallback")),"Official alias must not redirect account");
   check(get.invoke(instance,null,null)==null,"Null fallback");
   check("fallback".equals(get.invoke(instance,UUID.randomUUID(),"fallback")),"Unknown account fallback");
   var bank=Class.forName("dev.ithundxr.createnumismatics.Numismatics").getField("BANK").get(null);
   var type=Class.forName("dev.ithundxr.createnumismatics.content.backend.BankAccount$Type");
   var account=bank.getClass().getMethod("getOrCreateAccount",UUID.class,type).invoke(bank,id,type.getField("PLAYER").get(null));
   var balance=account.getClass().getMethod("getBalance").invoke(account);
   check((boolean)account.getClass().getMethod("isAuthorized",UUID.class).invoke(account,id),"Owner access");
   check(!(boolean)account.getClass().getMethod("isAuthorized",UUID.class).invoke(account,official),"No implicit alias access");
   check(((net.minecraft.network.chat.Component)account.getClass().getMethod("getDisplayName").invoke(account)).getString().equals("RivetBankTest"),"Bank display name");
   var named=bank.getClass().getMethod("getOrCreateAccount",UUID.class,type).invoke(bank,UUID.randomUUID(),type.getField("BLAZE_BANKER").get(null));
   named.getClass().getMethod("setLabel",String.class).invoke(named,"Custom bank");
   check(((net.minecraft.network.chat.Component)named.getClass().getMethod("getDisplayName").invoke(named)).getString().equals("Custom bank"),"Custom label preserved");
   check(balance.equals(account.getClass().getMethod("getBalance").invoke(account)),"Lookup changed balance");
  }finally{field.set(null,previous);}
 }
 private static void check(boolean result,String message){if(!result)throw new AssertionError(message);}
}
