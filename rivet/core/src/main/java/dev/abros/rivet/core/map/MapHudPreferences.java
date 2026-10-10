package dev.abros.rivet.core.map;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
/** Map-owned navigation and HUD placement. Legacy HUD values are imported only once. */
public final class MapHudPreferences {
 public boolean directionEnabled=true;
 public boolean directionCoordinates=true;
 public boolean directionMap=true;
 public float directionOpacity=.85f;
 public float directionScale=1;
 public int directionAnchorX=1;
 public int directionAnchorY=1;
 public int directionOffsetX=0;
 public int directionOffsetY=28;
 public int mapAnchorX=0;
 public int mapAnchorY=0;
 public int mapOffsetX=8;
 public int mapOffsetY=8;
 public static MapHudPreferences read(JsonObject map,JsonObject legacy){var result=new MapHudPreferences();var j=map.has("hud")&&map.get("hud").isJsonObject()?map.getAsJsonObject("hud"):legacy;
 if(j.has("directionEnabled")&&j.get("directionEnabled").isJsonPrimitive()&&j.getAsJsonPrimitive("directionEnabled").isBoolean())result.directionEnabled=j.get("directionEnabled").getAsBoolean();
 if(j.has("directionCoordinates")&&j.get("directionCoordinates").isJsonPrimitive()&&j.getAsJsonPrimitive("directionCoordinates").isBoolean())result.directionCoordinates=j.get("directionCoordinates").getAsBoolean();
 if(!j.has("directionMap")&&j.has("directionXaero")&&j.get("directionXaero").isJsonPrimitive()&&j.getAsJsonPrimitive("directionXaero").isBoolean())result.directionMap=j.get("directionXaero").getAsBoolean();
 if(j.has("directionMap")&&j.get("directionMap").isJsonPrimitive()&&j.getAsJsonPrimitive("directionMap").isBoolean())result.directionMap=j.get("directionMap").getAsBoolean();
 result.directionOpacity=(float)number(j,"directionOpacity",.85f,0f,1f);
 result.directionScale=(float)number(j,"directionScale",1,0.65f,2f);
 result.directionAnchorX=(int)number(j,"directionAnchorX",1,0f,2f);
 result.directionAnchorY=(int)number(j,"directionAnchorY",1,0f,2f);
 result.directionOffsetX=(int)number(j,"directionOffsetX",0,-4096f,4096f);
 result.directionOffsetY=(int)number(j,"directionOffsetY",28,-4096f,4096f);
 result.mapAnchorX=(int)number(j,"mapAnchorX",0,0f,2f);
 result.mapAnchorY=(int)number(j,"mapAnchorY",0,0f,2f);
 result.mapOffsetX=(int)number(j,"mapOffsetX",8,-4096f,4096f);
 result.mapOffsetY=(int)number(j,"mapOffsetY",8,-4096f,4096f);
 return result;}
 private static float number(JsonObject j,String key,float fallback,float min,float max){try{float value=j.has(key)?j.get(key).getAsFloat():fallback;return Float.isFinite(value)?Math.clamp(value,min,max):fallback;}catch(RuntimeException e){return fallback;}}
 public JsonObject json(){return Json.GSON.toJsonTree(this).getAsJsonObject();}
}
