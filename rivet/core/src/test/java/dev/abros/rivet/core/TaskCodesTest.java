package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TaskCodesTest {
 @Test void markerIgnoresCapitalizationAndSurroundingWhitespace(){for(String value:List.of("&rivet","&RIVET","&Rivet","  &rIvEt  "))assertTrue(TaskCodes.isMarker(value));for(String value:List.of("rivet","&rivet-other","& rivet",""))assertFalse(TaskCodes.isMarker(value));assertFalse(TaskCodes.isMarker(null));}
@Test void codesAreUniqueReadableAndRoundTripNormalizes(){var seen=new HashSet<String>();for(long n=10000;n<100000;n++){String c=TaskCodes.encode(n);assertTrue(c.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4,}"));assertTrue(seen.add(c));assertEquals(c,TaskCodes.normalize("  "+c.toLowerCase(Locale.ROOT)+" "));}assertThrows(IllegalArgumentException.class,()->TaskCodes.encode(-1));}}
