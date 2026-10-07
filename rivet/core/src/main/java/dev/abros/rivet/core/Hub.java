package dev.abros.rivet.core;
import com.google.gson.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Client runtime shared by server packs and Rivet updates. */
public final class Hub {
 public final Path game;public final Remote remote=new Remote();public final Cache cache;
 private final String coreVersion,neoVersion;private Process helperProcess;private String helperTransaction="";
 private final Object coreCatalogLock=new Object(),coreInstallLock=new Object();
 private final CoreUpdater coreUpdater;
 private List<CoreUpdater.Update> coreCatalog;private long coreCatalogAt;
 public Hub(Path game,String coreVersion,String neoVersion)throws IOException {this.game=game.toRealPath();this.coreVersion=coreVersion;this.neoVersion=neoVersion;new Transactions(this.game);remote.cacheMetadata(this.game.resolve("rivet/cache/http"));cache=new Cache(this.game,remote);coreUpdater=new CoreUpdater(remote,this.game.resolve("rivet/cache/updates"));
  try{cache.cleanup(this.game);}catch(IOException invalid){System.getLogger(Hub.class.getName()).log(System.Logger.Level.WARNING,"Download cache cleanup skipped; protection state is unavailable");}
 }
 public String coreVersion(){return coreVersion;}public String neoVersion(){return neoVersion;}
 public JsonObject state()throws IOException {Path p=game.resolve("rivet/state.json");return Files.exists(p)?Json.read(p):new JsonObject();}
 public String activeHash(){try{return new dev.abros.rivet.core.pack.PackInstaller(game,cache).installedHash();}catch(Exception invalid){return "invalid";}}
 public List<String> audit()throws IOException {var issues=new ArrayList<String>();var state=state();if(state.has("ownership"))for(var e:state.getAsJsonObject("ownership").entrySet()){var value=e.getValue().getAsJsonObject();if(Json.str(value,"policy").equals("enforce")&&!Json.str(value,"hash").equals(Planner.hash(SafePaths.resolve(game,e.getKey()))))issues.add(e.getKey());}return List.copyOf(issues);}
    public synchronized Process startHelper(String transactionId)throws Exception{
        if(helperProcess!=null&&helperProcess.isAlive()){if(!helperTransaction.equals(transactionId))throw new IOException("A different update is already pending");return helperProcess;}
        Path runtime=game.resolve("rivet/runtime");Files.createDirectories(runtime);Path helper=runtime.resolve("helper.jar");
        try(InputStream in=Hub.class.getResourceAsStream("/rivet/helper.jar")){if(in==null)throw new IOException("Embedded helper missing");Files.copy(in,helper,StandardCopyOption.REPLACE_EXISTING);}
        String javaExecutable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        Process probe=new ProcessBuilder(javaExecutable,"-jar",helper.toString(),"--version").redirectErrorStream(true).start();if(!probe.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)||probe.exitValue()!=0)throw new IOException("HELPER_UNAVAILABLE");
        var self=ProcessHandle.current();helperTransaction=transactionId;helperProcess=new ProcessBuilder(javaExecutable,"-jar",helper.toString(),"apply",game.toString(),transactionId,Long.toString(self.pid()),self.info().startInstant().orElseThrow().toString()).redirectOutput(runtime.resolve("helper.log").toFile()).redirectErrorStream(true).start();return helperProcess;
    }
    /** Checking never downloads a JAR, prepares a transaction or starts a helper. */
    public java.util.Optional<CoreUpdater.Update> checkCoreUpdate()throws Exception{
        synchronized(this){if(Files.exists(game.resolve("rivet/pending.json"))||(helperProcess!=null&&helperProcess.isAlive()))return java.util.Optional.empty();}
        return coreUpdater.check(coreVersion,"1.21.1",neoVersion);
    }
    public java.util.List<CoreUpdater.Update> availableCoreUpdates()throws Exception{
        synchronized(coreCatalogLock){
        if(coreCatalog!=null&&System.nanoTime()-coreCatalogAt<java.util.concurrent.TimeUnit.MINUTES.toNanos(5))return coreCatalog;
        var updates=coreUpdater.releases(coreVersion,"1.21.1",neoVersion,100,true);coreCatalog=updates;coreCatalogAt=System.nanoTime();return updates;}
    }
    /** Called only after the player accepts this particular update. */
    public String prepareCoreUpdate(Path loadedJar,CoreUpdater.Update update)throws Exception{ synchronized(coreInstallLock){
        state();
        if(Files.exists(game.resolve("rivet/pending.json"))||(helperProcess!=null&&helperProcess.isAlive()))throw new IllegalStateException("Сначала завершите уже подготовленное обновление");
        String id=coreUpdater.stage(game,loadedJar,update,cache,state());
        try { startHelper(id); }
        catch(Exception failure){if(helperProcess==null||!helperProcess.isAlive())new Transactions(game).abortReady(id);throw failure;}
        return id;}
    }
}
