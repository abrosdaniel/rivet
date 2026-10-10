package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable UUID deadlines. Audio threads only read an immutable in-memory snapshot. */
public final class VoiceMutes {
 private final Path file;
 private volatile Map<UUID,Long> deadlines;
 public VoiceMutes(Path file,long now)throws IOException {
  this.file=file;var loaded=new HashMap<UUID,Long>();
  if(Files.exists(file)){
   if(Files.size(file)>512*1024)throw new IOException("Voice mute file exceeds limit");
   try{var body=Json.read(file);if(body.get("version").getAsInt()!=1)throw new IOException("Unsupported voice mute format");
    for(var entry:body.getAsJsonObject("players").entrySet()){var id=UUID.fromString(entry.getKey());long until=entry.getValue().getAsLong();if(until>now)loaded.put(id,until);}
    if(loaded.size()>4096)throw new IOException("Too many active voice mutes");
   }catch(RuntimeException ex){throw new IOException("Invalid voice mute file",ex);}
  }
  deadlines=Map.copyOf(loaded);
 }
 public boolean muted(UUID player,long now){return deadlines.getOrDefault(player,0L)>now;}
 public synchronized void mute(UUID player,int minutes,long now)throws IOException {
  if(minutes<=0||minutes>10080)throw new IllegalArgumentException("Invalid voice mute duration");
  if(muted(player,now))throw new IllegalArgumentException(Messages.text("rivet.core.already_muted_existing_punishment_preserved_809c6c08"));
  var next=new HashMap<UUID,Long>();deadlines.forEach((id,until)->{if(until>now)next.put(id,until);});
  if(next.size()>=4096)throw new IOException("Too many active voice mutes");
  next.put(Objects.requireNonNull(player),Math.addExact(now,minutes*60000L));
  var body=new JsonObject();body.addProperty("version",1);var players=new JsonObject();next.forEach((id,until)->players.addProperty(id.toString(),until));body.add("players",players);
  Json.write(file,body);deadlines=Map.copyOf(next);
 }
}
