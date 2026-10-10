package dev.abros.rivet.core;

import dev.abros.rivet.core.map.MapPositionPoll;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapPositionPollTest {
 @Test void initialWaveIsSpreadAcrossGameTicks(){
  int[] ticks=new int[20];
  for(int i=1;i<=100;i++){var id=new UUID(0,i);long delay=MapPositionPoll.initialDelay(id);assertTrue(delay>=0&&delay<1000);assertEquals(delay,MapPositionPoll.initialDelay(id));ticks[(int)delay/50]++;}
  assertTrue(Arrays.stream(ticks).max().orElseThrow()<20,"Polls cluster in one game tick");
  assertTrue(Arrays.stream(ticks).filter(n->n>0).count()>=16);
 }
 @Test void repeatedFailuresBackOffAndStayBounded(){
  var poll=new MapPositionPoll();var id=new UUID(0,17);
  for(int attempt=0;attempt<100;attempt++){long minimum=1000L<<Math.min(2,attempt);long delay=poll.failed(id);assertTrue(delay>=minimum&&delay<minimum+1000);}
 }
 @Test void successOrDisconnectResetsBackoff(){
  var poll=new MapPositionPoll();var id=new UUID(0,17);long first=poll.failed(id);poll.failed(id);poll.failed(id);poll.reset();assertEquals(first,poll.failed(id));
 }
}
