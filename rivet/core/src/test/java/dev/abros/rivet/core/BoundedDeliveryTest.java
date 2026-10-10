package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class BoundedDeliveryTest {
 @Test void timedOutDeliveryCannotRunAfterLeaseIsReleased(){var jobs=new ArrayList<Runnable>();var delivered=new AtomicBoolean();assertThrows(TimeoutException.class,()->BoundedDelivery.run(jobs::add,()->delivered.set(true),10));jobs.getFirst().run();assertFalse(delivered.get());}
 @Test void successfulDeliveryCompletesBeforeReturning()throws Exception{var delivered=new AtomicBoolean();BoundedDelivery.run(Runnable::run,()->delivered.set(true),1000);assertTrue(delivered.get());}
}
