package dev.abros.rivet.core;
import dev.abros.rivet.core.skins.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class SkinImageTest {
 static byte[] png(int width,int height)throws Exception{var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);image.setRGB(8,8,height==32?0xffff0000:0xff00ff00);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
 @Test void normalizesLegacyAndKeepsFace()throws Exception{var bytes=SkinImage.normalize(png(64,32),1048576);var image=ImageIO.read(new ByteArrayInputStream(bytes));assertEquals(64,image.getHeight());assertEquals(0xffff0000,image.getRGB(8,8));assertEquals(255,image.getRGB(0,16)>>>24);}
 @Test void rejectsInvalidSizesBeforeDecode()throws Exception{assertThrows(Exception.class,()->SkinImage.normalize(png(96,96),1048576));assertThrows(Exception.class,()->SkinImage.normalize(png(64,64),32));assertThrows(Exception.class,()->SkinImage.normalize(new byte[100],1048576));}
 @Test void hdLayersAndLegacyConversionKeepScaledPixels()throws Exception{
  for(int width:new int[]{128,256,512,1024}){int k=width/64;var image=new BufferedImage(width,width,BufferedImage.TYPE_INT_ARGB);image.setRGB(8*k,8*k,0x00123456);image.setRGB(40*k,8*k,0x80123456);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);var normalized=SkinImage.normalize(out.toByteArray(),2097152);var decoded=ImageIO.read(new ByteArrayInputStream(normalized));assertEquals(width,decoded.getWidth());assertEquals(0xff123456,decoded.getRGB(8*k,8*k));assertEquals(0x80123456,decoded.getRGB(40*k,8*k));assertArrayEquals(normalized,SkinImage.normalize(normalized,2097152));
   var legacy=new BufferedImage(width,width/2,BufferedImage.TYPE_INT_ARGB);legacy.setRGB(4*k,20*k,0xffabcdef);out.reset();ImageIO.write(legacy,"png",out);decoded=ImageIO.read(new ByteArrayInputStream(SkinImage.normalize(out.toByteArray(),2097152)));assertEquals(width,decoded.getHeight());assertEquals(0xffabcdef,decoded.getRGB(24*k-1,52*k));
  }
 }
 @Test void hdServerLimitsAreEnforced()throws Exception{for(int width:new int[]{64,128,256,512,1024}){var png=png(width,width);assertDoesNotThrow(()->SkinImage.normalize(png,2097152,width));if(width>64)assertThrows(IOException.class,()->SkinImage.normalize(png,2097152,width/2));}assertThrows(IOException.class,()->SkinImage.normalize(png(2048,2048),8388608));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,8,"false",true,8,256,8,96,256));}
 @Test void acceptsEightSavedSkins(){for(int n=1;n<=8;n++)assertEquals(n,new SkinSettings(true,1,n,"false").maxSkinsPerPlayer());}
 @Test void stableEncodingAndLimits()throws Exception{var bytes=SkinImage.normalize(png(64,64),1048576);assertArrayEquals(bytes,SkinImage.normalize(bytes,1048576));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,0,"false"));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,9,"false"));assertThrows(IllegalArgumentException.class,()->new SkinSettings(true,1,2,"true"));assertEquals(1048576,new SkinSettings(true,1,2,"UUID").maxBytes());}
}
