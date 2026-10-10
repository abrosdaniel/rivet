package dev.abros.rivet.core.map;

import dev.abros.rivet.core.ServerSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapRepositoryTest {
    @TempDir Path root;
    @Test void saveIdentitySurvivesRestartAndMoveButNewWorldIsSeparate()throws Exception {
        Path old=root.resolve("world"),moved=root.resolve("moved");UUID first=MapRepository.worldId(old);
        assertEquals(first,MapRepository.worldId(old));Files.move(old,moved);assertEquals(first,MapRepository.worldId(moved));
        assertNotEquals(first,MapRepository.worldId(root.resolve("new")));
    }
    @Test void chunksAndUnknownPixelsSurviveReloadWithNegativeCoordinates()throws Exception {
        UUID world=UUID.randomUUID(),player=UUID.randomUUID();var repo=new MapRepository(root,world,player);var tile=new MapTile();
        tile.set(15,0,0xff123456,-24);repo.write("minecraft:overworld",-1,-2,tile);
        var restored=new MapRepository(root,world,player).read("minecraft:overworld",-1,-2).orElseThrow();
        assertEquals(0xff123456,restored.color(15,0));assertEquals(-24,restored.height(15,0));assertEquals(0,restored.color(14,0));
        assertEquals(Set.of(new MapRepository.Chunk(-1,-2)),repo.chunks("minecraft:overworld"));
        assertTrue(repo.read("minecraft:the_nether",-1,-2).isEmpty());
        assertTrue(new MapRepository(root,world,UUID.randomUUID()).read("minecraft:overworld",-1,-2).isEmpty());
        assertTrue(new MapRepository(root,UUID.randomUUID(),player).read("minecraft:overworld",-1,-2).isEmpty());
    }
    @Test void tileSnapshotCannotBeModifiedThroughCallerArrays() {
        var tile=new MapTile();tile.set(0,0,0xff112233,80);var snapshot=tile.copy();tile.set(0,0,0xff332211,50);snapshot.colors()[0]=0;
        assertEquals(0xff112233,snapshot.color(0,0));assertEquals(80,snapshot.height(0,0));
    }
    @Test void markersRoundTripReplaceAndDeleteWithoutTouchingTerrain()throws Exception {
        var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var marker=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Дом",-1,64,32,0x123456,"home");
        repo.markers(List.of(marker));assertEquals(List.of(marker),repo.markers());assertEquals(0xff123456,marker.color());
        repo.write("minecraft:overworld",0,0,new MapTile());repo.markers(List.of());assertTrue(repo.markers().isEmpty());assertTrue(repo.read("minecraft:overworld",0,0).isPresent());
        assertThrows(java.io.IOException.class,()->repo.markers(List.of(marker,marker)));
    }
    @Test void corruptTileIsReportedInsteadOfReplacingExistingFile()throws Exception {
        var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write("minecraft:overworld",0,0,new MapTile());
        Path file;try(var files=Files.walk(root)){file=files.filter(p->p.toString().endsWith(".tile")).findFirst().orElseThrow();}
        Files.write(file,new byte[]{1,2,3});assertThrows(java.io.IOException.class,()->repo.read("minecraft:overworld",0,0));assertEquals(3,Files.size(file));
    }
    @Test void invalidIdentifiersAndMarkersDoNotEscapeTheirStorage()throws Exception {
        var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());assertThrows(IllegalArgumentException.class,()->repo.read("../../escape",0,0));
        assertThrows(IllegalArgumentException.class,()->new MapMarker(UUID.randomUUID(),"minecraft:overworld","\n",0,64,0,0,"pin"));
        assertThrows(IllegalArgumentException.class,()->new MapMarker(UUID.randomUUID(),"minecraft:overworld","x",Integer.MIN_VALUE,64,0,0,"pin"));
    }
    @ParameterizedTest @ValueSource(strings={"\n","\r\n"})
    void serverConfigUpgradeAddsCommentedSwitchPreservesValuesAndBackup(String newline)throws Exception {
        String template=ServerSettings.template().replace("\r\n","\n");String old=template.substring(0,template.indexOf("\n[map]"));
        Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old.replace("\n",newline));
        assertTrue(ServerSettings.load(root).flag("map.enabled"));assertTrue(ServerSettings.load(root).flag("map.radar"));String expanded=Files.readString(file);assertTrue(expanded.contains("[map]"));assertTrue(expanded.contains("# Карта Rivet"));
        Path backup;try(var files=Files.list(file.getParent())){backup=files.filter(p->p.toString().endsWith(".bak")).findFirst().orElseThrow();}
        assertEquals(old.replace("\n",newline),Files.readString(backup));
        String disabled=expanded.replace("# Изменение применяется после полного перезапуска сервера."+newline+"enabled = true", "# Изменение применяется после полного перезапуска сервера."+newline+"enabled = false");
        Files.writeString(file,disabled);assertFalse(ServerSettings.load(root).flag("map.enabled"));assertEquals(disabled,Files.readString(file));
    }
    @Test void deathTimePersistsAndLegacyMarkersKeepTheirIdentity()throws Exception {
        var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var id=UUID.randomUUID();
        var old=new MapMarker(id,"minecraft:overworld","Дом",1,64,2,0x123456,"home");
        var death=new MapMarker(UUID.randomUUID(),"minecraft:the_nether","Смерть",-12,44,3,0xffef7777,"skull",1791450000000L);
        repo.markers(List.of(old,death));assertEquals(List.of(old,death),repo.markers());assertFalse(repo.markers().getFirst().death());assertTrue(repo.markers().getLast().death());
        assertThrows(IllegalArgumentException.class,()->new MapMarker(id,"minecraft:overworld","x",0,64,0,0,"skull",-1));
        Path file;try(var files=Files.walk(root)){file=files.filter(p->p.getFileName().toString().equals("markers.json")).findFirst().orElseThrow();}
        var json=dev.abros.rivet.core.Json.read(file);for(var row:json.getAsJsonArray("markers"))row.getAsJsonObject().remove("deathAt");dev.abros.rivet.core.Json.write(file,json);
        assertEquals(id,repo.markers().getFirst().id());assertFalse(repo.markers().getLast().death());
    }
}
