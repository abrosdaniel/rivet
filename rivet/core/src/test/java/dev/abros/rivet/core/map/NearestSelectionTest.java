package dev.abros.rivet.core.map;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NearestSelectionTest {
 record Point(int index,double x,double z){}
 @Test void matchesStableFullSortForRealisticSizesAndMovingCenters(){var random=new Random(1847);for(int count:new int[]{0,1,63,64,65,100,1000,4096}){var data=new ArrayList<Point>();for(int i=0;i<count;i++)data.add(new Point(i,random.nextInt(1000)-500,random.nextInt(1000)-500));for(double center:new double[]{0,.125,9.99,-10000}){java.util.function.Predicate<Point> visible=p->p.index()%7!=0;java.util.function.ToDoubleFunction<Point> distance=p->Math.hypot(p.x()-center,p.z()+center);var expected=data.stream().filter(visible).sorted(Comparator.comparingDouble(distance)).limit(64).toList();assertEquals(expected,NearestSelection.select(data,64,visible,distance));}}}
 @Test void tiesKeepOriginalOrderAcrossHeapReplacement(){var points=new ArrayList<Point>();for(int i=0;i<200;i++)points.add(new Point(i,i<40?2:1,0));var expected=points.stream().sorted(Comparator.comparingDouble(Point::x)).limit(64).toList();assertEquals(expected,NearestSelection.select(points,64,p->true,Point::x));}
 @Test void distanceIsCalculatedOncePerEligibleValue(){var count=new AtomicInteger();var data=java.util.stream.IntStream.range(0,4096).boxed().toList();var result=NearestSelection.select(data,64,i->i%2==0,i->{count.incrementAndGet();return Math.abs(2000-i);});assertEquals(2048,count.get());assertEquals(64,result.size());assertEquals(2000,result.getFirst());}
 @Test void zeroLimitAndEmptySelectionAvoidDistanceWork(){assertEquals(List.of(),NearestSelection.select(List.of(1),0,i->true,i->{throw new AssertionError();}));assertEquals(List.of(),NearestSelection.select(List.of(1),64,i->false,i->{throw new AssertionError();}));assertThrows(IllegalArgumentException.class,()->NearestSelection.select(List.of(1),-1,i->true,i->i));}
 @Test void doubleOrderingMatchesSortIncludingNonFiniteScores(){var data=List.of(Double.NaN,Double.POSITIVE_INFINITY,0d,-0d,Double.NEGATIVE_INFINITY,1d);assertEquals(data.stream().sorted().limit(4).toList(),NearestSelection.select(data,4,i->true,i->i));}
}
