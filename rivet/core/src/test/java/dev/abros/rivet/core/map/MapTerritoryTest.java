package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapTerritoryTest {
 private static List<MapTerritory.Point> points(int... v){var result=new ArrayList<MapTerritory.Point>();for(int i=0;i<v.length;i+=2)result.add(new MapTerritory.Point(v[i],v[i+1]));return result;}
 private static MapTerritory polygon(int... v){return new MapTerritory(UUID.randomUUID(),"minecraft:overworld","Дом",0xffe3be70,points(v));}
 @Test void concaveBoundaryAndReversedWinding(){var p=polygon(0,0,10,0,10,4,4,4,4,10,0,10);assertTrue(p.contains(2,8));assertTrue(p.contains(8,2));assertTrue(p.contains(4,7));assertFalse(p.contains(8,8));assertFalse(p.contains(-.01,0));var reverse=new MapTerritory(p.id(),p.dimension(),p.name(),p.color(),p.points().reversed());for(double x=-1;x<=11;x+=.5)for(double z=-1;z<=11;z+=.5)assertEquals(p.contains(x,z),reverse.contains(x,z));}
 @Test void invalidPolygonsRejected(){for(int[] shape:new int[][]{{0,0,1,1},{0,0,1,1,2,2},{0,0,10,10,0,10,10,0},{0,0,10,0,5,0,5,10},{0,0,10,0,10,10,0,0},{0,0,10,0,10,10,5,0,0,10}})assertThrows(IllegalArgumentException.class,()->polygon(shape));}
 @Test void worldBoundaryDoesNotOverflow(){var p=polygon(-30000000,-30000000,30000000,-30000000,30000000,30000000,-30000000,30000000);assertTrue(p.contains(0,0));assertTrue(p.contains(30000000,0));assertFalse(p.contains(30000001,0));assertThrows(IllegalArgumentException.class,()->new MapTerritory.Point(Integer.MIN_VALUE,0));}
 @Test void immutablePointsAndNames(){var v=points(0,0,10,0,0,10);var p=new MapTerritory(UUID.randomUUID(),"minecraft:overworld"," Дом ",0,v);v.clear();assertEquals(3,p.points().size());assertEquals("Дом",p.name());assertEquals(0xff000000,p.color());assertThrows(UnsupportedOperationException.class,()->p.points().clear());assertThrows(IllegalArgumentException.class,()->new MapTerritory(p.id(),p.dimension(),"\t",0,p.points()));}
 @Test void serverGeometryCodecRejectsFractionalCoordinatesAndUsesGroupName(){var polygon=polygon(0,0,10,0,0,10);var j=MapTerritoryJson.write(polygon);j.remove("name");assertEquals("Г".repeat(100),MapTerritoryJson.read(j,polygon.id(),"Г".repeat(100)).name());j.getAsJsonArray("points").get(0).getAsJsonObject().addProperty("x",.5);assertThrows(RuntimeException.class,()->MapTerritoryJson.read(j,polygon.id(),"Group"));}

}
