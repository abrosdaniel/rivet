package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapTile;
import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise source validation without initializing Minecraft or relying on a GC deadline. */
class MapTerrainSourceTest {
 private static Object source(WeakReference<MapTile> tile,long revision)throws Exception{
  var constructor=Class.forName("dev.abros.rivet.client.MapTerrainCache$Source").getDeclaredConstructors()[0];
  constructor.setAccessible(true);return constructor.newInstance(tile,revision,null,null,null,0,16,false,null);
 }
 private static boolean same(Object a,Object b)throws Exception{
  var method=a.getClass().getDeclaredMethod("same",a.getClass());method.setAccessible(true);return (boolean)method.invoke(a,b);
 }
 @Test void unchangedLiveSourceIsReusedButReloadedSampleIsNot()throws Exception{
  var tile=new MapTile();var before=source(new WeakReference<>(tile),0);
  assertTrue(same(before,source(new WeakReference<>(tile),0)));
  assertFalse(same(before,source(new WeakReference<>(tile),1)));
  assertFalse(same(before,source(new WeakReference<>(new MapTile()),0)));
 }
 @Test void collectedSourcesNeverCompareAsUnchanged()throws Exception{
  var tile=new MapTile();var ref=new WeakReference<>(tile);var before=source(ref,0);
  ref.clear();assertFalse(same(before,source(new WeakReference<>(tile),0)));
  assertFalse(same(before,source(new WeakReference<>(null),0)));assertFalse(same(before,null));
 }
}
