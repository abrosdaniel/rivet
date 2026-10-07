package dev.abros.rivet.core;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
public final class Cache {
    public record Artifact(String path, java.util.List<String> urls,String sha256,long size){}
    // Bounded locks coordinate Cache instances without retaining every hash ever seen.
    private static final java.util.concurrent.locks.ReentrantLock[] LOCKS=new java.util.concurrent.locks.ReentrantLock[256];
    static {java.util.Arrays.setAll(LOCKS,i->new java.util.concurrent.locks.ReentrantLock());}
    private static final System.Logger LOG=System.getLogger(Cache.class.getName());
    private final Path root;private final Remote remote;
    public Cache(Path game,Remote remote)throws IOException{root=game.resolve("rivet/cache/objects");for(Path p=root;p!=null&&!p.equals(game);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symlink in cache path");Files.createDirectories(root);this.remote=remote;}
    public Path path(String hash){Hashes.check(hash);Path p=root.resolve(hash.substring(0,2)).resolve(hash);if(Files.isSymbolicLink(p)||Files.isSymbolicLink(p.getParent()))throw new IllegalArgumentException("Symlink in cache path");return p;}
    public boolean contains(String hash)throws IOException{Path p=path(hash);return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Hashes.sha256(p).equals(hash);}
    public long clearUnused(java.util.Set<String> protectedHashes)throws IOException{
        return clearUnused(protectedHashes,0);
    }
    private long clearUnused(java.util.Set<String> protectedHashes,long objectCutoff)throws IOException{
        long freed=0;
        try(var files=Files.walk(root)){
            for(Path p:files.filter(f->Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)).toList()){
                String name=p.getFileName().toString();String hash=name.split("\\.",2)[0];
                if(!hash.matches("[0-9a-f]{64}"))continue;
                var lock=LOCKS[Math.floorMod(path(hash).toAbsolutePath().normalize().hashCode(),LOCKS.length)];
                if(!lock.tryLock())continue;
                try(var lease=lease(path(hash),new AtomicBoolean(),false)){
                    if(lease==null)continue;
                    boolean object=name.equals(hash);
                    boolean partial=name.matches("[0-9a-f]{64}\\.([0-9a-f]{64}|pack)\\.part(\\.etag)?");
                    if(Files.exists(p)&&((object&&!protectedHashes.contains(hash)&&(objectCutoff==0||Files.getLastModifiedTime(p).toMillis()<objectCutoff))||(partial&&Files.getLastModifiedTime(p).toMillis()<System.currentTimeMillis()-java.time.Duration.ofDays(7).toMillis()))){freed+=Files.size(p);Files.delete(p);}
                }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Cache cleanup interrupted",interrupted);}finally{lock.unlock();}
            }
        }return freed;
    }
    /** Startup housekeeping: pending/recovery installs and saved server preferences are never removed. */
    public long cleanup(Path game)throws IOException{
        Path data=game.toRealPath().resolve("rivet");
        try(var prepare=java.nio.channels.FileChannel.open(data.resolve("prepare.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            var apply=java.nio.channels.FileChannel.open(data.resolve("apply.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            try(var preparing=prepare.tryLock();var applying=apply.tryLock()){
                if(preparing==null||applying==null||Files.exists(data.resolve("pending.json"),LinkOption.NOFOLLOW_LINKS))return 0;
                var protectedHashes=new java.util.HashSet<String>();
                Path state=data.resolve("state.json");
                if(Files.isSymbolicLink(state))throw new IOException("Symlink in cache state");
                if(Files.exists(state)){
                    var saved=Json.read(state);
                    if(saved.has("ownership"))for(var entry:saved.getAsJsonObject("ownership").entrySet()){
                        String hash=Json.str(entry.getValue().getAsJsonObject(),"hash");Hashes.check(hash);protectedHashes.add(hash);
                    }
                }
                Path transactions=data.resolve("transactions");
                if(Files.isSymbolicLink(transactions))throw new IOException("Symlink in transactions");
                if(Files.exists(transactions))try(var dirs=Files.list(transactions)){
                    for(Path dir:dirs.toList()){
                        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid transaction directory");
                        Path journal=dir.resolve("journal.json");if(Files.isSymbolicLink(journal))throw new IOException("Symlink in transaction journal");
                        String status=Json.str(Json.read(journal),"status");
                        if(!java.util.Set.of("COMMITTED","CANCELLED","ROLLED_BACK").contains(status))return 0;
                    }
                }
                return clearUnused(protectedHashes,System.currentTimeMillis()-java.time.Duration.ofDays(7).toMillis());
            }
        }catch(java.nio.channels.OverlappingFileLockException busy){return 0;}
        catch(RuntimeException invalid){throw new IOException("Invalid cache protection state",invalid);}
    }
    @FunctionalInterface public interface ObjectWriter {void write(Path object)throws IOException;}
    /** Shares the download lease with cleanup and other cache users. */
    public void write(String hash,AtomicBoolean cancel,ObjectWriter writer)throws IOException{
        var lock=LOCKS[Math.floorMod(path(hash).toAbsolutePath().normalize().hashCode(),LOCKS.length)];
        try{
            while(!lock.tryLock(100,java.util.concurrent.TimeUnit.MILLISECONDS))if(cancel.get())throw new IOException("Cancelled");
            try(var lease=lease(path(hash),cancel,true)){if(cancel.get())throw new IOException("Cancelled");writer.write(path(hash));}finally{lock.unlock();}
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Cache write interrupted",interrupted);}
    }
    private record Lease(java.nio.channels.FileChannel channel,java.nio.channels.FileLock lock) implements AutoCloseable{
        public void close()throws IOException{try{lock.release();}finally{channel.close();}}
    }
    private Lease lease(Path object,AtomicBoolean cancel,boolean wait)throws IOException,InterruptedException{
        Path directory=root.resolve("locks");Files.createDirectories(directory);
        if(Files.isSymbolicLink(directory))throw new IOException("Symlink in cache locks");
        Path file=directory.resolve(Integer.toString(Math.floorMod(object.getFileName().toString().hashCode(),LOCKS.length))+".lock");
        var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{while(true){
            if(cancel.get())throw new IOException("Cancelled");
            java.nio.channels.FileLock lock=null;try{lock=channel.tryLock();}catch(java.nio.channels.OverlappingFileLockException busy){}
            if(lock!=null)return new Lease(channel,lock);
            if(!wait){channel.close();return null;}Thread.sleep(100);
        }}catch(IOException|InterruptedException|RuntimeException ex){channel.close();throw ex;}
    }
    public Path obtain(Artifact f,AtomicBoolean cancel)throws IOException,InterruptedException{return obtain(f,cancel,index->{});}
    public Path obtain(Artifact f,AtomicBoolean cancel,IntConsumer sourceChanged)throws IOException,InterruptedException{
        return obtain(f,cancel,sourceChanged,null);
    }
    public Path obtain(Artifact f,AtomicBoolean cancel,IntConsumer sourceChanged,Remote.Progress progress)throws IOException,InterruptedException{
        var lock=LOCKS[Math.floorMod(path(f.sha256()).toAbsolutePath().normalize().hashCode(),LOCKS.length)];
        while(!lock.tryLock(100,java.util.concurrent.TimeUnit.MILLISECONDS))if(cancel.get())throw new IOException("Cancelled");
        try(var lease=lease(path(f.sha256()),cancel,true)){return obtainLocked(f,cancel,sourceChanged,progress);}finally{lock.unlock();}
    }
    private Path obtainLocked(Artifact f,AtomicBoolean cancel,IntConsumer sourceChanged,Remote.Progress progress)throws IOException,InterruptedException{
        if(cancel.get())throw new IOException("Cancelled");
        Path p=path(f.sha256());if(contains(f.sha256()))return p;Files.createDirectories(p.getParent());
        IOException failures=new IOException("All download sources failed: "+f.path());
        var deferred=new java.util.HashMap<Integer,Remote.CoolingDown>();
        var order=java.util.stream.IntStream.range(0,f.urls().size()).boxed().toList();
        for(int pass=0;pass<2;pass++)for(int index:order){
            if(pass==1&&!deferred.containsKey(index))continue;
            if(cancel.get())throw new IOException("Cancelled");
            // Source-specific partials permit safe resume without mixing different mirrors.
            String url=f.urls().get(index);
            Path tmp=p.resolveSibling(p.getFileName()+"."+Hashes.sha256(url.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".part");
            Path etag=tmp.resolveSibling(tmp.getFileName()+".etag");
            sourceChanged.accept(index);
            try{
                if(Files.isSymbolicLink(tmp)||Files.isSymbolicLink(etag))throw new IOException("Symlink in partial download path");
                if(Files.exists(tmp)&&Files.size(tmp)==f.size()&&Hashes.sha256(tmp).equals(f.sha256())){Json.move(tmp,p);Files.deleteIfExists(etag);return p;}
                if(pass==1)deferred.get(index).await(cancel);
                if(progress==null)remote.download(url,tmp,f.size(),cancel);else remote.download(url,tmp,f.size(),cancel,progress);
                if(cancel.get())throw new IOException("Cancelled");
                if(Files.size(tmp)!=f.size()||!Hashes.sha256(tmp).equals(f.sha256())){
                    Files.deleteIfExists(tmp);Files.deleteIfExists(etag);throw new IOException("HASH_MISMATCH: "+f.path());
                }
                Json.move(tmp,p);Files.deleteIfExists(etag);return p;
            }catch(IOException ex){
                if(cancel.get())throw ex;
                if(ex instanceof Remote.CoolingDown cooldown)deferred.put(index,cooldown);
                failures.addSuppressed(ex);
                LOG.log(System.Logger.Level.WARNING,"Download source "+(index+1)+" failed for "+f.path()+" ("+Remote.https(url).getHost()+")",ex);
            }
        }
        throw failures;
    }
}
