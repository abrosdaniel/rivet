package dev.abros.rivet.client;
import java.util.*;
/** Optional, read-only Plasmo Voice diagnostics. */
final class PlasmoVoiceClientAdapter {
 static String probe()throws ReflectiveOperationException{Class.forName("su.plo.voice.client.ModVoiceClient").getField("INSTANCE");Class<?> client=Class.forName("su.plo.voice.api.client.PlasmoVoiceClient");client.getMethod("getUdpClientManager");client.getMethod("getDeviceManager");client.getMethod("getServerConnection");Class.forName("su.plo.voice.api.client.connection.UdpClientManager").getMethod("isConnected");Class.forName("su.plo.voice.api.client.audio.device.DeviceManager").getMethod("getInputDevice");Class.forName("su.plo.voice.api.client.connection.ServerConnection").getMethod("getPlayerById",UUID.class);Class<?> info=Class.forName("su.plo.voice.proto.data.player.VoicePlayerInfo");info.getMethod("isMuted");info.getMethod("isMicrophoneMuted");return "Plasmo Voice 2 · client diagnostics";}
 private static Object call(Object instance,String api,String method)throws ReflectiveOperationException{return Class.forName(api).getMethod(method).invoke(instance);}
 static List<String> inspect()throws ReflectiveOperationException{String connection="неизвестно",microphone="неизвестно",muted="неизвестно",device="неизвестно";
  {
   Object client=Class.forName("su.plo.voice.client.ModVoiceClient").getField("INSTANCE").get(null);String api="su.plo.voice.api.client.PlasmoVoiceClient";
   if(client!=null){Object udp=call(client,api,"getUdpClientManager");connection=Boolean.TRUE.equals(call(udp,"su.plo.voice.api.client.connection.UdpClientManager","isConnected"))?"подключён":"нет соединения";
    Object manager=call(client,api,"getDeviceManager");device=((Optional<?>)call(manager,"su.plo.voice.api.client.audio.device.DeviceManager","getInputDevice")).isPresent()?"доступно":"не открыто";
    var server=(Optional<?>)call(client,api,"getServerConnection");if(server.isPresent()&&net.minecraft.client.Minecraft.getInstance().player!=null){var player=(Optional<?>)Class.forName("su.plo.voice.api.client.connection.ServerConnection").getMethod("getPlayerById",UUID.class).invoke(server.get(),net.minecraft.client.Minecraft.getInstance().player.getUUID());if(player.isPresent()){String type="su.plo.voice.proto.data.player.VoicePlayerInfo";muted=Boolean.TRUE.equals(call(player.get(),type,"isMuted"))?"да":"нет";microphone=Boolean.TRUE.equals(call(player.get(),type,"isMicrophoneMuted"))?"выключен":"включён";}}
   }
  }
return List.of("Plasmo Voice: установлен","UDP: "+connection,"Устройство записи: "+device,"Микрофон: "+microphone,"Серверный mute: "+muted,"Настройки Plasmo Voice: клавиша V");}
}
