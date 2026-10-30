package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuChangeBatchTest {
 private static JsonObject event(String topic,String id,String owner){var e=new JsonObject();e.addProperty("topic",topic);e.addProperty("entity",id);e.addProperty("recipient",owner);return e;}
 @Test void notificationBurstStaysAddressedAndNeverResetsMap(){
  var events=new JsonArray();for(int i=0;i<100;i++)events.add(event("notifications","note"+i,"user"));events.add(event("groups","private","other"));
  var hints=MenuChangeBatch.hints(events,"user","notifications","");assertEquals(1,hints.size());assertEquals("notifications",Json.str(hints.getFirst(),"section"));assertFalse(MenuChangeHint.territories(hints.getFirst()));assertFalse(MenuChangeHint.activities(hints.getFirst()));assertTrue(MenuChangeBatch.hints(events,"other","notifications","").isEmpty());
 }
 @Test void homeGroupsTopicsSeparatelyAndGlobalChangesReachDetails(){
  var events=new JsonArray();events.add(event("groups","g",""));events.add(event("events","e",""));events.add(event("groups","g",""));
  assertEquals(2,MenuChangeBatch.hints(events,"user","home","").size());assertEquals(1,MenuChangeBatch.hints(events,"user","groups","g").size());assertTrue(MenuChangeBatch.hints(events,"user","groups","different").isEmpty());
  events.add(event("groups","",""));var detail=MenuChangeBatch.hints(events,"user","groups","different");assertEquals(1,detail.size());assertEquals("",Json.str(detail.getFirst(),"id"));
 }
}
