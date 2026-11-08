package dev.abros.rivet.client;

import java.util.*;
import dev.abros.rivet.client.MapRegionTextures.Key;

/** One bounded viewport snapshot, reused while panning stays inside its region boundaries. */
final class MapCoverage {
    private Set<Key> coverageInput=Set.of(),coverageCache=Set.of();
    void clear(){coverageInput=Set.of();coverageCache=Set.of();}
    Set<Key> get(Set<Key> keys){
        if(coverageInput.equals(keys))return coverageCache;
        var all=new LinkedHashSet<Key>(keys);
        for(var key:keys)for(int level=key.level()+1;level<=4;level++){
            int scale=1<<(level-key.level());all.add(new Key(key.layer(),Math.floorDiv(key.x(),scale),Math.floorDiv(key.z(),scale),level));
        }
        var ordered=new LinkedHashSet<Key>();
        for(int level=4;level>=0;level--)for(var key:all)if(key.level()==level)ordered.add(key);
        coverageInput=Set.copyOf(keys);coverageCache=Collections.unmodifiableSet(ordered);return coverageCache;
    }
}
