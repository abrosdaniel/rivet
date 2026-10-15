package dev.abros.rivet.core.api;

import java.util.*;

/** Single server-thread ownership. Leases remain dirty until both image profiles finish. */
public final class TerrainDirtyQueue {
 public record Lease(TerrainJournal.Chunk chunk,long revision){}
 private static final class Entry {long revision,due;Entry(long revision,long due){this.revision=revision;this.due=due;}}
 private final LinkedHashMap<TerrainJournal.Chunk,Entry> entries=new LinkedHashMap<>();private long sequence;private boolean sweep;
 public synchronized void mark(TerrainJournal.Chunk chunk,long tick){var e=entries.get(chunk);if(e!=null){e.revision=++sequence;return;}if(entries.size()>=4096){sweep=true;return;}entries.put(chunk,new Entry(++sequence,tick+20));}
 public synchronized Lease poll(long tick){for(var e:entries.entrySet())if(e.getValue().due<=tick)return new Lease(e.getKey(),e.getValue().revision);return null;}
 public synchronized void complete(Lease lease){var e=entries.get(lease.chunk());if(e!=null){entries.remove(lease.chunk());if(e.revision!=lease.revision())entries.put(lease.chunk(),e);}}
 public synchronized void retry(Lease lease,long tick){var e=entries.remove(lease.chunk());if(e!=null){e.due=tick+200;entries.put(lease.chunk(),e);}}
 public synchronized List<TerrainJournal.Chunk> snapshot(){return List.copyOf(entries.keySet());}public synchronized int size(){return entries.size();}
 public synchronized boolean sweep(){return sweep;}public synchronized void sweep(boolean value){sweep=value;}
 public synchronized void restore(List<TerrainJournal.Chunk> chunks,boolean sweep){entries.clear();for(var c:chunks)mark(c,-20);this.sweep=sweep;}
}
