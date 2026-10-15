package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class IsometricRendererTest {
 @TempDir Path root;
 static int[] cube(int y,int color){int[] data=new int[32*256];data[y*256]=color;return data;}
 static int pixel(IsometricRenderer r,int u,int v)throws Exception{return ImageIO.read(new ByteArrayInputStream(r.tile(new TerrainApi.Tile("minecraft:overworld",0,Math.floorDiv(u,256),Math.floorDiv(v,256))).get().png())).getRGB(Math.floorMod(u,256),Math.floorMod(v,256));}
 @Test void crossChunkOcclusionOrderRemovalAndRestart()throws Exception{
  UUID id=UUID.randomUUID();var settings=TerrainRendererTest.settings(List.of());
  try(var r=new IsometricRenderer(root,id,settings)){
   r.render("minecraft:overworld",1,1,0,32,cube(16,0xffff0000)).get();r.render("minecraft:overworld",0,0,0,32,cube(0,0xff0000ff)).get();
   int top=pixel(r,0,-1);assertTrue((top>>>16&255)>0);assertEquals(0,top&255);
   r.render("minecraft:overworld",1,1,0,32,new int[32*256]).get();assertTrue((pixel(r,0,-1)&255)>0);assertEquals("isometric",r.projection().profile());
  }
  try(var r=new IsometricRenderer(root,id,settings)){assertTrue((pixel(r,0,-1)&255)>0);r.render("minecraft:overworld",1,1,0,32,cube(16,0xffff0000)).get();assertEquals(0,pixel(r,0,-1)&255);}
 }
 @Test void transparencyAndMaskAppliedBeforeProjection()throws Exception{
  try(var r=new IsometricRenderer(root.resolve("alpha"),UUID.randomUUID(),TerrainRendererTest.settings(List.of()))){
   r.render("minecraft:overworld",0,0,0,32,cube(0,0xff0000ff)).get();r.render("minecraft:overworld",1,1,0,32,cube(16,0x80ff0000)).get();int c=pixel(r,0,-1);assertTrue((c>>>16&255)>0);assertTrue((c&255)>0);
  }
  try(var r=new IsometricRenderer(root.resolve("mask"),UUID.randomUUID(),TerrainRendererTest.settings(List.of("16,16,16,16")))){
   r.render("minecraft:overworld",0,0,0,32,cube(0,0xff0000ff)).get();r.render("minecraft:overworld",1,1,0,32,cube(16,0xffff0000)).get();assertEquals(0,pixel(r,0,-1)>>>16&255);assertTrue((pixel(r,0,-1)&255)>0);
  }
 }
 @Test void geometryCrossesScreenTileBoundaryAndReplayIsIndependent()throws Exception{
  try(var r=new IsometricRenderer(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()))){var cursor=r.cursor();r.render("minecraft:overworld",4,-4,0,32,cube(0,0xff00ff00)).get();assertNotEquals(0,pixel(r,255,-1));assertNotEquals(0,pixel(r,256,-1));assertFalse(r.changes(cursor).items().isEmpty());assertEquals(256,r.metadata().tilePixels());}
 }
 @Test void architecturalSceneHasStableTileComposition()throws Exception{
  try(var r=new IsometricRenderer(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()))){
   for(int cz=0;cz<2;cz++)for(int cx=0;cx<2;cx++){int[] data=new int[32*256];for(int z=0;z<16;z++)for(int x=0;x<16;x++){
    int wx=cx*16+x,wz=cz*16+z;data[z*16+x]=0xff608d42;
    if(wx>=4&&wx<=9&&wz>=4&&wz<=9)data[256+z*16+x]=0xb04080d0;
    if(wx>=12&&wx<=19&&wz>=12&&wz<=19){for(int y=1;y<=12;y++)if(wx==12||wx==19||wz==12||wz==19)data[y*256+z*16+x]=0xffb8a080;data[13*256+z*16+x]=0x8090d0ed;}
   }r.render("minecraft:overworld",cx,cz,0,32,data).get();}
   var joined=new java.awt.image.BufferedImage(512,512,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=joined.createGraphics();for(int z=-1;z<=0;z++)for(int x=-1;x<=0;x++)g.drawImage(ImageIO.read(new ByteArrayInputStream(r.tile(new TerrainApi.Tile("minecraft:overworld",0,x,z)).get().png())),(x+1)*256,(z+1)*256,null);g.dispose();
   assertTrue((joined.getRGB(256,270)>>>24)>0);
   String output=System.getProperty("rivet.iso.preview");if(output!=null){var preview=new java.awt.image.BufferedImage(768,768,java.awt.image.BufferedImage.TYPE_INT_RGB);var draw=preview.createGraphics();draw.setColor(new java.awt.Color(24,28,34));draw.fillRect(0,0,768,768);draw.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);draw.drawImage(joined.getSubimage(192,192,128,128),0,0,768,768,null);draw.dispose();ImageIO.write(preview,"png",Path.of(output).toFile());}
  }
 }
}
