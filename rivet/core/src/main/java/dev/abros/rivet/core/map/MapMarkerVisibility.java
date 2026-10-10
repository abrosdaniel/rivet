package dev.abros.rivet.core.map;

import java.util.Map;
import java.util.function.Predicate;

/** Immutable visibility snapshot for one render pass; no per-marker streams or category scans. */
public record MapMarkerVisibility(boolean minimap, boolean markers, boolean deaths,
                                  Map<String,MapCategory> categories) implements Predicate<MapMarker> {
    public MapMarkerVisibility { categories=Map.copyOf(categories); }
    @Override public boolean test(MapMarker marker) {
        if(!(marker.death()?deaths:markers)||!(minimap?marker.minimapVisible():marker.mapVisible()))return false;
        var category=categories.get(marker.category());
        return category==null||(minimap?category.minimapVisible():category.mapVisible());
    }
}
