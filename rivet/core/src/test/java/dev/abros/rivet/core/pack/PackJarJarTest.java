package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;
class PackJarJarTest {
 @TempDir Path root;
 Path jar(String name,String id,String version,String dependency,Path child,String range)throws Exception{
  return jar(name,id,version,dependency,child,range,"META-INF/jarjar/lib.jar");
 }
 Path jar(String name,String id,String version,String dependency,Path child,String range,String childPath)throws Exception{
  return jar(name,id,version,dependency,child,range,childPath,"library");
 }
 Path jar(String name,String id,String version,String dependency,Path child,String range,String childPath,String artifact)throws Exception{
  Path file=root.resolve(name);try(var out=new JarOutputStream(Files.newOutputStream(file))){
   entry(out,"META-INF/neoforge.mods.toml",("[[mods]]\nmodId='"+id+"'\nversion='"+version+"'\n"+dependency).getBytes());
   if(child!=null){
    var info=Map.of("identifier",Map.of("group","example","artifact",artifact),"version",Map.of("range",range,"artifactVersion",child.getFileName().toString().startsWith("v2")?"2.0":"1.0"),"path",childPath,"isObfuscated",false);
    entry(out,"META-INF/jarjar/metadata.json",Json.GSON.toJson(Map.of("jars",List.of(info))).getBytes());entry(out,childPath,Files.readAllBytes(child));
   }
  }return file;
 }
 void entry(JarOutputStream out,String name,byte[] bytes)throws IOException{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
 String dependency(String owner,String id,String range){return "[[dependencies."+owner+"]]\nmodId='"+id+"'\ntype='required'\nversionRange='"+range+"'\n";}
 PackModAudit.Input input(Path p,String component){return new PackModAudit.Input("mods/"+p.getFileName(),p,component);}
 void check(PackModAudit.Input... inputs)throws Exception{PackModAudit.validate(List.of(inputs),"1.21.1","21.1.250");}
 @Test void nestedLibrariesOutsideJarjarDirectoryAreAccepted()throws Exception{
  var lib=jar("v1.jar","library","1.0","",null,"");
  for(String path:List.of("META-INF/jars/lib.jar","libraries/lib.jar","lib.jar","META-INF/jarjar/lib..jar","META-INF/jars/lib.JAR")){
   var main=jar("a.jar","first","1.0",dependency("first","library","[1,2)"),lib,"[1,2)",path);check(input(main,""));
  }
  var nested=jar("nested.jar","bridge","1.0",dependency("bridge","library","[1,2)"),lib,"[1,2)","META-INF/jars/lib.jar");
  var main=jar("recursive.jar","first","1.0",dependency("first","bridge","[1,2)"),nested,"[1,2)","META-INF/jars/bridge.jar","bridge");check(input(main,""));
 }
 @Test void unsafeNestedPathsRemainRejectedWithOwnerAndPath()throws Exception{
  var lib=jar("v1.jar","library","1.0","",null,"");
  for(String path:List.of("../lib.jar","META-INF/jars/../lib.jar","/lib.jar","C:/lib.jar","META-INF\\jars\\lib.jar","META-INF//lib.jar","./lib.jar","META-INF/jars/lib.jar\n","META-INF/jars/lib.zip")){
   var main=jar("a.jar","first","1.0","",lib,"[1,2)",path);var error=assertThrows(IOException.class,()->check(input(main,"")));
   assertTrue(error.getMessage().contains("Небезопасный путь JarJar"));assertTrue(error.getMessage().contains("mods/a.jar"));assertTrue(error.getMessage().contains(Json.GSON.toJson(path)));
  }
 }
 @Test void dependencyTypesAcceptLoaderSupportedCaseAndStillEnforceRequirements()throws Exception{
  var lib=jar("library.jar","library","1.0","",null,"");
  for(String type:List.of("required","REQUIRED","Required","")){
   var main=jar("main.jar","first","1.0",dependency("first","library","[1,2)").replace("type='required'",type.isEmpty()?"":"type='"+type+"'"),null,"");
   check(input(main,""),input(lib,""));assertThrows(IOException.class,()->check(input(main,"")));
  }
 }
 @Test void libraryContainerUsesEmbeddedModsInsteadOfOuterToml()throws Exception{
  var lib=jar("v1.jar","library","1.0","",null,"");var wrapper=jar("wrapper.jar","library","1.0","",lib,"[1,2)");
  var attrs=new Manifest();attrs.getMainAttributes().putValue("Manifest-Version","1.0");attrs.getMainAttributes().putValue("FMLModType","LIBRARY");var converted=root.resolve("container.jar");
  try(var source=new JarFile(wrapper.toFile());var out=new JarOutputStream(Files.newOutputStream(converted),attrs)){
   var entries=source.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(!e.getName().equals("META-INF/MANIFEST.MF"))entry(out,e.getName(),source.getInputStream(e).readAllBytes());}
  }
  var owner=jar("owner.jar","first","1.0",dependency("first","library","[1,2)"),null,"");check(input(owner,""),input(converted,""));
  var duplicate=jar("duplicate.jar","library","1.0","",null,"");assertThrows(IOException.class,()->check(input(converted,""),input(duplicate,"")));
 }
 @Test void loaderMinecraftCompatibilityMatrixDoesNotRelaxOtherVersions()throws Exception{
  for(String id:List.of("minecraft","neoforge")){
   String range=id.equals("minecraft")?"[1.21,1.21.1)":"[21.0,21.1)";
   var main=jar("matrix.jar","first","1.0",dependency("first",id,range),null,"");check(input(main,""));
   assertThrows(IOException.class,()->PackModAudit.validate(List.of(input(main,"")),"1.21.2","21.2.0"));
  }
  var future=jar("future.jar","first","1.0",dependency("first","minecraft","[1.21.2,)"),null,"");assertThrows(IOException.class,()->check(input(future,"")));
  var old=jar("old.jar","first","1.0",dependency("first","minecraft","[1.20,1.21)"),null,"");assertThrows(IOException.class,()->check(input(old,"")));
 }
 @Test void sharedEmbeddedModIsSelectedOnceIncludingOptionalCopies()throws Exception{
  var lib=jar("v1.jar","library","1.0","",null,"");
  var a=jar("a.jar","first","1.0",dependency("first","library","[1,3)"),lib,"[1,3)");
  var b=jar("b.jar","second","1.0",dependency("second","library","[1,3)"),lib,"[1,3)");
  check(input(a,""),input(b,""));check(input(a,"first"),input(b,"second"));
 }
 @Test void compatibleRangesSelectMatchingVersionAndIgnoreUnselectedDependencies()throws Exception{
  var old=jar("v1.jar","library","1.0",dependency("library","missing","*"),null,"");
  var newer=jar("v2.jar","library","2.0","",null,"");
  var a=jar("a.jar","first","1.0",dependency("first","library","[2,3)"),old,"[1,3)");
  var b=jar("b.jar","second","1.0","",newer,"[2,3)");check(input(a,""),input(b,""));
 }
 @Test void conflictingJarJarRangesFail()throws Exception{
  var a=jar("a.jar","first","1.0","",jar("v1.jar","library","1.0","",null,""),"[1,2)");
  var b=jar("b.jar","second","1.0","",jar("v2.jar","library","2.0","",null,""),"[2,3)");
  assertTrue(assertThrows(IOException.class,()->check(input(a,""),input(b,""))).getMessage().contains("JarJar"));
 }
 @Test void duplicateRootModsRemainInvalid()throws Exception{
  var a=jar("a.jar","sample","1.0","",null,"");var b=jar("b.jar","sample","1.0","",null,"");
  assertTrue(assertThrows(IOException.class,()->check(input(a,""),input(b,""))).getMessage().contains("Повторяющийся мод"));
 }
 @Test void mandatoryOwnerCannotDependOnOptionalEmbeddedLibrary()throws Exception{
  var a=jar("a.jar","first","1.0",dependency("first","library","*"),null,"");
  var b=jar("b.jar","second","1.0","",jar("v1.jar","library","1.0","",null,""),"[1,3)");
  assertThrows(IOException.class,()->check(input(a,""),input(b,"extra")));
 }
 @Test void punctuationInRootFileNameDoesNotBypassComponentDependencyCheck()throws Exception{
  var a=jar("a.jar","first","1.0",dependency("first","library","*"),null,"");
  var b=jar("lib!root.jar","library","1.0","",null,"");
  assertThrows(IOException.class,()->check(input(a,""),input(b,"extra")));
 }
 @Test void selectedEmbeddedDependenciesAreChecked()throws Exception{
  var lib=jar("v1.jar","library","1.0",dependency("library","missing","*"),null,"");
  var a=jar("a.jar","first","1.0","",lib,"[1,3)");assertThrows(IOException.class,()->check(input(a,"")));
 }
}
