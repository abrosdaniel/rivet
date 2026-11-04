package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StockScanQueueTest {
 @Test void unloadedLocationsDoNotDelayLoadedStocks(){var q=new StockScanQueue();for(int i=0;i<4096;i++)q.active("s"+i,i<8);var visited=new HashSet<String>();for(int i=0;i<8;i++)visited.add(q.poll());assertEquals(8,visited.size());assertEquals(8,q.size());q.active("s0",false);for(int i=0;i<20;i++)assertNotEquals("s0",q.poll());}
 @Test void changesArePromptButNeverStarveFallback(){var q=new StockScanQueue();for(int i=0;i<100;i++)q.active("s"+i,true);q.changed("s99");assertEquals("s99",q.poll());var seen=new HashSet<String>();for(int i=0;i<400;i++){q.changed("s99");seen.add(q.poll());}assertEquals(100,seen.size());q.clear();assertNull(q.poll());}
}
