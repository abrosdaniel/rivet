package dev.abros.rivet.core;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class PerformanceMetricsTest {
 @BeforeEach void start(){PerformanceMetrics.clear();PerformanceMetrics.detailed(true);}
 @AfterEach void stop(){PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 @Test void rollingWindowIsBoundedAndQuantilesAreNearestRank(){for(int n=1;n<=3000;n++)PerformanceMetrics.record("test",n,false);var p=PerformanceMetrics.distributions().get("test");assertEquals(2048,p.samples());assertEquals(1976,p.p50Nanos());assertEquals(2898,p.p95Nanos());assertEquals(2980,p.p99Nanos());assertEquals(3000,PerformanceMetrics.snapshot().get("test").count());}
 @Test void disabledTimingDoesNotRecordOrAllocateWindows(){PerformanceMetrics.detailed(false);assertEquals(0,PerformanceMetrics.start());PerformanceMetrics.end("ignored",0);assertTrue(PerformanceMetrics.snapshot().isEmpty());PerformanceMetrics.record("existing",17,true);assertTrue(PerformanceMetrics.distributions().isEmpty());assertEquals(1,PerformanceMetrics.snapshot().get("existing").failures());}
 @Test void resetAndSnapshotsAreIndependent(){PerformanceMetrics.record("a",-1,false);var old=PerformanceMetrics.distributions();PerformanceMetrics.clear();assertEquals(0,old.get("a").p95Nanos());assertTrue(PerformanceMetrics.distributions().isEmpty());}
 @Test void concurrentWritersAndCardinalityStayBounded()throws Exception {var workers=new Thread[4];for(int i=0;i<4;i++){workers[i]=new Thread(()->{for(int n=0;n<1000;n++)PerformanceMetrics.record("shared",n,false);});workers[i].start();}for(var w:workers)w.join();assertEquals(4000,PerformanceMetrics.snapshot().get("shared").count());for(int n=0;n<100;n++)PerformanceMetrics.record("op"+n,1,false);assertEquals(64,PerformanceMetrics.snapshot().size());assertEquals(64,PerformanceMetrics.distributions().size());}
}
