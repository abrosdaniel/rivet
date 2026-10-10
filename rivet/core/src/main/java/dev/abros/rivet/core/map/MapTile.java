package dev.abros.rivet.core.map;

import java.util.Arrays;

/** Surface samples retain the ground and translucent overlays independently. Zero colour is unknown. */
public final class MapTile {
    private final int[] colors, heights, groundHeights, overlays;
    private final boolean[] glowing;
    private final java.util.List<MapOverlay>[] layers;
    private final String[] biomes=new String[256];
    private final byte[] blockLight=new byte[256],skyLight=new byte[256];
    {Arrays.fill(blockLight,(byte)-1);Arrays.fill(skyLight,(byte)-1);}
    private long revision;
    public MapTile(){this(new int[256],new int[256]);}
    public MapTile(int[] colors,int[] heights){this(colors,heights,heights,opaque(),new boolean[256]);}
    public MapTile(int[] colors,int[] heights,int[] groundHeights,int[] overlays,boolean[] glowing){
        this(colors,heights,groundHeights,overlays,glowing,null);
    }
    @SuppressWarnings("unchecked")
    public MapTile(int[] colors,int[] heights,int[] groundHeights,int[] overlays,boolean[] glowing,java.util.List<MapOverlay>[] layers){
        this.layers=(java.util.List<MapOverlay>[])new java.util.List<?>[256];
        if(layers!=null){if(layers.length!=256)throw new IllegalArgumentException("Invalid overlay array");for(int i=0;i<256;i++)if(layers[i]!=null){if(layers[i].size()>10)throw new IllegalArgumentException("Too many overlays");this.layers[i]=java.util.List.copyOf(layers[i]);}}
        if(colors.length!=256||heights.length!=256||groundHeights.length!=256||overlays.length!=256||glowing.length!=256)throw new IllegalArgumentException("Invalid map tile");
        this.colors=colors.clone();this.heights=heights.clone();this.groundHeights=groundHeights.clone();this.overlays=overlays.clone();this.glowing=glowing.clone();
    }
    private static int[] opaque(){int[] values=new int[256];Arrays.fill(values,0xff000000);return values;}
    public int color(int x,int z){return colors[index(x,z)];}
    public int height(int x,int z){return heights[index(x,z)];}
    public int groundHeight(int x,int z){return groundHeights[index(x,z)];}
    public int overlay(int x,int z){return overlays[index(x,z)];}
    public boolean glowing(int x,int z){return glowing[index(x,z)];}
    public java.util.List<MapOverlay> layers(int x,int z){return layers[index(x,z)];}
    public String biome(int x,int z){String value=biomes[index(x,z)];return value==null?"":value;}
    public boolean biome(int x,int z,String value){if(value==null||value.length()>256||!value.isEmpty()&&!value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid biome");int i=index(x,z);if(biome(x,z).equals(value))return false;biomes[i]=value;revision++;return true;}
    public int blockLight(int x,int z){return blockLight[index(x,z)];}
    public boolean blockLight(int x,int z,int light){if(light< -1||light>15)throw new IllegalArgumentException("Invalid block light");int i=index(x,z);if(blockLight[i]==light)return false;blockLight[i]=(byte)light;revision++;return true;}
    public int skyLight(int x,int z){return skyLight[index(x,z)];}
    public boolean skyLight(int x,int z,int light){if(light< -1||light>15)throw new IllegalArgumentException("Invalid sky light");int i=index(x,z);if(skyLight[i]==light)return false;skyLight[i]=(byte)light;revision++;return true;}
    public long revision(){return revision;}
    public boolean set(int x,int z,int color,int height){return set(x,z,color,height,height,0xff000000,false);}
    public boolean set(int x,int z,int color,int height,int groundHeight,int overlay,boolean glow){
        return set(x,z,color,height,groundHeight,overlay,glow,null);
    }
    public boolean set(int x,int z,int color,int height,int groundHeight,int overlay,boolean glow,java.util.List<MapOverlay> transparent){
        if(transparent!=null&&transparent.size()>10)throw new IllegalArgumentException("Too many overlays");
        int i=index(x,z);if(java.util.Objects.equals(layers[i],transparent)&&colors[i]==color&&heights[i]==height&&groundHeights[i]==groundHeight&&overlays[i]==overlay&&glowing[i]==glow)return false;
        layers[i]=transparent==null?null:java.util.List.copyOf(transparent);colors[i]=color;heights[i]=height;groundHeights[i]=groundHeight;overlays[i]=overlay;glowing[i]=glow;revision++;return true;
    }
    public MapTile copy(){var copy=new MapTile(colors,heights,groundHeights,overlays,glowing,layers);System.arraycopy(biomes,0,copy.biomes,0,256);System.arraycopy(blockLight,0,copy.blockLight,0,256);System.arraycopy(skyLight,0,copy.skyLight,0,256);return copy;}
    public int[] colors(){return colors.clone();}
    public int[] heights(){return heights.clone();}
    private static int index(int x,int z){if(x<0||x>15||z<0||z>15)throw new IllegalArgumentException("Invalid tile position");return z*16+x;}
}
