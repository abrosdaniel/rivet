package dev.abros.rivet.server;

import dev.abros.rivet.compat.voice.SimpleVoiceBridge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Optional-mod discovery on a real NeoForge server; never opens a microphone or modifies punishments. */
@EventBusSubscriber(modid="rivet")
public final class SimpleVoiceServerHarness {
 private static long started;private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  String mode=System.getenv("RIVET_SIMPLE_VOICE_TEST");if(mode==null||done)return;
  long now=System.currentTimeMillis();if(started==0)started=now;
  try{
   boolean present=mode.equals("present");
   if(present&&!SimpleVoiceChatAdapter.available()&&now-started<20000)return;
   if(SimpleVoiceChatAdapter.available()!=present)throw new IllegalStateException("Unexpected Simple Voice Chat availability: "+new SimpleVoiceChatAdapter().diagnostics());
   if(present){if(!SimpleVoiceBridge.registered||!SimpleVoiceBridge.serverReady||!VoiceModeration.available())throw new IllegalStateException("Plugin or moderation not ready");}
   else {try{Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");throw new IllegalStateException("Optional API was bundled unexpectedly");}catch(ClassNotFoundException expected){}}
   System.out.println("RIVET_SIMPLE_VOICE_SERVER_OK "+mode);
  }catch(Throwable failure){System.out.println("RIVET_SIMPLE_VOICE_SERVER_FAILED "+mode);failure.printStackTrace();}
  done=true;event.getServer().halt(false);
 }
}
