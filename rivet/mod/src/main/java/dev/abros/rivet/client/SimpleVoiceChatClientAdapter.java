package dev.abros.rivet.client;

import dev.abros.rivet.compat.voice.SimpleVoiceBridge;
import dev.abros.rivet.core.OptionalIntegration;
import java.util.List;

final class SimpleVoiceChatClientAdapter {
 static String probe()throws ReflectiveOperationException{var api=Class.forName("de.maxhenkel.voicechat.api.VoicechatClientApi");api.getMethod("isDisconnected");api.getMethod("isMuted");api.getMethod("isDisabled");return "Simple Voice Chat API 2.5+";}
 static List<String> inspect()throws Exception{
  var provider=SimpleVoiceBridge.client;if(provider==null)throw new OptionalIntegration.NotReadyException("Simple Voice Chat client is starting");
  var state=provider.get();return List.of("Simple Voice Chat", "UDP: "+Client.text(state.disconnected()?"ui.not_connected_d1d7537a":"ui.connected_e0d46a96"),Client.text("ui.microphone_4952b556")+Client.text(state.muted()?"ui.disabled_0b48cc87":"ui.enabled_bbbf3850"),Client.text("voice.sound")+Client.text(state.disabled()?"ui.disabled_0b48cc87":"ui.enabled_bbbf3850"),Client.text("ui.server_mute_8b4dac8c")+(ServerMenuClient.state.has("simpleVoiceMuted")?Client.text(ServerMenuClient.state.get("simpleVoiceMuted").getAsBoolean()?"ui.yes_d4f57dba":"ui.no_ced07fd1"):Client.text("ui.unknown_478fa305")),Client.text("voice.settings_hint"));
 }
}
