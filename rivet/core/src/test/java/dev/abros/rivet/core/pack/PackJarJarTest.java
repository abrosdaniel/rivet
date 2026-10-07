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
  Path file=root.resolve(name);try(var out=new JarOutputStream(Files.newOutputStream(file))){
   entry(out,"META-INF/neoforge.mods.toml",("[[mods]]\nmodId='"+id+"'\nversion='"+version+"'\n"+dependency).getBytes());
   if(child!=null){
    var info=Map.of("identifier",Map.of("group","example","artifact","library"),"version",Map.of("range",range,"artifactVersion",child.getFileName().toString().startsWith("v2")?"2.0":"1.0"),"path","META-INF/jarjar/lib.jar","isObfuscated",false);
    entry(out,"META-INF/jarjar/metadata.json",Json.GSON.toJson(Map.of("jars",List.of(info))).getBytes());entry(out,"META-INF/jarjar/lib.jar",Files.readAllBytes(child));
   }
  }return file;
 }
 void entry(JarOutputStream out,String name,byte[] bytes)throws IOException{out.putNextEntry(new JarEntry(name));out.write(bytes);out.closeEntry();}
 String dependency(String owner,String id,String range){return "[[dependencies."+owner+"]]\nmodId='"+id+"'\ntype='required'\nversionRange='"+range+"'\n";}
 PackModAudit.Input input(Path p,String component){return new PackModAudit.Input("mods/"+p.getFileName(),p,component);}
 void check(PackModAudit.Input... inputs)throws Exception{PackModAudit.validate(List.of(inputs),"1.21.1","21.1.250");}
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
