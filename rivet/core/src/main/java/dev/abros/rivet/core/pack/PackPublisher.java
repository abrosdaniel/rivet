package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Explicit publication of a preparation tree; readers never serve that mutable tree. */
public final class PackPublisher {
 private final Path source,data;
 private final PackSources sources;
 public PackPublisher(Path source,Path data)throws IOException{this(source,data,new PackSources());}
 PackPublisher(Path source,Path data,PackSources sources)throws IOException{
  this.sources=sources;
  this.source=source.toAbsolutePath().normalize();this.data=data.toAbsolutePath().normalize();
  if(this.source.startsWith(this.data)||this.data.startsWith(this.source))throw new IOException("Папки подготовки и снимков пересекаются");
  safe(this.source);safe(this.data);Files.createDirectories(this.source);Files.createDirectories(this.data.resolve("objects"));Files.createDirectories(this.data.resolve("publications"));
  var settings=this.source.resolve("pack.toml");safe(settings);
  String header="# Ключ CurseForge хранится только в переменной окружения сервера.\ncurseforgeKeyEnv = \"RIVET_CURSEFORGE_KEY\"\n\n";
  if(!Files.exists(settings))Files.writeString(settings,header+"# Все файлы обязательны. Конфиги устанавливаются только при отсутствии.\n# Необязательные компоненты задаются через [[components]].\n# Ссылки: [[mods]] и url на конкретную версию Modrinth или файл CurseForge.\n");
  else {if(Files.size(settings)>1024*1024)throw new IOException("pack.toml превышает 1 МиБ");String original=Files.readString(settings);var cfg=new TomlParser().parse(original);keys(cfg,"components","configs","mods","curseforgeKeyEnv");if(!cfg.contains("curseforgeKeyEnv")){Path backups=this.data.resolve("settings-backups");safe(backups);Files.createDirectories(backups);Files.copy(settings,backups.resolve("pack-"+UUID.randomUUID()+".toml"));Path tmp=Files.createTempFile(this.source,"settings-",".tmp");try{Files.writeString(tmp,header+original);Json.move(tmp,settings);}finally{Files.deleteIfExists(tmp);}}}

 }
 public static void safe(Path p)throws IOException{for(Path q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("Символическая ссылка запрещена: "+q.getFileName());}
 private static String str(UnmodifiableConfig c,String key,String fallback){Object x=c.get(key);if(x==null)return fallback;if(!(x instanceof String s))throw new IllegalArgumentException("Ожидается строка: "+key);return s;}
 private static List<UnmodifiableConfig> rows(UnmodifiableConfig c,String key){Object v=c.get(key);if(v==null)return List.of();if(!(v instanceof List<?> list)||list.stream().anyMatch(x->!(x instanceof UnmodifiableConfig)))throw new IllegalArgumentException("Ожидается список: "+key);return list.stream().map(x->(UnmodifiableConfig)x).toList();}
 private static void keys(UnmodifiableConfig c,String... allowed){var names=Set.of(allowed);for(var e:c.entrySet())if(!names.contains(e.getKey()))throw new IllegalArgumentException("Неизвестный параметр сборки: "+e.getKey());}
 public synchronized PackManifest prepare(String minecraft,String loader)throws IOException{
  safe(source);var configFile=source.resolve("pack.toml");safe(configFile);if(Files.size(configFile)>1024*1024)throw new IOException("pack.toml превышает 1 МиБ");
  var cfg=new TomlParser().parse(Files.readString(configFile));keys(cfg,"components","configs","mods","curseforgeKeyEnv");
  var components=new ArrayList<PackManifest.Component>();var membership=new HashMap<String,String>();var policies=new HashMap<String,String>();
  for(var c:rows(cfg,"components")){keys(c,"id","name","description","optional","selected","files");String id=str(c,"id","");if(!Boolean.TRUE.equals(c.get("optional")))throw new IllegalArgumentException("Компоненты должны быть optional = true");Object selected=c.get("selected");if(selected!=null&&!(selected instanceof Boolean))throw new IllegalArgumentException("selected должен быть true/false");components.add(new PackManifest.Component(id,str(c,"name",id),str(c,"description",""),selected==null||(Boolean)selected));
   Object entries=c.get("files");if(!(entries instanceof List<?> names))throw new IllegalArgumentException("Укажите files компонента");for(Object v:names){if(!(v instanceof String path))throw new IllegalArgumentException("Ожидается путь файла");SafePaths.validate(path);if(membership.putIfAbsent(SafePaths.key(path),id)!=null)throw new IllegalArgumentException("Файл принадлежит нескольким компонентам: "+path);}}
  for(var c:rows(cfg,"configs")){keys(c,"path","update");String path=str(c,"path","");SafePaths.validate(path);String mode=str(c,"update","missing");if(!Planner.configurable(path)||!Set.of("missing","replace").contains(mode)||policies.putIfAbsent(SafePaths.key(path),mode)!=null)throw new IllegalArgumentException("Некорректное правило конфига: "+path);}
  var external=sources.resolve(rows(cfg,"mods"),str(cfg,"curseforgeKeyEnv","RIVET_CURSEFORGE_KEY"),data.resolve("sources"),minecraft);
  var componentIds=new HashSet<String>();for(var component:components)componentIds.add(component.id());
  for(var mod:external)if(!mod.component().isEmpty()){if(!componentIds.contains(mod.component()))throw new IOException("Неизвестный компонент: "+mod.component());String old=membership.putIfAbsent(SafePaths.key(mod.path()),mod.component());if(old!=null&&!old.equals(mod.component()))throw new IOException("Файл принадлежит нескольким компонентам: "+mod.path());}
  var files=new ArrayList<PackManifest.Entry>();var found=new HashSet<String>();var inputs=new ArrayList<PackModAudit.Input>();
  try(var walk=Files.walk(source)){for(var file:walk.sorted().toList()){
   safe(file);if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))continue;String path=source.relativize(file).toString().replace('\\','/');if(path.equals("pack.toml"))continue;
   add(file,path,membership,policies,files,found,inputs,"");
  }}
  for(var mod:external)add(mod.file(),mod.path(),membership,policies,files,found,inputs,mod.url());
  if(inputs.size()>512)throw new IOException("В сборке больше 512 модов");
  PackModAudit.validate(inputs,minecraft,loader);
  files.sort(Comparator.comparing(PackManifest.Entry::path));
  if(!found.containsAll(membership.keySet())||!found.containsAll(policies.keySet()))throw new IOException("Настройки ссылаются на отсутствующие файлы");
  var manifest=new PackManifest(minecraft,loader,components,files);byte[] bytes=manifest.bytes();if(bytes.length>8*1024*1024)throw new IOException("Описание слишком большое");var publication=data.resolve("publications").resolve(manifest.hash()+".json");safe(publication);if(!Files.exists(publication)){Path pending=Files.createTempFile(publication.getParent(),"publication-",".tmp");try{Files.write(pending,bytes);Json.move(pending,publication);}finally{Files.deleteIfExists(pending);}}return manifest;
 }
 private void add(Path file,String path,Map<String,String> membership,Map<String,String> policies,List<PackManifest.Entry> files,Set<String> found,List<PackModAudit.Input> inputs,String url)throws IOException{
  if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Публикация отменена");
  SafePaths.validate(path);safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Необычный файл: "+path);if(files.size()>=10000)throw new IOException("Слишком много файлов");
  if(!found.add(SafePaths.key(path)))throw new IOException("Повторяющийся путь: "+path);
  long size=Files.size(file);if(size>8L*1024*1024*1024)throw new IOException("Файл превышает 8 ГиБ: "+path);String hash=Hashes.sha256(file);var object=object(hash);
  if(!Files.exists(object)){var tmp=Files.createTempFile(data.resolve("objects"),"publish-",".tmp");try{Files.copy(file,tmp,StandardCopyOption.REPLACE_EXISTING);if(Files.size(tmp)!=size||!Hashes.sha256(tmp).equals(hash))throw new IOException("Файл изменился во время публикации: "+path);Json.move(tmp,object);}finally{Files.deleteIfExists(tmp);}}
  else if(!Hashes.sha256(object).equals(hash))throw new IOException("Повреждён опубликованный файл");
  String component=membership.getOrDefault(SafePaths.key(path),"");files.add(new PackManifest.Entry(path,hash,size,component,policies.getOrDefault(SafePaths.key(path),Planner.configurable(path)?"missing":"replace"),url));
  if(path.startsWith("mods/")&&path.endsWith(".jar"))inputs.add(new PackModAudit.Input(path,object,component));
 }
 public Path object(String hash)throws IOException{Hashes.check(hash);Path p=data.resolve("objects").resolve(hash);safe(p);return p;}
 public synchronized void activate(PackManifest manifest,boolean onRestart)throws IOException{load(manifest.hash());Json.write(data.resolve(onRestart?"pending.json":"current.json"),Map.of("hash",manifest.hash()));if(!onRestart)Files.deleteIfExists(data.resolve("pending.json"));}
 public synchronized void startup()throws IOException{Path pending=data.resolve("pending.json");safe(pending);if(Files.exists(pending)){String hash=Json.str(Json.read(pending),"hash");load(hash);Json.write(data.resolve("current.json"),Map.of("hash",hash));Files.delete(pending);}}
 public PackManifest load(String hash)throws IOException{Hashes.check(hash);Path file=data.resolve("publications").resolve(hash+".json");safe(file);if(Files.size(file)>8*1024*1024)throw new IOException("Описание слишком большое");byte[] bytes=Files.readAllBytes(file);var manifest=PackManifest.parse(bytes);if(!manifest.hash().equals(hash))throw new IOException("Снимок повреждён");return manifest;}
 public PackManifest current()throws IOException{Path file=data.resolve("current.json");safe(file);return Files.exists(file)?load(Json.str(Json.read(file),"hash")):null;}
}
