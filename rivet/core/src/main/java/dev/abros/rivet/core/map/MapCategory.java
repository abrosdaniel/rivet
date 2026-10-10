package dev.abros.rivet.core.map;
import java.util.UUID;
/** Personal category identity is stable across renaming. */
public record MapCategory(UUID id,String name,boolean mapVisible,boolean minimapVisible){
 public MapCategory{if(id==null||name==null||name.isBlank()||name.codePointCount(0,name.length())>40||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid map category");name=name.strip();}
 public boolean shows(MapMarker marker,boolean minimap){return (minimap?marker.minimapVisible()&&minimapVisible:marker.mapVisible()&&mapVisible);}
}
