package dev.abros.rivet.core.modules;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleRuntimeTest {
 private ModuleRuntime.Module<List<String>> module(String id,List<String> requires,boolean enabled){
  return new ModuleRuntime.Module<>(id,requires,c->enabled,c->c.add("start:"+id),c->c.add("stop:"+id));
 }
 @Test void dependenciesStartFirstAndStopLast() throws Exception {
  var calls=new ArrayList<String>();
  var runtime=new ModuleRuntime<>(List.of(module("chat",List.of("display"),true),module("display",List.of("base"),true),module("base",List.of(),true)));
  runtime.start(calls);assertEquals(Set.of("base","display","chat"),runtime.active());runtime.stop();runtime.stop();
  assertEquals(List.of("start:base","start:display","start:chat","stop:chat","stop:display","stop:base"),calls);
  assertTrue(runtime.active().isEmpty());runtime.start(calls);runtime.stop();assertEquals(12,calls.size());
 }
 @Test void unavailableDependenciesDoNotStartDependents() throws Exception {
  var calls=new ArrayList<String>();var runtime=new ModuleRuntime<>(List.of(module("map",List.of("adapter"),true),module("adapter",List.of(),false),module("chat",List.of(),true)));
  runtime.start(calls);runtime.stop();assertEquals(List.of("start:chat","stop:chat"),calls);
 }
 @Test void failureRollsBackPartiallyStartedModule() {
  var calls=new ArrayList<String>();
  var broken=new ModuleRuntime.Module<List<String>>("broken",List.of("base"),c->true,c->{c.add("partial");throw new IllegalStateException("startup");},c->c.add("rollback"));
  var runtime=new ModuleRuntime<>(List.of(module("base",List.of(),true),broken,module("later",List.of(),true)));
  var failure=assertThrows(IllegalStateException.class,()->runtime.start(calls));assertEquals("startup",failure.getMessage());
  assertEquals(List.of("start:base","partial","rollback","stop:base"),calls);assertTrue(runtime.active().isEmpty());
 }
 @Test void cleanupFailureDoesNotPreventOtherResourcesClosing() throws Exception {
  var calls=new ArrayList<String>();var broken=new ModuleRuntime.Module<List<String>>("broken",List.of("base"),c->true,c->{},c->{throw new IllegalArgumentException("cleanup");});
  var runtime=new ModuleRuntime<>(List.of(module("base",List.of(),true),broken));runtime.start(calls);
  assertThrows(IllegalArgumentException.class,runtime::stop);assertEquals(List.of("start:base","stop:base"),calls);assertTrue(runtime.active().isEmpty());runtime.stop();
 }
 @Test void rollbackKeepsOriginalFailure() {
  var original=new IllegalStateException("startup");
  var broken=new ModuleRuntime.Module<Object>("broken",List.of(),c->true,c->{throw original;},c->{throw new IllegalArgumentException("cleanup");});
  var runtime=new ModuleRuntime<>(List.of(broken));assertSame(original,assertThrows(IllegalStateException.class,()->runtime.start(new Object())));assertEquals("cleanup",original.getSuppressed()[0].getMessage());
 }
 @Test void availabilityFailureAlsoRollsBack() {
  var calls=new ArrayList<String>();var broken=new ModuleRuntime.Module<List<String>>("broken",List.of(),c->{throw new IllegalArgumentException("availability");},c->{},c->{});
  var runtime=new ModuleRuntime<>(List.of(module("base",List.of(),true),broken));assertThrows(IllegalArgumentException.class,()->runtime.start(calls));assertEquals(List.of("start:base","stop:base"),calls);
 }
 @Test void invalidGraphsFailBeforeStarting() {
  assertThrows(IllegalArgumentException.class,()->new ModuleRuntime<>(List.of(module("a",List.of(),true),module("a",List.of(),true))));
  assertThrows(IllegalArgumentException.class,()->new ModuleRuntime<>(List.of(module("a",List.of("missing"),true))));
  assertThrows(IllegalArgumentException.class,()->new ModuleRuntime<>(List.of(module("a",List.of("b"),true),module("b",List.of("a"),true))));
 }
 @Test void startingTwiceCannotDuplicateResources() throws Exception {
  var runtime=new ModuleRuntime<>(List.of(module("base",List.of(),true)));var calls=new ArrayList<String>();runtime.start(calls);
  assertThrows(IllegalStateException.class,()->runtime.start(calls));runtime.stop();assertEquals(List.of("start:base","stop:base"),calls);
 }
}
