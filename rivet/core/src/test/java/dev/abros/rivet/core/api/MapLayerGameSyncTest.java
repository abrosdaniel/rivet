package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.map.MapIntegrationState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapLayerGameSyncTest {
 private static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();
 private static MapLayersApi.ObjectSpec point(String id,MapLayersApi.Audience audience){return new MapLayersApi.ObjectSpec(id,new DomainData.Marker("minecraft:overworld","Point",1,64,2,0xff00ff00,"home"),audience);}
 @Test void filtersBeforeWireAndRemovesOnAudienceRevocation(){
  var registry=new MapLayerRegistry(true);var owner=registry.owner("test_mod");var layer=owner.register(new MapLayersApi.LayerSpec("stations","Stations",MapLayersApi.Audience.publicAccess()));
  long revision=layer.put(point("secret",MapLayersApi.Audience.restricted(Set.of(A))),0);
  var a=new MapLayerGameSync();var b=new MapLayerGameSync();var state=new MapIntegrationState();
  state.receive(a.next(registry,A,100),100);assertEquals(1,state.objects(100).size());assertFalse(b.next(registry,B,100).toString().contains("secret"));
  layer.put(point("secret",MapLayersApi.Audience.restricted(Set.of(B))),revision);
  var packet=a.next(registry,A,101);assertTrue(packet.get("reset").getAsBoolean());assertFalse(packet.toString().contains("secret"));state.receive(packet,101);assertTrue(state.objects(101).isEmpty());
  owner.close();state.receive(a.next(registry,A,102),102);assertTrue(state.layers(102).isEmpty());
 }
 @Test void incrementalChangesKeepExistingObjectsAndEmptyHeartbeatRenewsLease(){
  var registry=new MapLayerRegistry(true);var owner=registry.owner("test_mod");var layer=owner.register(new MapLayersApi.LayerSpec("stations","Stations",MapLayersApi.Audience.publicAccess()));
  layer.put(point("one",MapLayersApi.Audience.publicAccess()),0);var sync=new MapLayerGameSync();var state=new MapIntegrationState();state.receive(sync.next(registry,A,100),100);
  layer.put(point("two",MapLayersApi.Audience.publicAccess()),0);var packet=sync.next(registry,A,101);assertFalse(packet.get("reset").getAsBoolean());state.receive(packet,101);assertEquals(2,state.objects(101).size());
  assertNull(sync.next(registry,A,102));var heartbeat=sync.next(registry,A,4000);assertEquals(0,heartbeat.getAsJsonArray("rows").size());state.receive(heartbeat,4000);assertTrue(state.fresh(15000));assertFalse(state.fresh(16000));
 }
 @Test void gapClearsPrivateDataAndFreshConnectionCanRecover(){
  var registry=new MapLayerRegistry(true);registry.owner("test_mod").register(new MapLayersApi.LayerSpec("stations","Stations",MapLayersApi.Audience.publicAccess()));
  var sync=new MapLayerGameSync();var state=new MapIntegrationState();state.receive(sync.next(registry,A,100),100);sync.next(registry,A,4000);
  assertThrows(IllegalArgumentException.class,()->state.receive(sync.next(registry,A,8000),8000));assertTrue(state.layers(8000).isEmpty());state.receive(new MapLayerGameSync().next(registry,A,9000),9000);assertEquals(1,state.layers(9000).size());
 }
 @Test void pagesFitGamePacketAndCompleteWithoutRepeatedSnapshots(){
  var registry=new MapLayerRegistry(true);var layer=registry.owner("test_mod").register(new MapLayersApi.LayerSpec("stations","Stations",MapLayersApi.Audience.publicAccess()));
  for(int i=0;i<1024;i++)layer.put(point("p"+i,MapLayersApi.Audience.publicAccess()),0);
  var sync=new MapLayerGameSync();var state=new MapIntegrationState();int packets=0;
  for(long now=1;now<1000;now++){var packet=sync.next(registry,A,now);if(packet==null)break;assertTrue(packet.toString().length()<32767);assertTrue(packet.getAsJsonArray("rows").size()<=8);state.receive(packet,now);packets++;}
  assertEquals(129,packets);assertEquals(1024,state.objects(1000).size());
 }
 @Test void replacingLayerUpdatesItsTitleAndStaleObjectsDisappear(){
  var registry=new MapLayerRegistry(true);var owner=registry.owner("test_mod");var layer=owner.register(new MapLayersApi.LayerSpec("stations","Old",MapLayersApi.Audience.publicAccess()));layer.put(point("old",MapLayersApi.Audience.publicAccess()),0);
  var sync=new MapLayerGameSync();var state=new MapIntegrationState();state.receive(sync.next(registry,A,1),1);layer.close();owner.register(new MapLayersApi.LayerSpec("stations","New",MapLayersApi.Audience.publicAccess()));state.receive(sync.next(registry,A,2),2);assertEquals("New",state.layers(2).getFirst().title());assertTrue(state.objects(2).isEmpty());
 }
}
