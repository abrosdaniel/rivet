package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommunityInvalidationTest {
 private static JsonObject command(String op){var q=new JsonObject();q.addProperty("action","community");q.addProperty("op",op);return q;}
 @Test void mapPollingAndCardReadsCannotInvalidateTheirOwnSnapshots(){
  for(String op:new String[]{"groupMap","mapActivities","detail","list","hud","workGet","workList","plusProfile","plusItemRead","toolsMapPeers","mapPositionPeers","mapPositionSettings"}){
   var q=command(op);assertTrue(MenuRequests.read(q),op);assertFalse(MenuRequests.invalidatesCommunity(q),op);
  }
 }
 @Test void writesStillNotifySubscribersWhilePositionChangesUseDedicatedPath(){
  for(String op:new String[]{"plusTerritorySave","leave","invite","delete","workSave","plusKick"})assertTrue(MenuRequests.invalidatesCommunity(command(op)),op);
  assertFalse(MenuRequests.invalidatesCommunity(command("mapPositionSave")));
  var subscribe=new JsonObject();subscribe.addProperty("action","subscribe");assertFalse(MenuRequests.invalidatesCommunity(subscribe));
 }
}
