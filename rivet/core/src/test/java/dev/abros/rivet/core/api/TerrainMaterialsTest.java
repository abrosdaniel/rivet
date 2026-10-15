package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.material.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class TerrainMaterialsTest {
 @TempDir Path root;
 void json(String location,String text)throws Exception{Path p=root.resolve("assets/test/"+location+".json");Files.createDirectories(p.getParent());Files.writeString(p,text);}
 void texture(int... colors)throws Exception{Path p=root.resolve("assets/test/textures/block/check.png");Files.createDirectories(p.getParent());var image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,2,2,colors,0,2);ImageIO.write(image,"png",p.toFile());}
 String element(String from,String to){return "{\"from\":"+from+",\"to\":"+to+",\"faces\":{\"up\":{\"texture\":\"#all\"},\"east\":{\"texture\":\"#all\"},\"south\":{\"texture\":\"#all\"}}}";}
 void base()throws Exception{texture(0xffff0000,0xff00ff00,0xff0000ff,0xffffffff);json("models/block/base","{\"textures\":{\"all\":\"test:block/check\"},\"elements\":["+element("[0,0,0]","[16,16,16]")+"]}");json("blockstates/cube","{\"variants\":{\"\":{\"model\":\"test:block/base\"}}}");}
 TerrainMaterials materials()throws Exception{return new TerrainMaterials(new TerrainResources(List.of(root)),List.of());}
 BufferedImage raster(TerrainMaterialProvider.Model model){var r=new ModelRaster(64,-32,-32);r.block(model,0,0,0,0xffffff);return r.image();}
 @Test void inheritedTexturesGeometryAndSurfaceAverage()throws Exception{
  base();json("models/block/slab","{\"parent\":\"test:block/base\",\"elements\":["+element("[0,0,0]","[16,8,16]")+"]}");json("blockstates/slab","{\"variants\":{\"type=bottom\":{\"model\":\"test:block/slab\"},\"type=double\":{\"model\":\"test:block/base\"}}}");
  try(var m=materials()){var slab=m.model("test:slab[type=bottom]").orElseThrow();assertEquals(.5,slab.quads().getFirst().vertices().getFirst().y());assertEquals(0,raster(slab).getRGB(32,26));assertNotEquals(0,raster(m.model("test:cube").orElseThrow()).getRGB(32,26));
   var image=raster(m.model("test:cube").orElseThrow());var colors=new HashSet<Integer>();for(int y=24;y<32;y++)for(int x=24;x<40;x++)if(image.getRGB(x,y)!=0)colors.add(image.getRGB(x,y));assertTrue(colors.size()>=4,"individual texels must survive projection");assertEquals(0xff7f7f7f,m.surface("test:cube",0xffffff,0xff112233));}
 }
 @Test void multipartPropertiesAndRotation()throws Exception{
  base();json("blockstates/fence","{\"multipart\":[{\"apply\":{\"model\":\"test:block/base\"}},{\"when\":{\"OR\":[{\"north\":\"true\"},{\"south\":\"true\"}]},\"apply\":{\"model\":\"test:block/base\",\"y\":90,\"uvlock\":true}}]}");
  try(var m=materials()){assertEquals(3,m.model("test:fence[north=false,south=false]").orElseThrow().quads().size());assertEquals(6,m.model("test:fence[north=true,south=false]").orElseThrow().quads().size());assertTrue(m.diagnostics().isEmpty());}
 }
 @Test void transparentTextureDoesNotOccludeAndDepthIndependent()throws Exception{
  var back=ModelRaster.cube(0xff0000ff);var front=ModelRaster.cube(0x80ff0000);var a=new ModelRaster(64,-32,-32);a.block(front,1,1,1,0xffffff);a.block(back,0,0,0,0xffffff);var b=new ModelRaster(64,-32,-32);b.block(back,0,0,0,0xffffff);b.block(front,1,1,1,0xffffff);
  assertArrayEquals(a.image().getRGB(0,0,64,64,null,0,64),b.image().getRGB(0,0,64,64,null,0,64));int color=a.image().getRGB(32,27);assertTrue((color&255)>0);assertTrue((color>>>16&255)>0);
 }
 @Test void cyclesMissingResourcesAndUnknownLoadersFallback()throws Exception{
  base();json("models/block/loop","{\"parent\":\"test:block/loop\"}");json("blockstates/loop","{\"variants\":{\"\":{\"model\":\"test:block/loop\"}}}");json("models/block/custom","{\"loader\":\"test:custom\"}");json("blockstates/custom","{\"variants\":{\"\":{\"model\":\"test:block/custom\"}}}");
  try(var m=materials()){assertTrue(m.model("test:loop").isEmpty());assertTrue(m.model("test:missing").isEmpty());assertTrue(m.model("test:custom").isEmpty());assertEquals("custom_loader_requires_provider",m.diagnostics().get("test:custom"));assertTrue(m.model("test:../../outside").isEmpty());assertEquals(0xff123456,m.surface("test:missing",0xffffff,0xff123456));}
 }
 @Test void resourceOrderFingerprintAndMutationDetection()throws Exception{
  base();String first;try(var m=materials()){first=m.fingerprint();texture(0xff112233,0xff112233,0xff112233,0xff112233);assertTrue(m.model("test:cube").isEmpty(),"changed resources must not enter old generation");}try(var m=materials()){assertNotEquals(first,m.fingerprint());}
  Path zip=root.resolve("override.zip");try(var out=new java.util.zip.ZipOutputStream(Files.newOutputStream(zip))){out.putNextEntry(new java.util.zip.ZipEntry("assets/test/models/block/base.json"));out.write("{\"elements\":[]}".getBytes());out.closeEntry();}
  try(var m=new TerrainMaterials(new TerrainResources(List.of(root,zip)),List.of())){assertTrue(m.model("test:cube").orElseThrow().quads().isEmpty());}
 }
 @Test void providerSupportsCustomGeometryAndChangesGeneration()throws Exception{
  base();TerrainMaterialProvider provider=new TerrainMaterialProvider(){public String id(){return "test:models";}public String revision(){return "1";}public Optional<Model> model(String id,Map<String,String> props){return id.equals("test:custom")?Optional.of(ModelRaster.cube(0xffabcdef)):Optional.empty();}};
  try(var m=new TerrainMaterials(new TerrainResources(List.of(root)),List.of(provider))){assertTrue(m.model("test:custom").isPresent());assertEquals(0xffabcdef,m.surface("test:custom",0xffffff,0));try(var plain=materials()){assertNotEquals(plain.fingerprint(),m.fingerprint());}}
 }
 @Test void texturedSnapshotRestartAndMask()throws Exception{
  base();var settings=TerrainRendererTest.settings(List.of("0,0,0,0"));UUID world=UUID.randomUUID();int[] colors=new int[16*256],ids=new int[colors.length],tints=new int[colors.length];colors[0]=colors[1]=0xff808080;ids[0]=ids[1]=1;Arrays.fill(tints,0xffffff);String etag;
  try(var m=materials();var renderer=new IsometricRenderer(root.resolve("cache"),world,settings,m)){renderer.render("minecraft:overworld",0,0,0,16,colors,List.of("test:cube"),ids,tints).get();etag=renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,0,0)).get().etag();assertEquals("8*(x-z)",renderer.projection().horizontal());assertTrue(renderer.materials().active());var masked=ImageIO.read(new java.io.ByteArrayInputStream(renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,-1,-1)).get().png()));assertEquals(0,masked.getRGB(252,252),"masked block must not contribute geometry");}
  try(var m=materials();var renderer=new IsometricRenderer(root.resolve("cache"),world,settings,m)){assertEquals(etag,renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,0,0)).get().etag());renderer.render("minecraft:overworld",0,0,0,16,new int[colors.length],List.of(),new int[colors.length],tints).get();assertNotEquals(etag,renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,0,0)).get().etag());}
 }
 @Test void elementRotationTintAndAnimationFrame()throws Exception{
  base();json("models/block/plant","{\"textures\":{\"all\":\"test:block/check\"},\"elements\":[{\"from\":[0,0,8],\"to\":[16,16,8],\"rotation\":{\"origin\":[8,8,8],\"axis\":\"y\",\"angle\":45,\"rescale\":true},\"faces\":{\"south\":{\"texture\":\"#all\",\"tintindex\":0}}}]}");json("blockstates/plant","{\"variants\":{\"\":{\"model\":\"test:block/plant\"}}}");
  try(var m=materials()){var q=m.model("test:plant").orElseThrow().quads().getFirst();assertTrue(q.tinted());assertTrue(q.vertices().stream().anyMatch(v->Math.abs(v.z()-.5)>.1));}
  Path png=root.resolve("assets/test/textures/block/check.png");var image=new BufferedImage(2,4,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<4;y++)for(int x=0;x<2;x++)image.setRGB(x,y,y<2?0xffff0000:0xff00ff00);ImageIO.write(image,"png",png.toFile());Files.writeString(Path.of(png+".mcmeta"),"{\"animation\":{\"frames\":[1,0]}}");
  try(var m=materials()){assertEquals(0xff00ff00,m.surface("test:cube",0xffffff,0));}
 }
 @Test void complexScenePreview()throws Exception{
  base();json("models/block/stairs","{\"parent\":\"test:block/base\",\"elements\":["+element("[0,0,0]","[16,8,16]")+","+element("[8,8,0]","[16,16,16]")+"]}");json("blockstates/stairs","{\"variants\":{\"\":{\"model\":\"test:block/stairs\"}}}");
  try(var m=materials()){var r=new ModelRaster(256,-128,-96);for(int z=0;z<8;z++)for(int x=0;x<8;x++)r.block(ModelRaster.cube(0xff527743),x,0,z,0xffffff);for(int z=1;z<7;z++)r.block(m.model("test:stairs").orElseThrow(),2,1,z,0xffffff);r.block(ModelRaster.cube(0x7090c0f0),4,2,4,0xffffff);r.block(m.model("test:cube").orElseThrow(),4,1,4,0xffffff);
   var image=r.image();assertTrue((image.getRGB(128,96)>>>24)>0);String output=System.getProperty("rivet.material.preview");if(output!=null){var preview=new BufferedImage(768,768,BufferedImage.TYPE_INT_RGB);var g=preview.createGraphics();g.setColor(new java.awt.Color(24,28,34));g.fillRect(0,0,768,768);g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(image.getSubimage(48,48,160,160),0,0,768,768,null);g.dispose();ImageIO.write(preview,"png",Path.of(output).toFile());}
  }
 }

 @Test void texturedModelsCrossTileSeamWithoutCracks()throws Exception{
  base();int[] colors=new int[16*256],ids=new int[colors.length],tints=new int[colors.length];colors[0]=0xff808080;ids[0]=1;Arrays.fill(tints,0xffffff);
  try(var m=materials();var renderer=new IsometricRenderer(root.resolve("seam"),UUID.randomUUID(),TerrainRendererTest.settings(List.of()),m)){renderer.render("minecraft:overworld",1,-1,0,16,colors,List.of("test:cube"),ids,tints).get();
   var left=ImageIO.read(new java.io.ByteArrayInputStream(renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,0,-1)).get().png()));var right=ImageIO.read(new java.io.ByteArrayInputStream(renderer.tile(new TerrainApi.Tile("minecraft:overworld",0,1,-1)).get().png()));assertNotEquals(0,left.getRGB(255,252));assertNotEquals(0,right.getRGB(0,252));
  }
 }

}
