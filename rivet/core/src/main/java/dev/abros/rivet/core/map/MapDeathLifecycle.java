package dev.abros.rivet.core.map;

/** Death points expire even while disconnected; reaching one requires a living player. */
public final class MapDeathLifecycle {
    public static final long LIFETIME_MILLIS=10*60*1000L;
    public static boolean expired(MapMarker marker,long now){return marker.death()&&now>=marker.deathAt()&&now-marker.deathAt()>=LIFETIME_MILLIS;}
    public static boolean remove(MapMarker marker,long now,String dimension,double x,double y,double z,boolean alive){
        return expired(marker,now)||marker.death()&&alive&&marker.dimension().equals(dimension)&&Math.hypot(x-(marker.x()+.5),z-(marker.z()+.5))<=3&&Math.abs(y-marker.y())<=4;
    }
    public static String countdown(MapMarker marker,long now){
        long remaining=Math.max(0,LIFETIME_MILLIS-Math.max(0,now-marker.deathAt()));
        long seconds=(remaining+999)/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
    private MapDeathLifecycle(){}
}
