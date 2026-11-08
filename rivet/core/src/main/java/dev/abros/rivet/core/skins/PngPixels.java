package dev.abros.rivet.core.skins;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Do not pass compressed profiles/text or animation extensions to the image decoder. */
final class PngPixels {
 private static final Set<Integer> PIXELS=Set.of(0x49484452,0x504c5445,0x74524e53,0x49444154,0x49454e44);
 static BufferedImage read(byte[] input)throws IOException{
  var clean=new ByteArrayOutputStream(input.length);clean.write(input,0,8);boolean end=false,header=false,pixels=false,pixelsEnded=false,palette=false,transparency=false;
  for(int at=8;at<input.length;){
   if(input.length-at<12)throw invalid();int size=ByteBuffer.wrap(input,at,4).getInt();if(size<0||size>input.length-at-12)throw invalid();int type=ByteBuffer.wrap(input,at+4,4).getInt();
   var crc=new CRC32();crc.update(input,at+4,size+4);if((int)crc.getValue()!=ByteBuffer.wrap(input,at+8+size,4).getInt())throw invalid();
   if(type==0x6163544c||!PIXELS.contains(type)&&(input[at+4]&32)==0)throw invalid();
   switch(type){
    case 0x49484452->{if(header||at!=8||size!=13)throw invalid();header=true;}
    case 0x504c5445->{if(!header||palette||pixels||size==0||size>768||size%3!=0)throw invalid();palette=true;}
    case 0x74524e53->{if(!header||transparency||pixels)throw invalid();transparency=true;}
    case 0x49444154->{if(!header||pixelsEnded)throw invalid();pixels=true;}
    case 0x49454e44->{if(!pixels)throw invalid();}
    default->{if(pixels)pixelsEnded=true;}
   }
   if(PIXELS.contains(type))clean.write(input,at,size+12);
   at+=size+12;if(type==0x49454e44){if(size!=0||at!=input.length)throw invalid();end=true;break;}
  }
  if(!end)throw invalid();return ImageIO.read(new ByteArrayInputStream(clean.toByteArray()));
 }
 private static IOException invalid(){return new IOException(dev.abros.rivet.core.Messages.text("rivet.core.corrupt_png_cd2a4224"));}
 private PngPixels(){}
}
