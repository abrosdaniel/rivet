package dev.abros.rivet.core;
import dev.abros.rivet.core.skins.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class CapeStoreTest {
 @TempDir Path temp;
 private static com.google.gson.JsonObject input(String id){var j=new com.google.gson.JsonObject();j.addProperty("id",id);j.addProperty("name","Cape");return j;}
 @Test void ownershipPersistenceHideDefaultAndSharedBlobCleanup()throws Exception{
  var db=TestDatabase.database(temp);var settings=new SkinSettings(true,1,8,"false");var store=new SkinStore(db,settings);var a=UUID.randomUUID();var b=UUID.randomUUID();var png=SkinImageTest.png(64,32);
  store.mutate(a,"capeUpload",input(""),png);store.mutate(b,"capeUpload",input(""),png);
  var library=store.library(a).getAsJsonObject("capes");String id=Json.str(library,"active"),hash=Json.str(store.appearance(a),"capeHash");assertFalse(hash.isEmpty());assertArrayEquals(CapeImage.normalize(png,262144),store.capeBytes(hash));
  assertThrows(IllegalArgumentException.class,()->store.mutate(b,"capeSelect",input(id),null));
  store.mutate(a,"capeHide",input(""),null);assertTrue(store.appearance(a).get("capeHidden").getAsBoolean());assertEquals("",Json.str(store.appearance(a),"capeHash"));
  var reopened=new SkinStore(db,settings);assertTrue(reopened.appearance(a).get("capeHidden").getAsBoolean());reopened.mutate(a,"capeSelect",input(id),null);assertEquals(hash,Json.str(reopened.appearance(a),"capeHash"));
  store.mutate(a,"capeDelete",input(id),null);assertEquals("",Json.str(store.appearance(a),"capeHash"));assertTrue(store.capeBytes(hash).length>0);
  String second=Json.str(store.library(b).getAsJsonObject("capes"),"active");store.mutate(b,"capeDelete",input(second),null);assertThrows(IllegalArgumentException.class,()->store.capeBytes(hash));
 }
 @Test void dedupQuotaDisabledAndReceipts()throws Exception{
  var db=TestDatabase.database(temp);var settings=new SkinSettings(true,1,8,"false",true,1,256);var store=new SkinStore(db,settings);var owner=UUID.randomUUID();var request=input("");request.addProperty("operationId",UUID.randomUUID().toString());request.addProperty("issuedAt",System.currentTimeMillis());
  var png=SkinImageTest.png(64,32);store.mutate(owner,"capeUpload",request,png);assertTrue(store.mutate(owner,"capeUpload",request,png).get("replayed").getAsBoolean());store.mutate(owner,"capeUpload",input(""),png);assertEquals(1,store.library(owner).getAsJsonObject("capes").getAsJsonArray("entries").size());
  var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));image.setRGB(1,1,0xff123456);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);assertThrows(IllegalArgumentException.class,()->store.mutate(owner,"capeUpload",input(""),bytes.toByteArray()));
  var disabled=new SkinStore(db,new SkinSettings(true,1,8,"false",false,1,256));assertThrows(IllegalArgumentException.class,()->disabled.mutate(owner,"capeHide",input(""),null));assertEquals("",Json.str(disabled.appearance(owner),"capeHash"));
  store.mutate(owner,"capeSelect",input(""),null);assertEquals("",Json.str(store.appearance(owner),"capeHash"));assertEquals(1,store.library(owner).getAsJsonObject("capes").getAsJsonArray("entries").size());
 }
}
