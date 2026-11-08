package dev.abros.rivet.core.map;

/** Entry ceilings scale down on small client heaps. They are not a byte-accurate heap quota. */
public record MapCacheBudget(int tiles,int overview) {
    public MapCacheBudget {
        if(tiles<1024||tiles>8192||overview<16384||overview>100000)
            throw new IllegalArgumentException("Invalid map cache budget");
    }
    public static MapCacheBudget forHeap(long maximumBytes){
        int tiles=(int)Math.clamp(maximumBytes/(512L*1024),1024L,8192L);
        return new MapCacheBudget(tiles,Math.clamp(tiles*12,16384,100000));
    }
}
