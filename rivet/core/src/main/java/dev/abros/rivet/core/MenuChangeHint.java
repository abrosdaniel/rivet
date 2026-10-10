package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.Set;

/** A subscription refresh is not evidence that permission-filtered map data changed. */
public final class MenuChangeHint {
 private MenuChangeHint(){}
 public static JsonObject subscription(String section,String id){
  var hint=new JsonObject();hint.addProperty("kind","changed");hint.addProperty("section",section);hint.addProperty("id",id);hint.addProperty("resync",true);return hint;
 }
 private static boolean dataChange(JsonObject hint){return !hint.has("resync")||!hint.get("resync").getAsBoolean();}
 public static boolean territories(JsonObject hint){return dataChange(hint)&&Set.of("","groups").contains(Json.opt(hint,"section",""));}
 public static boolean activities(JsonObject hint){return dataChange(hint)&&Set.of("","home","groups","events").contains(Json.opt(hint,"section",""));}
}
