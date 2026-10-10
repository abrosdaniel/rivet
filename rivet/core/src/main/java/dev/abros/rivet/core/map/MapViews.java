package dev.abros.rivet.core.map;

/** Views derive only from recorded columns; unknown terrain never gains pixels. */
public final class MapViews {
 public static final int SURFACE=0,NIGHT=1,BIOMES=2,RELIEF=3;
 private MapViews(){}
 public static int color(MapTile tile,int x,int z,int terrain,int north,int northWest,int view){
  if(tile.color(x,z)==0)return 0;
  if(view==NIGHT){
   int light=tile.glowing(x,z)?15:Math.max(0,tile.blockLight(x,z));
   var layers=tile.layers(x,z);if(layers!=null)for(var layer:layers)light=Math.max(light,layer.light());
   float strength=light/15f;float brightness=.23f+.77f*strength;
   return multiply(terrain,brightness*(.75f+.25f*strength),brightness*(.85f+.15f*strength),brightness);
  }
  if(view==BIOMES)return biome(tile.biome(x,z));
  if(view==RELIEF){
   int height=tile.groundHeight(x,z);int color=height<0?mix(0xff254d79,0xff5e9aa1,(height+64)/64f):height<64?mix(0xff5e9aa1,0xff76a557,height/64f):height<128?mix(0xff76a557,0xffb5ae68,(height-64)/64f):height<224?mix(0xffb5ae68,0xff947968,(height-128)/96f):mix(0xff947968,0xfff0ebe2,(height-224)/96f);
   float slope=Math.clamp(1+(height-north)*.018f+(height-northWest)*.009f,.65f,1.2f);
   // Subtle contours remain legible when colours are reduced to overview levels.
   if(Math.floorMod(height,16)==0)slope*=.88f;
   return multiply(color,slope,slope,slope);
  }
  return terrain;
 }
 public static int biome(String id){
  if(id.isEmpty())return 0xff777777; // Legacy columns have no recorded biome.
  if(id.startsWith("minecraft:")){
   String name=id.substring(10);
   if(name.contains("frozen")||name.contains("snow")||name.contains("ice"))return 0xffb9e0ec;
   if(name.contains("ocean"))return name.contains("deep")?0xff28568e:0xff398db5;
   if(name.contains("river"))return 0xff65bdd1;
   if(name.contains("desert")||name.contains("beach"))return 0xffe2cd7a;
   if(name.contains("badlands"))return 0xffc7744a;
   if(name.contains("swamp"))return 0xff658366;
   if(name.contains("jungle"))return 0xff38974c;
   if(name.contains("taiga"))return 0xff517c74;
   if(name.contains("forest")||name.contains("grove"))return name.contains("cherry")?0xffdd98bb:0xff548d42;
   if(name.contains("plains")||name.contains("meadow"))return 0xff94b956;
   if(name.contains("savanna"))return 0xffb4ad52;
   if(name.contains("peak")||name.contains("slope")||name.contains("hill"))return 0xff999c92;
   if(name.contains("mushroom"))return 0xffac76b5;
   if(name.contains("nether")||name.contains("crimson"))return 0xffa84448;
   if(name.contains("warped"))return 0xff42aaa4;
   if(name.contains("basalt"))return 0xff696374;
   if(name.contains("soul"))return 0xff987864;
   if(name.contains("end")||name.contains("void"))return 0xffb9aece;
   if(name.contains("lush"))return 0xff78b36c;
   if(name.contains("dripstone"))return 0xffaf936e;
   if(name.contains("deep_dark"))return 0xff305b66;
  }
  // Stable colour for modded biomes, independent of registry order and session.
  int hash=id.hashCode();return 0xff000000|(80+Math.floorMod(hash,144))<<16|(80+Math.floorMod(hash>>>8,144))<<8|80+Math.floorMod(hash>>>16,144);
 }
 private static int mix(int a,int b,float t){t=Math.clamp(t,0,1);int color=0xff000000;for(int shift:new int[]{16,8,0})color|=Math.round((a>>>shift&255)*(1-t)+(b>>>shift&255)*t)<<shift;return color;}
 private static int multiply(int color,float r,float g,float b){return color&0xff000000|Math.clamp(Math.round((color>>>16&255)*r),0,255)<<16|Math.clamp(Math.round((color>>>8&255)*g),0,255)<<8|Math.clamp(Math.round((color&255)*b),0,255);}
}
