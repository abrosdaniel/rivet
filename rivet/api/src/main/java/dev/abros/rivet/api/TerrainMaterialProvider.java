package dev.abros.rivet.api;

import java.util.*;

/** Optional ServiceLoader extension for server-safe block models. Never reference client classes.
 * Called on render workers, not the server thread. Implementations must be deterministic and thread-safe.
 * Change revision whenever returned geometry/textures change. No world or block-entity access is supplied.
 */
public interface TerrainMaterialProvider {
 String id();
 String revision();
 Optional<Model> model(String block, Map<String,String> properties);
 record Vertex(double x,double y,double z,double u,double v) {
  public Vertex {for(double n:new double[]{x,y,z,u,v})if(!Double.isFinite(n)||Math.abs(n)>64)throw new IllegalArgumentException("Invalid vertex");if(x< -1||x>2||y< -1||y>2||z< -1||z>2)throw new IllegalArgumentException("Geometry outside block bounds");}
 }
 record Texture(int width,int height,int[] argb) {
  public Texture {if(width<1||height<1||width>512||height>512||argb.length!=width*height)throw new IllegalArgumentException("Invalid texture");argb=argb.clone();}
  public int[] argb(){return argb.clone();}
  public int sample(double u,double v){return argb[Math.min(height-1,Math.max(0,(int)(v*height)))*width+Math.min(width-1,Math.max(0,(int)(u*width)))];}
 }
 record Quad(List<Vertex> vertices,Texture texture,boolean tinted,boolean shade) {
  public Quad {vertices=List.copyOf(vertices);if(vertices.size()!=4)throw new IllegalArgumentException("Expected quad");Objects.requireNonNull(texture);}
 }
 record Model(List<Quad> quads) {
  public Model {quads=List.copyOf(quads);if(quads.size()>384)throw new IllegalArgumentException("Too many faces");}
 }
}
