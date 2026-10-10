package dev.abros.rivet.core.pack;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import dev.abros.rivet.core.Json;
import net.neoforged.jarjar.selection.JarSelector;
import net.neoforged.jarjar.metadata.MetadataIOHandler;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Checks client-required dependencies without loading code, including bundled JarJar mods. */
final class PackModAudit {
 record Input(String path,Path file,String component){}
 private record Mod(String id,String version,String component,String path,boolean embedded){}
 private record Dependency(Mod owner,String id,String range,String type){}
 private final Map<String,Mod> mods=new TreeMap<>();
 private final List<Dependency> dependencies=new ArrayList<>();
 private boolean installed;private String rivetVersion;
 private int jars;private long nestedBytes;
 private final Map<Input,byte[]> metadata=new HashMap<>();
 private final Map<Input,Map<String,Input>> children=new HashMap<>();
 private final List<Path> extracted=new ArrayList<>();
 static void validate(List<Input> inputs,String minecraft,String loader)throws IOException{
  validate(inputs,minecraft,loader,false,null);
 }
 static void validateInstalled(List<Input> inputs,String minecraft,String loader,String rivetVersion)throws IOException{
  validate(inputs,minecraft,loader,true,rivetVersion);
 }
 private static void validate(List<Input> sources,String minecraft,String loader,boolean installed,String rivetVersion)throws IOException{
  var audit=new PackModAudit();audit.installed=installed;audit.rivetVersion=rivetVersion;
  try{
   var inputs=new ArrayList<Input>();
   for(var source:sources){var input=installed?audit.gameModule(source):source;inputs.add(input);audit.extract(input,0);}
   audit.checkSelection(inputs,minecraft,loader);
   // Optional copies must not hide missing dependencies in the mandatory pack or another component.
   var components=new TreeSet<String>();for(var input:inputs)if(!input.component().isEmpty())components.add(input.component());
   if(!components.isEmpty()){
    audit.checkSelection(inputs.stream().filter(i->i.component().isEmpty()).toList(),minecraft,loader);
    for(String component:components)audit.checkSelection(inputs.stream().filter(i->i.component().isEmpty()||i.component().equals(component)).toList(),minecraft,loader);
   }
  }finally{for(Path temp:audit.extracted)Files.deleteIfExists(temp);}
 }
 private void checkSelection(List<Input> inputs,String minecraft,String loader)throws IOException{
  mods.clear();dependencies.clear();
  mods.put("minecraft",new Mod("minecraft",minecraft,"","Minecraft",false));
  mods.put("neoforge",new Mod("neoforge",loader,"","NeoForge",false));
  for(var input:inputs)read(input,true);
  var selected=JarSelector.detectAndSelect(inputs,
   (input,path)->Optional.ofNullable(metadata.get(input)).map(bytes->(InputStream)new ByteArrayInputStream(bytes)),
   (input,path)->Optional.ofNullable(children.getOrDefault(input,Map.of()).get(path.toString().replace('\\','/'))),
   Input::path,failures->new IOException(dev.abros.rivet.core.Messages.text("rivet.core.incompatible_jarjar_versions_1dbc311e")+failures));
  for(var input:selected)read(input,false);
  if(installed&&rivetVersion!=null&&!mods.containsKey("rivet"))mods.put("rivet",new Mod("rivet",rivetVersion,"",dev.abros.rivet.core.Messages.text("key.categories.rivet"),false));
  for(var d:dependencies){
   Mod target=mods.get(d.id());boolean present=target!=null;
   boolean matches=present&&matches(target,d.range(),minecraft);
   if(d.type().equals("required")){
    if(!matches)throw new IOException(d.owner().path()+dev.abros.rivet.core.Messages.text("rivet.core.requires_ec4daf9d")+d.id()+" "+d.range()+(present?dev.abros.rivet.core.Messages.text("rivet.core.found_950207b5")+target.version():dev.abros.rivet.core.Messages.text("rivet.core.mod_missing_a3f77526")));
    if(!target.embedded()&&!target.component().isEmpty()&&!target.component().equals(d.owner().component()))throw new IOException(d.owner().path()+dev.abros.rivet.core.Messages.text("rivet.core.dependency_17ae1988")+d.id()+dev.abros.rivet.core.Messages.text("rivet.core.is_in_another_optional_component_make_67256f36"));
   }else if(d.type().equals("incompatible")&&matches)throw new IOException(d.owner().path()+dev.abros.rivet.core.Messages.text("rivet.core.incompatible_with_3dc62610")+d.id()+" "+target.version());
  }
 }
 /** Mirrors FancyModLoader's VersionSupportMatrix for the supported Minecraft branch. */
 private static boolean matches(Mod target,String range,String minecraft)throws IOException{
  if(inRange(target.version(),range))return true;
  if(!minecraft.equals("1.21.1"))return false;
  String alias=switch(target.id()){case "minecraft"->"1.21";case "neoforge"->"21.0.166";default->"";};
  if(alias.isEmpty())return false;
  try{return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(alias));}
  catch(Exception e){throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_version_range_5a79f2ae")+range);}
 }
 private static boolean inRange(String version,String range)throws IOException{
  if(range.isBlank()||range.equals("*"))return true;
  try{var r=VersionRange.createFromVersionSpec(range);return r.hasRestrictions()?r.containsVersion(new DefaultArtifactVersion(version)):new DefaultArtifactVersion(version).compareTo(r.getRecommendedVersion())>=0;}
  catch(Exception e){throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_version_range_5a79f2ae")+range);}
 }
 private void read(Input input,boolean root)throws IOException{
  try(var jar=new JarFile(input.file().toFile(),false)){
   // NeoForge library containers expose their actual mods through JarJar, not outer TOML.
   var manifest=jar.getManifest();if(manifest!=null&&"LIBRARY".equals(manifest.getMainAttributes().getValue("FMLModType")))return;
   JarEntry meta=jar.getJarEntry("META-INF/neoforge.mods.toml");if(meta==null)meta=jar.getJarEntry("META-INF/mods.toml");
   if(meta==null&&root&&!installed)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.no_neoforge_metadata_in_jar_bb4558e9")+input.path());
   if(meta!=null){
    var cfg=new TomlParser().parse(text(jar,meta));Object entries=cfg.get("mods");if(!(entries instanceof List<?> list)||list.isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.missing_mods_list_3fa78dbe")+input.path());
    String jarVersion=jar.getManifest()==null?null:jar.getManifest().getMainAttributes().getValue("Implementation-Version");
    for(var entry:list){if(!(entry instanceof UnmodifiableConfig c))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_metadata_ea00af31")+input.path());String id=value(c,"modId",""),version=value(c,"version","");
     if(!id.matches("[a-z][a-z0-9_]{1,63}")||version.isBlank())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_id_version_281cb794")+input.path());
     if(version.equals("${file.jarVersion}"))version=jarVersion==null?"":jarVersion;
     if(version.isBlank()||version.contains("${"))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.could_not_determine_version_da464051")+id+": "+input.path());
     if(id.equals("rivet")&&!installed)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.players_install_rivet_themselves_do_not_252384a5"));
     var mod=new Mod(id,version,input.component(),input.path(),!root);var old=mods.putIfAbsent(id,mod);if(old!=null)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.duplicate_mod_55bd6b39")+id+": "+old.path()+", "+input.path());
     Object ds=cfg.get("dependencies."+id);if(ds instanceof List<?> rows)for(var row:rows)if(row instanceof UnmodifiableConfig dep){
      String side=value(dep,"side","BOTH");if(side.equals("SERVER"))continue;if(!Set.of("CLIENT","BOTH").contains(side))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_dependency_side_28a88aec")+input.path());
      String type=value(dep,"type",Boolean.FALSE.equals(dep.get("mandatory"))?"optional":"required").toLowerCase(Locale.ROOT);if(!Set.of("required","optional","incompatible","discouraged").contains(type))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_dependency_type_3acaf60c")+input.path());dependencies.add(new Dependency(mod,value(dep,"modId",""),value(dep,"versionRange","*"),type));
     }
    }
   }
  }catch(RuntimeException e){throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.could_not_verify_metadata_bf099598")+input.path(),e);}
 }
 private void extract(Input input,int depth)throws IOException{
  if(depth>8||++jars>2048)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_nested_jars_025b1225"));
  try(var jar=new JarFile(input.file().toFile(),false)){
   JarEntry nested=jar.getJarEntry("META-INF/jarjar/metadata.json");if(nested!=null){byte[] bytes=text(jar,nested).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if(MetadataIOHandler.fromStream(new ByteArrayInputStream(bytes)).isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_jarjar_metadata_89819fca")+input.path());
    metadata.put(input,bytes);var description=Json.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));var list=description.getAsJsonArray("jars");if(list.size()>128)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_jarjar_libraries_ee838af5"));
    for(var item:list){String path=Json.str(item.getAsJsonObject(),"path");if(!safeNestedPath(path))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unsafe_jarjar_path_46bfb773")+input.path()+" → "+Json.GSON.toJson(path));var child=jar.getJarEntry(path);if(child==null||child.isDirectory()||child.getSize()<0||child.getSize()>128L*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_nested_jar_261b908b")+input.path()+" → "+Json.GSON.toJson(path));
     var childInput=new Input(input.path()+"!"+path,extractEntry(jar,child),input.component());
     if(children.computeIfAbsent(input,key->new HashMap<>()).putIfAbsent(path,childInput)!=null)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.duplicate_jarjar_path_3e8894b5")+input.path());
     extract(childInput,depth+1);
    }
   }
  }catch(RuntimeException e){throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.could_not_verify_metadata_bf099598")+input.path(),e);}
 }
 /** JarJar metadata may reference any relative entry inside the containing archive. */
 private static boolean safeNestedPath(String path){
  if(path.isEmpty()||path.length()>2048||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.codePoints().anyMatch(Character::isISOControl)||!path.toLowerCase(Locale.ROOT).endsWith(".jar"))return false;
  for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;
  return true;
 }
 /** Rivet's outer bundle is launcher metadata; the loader uses its embedded game module. */
 private Input gameModule(Input input)throws IOException{
  try(var jar=new JarFile(input.file().toFile(),false)){
   var manifest=jar.getManifest();
   if(manifest!=null&&"dev.abros.rivet.bootstrap".equals(manifest.getMainAttributes().getValue("Automatic-Module-Name"))){
    var entry=jar.getJarEntry("rivet/game.jar");if(entry==null)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.rivet_game_module_not_found_8801385d")+input.path());
    return new Input(input.path()+"!rivet/game.jar",extractEntry(jar,entry),input.component());
   }
   return input;
  }
 }
 private Path extractEntry(JarFile jar,JarEntry child)throws IOException{
  if(child.getSize()<0||child.getSize()>128L*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_nested_jar_0b5cb6f8"));
  Path temp=Files.createTempFile("rivet-mod-audit-",".jar");extracted.add(temp);
  try(var in=jar.getInputStream(child);var out=Files.newOutputStream(temp)){
   long n=0;byte[] buffer=new byte[65536];for(int count;(count=in.read(buffer))!=-1;){n+=count;nestedBytes+=count;if(n>child.getSize()||nestedBytes>512L*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.nested_jars_exceed_the_size_limit_a713d43e"));out.write(buffer,0,count);}
   if(n!=child.getSize())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.corrupt_nested_jar_86217778"));
  }
  return temp;
 }
 private static String value(UnmodifiableConfig c,String key,String fallback)throws IOException{Object v=c.get(key);if(v==null)return fallback;if(v instanceof String s)return s;throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.expected_metadata_string_7071173d")+key);}
 private static String text(JarFile jar,JarEntry e)throws IOException{try(var in=jar.getInputStream(e)){byte[] b=in.readNBytes(1024*1024+1);if(b.length>1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.jar_metadata_exceeds_1_mib_ed63470d"));return new String(b,java.nio.charset.StandardCharsets.UTF_8);}}
}
