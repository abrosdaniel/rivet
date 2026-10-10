package dev.abros.rivet.bootstrap;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
/** Acquired before normal mods are discovered; retained until the JVM terminates. */
public final class LaunchGate {
    private FileChannel channel;private FileLock lock;
    public void enter(Path game)throws IOException{
        if(channel!=null)throw new IllegalStateException("Launch gate already entered");
        Path data=game.toRealPath().resolve("rivet");if(Files.isSymbolicLink(data))throw new IOException("Unsafe Rivet directory");Files.createDirectories(data);
        Path pending=data.resolve("pending.json"),lockPath=data.resolve("apply.lock");
        if(Files.exists(pending))throw new IOException("Rivet is applying or recovering an update. Wait for the helper to finish, then launch Minecraft again. See rivet/runtime/helper.log.");
        if(Files.isSymbolicLink(lockPath))throw new IOException("Unsafe update lock");
        channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE);
        try {
        try{lock=channel.tryLock(0,Long.MAX_VALUE,true);}catch(OverlappingFileLockException busy){throw new IOException("Rivet update lock is already held",busy);}
        if(lock==null||Files.exists(pending))throw new IOException("Rivet update in progress. Launch Minecraft again after completion.");
        Path epoch=data.resolve("mutation.epoch");
        if(Files.exists(epoch)&&Files.getLastModifiedTime(epoch).toInstant().isAfter(ProcessHandle.current().info().startInstant().orElseThrow()))throw new IOException("Rivet completed an update while this launch was starting. Start Minecraft again to load a consistent pack.");
        }catch(IOException|RuntimeException|Error failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public void close()throws IOException{var opened=channel;channel=null;lock=null;if(opened!=null)opened.close();}
}
