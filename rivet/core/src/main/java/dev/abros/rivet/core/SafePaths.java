package dev.abros.rivet.core;
import java.nio.file.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
public final class SafePaths {
    // Pack assets may use mod-defined folders. Local worlds, identities and runtime state are never pack assets.
    private static final Set<String> PROTECTED_ROOTS=Set.of(
        "rivet","saves","world","world_nether","world_the_end","screenshots","logs","crash-reports","debug",
        "backups","replay_recordings","recordings","downloads","cache","caches","libraries","versions",
        "assets","runtime","natives","launcher","launchers","patches","instances","accounts",
        "xaero","xaerominimap","xaeroworldmap","journeymap","voxelmap");
    private static final Set<String> CANONICAL_ROOTS=Set.of("mods","config","defaultconfigs","kubejs","resourcepacks","shaderpacks");
    private SafePaths(){}
    public static String key(String s){return Normalizer.normalize(s,Normalizer.Form.NFC).toLowerCase(Locale.ROOT);}
    public static void validate(String value){
        if(value==null||value.length()>240||!value.equals(Normalizer.normalize(value,Normalizer.Form.NFC))||value.contains("\\")||value.contains(":")||value.startsWith("/")||value.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Unsafe path: "+value);
        String[] parts=value.split("/",-1);
        if(parts.length<2||PROTECTED_ROOTS.contains(key(parts[0])))throw new IllegalArgumentException("Protected path: "+value);
        // These folders have additional mod/config semantics throughout Builder.
        if(CANONICAL_ROOTS.contains(key(parts[0]))&&!parts[0].equals(key(parts[0])))throw new IllegalArgumentException("Noncanonical pack folder: "+parts[0]);
        for(String p:parts)if(p.isEmpty()||p.startsWith(".")||p.endsWith(".")||p.endsWith(" ")||p.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?"))throw new IllegalArgumentException("Unsafe segment: "+p);
        if(key(parts[0]).equals("config")&&parts[1].toLowerCase(Locale.ROOT).startsWith("rivet"))throw new IllegalArgumentException("Protected Rivet configuration");
    }
    public static Path resolve(Path root,String value)throws IOException {
        validate(value);Path base=root.toRealPath();Path p=base.resolve(value).normalize();
        if(!p.startsWith(base))throw new IOException("Path escapes game directory");
        for(Path q=p;q!=null&&!q.equals(base);q=q.getParent()){
            if(Files.isSymbolicLink(q))throw new IOException("Symlink rejected: "+q);
            if(Files.exists(q,LinkOption.NOFOLLOW_LINKS)&&!q.toRealPath().startsWith(base))throw new IOException("Redirected path");
        }
        return p;
    }
}
