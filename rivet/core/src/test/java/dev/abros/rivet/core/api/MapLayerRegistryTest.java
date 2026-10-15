package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.abros.rivet.api.MapLayersApi.*;
import static dev.abros.rivet.api.ApiException.Code.*;

class MapLayerRegistryTest {
 private static final Audience PUBLIC=Audience.publicAccess();
 private static ObjectSpec point(String id,Audience audience){return new ObjectSpec(id,new DomainData.Marker("minecraft:overworld","Base",1,64,2,0,"flag"),audience);}
 private static LayerSpec spec(String id,Audience audience){return new LayerSpec(id,"Layer",audience);}
 @Test void separateNamespacesAndLeaseCleanupNeverTouchForeignOrReplacementLayers(){
  var registry=new MapLayerRegistry(true);var first=registry.owner("first_mod");var second=registry.owner("second_mod");var a=first.register(spec("town",PUBLIC));var b=second.register(spec("town",PUBLIC));a.put(point("home",PUBLIC),0);b.put(point("home",PUBLIC),0);
  assertEquals(2,registry.layers(null,"",OptionalLong.empty()).items().size());assertThrows(ApiException.class,()->a.remove("second_mod:home",1));
  first.close();assertEquals("second_mod:town",registry.layers(null,"",OptionalLong.empty()).items().getFirst().id());assertThrows(ApiException.class,()->a.put(point("other",PUBLIC),0));
  var replacement=registry.owner("first_mod").register(spec("town",PUBLIC));a.close();first.close();replacement.put(point("home",PUBLIC),0);assertEquals(2,registry.layers(null,"",OptionalLong.empty()).items().size());
 }
 @Test void layerAndObjectAudiencesIntersectWithoutPublishingRecipientLists(){
  var user=UUID.randomUUID();var stranger=UUID.randomUUID();var registry=new MapLayerRegistry(true);var owner=registry.owner("example_mod");var privateLayer=owner.register(spec("private",Audience.restricted(Set.of(user))));privateLayer.put(point("public_child",PUBLIC),0);
  assertTrue(registry.layers(null,"",OptionalLong.empty()).items().isEmpty());assertEquals(NOT_FOUND,assertThrows(ApiException.class,()->registry.objects(stranger,"example_mod:private","",OptionalLong.empty())).code());
  assertEquals(1,registry.objects(user,"example_mod:private","",OptionalLong.empty()).items().size());var publicLayer=owner.register(spec("public",PUBLIC));publicLayer.put(point("hidden",Audience.restricted(Set.of(user))),0);assertTrue(registry.objects(null,"example_mod:public","",OptionalLong.empty()).items().isEmpty());
  assertEquals(1,registry.objects(user,"example_mod:public","",OptionalLong.empty()).items().size());
 }
 @Test void objectRevisionsRejectLostUpdatesAndDeleteRecreateAba(){
  var registry=new MapLayerRegistry(true);var layer=registry.owner("example_mod").register(spec("test",PUBLIC));long v=layer.put(point("home",PUBLIC),0);long updated=layer.put(point("home",Audience.restricted(Set.of())),v);
  assertEquals(CONFLICT,assertThrows(ApiException.class,()->layer.put(point("home",PUBLIC),v)).code());assertEquals(CONFLICT,assertThrows(ApiException.class,()->layer.remove("home",v)).code());layer.remove("home",updated);long recreated=layer.put(point("home",PUBLIC),0);assertTrue(recreated>updated);assertThrows(ApiException.class,()->layer.remove("home",updated));
 }
 @Test void disablingAndStoppingInvalidateAllOldHandlesAndClearObjects(){
  var registry=new MapLayerRegistry(true);var owner=registry.owner("example_mod");var layer=owner.register(spec("test",PUBLIC));layer.put(point("home",PUBLIC),0);registry.enabled(false);assertEquals(MODULE_DISABLED,assertThrows(ApiException.class,()->layer.put(point("home",PUBLIC),0)).code());registry.enabled(true);assertTrue(registry.layers(null,"",OptionalLong.empty()).items().isEmpty());assertThrows(ApiException.class,()->owner.register(spec("test",PUBLIC)));var fresh=registry.owner("example_mod").register(spec("test",PUBLIC));layer.close();fresh.put(point("home",PUBLIC),0);registry.close();assertEquals(UNAVAILABLE,assertThrows(ApiException.class,()->fresh.put(point("home",PUBLIC),0)).code());registry.close();
 }
 @Test void paginationUsesVisibleIdsAndRejectsChangedSnapshots(){
  var registry=new MapLayerRegistry(true);var layer=registry.owner("example_mod").register(spec("test",PUBLIC));for(int i=0;i<9;i++)layer.put(point(String.format("p%03d",i),PUBLIC),0);layer.put(point("secret",Audience.restricted(Set.of())),0);
  var page=registry.objects(null,layer.id(),"",OptionalLong.empty());assertEquals(8,page.items().size());assertEquals("p007",page.nextCursor());assertEquals(1,registry.objects(null,layer.id(),page.nextCursor(),OptionalLong.of(page.snapshot())).items().size());assertThrows(UnsupportedOperationException.class,()->page.items().clear());layer.put(point("last",PUBLIC),0);assertEquals(CONFLICT,assertThrows(ApiException.class,()->registry.objects(null,layer.id(),page.nextCursor(),OptionalLong.of(page.snapshot()))).code());
 }
 @Test void registrationAndGeometryBudgetsAreBounded(){
  var registry=new MapLayerRegistry(true);var owner=registry.owner("example_mod");var layer=owner.register(spec("test",PUBLIC));assertEquals(CONFLICT,assertThrows(ApiException.class,()->registry.owner("example_mod").register(spec("test",PUBLIC))).code());assertThrows(ApiException.class,()->owner.register(spec("../../foreign",PUBLIC)));
  var invalid=new ObjectSpec("bad",new DomainData.Marker("minecraft:overworld","bad",30000001,64,0,0,"flag"),PUBLIC);assertEquals(INVALID_ARGUMENT,assertThrows(ApiException.class,()->layer.put(invalid,0)).code());
  var polygon=new ObjectSpec("polygon",new DomainData.Territory("minecraft:overworld","Area",1,List.of(new DomainData.Point(0,0),new DomainData.Point(10,0),new DomainData.Point(0,10))),PUBLIC);layer.put(polygon,0);
  for(int i=0;i<15;i++)owner.register(spec("layer"+i,PUBLIC));assertEquals(BUSY,assertThrows(ApiException.class,()->owner.register(spec("overflow",PUBLIC))).code());owner.close();registry.owner("example_mod").register(spec("again",PUBLIC));
 }
}
