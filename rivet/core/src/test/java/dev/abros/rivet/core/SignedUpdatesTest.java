package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SignedUpdatesTest {
 @TempDir Path root;
 static JsonObject sign(JsonObject payload,PrivateKey key)throws Exception{byte[] bytes=payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);var signature=Signature.getInstance("Ed25519");signature.initSign(key);signature.update(bytes);var envelope=new JsonObject();envelope.addProperty("payload",Base64.getEncoder().encodeToString(bytes));envelope.addProperty("signature",Base64.getEncoder().encodeToString(signature.sign()));return envelope;}
 @Test void tamperingAndWrongSignerAreRejected()throws Exception{
  var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var payload=Json.parse("{\"version\":\"2.10.0\"}");var signed=sign(payload,key.getPrivate());assertEquals(payload,SignedUpdates.verify(signed,key.getPublic()));
  assertThrows(java.io.IOException.class,()->SignedUpdates.verify(signed,KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()));signed.addProperty("payload",Base64.getEncoder().encodeToString("{}".getBytes()));assertThrows(java.io.IOException.class,()->SignedUpdates.verify(signed,key.getPublic()));
 }
 @Test void mirrorCatalogWorksWithoutGithubApiAndRejectsCorruptPrimary()throws Exception{
  var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();var fixture=new CoreUpdaterTest();var payload=new JsonObject();payload.add("descriptor",fixture.descriptor("1.1.0","21.1.250"));var mirrors=new JsonArray();mirrors.add("https://mirror.example.org/release.jar");payload.add("mirrors",mirrors);var release=sign(payload,key.getPrivate());
  var catalog=new JsonObject();catalog.addProperty("schemaVersion",1);var list=new JsonArray();list.add(release);catalog.add("releases",list);var signed=sign(catalog,key.getPrivate());var requests=new ArrayList<String>();
  var remote=new Remote(){@Override public byte[] bytes(String url,int limit)throws java.io.IOException{requests.add(url);if(url.equals(SignedUpdates.SOURCES.get(1)+"catalog.json"))return signed.toString().getBytes();throw new java.io.IOException("Unavailable");}};
  var result=new CoreUpdater(remote,root,key.getPublic()).catalog("1.0.0","1.21.1","21.1.250",100,false,true);assertFalse(result.partial());assertEquals("1.1.0",result.updates().getFirst().version());assertEquals(2,result.updates().getFirst().artifact().urls().size());assertFalse(requests.contains(CoreUpdater.RELEASES));
  var decoded=SignedUpdates.update(release,"1.21.1","21.1.249",key.getPublic());assertTrue(decoded.isEmpty());
 }
}
