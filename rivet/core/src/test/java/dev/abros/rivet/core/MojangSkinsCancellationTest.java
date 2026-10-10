package dev.abros.rivet.core;

import dev.abros.rivet.core.skins.MojangSkins;
import java.io.InterruptedIOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MojangSkinsCancellationTest {
 @Test void cancelledLookupStopsBeforeStartingNetworkAndPreservesInterrupt(){
  Thread.currentThread().interrupt();try{
   assertThrows(InterruptedIOException.class,()->MojangSkins.find("Player",null));
   assertThrows(InterruptedIOException.class,()->MojangSkins.find("Player",UUID.randomUUID()));
   assertTrue(Thread.currentThread().isInterrupted());
  }finally{Thread.interrupted();}
 }
 @Test void nonCancelledInvalidNameStillReturnsDefaultWithoutNetwork()throws Exception{
  var result=MojangSkins.find("invalid name",null);assertNull(result.png());assertFalse(result.slim());
 }
}
