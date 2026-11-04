package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import java.nio.file.*;
import java.io.*;
public final class PackTrust {
 private final Path directory;
 public PackTrust(Path game)throws IOException{directory=game.resolve("rivet/pack-trust");PackPublisher.safe(directory);Files.createDirectories(directory);}
 private Path path(String server){return directory.resolve(Hashes.sha256(server.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json");}
 public String fingerprint(String server)throws IOException{Path path=path(server);PackPublisher.safe(path);return Files.exists(path)?Json.str(Json.read(path),"fingerprint"):"";}
 /** Called only after a pinned TLS connection succeeds and trust has been accepted. */
 public synchronized String canonical(String server,String fingerprint)throws IOException{
  Hashes.check(fingerprint);Path aliases=directory.resolve("identity-"+fingerprint+".json");PackPublisher.safe(aliases);
  if(Files.exists(aliases))return Json.str(Json.read(aliases),"server");
  Path state=directory.getParent().resolve("state.json");PackPublisher.safe(state);
  if(Files.exists(state)){String previous=Json.opt(Json.read(state),"packServer","");if(!previous.isBlank()&&fingerprint(previous).equals(fingerprint))server=previous;}
  Json.write(aliases,java.util.Map.of("server",server));return server;
 }
 public void accept(String server,String fingerprint)throws IOException{Hashes.check(fingerprint);Json.write(path(server),java.util.Map.of("fingerprint",fingerprint));}
}
