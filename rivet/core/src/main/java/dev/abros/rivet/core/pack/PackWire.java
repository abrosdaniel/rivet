package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.Json;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Bounded JSON control frames followed by explicitly sized binary file data over TLS. */
public final class PackWire {
 private PackWire(){}
 public static void write(DataOutputStream out,JsonObject value)throws IOException{byte[] bytes=Json.GSON.toJson(value).getBytes(StandardCharsets.UTF_8);if(bytes.length>8*1024*1024)throw new IOException("Ответ сборки слишком большой");out.writeInt(bytes.length);out.write(bytes);out.flush();}
 public static JsonObject read(DataInputStream in,int limit)throws IOException{int n=in.readInt();if(n<2||n>limit)throw new IOException("Некорректный размер сообщения сборки");byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();try{return Json.parse(new String(bytes,StandardCharsets.UTF_8));}catch(RuntimeException ex){throw new IOException("Некорректное сообщение сборки",ex);}}
}
