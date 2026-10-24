package dev.abros.rivet.core.map;

/** Surface and full-depth sentinels cannot collide with any 16-block cave band. */
public record MapLayer(String dimension,int band) {
    public static final int SURFACE=Integer.MAX_VALUE,FULL=Integer.MIN_VALUE;
    public MapLayer {if(!validDimension(dimension)||band!=SURFACE&&band!=FULL&&(band< -128||band>127))throw new IllegalArgumentException("Invalid map layer");}
    // Layer keys are constructed in render loops. Preserve the resource-ID grammar without
    // allocating a Matcher and its capture/group arrays for every tile lookup.
    private static boolean validDimension(String value){
        if(value==null)return false;boolean path=false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c==':'){if(path||i==0||i==value.length()-1)return false;path=true;}
            else if(!(c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_'||c=='.'||c=='-'||path&&c=='/'))return false;
        }
        return path;
    }
    public static MapLayer surface(String dimension){return new MapLayer(dimension,SURFACE);}
    public static MapLayer cave(String dimension,int top){return new MapLayer(dimension,Math.floorDiv(Math.clamp(top,-2048,2047),16));}
    public boolean cave(){return band!=SURFACE;}
}
