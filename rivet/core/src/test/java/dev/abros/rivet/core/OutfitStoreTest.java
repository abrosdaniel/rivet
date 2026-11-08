package dev.abros.rivet.core;
import dev.abros.rivet.core.skins.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class OutfitStoreTest {
 @TempDir Path temp;
 private static com.google.gson.JsonObject input(String id){var j=new com.google.gson.JsonObject();j.addProperty("id",id);j.addProperty("name","Outfit");return j;}
 @Test void appliesOwnedCombinationAtomicallyAndSurvivesReopen()throws Exception{
  var db=TestDatabase.database(temp);var settings=new SkinSettings(true,1,8,"false");var s=new SkinStore(db,settings);var owner=UUID.randomUUID();s.upload(owner,"Skin",true,SkinImageTest.png(64,64));String skin=Json.str(s.appearance(owner),"active");s.mutate(owner,"capeUpload",input(""),SkinImageTest.png(64,32));String cape=Json.str(s.library(owner).getAsJsonObject("capes"),"active");s.mutate(owner,"outfitSave",input(""),null);String outfit=Json.str(s.library(owner).getAsJsonArray("outfits").get(0).getAsJsonObject(),"id");
  s.change(owner,"select","",false);s.mutate(owner,"capeHide",input(""),null);s=new SkinStore(db,settings);s.mutate(owner,"outfitSelect",input(outfit),null);assertEquals(skin,Json.str(s.appearance(owner),"active"));assertEquals(cape,Json.str(s.library(owner).getAsJsonObject("capes"),"active"));assertFalse(s.appearance(owner).get("capeHidden").getAsBoolean());assertTrue(s.library(owner).getAsJsonArray("outfits").get(0).getAsJsonObject().get("selected").getAsBoolean());
  final var store=s;assertThrows(IllegalArgumentException.class,()->store.mutate(UUID.randomUUID(),"outfitSelect",input(outfit),null));
  s.mutate(owner,"capeDelete",input(cape),null);s.change(owner,"select","",false);assertThrows(IllegalArgumentException.class,()->store.mutate(owner,"outfitSelect",input(outfit),null));assertEquals("",Json.str(s.appearance(owner),"active"));assertFalse(s.library(owner).getAsJsonArray("outfits").get(0).getAsJsonObject().get("available").getAsBoolean());
  s.mutate(owner,"outfitDelete",input(outfit),null);assertTrue(s.library(owner).getAsJsonArray("outfits").isEmpty());assertEquals(1,s.library(owner).getAsJsonArray("entries").size());
 }
 @Test void quotaAndReceiptDoNotDuplicateSavedOutfits()throws Exception{
  var s=new SkinStore(TestDatabase.database(temp),new SkinSettings(true,1,8,"false",true,8,256,1));var owner=UUID.randomUUID();var request=input("");request.addProperty("operationId",UUID.randomUUID().toString());request.addProperty("issuedAt",System.currentTimeMillis());s.mutate(owner,"outfitSave",request,null);assertTrue(s.mutate(owner,"outfitSave",request,null).get("replayed").getAsBoolean());assertThrows(IllegalArgumentException.class,()->s.mutate(owner,"outfitSave",input(""),null));assertEquals(1,s.library(owner).getAsJsonArray("outfits").size());
 }
 @Test void undoRestoresWholeOutfitOnceAcrossReopen()throws Exception{
  var db=TestDatabase.database(temp);var settings=new SkinSettings(true,1,8,"false");var owner=UUID.randomUUID();var s=new SkinStore(db,settings);
  s.mutate(owner,"upload",skinInput(),SkinImageTest.png(64,64));s.mutate(owner,"capeUpload",input(""),SkinImageTest.png(64,32));s.mutate(owner,"outfitSave",input(""),null);
  String outfit=Json.str(s.library(owner).getAsJsonArray("outfits").get(0).getAsJsonObject(),"id");
  s.mutate(owner,"select",input(""),null);s.mutate(owner,"capeHide",input(""),null);var before=s.appearance(owner);
  s.mutate(owner,"outfitSelect",input(outfit),null);assertTrue(s.library(owner).get("canUndoAppearance").getAsBoolean());
  var reopened=new SkinStore(db,settings);var request=input("");request.addProperty("operationId",UUID.randomUUID().toString());request.addProperty("issuedAt",System.currentTimeMillis());
  reopened.mutate(owner,"appearanceUndo",request,null);assertEquals(before,reopened.appearance(owner));assertFalse(reopened.library(owner).get("canUndoAppearance").getAsBoolean());
  assertTrue(reopened.mutate(owner,"appearanceUndo",request,null).get("replayed").getAsBoolean());assertThrows(IllegalArgumentException.class,()->reopened.mutate(owner,"appearanceUndo",input(""),null));
 }
 @Test void capeOnlyUndoAndNoOpKeepThePreviousSelection()throws Exception{
  var s=new SkinStore(TestDatabase.database(temp),new SkinSettings(true,1,8,"false"));var owner=UUID.randomUUID();
  s.mutate(owner,"capeUpload",input(""),SkinImageTest.png(64,32));String cape=Json.str(s.library(owner).getAsJsonObject("capes"),"active");
  s.mutate(owner,"capeHide",input(""),null);s.mutate(owner,"capeHide",input(""),null);s.mutate(owner,"appearanceUndo",input(""),null);
  assertFalse(s.appearance(owner).get("capeHidden").getAsBoolean());assertEquals(cape,Json.str(s.library(owner).getAsJsonObject("capes"),"active"));
 }
 @Test void deletedPreviousComponentAndForeignOwnerCannotUndo()throws Exception{
  var s=new SkinStore(TestDatabase.database(temp),new SkinSettings(true,1,8,"false"));var owner=UUID.randomUUID();
  s.mutate(owner,"capeUpload",input(""),SkinImageTest.png(64,32));String cape=Json.str(s.library(owner).getAsJsonObject("capes"),"active");
  s.mutate(owner,"capeSelect",input(""),null);s.mutate(owner,"capeDelete",input(cape),null);
  assertFalse(s.library(owner).get("canUndoAppearance").getAsBoolean());assertThrows(IllegalArgumentException.class,()->s.mutate(owner,"appearanceUndo",input(""),null));
  assertThrows(IllegalArgumentException.class,()->s.mutate(UUID.randomUUID(),"appearanceUndo",input(""),null));
 }
 private static com.google.gson.JsonObject skinInput(){var j=input("");j.addProperty("slim",true);return j;}
}
