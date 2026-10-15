package dev.abros.rivet.core.api.material;

import com.google.gson.*;
import dev.abros.rivet.api.TerrainMaterialProvider;
import dev.abros.rivet.api.TerrainMaterialProvider.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;

/** Bounded, headless vanilla JSON model compiler. Custom loaders use the public provider SPI. */
public final class TerrainMaterials implements AutoCloseable {
 private final TerrainResources resources;private final List<TerrainMaterialProvider> providers;
 private Texture waterStill,waterFlow;
 private final Map<String,List<JsonElement>> plans=new LinkedHashMap<>();
 private final Map<String,Optional<Model>> models=new LinkedHashMap<>();private final Map<String,Texture> textures=new LinkedHashMap<>();
 private final LinkedHashMap<String,String> missing=new LinkedHashMap<>();private final Map<String,Long> costs=new HashMap<>();private long modelBytes;private long texturePixels,resolved,fallback;private final String fingerprint;
 public TerrainMaterials(TerrainResources resources,List<TerrainMaterialProvider> providers){
  this.resources=resources;this.providers=providers.stream().sorted(Comparator.comparing(TerrainMaterialProvider::id)).toList();
  var ids=new HashSet<String>();var revisions=new StringBuilder(resources.fingerprint());for(var p:this.providers){if(!p.id().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!ids.add(p.id())||p.revision()==null||p.revision().length()>128)throw new IllegalArgumentException("Invalid material provider");revisions.append(':').append(p.id()).append(':').append(p.revision());}
  fingerprint=dev.abros.rivet.core.Hashes.sha256((revisions+":models-v2").getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 public String fingerprint(){return fingerprint;}
 public synchronized Map<String,String> diagnostics(){return Map.copyOf(missing);}
 public synchronized long resolved(){return resolved;}public synchronized long fallback(){return fallback;}public int resources(){return resources.size();}
 public Optional<Model> model(String state){return model(state,0,0,0);}
 public synchronized Optional<Model> model(String state,int x,int y,int z){
  String key=state;var cached=models.get(key);if(cached!=null)return cached;Optional<Model> result=Optional.empty();try{
   int split=state.indexOf('[');String block=split<0?state:state.substring(0,split);id(block);var properties=new TreeMap<String,String>();
   if(split>=0){if(!state.endsWith("]"))throw new IOException("Invalid state");String text=state.substring(split+1,state.length()-1);if(!text.isEmpty())for(String pair:text.split(",")){String[] p=pair.split("=",-1);if(p.length!=2)throw new IOException("Invalid state");properties.put(p[0],p[1]);}}
   for(var provider:providers){try{result=provider.model(block,Collections.unmodifiableMap(properties));if(result.isPresent())break;}catch(RuntimeException failure){if(missing.size()<100)missing.put("provider:"+provider.id(),"provider_failed");}}
   if(result.isEmpty()){
    var plan=plans.get(state);if(plan==null){var definition=json(path(block,"blockstates",".json"));var choices=new ArrayList<JsonElement>();
     if(definition.has("variants")){boolean matched=false;for(var e:definition.getAsJsonObject("variants").entrySet())if(variant(e.getKey(),properties)){choices.add(e.getValue());matched=true;break;}if(!matched)throw new IOException("No matching variant");}
     else if(definition.has("multipart")){var list=definition.getAsJsonArray("multipart");if(list.size()>128)throw new IOException("Multipart limit");for(var part:list){var p=part.getAsJsonObject();if(!p.has("when")||condition(p.getAsJsonObject("when"),properties,0))choices.add(p.get("apply"));}}
     else throw new IOException("Unsupported blockstate definition");
     // Bound retained JSON independently of compiled geometry.
     if(plans.size()>=64)plans.remove(plans.keySet().iterator().next());plan=List.copyOf(choices);plans.put(state,plan);
    }
    var selected=new ArrayList<JsonObject>();long seed=positionSeed(x,y,z);var random=new Random(seed);var selection=new StringBuilder(state).append('|');
    boolean varying=false;for(var choice:plan){var chosen=select(choice,random);selected.add(chosen);varying|=choice.isJsonArray();selection.append(choice.isJsonArray()?choice.getAsJsonArray().asList().indexOf(chosen):0).append(';');}key=varying?selection.toString():state;cached=models.get(key);if(cached!=null)return cached;
    var parts=new ArrayList<Quad>();for(var chosen:selected)apply(chosen,parts);result=Optional.of(new Model(parts));
   }if(cost(result)>16L*1024*1024)throw new IOException("Model memory limit");resolved++;
  }catch(Exception invalid){result=Optional.empty();fallback++;if(missing.size()<100)missing.put(state,invalid instanceof FileNotFoundException?"missing_resource":invalid instanceof UnsupportedOperationException?"custom_loader_requires_provider":"invalid_or_unsupported_model");}
  long cost=cost(result)+key.length()*2L;while(!models.isEmpty()&&(models.size()>=4096||modelBytes+cost>64L*1024*1024)){String oldest=models.keySet().iterator().next();models.remove(oldest);modelBytes-=costs.remove(oldest);}models.put(key,result);costs.put(key,cost);modelBytes+=cost;return result;
 }
 /** Minecraft's positional model seed, stable for negative coordinates and across restarts. */
 public static long positionSeed(int x,int y,int z){long seed=(long)(x*3129871)^(long)z*116129781L^(long)y;return (seed*seed*42317861L+seed*11L)>>16;}
 private static JsonObject select(JsonElement choice,Random random)throws IOException{
  if(!choice.isJsonArray())return choice.getAsJsonObject();var choices=choice.getAsJsonArray();if(choices.isEmpty()||choices.size()>128)throw new IOException("Variant limit");int total=0;
  for(var c:choices){int weight=integer(c.getAsJsonObject(),"weight",1);if(weight<1||weight>1000000)throw new IOException("Variant weight");total+=weight;}
  int pick=Math.abs((int)random.nextLong())%total;if(pick<0)pick=0;for(var c:choices){var selected=c.getAsJsonObject();pick-=integer(selected,"weight",1);if(pick<0)return selected;}throw new IOException("Variant selection");
 }
 public synchronized Model fluid(String state,FluidGeometry.States states){String fluid=FluidGeometry.fluid(state);if(fluid==null)return new Model(List.of());Texture still,flow;
  try{still=texture("minecraft:block/"+fluid+"_still");flow=texture("minecraft:block/"+fluid+"_flow");}catch(IOException missing){still=flow=new Texture(1,1,new int[]{fluid.equals("water")?0xffffffff:0xffff6410});}
  if(fluid.equals("water")){if(waterStill==null){waterStill=colored(still,0xb4ffffff);waterFlow=colored(flow,0xb4ffffff);}still=waterStill;flow=waterFlow;}return FluidGeometry.model(state,states,still,flow);
 }
 private static long cost(Optional<Model> model){if(model.isEmpty())return 64;long bytes=128;var seen=Collections.newSetFromMap(new IdentityHashMap<Texture,Boolean>());for(var q:model.get().quads()){bytes+=256;if(seen.add(q.texture()))bytes+=(long)q.texture().width()*q.texture().height()*4;}return bytes;}
 private static boolean variant(String key,Map<String,String> p){if(key.isEmpty())return true;for(String pair:key.split(",")){var kv=pair.split("=",-1);if(kv.length!=2||!Objects.equals(p.get(kv[0]),kv[1]))return false;}return true;}
 private static boolean condition(JsonObject c,Map<String,String> p,int depth)throws IOException{if(depth>16)throw new IOException("Condition depth");for(var e:c.entrySet()){
  if(e.getKey().equals("OR")||e.getKey().equals("AND")){boolean or=e.getKey().equals("OR"),value=!or;for(var child:e.getValue().getAsJsonArray()){boolean hit=condition(child.getAsJsonObject(),p,depth+1);value=or?value||hit:value&&hit;}if(!value)return false;}
  else if(!Arrays.asList(e.getValue().getAsString().split("\\|")).contains(p.get(e.getKey())))return false;
 }return true;}
 private void apply(JsonObject selected,List<Quad> result)throws IOException{
  var model=inherit(selected.get("model").getAsString(),new HashSet<>());compile(model,selected,result,List.of(),0);
 }
 private void compile(JsonObject model,JsonObject selected,List<Quad> result,List<JsonElement> parentTransforms,int depth)throws IOException{
  if(depth>16)throw new IOException("Composite model depth");var transforms=new ArrayList<JsonElement>();if(model.has("transform"))transforms.add(model.get("transform"));transforms.addAll(parentTransforms);
  if(model.has("loader")){
   if(!model.get("loader").getAsString().equals("neoforge:composite"))throw new UnsupportedOperationException("Custom model");
   var children=model.getAsJsonObject("children");if(children==null||children.isEmpty()||children.size()>64)throw new IOException("Composite children limit");
   for(var entry:children.entrySet()){if(model.has("visibility")&&model.getAsJsonObject("visibility").has(entry.getKey())&&!model.getAsJsonObject("visibility").get(entry.getKey()).getAsBoolean())continue;var own=entry.getValue().getAsJsonObject();var child=own.has("parent")?inherit(own.get("parent").getAsString(),new HashSet<>()):new JsonObject();for(var field:own.entrySet()){if(field.getKey().equals("textures")&&child.has("textures")){for(var texture:field.getValue().getAsJsonObject().entrySet())child.getAsJsonObject("textures").add(texture.getKey(),texture.getValue());}else child.add(field.getKey(),field.getValue());}compile(child,selected,result,transforms,depth+1);}return;
  }
  if(!model.has("elements"))throw new IOException("No model elements");var elements=model.getAsJsonArray("elements");if(elements.size()>64)throw new IOException("Element limit");
  int rx=integer(selected,"x",0),ry=integer(selected,"y",0);if(rx%90!=0||ry%90!=0||rx<0||rx>270||ry<0||ry>270)throw new IOException("Invalid model rotation");boolean lock=selected.has("uvlock")&&selected.get("uvlock").getAsBoolean();
  JsonObject vars=model.has("textures")?model.getAsJsonObject("textures"):new JsonObject();
  for(var item:elements){var e=item.getAsJsonObject();double[] from=vector(e.getAsJsonArray("from")),to=vector(e.getAsJsonArray("to"));for(int a=0;a<3;a++)if(from[a]>to[a])throw new IOException("Inverted element");
   for(var face:e.getAsJsonObject("faces").entrySet()){
    String side=face.getKey();var f=face.getValue().getAsJsonObject();String name=f.get("texture").getAsString();var seen=new HashSet<String>();while(name.startsWith("#")){if(!seen.add(name)||seen.size()>32||!vars.has(name.substring(1)))throw new IOException("Texture reference cycle");name=vars.get(name.substring(1)).getAsString();}
    var texture=texture(name);JsonObject extra=f.has("neoforge_data")?f.getAsJsonObject("neoforge_data"):e.has("neoforge_data")?e.getAsJsonObject("neoforge_data"):new JsonObject();if(extra.has("color")){var value=extra.get("color");int color=value.getAsJsonPrimitive().isString()?(int)Long.parseUnsignedLong(value.getAsString().replace("#", "").replace("0x", ""),16):value.getAsInt();texture=colored(texture,color);}double[][] points=face(side,from,to);double[] uv=f.has("uv")?uv(f.getAsJsonArray("uv")):defaultUv(side,from,to);int rotation=integer(f,"rotation",0);if(rotation<0||rotation>270||rotation%90!=0)throw new IOException("UV rotation");
    double[][] coords={{uv[0],uv[1]},{uv[0],uv[3]},{uv[2],uv[3]},{uv[2],uv[1]}};var vertices=new ArrayList<Vertex>();
    for(int n=0;n<4;n++){double[] point=points[n].clone();if(e.has("rotation")){var r=e.getAsJsonObject("rotation");double[] origin=r.has("origin")?vector(r.getAsJsonArray("origin")):new double[3];String axis=r.get("axis").getAsString();double angle=r.get("angle").getAsDouble();if(!Double.isFinite(angle)||Math.abs(angle)>45||angle%22.5!=0)throw new IOException("Element rotation");rotate(point,origin,axis,angle,r.has("rescale")&&r.get("rescale").getAsBoolean());}
     for(var transform:transforms)ModelTransform.apply(point,transform);
     rotate(point,new double[]{8,8,8},"x",-rx,false);rotate(point,new double[]{8,8,8},"y",-ry,false);
     double[] tex=coords[Math.floorMod(n-rotation/90,4)];
     // UV-lock cancels blockstate rotation in the face's canonical texture basis.
     if(lock&&(rx!=0||ry!=0)){double[] basis=normal(side);rotate(basis,new double[3],"x",-rx,false);rotate(basis,new double[3],"y",-ry,false);double[] locked=faceUv(basis,point);double[] original=faceUv(normal(side),points[n]);double du=locked[0]-original[0],dv=locked[1]-original[1];tex=new double[]{tex[0]+du,tex[1]+dv};}
     vertices.add(new Vertex(point[0]/16,point[1]/16,point[2]/16,tex[0]/16,tex[1]/16));
    }
    result.add(new Quad(vertices,texture,integer(f,"tintindex",-1)>=0,(integer(extra,"block_light",0)==15||integer(extra,"sky_light",0)==15)?false:!e.has("shade")||e.get("shade").getAsBoolean()));if(result.size()>384)throw new IOException("Face limit");
   }
  }
 }
 private static Texture colored(Texture texture,int color){int[] pixels=texture.argb();for(int i=0;i<pixels.length;i++){int result=0;for(int shift:new int[]{24,16,8,0})result|=((pixels[i]>>>shift&255)*(color>>>shift&255)/255)<<shift;pixels[i]=result;}return new Texture(texture.width(),texture.height(),pixels);}
 private JsonObject inherit(String name,Set<String> seen)throws IOException{if(seen.size()>=32||!seen.add(name))throw new IOException("Parent cycle");var own=json(path(name,"models",".json"));JsonObject merged=own.has("parent")?inherit(own.get("parent").getAsString(),seen):new JsonObject();for(var e:own.entrySet()){if(e.getKey().equals("textures")){JsonObject t=merged.has("textures")?merged.getAsJsonObject("textures"):new JsonObject();for(var entry:e.getValue().getAsJsonObject().entrySet())t.add(entry.getKey(),entry.getValue());merged.add("textures",t);}else merged.add(e.getKey(),e.getValue());}return merged;}
 private JsonObject json(String path)throws IOException{byte[] bytes=resources.read(path);if(bytes.length>1024*1024)throw new IOException("JSON limit");String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);int depth=0;boolean quoted=false,escape=false;for(char c:text.toCharArray()){if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>64)throw new IOException("JSON depth");}else if(c=='}'||c==']')depth--; }return JsonParser.parseString(text).getAsJsonObject();}
 private Texture texture(String id)throws IOException{String p=path(id,"textures",".png");var cached=textures.get(p);if(cached!=null)return cached;
  byte[] data=resources.read(p);try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid PNG");var reader=readers.next();try{reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||width>512||height>8192||(long)width*height>4194304)throw new IOException("Texture dimensions");var image=reader.read(0);
   int frameWidth=width,frameHeight=height,index=0;if(resources.contains(p+".mcmeta")){var meta=json(p+".mcmeta");if(meta.has("animation")){var a=meta.getAsJsonObject("animation");frameWidth=integer(a,"width",width);frameHeight=integer(a,"height",a.has("width")?height:Math.min(width,height));if(a.has("frames")&&!a.getAsJsonArray("frames").isEmpty()){var first=a.getAsJsonArray("frames").get(0);index=first.isJsonObject()?first.getAsJsonObject().get("index").getAsInt():first.getAsInt();}}}
   if(frameWidth<1||frameHeight<1||frameWidth>512||frameHeight>512||width%frameWidth!=0||height%frameHeight!=0||index<0||index>=width/frameWidth*(height/frameHeight))throw new IOException("Invalid animation frame");
   var texture=new Texture(frameWidth,frameHeight,image.getRGB(index%(width/frameWidth)*frameWidth,index/(width/frameWidth)*frameHeight,frameWidth,frameHeight,null,0,frameWidth));
   while(texturePixels+(long)frameWidth*frameHeight>16*1024*1024&&!textures.isEmpty()){var key=textures.keySet().iterator().next();var old=textures.remove(key);texturePixels-=(long)old.width()*old.height();models.clear();costs.clear();modelBytes=0;}textures.put(p,texture);texturePixels+=(long)frameWidth*frameHeight;return texture;
  }finally{reader.dispose();}}
 }
 private static String id(String id)throws IOException{if(!id.contains(":"))id="minecraft:"+id;if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||id.contains(".."))throw new IOException("Invalid resource location");return id;}
 private static String path(String id,String category,String suffix)throws IOException{String[] p=id(id).split(":");return "assets/"+p[0]+"/"+category+"/"+p[1]+suffix;}
 private static int integer(JsonObject o,String k,int value){return o.has(k)?o.get(k).getAsInt():value;}
 private static double[] vector(JsonArray a)throws IOException{if(a.size()!=3)throw new IOException("Invalid vector");double[] v=new double[3];for(int i=0;i<3;i++){v[i]=a.get(i).getAsDouble();if(!Double.isFinite(v[i])||v[i]< -16||v[i]>32)throw new IOException("Invalid element bounds");}return v;}
 private static double[] uv(JsonArray a)throws IOException{if(a.size()!=4)throw new IOException("Invalid UV");double[] v=new double[4];for(int i=0;i<4;i++){v[i]=a.get(i).getAsDouble();if(!Double.isFinite(v[i])||Math.abs(v[i])>1024)throw new IOException("Invalid UV");}return v;}
 private static double[] normal(String side)throws IOException{return switch(side){case "up"->new double[]{0,1,0};case "down"->new double[]{0,-1,0};case "east"->new double[]{1,0,0};case "west"->new double[]{-1,0,0};case "south"->new double[]{0,0,1};case "north"->new double[]{0,0,-1};default->throw new IOException("Invalid face");};}
 private static double[] faceUv(double[] n,double[] p){if(n[1]>.5)return new double[]{p[0],p[2]};if(n[1]<-.5)return new double[]{p[0],16-p[2]};if(n[0]>.5)return new double[]{16-p[2],16-p[1]};if(n[0]<-.5)return new double[]{p[2],16-p[1]};if(n[2]>.5)return new double[]{p[0],16-p[1]};return new double[]{16-p[0],16-p[1]};}
 private static double[] defaultUv(String side,double[] a,double[] b)throws IOException{return switch(side){case "up"->new double[]{a[0],a[2],b[0],b[2]};case "down"->new double[]{a[0],16-b[2],b[0],16-a[2]};case "east"->new double[]{16-b[2],16-b[1],16-a[2],16-a[1]};case "west"->new double[]{a[2],16-b[1],b[2],16-a[1]};case "south"->new double[]{a[0],16-b[1],b[0],16-a[1]};case "north"->new double[]{16-b[0],16-b[1],16-a[0],16-a[1]};default->throw new IOException("Invalid face");};}
 private static double[][] face(String side,double[] a,double[] b)throws IOException{double x=a[0],y=a[1],z=a[2],X=b[0],Y=b[1],Z=b[2];return switch(side){case "up"->new double[][]{{x,Y,z},{x,Y,Z},{X,Y,Z},{X,Y,z}};case "down"->new double[][]{{x,y,Z},{x,y,z},{X,y,z},{X,y,Z}};case "east"->new double[][]{{X,Y,Z},{X,y,Z},{X,y,z},{X,Y,z}};case "west"->new double[][]{{x,Y,z},{x,y,z},{x,y,Z},{x,Y,Z}};case "south"->new double[][]{{x,Y,Z},{x,y,Z},{X,y,Z},{X,Y,Z}};case "north"->new double[][]{{X,Y,z},{X,y,z},{x,y,z},{x,Y,z}};default->throw new IOException("Invalid face");};}
 private static void rotate(double[] p,double[] o,String axis,double degrees,boolean rescale)throws IOException{int a,b;switch(axis){case "x"->{a=1;b=2;}case "y"->{a=2;b=0;}case "z"->{a=0;b=1;}default->throw new IOException("Invalid axis");}double r=Math.toRadians(degrees),c=Math.cos(r),s=Math.sin(r),x=p[a]-o[a],y=p[b]-o[b],scale=rescale?1/Math.cos(r):1;p[a]=o[a]+(x*c-y*s)*scale;p[b]=o[b]+(x*s+y*c)*scale;}
 /** Surface remains one pixel per block: average the textured upward faces by projected area. */
 public int surface(String state,int tint,int fallback){return surface(state,tint,fallback,0,0,0);}
 public int surface(String state,int tint,int fallback,int blockX,int blockY,int blockZ){var model=model(state,blockX,blockY,blockZ);if(model.isEmpty())return fallback;double red=0,green=0,blue=0,weight=0;
  for(var q:model.get().quads()){var a=q.vertices().get(0);var b=q.vertices().get(1);var c=q.vertices().get(2);double area=(b.z()-a.z())*(c.x()-a.x())-(b.x()-a.x())*(c.z()-a.z());if(area<=0)continue;
   for(int y=0;y<8;y++)for(int x=0;x<8;x++){double u=(x+.5)/8,v=(y+.5)/8;var d=q.vertices().get(3);double tu=a.u()*(1-u)*(1-v)+b.u()*(1-u)*v+c.u()*u*v+d.u()*u*(1-v),tv=a.v()*(1-u)*(1-v)+b.v()*(1-u)*v+c.v()*u*v+d.v()*u*(1-v);int color=q.texture().sample(tu,tv);double w=area*(color>>>24)/255.;red+=(color>>>16&255)*w*(q.tinted()?(tint>>>16&255)/255.:1);green+=(color>>>8&255)*w*(q.tinted()?(tint>>>8&255)/255.:1);blue+=(color&255)*w*(q.tinted()?(tint&255)/255.:1);weight+=w;}
  }if(weight==0)return fallback;double shade=(tint>>>24)==0?1:(tint>>>24)/255.;return 0xff000000|(int)(red/weight*shade)<<16|(int)(green/weight*shade)<<8|(int)(blue/weight*shade);
 }
 @Override public synchronized void close(){models.clear();plans.clear();costs.clear();modelBytes=0;textures.clear();resources.close();}
}
