package dev.abros.rivet.core.map;

import java.util.Locale;

/** Surface height + half a block, matching Xaero's default partial-Y teleport. */
public final class MapTeleport {
    public static String command(MapMarker marker,String currentDimension) {
        return command(marker,currentDimension,true);
    }
    public static String command(MapMarker marker,String currentDimension,boolean alias) {
        String target=String.format(Locale.ROOT,"%s @s %.1f %.1f %.1f",alias?"tp":"teleport",marker.x()+.5,marker.y()+.5,marker.z()+.5);
        return marker.dimension().equals(currentDimension)?target:"execute in "+marker.dimension()+" run "+target;
    }
    private MapTeleport() {}
}
