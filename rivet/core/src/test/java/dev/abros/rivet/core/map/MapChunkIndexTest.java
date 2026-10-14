package dev.abros.rivet.core.map;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapChunkIndexTest {
 @Test void sparseQueriesMatchExploredSetIncludingNegativeAndExtremeCoordinates(){
  var random=new Random(20261013);var chunks=new HashSet<MapRepository.Chunk>();for(int i=0;i<20000;i++)chunks.add(new MapRepository.Chunk(random.nextInt(2000)-1000,random.nextInt(2000)-1000));chunks.add(new MapRepository.Chunk(Integer.MIN_VALUE,Integer.MAX_VALUE));var index=new MapChunkIndex();index.addAll(chunks);index.addAll(chunks);
  for(int n=0;n<100;n++){int x=random.nextInt(2000)-1000,z=random.nextInt(2000)-1000;var expected=new HashSet<MapRepository.Chunk>();for(var c:chunks)if(c.x()>=x&&c.x()<=x+80&&c.z()>=z&&c.z()<=z+90)expected.add(c);assertEquals(expected,new HashSet<>(index.within(x,x+80,z,z+90)));}
  assertEquals(chunks,new HashSet<>(index.within(Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE)));assertTrue(index.within(1,0,0,1).isEmpty());
 }
}
