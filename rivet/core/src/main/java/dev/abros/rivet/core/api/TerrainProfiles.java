package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.ServerSettings;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.*;

/** Fixed startup profiles, never per-player copies. Global masks are an immutable ceiling. */
public final class TerrainProfiles {
 private TerrainProfiles(){}
 public record Spec(String id,String projection,List<String> hidden,int minY,int maxY){
  public Spec(String id,String projection,List<String> hidden){this(id,projection,hidden,0,0);}
  public Spec{hidden=List.copyOf(hidden);if(projection.equals("cave")&&(minY< -2048||minY>2047||maxY< -2048||maxY>2047||(long)maxY-minY<3||(long)maxY-minY>=256))throw new IllegalArgumentException("Cave profile needs a 4–256 block band within -2048..2047");}
 }
 public static boolean privateName(String id){return id!=null&&id.matches("[a-z][a-z0-9_-]{0,31}")&&!Set.of("surface","isometric").contains(id);}
 public static String scope(String id){if(!privateName(id))throw new ApiException(ApiException.Code.INVALID_ARGUMENT,"Invalid terrain profile");return "map.tiles.profile."+id;}
 public static List<Spec> parse(List<?> values){
  if(values.size()>4)throw new IllegalArgumentException("api.terrain.profiles: at most four profiles");var out=new ArrayList<Spec>();var ids=new HashSet<String>();
  for(Object value:values){if(!(value instanceof UnmodifiableConfig c)||!(c.valueMap().keySet().equals(Set.of("id","projection","hidden"))||c.valueMap().keySet().equals(Set.of("id","projection","hidden","minY","maxY"))))throw new IllegalArgumentException("api.terrain.profiles: expected id, projection, hidden");
   Object id=c.get("id"),projection=c.get("projection"),hidden=c.get("hidden");
   if(!(id instanceof String name)||!privateName(name)||!ids.add(name)||!(projection instanceof String mode)||!Set.of("surface","isometric","cave").contains(mode)||!(hidden instanceof List<?> areas)||areas.stream().anyMatch(a->!(a instanceof String)))throw new IllegalArgumentException("Invalid terrain profile");
   if(mode.equals("cave")){Object low=c.get("minY"),high=c.get("maxY");if(!(low instanceof Integer||low instanceof Long)||!(high instanceof Integer||high instanceof Long))throw new IllegalArgumentException("Cave profile requires integer minY/maxY");out.add(new Spec(name,mode,areas.stream().map(String.class::cast).toList(),Math.toIntExact(((Number)low).longValue()),Math.toIntExact(((Number)high).longValue())));}
   else{if(c.contains("minY")||c.contains("maxY"))throw new IllegalArgumentException("Vertical band is cave-only");out.add(new Spec(name,mode,areas.stream().map(String.class::cast).toList()));}
  }return List.copyOf(out);
 }
 public static TerrainSettings masked(TerrainSettings base,List<String> extra){var masks=new ArrayList<>(base.hidden());masks.addAll(extra);return new TerrainSettings(base.enabled(),base.dimensions(),base.minX(),base.minZ(),base.maxX(),base.maxZ(),base.maxZoom(),base.maxTiles(),base.columnsPerTick(),base.budgetMicros(),base.radius(),masks);}
 public static List<Spec> from(ServerSettings settings){return settings.terrainProfiles();}
}
