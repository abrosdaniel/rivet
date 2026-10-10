package dev.abros.rivet.core;

import com.google.gson.*;
/** Optional user-published location. Never trusts a client-provided dimension or numeric coercion. */
public record CommunityLocation(String name,String dimension,int x,int y,int z,boolean membersOnly) {
 public static CommunityLocation read(JsonObject j){
  Json.keys(j,"name","dimension","x","y","z","membersOnly");
  String name=Json.str(j,"name").strip(),dimension=Json.str(j,"dimension");
  if(name.isEmpty()||name.length()>80||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.location_name_1_80_characters_7d9a6e45"));
  if(dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.contains(".."))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_dimension_707327b2"));
  boolean privatePlace=false;if(j.has("membersOnly")){if(!j.get("membersOnly").isJsonPrimitive()||!j.getAsJsonPrimitive("membersOnly").isBoolean())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_location_visibility_f03d3000"));privatePlace=j.get("membersOnly").getAsBoolean();}
  return new CommunityLocation(name,dimension,coordinate(j,"x",30000000),coordinate(j,"y",2048),coordinate(j,"z",30000000),privatePlace);
 }
 private static int coordinate(JsonObject j,String key,int bound){try{var v=j.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int n=v.getAsBigDecimal().intValueExact();if(n < -bound||n>bound)throw new IllegalArgumentException();return n;}catch(Exception ex){throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_coordinate_a4aefd57")+key.toUpperCase());}}
 public JsonObject json(){return Json.GSON.toJsonTree(this).getAsJsonObject();}
 public String coordinates(){return x+" "+y+" "+z+" · "+dimension;}
}
