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
   Input::path,failures->new IOException("Несовместимые версии JarJar: "+failures));
  for(var input:selected)read(input,false);
  if(installed&&rivetVersion!=null&&!mods.containsKey("rivet"))mods.put("rivet",new Mod("rivet",rivetVersion,"","Rivet",false));
  for(var d:dependencies){
   Mod target=mods.get(d.id());boolean present=target!=null;
   boolean matches=present&&inRange(target.version(),d.range());
   if(d.type().equals("required")){
    if(!matches)throw new IOException(d.owner().path()+": требуется "+d.id()+" "+d.range()+(present?"; найдена "+target.version():"; мод отсутствует"));
    if(!target.embedded()&&!target.component().isEmpty()&&!target.component().equals(d.owner().component()))throw new IOException(d.owner().path()+": зависимость "+d.id()+" находится в другом необязательном компоненте. Сделайте её обязательной или объедините компоненты");
   }else if(d.type().equals("incompatible")&&matches)throw new IOException(d.owner().path()+": несовместим с "+d.id()+" "+target.version());
  }
 }
 private static boolean inRange(String version,String range)throws IOException{
  if(range.isBlank()||range.equals("*"))return true;
  try{var r=VersionRange.createFromVersionSpec(range);return r.hasRestrictions()?r.containsVersion(new DefaultArtifactVersion(version)):new DefaultArtifactVersion(version).compareTo(r.getRecommendedVersion())>=0;}
  catch(Exception e){throw new IOException("Некорректный диапазон версий: "+range);}
 }
 private void read(Input input,boolean root)throws IOException{
  try(var jar=new JarFile(input.file().toFile(),false)){
   JarEntry meta=jar.getJarEntry("META-INF/neoforge.mods.toml");if(meta==null)meta=jar.getJarEntry("META-INF/mods.toml");
   if(meta==null&&root&&!installed)throw new IOException("В JAR нет метаданных NeoForge: "+input.path());
   if(meta!=null){
    var cfg=new TomlParser().parse(text(jar,meta));Object entries=cfg.get("mods");if(!(entries instanceof List<?> list)||list.isEmpty())throw new IOException("Нет списка mods: "+input.path());
    String jarVersion=jar.getManifest()==null?null:jar.getManifest().getMainAttributes().getValue("Implementation-Version");
    for(var entry:list){if(!(entry instanceof UnmodifiableConfig c))throw new IOException("Некорректные метаданные: "+input.path());String id=value(c,"modId",""),version=value(c,"version","");
     if(!id.matches("[a-z][a-z0-9_]{1,63}")||version.isBlank())throw new IOException("Некорректные id/version: "+input.path());
     if(version.equals("${file.jarVersion}"))version=jarVersion==null?"":jarVersion;
     if(version.isBlank()||version.contains("${"))throw new IOException("Не удалось определить версию "+id+": "+input.path());
     if(id.equals("rivet")&&!installed)throw new IOException("Rivet устанавливает игрок; не включайте его в сборку");
     var mod=new Mod(id,version,input.component(),input.path(),!root);var old=mods.putIfAbsent(id,mod);if(old!=null)throw new IOException("Повторяющийся мод "+id+": "+old.path()+", "+input.path());
     Object ds=cfg.get("dependencies."+id);if(ds instanceof List<?> rows)for(var row:rows)if(row instanceof UnmodifiableConfig dep){
      String side=value(dep,"side","BOTH");if(side.equals("SERVER"))continue;if(!Set.of("CLIENT","BOTH").contains(side))throw new IOException("Некорректная сторона зависимости: "+input.path());
      String type=value(dep,"type",Boolean.TRUE.equals(dep.get("mandatory"))?"required":"optional");if(!Set.of("required","optional","incompatible","discouraged").contains(type))throw new IOException("Неизвестный тип зависимости: "+input.path());dependencies.add(new Dependency(mod,value(dep,"modId",""),value(dep,"versionRange","*"),type));
     }
    }
   }
  }catch(RuntimeException e){throw new IOException("Не удалось проверить метаданные: "+input.path(),e);}
 }
 private void extract(Input input,int depth)throws IOException{
  if(depth>8||++jars>2048)throw new IOException("Слишком много вложенных JAR");
  try(var jar=new JarFile(input.file().toFile(),false)){
   JarEntry nested=jar.getJarEntry("META-INF/jarjar/metadata.json");if(nested!=null){byte[] bytes=text(jar,nested).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if(MetadataIOHandler.fromStream(new ByteArrayInputStream(bytes)).isEmpty())throw new IOException("Некорректные метаданные JarJar: "+input.path());
    metadata.put(input,bytes);var description=Json.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));var list=description.getAsJsonArray("jars");if(list.size()>128)throw new IOException("Слишком много JarJar библиотек");
    for(var item:list){String path=Json.str(item.getAsJsonObject(),"path");if(!path.startsWith("META-INF/jarjar/")||path.contains("..")||path.contains("\\")||!path.endsWith(".jar"))throw new IOException("Небезопасный путь JarJar");var child=jar.getJarEntry(path);if(child==null||child.getSize()<0||child.getSize()>128L*1024*1024)throw new IOException("Некорректный вложенный JAR");
     var childInput=new Input(input.path()+"!"+path,extractEntry(jar,child),input.component());
     if(children.computeIfAbsent(input,key->new HashMap<>()).putIfAbsent(path,childInput)!=null)throw new IOException("Повторяющийся путь JarJar: "+input.path());
     extract(childInput,depth+1);
    }
   }
  }catch(RuntimeException e){throw new IOException("Не удалось проверить метаданные: "+input.path(),e);}
 }
 /** Rivet's outer bundle is launcher metadata; the loader uses its embedded game module. */
 private Input gameModule(Input input)throws IOException{
  try(var jar=new JarFile(input.file().toFile(),false)){
   var manifest=jar.getManifest();
   if(manifest!=null&&"dev.abros.rivet.bootstrap".equals(manifest.getMainAttributes().getValue("Automatic-Module-Name"))){
    var entry=jar.getJarEntry("rivet/game.jar");if(entry==null)throw new IOException("Не найден игровой модуль Rivet: "+input.path());
    return new Input(input.path()+"!rivet/game.jar",extractEntry(jar,entry),input.component());
   }
   return input;
  }
 }
 private Path extractEntry(JarFile jar,JarEntry child)throws IOException{
  if(child.getSize()<0||child.getSize()>128L*1024*1024)throw new IOException("Некорректный вложенный JAR");
  Path temp=Files.createTempFile("rivet-mod-audit-",".jar");extracted.add(temp);
  try(var in=jar.getInputStream(child);var out=Files.newOutputStream(temp)){
   long n=0;byte[] buffer=new byte[65536];for(int count;(count=in.read(buffer))!=-1;){n+=count;nestedBytes+=count;if(n>child.getSize()||nestedBytes>512L*1024*1024)throw new IOException("Вложенные JAR превышают допустимый размер");out.write(buffer,0,count);}
   if(n!=child.getSize())throw new IOException("Повреждён вложенный JAR");
  }
  return temp;
 }
 private static String value(UnmodifiableConfig c,String key,String fallback)throws IOException{Object v=c.get(key);if(v==null)return fallback;if(v instanceof String s)return s;throw new IOException("Ожидается строка метаданных: "+key);}
 private static String text(JarFile jar,JarEntry e)throws IOException{try(var in=jar.getInputStream(e)){byte[] b=in.readNBytes(1024*1024+1);if(b.length>1024*1024)throw new IOException("Метаданные JAR превышают 1 МиБ");return new String(b,java.nio.charset.StandardCharsets.UTF_8);}}
}
