package dev.abros.rivet.core.api;
import dev.abros.rivet.core.ServerSettings;
import java.util.*;

public record TerrainSettings(boolean enabled,List<String> dimensions,int minX,int minZ,int maxX,int maxZ,
 int maxZoom,int maxTiles,int columnsPerTick,int budgetMicros,int radius,List<String> hidden){
 public TerrainSettings{
  dimensions=List.copyOf(dimensions);hidden=List.copyOf(hidden);
  if(dimensions.isEmpty()||dimensions.size()>16||dimensions.stream().anyMatch(d->d.length()>128||!d.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||d.contains(".."))||minX>maxX||minZ>maxZ||Math.abs((long)minX)>30000000||Math.abs((long)maxX)>30000000||Math.abs((long)minZ)>30000000||Math.abs((long)maxZ)>30000000||maxZoom<0||maxZoom>6||maxTiles<64||maxTiles>65536||columnsPerTick<1||columnsPerTick>256||budgetMicros<100||budgetMicros>5000||radius<0||radius>16||hidden.size()>64)throw new IllegalArgumentException("Invalid terrain settings");
  for(String area:hidden)rectangle(area);
 }
 public static TerrainSettings from(ServerSettings s){String p="api.terrain.";return new TerrainSettings(s.flag(p+"enabled"),s.strings(p+"dimensions"),s.number(p+"minX"),s.number(p+"minZ"),s.number(p+"maxX"),s.number(p+"maxZ"),s.number(p+"maxZoom"),s.number(p+"maxTiles"),s.number(p+"columnsPerTick"),s.number(p+"budgetMicros"),s.number(p+"radius"),s.strings(p+"hidden"));}
 private static int[] rectangle(String area){String[] p=area.split(",",-1);if(p.length!=4)throw new IllegalArgumentException("Hidden rectangle needs minX,minZ,maxX,maxZ");int[] r=Arrays.stream(p).mapToInt(v->Integer.parseInt(v.strip())).toArray();if(r[0]>r[2]||r[1]>r[3])throw new IllegalArgumentException("Invalid hidden rectangle");return r;}
 public boolean visible(String dimension,int x,int z){if(!dimensions.contains(dimension)||x<minX||x>maxX||z<minZ||z>maxZ)return false;for(String h:hidden){var r=rectangle(h);if(x>=r[0]&&x<=r[2]&&z>=r[1]&&z<=r[3])return false;}return true;}
 public String profile(){return dimensions+":"+minX+":"+minZ+":"+maxX+":"+maxZ+":"+maxZoom+":"+hidden+":surface-v1";}
}
