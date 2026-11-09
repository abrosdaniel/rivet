package dev.abros.rivet.core.skins;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
/** Decode only bounded skin dimensions, then re-encode pixels without uploaded metadata. */
public final class SkinImage {
 private SkinImage(){}
 public static byte[] normalize(byte[] input,int limit)throws IOException{
  return normalize(input,limit,1024);
 }
 public static byte[] normalize(byte[] input,int limit,int maxResolution)throws IOException{
  if(input==null||input.length<33||input.length>limit||!Arrays.equals(Arrays.copyOf(input,8),new byte[]{(byte)137,80,78,71,13,10,26,10})||ByteBuffer.wrap(input,8,4).getInt()!=13||ByteBuffer.wrap(input,12,4).getInt()!=0x49484452)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.select_a_png_within_the_size_ac695159"));
  int w=ByteBuffer.wrap(input,16,4).getInt(),h=ByteBuffer.wrap(input,20,4).getInt();if(!SkinSettings.resolution(w)||w>maxResolution||(h!=w&&h!=w/2))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.skin_must_be_64_64_or_1fe3d960"));
  var decoded=PngPixels.read(input);if(decoded==null||decoded.getWidth()!=w||decoded.getHeight()!=h)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.corrupt_png_cd2a4224"));
  var image=new BufferedImage(w,w,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,decoded.getRGB(0,0,w,h,null,0,w),0,w);int scale=w/64;
  if(h==w/2){int[][] parts={{4,16,16,32,4,4},{8,16,16,32,4,4},{0,20,24,32,4,12},{4,20,16,32,4,12},{8,20,8,32,4,12},{12,20,16,32,4,12},{44,16,-8,32,4,4},{48,16,-8,32,4,4},{40,20,0,32,4,12},{44,20,-8,32,4,12},{48,20,-16,32,4,12},{52,20,-8,32,4,12}};for(var p:parts)for(int y=0;y<p[5]*scale;y++)for(int x=0;x<p[4]*scale;x++)image.setRGB((p[0]+p[2]+p[4])*scale-1-x,(p[1]+p[3])*scale+y,decoded.getRGB(p[0]*scale+x,p[1]*scale+y));
   boolean solid=true;for(int y=0;y<32*scale;y++)for(int x=32*scale;x<64*scale;x++)if((image.getRGB(x,y)>>>24)<128)solid=false;
   if(solid)for(int y=0;y<32*scale;y++)for(int x=32*scale;x<64*scale;x++)image.setRGB(x,y,image.getRGB(x,y)&0xFFFFFF);
  }
  opaque(image,0,0,32*scale,16*scale);opaque(image,0,16*scale,64*scale,32*scale);opaque(image,16*scale,48*scale,48*scale,64*scale);
  var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return out.toByteArray();
 }
 private static void opaque(BufferedImage i,int left,int top,int right,int bottom){for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)i.setRGB(x,y,i.getRGB(x,y)|0xFF000000);}
}
