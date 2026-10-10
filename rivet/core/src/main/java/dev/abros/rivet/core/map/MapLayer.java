package dev.abros.rivet.core.map;

/** Surface and full-depth sentinels cannot collide with any 16-block cave band. */
public record MapLayer(String dimension,int band) {
    public static final int SURFACE=Integer.MAX_VALUE,FULL=Integer.MIN_VALUE;
    private static final java.util.regex.Pattern DIMENSION=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public MapLayer {if(dimension==null||!DIMENSION.matcher(dimension).matches()||band!=SURFACE&&band!=FULL&&(band< -128||band>127))throw new IllegalArgumentException("Invalid map layer");}
    public static MapLayer surface(String dimension){return new MapLayer(dimension,SURFACE);}
    public static MapLayer cave(String dimension,int top){return new MapLayer(dimension,Math.floorDiv(Math.clamp(top,-2048,2047),16));}
    public boolean cave(){return band!=SURFACE;}
}
