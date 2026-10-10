package dev.abros.rivet.core.map;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import java.util.Set;

/** Persistent privacy choice; live coordinates never belong in its storage. */
public record MapPositionPolicy(String mode,String audience) {
 public MapPositionPolicy {if(!Set.of("full","nearby","hidden").contains(mode)||!Set.of("all","groups","none").contains(audience))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_position_visibility_5c86d516"));}
 public static MapPositionPolicy defaults(){return new MapPositionPolicy("full","all");}
 public static MapPositionPolicy read(JsonObject j){return j==null?defaults():new MapPositionPolicy(Json.str(j,"mode"),Json.str(j,"audience"));}
 public JsonObject json(){var j=new JsonObject();j.addProperty("mode",mode);j.addProperty("audience",audience);return j;}
 public boolean audienceAllows(boolean sharedGroup){return !mode.equals("hidden")&&!audience.equals("none")&&(audience.equals("all")||sharedGroup);}
 public boolean distanceAllows(String dimension,double x,double y,double z,String viewerDimension,double vx,double vy,double vz,int radius){if(mode.equals("hidden"))return false;if(mode.equals("full"))return true;double dx=x-vx,dy=y-vy,dz=z-vz;return dimension.equals(viewerDimension)&&dx*dx+dy*dy+dz*dz<=(double)radius*radius;}
}
