package dev.abros.rivet.core.map;

import com.google.gson.JsonObject;
import java.util.UUID;

/** Immutable map permissions belong to a connection, never to a menu snapshot. */
public final class MapConnectionPolicy<C> {
    public record State(UUID world,boolean enabled,boolean caves,boolean radar,boolean positions,int nearbyRadius) {}
    private static final State DENIED=new State(null,false,false,false,false,128);
    private C connection;
    private State state=DENIED;
    public void connect(C current){if(connection!=current){connection=current;state=DENIED;}}
    public State current(C current){return current!=null&&connection==current?state:DENIED;}
    public boolean accept(C source,JsonObject packet){
        if(source==null||source!=connection)return false;
        state=DENIED;
        try {
            if(!packet.has("features")||!packet.getAsJsonArray("features").contains(new com.google.gson.JsonPrimitive("world-map")))return false;
            var map=packet.getAsJsonObject("map");
            if(map==null||!flag(map,"enabled",false))return false;
            UUID world=UUID.fromString(map.get("world").getAsString());
            int radius=map.has("nearbyRadius")?map.get("nearbyRadius").getAsBigDecimal().intValueExact():128;
            if(radius<1||radius>4096)return false;
            state=new State(world,true,flag(map,"caves",true),flag(map,"radar",true),flag(map,"positions",false),radius);
            return true;
        }catch(RuntimeException invalid){return false;}
    }
    private static boolean flag(JsonObject object,String key,boolean fallback){
        if(!object.has(key))return fallback;
        if(!object.get(key).isJsonPrimitive()||!object.getAsJsonPrimitive(key).isBoolean())throw new IllegalArgumentException("Invalid map policy");
        return object.get(key).getAsBoolean();
    }
}
