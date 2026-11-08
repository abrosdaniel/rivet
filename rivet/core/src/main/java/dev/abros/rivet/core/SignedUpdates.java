package dev.abros.rivet.core;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Release signatures authenticate mirrors and Builder offers independently of their TLS peer. */
public final class SignedUpdates {
 public static final List<String> SOURCES=List.of("https://raw.githubusercontent.com/abrosdaniel/rivet/updates/","https://abrosdaniel.github.io/rivet/updates/");
 private SignedUpdates(){}
 static PublicKey key()throws Exception{try(var in=SignedUpdates.class.getResourceAsStream("/rivet/update-key.txt")){if(in==null)throw new IOException("Missing update signing key");return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(new String(in.readAllBytes(),StandardCharsets.UTF_8).trim())));}}
 public static JsonObject verify(JsonObject envelope)throws IOException{try{return verify(envelope,key());}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid update signature",e);}}
 static JsonObject verify(JsonObject envelope,PublicKey key)throws IOException{
  try{Json.keys(envelope,"payload","signature");String encoded=Json.str(envelope,"payload");if(encoded.length()>6*1024*1024)throw new IOException("Update metadata limit");byte[] payload=Base64.getDecoder().decode(encoded),signature=Base64.getDecoder().decode(Json.str(envelope,"signature"));var verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(payload);if(!verifier.verify(signature))throw new IOException("Invalid update signature");return Json.parse(new String(payload,StandardCharsets.UTF_8));}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid signed update metadata",e);}
 }
 public static Optional<CoreUpdater.Update> update(JsonObject envelope,String minecraft,String loader)throws IOException{
  try{return update(envelope,minecraft,loader,key());}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Invalid signed release",e);}
 }
 static Optional<CoreUpdater.Update> update(JsonObject envelope,String minecraft,String loader,PublicKey key)throws IOException{
  var signed=verify(envelope,key);Json.keys(signed,"descriptor","mirrors");var descriptor=signed.getAsJsonObject("descriptor");Schema.validate("core-release",descriptor);String version=Json.str(descriptor,"version");if(!Versions.isRelease(version))throw new IOException("Invalid release version");
  for(var item:descriptor.getAsJsonArray("artifacts")){var a=item.getAsJsonObject();String minimum=Json.str(a,"neoForge");String[] actual=loader.split("\\."),required=minimum.split("\\.");
   if(!Json.str(a,"minecraft").equals(minecraft)||actual.length!=3||required.length!=3||!actual[0].equals(required[0])||!actual[1].equals(required[1])||Versions.compare(loader,minimum)<0||a.get("java").getAsInt()>Runtime.version().feature())continue;
   var urls=new ArrayList<String>();urls.add(Json.str(a,"url"));if(signed.has("mirrors")){var mirrors=signed.getAsJsonArray("mirrors");if(mirrors.size()>8)throw new IOException("Too many update mirrors");for(var mirror:mirrors)urls.add(mirror.getAsString());}for(String url:urls)Remote.https(url);
   long size=a.get("size").getAsLong();if(size<1||size>256L*1024*1024)throw new IOException("Update size limit");String hash=Json.str(a,"sha256");Hashes.check(hash);
   return Optional.of(new CoreUpdater.Update(version,new Cache.Artifact("mods/rivet-"+version+"-mc"+minecraft+"-neoforge.jar",List.copyOf(urls),hash,size),WireProtocols.compatible(descriptor.getAsJsonObject("protocols")),descriptor.getAsJsonObject("protocols"),envelope));
  }return Optional.empty();
 }
 public static void validate(CoreUpdater.Update update)throws IOException{
  var signed=verify(update.proof());var descriptor=signed.getAsJsonObject("descriptor");String loader=descriptor.getAsJsonArray("artifacts").get(0).getAsJsonObject().get("neoForge").getAsString();
  var expected=update(update.proof(),"1.21.1",loader).orElseThrow(()->new IOException("Signed release has no matching artifact"));
  if(!expected.version().equals(update.version())||!expected.artifact().equals(update.artifact())||!expected.protocols().equals(update.protocols()))throw new IOException("Update differs from signed release");
 }
}
