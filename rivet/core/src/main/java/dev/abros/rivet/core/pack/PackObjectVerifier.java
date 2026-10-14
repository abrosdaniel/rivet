package dev.abros.rivet.core.pack;

import dev.abros.rivet.core.Hashes;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Bounded verification cache; changed/replaced objects must be hashed again. */
final class PackObjectVerifier {
 private record Stamp(Object key,long size,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,Object changed){}
 private record Verified(Stamp stamp,long at){}
 private final Map<String,Verified> verified=new LinkedHashMap<>(256,.75f,true);
 private static Stamp stamp(Path path)throws IOException{PackPublisher.safe(path);var a=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(!a.isRegularFile())throw new IOException("Invalid pack object");Object changed=null;
  // Basic timestamps and file keys can survive replacement (notably on Windows).
  // Reuse only with a filesystem change timestamp; otherwise verify every request.
  try{changed=Files.getAttribute(path,"unix:ctime",LinkOption.NOFOLLOW_LINKS);}catch(UnsupportedOperationException ignored){}
  return new Stamp(a.fileKey(),a.size(),a.lastModifiedTime(),a.creationTime(),changed);}
 synchronized void check(Path path,String hash,long size)throws IOException{
  var before=stamp(path);if(before.size!=size)throw new IOException("Invalid pack object size");var old=verified.get(hash);long now=System.nanoTime();
  if(before.key!=null&&before.changed!=null&&old!=null&&old.stamp.equals(before)&&now-old.at<30_000_000_000L)return;
  verified.remove(hash);if(!Hashes.sha256(path).equals(hash)||!before.equals(stamp(path)))throw new IOException("Published pack object changed or is corrupt");
  verified.put(hash,new Verified(before,now));while(verified.size()>256)verified.remove(verified.keySet().iterator().next());
 }
}
