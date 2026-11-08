package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatEffectEnvelopeTest {
 @Test void metadataRoundTripsAndRejectsUnboundedOrMalformedInput(){
  var value=new ChatEffectEnvelope(255,Long.MIN_VALUE);assertEquals(value,ChatEffectEnvelope.read(value.encode()).orElseThrow());
  for(String invalid:new String[]{"ordinary text","rivet:chat-body:256:0","rivet:chat-body:-1:0","rivet:chat-body:1:NaN","rivet:chat-body:1:0:extra","rivet:chat-body:"+"1".repeat(100)})assertTrue(ChatEffectEnvelope.read(invalid).isEmpty());
  assertTrue(ChatEffectEnvelope.read(null).isEmpty());
 }
}
