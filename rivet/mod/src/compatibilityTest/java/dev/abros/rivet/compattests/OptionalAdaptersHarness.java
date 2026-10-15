package dev.abros.rivet.compattests;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
/** Installed-mod contract smoke test; compiled only into the separate fixture JAR. */
@EventBusSubscriber(modid="rivet_compat_tests",value=Dist.DEDICATED_SERVER)
public final class OptionalAdaptersHarness {
 private static boolean done;private static int warmup;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
  if(System.getenv("RIVET_OPTIONAL_ADAPTER_TEST")==null||done||e.getServer().getPlayerList().getPlayers().isEmpty())return;
  if(++warmup<120)return;
  var player=e.getServer().getPlayerList().getPlayers().getFirst();
  try {var profile=dev.abros.rivet.server.ServerPermissions.profile(player);if(!profile.has("available"))return;done=true;verify(player);}catch(Throwable failure){done=true;System.out.println("RIVET_OPTIONAL_ADAPTER_TEST_FAILED");failure.printStackTrace();}
 }
 private static void verify(ServerPlayer p)throws Exception{
  var lp=Class.forName("net.luckperms.api.LuckPerms");var api=Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null);
  Object manager=lp.getMethod("getUserManager").invoke(api);
  var userType=Class.forName("net.luckperms.api.model.user.User");Object user=Class.forName("net.luckperms.api.model.user.UserManager").getMethod("getUser",UUID.class).invoke(manager,p.getUUID());
  Object context=lp.getMethod("getContextManager").invoke(api);Object query=Class.forName("net.luckperms.api.context.ContextManager").getMethod("getQueryOptions",Object.class).invoke(context,p);
  Object contexts=Class.forName("net.luckperms.api.query.QueryOptions").getMethod("context").invoke(query);
  var world=(Optional<?>)Class.forName("net.luckperms.api.context.ContextSet").getMethod("getAnyValue",String.class).invoke(contexts,"world");
  check(world.isPresent(),"Player world context missing");String current=world.get().toString();
  var nodeType=Class.forName("net.luckperms.api.node.Node");var builderType=Class.forName("net.luckperms.api.node.NodeBuilder");var mapType=Class.forName("net.luckperms.api.model.data.NodeMap");
  Object nodes=userType.getMethod("transientData").invoke(user);List<Object> added=new ArrayList<>();
  try {
   Object prefix=node("PrefixNode","&a[ENGINEER]",100,current);Object suffix=node("SuffixNode","&b[TEST]",100,current);Object wrong=node("PrefixNode","WRONG_WORLD",200,"rivet_unrelated_world");
   Object grant=node("PermissionNode","rivet.diagnostics",0,current);Object forbidden=node("PermissionNode","rivet.stats.edit",0,"rivet_unrelated_world");
   for(Object n:List.of(prefix,suffix,wrong,grant,forbidden)){mapType.getMethod("add",nodeType).invoke(nodes,n);added.add(n);}
   Object cached=userType.getMethod("getCachedData").invoke(user);Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(cached);
   var profile=dev.abros.rivet.server.ServerPermissions.profile(p);check(profile.get("prefix").getAsString().equals("&a[ENGINEER]"),"Contextual prefix failed: "+profile);check(profile.get("suffix").getAsString().equals("&b[TEST]"),"Contextual suffix failed");check(profile.getAsJsonObject("capabilities").get("rivet.diagnostics").getAsBoolean(),"World permission missing");check(!profile.getAsJsonObject("capabilities").get("rivet.stats.edit").getAsBoolean(),"Other-world permission leaked");
   var voice=dev.abros.rivet.api.RivetApi.connect("rivet_compat_tests").orElseThrow().integrations().find(dev.abros.rivet.api.IntegrationApi.VOICE,"plasmovoice").orElseThrow();check(voice.available(),"Voice unavailable through Java API");
   Object instance=Class.forName("su.plo.voice.server.ModVoiceServer").getField("INSTANCE").get(null);Object muteManager=Class.forName("su.plo.voice.api.server.PlasmoVoiceServer").getMethod("getMuteManager").invoke(instance);var muteApi=Class.forName("su.plo.voice.api.server.mute.MuteManager");UUID target=p.getUUID();
   try {voice.mute(target,1,"Rivet integration smoke test");check(((Optional<?>)muteApi.getMethod("getMute",UUID.class).invoke(muteManager,target)).isPresent(),"Mute not applied");try{voice.mute(target,1,"Must not replace");throw new AssertionError("Existing mute replaced");}catch(IllegalArgumentException expected){}}finally{muteApi.getMethod("unmute",UUID.class,boolean.class).invoke(muteManager,target,false);}
   check(((Optional<?>)muteApi.getMethod("getMute",UUID.class).invoke(muteManager,target)).isEmpty(),"Test mute not cleaned");
   var spark=new dev.abros.rivet.server.compat.SparkAdapter();spark.clear();for(int i=0;i<3;i++)spark.lifecycle().call(()->{throw new NoSuchMethodException("Injected runtime failure");},()->false);check(!spark.lifecycle().available(),"Spark failure limit ignored");var snapshot=new com.google.gson.JsonObject();snapshot.addProperty("op","snapshot");var disabledSnapshot=spark.execute(p,snapshot,dev.abros.rivet.server.ServerProviders.find(dev.abros.rivet.api.IntegrationApi.IDENTITY,"rivet").orElseThrow());check(disabledSnapshot.get("status").getAsString().equals("Отключён после ошибок"),"Disabled spark diagnostics unavailable");check(!disabledSnapshot.get("commands").getAsBoolean(),"Disabled spark commands enabled");check(disabledSnapshot.getAsJsonObject("adapter").get("failures").getAsInt()==3,"Registry/lifecycle failure count diverged");spark.clear();
   verifyBothVoices(p);
   System.out.println("RIVET_OPTIONAL_ADAPTER_TEST_OK LuckPerms contextual prefix/suffix/permissions; Plasmo Voice mute/duplicate protection/cleanup");
  } finally {for(Object n:added)mapType.getMethod("remove",nodeType).invoke(nodes,n);Object cached=userType.getMethod("getCachedData").invoke(user);Class.forName("net.luckperms.api.cacheddata.CachedDataManager").getMethod("invalidate").invoke(cached);}
 }
 private static void verifyBothVoices(ServerPlayer p)throws Exception{
  var registry=dev.abros.rivet.api.RivetApi.connect("rivet_compat_tests").orElseThrow().integrations();
  var simple=registry.find(dev.abros.rivet.api.IntegrationApi.VOICE,"voicechat").orElseThrow();
  check(simple.available(),"Simple Voice Chat unavailable alongside Plasmo");
  var previous=dev.abros.rivet.compat.voice.SimpleVoiceBridge.mutes;
  var file=java.nio.file.Files.createTempDirectory(p.server.getServerDirectory(),"voice-test-").resolve("mutes.json");
  dev.abros.rivet.compat.voice.SimpleVoiceBridge.mutes=new dev.abros.rivet.core.VoiceMutes(file,System.currentTimeMillis());
  Object instance=Class.forName("su.plo.voice.server.ModVoiceServer").getField("INSTANCE").get(null);
  Object manager=Class.forName("su.plo.voice.api.server.PlasmoVoiceServer").getMethod("getMuteManager").invoke(instance);
  var api=Class.forName("su.plo.voice.api.server.mute.MuteManager");
  try{
   var method=Class.forName("dev.abros.rivet.server.VoiceModeration").getDeclaredMethod("mute",UUID.class,int.class,String.class);method.setAccessible(true);
   verifyMicrophoneEvents(p,false);
   method.invoke(null,p.getUUID(),1,"Both voice transports test");
   verifyMicrophoneEvents(p,true);
   check(simple.muted(p.getUUID()).orElseThrow(),"Simple mute missing");
   check(((Optional<?>)api.getMethod("getMute",UUID.class).invoke(manager,p.getUUID())).isPresent(),"Plasmo mute missing");
   check(new dev.abros.rivet.core.VoiceMutes(file,System.currentTimeMillis()).muted(p.getUUID(),System.currentTimeMillis()),"Simple mute did not persist");
   try{method.invoke(null,p.getUUID(),1,"Must not replace");throw new AssertionError("Duplicate dual mute accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalArgumentException,"Wrong duplicate failure");}
   System.out.println("RIVET_DUAL_VOICE_TEST_OK API availability/mute/both backends/proximity-whisper-group packet cancellation/duplicate/persistence");
  }finally{api.getMethod("unmute",UUID.class,boolean.class).invoke(manager,p.getUUID(),false);dev.abros.rivet.compat.voice.SimpleVoiceBridge.mutes=previous;java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(file.getParent());}
 }
 private static void verifyMicrophoneEvents(ServerPlayer p,boolean cancelled)throws Exception{
  var manager=Class.forName("de.maxhenkel.voicechat.plugins.PluginManager");var instance=manager.getMethod("instance").invoke(null);
  var stateClass=Class.forName("de.maxhenkel.voicechat.voice.common.PlayerState");var packetClass=Class.forName("de.maxhenkel.voicechat.voice.common.MicPacket");
  var state=stateClass.getConstructor(UUID.class,String.class,boolean.class,boolean.class).newInstance(p.getUUID(),p.getGameProfile().getName(),false,false);
  for(int kind=0;kind<3;kind++){
   stateClass.getMethod("setGroup",UUID.class).invoke(state,kind==2?UUID.randomUUID():null);
   var packet=packetClass.getConstructor(byte[].class,boolean.class,long.class).newInstance(new byte[0],kind==1,0L);
   boolean actual=(boolean)manager.getMethod("onMicPacket",ServerPlayer.class,stateClass,packetClass).invoke(instance,p,state,packet);
   check(actual==cancelled,"Microphone event cancellation mode="+kind+" expected="+cancelled);
  }
 }
 private static Object node(String type,String value,int priority,String world)throws Exception{var clazz=Class.forName("net.luckperms.api.node.types."+type);Object builder=type.equals("PermissionNode")?clazz.getMethod("builder",String.class).invoke(null,value):clazz.getMethod("builder",String.class,int.class).invoke(null,value,priority);var b=Class.forName("net.luckperms.api.node.NodeBuilder");b.getMethod("withContext",String.class,String.class).invoke(builder,"world",world);return b.getMethod("build").invoke(builder);}
 private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
