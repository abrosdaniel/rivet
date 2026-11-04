package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class UpdateSpaceTest {
 @TempDir Path game;
 @Test void includesDownloadsBackupsGrowthAndRollbackTemporary()throws Exception{
  Files.createDirectories(game.resolve("mods"));Files.write(game.resolve("mods/old.jar"),new byte[100]);
  var plan=new Planner.Plan(UUID.randomUUID().toString(),"pack",List.of(new Planner.Change("mods/old.jar","before","after"),new Planner.Change("mods/add.jar",null,"add")),Map.of(),Set.of(),List.of(),0);
  assertEquals(16L*1024*1024+350+100+250+200,UpdateSpace.required(game,plan,Map.of("after",200L,"add",150L),350));
  assertThrows(java.io.IOException.class,()->UpdateSpace.required(game,plan,Map.of("after",200L),0));
  assertThrows(java.io.IOException.class,()->UpdateSpace.required(game,plan,Map.of("after",Long.MAX_VALUE,"add",150L),0));
 }
}
