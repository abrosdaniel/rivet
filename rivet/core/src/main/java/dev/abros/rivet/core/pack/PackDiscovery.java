package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
/** Standard Minecraft status query works before mod negotiation and account login. */
public final class PackDiscovery {
 public record Status(Endpoint pack,String coreVersion,JsonObject protocols,java.util.List<Endpoint> routes){
  public Status(Endpoint pack,String coreVersion,JsonObject protocols){this(pack,coreVersion,protocols,pack==null?java.util.List.of():java.util.List.of(pack));}
  public Status{protocols=protocols.deepCopy();routes=java.util.List.copyOf(routes);}
  @Override public JsonObject protocols(){return protocols.deepCopy();}
  public boolean compatible(String installed){return compatible(installed,WireProtocols.current());}
  public boolean compatible(String installed,JsonObject clientProtocols){if(coreVersion.isEmpty())return true;if(!Versions.sameMajor(installed,coreVersion)||Versions.compare(installed,coreVersion)<0)return false;for(String key:java.util.List.of("pack","auth","menu"))if(!protocols.has(key)||!protocols.get(key).equals(clientProtocols.get(key)))return false;return true;}
 }
 public record Endpoint(String host,int port,String fingerprint,boolean required){}
 private PackDiscovery(){}
 public static Endpoint query(InetSocketAddress socketAddress,String handshakeHost,int gamePort,String fallbackHost)throws IOException{return status(socketAddress,handshakeHost,gamePort,fallbackHost).pack();}
 public static Status status(InetSocketAddress socketAddress,String handshakeHost,int gamePort,String fallbackHost)throws IOException{
  try(var socket=new Socket()){socket.connect(socketAddress,3000);socket.setSoTimeout(3000);var out=new DataOutputStream(socket.getOutputStream());var in=new DataInputStream(socket.getInputStream());var bytes=new ByteArrayOutputStream();var handshake=new DataOutputStream(bytes);varInt(handshake,0);varInt(handshake,767);byte[] host=handshakeHost.getBytes(StandardCharsets.UTF_8);if(host.length>1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.server_address_is_too_long_afc8e94b"));varInt(handshake,host.length);handshake.write(host);handshake.writeShort(gamePort);varInt(handshake,1);varInt(out,bytes.size());out.write(bytes.toByteArray());out.write(new byte[]{1,0});out.flush();int length=varInt(in);if(length<3||length>1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.server_response_is_too_large_1c81101b"));byte[] frame=in.readNBytes(length);if(frame.length!=length)throw new EOFException();var packet=new DataInputStream(new ByteArrayInputStream(frame));if(varInt(packet)!=0)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_status_packet_615ad200"));int count=varInt(packet);if(count<2||count>packet.available())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_status_json_ef9fcd65"));var json=Json.parse(new String(packet.readNBytes(count),StandardCharsets.UTF_8));return parseStatus(json,fallbackHost);}
 }
 public static Status parseStatus(JsonObject json,String fallbackHost)throws IOException{
 try{
 String version="";var protocols=new JsonObject();if(json.has("rivet")){var rivet=json.getAsJsonObject("rivet");if(rivet.get("preflight").getAsInt()!=1)throw new IOException("Unsupported Rivet preflight protocol");version=Json.str(rivet,"version");if(!Versions.isRelease(version)||version.length()>64)throw new IOException("Invalid Rivet server version");protocols=rivet.getAsJsonObject("protocols");if(protocols==null)throw new IOException("Missing Rivet server protocols");for(String key:java.util.List.of("pack","auth","menu")){if(!protocols.has(key)||!protocols.get(key).isJsonPrimitive()||!protocols.getAsJsonPrimitive(key).isNumber()||protocols.get(key).getAsInt()<1)throw new IOException("Invalid Rivet server protocol");}}
 if(!json.has("rivetPack"))return new Status(null,version,protocols);var p=json.getAsJsonObject("rivetPack");if(p.get("protocol").getAsInt()!=dev.abros.rivet.core.WireProtocols.version("pack"))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unsupported_modpack_protocol_17fa443e"));String fingerprint=Json.str(p,"fingerprint");Hashes.check(fingerprint);String endpoint=Json.opt(p,"host","");int port=p.get("port").getAsInt();if(port<1||port>65535)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_distribution_port_488ea76b"));if(endpoint.isBlank())endpoint=fallbackHost;if(endpoint.length()>253||endpoint.contains("/")||endpoint.contains("@"))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_distribution_address_d7a8add6"));var legacy=new Endpoint(endpoint,port,fingerprint,p.get("required").getAsBoolean());return new Status(legacy,version,protocols,PackRoutes.endpoints(p,fallbackHost,legacy));
 }catch(RuntimeException invalid){throw new IOException("Invalid Rivet server status",invalid);}
 }
 private static int varInt(DataInputStream in)throws IOException{int value=0;for(int i=0;i<5;i++){int b=in.readUnsignedByte();value|=(b&127)<<(7*i);if((b&128)==0)return value;}throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_varint_7e79bd24"));}
 private static void varInt(DataOutputStream out,int n)throws IOException{while((n&~127)!=0){out.writeByte((n&127)|128);n>>>=7;}out.writeByte(n);}
}
