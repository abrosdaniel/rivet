package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.function.BiPredicate;

/** Write-ahead escrow snapshot. The player-file receipt is the inventory commit point.
 * Never discard an unresolved transfer or fall back to an older escrow snapshot. */
public final class TradeJournal {
    private final Path directory;
    public TradeJournal(Path directory) throws IOException { this.directory=directory; Files.createDirectories(directory); }
    private Path file(String name){return directory.resolve(name+".json");}
    private JsonObject read(Path path)throws IOException {if(Files.size(path)>64*1024*1024)throw new IOException("Trade journal capacity exceeded");return Json.parse(Files.readString(path),64*1024*1024);}
    public JsonObject load() throws IOException {return Files.exists(file("book"))?read(file("book")):new JsonObject();}
    public JsonObject recover(BiPredicate<String,String> receipt) throws IOException {
        if(Files.exists(file("pending"))){
            var pending=read(file("pending"));
            if(pending.get("player").getAsString().isEmpty()||receipt.test(pending.get("player").getAsString(),pending.get("receipt").getAsString()))
                write(file("book"),pending.getAsJsonObject("next"));
            Files.delete(file("pending")); forceDirectory();
        }
        return load();
    }
    public void prepare(JsonObject next,String player,String receipt) throws IOException {
        if(Files.exists(file("pending")))throw new IOException("Unresolved trade transfer");
        var pending=new JsonObject();pending.addProperty("player",player);pending.addProperty("receipt",receipt);pending.add("next",next);
        write(file("pending"),pending);
    }
    public void commit() throws IOException {
        var pending=read(file("pending"));write(file("book"),pending.getAsJsonObject("next"));Files.delete(file("pending"));forceDirectory();
    }
    public void abort() throws IOException {Files.deleteIfExists(file("pending"));forceDirectory();}
    private void write(Path destination,JsonObject value)throws IOException{
        byte[] data=Json.GSON.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if(data.length>64*1024*1024)throw new IOException("Trade journal capacity reached");
        Path tmp=Files.createTempFile(directory,"trade-",".tmp");
        try{try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(data);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);forceDirectory();
        }finally{Files.deleteIfExists(tmp);}
    }
    private void forceDirectory()throws IOException{
        // Windows does not permit opening directories as file channels. Atomic replacement
        // still applies there; individual files are flushed before replacement.
        if(System.getProperty("os.name","").startsWith("Windows"))return;
        try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
    }
}
