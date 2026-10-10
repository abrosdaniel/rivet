package dev.abros.rivet.core.map;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapMarkerVisibilityTest {
    private static MapMarker marker(String category,boolean map,boolean mini){
        return new MapMarker(UUID.randomUUID(),"minecraft:overworld","Marker",0,64,0,0,"pin",0,category,map,mini);
    }
    @Test void categoriesAndMarkerPreferencesRemainIndependentForEachSurface(){
        var id=UUID.randomUUID();var categories=Map.of(id.toString(),new MapCategory(id,"Category",false,true));
        var map=new MapMarkerVisibility(false,true,true,categories);
        var mini=new MapMarkerVisibility(true,true,true,categories);
        assertFalse(map.test(marker(id.toString(),true,true)));
        assertTrue(mini.test(marker(id.toString(),true,true)));
        assertFalse(mini.test(marker(id.toString(),true,false)));
        assertTrue(map.test(marker("",true,false)));
        assertTrue(map.test(marker(UUID.randomUUID().toString(),true,true)),"Removing a category must not hide its markers");
    }
    @Test void disabledLayersAndDeathsUseTheirOwnVisibility(){
        var ordinary=marker("",true,true);
        var death=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Death",0,64,0,0,"skull",1);
        for(boolean mini:List.of(false,true)){
            var deathsOnly=new MapMarkerVisibility(mini,false,true,Map.of());
            assertFalse(deathsOnly.test(ordinary));assertTrue(deathsOnly.test(death));
            var markersOnly=new MapMarkerVisibility(mini,true,false,Map.of());
            assertTrue(markersOnly.test(ordinary));assertFalse(markersOnly.test(death));
            var disabled=new MapMarkerVisibility(mini,false,false,Map.of());
            assertFalse(disabled.test(ordinary));assertFalse(disabled.test(death));
        }
    }
    @Test void categoryChangesTakeEffectInTheNextSnapshot(){
        var id=UUID.randomUUID();var categories=new HashMap<String,MapCategory>();
        categories.put(id.toString(),new MapCategory(id,"Before",false,false));
        var before=new MapMarkerVisibility(false,true,true,categories);var marker=marker(id.toString(),true,true);
        categories.put(id.toString(),new MapCategory(id,"Renamed",true,true));
        var after=new MapMarkerVisibility(false,true,true,categories);
        assertFalse(before.test(marker));assertTrue(after.test(marker));
        categories.clear();assertTrue(new MapMarkerVisibility(false,true,true,categories).test(marker));
    }
}
