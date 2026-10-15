package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RestartCountdownTest {
 @Test void countsSecondsUsingMonotonicTimeAndIgnoresClientWallClock(){var timer=new RestartCountdown();timer.update(1_060_000,1_000_000,500);assertEquals(60,timer.seconds(500));assertEquals(59,timer.seconds(1500));assertEquals(1,timer.seconds(60499));assertEquals(0,timer.seconds(60500));}
 @Test void cancellationReschedulingAndDisconnect(){var timer=new RestartCountdown();assertEquals(-1,timer.seconds(0));timer.update(20000,10000,0);timer.update(40000,11000,1000);assertEquals(29,timer.seconds(1000));timer.update(0,12000,2000);assertEquals(-1,timer.seconds(2000));timer.update(20000,10000,0);timer.clear();assertEquals(-1,timer.seconds(0));}
 @Test void lateJoinAndExpiredDeadline(){var timer=new RestartCountdown();timer.update(20000,19000,100);assertEquals(1,timer.seconds(100));timer.update(20000,21000,200);assertEquals(0,timer.seconds(200));}
}
