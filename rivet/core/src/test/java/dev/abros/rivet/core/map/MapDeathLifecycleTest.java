package dev.abros.rivet.core.map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapDeathLifecycleTest {
 private final long at=1000000;
 private MapMarker death(){return new MapMarker(UUID.randomUUID(),"minecraft:overworld","Death",10,64,20,0xffffff,"skull",at);}
 @Test void expiresAtTenMinutesIncludingOfflineTime(){var m=death();assertFalse(MapDeathLifecycle.expired(m,at+599999));assertTrue(MapDeathLifecycle.expired(m,at+600000));assertTrue(MapDeathLifecycle.expired(m,at+86400000));assertFalse(MapDeathLifecycle.expired(m,at-1));}
 @Test void onlyLivingArrivalInSameDimensionRemoves(){var m=death();assertTrue(MapDeathLifecycle.remove(m,at+1,m.dimension(),10.5,64,20.5,true));assertFalse(MapDeathLifecycle.remove(m,at+1,m.dimension(),10.5,64,20.5,false));assertFalse(MapDeathLifecycle.remove(m,at+1,"minecraft:the_nether",10.5,64,20.5,true));assertFalse(MapDeathLifecycle.remove(m,at+1,m.dimension(),10.5,69,20.5,true));assertFalse(MapDeathLifecycle.remove(m,at+1,m.dimension(),14,64,20.5,true));}
 @Test void countdownUsesExpiryTimeWithoutResetting(){var m=death();assertEquals("10:00",MapDeathLifecycle.countdown(m,at));assertEquals("9:59",MapDeathLifecycle.countdown(m,at+1000));assertEquals("0:01",MapDeathLifecycle.countdown(m,at+599999));assertEquals("0:00",MapDeathLifecycle.countdown(m,at+600000));assertEquals("0:00",MapDeathLifecycle.countdown(m,at+86400000));}
 @Test void deathColorIsFixedOnCreationAndReload(){assertEquals(0xff606876,death().color());}
 @Test void skullIsReservedForDeaths(){var normal=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Home",0,64,0,0xffffff,"skull");assertEquals("pin",normal.icon());var d=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Death",0,64,0,0xffffff,"home",at);assertEquals("skull",d.icon());}
 @Test void ordinaryMarkersNeverExpireOrDisappearOnArrival(){var m=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Home",10,64,20,0xffffff,"house");assertFalse(MapDeathLifecycle.remove(m,Long.MAX_VALUE,m.dimension(),10.5,64,20.5,true));}
}
