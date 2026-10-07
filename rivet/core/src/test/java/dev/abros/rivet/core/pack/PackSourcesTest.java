package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;
class PackSourcesTest {
 @TempDir Path root;
 @BeforeEach void canonicalRoot()throws Exception{root=root.toRealPath();}
 Path jar(String name,String id,String version,String dependency)throws Exception{var p=root.resolve(name);try(var out=new JarOutputStream(Files.newOutputStream(p))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write(("modLoader='javafml'\nloaderVersion='[1,)'\nlicense='MIT'\n[[mods]]\nmodId='"+id+"'\nversion='"+version+"'\n"+dependency).getBytes());out.closeEntry();}return p;}
 class Fake implements PackSources.Transport {
  Path payload;boolean corrupt,restricted,missingUrl;int downloads;List<String> keys=new ArrayList<>();
  Fake(Path payload){this.payload=payload;}
  public com.google.gson.JsonObject json(String url,String key)throws IOException{
   keys.add(key);var o=new com.google.gson.JsonObject();
   if(url.contains("modrinth")){o.addProperty("id","v1");o.addProperty("project_id","p1");o.add("game_versions",Json.GSON.toJsonTree(List.of("1.21.1")));o.add("loaders",Json.GSON.toJsonTree(List.of("neoforge")));var f=new com.google.gson.JsonObject();f.addProperty("primary",true);f.addProperty("filename","sample.jar");f.addProperty("size",Files.size(payload));f.addProperty("url","https://cdn.modrinth.com/data/p1/versions/v1/sample.jar");f.add("hashes",Json.GSON.toJsonTree(Map.of("sha512",PackSources.digest(payload,"SHA-512"))));o.add("files",Json.GSON.toJsonTree(List.of(f)));return o;}
   if(url.contains("search")){o.add("data",Json.GSON.toJsonTree(List.of(Map.of("id",123,"slug","sample","allowModDistribution",!restricted))));return o;}
   var f=new com.google.gson.JsonObject();f.addProperty("id",456);f.addProperty("modId",123);f.addProperty("fileName","sample.jar");f.addProperty("fileLength",Files.size(payload));f.addProperty("isAvailable",true);f.add("gameVersions",Json.GSON.toJsonTree(List.of("1.21.1","NeoForge")));f.add("hashes",Json.GSON.toJsonTree(List.of(Map.of("algo",1,"value",PackSources.digest(payload,"SHA-1")))));if(!missingUrl)f.addProperty("downloadUrl","https://edge.forgecdn.net/files/0/456/sample.jar");o.add("data",f);return o;
  }
  public void download(String url,String key,Path target,long size)throws IOException{downloads++;keys.add(key);if(corrupt)Files.writeString(target,"corrupt");else Files.copy(payload,target,StandardCopyOption.REPLACE_EXISTING);}
 }
 PackPublisher publisher(Fake fake)throws Exception{return new PackPublisher(root.resolve("source"),root.resolve("data"),new PackSources(fake,name->name.equals("CF_KEY")?"secret-test-key":null));}
 @Test void resolvedPublicUrlIsPublishedWithoutCredentials()throws Exception{var payload=jar("url.jar","sample","1.0","");var fake=new Fake(payload);var publisher=publisher(fake);config(mr);var manifest=publisher.prepare("1.21.1","21.1.250");assertEquals("https://cdn.modrinth.com/data/p1/versions/v1/sample.jar",manifest.files().getFirst().url());config("curseforgeKeyEnv='CF_KEY'\n[[mods]]\nurl='https://curseforge.com/minecraft/mc-mods/sample/files/456'\n");manifest=publisher.prepare("1.21.1","21.1.250");assertEquals("https://edge.forgecdn.net/files/0/456/sample.jar",manifest.files().getFirst().url());assertFalse(new String(manifest.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("secret-test-key"));}
 void config(String contents)throws Exception{Files.writeString(root.resolve("source/pack.toml"),contents);}
 String mr="[[mods]]\nurl='https://modrinth.com/mod/sample/version/v1'\n";
 String cf="curseforgeKeyEnv='CF_KEY'\n[[mods]]\nurl='https://www.curseforge.com/minecraft/mc-mods/sample/files/456'\n";
 @Test void exactSourcePublishesImmutableObjectAndReusesVerifiedDownload()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);config(mr);var first=p.prepare("1.21.1","21.1.250");assertEquals("mods/sample.jar",first.files().getFirst().path());assertEquals(first.hash(),p.prepare("1.21.1","21.1.250").hash());assertEquals(1,f.downloads);assertFalse(Files.exists(root.resolve("source/mods/sample.jar")));p.activate(first,false);config("");var empty=p.prepare("1.21.1","21.1.250");assertTrue(empty.files().isEmpty());assertEquals(first.hash(),p.current().hash());}
 @Test void checksumFailureNeverPublishes()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));f.corrupt=true;var p=publisher(f);config(mr);assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));assertNull(p.current());}
 @Test void sourceCannotCollideWithLocalFile()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);config(mr);Files.createDirectories(root.resolve("source/mods"));Files.copy(f.payload,root.resolve("source/mods/sample.jar"));assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));}
 @Test void curseforgeKeyStaysOutOfManifestAndRestrictionsAreRespected()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);config(cf);var m=p.prepare("1.21.1","21.1.250");assertFalse(new String(m.bytes()).contains("secret-test-key"));assertTrue(f.keys.stream().allMatch(k->k.equals("secret-test-key")));f.restricted=true;assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));f.restricted=false;f.missingUrl=true;assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));}
 @Test void missingCredentialIsActionableAndDoesNotContactProvider()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);config(cf.replace("CF_KEY","MISSING_KEY"));assertTrue(assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250")).getMessage().contains("MISSING_KEY"));assertTrue(f.keys.isEmpty());}
 @Test void unsupportedAndFloatingLinksAreRejected()throws Exception{var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);for(var url:List.of("http://modrinth.com/mod/sample/version/v1","https://modrinth.com/mod/sample","https://evil.test/mod/sample/version/v1","https://modrinth.com/mod/sample/version/v1?token=secret")){config("[[mods]]\nurl='"+url+"'\n");assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));}assertTrue(f.keys.isEmpty());}
 @Test void requiredDependencyAndVersionAreValidatedBeforePublication()throws Exception{Path a=jar("a.jar","sample","1.0","[[dependencies.sample]]\nmodId='library'\ntype='required'\nversionRange='[2,3)'\nside='CLIENT'\n");var f=new Fake(a);var p=publisher(f);config(mr);assertTrue(assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250")).getMessage().contains("library"));Files.createDirectories(root.resolve("source/mods"));Files.copy(jar("lib.jar","library","1.0",""),root.resolve("source/mods/lib.jar"));assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));Files.copy(jar("lib2.jar","library","2.5",""),root.resolve("source/mods/lib.jar"),StandardCopyOption.REPLACE_EXISTING);assertEquals(2,p.prepare("1.21.1","21.1.250").files().size());}
 @Test void dependencyCannotDisappearWhenComponentIsDisabled()throws Exception{var f=new Fake(jar("a.jar","sample","1.0","[[dependencies.sample]]\nmodId='library'\ntype='required'\nversionRange='*'\n"));var p=publisher(f);Files.createDirectories(root.resolve("source/mods"));Files.copy(jar("lib.jar","library","2.0",""),root.resolve("source/mods/lib.jar"));String component="[[components]]\nid='extras'\noptional=true\nfiles=['mods/lib.jar']\n";config(mr+component);assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));config(mr+"component='extras'\n"+component);var m=p.prepare("1.21.1","21.1.250");assertTrue(m.selected(Set.of()).isEmpty());}
 @Test void corruptAndFabricOnlyJarsAreRejected()throws Exception{var p=publisher(new Fake(jar("a.jar","sample","1.0","")));Files.createDirectories(root.resolve("source/mods"));Files.writeString(root.resolve("source/mods/bad.jar"),"not a jar");assertThrows(IOException.class,()->p.prepare("1.21.1","21.1.250"));}
 @Test void upgradingSettingsPreservesCommentsAndCreatesPrivateBackup()throws Exception{
  Files.createDirectories(root.resolve("source"));String original="# Owner comment\n[[configs]]\npath='config/example.toml'\nupdate='missing'\n";Files.writeString(root.resolve("source/pack.toml"),original);
  var p=publisher(new Fake(jar("a.jar","sample","1.0","")));String updated=Files.readString(root.resolve("source/pack.toml"));assertTrue(updated.endsWith(original));assertTrue(updated.contains("curseforgeKeyEnv"));try(var backups=Files.list(root.resolve("data/settings-backups"))){assertEquals(original,Files.readString(backups.findFirst().orElseThrow()));}
  publisher(new Fake(root.resolve("a.jar")));try(var backups=Files.list(root.resolve("data/settings-backups"))){assertEquals(1,backups.count());}
 }
 @Test void embeddedJarJarModSatisfiesDependency()throws Exception{
  Path lib=jar("lib.jar","library","2.0","");Path main=root.resolve("embedded.jar");
  try(var out=new JarOutputStream(Files.newOutputStream(main))){out.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));out.write("[[mods]]\nmodId='sample'\nversion='1.0'\n[[dependencies.sample]]\nmodId='library'\ntype='required'\nversionRange='[2,3)'\n".getBytes());out.closeEntry();out.putNextEntry(new JarEntry("META-INF/jarjar/metadata.json"));out.write("{\"jars\":[{\"identifier\":{\"group\":\"example\",\"artifact\":\"library\"},\"version\":{\"range\":\"[2,3)\",\"artifactVersion\":\"2.0\"},\"path\":\"META-INF/jarjar/lib.jar\",\"isObfuscated\":false}]}".getBytes());out.closeEntry();out.putNextEntry(new JarEntry("META-INF/jarjar/lib.jar"));Files.copy(lib,out);out.closeEntry();}
  var p=publisher(new Fake(main));config(mr);assertEquals(1,p.prepare("1.21.1","21.1.250").files().size());
 }

 @Test void personalModWithDifferentFileNameBlocksInstallWithoutChangingFiles()throws Exception{
  var f=new Fake(jar("a.jar","sample","1.0",""));var p=publisher(f);config(mr);var manifest=p.prepare("1.21.1","21.1.250");p.activate(manifest,false);
  Path game=root.resolve("game");Files.createDirectories(game.resolve("mods"));Files.copy(f.payload,game.resolve("mods/personal-name.jar"));var installer=new PackInstaller(game,new Cache(game,new Remote()));
  try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   var error=assertThrows(IOException.class,()->installer.stage(installer.review("server",manifest,Set.of()),client,new java.util.concurrent.atomic.AtomicBoolean(),text->{}));assertTrue(error.getMessage().contains("sample"));assertTrue(error.getMessage().contains("personal-name.jar"));
  }
  assertTrue(Files.exists(game.resolve("mods/personal-name.jar")));assertFalse(Files.exists(game.resolve("mods/sample.jar")));assertFalse(Files.exists(game.resolve("rivet/pending.json")));assertFalse(Files.exists(game.resolve("rivet/state.json")));
 }
 @Test void renamedOwnedModDoesNotProduceFalseDuplicate()throws Exception{
  Path mod=jar("a.jar","sample","1.0","");var p=publisher(new Fake(mod));Files.createDirectories(root.resolve("source/mods"));Files.copy(mod,root.resolve("source/mods/old.jar"));var first=p.prepare("1.21.1","21.1.250");p.activate(first,false);
  Path game=root.resolve("game");Files.createDirectories(game);var installer=new PackInstaller(game,new Cache(game,new Remote()));
  try(var server=new PackServer(p,root.resolve("identity"),"127.0.0.1",0,true,0,0,0);var client=new PackClient("127.0.0.1",server.port(),server.fingerprint())){
   String id=installer.stage(installer.review("server",first,Set.of()),client,new java.util.concurrent.atomic.AtomicBoolean(),text->{});new Transactions(game).apply(id);
   Files.move(root.resolve("source/mods/old.jar"),root.resolve("source/mods/new.jar"));var next=p.prepare("1.21.1","21.1.250");p.activate(next,false);String update=installer.stage(installer.review("server",next,Set.of()),client,new java.util.concurrent.atomic.AtomicBoolean(),text->{});new Transactions(game).apply(update);
  }
  assertTrue(Files.exists(game.resolve("mods/new.jar")));assertFalse(Files.exists(game.resolve("mods/old.jar")));
 }

}
