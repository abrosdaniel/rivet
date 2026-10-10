package dev.abros.rivet.core.map;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapHudPreferencesTest {
 @Test void importsLegacyNavigationAndPlacement(){var legacy=new JsonObject();legacy.addProperty("mapOffsetX",91);legacy.addProperty("directionEnabled",false);legacy.addProperty("directionScale",1.6);var value=MapHudPreferences.read(new JsonObject(),legacy);assertEquals(91,value.mapOffsetX);assertFalse(value.directionEnabled);assertEquals(1.6f,value.directionScale);assertEquals(28,value.directionOffsetY);}
 @Test void migratedSettingsNeverReimportOldHud(){var map=new JsonObject();var own=new JsonObject();own.addProperty("mapOffsetX",24);map.add("hud",own);var legacy=new JsonObject();legacy.addProperty("mapOffsetX",99);legacy.addProperty("directionEnabled",false);var value=MapHudPreferences.read(map,legacy);assertEquals(24,value.mapOffsetX);assertTrue(value.directionEnabled);}
 @Test void rejectsInvalidAndClampsBounds(){var j=new JsonObject();j.addProperty("directionScale","NaN");j.addProperty("mapAnchorX",99);j.addProperty("directionOffsetY",-50000);j.addProperty("directionOpacity","bad");var value=MapHudPreferences.read(new JsonObject(),j);assertEquals(1,value.directionScale);assertEquals(2,value.mapAnchorX);assertEquals(-4096,value.directionOffsetY);assertEquals(.85f,value.directionOpacity);}
 @Test void newPreferencesRoundTrip(){var value=new MapHudPreferences();value.directionCoordinates=false;value.mapAnchorY=2;value.mapOffsetY=57;var map=new JsonObject();map.add("hud",value.json());var read=MapHudPreferences.read(map,new JsonObject());assertFalse(read.directionCoordinates);assertEquals(2,read.mapAnchorY);assertEquals(57,read.mapOffsetY);}
 @Test void migratesMapRouteVisibilityWithoutOverridingNewChoice(){var old=new JsonObject();old.addProperty("directionXaero",false);assertFalse(MapHudPreferences.read(new JsonObject(),old).directionMap);old.addProperty("directionMap",true);var value=MapHudPreferences.read(new JsonObject(),old);assertTrue(value.directionMap);assertFalse(value.json().has("directionXaero"));assertTrue(value.json().has("directionMap"));}
}
