package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.*;
import java.util.function.LongConsumer;

/** A damaged entry must not prevent the replay cursor from advancing past its page. */
public final class ChatHistoryReplay {
 private ChatHistoryReplay(){}
 public static List<JsonObject> packets(List<ChatHistory.Message> messages,LongConsumer rejected){
  var packets=new ArrayList<JsonObject>();
  for(var message:messages){
   try{
    // Minecraft components may be objects, strings or arrays. Keep strict JSON validation.
    var body=Json.parse("{\"message\":"+message.body()+"}").get("message");
    if(body.isJsonNull()||body.isJsonPrimitive()&&!body.getAsJsonPrimitive().isString())throw new IOException("Invalid component");
    var packet=new JsonObject();packet.addProperty("kind","chatHistory");packet.addProperty("id",message.id());packet.addProperty("at",message.at());packet.add("message",body);
    if(packet.toString().length()>32767)throw new IOException("History packet too large");
    packets.add(packet);
   }catch(IOException|IllegalArgumentException failure){rejected.accept(message.id());}
  }
  return List.copyOf(packets);
 }
}
