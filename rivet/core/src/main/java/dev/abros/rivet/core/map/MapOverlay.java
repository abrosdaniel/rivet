package dev.abros.rivet.core.map;

/** Original transparent material, before display lighting and blending. */
public record MapOverlay(int color,int light,int opacity,int skyLight) {
 public MapOverlay(int color,int light,int opacity){this(color,light,opacity,-1);}
 public MapOverlay {if(skyLight< -1||skyLight>15)throw new IllegalArgumentException("Invalid sky light");if(light<0||light>15||opacity<0||opacity>15)throw new IllegalArgumentException("Invalid overlay lighting");}
}
