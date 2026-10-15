package dev.abros.rivet.server;

import dev.abros.rivet.core.Messages;
import java.util.UUID;

/** Every installed voice transport must be enforceable; never silently leave a second one open. */
final class VoiceModeration {
 static boolean available(){
  var mods=net.neoforged.fml.ModList.get();boolean plasmo=mods.isLoaded("plasmovoice"),simple=mods.isLoaded("voicechat");
  return (plasmo||simple)&&(!plasmo||voice("plasmovoice").map(dev.abros.rivet.api.IntegrationApi.Voice::available).orElse(false))&&(!simple||voice("voicechat").map(dev.abros.rivet.api.IntegrationApi.Voice::available).orElse(false));
 }
 static void mute(UUID target,int minutes,String reason)throws Exception{
  if(!available())throw new IllegalArgumentException(Messages.text("rivet.voice.unavailable"));
  boolean plasmo=net.neoforged.fml.ModList.get().isLoaded("plasmovoice"),simple=net.neoforged.fml.ModList.get().isLoaded("voicechat");
  if(plasmo)voice("plasmovoice").orElseThrow().checkMute(target);
  if(simple)voice("voicechat").orElseThrow().checkMute(target);
  // Preflight both before applying; a backend failure is reported, never recorded as success.
  if(simple)voice("voicechat").orElseThrow().mute(target,minutes,reason);
  if(plasmo)voice("plasmovoice").orElseThrow().mute(target,minutes,reason);
 }
 private static java.util.Optional<dev.abros.rivet.api.IntegrationApi.Voice> voice(String id){return ServerProviders.find(dev.abros.rivet.api.IntegrationApi.VOICE,id);}
 private VoiceModeration(){}
}
