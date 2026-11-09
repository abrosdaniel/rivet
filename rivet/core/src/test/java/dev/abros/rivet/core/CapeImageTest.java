package dev.abros.rivet.core;
import dev.abros.rivet.core.skins.CapeImage;
import org.junit.jupiter.api.Test;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class CapeImageTest {
 @Test void hdPreservesAlphaAndChecksServerWidth()throws Exception{for(int width:new int[]{128,256,512,1024}){var png=SkinImageTest.png(width,width/2);var normalized=CapeImage.normalize(png,1048576,width);var image=ImageIO.read(new ByteArrayInputStream(normalized));assertEquals(width,image.getWidth());assertEquals(width/2,image.getHeight());assertEquals(0,image.getRGB(0,0)>>>24);assertArrayEquals(normalized,CapeImage.normalize(normalized,1048576,width));assertThrows(IOException.class,()->CapeImage.normalize(png,1048576,width/2));}assertThrows(IOException.class,()->CapeImage.normalize(SkinImageTest.png(96,48),1048576));}
 @Test void staticLayoutPreservesTransparencyAndReencodes()throws Exception{
  byte[] png=SkinImageTest.png(64,32);var result=CapeImage.normalize(png,262144);var image=ImageIO.read(new ByteArrayInputStream(result));var original=ImageIO.read(new ByteArrayInputStream(png));
  assertEquals(64,image.getWidth());assertEquals(32,image.getHeight());assertEquals(original.getRGB(0,0),image.getRGB(0,0));assertArrayEquals(result,CapeImage.normalize(result,262144));
 }
 @Test void rejectDimensionsOversizeTruncationAndAnimation()throws Exception{
  var png=SkinImageTest.png(64,32);assertThrows(IOException.class,()->CapeImage.normalize(png,png.length-1));assertThrows(IOException.class,()->CapeImage.normalize(SkinImageTest.png(64,64),262144));assertThrows(IOException.class,()->CapeImage.normalize(java.util.Arrays.copyOf(png,33),262144));
  var out=new ByteArrayOutputStream();out.write(png,0,33);var extra=new java.io.DataOutputStream(out);extra.writeInt(8);extra.writeInt(0x6163544c);extra.writeLong(1);extra.writeInt(0);out.write(png,33,png.length-33);assertThrows(IOException.class,()->CapeImage.normalize(out.toByteArray(),262144));
 }
 @Test void compressedMetadataIsNotHandedToDecoderAndCrcIsChecked()throws Exception{
  var png=SkinImageTest.png(64,32);var metadata=new java.io.ByteArrayOutputStream();var zipped=new java.util.zip.DeflaterOutputStream(metadata);zipped.write(new byte[2*1024*1024]);zipped.close();var body=new java.io.ByteArrayOutputStream();body.write(new byte[]{'x',0,0});body.write(metadata.toByteArray());var bytes=body.toByteArray();
  var out=new ByteArrayOutputStream();out.write(png,0,33);var data=new java.io.DataOutputStream(out);data.writeInt(bytes.length);data.writeInt(0x69434350);data.write(bytes);var crc=new java.util.zip.CRC32();crc.update(new byte[]{'i','C','C','P'});crc.update(bytes);data.writeInt((int)crc.getValue());out.write(png,33,png.length-33);
  assertArrayEquals(CapeImage.normalize(png,262144),CapeImage.normalize(out.toByteArray(),262144));
  var broken=png.clone();broken[29]^=1;assertThrows(IOException.class,()->CapeImage.normalize(broken,262144));
 }
 @Test void secondHeaderCannotOverrideValidatedDimensions()throws Exception{
  var png=SkinImageTest.png(64,32);var other=SkinImageTest.png(128,128);var out=new ByteArrayOutputStream();out.write(png,0,33);out.write(other,8,25);out.write(png,33,png.length-33);
  assertThrows(IOException.class,()->CapeImage.normalize(out.toByteArray(),262144));
  assertThrows(IOException.class,()->dev.abros.rivet.core.skins.SkinImage.normalize(out.toByteArray(),262144));
 }

}
