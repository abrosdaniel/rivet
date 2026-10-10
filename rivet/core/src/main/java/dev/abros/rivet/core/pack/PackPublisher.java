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
  if(this.source.startsWith(this.data)||this.data.startsWith(this.source))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.staging_and_snapshot_folders_overlap_32a48dfc"));
  safe(this.source);safe(this.data);Files.createDirectories(this.source);Files.createDirectories(this.data.resolve("objects"));Files.createDirectories(this.data.resolve("publications"));
  var settings=this.source.resolve("pack.toml");safe(settings);
  String header=dev.abros.rivet.core.Messages.text("rivet.core.the_curseforge_key_is_stored_only_1c0f54a5");
  if(!Files.exists(settings))Files.writeString(settings,header+dev.abros.rivet.core.Messages.text("rivet.core.all_files_are_required_configs_are_1f01f45e"));
  else {if(Files.size(settings)>1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.pack_toml_exceeds_1_mib_6da25a20"));String original=Files.readString(settings);var cfg=new TomlParser().parse(original);keys(cfg,"components","configs","mods","curseforgeKeyEnv");if(!cfg.contains("curseforgeKeyEnv")){Path backups=this.data.resolve("settings-backups");safe(backups);Files.createDirectories(backups);Files.copy(settings,backups.resolve("pack-"+UUID.randomUUID()+".toml"));Path tmp=Files.createTempFile(this.source,"settings-",".tmp");try{Files.writeString(tmp,header+original);Json.move(tmp,settings);}finally{Files.deleteIfExists(tmp);}}}

 }
 public static void safe(Path p)throws IOException{for(Path q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.symbolic_links_are_not_allowed_223cd181")+q.getFileName());}
 private static String str(UnmodifiableConfig c,String key,String fallback){Object x=c.get(key);if(x==null)return fallback;if(!(x instanceof String s))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.expected_string_d791e188")+key);return s;}
 private static List<UnmodifiableConfig> rows(UnmodifiableConfig c,String key){Object v=c.get(key);if(v==null)return List.of();if(!(v instanceof List<?> list)||list.stream().anyMatch(x->!(x instanceof UnmodifiableConfig)))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.expected_list_c001feaf")+key);return list.stream().map(x->(UnmodifiableConfig)x).toList();}
 private static void keys(UnmodifiableConfig c,String... allowed){var names=Set.of(allowed);for(var e:c.entrySet())if(!names.contains(e.getKey()))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_modpack_setting_98d240bd")+e.getKey());}
 private static void checkCancelled()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException(dev.abros.rivet.core.Messages.text("rivet.core.publication_cancelled_fe3f2b95"));}
 public synchronized PackManifest prepare(String minecraft,String loader)throws IOException{
  checkCancelled();
  safe(source);var configFile=source.resolve("pack.toml");safe(configFile);if(Files.size(configFile)>1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.pack_toml_exceeds_1_mib_6da25a20"));
  var cfg=new TomlParser().parse(Files.readString(configFile));keys(cfg,"components","configs","mods","curseforgeKeyEnv");
  var components=new ArrayList<PackManifest.Component>();var membership=new HashMap<String,String>();var policies=new HashMap<String,String>();
  for(var c:rows(cfg,"components")){keys(c,"id","name","description","optional","selected","files");String id=str(c,"id","");if(!Boolean.TRUE.equals(c.get("optional")))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.components_must_have_optional_true_76d6b4aa"));Object selected=c.get("selected");if(selected!=null&&!(selected instanceof Boolean))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.selected_must_be_true_false_8b2e7802"));components.add(new PackManifest.Component(id,str(c,"name",id),str(c,"description",""),selected==null||(Boolean)selected));
   Object entries=c.get("files");if(!(entries instanceof List<?> names))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.specify_the_component_s_files_266e61ac"));for(Object v:names){if(!(v instanceof String path))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.expected_file_path_fc690938"));SafePaths.validate(path);if(membership.putIfAbsent(SafePaths.key(path),id)!=null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.file_belongs_to_multiple_components_78b026c7")+path);}}
  for(var c:rows(cfg,"configs")){keys(c,"path","update");String path=str(c,"path","");SafePaths.validate(path);String mode=str(c,"update","missing");if(!Planner.configurable(path)||!Set.of("missing","replace").contains(mode)||policies.putIfAbsent(SafePaths.key(path),mode)!=null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_configuration_rule_e09d3331")+path);}
  var external=sources.resolve(rows(cfg,"mods"),str(cfg,"curseforgeKeyEnv","RIVET_CURSEFORGE_KEY"),data.resolve("sources"),minecraft);
  var componentIds=new HashSet<String>();for(var component:components)componentIds.add(component.id());
  for(var mod:external)if(!mod.component().isEmpty()){if(!componentIds.contains(mod.component()))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_component_4f23e9e6")+mod.component());String old=membership.putIfAbsent(SafePaths.key(mod.path()),mod.component());if(old!=null&&!old.equals(mod.component()))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.file_belongs_to_multiple_components_78b026c7")+mod.path());}
  var files=new ArrayList<PackManifest.Entry>();var found=new HashSet<String>();var inputs=new ArrayList<PackModAudit.Input>();
  try(var walk=Files.walk(source)){var paths=walk.iterator();while(paths.hasNext()){checkCancelled();var file=paths.next();
   safe(file);if(Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))continue;String path=source.relativize(file).toString().replace('\\','/');if(path.equals("pack.toml"))continue;
   add(file,path,membership,policies,files,found,inputs,"");
  }}
  for(var mod:external)add(mod.file(),mod.path(),membership,policies,files,found,inputs,mod.url());
  if(inputs.size()>512)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.the_pack_contains_over_512_mods_8bf0898e"));
  checkCancelled();
  PackModAudit.validate(inputs,minecraft,loader);
  files.sort(Comparator.comparing(PackManifest.Entry::path));
  if(!found.containsAll(membership.keySet())||!found.containsAll(policies.keySet()))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.settings_reference_missing_files_e0f8d18f"));
  checkCancelled();
  var manifest=new PackManifest(minecraft,loader,components,files);byte[] bytes=manifest.bytes();if(bytes.length>8*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.description_is_too_large_28b2d586"));var publication=data.resolve("publications").resolve(manifest.hash()+".json");safe(publication);if(!Files.exists(publication)){Path pending=Files.createTempFile(publication.getParent(),"publication-",".tmp");try{Files.write(pending,bytes);Json.move(pending,publication);}finally{Files.deleteIfExists(pending);}}return manifest;
 }
 private void add(Path file,String path,Map<String,String> membership,Map<String,String> policies,List<PackManifest.Entry> files,Set<String> found,List<PackModAudit.Input> inputs,String url)throws IOException{
  checkCancelled();
  SafePaths.validate(path);safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unexpected_file_7629a3be")+path);if(files.size()>=10000)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_files_5dca7542"));
  if(!found.add(SafePaths.key(path)))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.duplicate_path_78002a20")+path);
  long size=Files.size(file);if(size>8L*1024*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.file_exceeds_8_gib_c8021d82")+path);String hash=Hashes.sha256(file);var object=object(hash);
  if(!Files.exists(object)){var tmp=Files.createTempFile(data.resolve("objects"),"publish-",".tmp");try{Files.copy(file,tmp,StandardCopyOption.REPLACE_EXISTING);if(Files.size(tmp)!=size||!Hashes.sha256(tmp).equals(hash))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.file_changed_during_publication_492cd2f7")+path);Json.move(tmp,object);}finally{Files.deleteIfExists(tmp);}}
  else if(!Hashes.sha256(object).equals(hash))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.corrupt_published_file_66dbdeea"));
  String component=membership.getOrDefault(SafePaths.key(path),"");files.add(new PackManifest.Entry(path,hash,size,component,policies.getOrDefault(SafePaths.key(path),Planner.configurable(path)?"missing":"replace"),url));
  if(path.startsWith("mods/")&&path.endsWith(".jar"))inputs.add(new PackModAudit.Input(path,object,component));
 }
 public Path object(String hash)throws IOException{Hashes.check(hash);Path p=data.resolve("objects").resolve(hash);safe(p);return p;}
 public synchronized void activate(PackManifest manifest,boolean onRestart)throws IOException{checkCancelled();load(manifest.hash());checkCancelled();Json.write(data.resolve(onRestart?"pending.json":"current.json"),Map.of("hash",manifest.hash()));if(!onRestart)Files.deleteIfExists(data.resolve("pending.json"));}
 public synchronized void startup()throws IOException{Path pending=data.resolve("pending.json");safe(pending);if(Files.exists(pending)){String hash=Json.str(Json.read(pending),"hash");load(hash);Json.write(data.resolve("current.json"),Map.of("hash",hash));Files.delete(pending);}}
 public PackManifest load(String hash)throws IOException{Hashes.check(hash);Path file=data.resolve("publications").resolve(hash+".json");safe(file);if(Files.size(file)>8*1024*1024)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.description_is_too_large_28b2d586"));byte[] bytes=Files.readAllBytes(file);var manifest=PackManifest.parse(bytes);if(!manifest.hash().equals(hash))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.snapshot_is_corrupt_582404a6"));return manifest;}
 public PackManifest current()throws IOException{Path file=data.resolve("current.json");safe(file);return Files.exists(file)?load(Json.str(Json.read(file),"hash")):null;}
}
