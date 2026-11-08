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
  if(input==null||input.length<33||input.length>limit||!Arrays.equals(Arrays.copyOf(input,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))throw invalid();
  if(ByteBuffer.wrap(input,8,4).getInt()!=13||ByteBuffer.wrap(input,12,4).getInt()!=0x49484452||ByteBuffer.wrap(input,16,4).getInt()!=64||ByteBuffer.wrap(input,20,4).getInt()!=32)throw invalid();
  var decoded=PngPixels.read(input);if(decoded==null||decoded.getWidth()!=64||decoded.getHeight()!=32)throw invalid();
  var image=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,64,32,decoded.getRGB(0,0,64,32,null,0,64),0,64);var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"PNG",out))throw invalid();return out.toByteArray();
 }
 private static IOException invalid(){return new IOException(Messages.text("rivet.cape.invalid"));}
 private CapeImage(){}
}
