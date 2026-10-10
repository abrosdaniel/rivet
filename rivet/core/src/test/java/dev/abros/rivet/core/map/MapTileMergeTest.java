package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapTileMergeTest {
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;

 @Test void mergeNeverMutatesOrRepublishesOwnRepository(){var own=new MapTile();own.set(1,1,0xff112233,20);var received=new MapTile();received.set(1,1,-1,21);received.set(2,2,-1,22);var combined=MapTileMerge.merge(own,received);assertEquals(own.color(1,1),combined.color(1,1));assertEquals(-1,combined.color(2,2));assertEquals(0,own.color(2,2));assertEquals(-1,received.color(1,1));}
}
