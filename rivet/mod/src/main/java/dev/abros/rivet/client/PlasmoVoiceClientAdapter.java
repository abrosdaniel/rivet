package dev.abros.rivet.client;
import java.util.*;
/** Optional, read-only Plasmo Voice diagnostics. */
final class PlasmoVoiceClientAdapter {
 static String probe()throws ReflectiveOperationException{Class.forName("su.plo.voice.client.ModVoiceClient").getField("INSTANCE");Class<?> client=Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");client.getMethod("getUdpClientManager");client.getMethod("getDeviceManager");client.getMethod("getServerConnection");Class.forName("su.plo.voice.api.client.connection.UdpClientManager").getMethod("isConnected");Class.forName("su.plo.voice.api.client.audio.device.DeviceManager").getMethod("getInputDevice");Class.forName("su.plo.voice.api.client.connection.ServerConnection").getMethod("getPlayerById",UUID.class);Class<?> info=Class.forName("su.plo.voice.proto.data.player.VoicePlayerInfo");info.getMethod("isMuted");info.getMethod("isMicrophoneMuted");return Client.text("message.voice_client_diagnostics");}
 private static Object call(Object instance,String api,String method)throws ReflectiveOperationException{return Class.forName(api).getMethod(method).invoke(instance);}
 static List<String> inspect()throws ReflectiveOperationException{String connection=Client.text("ui.unknown_478fa305"),microphone=Client.text("ui.unknown_478fa305"),muted=Client.text("ui.unknown_478fa305"),device=Client.text("ui.unknown_478fa305");
  {
   Object client=Class.forName("su.plo.voice.client.ModVoiceClient").getField("INSTANCE").get(null);String api="su.plo.voice.api.client.PlasmoVoiceClient";
   if(client!=null){Object udp=call(client,api,"getUdpClientManager");connection=Boolean.TRUE.equals(call(udp,"su.plo.voice.api.client.connection.UdpClientManager","isConnected"))?Client.text("ui.connected_e0d46a96"):Client.text("ui.not_connected_d1d7537a");
    Object manager=call(client,api,"getDeviceManager");device=((Optional<?>)call(manager,"su.plo.voice.api.client.audio.device.DeviceManager","getInputDevice")).isPresent()?Client.text("ui.available_d8214019"):Client.text("ui.not_opened_9f255c2c");
    var server=(Optional<?>)call(client,api,"getServerConnection");if(server.isPresent()&&net.minecraft.client.Minecraft.getInstance().player!=null){var player=(Optional<?>)Class.forName("su.plo.voice.api.client.connection.ServerConnection").getMethod("getPlayerById",UUID.class).invoke(server.get(),net.minecraft.client.Minecraft.getInstance().player.getUUID());if(player.isPresent()){String type="su.plo.voice.proto.data.player.VoicePlayerInfo";muted=Boolean.TRUE.equals(call(player.get(),type,"isMuted"))?Client.text("ui.yes_d4f57dba"):Client.text("ui.no_ced07fd1");microphone=Boolean.TRUE.equals(call(player.get(),type,"isMicrophoneMuted"))?Client.text("ui.disabled_0b48cc87"):Client.text("ui.enabled_bbbf3850");}}
   }
  }
return List.of(Client.text("ui.plasmo_voice_installed_8ca59e4e"),"UDP: "+connection,Client.text("ui.recording_device_71e7993e")+device,Client.text("ui.microphone_4952b556")+microphone,Client.text("ui.server_mute_8b4dac8c")+muted,Client.text("ui.plasmo_voice_settings_v_key_0414e039"));}
}
