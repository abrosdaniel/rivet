package dev.abros.rivet.core.api;

import dev.abros.rivet.api.TerrainApi;
import dev.abros.rivet.core.Json;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One coalescing IO writer. An acknowledged save is forced before atomic replacement. */
public final class TerrainJournal implements AutoCloseable {
 public record Cursor(String dimension,int regionX,int regionZ,int slot){}
 public record Job(UUID id,TerrainApi.Region region,Cursor cursor,int offset,int total,int rendered,int skipped,String state){}
 public record Chunk(String dimension,int x,int z){}
 public record State(int version,String generation,List<Job> jobs,List<Chunk> dirty,boolean sweep,boolean running){public State(int version,String generation,List<Job> jobs,List<Chunk> dirty,boolean sweep){this(version,generation,jobs,dirty,sweep,true);}public State{jobs=List.copyOf(jobs);dirty=List.copyOf(dirty);}}
 private final Path path;private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet terrain journal");t.setDaemon(true);return t;});
 private State next;private final List<CompletableFuture<Void>> waiters=new ArrayList<>();private boolean running,closed;
 public TerrainJournal(Path root)throws IOException{Files.createDirectories(root);if(Files.isSymbolicLink(root))throw new IOException("Unsafe terrain journal");path=root.resolve("schedule.json");}
 public State load()throws IOException{if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))return null;if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>2*1024*1024)throw new IOException("Invalid terrain journal");try{var state=Json.GSON.fromJson(Files.readString(path),State.class);validate(state);return state;}catch(RuntimeException bad){throw new IOException("Invalid terrain journal",bad);}}
 private static void validate(State s){if(s==null||s.version()!=1||s.generation()==null||s.generation().length()>128||s.jobs().size()>32||s.dirty().size()>4096)throw new IllegalArgumentException("Journal bounds");var ids=new HashSet<UUID>();for(var j:s.jobs()){if(j.id()==null||!ids.add(j.id())||j.offset()<0||j.rendered()<0||j.skipped()<0||j.total()< -1||!Set.of("QUEUED","READING","CAPTURING","WRITING","COMPLETE","FAILED","CANCELLED").contains(j.state()))throw new IllegalArgumentException("Invalid job");if(j.region()!=null){var r=j.region();dimension(r.dimension());long width=(long)r.maxChunkX()-r.minChunkX()+1,height=(long)r.maxChunkZ()-r.minChunkZ()+1;if(width<1||height<1||width>4096||height>4096||width*height>4096||j.total()!=width*height||j.offset()>j.total())throw new IllegalArgumentException("Invalid region job");}if(j.cursor()!=null){var c=j.cursor();dimension(c.dimension());if(c.slot()<0||c.slot()>1024||Math.abs((long)c.regionX())>58600||Math.abs((long)c.regionZ())>58600)throw new IllegalArgumentException("Invalid cursor");}}
  for(var c:s.dirty()){dimension(c.dimension());if(Math.abs((long)c.x())>1875000||Math.abs((long)c.z())>1875000)throw new IllegalArgumentException("Invalid chunk");}}
 private static void dimension(String s){if(s==null||s.length()>128||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||s.contains(".."))throw new IllegalArgumentException("Invalid dimension");}
 public synchronized CompletableFuture<Void> save(State state){if(closed)return CompletableFuture.failedFuture(new IOException("Journal closed"));validate(state);if(waiters.size()>=128)return CompletableFuture.failedFuture(new IOException("Journal queue full"));var f=new CompletableFuture<Void>();next=state;waiters.add(f);if(!running){running=true;worker.execute(this::drain);}return f;}
 private void drain(){while(true){State state;List<CompletableFuture<Void>> batch;synchronized(this){if(next==null){running=false;return;}state=next;next=null;batch=new ArrayList<>(waiters);waiters.clear();}try{byte[] bytes=Json.GSON.toJson(state).getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>2*1024*1024)throw new IOException("Journal too large");Path temp=Files.createTempFile(path.getParent(),"schedule-",".tmp");try{try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}if(Files.isSymbolicLink(path))throw new IOException("Unsafe journal target");Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}batch.forEach(f->f.complete(null));}catch(Exception error){batch.forEach(f->f.completeExceptionally(error));}}}
 @Override public void close(){synchronized(this){closed=true;}worker.shutdown();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))worker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();worker.shutdownNow();}}
}
