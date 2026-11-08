package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapCacheBudgetTest {
 @Test void smallHeapsHaveSmallerBoundedCachesAndLargeHeapsCannotGrowWithoutLimit(){
  var small=MapCacheBudget.forHeap(1024L*1024*1024);
  var normal=MapCacheBudget.forHeap(4L*1024*1024*1024);
  assertTrue(small.tiles()<normal.tiles());assertTrue(small.overview()<normal.overview());
  assertEquals(normal,MapCacheBudget.forHeap(Long.MAX_VALUE));
  assertEquals(new MapCacheBudget(1024,16384),MapCacheBudget.forHeap(0));
  assertEquals(new MapCacheBudget(1024,16384),MapCacheBudget.forHeap(Long.MIN_VALUE));
 }
}
