package dev.abros.rivet.core.map;
import dev.abros.rivet.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapCategoryTest{
 @TempDir Path root;
 @Test void categoryRenameKeepsMarkerIdentityAndVisibilitySurvivesRestart()throws Exception{
  var world=UUID.randomUUID();var player=UUID.randomUUID();var repo=new MapRepository(root,world,player);var c=new MapCategory(UUID.randomUUID(),"Дом",false,true);var m=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Дача",-123,64,45,0,"none",0,c.id().toString(),true,false);repo.categories(List.of(c));repo.markers(List.of(m));var renamed=new MapCategory(c.id(),"Постройки",true,true);repo.categories(List.of(renamed));var reopened=new MapRepository(root,world,player);assertEquals(List.of(renamed),reopened.categories());assertEquals(List.of(m),reopened.markers());assertTrue(renamed.shows(m,false));assertFalse(renamed.shows(m,true));repo.categories(List.of());assertEquals(List.of(m),repo.markers());assertTrue(repo.categories().isEmpty());assertTrue(new MapRepository(root,world,UUID.randomUUID()).categories().isEmpty());
 }
 @Test void oldMarkerFilesDefaultToVisibleAndUncategorized()throws Exception{
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var m=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Дом",1,64,2,0,"none");repo.markers(List.of(m));Path file;try(var files=Files.walk(root)){file=files.filter(p->p.getFileName().toString().equals("markers.json")).findFirst().orElseThrow();}var j=Json.read(file);var row=j.getAsJsonArray("markers").get(0).getAsJsonObject();for(String field:List.of("category","mapVisible","minimapVisible"))row.remove(field);Json.write(file,j);assertEquals(m,repo.markers().getFirst());assertTrue(repo.categories().isEmpty());
 }
 @Test void duplicatesAndInvalidCategoryNamesDoNotReplaceSavedCategories()throws Exception{
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var c=new MapCategory(UUID.randomUUID(),"Дом",true,true);repo.categories(List.of(c));assertThrows(java.io.IOException.class,()->repo.categories(List.of(c,new MapCategory(UUID.randomUUID(),"дом",true,true))));assertEquals(List.of(c),repo.categories());assertThrows(IllegalArgumentException.class,()->new MapCategory(UUID.randomUUID(),"\n",true,true));assertThrows(IllegalArgumentException.class,()->new MapCategory(UUID.randomUUID(),"a".repeat(41),true,true));
 }
 @Test void categoryAndMarkerVisibilityBothApply(){var c=new MapCategory(UUID.randomUUID(),"Дом",false,true);var m=new MapMarker(UUID.randomUUID(),"minecraft:overworld","x",0,0,0,0,"none",0,c.id().toString(),true,true);assertFalse(c.shows(m,false));assertTrue(c.shows(m,true));}
}
