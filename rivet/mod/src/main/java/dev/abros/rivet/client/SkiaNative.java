package dev.abros.rivet.client;

import java.nio.file.*;
import java.util.Locale;
import dev.abros.rivet.core.Hashes;

/** Loads only verified resources distributed inside Rivet; never downloads executable code. */
final class SkiaNative {
    private SkiaNative(){}
    static void load(){
        String os=System.getProperty("os.name").toLowerCase(Locale.ROOT),arch=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String platform=os.contains("mac")?"macos":os.contains("win")?"windows":os.contains("linux")?"linux":null;
        String cpu=switch(arch){case "aarch64","arm64"->"arm64";case "amd64","x86_64"->"x64";default->null;};
        if(platform==null||cpu==null)throw new IllegalStateException("Unsupported Skia platform: "+os+" / "+arch);
        String library=platform.equals("windows")?"skija.dll":platform.equals("macos")?"libskija.dylib":"libskija.so";
        String prefix="/io/github/humbleui/skija/"+platform+"/"+cpu+"/";
        try{
            // A versioned cache avoids a new undeletable loaded DLL in Windows temp on every launch.
            Path directory=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath();
            for(String part:new String[]{"rivet","cache","native","skija-0.143.18",platform,cpu}){
                directory=directory.resolve(part);
                if(Files.isSymbolicLink(directory))throw new java.io.IOException("Unsafe native cache directory");
                Files.createDirectories(directory);
            }
            extract(prefix,library,directory);
            if(platform.equals("windows"))extract("/io/github/humbleui/skija/windows/shared/","icudtl.dat",directory);
            // Skija's library module cannot see resources in NeoForge's transformed mod layer.
            synchronized(io.github.humbleui.skija.impl.Library.class){
                String before=System.getProperty("skija.library.path");
                try{System.setProperty("skija.library.path",directory.toAbsolutePath().toString());io.github.humbleui.skija.impl.Library.load();}
                finally{if(before==null)System.clearProperty("skija.library.path");else System.setProperty("skija.library.path",before);}
            }
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load bundled Skia",e);}
    }
    private static void extract(String prefix,String name,Path directory)throws java.io.IOException{
        try(var stream=SkiaNative.class.getResourceAsStream(prefix+name)){
            if(stream==null)throw new java.io.IOException("Missing bundled Skia resource: "+prefix+name);
            byte[] bytes=stream.readAllBytes();Path file=directory.resolve(name);
            if(Files.isSymbolicLink(file))throw new java.io.IOException("Unsafe native cache file");
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Hashes.sha256(file).equals(Hashes.sha256(bytes)))return;
            Path temporary=Files.createTempFile(directory,"extract-",".tmp");
            try{
                Files.write(temporary,bytes);
                try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException ex){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
        }
    }
}
