package dev.abros.rivet.core;

import dev.abros.rivet.api.NotificationsApi;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class NotificationCategoriesTest {
 @TempDir Path root;
 @Test void legacyAnnouncementsUseServerCategoryWithoutRewritingSavedNotices() throws Exception {
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
  var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
  var notice=Json.parse("{\"section\":\"home\",\"title\":\"Server notice\",\"routeSection\":\"server\",\"routeId\":\"\"}");
  db.communityTransaction(()->new NotificationStore(db).append(actor.id(),notice));
  assertEquals("server",NotificationCategories.source(notice));
  var list=store.request(actor,Json.parse("{\"section\":\"notifications\",\"op\":\"list\",\"category\":\"server\"}"));
  assertEquals(1,list.getAsJsonArray("entries").size());
  store.request(actor,Json.parse("{\"section\":\"notifications\",\"op\":\"preferences\",\"preferencePatch\":{\"category\":\"server\",\"muted\":true}}"));
  assertEquals(0,store.popupSequence(actor.id()));assertEquals(1,store.unread(actor.id()));
  var hud=store.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"hud\"}"));
  assertEquals("server",Json.str(hud.getAsJsonArray("digest").get(0).getAsJsonObject(),"sourceType"));
  assertTrue(NotificationCategories.muted(Json.parse("{\"muted\":[\"home\"]}"),notice));
 }
 @Test void independentPreferenceChangesPreserveOtherSourcesAndQuietMode() throws Exception {
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
  var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
  var first=Json.parse("{\"section\":\"notifications\",\"op\":\"preferences\",\"preferencePatch\":{\"category\":\"first:mail\",\"muted\":true}}");
  var second=Json.parse("{\"section\":\"notifications\",\"op\":\"preferences\",\"preferencePatch\":{\"category\":\"second:mail\",\"muted\":true}}");
  store.request(actor,first);store.request(actor,second);
  var snooze=Json.parse("{\"section\":\"notifications\",\"op\":\"preferences\",\"preferencePatch\":{\"snoozeUntil\":0}}");
  long until=System.currentTimeMillis()+3600000;snooze.getAsJsonObject("preferencePatch").addProperty("snoozeUntil",until);store.request(actor,snooze);
  first.getAsJsonObject("preferencePatch").addProperty("muted",false);store.request(actor,first);
  var prefs=store.preferences(actor.id());assertEquals(until,prefs.get("snoozeUntil").getAsLong());
  assertEquals(Json.parse("{\"muted\":[\"second:mail\"]}").get("muted"),prefs.get("muted"));
  store.request(actor,second);assertEquals(prefs,store.preferences(actor.id()));
  first.getAsJsonObject("preferencePatch").addProperty("muted","true");assertThrows(IllegalArgumentException.class,()->store.request(actor,first));
  assertEquals(prefs,store.preferences(actor.id()));
 }
 @Test void sourceFiltersAndMutePoliciesAgreeAcrossListHudAndStoredPreferences() throws Exception {
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());
  var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Reader",false,false);store.seen(actor);
  try(var publisher=new NotificationPublisher(db)) {
   var one=publisher.owner("first_mod").register("mail");var two=publisher.owner("second_mod").register("mail");
   var first=one.put(UUID.fromString(actor.id()),"one",new NotificationsApi.Message("First","Body"),0).join();
   var second=two.put(UUID.fromString(actor.id()),"two",new NotificationsApi.Message("Second","Body"),0).join();
   var list=Json.parse("{\"section\":\"notifications\",\"op\":\"list\",\"category\":\"first_mod:mail\"}");
   var page=store.request(actor,list);assertEquals(1,page.getAsJsonArray("entries").size());
   assertEquals("First",Json.str(page.getAsJsonArray("entries").get(0).getAsJsonObject(),"title"));
   assertTrue(page.getAsJsonArray("notificationCategories").contains(new com.google.gson.JsonPrimitive("second_mod:mail")));
   var save=Json.parse("{\"section\":\"notifications\",\"op\":\"preferences\",\"preferences\":{\"muted\":[\"second_mod:mail\"],\"snoozeUntil\":0}}");
   store.request(actor,save);var notices=new NotificationStore(db);
   assertEquals(first.id(),notices.popupSequence(actor.id()));assertEquals(2,notices.unread(actor.id()));
   var hud=store.request(actor,Json.parse("{\"section\":\"home\",\"op\":\"hud\",\"since\":0}"));
   for(var item:hud.getAsJsonArray("notices"))assertEquals(Json.str(item.getAsJsonObject(),"title").equals("Second"),NotificationCategories.muted(hud.getAsJsonObject("preferences"),item.getAsJsonObject()));
   save.getAsJsonObject("preferences").add("muted",Json.parse("{\"values\":[\"notifications\"]}").get("values"));store.request(actor,save);
   assertEquals(0,notices.popupSequence(actor.id()));
   list.addProperty("category","notifications");assertEquals(2,store.request(actor,list).getAsJsonArray("entries").size());
   two.revoke(UUID.fromString(actor.id()),"two",second.revision()).join();
   // A preference remains available even when its last retained notification is gone.
   save.getAsJsonObject("preferences").add("muted",Json.parse("{\"values\":[\"second_mod:mail\"]}").get("values"));store.request(actor,save);
   assertTrue(store.request(actor,list).getAsJsonArray("notificationCategories").contains(new com.google.gson.JsonPrimitive("second_mod:mail")));
  }
 }
}
