package dev.abros.rivet.core.pack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackRuntimeTest {
 @Test void acceptsStableNeoForgeBranchButNeverChangesMinecraft(){
  assertTrue(PackRuntime.supported("1.21.1","21.1.250","21.1.251"));
  assertTrue(PackRuntime.supported("1.21.1","21.1.251","21.1.250"));
  for(String version:new String[]{"21.1.249","21.2.250","21.1.251-beta","21.1.250.1","",null})assertFalse(PackRuntime.supported("1.21.1","21.1.250",version));
  assertFalse(PackRuntime.supported("1.21","21.1.250","21.1.250"));
 }
}
