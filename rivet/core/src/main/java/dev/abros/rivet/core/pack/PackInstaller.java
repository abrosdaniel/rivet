package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Reviews current files, owns only explicitly installed paths and stages the common helper transaction. */
public final class PackInstaller {
 public record Review(Planner.Plan plan,PackManifest manifest,JsonObject state,String server,JsonObject configurations,Map<String,String> activeConfigurations){}
 private final Path game;private final Cache cache;private final String rivetVersion;
 public PackInstaller(Path game,Cache cache){this(game,cache,null);}
 public PackInstaller(Path game,Cache cache,String rivetVersion){this.game=game;this.cache=cache;this.rivetVersion=rivetVersion;}
 private JsonObject state()throws IOException{Path file=game.resolve("rivet/state.json");return Files.exists(file)?Json.read(file):new JsonObject();}
 public Set<String> choices(String server,PackManifest manifest)throws IOException{var state=state();var selected=new HashSet<String>(manifest.initial());if(state.has("serverChoices")&&state.getAsJsonObject("serverChoices").has(server)){var choice=state.getAsJsonObject("serverChoices").getAsJsonObject(server);for(var c:manifest.components())if(c.optional()&&choice.has(c.id())){if(choice.get(c.id()).getAsBoolean())selected.add(c.id());else selected.remove(c.id());}}return Set.copyOf(selected);}
 public Review review(String server,PackManifest manifest,Set<String> choices)throws IOException{
  var state=state();var old=new TreeMap<String,Planner.Owned>();if(state.has("ownership"))for(var e:state.getAsJsonObject("ownership").entrySet()){SafePaths.validate(e.getKey());var value=e.getValue().getAsJsonObject();String hash=Json.str(value,"hash");Hashes.check(hash);old.put(e.getKey(),new Planner.Owned(hash,Json.str(value,"policy"),Json.str(value,"componentId")));}
  String previous=Json.opt(state,"packServer","");boolean switching=!previous.isEmpty()&&!previous.equals(server);
  var configurations=state.has("serverConfigurations")?state.getAsJsonObject("serverConfigurations").deepCopy():new JsonObject();
  var activeConfigurations=new TreeMap<String,String>();
  if(!previous.isEmpty()){
   var saved=configurations.has(previous)?configurations.getAsJsonObject(previous).deepCopy():new JsonObject();for(var e:old.entrySet())if(Planner.configurable(e.getKey())){String hash=Planner.hash(SafePaths.resolve(game,e.getKey()));if(hash!=null){saved.addProperty(e.getKey(),hash);activeConfigurations.put(e.getKey(),hash);}}
   configurations.add(previous,saved);
  }
  var target=configurations.has(server)?configurations.getAsJsonObject(server):new JsonObject();
  var owned=new TreeMap<String,Planner.Owned>();var changes=new ArrayList<Planner.Change>();var conflicts=new ArrayList<String>();var desired=new HashSet<String>();long downloads=0;var seen=new HashSet<String>();
  for(var file:manifest.selected(choices)){String path=file.path();desired.add(path);String current=Planner.hash(SafePaths.resolve(game,path));String policy=file.policy().equals("missing")?"preserve":"enforce";
   // Existing personal defaults are never acquired by the pack.
   if(current!=null&&file.policy().equals("missing")&&!old.containsKey(path))continue;
   if(current!=null&&file.policy().equals("missing")&&!switching){owned.put(path,new Planner.Owned(current,"preserve",file.component()));continue;}
   String wanted=file.hash();boolean restored=(switching||current==null)&&Planner.configurable(path)&&file.policy().equals("missing")&&target.has(path);
   if(restored){wanted=target.get(path).getAsString();Hashes.check(wanted);if(!wanted.equals(file.hash()))configurationObject(wanted);}

   if(current!=null&&current.equals(wanted)){owned.put(path,new Planner.Owned(current,policy,file.component()));continue;}
   if(current!=null&&(!old.containsKey(path)||!old.get(path).hash().equals(current)))conflicts.add(dev.abros.rivet.core.Messages.text("rivet.core.replace_existing_or_modified_file_ef998049")+path);
   owned.put(path,new Planner.Owned(wanted,policy,file.component()));changes.add(new Planner.Change(path,current,wanted));if(seen.add(wanted)&&!cache.contains(wanted)&&!(restored&&!wanted.equals(file.hash())))downloads+=file.size();
  }
  for(var e:old.entrySet())if(!desired.contains(e.getKey())){String current=Planner.hash(SafePaths.resolve(game,e.getKey()));if(current==null)continue;if(!current.equals(e.getValue().hash()))conflicts.add(dev.abros.rivet.core.Messages.text("rivet.core.delete_modified_file_464bc3ca")+e.getKey());changes.add(new Planner.Change(e.getKey(),current,null));}
  var plan=new Planner.Plan(UUID.randomUUID().toString(),Hashes.sha256(server.getBytes(java.nio.charset.StandardCharsets.UTF_8)),List.copyOf(changes),Map.copyOf(owned),Set.copyOf(choices),List.copyOf(conflicts),downloads);return new Review(plan,manifest,state,server,configurations,Map.copyOf(activeConfigurations));
 }
 public String stage(Review review,PackClient client,AtomicBoolean cancel,Consumer<String> progress)throws IOException{
  validateReview(review);
  var hashes=new HashSet<String>();for(var c:review.plan().changes())if(c.after()!=null)hashes.add(c.after());for(var hash:List.copyOf(hashes))if(!review.manifest().files().stream().anyMatch(f->f.hash().equals(hash))){Path source=configurationObject(hash);Path object=cache.path(hash);Files.createDirectories(object.getParent());Files.copy(source,object,StandardCopyOption.REPLACE_EXISTING);hashes.remove(hash);}
  for(var file:review.manifest().files())if(hashes.remove(file.hash())){if(cancel.get())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.upload_cancelled_13f622b6"));cache.write(file.hash(),cancel,object->client.download(review.manifest().hash(),file,object,cancel,progress));}if(!hashes.isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.the_manifest_is_missing_required_files_f9ada0c2"));if(cancel.get())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.upload_cancelled_13f622b6"));
  validateMods(review);
  var plan=review.plan();var accepted=new Planner.Plan(plan.id(),plan.projectKey(),plan.changes(),plan.ownership(),plan.selection(),List.of(),plan.downloadBytes());
  new Transactions(game).prepare(accepted,review.manifest().bytes(),nextState(review));return plan.id();
 }
 public void saveSelection(Review review)throws IOException{
  if(!review.plan().changes().isEmpty())throw new IOException("Selection requires a file transaction");
  validateReview(review);validateMods(review);
  new Transactions(game).commitState(review.plan(),review.state(),nextState(review));
 }
 private void validateReview(Review review)throws IOException{
  if(!state().equals(review.state()))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.state_changed_review_the_changes_again_a2da111b"));
  for(var c:review.plan().changes())if(!Objects.equals(c.before(),Planner.hash(SafePaths.resolve(game,c.path()))))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.files_changed_review_the_changes_again_b77a8e4d"));
  // Snapshot the active server before the helper replaces its files; state commits with the transaction.
  for(var e:review.activeConfigurations().entrySet()){
   Path current=SafePaths.resolve(game,e.getKey());String hash=e.getValue();
   if(!hash.equals(Planner.hash(current)))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.settings_changed_review_the_changes_again_2917d72c"));
   saveConfiguration(current,hash);
  }
 }
 private JsonObject nextState(Review review)throws IOException{
  var plan=review.plan();
  var next=review.state().deepCopy();next.remove("lock");next.remove("lockSha256");next.remove("repository");next.add("serverConfigurations",review.configurations());next.add("ownership",Json.GSON.toJsonTree(plan.ownership()));next.addProperty("packServer",review.server());next.addProperty("packHash",review.manifest().hash());next.add("serverPack",Json.parse(new String(review.manifest().bytes(),java.nio.charset.StandardCharsets.UTF_8)));var choices=next.has("serverChoices")?next.getAsJsonObject("serverChoices"):new JsonObject();var selected=new JsonObject();for(var c:review.manifest().components())selected.addProperty(c.id(),!c.optional()||plan.selection().contains(c.id()));choices.add(review.server(),selected);next.add("serverChoices",choices);return next;
 }

 private Path configurationPath(String hash)throws IOException{
  Hashes.check(hash);Path object=game.toRealPath().resolve("rivet/pack-settings/"+hash);PackPublisher.safe(object);Files.createDirectories(object.getParent());return object;
 }
 private Path configurationObject(String hash)throws IOException{
  Path object=configurationPath(hash);if(!Files.isRegularFile(object,LinkOption.NOFOLLOW_LINKS)||!hash.equals(Hashes.sha256(object)))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.saved_settings_are_corrupt_b83b1158")+hash);return object;
 }
 private void saveConfiguration(Path source,String hash)throws IOException{
  Path object=configurationPath(hash);if(Files.exists(object)&&hash.equals(Hashes.sha256(object)))return;
  Path temporary=object.resolveSibling(hash+"."+UUID.randomUUID()+".tmp");
  try{Files.copy(source,temporary);if(!hash.equals(Hashes.sha256(temporary)))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.settings_changed_review_the_changes_again_2917d72c"));Json.move(temporary,object);}finally{Files.deleteIfExists(temporary);}
 }
 public void validateMods(Review review)throws IOException{
  var projected=new TreeMap<String,Path>();Path mods=SafePaths.resolve(game,"mods/__rivet_check__.jar").getParent();
  if(Files.isDirectory(mods))try(var list=Files.list(mods)){for(var file:list.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){PackPublisher.safe(file);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unexpected_mod_file_5e0160ec")+file.getFileName());projected.put(file.getFileName().toString(),file);}}
  for(var change:review.plan().changes())if(change.path().startsWith("mods/")&&change.path().endsWith(".jar")){
   String name=change.path().substring("mods/".length());projected.remove(name);if(change.after()!=null)projected.put(name,cache.path(change.after()));
  }
  var duplicates=ModDuplicates.scanFiles(projected);if(!duplicates.isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.duplicate_mods_after_installation_abf4e1fa")+String.join("; ",duplicates)+dev.abros.rivet.core.Messages.text("rivet.core.remove_the_personal_duplicate_manually_and_c322f7e8"));
  var inputs=new ArrayList<PackModAudit.Input>();for(var entry:projected.entrySet())inputs.add(new PackModAudit.Input("mods/"+entry.getKey(),entry.getValue(),""));
  try{PackModAudit.validateInstalled(inputs,review.manifest().minecraft(),review.manifest().loader(),rivetVersion);}
  catch(IOException incompatible){throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.installed_mod_set_is_incompatible_18ba71db")+incompatible.getMessage()+dev.abros.rivet.core.Messages.text("rivet.core.change_your_selected_components_or_personal_5dcd8279"),incompatible);}
 }
 public String installedHash()throws IOException{var state=state();if(!state.has("serverPack"))return "";var manifest=PackManifest.parse(Json.GSON.toJson(state.get("serverPack")).getBytes(java.nio.charset.StandardCharsets.UTF_8));for(var f:manifest.selected(Set.of()))if(f.policy().equals("replace")&&!f.hash().equals(Planner.hash(SafePaths.resolve(game,f.path()))))return "invalid";return Json.opt(state,"packHash","");}
}
