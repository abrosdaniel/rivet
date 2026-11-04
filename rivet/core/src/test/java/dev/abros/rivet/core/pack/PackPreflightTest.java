package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackPreflightTest {
 @Test void serverWithoutPackCanRequireCoreBeforeLogin()throws Exception{
  var status=new JsonObject();var rivet=new JsonObject();rivet.addProperty("preflight",1);rivet.addProperty("version","2.6.1");rivet.add("protocols",WireProtocols.current());status.add("rivet",rivet);
  var result=PackDiscovery.parseStatus(status,"localhost");assertNull(result.pack());assertFalse(result.compatible("2.6.0"));assertTrue(result.compatible("2.6.1"));assertFalse(result.compatible("3.0.0"));
  var changed=WireProtocols.current();changed.addProperty("auth",999);assertFalse(result.compatible("2.7.0",changed));
 }
 @Test void vanillaAndLegacyStatusesKeepTheirBehaviour()throws Exception{
  var result=PackDiscovery.parseStatus(new JsonObject(),"localhost");assertNull(result.pack());assertTrue(result.compatible("2.0.0"));
 }

 @Test void malformedExtensionIsAReportedDiscoveryFailure(){
  for(String json:java.util.List.of("{\"rivet\":null}","{\"rivet\":{\"preflight\":1,\"version\":\"2.5.3\"}}"))assertThrows(java.io.IOException.class,()->PackDiscovery.parseStatus(com.google.gson.JsonParser.parseString(json).getAsJsonObject(),"localhost"));
 }
}
