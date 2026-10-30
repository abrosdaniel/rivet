package dev.abros.rivet.server;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.compat.*;
import dev.abros.rivet.core.auth.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Real Envelope types, codecs and transformed routing in an isolated test server. */
@EventBusSubscriber(modid="rivet")
public final class EnvelopeFixture {
 private static int phase,checks;private static long deadline;private static Object known,firstBox,secondBox,address;private static Map<String,Object> entries;
 private static java.lang.reflect.Constructor<?> dataConstructor,addressConstructor;private static java.lang.reflect.Method destination;
 private static net.minecraft.server.MinecraftServer fixtureServer;private static ServerIdentities index;private static final UUID FIRST=UUID.fromString("eeb0e162-ac57-471a-a83a-e27447412022"),SECOND=UUID.fromString("7b57f4c4-3880-498c-b398-d037e0f45df6");
 private static Codec<Object> codec;private static com.google.gson.JsonElement saved;
 @SuppressWarnings("unchecked") @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(System.getenv("RIVET_ENVELOPE_TEST")==null||phase==99)return;var server=event.getServer();fixtureServer=server;try{
   if(deadline==0)deadline=System.currentTimeMillis()+45000;if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Envelope fixture timeout phase="+phase);
   if(phase==0){
    check(EnvelopeBindings.CAPABILITY.available(),"Envelope API unsupported: "+EnvelopeBindings.CAPABILITY.diagnostics());
    check(ServerProviders.find(IntegrationApi.ACCESS,"envelope").isPresent(),"Java API adapter missing");
    boolean resume="resume".equals(System.getenv("RIVET_ENVELOPE_TEST"));
    index=new ServerIdentities(List.of());if(resume){remember("MailAfter",FIRST);remember("MailBefore",SECOND);}else remember("MailBefore",FIRST);var identities=AuthServer.class.getDeclaredField("identities");identities.setAccessible(true);identities.set(null,index);
    String base="io.github.mortuusars.envelope.world.";var service=Class.forName(base+"mail.MailService");var serviceObject=service.getMethod("of",net.minecraft.server.level.ServerLevel.class).invoke(null,server.overworld());known=service.getMethod("getKnownPlayers").invoke(serviceObject);
    var values=known.getClass().getDeclaredField("data");values.setAccessible(true);entries=(Map<String,Object>)values.get(known);if(!resume)entries.clear();
    var block=Class.forName(base+"mail.address.type.BlockAddress").getConstructor(String.class);firstBox=block.newInstance("FirstMailbox");secondBox=block.newInstance("SecondMailbox");
    dataConstructor=Class.forName(base+"KnownPlayers$PlayerData").getConstructor(GameProfile.class,Optional.class);if(!resume)entries.put("MailBefore",dataConstructor.newInstance(new GameProfile(FIRST,"MailBefore"),Optional.of(firstBox)));((SavedData)known).setDirty();
    var playerAddress=Class.forName(base+"mail.address.type.PlayerAddress");addressConstructor=playerAddress.getConstructor(String.class);destination=known.getClass().getMethod("getDefaultAddressOf",playerAddress);codec=((MapCodec<Object>)playerAddress.getField("CODEC").get(null)).codec();if(resume){
     var path=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("rivet/envelope-fixture-address.json");var persisted=dev.abros.rivet.core.Json.parse(java.nio.file.Files.readString(path));
     check(destination.invoke(known,codec.parse(JsonOps.INSTANCE,persisted).getOrThrow()).equals(Optional.of(firstBox)),"Restart lost saved UUID");persisted.remove("rivet_owner");check(destination.invoke(known,codec.parse(JsonOps.INSTANCE,persisted).getOrThrow()).equals(Optional.of(firstBox)),"Restart lost legacy owner");
     System.out.println("RIVET_ENVELOPE_OK restart=true checks="+checks);phase=99;server.halt(false);return;
    }phase=1;return;
   }
   if(phase==1){var ready=EnvelopeBindings.class.getDeclaredField("ready");ready.setAccessible(true);if(!ready.getBoolean(null))return;
    address=addressConstructor.newInstance("MailBefore");check(((EnvelopeAddressOwner)address).rivet$owner().equals(FIRST),"New address not bound at creation");check(destination.invoke(known,address).equals(Optional.of(firstBox)),"Initial default mailbox");
    saved=codec.encodeStart(JsonOps.INSTANCE,address).getOrThrow();check(saved.getAsJsonObject().get("rivet_owner").getAsString().equals(FIRST.toString()),"UUID not saved in address");
    remember("MailAfter",FIRST);check(destination.invoke(known,address).equals(Optional.of(firstBox)),"Rename lost pending mail");phase=2;return;
   }
   if(phase==2){if(!entries.containsKey("MailAfter")||entries.containsKey("MailBefore"))return;
    remember("MailBefore",SECOND);entries.put("MailBefore",dataConstructor.newInstance(new GameProfile(SECOND,"MailBefore"),Optional.of(secondBox)));((SavedData)known).setDirty();
    check(destination.invoke(known,address).equals(Optional.of(firstBox)),"Old mail transferred to reused name");
    check(destination.invoke(known,addressConstructor.newInstance("MailBefore")).equals(Optional.of(secondBox)),"New mail did not use current owner");
    var restored=codec.parse(JsonOps.INSTANCE,saved).getOrThrow();check(destination.invoke(known,restored).equals(Optional.of(firstBox)),"Saved address lost UUID");
    var legacy=saved.deepCopy().getAsJsonObject();legacy.remove("rivet_owner");var restoredLegacy=codec.parse(JsonOps.INSTANCE,legacy).getOrThrow();check(destination.invoke(known,restoredLegacy).equals(Optional.of(firstBox)),"Legacy mail transferred to reused name");
    var invalid=saved.deepCopy().getAsJsonObject();invalid.addProperty("rivet_owner","invalid");check(codec.parse(JsonOps.INSTANCE,invalid).error().isPresent(),"Malformed owner accepted");
    check(destination.invoke(known,addressConstructor.newInstance("UnknownMailUser")).equals(Optional.empty()),"Unknown name routed");
    entries.put("CollisionMail",dataConstructor.newInstance(new GameProfile(FIRST,"CollisionMail"),Optional.of(firstBox)));
    entries.put("collisionmail",dataConstructor.newInstance(new GameProfile(SECOND,"collisionmail"),Optional.of(secondBox)));
    var ambiguous=new com.google.gson.JsonObject();ambiguous.addProperty("name","CollisionMail");
    check(destination.invoke(known,codec.parse(JsonOps.INSTANCE,ambiguous).getOrThrow()).equals(Optional.empty()),"Ambiguous legacy name routed");
    entries.remove("CollisionMail");entries.remove("collisionmail");
    dev.abros.rivet.core.Json.write(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("rivet/envelope-fixture-address.json"),saved);
    System.out.println("RIVET_ENVELOPE_OK checks="+checks+" uuid=true rename=true reusedName=true persistence=true legacy=true");phase=99;server.halt(false);
   }
  }catch(Throwable failure){System.out.println("RIVET_ENVELOPE_FAILED phase="+phase);phase=99;failure.printStackTrace();server.halt(false);}
 }
 private static void remember(String name,UUID id){if(AuthServer.enabled())index.remember(new AuthStore.Profile(name,id));fixtureServer.getProfileCache().add(new GameProfile(id,name));}
 private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
}
