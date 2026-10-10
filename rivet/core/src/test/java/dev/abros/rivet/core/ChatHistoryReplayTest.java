package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryReplayTest {
 @Test void damagedRowDoesNotHideOlderMessages(){
  var rejected=new ArrayList<Long>();
  var packets=ChatHistoryReplay.packets(List.of(new ChatHistory.Message(3,1,"{broken"),new ChatHistory.Message(2,1,"{\"text\":\"valid\"}"),new ChatHistory.Message(1,1,"\"older\"")),rejected::add);
  assertEquals(List.of(3L),rejected);assertEquals(List.of(2L,1L),packets.stream().map(p->p.get("id").getAsLong()).toList());
 }
 @Test void supportsAllMinecraftComponentJsonShapes(){
  var packets=ChatHistoryReplay.packets(List.of(new ChatHistory.Message(3,1,"\"text\""),new ChatHistory.Message(2,1,"{\"text\":\"object\"}"),new ChatHistory.Message(1,1,"[\"first\",{\"text\":\"second\"}]")),id->fail("Valid component rejected"));
  assertEquals(3,packets.size());assertTrue(packets.getFirst().get("message").isJsonPrimitive());assertTrue(packets.getLast().get("message").isJsonArray());
 }
 @Test void oversizedAndInvalidRowsCannotBreakPacketEncoding(){
  var rejected=new ArrayList<Long>();
  assertTrue(ChatHistoryReplay.packets(List.of(new ChatHistory.Message(1,1,"\""+"x".repeat(32768)+"\""),new ChatHistory.Message(2,1,"null"),new ChatHistory.Message(3,1,"true")),rejected::add).isEmpty());
  assertEquals(List.of(1L,2L,3L),rejected);
 }
}
