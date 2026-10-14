package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapTerritoryIndexTest {
 MapTerritory square(int i,int x,int z,int size){return new MapTerritory(new UUID(0,i),i%2==0?"minecraft:overworld":"minecraft:the_end","Area",-1,List.of(new MapTerritory.Point(x,z),new MapTerritory.Point(x+size,z),new MapTerritory.Point(x+size,z+size),new MapTerritory.Point(x,z+size)));}
 @Test void matchesFullScanAndKeepsDrawingOrder(){var r=new Random(42);var rows=new ArrayList<MapTerritory>();for(int i=0;i<8192;i++)rows.add(square(i,r.nextInt(100000)-50000,r.nextInt(100000)-50000,100));rows.add(square(8192,-100000,-100000,200000));var index=new MapTerritoryIndex(rows);
  for(int n=0;n<200;n++){int x=r.nextInt(100000)-50000,z=r.nextInt(100000)-50000;var expected=rows.stream().filter(t->t.dimension().equals("minecraft:overworld")&&t.points().get(2).x()>=x&&t.points().get(2).z()>=z&&t.points().get(0).x()<=x+200&&t.points().get(0).z()<=z+200).toList();assertEquals(expected,index.visible("minecraft:overworld",x,z,x+200,z+200));}
  assertTrue(index.visible("minecraft:missing",0,0,1,1).isEmpty());
 }
 @Test void boundaryAndNegativeCoordinates(){var t=square(0,-30000000,-30000000,10);var i=new MapTerritoryIndex(List.of(t));assertEquals(List.of(t),i.visible(t.dimension(),-29999990,-29999990,-29999980,-29999980));assertTrue(i.visible(t.dimension(),0,0,1,1).isEmpty());}
}
