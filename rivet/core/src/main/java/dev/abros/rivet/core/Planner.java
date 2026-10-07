package dev.abros.rivet.core;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public final class Planner {
    public record Owned(String hash,String policy,String componentId){}
    public record Change(String path,String before,String after){}
    public record Plan(String id,String projectKey,List<Change> changes,Map<String,Owned> ownership,Set<String> selection,List<String> conflicts,long downloadBytes){}
    public static boolean configurable(String path){return path.startsWith("config/")||path.startsWith("defaultconfigs/");}
    public static String hash(Path p)throws IOException{if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return null;if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Not a regular file: "+p);return Hashes.sha256(p);}
}
