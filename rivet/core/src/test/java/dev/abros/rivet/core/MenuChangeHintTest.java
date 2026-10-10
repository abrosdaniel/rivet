package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MenuChangeHintTest {
 @Test void openingAndResyncingMenuNeverInvalidatesMapSnapshots(){
  for(String section:new String[]{"home","groups","events",""}){
   var hint=MenuChangeHint.subscription(section,"group-id");assertEquals("changed",Json.str(hint,"kind"));assertEquals(section,Json.str(hint,"section"));assertEquals("group-id",Json.str(hint,"id"));
   assertFalse(MenuChangeHint.territories(hint));assertFalse(MenuChangeHint.activities(hint));
  }
 }
 @Test void realGroupChangesAndLegacyGlobalHintsStillInvalidateAccess(){
  var hint=new JsonObject();assertTrue(MenuChangeHint.territories(hint));assertTrue(MenuChangeHint.activities(hint));
  hint.addProperty("section","groups");hint.addProperty("resync",false);assertTrue(MenuChangeHint.territories(hint));assertTrue(MenuChangeHint.activities(hint));
 }
 @Test void unrelatedChangesDoNotEraseTerritories(){
  var hint=new JsonObject();for(String section:new String[]{"home","events"}){hint.addProperty("section",section);assertFalse(MenuChangeHint.territories(hint));assertTrue(MenuChangeHint.activities(hint));}
  hint.addProperty("section","notifications");assertFalse(MenuChangeHint.territories(hint));assertFalse(MenuChangeHint.activities(hint));
 }
}
