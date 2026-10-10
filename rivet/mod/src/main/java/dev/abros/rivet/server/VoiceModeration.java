package dev.abros.rivet.server;

import dev.abros.rivet.core.Messages;
import java.util.UUID;

/** Every installed voice transport must be enforceable; never silently leave a second one open. */
final class VoiceModeration {
 static boolean available(){
  var mods=net.neoforged.fml.ModList.get();boolean plasmo=mods.isLoaded("plasmovoice"),simple=mods.isLoaded("voicechat");
  return (plasmo||simple)&&(!plasmo||PlasmoVoiceAdapter.available())&&(!simple||SimpleVoiceChatAdapter.available());
 }
 static void mute(UUID target,int minutes,String reason)throws Exception{
  if(!available())throw new IllegalArgumentException(Messages.text("rivet.voice.unavailable"));
  boolean plasmo=net.neoforged.fml.ModList.get().isLoaded("plasmovoice"),simple=net.neoforged.fml.ModList.get().isLoaded("voicechat");
  if(plasmo)PlasmoVoiceAdapter.checkMute(target);
  if(simple)SimpleVoiceChatAdapter.checkMute(target);
  // Preflight both before applying; a backend failure is reported, never recorded as success.
  if(simple)SimpleVoiceChatAdapter.mute(target,minutes);
  if(plasmo)PlasmoVoiceAdapter.mute(target,minutes,reason);
 }
 private VoiceModeration(){}
}
