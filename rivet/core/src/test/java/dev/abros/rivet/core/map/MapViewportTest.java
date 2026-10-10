package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapViewportTest {
    @Test void zoomKeepsBlockUnderCursorAndPanUsesCurrentScale() {
        var v=new MapViewport();v.center(-140,90);double x=v.worldX(310,200),z=v.worldZ(40,100);
        v.zoomAt(4,310,40,200,100);assertEquals(x,v.worldX(310,200),1e-9);assertEquals(z,v.worldZ(40,100),1e-9);
        double before=v.x();v.pan(20,0);assertEquals(before-20/v.zoom(),v.x(),1e-9);
        assertEquals(310,v.screenX(v.worldX(310,200),200),1e-9);
    }
    @Test void zoomAndWorldBoundsAreLimited() {
        var v=new MapViewport();v.zoomAt(-100,0,0,0,0);assertEquals(MapViewport.MIN_ZOOM,v.zoom());
        v.zoomAt(100,0,0,0,0);assertEquals(MapViewport.MAX_ZOOM,v.zoom());v.center(9e20,-9e20);assertEquals(30000000,v.x());assertEquals(-30000000,v.z());
    }
    @Test void invalidInputLeavesViewIntact() {
        var v=new MapViewport();v.center(10,20);assertThrows(IllegalArgumentException.class,()->v.center(30,Double.NaN));assertEquals(10,v.x());
        assertThrows(IllegalArgumentException.class,()->v.zoomAt(Double.NaN,0,0,0,0));assertEquals(1,v.zoom());assertEquals(20,v.z());
    }
}
