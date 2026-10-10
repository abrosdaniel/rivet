package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.*;
import com.google.gson.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Content-addressed server pack, independent of repository identities and release versions. */
public record PackManifest(String minecraft,String loader,List<Component> components,List<Entry> files) {
 public record Component(String id,String name,String description,boolean selected,boolean optional) {
  public Component(String id,String name,String description,boolean selected){this(id,name,description,selected,true);}
  public Component{if(!optional)selected=true;}
 }
 public record Entry(String path,String hash,long size,String component,String policy,String url) {
  public Entry(String path,String hash,long size,String component,String policy){this(path,hash,size,component,policy,"");}
  public Entry{if(url==null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_source_address_00dbd18e"));if(!url.isEmpty())try{PackSources.publicDownload(url);}catch(java.io.IOException e){throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_source_address_00dbd18e"),e);}}
 }
 public PackManifest {
  components=List.copyOf(components);files=List.copyOf(files);
  if(components.size()>2000||files.size()>10000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_files_or_components_4c528d57"));
  var ids=new HashSet<String>();var paths=new HashSet<String>();
  for(var c:components){if(c.id()==null||!c.id().matches("[a-z0-9][a-z0-9_-]{0,63}"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_component_id_7386079f"));if(!ids.add(c.id())||c.name()==null||c.name().isBlank()||c.name().length()>120||c.description()==null||c.description().length()>2000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_component_d2e58316"));}
  for(var f:files){SafePaths.validate(f.path());Hashes.check(f.hash());if(!paths.add(SafePaths.key(f.path()))||f.size()<0||f.size()>8L*1024*1024*1024||!f.component().isEmpty()&&!ids.contains(f.component())||!Set.of("missing","replace").contains(f.policy())||!Planner.configurable(f.path())&&!f.policy().equals("replace"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_file_321afd39")+f.path());}
  if(minecraft==null||minecraft.length()>40||loader==null||loader.length()>40)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_game_requirements_65c40a4e"));
 }
 public byte[] bytes(){var j=new JsonObject();j.addProperty("protocol",hasRequiredComponents()?2:1);j.addProperty("minecraft",minecraft);j.addProperty("loader",loader);var groups=new JsonArray();for(var component:components){var group=Json.GSON.toJsonTree(component).getAsJsonObject();if(component.optional())group.remove("optional");groups.add(group);}j.add("components",groups);var entries=new JsonArray();for(var file:files){var entry=Json.GSON.toJsonTree(file).getAsJsonObject();if(file.url().isEmpty())entry.remove("url");entries.add(entry);}j.add("files",entries);return Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8);}
 public boolean hasRequiredComponents(){return components.stream().anyMatch(c->!c.optional());}
 public PackManifest legacy(){if(!hasRequiredComponents())return this;var optional=new HashSet<String>();components.stream().filter(Component::optional).forEach(c->optional.add(c.id()));return new PackManifest(minecraft,loader,components.stream().filter(Component::optional).toList(),files.stream().map(f->optional.contains(f.component())?f:new Entry(f.path(),f.hash(),f.size(),"",f.policy(),f.url())).toList());}
 public String hash(){return Hashes.sha256(bytes());}
 public Set<String> initial(){var out=new HashSet<String>();for(var c:components)if(c.selected())out.add(c.id());return Set.copyOf(out);}
 public List<Entry> selected(Set<String> choice){var ids=new HashSet<String>();components.forEach(c->ids.add(c.id()));if(!ids.containsAll(choice))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_component_d7eff93b"));var required=new HashSet<String>();components.stream().filter(c->!c.optional()).forEach(c->required.add(c.id()));return files.stream().filter(f->f.component().isEmpty()||required.contains(f.component())||choice.contains(f.component())).toList();}
 public static PackManifest parse(byte[] bytes)throws java.io.IOException{if(bytes.length>8*1024*1024)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.description_is_too_large_28b2d586"));var j=Json.parse(new String(bytes,StandardCharsets.UTF_8));Json.keys(j,"protocol","minecraft","loader","components","files");int protocol=j.get("protocol").getAsInt();if(protocol!=1&&protocol!=2)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unsupported_modpack_protocol_17fa443e"));
  var cs=new ArrayList<Component>();for(var e:j.getAsJsonArray("components")){var c=e.getAsJsonObject();if(protocol==1)Json.keys(c,"id","name","description","selected");else Json.keys(c,"id","name","description","selected","optional");boolean optional=true;if(c.has("optional")){if(!c.get("optional").isJsonPrimitive()||!c.get("optional").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_component_d2e58316"));optional=c.get("optional").getAsBoolean();}cs.add(new Component(Json.str(c,"id"),Json.str(c,"name"),Json.str(c,"description"),c.get("selected").getAsBoolean(),optional));}
  var fs=new ArrayList<Entry>();for(var e:j.getAsJsonArray("files")){var f=e.getAsJsonObject();Json.keys(f,"path","hash","size","component","policy","url");fs.add(new Entry(Json.str(f,"path"),Json.str(f,"hash"),f.get("size").getAsBigDecimal().longValueExact(),Json.str(f,"component"),Json.str(f,"policy"),Json.opt(f,"url","")));}
  return new PackManifest(Json.str(j,"minecraft"),Json.str(j,"loader"),cs,fs);
 }
}
