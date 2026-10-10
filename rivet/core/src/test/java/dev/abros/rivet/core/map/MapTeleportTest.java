package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
class MapTeleportTest {
    private MapMarker marker(String dimension,int x,int y,int z){return new MapMarker(UUID.randomUUID(),dimension,"Home",x,y,z,0xffffff,"home");}
    @Test void centersTheColumnAndRaisesStoredFeetHeight(){assertEquals("tp @s 10.5 81.5 -19.5",MapTeleport.command(marker("minecraft:overworld",10,81,-20),"minecraft:overworld"));}
    @Test void changesDimensionOnlyWhenNeeded(){assertEquals("execute in minecraft:the_end run tp @s 0.5 65.5 0.5",MapTeleport.command(marker("minecraft:the_end",0,65,0),"minecraft:overworld"));}
    @Test void fallsBackToCanonicalCommandWhenAliasIsHidden(){assertEquals("teleport @s 10.5 81.5 -19.5",MapTeleport.command(marker("minecraft:overworld",10,81,-20),"minecraft:overworld",false));}
    @Test void formatsCoordinatesIndependentOfLocale(){var before=Locale.getDefault();try{Locale.setDefault(Locale.GERMANY);assertEquals("tp @s -0.5 -59.5 -0.5",MapTeleport.command(marker("minecraft:overworld",-1,-60,-1),"minecraft:overworld"));}finally{Locale.setDefault(before);}}
}
