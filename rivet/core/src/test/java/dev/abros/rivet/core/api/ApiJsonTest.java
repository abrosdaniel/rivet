package dev.abros.rivet.core.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApiJsonTest {
 @Test void rejectsDuplicateNestedFieldsDepthAndTrailingInput(){
  for(String value:new String[]{"{\"x\":{\"a\":1,\"a\":2}}","{} {}","{\"x\":"+"[".repeat(20)+"0"+"]".repeat(20)+"}","{\"x\":NaN}"})assertThrows(Exception.class,()->ApiJson.object(value));
  assertEquals(2,ApiJson.object("{\"a\":1,\"b\":null}").size());
 }
 @Test void websocketRejectsOversizeBeforeAllocationAndPreservesGuardOnCopy(){
  var draft=new ApiWebSocketServer.BoundedDraft();assertThrows(Exception.class,()->draft.checkAlloc(Integer.MAX_VALUE));
  assertThrows(Exception.class,()->draft.copyInstance().checkAlloc(Integer.MAX_VALUE));
  draft.setParseMode(org.java_websocket.enums.Role.SERVER);
  assertThrows(Exception.class,()->draft.translateFrame(java.nio.ByteBuffer.wrap(new byte[]{(byte)0x81,(byte)0xff,0,0,0,0,0,1,0,0})));
 }
}
