package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.api.TerrainMaterialProvider.*;
import dev.abros.rivet.core.api.material.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

class TerrainGeometryTest {
 @TempDir Path root;
 TerrainMaterialsTest fixture()throws Exception{var f=new TerrainMaterialsTest();f.root=root;f.base();return f;}
 @Test void weightedVariantsUseCoordinatesAndReuseCompiledSelection()throws Exception{
  var f=fixture();f.json("models/block/short","{\"parent\":\"test:block/base\",\"elements\":["+f.element("[0,0,0]","[16,8,16]")+"]}");
  f.json("blockstates/random","{\"variants\":{\"\":[{\"model\":\"test:block/base\",\"weight\":2},{\"model\":\"test:block/short\",\"weight\":1}]}}");
  var heights=new HashMap<Integer,Double>();try(var m=f.materials()){
   var identities=Collections.newSetFromMap(new IdentityHashMap<Model,Boolean>());for(int x=-80;x<80;x++){var model=m.model("test:random",x,70,-9).orElseThrow();identities.add(model);heights.put(x,model.quads().getFirst().vertices().getFirst().y());}assertEquals(2,identities.size());assertEquals(2,m.resolved());
  }try(var m=f.materials()){for(var e:heights.entrySet())assertEquals(e.getValue(),m.model("test:random",e.getKey(),70,-9).orElseThrow().quads().getFirst().vertices().getFirst().y());}
 }
 @Test void rootTransformsCompositeAndFaceColors()throws Exception{
  var f=fixture();f.json("models/block/composite","""
   {"loader":"neoforge:composite","transform":{"origin":"corner","scale":[0.5,0.5,0.5],"translation":[0.25,0,0.25]},"children":{"body":{"parent":"test:block/base"},"cap":{"textures":{"all":"test:block/check"},"elements":[{"from":[0,16,0],"to":[16,16,16],"faces":{"up":{"texture":"#all","neoforge_data":{"color":"FF00FF00","block_light":15}}}}]}}}
   """);f.json("blockstates/composite","{\"variants\":{\"\":{\"model\":\"test:block/composite\"}}}");
  try(var m=f.materials()){var model=m.model("test:composite").orElseThrow();assertEquals(4,model.quads().size());var p=model.quads().getFirst().vertices().getFirst();assertEquals(.25,p.x());assertEquals(.5,p.y());assertEquals(.25,p.z());assertFalse(model.quads().getLast().shade());assertEquals(0xff00ff00,model.quads().getLast().texture().sample(.75,.75));}
 }
 @Test void rootMatrixAppliesBeforeBlockstateRotationAndRejectsEscapingGeometry()throws Exception{
  var f=fixture();f.json("models/block/matrix","{\"parent\":\"test:block/base\",\"transform\":[[1,0,0,0.25],[0,0.5,0,0],[0,0,1,0]]}");
  f.json("blockstates/matrix","{\"variants\":{\"\":{\"model\":\"test:block/matrix\",\"y\":90}}}");
  f.json("models/block/escape","{\"parent\":\"test:block/base\",\"transform\":{\"translation\":[100,0,0]}}");f.json("blockstates/escape","{\"variants\":{\"\":{\"model\":\"test:block/escape\"}}}");
  try(var m=f.materials()){var v=m.model("test:matrix").orElseThrow().quads().getFirst().vertices().getFirst();assertEquals(1,v.x(),1e-9);assertEquals(.5,v.y(),1e-9);assertEquals(.25,v.z(),1e-9);assertTrue(m.model("test:escape").isEmpty());}
 }
 @Test void surfaceDepthTexturesCutoutsAndAlphaAreNotAverages()throws Exception{
  var texture=new Texture(2,2,new int[]{0xffff0000,0,0x8000ff00,0xff0000ff});var top=ModelRaster.cube(0xffffffff).quads().getFirst();var model=new Model(List.of(new Quad(top.vertices(),texture,false,false)));var raster=new ModelRaster(16,0,0,true);raster.block(model,0,1,0,0xffffff);raster.block(ModelRaster.cube(0xffffff00),0,0,0,0xffffff);var image=raster.image();assertEquals(0xffff0000,image.getRGB(2,2));assertEquals(0xffffff00,image.getRGB(13,2));assertEquals(0xff0000ff,image.getRGB(13,13));int blend=image.getRGB(2,13);assertTrue((blend>>>16&255)>0);assertEquals(255,blend>>>8&255);
 }
 @Test void liquidsHaveSlopesAndNoInternalSurface()throws Exception{
  var texture=new Texture(1,1,new int[]{0x900000ff});String water="minecraft:water[level=0]";
  FluidGeometry.States pool=(x,y,z)->y==0?water:null;var source=FluidGeometry.model(water,pool,texture,texture);assertEquals(1,source.quads().size());assertEquals(8./9-.001,source.quads().getFirst().vertices().getFirst().y(),1e-9);
  var submerged=FluidGeometry.model(water,(x,y,z)->water,texture,texture);assertTrue(submerged.quads().isEmpty());
  var slope=FluidGeometry.model(water,(x,y,z)->y==0?(x>0?"minecraft:water[level=7]":water):null,texture,texture);var v=slope.quads().getFirst().vertices();assertTrue(v.get(0).y()>v.get(3).y());
  assertEquals("water",FluidGeometry.fluid("minecraft:oak_slab[type=bottom,waterlogged=true]"));assertFalse(FluidGeometry.pure("minecraft:oak_slab[waterlogged=true]"));
 }
 @Test void detailedSurfacePersistsAndClipsOverhangingGeometryAtMask()throws Exception{
  var f=fixture();f.json("models/block/overhang","{\"parent\":\"test:block/base\",\"elements\":["+f.element("[0,0,0]","[32,16,16]")+"]}");f.json("blockstates/overhang","{\"variants\":{\"\":{\"model\":\"test:block/overhang\"}}}");
  var settings=TerrainRendererTest.settings(List.of("1,0,1,0"));UUID world=UUID.randomUUID();int[] data=new int[4096],ids=new int[4096],tints=new int[4096];data[0]=0xff808080;ids[0]=1;String tag;
  try(var m=f.materials();var r=new IsometricRenderer(root.resolve("out"),world,settings,m,"",true)){
   r.render("minecraft:overworld",0,0,0,16,data,List.of("test:overhang"),ids,tints).get();assertEquals(256,r.metadata().tilePixels());assertEquals("16*x",r.projection().horizontal());var image=r.tile(TerrainRendererTest.tile(0,0,0)).get();tag=image.etag();var pixels=ImageIO.read(new ByteArrayInputStream(image.png()));assertNotEquals(0,pixels.getRGB(8,8));assertEquals(0,pixels.getRGB(24,8));
   var overview=ImageIO.read(new ByteArrayInputStream(r.tile(TerrainRendererTest.tile(1,0,0)).get().png()));assertEquals(0,overview.getRGB(12,4));
  }try(var m=f.materials();var r=new IsometricRenderer(root.resolve("out"),world,settings,m,"",true)){assertEquals(tag,r.tile(TerrainRendererTest.tile(0,0,0)).get().etag());}
 }
 @Test void fluidSeamUsesNeighborSnapshotAndRefreshesOnChange()throws Exception{
  var f=fixture();var settings=TerrainRendererTest.settings(List.of());int[] water=new int[4096],ids=new int[4096],tints=new int[4096];Arrays.fill(tints,0x90c0ff);Arrays.fill(water,0,256,0xb490c0ff);Arrays.fill(ids,0,256,1);
  try(var m=f.materials();var r=new IsometricRenderer(root.resolve("out"),UUID.randomUUID(),settings,m,"",true)){
   r.render("minecraft:overworld",0,0,0,16,water,List.of("minecraft:water[level=0]"),ids,tints).get();r.render("minecraft:overworld",1,0,0,16,water,List.of("minecraft:water[level=0]"),ids,tints).get();
   var a=ImageIO.read(new ByteArrayInputStream(r.tile(TerrainRendererTest.tile(0,0,0)).get().png()));var b=ImageIO.read(new ByteArrayInputStream(r.tile(TerrainRendererTest.tile(0,1,0)).get().png()));assertEquals(a.getRGB(255,128),b.getRGB(0,128));assertNotEquals(0,a.getRGB(255,128));
  }
 }
}
