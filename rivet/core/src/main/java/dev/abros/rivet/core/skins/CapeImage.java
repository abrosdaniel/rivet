package dev.abros.rivet.core.skins;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.imageio.ImageIO;
import dev.abros.rivet.core.Messages;

/** Static vanilla cape layout; preserve alpha and discard all uploaded metadata. */
public final class CapeImage {
 public static byte[] normalize(byte[] input,int limit)throws IOException{
  return normalize(input,limit,1024);
 }
 public static byte[] normalize(byte[] input,int limit,int maxResolution)throws IOException{
  if(input==null||input.length<33||input.length>limit||!Arrays.equals(Arrays.copyOf(input,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))throw invalid();
  if(ByteBuffer.wrap(input,8,4).getInt()!=13||ByteBuffer.wrap(input,12,4).getInt()!=0x49484452)throw invalid();
  int w=ByteBuffer.wrap(input,16,4).getInt(),h=ByteBuffer.wrap(input,20,4).getInt();if(!SkinSettings.resolution(w)||w>maxResolution||h!=w/2)throw invalid();
  var decoded=PngPixels.read(input);if(decoded==null||decoded.getWidth()!=w||decoded.getHeight()!=h)throw invalid();
  var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,w,h,decoded.getRGB(0,0,w,h,null,0,w),0,w);var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"PNG",out))throw invalid();return out.toByteArray();
 }
 private static IOException invalid(){return new IOException(Messages.text("rivet.cape.invalid"));}
 private CapeImage(){}
}
