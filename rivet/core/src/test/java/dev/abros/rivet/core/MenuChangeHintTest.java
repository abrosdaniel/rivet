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
  var hint=new JsonObject();for(String section:new String[]{"home"}){hint.addProperty("section",section);assertFalse(MenuChangeHint.territories(hint));assertTrue(MenuChangeHint.activities(hint));}
  hint.addProperty("section","notifications");assertFalse(MenuChangeHint.territories(hint));assertFalse(MenuChangeHint.activities(hint));
 }
 @Test void hudRefreshesOnRelevantDataWithoutRefreshingOnSubscription(){
  for(String section:new String[]{"", "home", "groups", "notifications"}){
   var hint=new JsonObject();hint.addProperty("section",section);assertTrue(MenuChangeHint.hud(hint));
   assertFalse(MenuChangeHint.hud(MenuChangeHint.subscription(section,"")));
  }
  for(String section:new String[]{"skins", "profile", "chat", "tasks", "storage", "events"}){
   var hint=new JsonObject();hint.addProperty("section",section);assertFalse(MenuChangeHint.hud(hint));
  }
 }
 @org.junit.jupiter.api.Test void dashboardResyncAndChangesStayScoped(){
  for(String section:java.util.List.of("","home","reports","myReports"))org.junit.jupiter.api.Assertions.assertTrue(MenuChangeHint.dashboard(MenuChangeHint.subscription(section,"")));
  for(String section:java.util.List.of("groups","players","notifications"))org.junit.jupiter.api.Assertions.assertFalse(MenuChangeHint.dashboard(MenuChangeHint.subscription(section,"")));
 }
}
