package dev.abros.rivet.server;

import dev.abros.rivet.compat.voice.SimpleVoiceBridge;
import dev.abros.rivet.core.*;
import java.util.UUID;

/** Optional Simple Voice Chat moderation; deadlines survive reconnects and server restarts. */
public final class SimpleVoiceChatAdapter implements dev.abros.rivet.server.compat.CompatibilityAdapter {
 private static final OptionalIntegration LIFECYCLE=dev.abros.rivet.compat.IntegrationSupport.capability("voicechat","moderation",()->{
  Class.forName("de.maxhenkel.voicechat.api.events.MicrophonePacketEvent").getMethod("cancel");
  return "Simple Voice Chat API 2.5+ · MicrophonePacketEvent";
 });
 public static void start(net.minecraft.server.MinecraftServer server){
  SimpleVoiceBridge.mutes=null;
  if(!LIFECYCLE.available())return;
  LIFECYCLE.call(()->{SimpleVoiceBridge.mutes=new VoiceMutes(server.getServerDirectory().resolve("rivet/voicechat-mutes.json"),System.currentTimeMillis());return true;},()->false);
 }
 public static boolean available(){return LIFECYCLE.available()&&SimpleVoiceBridge.registered&&SimpleVoiceBridge.serverReady&&SimpleVoiceBridge.mutes!=null;}
 static void checkMute(UUID target){if(SimpleVoiceBridge.muted(target))throw new IllegalArgumentException(Messages.text("rivet.core.already_muted_existing_punishment_preserved_809c6c08"));}
 static void mute(UUID target,int minutes)throws Exception{
  if(!available())throw new IllegalArgumentException(Messages.text("rivet.voice.unavailable"));
  LIFECYCLE.required(()->{SimpleVoiceBridge.mutes.mute(target,minutes,System.currentTimeMillis());return true;});
 }
 public String id(){return "voicechat";}
 public OptionalIntegration lifecycle(){return LIFECYCLE;}
 public String status(){return LIFECYCLE.available()&&!available()&&LIFECYCLE.diagnostics().get("failures").getAsInt()==0?Messages.text("rivet.core.loading_72005dc8"):LIFECYCLE.status();}
 public com.google.gson.JsonObject diagnostics(){var row=LIFECYCLE.diagnostics();row.addProperty("id",id());row.addProperty("available",available());row.addProperty("status",status());row.add("capabilities",Json.GSON.toJsonTree(java.util.List.of("voice-mute")));return row;}
 public void clear(){SimpleVoiceBridge.mutes=null;LIFECYCLE.reset();}
 public com.google.gson.JsonObject execute(net.minecraft.server.level.ServerPlayer actor,com.google.gson.JsonObject request,dev.abros.rivet.server.compat.ServerIdentityDirectory identities){throw new IllegalArgumentException(Messages.text("rivet.voice.use_vote"));}
}
