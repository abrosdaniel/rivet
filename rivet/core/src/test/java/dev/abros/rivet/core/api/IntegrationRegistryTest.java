package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationRegistryTest {
 private static final IntegrationApi.Waystones STONES=new IntegrationApi.Waystones(){
  public boolean available(){return true;}
  public List<DomainData.Waystone> points(UUID id){return List.of(new DomainData.Waystone(id,"private","minecraft:overworld",1,2,3));}
 };
 @Test void ownerCannotOverwriteOrRemoveAnotherProvider()throws Exception{
  var registry=new IntegrationRegistry();var a=registry.owner("waystones");var b=registry.owner("another");
  var lease=a.register(IntegrationApi.WAYSTONES,"waystones",STONES);
  assertThrows(IllegalArgumentException.class,()->b.register(IntegrationApi.WAYSTONES,"waystones",STONES));
  assertThrows(IllegalStateException.class,()->registry.owner("waystones").register(IntegrationApi.WAYSTONES,"waystones",STONES));
  b.close();assertTrue(a.find(IntegrationApi.WAYSTONES,"waystones").isPresent());
  lease.close();a.register(IntegrationApi.WAYSTONES,"waystones",STONES);lease.close();
  assertTrue(a.find(IntegrationApi.WAYSTONES,"waystones").isPresent());
 }
 @Test void retainedProviderFailsAfterUnregisterAndServerRestart()throws Exception{
  var registry=new IntegrationRegistry();var owner=registry.owner("waystones");var reader=registry.owner("rivet");
  var registration=owner.register(IntegrationApi.WAYSTONES,"waystones",STONES);var provider=reader.find(IntegrationApi.WAYSTONES,"waystones").orElseThrow();
  UUID player=UUID.randomUUID();assertEquals(player,provider.points(player).getFirst().uuid());
  registration.close();assertThrows(ApiException.class,provider::available);
  owner.register(IntegrationApi.WAYSTONES,"waystones",STONES);assertThrows(ApiException.class,provider::available);
  var replacement=reader.find(IntegrationApi.WAYSTONES,"waystones").orElseThrow();registry.close();
  assertThrows(ApiException.class,replacement::available);assertThrows(ApiException.class,()->reader.find(IntegrationApi.WAYSTONES,"waystones"));
  assertTrue(new IntegrationRegistry().owner("rivet").find(IntegrationApi.WAYSTONES,"waystones").isEmpty());
 }
 @Test void closingReaderDoesNotRemoveProvider(){var registry=new IntegrationRegistry();var owner=registry.owner("waystones");owner.register(IntegrationApi.WAYSTONES,"waystones",STONES);var reader=registry.owner("rivet");var provider=reader.find(IntegrationApi.WAYSTONES,"waystones").orElseThrow();reader.close();assertThrows(ApiException.class,provider::available);assertTrue(owner.find(IntegrationApi.WAYSTONES,"waystones").isPresent());}
 @Test void providerFailureDoesNotDisableOtherServices()throws Exception{
  var registry=new IntegrationRegistry();var broken=registry.owner("broken");
  broken.register(IntegrationApi.VOICE,"broken",new IntegrationApi.Voice(){public boolean available(){return true;}public void checkMute(UUID id)throws Exception{throw new java.io.IOException("backend failed");}public void mute(UUID id,int minutes,String reason){throw new IllegalArgumentException("existing mute");}});
  var healthy=registry.owner("waystones");healthy.register(IntegrationApi.WAYSTONES,"waystones",STONES);
  var voice=healthy.find(IntegrationApi.VOICE,"broken").orElseThrow();
  assertThrows(java.io.IOException.class,()->voice.checkMute(UUID.randomUUID()));
  assertThrows(IllegalArgumentException.class,()->voice.mute(UUID.randomUUID(),1,"test"));
  assertTrue(healthy.find(IntegrationApi.WAYSTONES,"waystones").orElseThrow().available());
 }
 @Test void dataCopiesNestedCollectionsAndRejectsMutableObjects(){var nested=new ArrayList<Object>();nested.add("before");var input=new HashMap<String,Object>();input.put("nested",nested);var data=new IntegrationApi.Data(input);nested.add("after");input.clear();assertEquals(List.of("before"),data.fields().get("nested"));assertThrows(UnsupportedOperationException.class,()->data.fields().clear());assertThrows(IllegalArgumentException.class,()->new IntegrationApi.Data(Map.of("bad",new Object())));assertThrows(IllegalArgumentException.class,()->new IntegrationApi.Data(Map.of("bad",Double.NaN)));}
}
