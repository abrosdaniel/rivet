package dev.abros.rivet.core.map;

/** Coverage-preserving colour levels for zoomed-out terrain. Zero means unexplored. */
public final class MapDetail {
    public static int level(double guiPixelsPerBlock,double guiScale){
        if(!Double.isFinite(guiScale)||guiScale<=0)throw new IllegalArgumentException("Invalid GUI scale");
        return level(guiPixelsPerBlock*guiScale);
    }
    public static int level(double pixelsPerBlock){
        if(!Double.isFinite(pixelsPerBlock)||pixelsPerBlock<=0)throw new IllegalArgumentException("Invalid map scale");
        return Math.clamp((int)Math.floor(Math.log(1/pixelsPerBlock)/Math.log(2)),0,4);
    }
    public static int[][] pyramid(int[] full){
        if(full.length!=256)throw new IllegalArgumentException("Invalid terrain image");
        int[][] levels=new int[5][];levels[0]=full.clone();
        for(int level=1,side=8;level<5;level++,side/=2){
            int[] previous=levels[level-1],next=new int[side*side];
            for(int z=0;z<side;z++)for(int x=0;x<side;x++){
                int red=0,green=0,blue=0,count=0;
                for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
                    int c=previous[(z*2+dz)*side*2+x*2+dx];if(c==0)continue;
                    red+=c>>>16&255;green+=c>>>8&255;blue+=c&255;count++;
                }
                if(count>0)next[z*side+x]=0xff000000|red/count<<16|green/count<<8|blue/count;
            }
            levels[level]=next;
        }
        return levels;
    }
    private MapDetail(){}
}
