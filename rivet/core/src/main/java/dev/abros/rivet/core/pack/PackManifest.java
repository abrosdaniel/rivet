package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import com.google.gson.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Content-addressed server pack, independent of repository identities and release versions. */
public record PackManifest(String minecraft,String loader,List<Component> components,List<Entry> files) {
 public record Component(String id,String name,String description,boolean selected) {}
 public record Entry(String path,String hash,long size,String component,String policy,String url) {
  public Entry(String path,String hash,long size,String component,String policy){this(path,hash,size,component,policy,"");}
  public Entry{if(url==null)throw new IllegalArgumentException("Некорректный адрес источника");if(!url.isEmpty())try{PackSources.publicDownload(url);}catch(java.io.IOException e){throw new IllegalArgumentException("Некорректный адрес источника",e);}}
 }
 public PackManifest {
  components=List.copyOf(components);files=List.copyOf(files);
  if(components.size()>2000||files.size()>10000)throw new IllegalArgumentException("Слишком много файлов или компонентов");
  var ids=new HashSet<String>();var paths=new HashSet<String>();
  for(var c:components){if(c.id()==null||!c.id().matches("[a-z0-9][a-z0-9_-]{0,63}"))throw new IllegalArgumentException("Некорректный id компонента");if(!ids.add(c.id())||c.name()==null||c.name().isBlank()||c.name().length()>120||c.description()==null||c.description().length()>2000)throw new IllegalArgumentException("Некорректный компонент");}
  for(var f:files){SafePaths.validate(f.path());Hashes.check(f.hash());if(!paths.add(SafePaths.key(f.path()))||f.size()<0||f.size()>8L*1024*1024*1024||!f.component().isEmpty()&&!ids.contains(f.component())||!Set.of("missing","replace").contains(f.policy())||!Planner.configurable(f.path())&&!f.policy().equals("replace"))throw new IllegalArgumentException("Некорректный файл: "+f.path());}
  if(minecraft==null||minecraft.length()>40||loader==null||loader.length()>40)throw new IllegalArgumentException("Некорректные требования игры");
 }
 public byte[] bytes(){var j=new JsonObject();j.addProperty("protocol",1);j.addProperty("minecraft",minecraft);j.addProperty("loader",loader);j.add("components",Json.GSON.toJsonTree(components));var entries=new JsonArray();for(var file:files){var entry=Json.GSON.toJsonTree(file).getAsJsonObject();if(file.url().isEmpty())entry.remove("url");entries.add(entry);}j.add("files",entries);return Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8);}
 public String hash(){return Hashes.sha256(bytes());}
 public Set<String> initial(){var out=new HashSet<String>();for(var c:components)if(c.selected())out.add(c.id());return Set.copyOf(out);}
 public List<Entry> selected(Set<String> choice){var ids=new HashSet<String>();components.forEach(c->ids.add(c.id()));if(!ids.containsAll(choice))throw new IllegalArgumentException("Неизвестный компонент");return files.stream().filter(f->f.component().isEmpty()||choice.contains(f.component())).toList();}
 public static PackManifest parse(byte[] bytes)throws java.io.IOException{if(bytes.length>8*1024*1024)throw new IllegalArgumentException("Описание слишком большое");var j=Json.parse(new String(bytes,StandardCharsets.UTF_8));Json.keys(j,"protocol","minecraft","loader","components","files");if(j.get("protocol").getAsInt()!=1)throw new IllegalArgumentException("Неподдерживаемый протокол сборки");
  var cs=new ArrayList<Component>();for(var e:j.getAsJsonArray("components")){var c=e.getAsJsonObject();Json.keys(c,"id","name","description","selected");cs.add(new Component(Json.str(c,"id"),Json.str(c,"name"),Json.str(c,"description"),c.get("selected").getAsBoolean()));}
  var fs=new ArrayList<Entry>();for(var e:j.getAsJsonArray("files")){var f=e.getAsJsonObject();Json.keys(f,"path","hash","size","component","policy","url");fs.add(new Entry(Json.str(f,"path"),Json.str(f,"hash"),f.get("size").getAsBigDecimal().longValueExact(),Json.str(f,"component"),Json.str(f,"policy"),Json.opt(f,"url","")));}
  return new PackManifest(Json.str(j,"minecraft"),Json.str(j,"loader"),cs,fs);
 }
}
