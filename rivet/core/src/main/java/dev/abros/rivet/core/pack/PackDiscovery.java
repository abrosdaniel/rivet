package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
/** Standard Minecraft status query works before mod negotiation and account login. */
public final class PackDiscovery {
 public record Endpoint(String host,int port,String fingerprint,boolean required){}
 private PackDiscovery(){}
 public static Endpoint query(InetSocketAddress socketAddress,String handshakeHost,int gamePort,String fallbackHost)throws IOException{
  try(var socket=new Socket()){socket.connect(socketAddress,3000);socket.setSoTimeout(3000);var out=new DataOutputStream(socket.getOutputStream());var in=new DataInputStream(socket.getInputStream());var bytes=new ByteArrayOutputStream();var handshake=new DataOutputStream(bytes);varInt(handshake,0);varInt(handshake,767);byte[] host=handshakeHost.getBytes(StandardCharsets.UTF_8);if(host.length>1024)throw new IOException("Адрес сервера слишком длинный");varInt(handshake,host.length);handshake.write(host);handshake.writeShort(gamePort);varInt(handshake,1);varInt(out,bytes.size());out.write(bytes.toByteArray());out.write(new byte[]{1,0});out.flush();int length=varInt(in);if(length<3||length>1024*1024)throw new IOException("Ответ сервера слишком большой");byte[] frame=in.readNBytes(length);if(frame.length!=length)throw new EOFException();var packet=new DataInputStream(new ByteArrayInputStream(frame));if(varInt(packet)!=0)throw new IOException("Некорректный status packet");int count=varInt(packet);if(count<2||count>packet.available())throw new IOException("Некорректный status JSON");var json=Json.parse(new String(packet.readNBytes(count),StandardCharsets.UTF_8));if(!json.has("rivetPack"))return null;var p=json.getAsJsonObject("rivetPack");if(p.get("protocol").getAsInt()!=dev.abros.rivet.core.WireProtocols.version("pack"))throw new IOException("Неподдерживаемый протокол сборки");String fingerprint=Json.str(p,"fingerprint");Hashes.check(fingerprint);String endpoint=Json.opt(p,"host","");int port=p.get("port").getAsInt();if(port<1||port>65535)throw new IOException("Некорректный порт раздачи");if(endpoint.isBlank())endpoint=fallbackHost;if(endpoint.length()>253||endpoint.contains("/")||endpoint.contains("@"))throw new IOException("Некорректный адрес раздачи");return new Endpoint(endpoint,port,fingerprint,p.get("required").getAsBoolean());}
 }
 private static int varInt(DataInputStream in)throws IOException{int value=0;for(int i=0;i<5;i++){int b=in.readUnsignedByte();value|=(b&127)<<(7*i);if((b&128)==0)return value;}throw new IOException("Некорректный VarInt");}
 private static void varInt(DataOutputStream out,int n)throws IOException{while((n&~127)!=0){out.writeByte((n&127)|128);n>>>=7;}out.writeByte(n);}
}
