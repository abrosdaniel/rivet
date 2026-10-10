package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class CoalescingTasksTest {
 @Test void slowDiskKeepsOnlyLatestAndClearWins(){var jobs=new ArrayList<Runnable>();var saved=new ArrayList<Integer>();var q=new CoalescingTasks<String>(jobs::add,2,e->{throw e;});for(int i=0;i<10000;i++){int value=i;q.submit("form",()->saved.add(value));}assertEquals(1,jobs.size());q.submit("form",()->saved.add(-1));jobs.removeFirst().run();assertEquals(List.of(-1),saved);}
 @Test void writesDuringExecutionSurviveAndKeysAreBounded(){var jobs=new ArrayList<Runnable>();var saved=new ArrayList<String>();var q=new CoalescingTasks<String>(jobs::add,2,e->{throw e;});q.submit("a",()->{saved.add("a");q.submit("a",()->saved.add("new"));});q.submit("b",()->saved.add("b"));assertThrows(RejectedExecutionException.class,()->q.submit("c",()->{}));jobs.removeFirst().run();assertEquals(List.of("a","b","new"),saved);}
}
