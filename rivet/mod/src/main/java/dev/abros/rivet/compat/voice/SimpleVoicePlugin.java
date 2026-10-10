package dev.abros.rivet.compat.voice;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;

/** Discovered by Simple Voice Chat only; API is compile-only and is never bundled. */
@ForgeVoicechatPlugin
public final class SimpleVoicePlugin implements VoicechatPlugin {
 @Override public String getPluginId(){return "rivet";}
 @Override public void registerEvents(EventRegistration events){
  events.registerEvent(ClientVoicechatInitializationEvent.class,event->{var api=event.getVoicechat();SimpleVoiceBridge.client=()->new SimpleVoiceBridge.ClientState(api.isDisconnected(),api.isMuted(),api.isDisabled());});
  events.registerEvent(VoicechatServerStartedEvent.class,event->SimpleVoiceBridge.serverReady=true);
  events.registerEvent(VoicechatServerStoppedEvent.class,event->SimpleVoiceBridge.serverReady=false);
  // Cancel before SVC distributes proximity, whisper or group audio. Never un-cancel another plugin.
  events.registerEvent(MicrophonePacketEvent.class,event->{var sender=event.getSenderConnection();if(sender!=null&&SimpleVoiceBridge.muted(sender.getPlayer().getUuid()))event.cancel();},100);
  SimpleVoiceBridge.registered=true;
 }
}
