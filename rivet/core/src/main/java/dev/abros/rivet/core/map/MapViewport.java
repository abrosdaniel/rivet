package dev.abros.rivet.core.map;

/** Map coordinates are block coordinates; zoom is GUI pixels per block. */
public final class MapViewport {
    public static final double MIN_ZOOM = 0.125, MAX_ZOOM = 8;
    private double x, z, zoom = 1;
    public double x() { return x; }
    public double z() { return z; }
    public double zoom() { return zoom; }
    public void center(double x, double z) { double cx=clamp(x),cz=clamp(z);this.x=cx;this.z=cz; }
    public double worldX(double pixel, double midpoint) { return x + (pixel-midpoint)/zoom; }
    public double worldZ(double pixel, double midpoint) { return z + (pixel-midpoint)/zoom; }
    public double screenX(double block, double midpoint) { return midpoint+(block-x)*zoom; }
    public double screenZ(double block, double midpoint) { return midpoint+(block-z)*zoom; }
    public void pan(double dx, double dz) { center(x-dx/zoom,z-dz/zoom); }
    public void zoomAt(double steps, double pixelX, double pixelZ, double midpointX, double midpointZ) {
        if(!Double.isFinite(steps)||!Double.isFinite(pixelX)||!Double.isFinite(pixelZ)||!Double.isFinite(midpointX)||!Double.isFinite(midpointZ))throw new IllegalArgumentException("Invalid map zoom");
        double anchorX=worldX(pixelX,midpointX), anchorZ=worldZ(pixelZ,midpointZ);
        zoom=Math.clamp(zoom*Math.pow(1.25,steps),MIN_ZOOM,MAX_ZOOM);
        center(anchorX-(pixelX-midpointX)/zoom,anchorZ-(pixelZ-midpointZ)/zoom);
    }
    private static double clamp(double n) { if(!Double.isFinite(n))throw new IllegalArgumentException("Invalid map coordinate");return Math.clamp(n,-30000000,30000000); }
}
