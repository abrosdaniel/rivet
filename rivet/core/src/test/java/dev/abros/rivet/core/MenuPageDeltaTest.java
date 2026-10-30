package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPageDeltaTest {
 @Test void reorderedDuplicatesAndPermissionRemovalCannotRestoreStaleCache(){
  var t=new MenuTransport<Object>();t.pageDeltas(true);var owner=new Object();var first=page("a","user");first.addProperty("canEdit",true);
  t.enqueue(query("a"),owner,false,0);t.dispatch(0,owner);t.receive(first,1);
  t.enqueue(query("b"),owner,false,600);var b=t.dispatch(600,owner).getFirst();t.enqueue(query("c"),owner,false,1200);var c=t.dispatch(1200,owner).getFirst();
  var latest=page("c","user");var packet=MenuPageDelta.encode(latest,c.getAsJsonObject("knownPage"));assertEquals(latest,t.receive(packet,1201));assertNull(t.receive(packet,1202));
  first.addProperty("request","b");t.receive(MenuPageDelta.encode(first,b.getAsJsonObject("knownPage")),1203);assertFalse(t.cached(query("c"),1204).has("canEdit"));
  t.enqueue(query("d"),owner,false,1800);var d=t.dispatch(1800,owner).getFirst();latest.addProperty("request","d");assertNull(t.receive(MenuPageDelta.encode(latest,d.getAsJsonObject("knownPage")),16800));
 }
 static JsonObject query(String request){var q=new JsonObject();q.addProperty("action","community");q.addProperty("op","list");q.addProperty("section","notifications");q.addProperty("request",request);return q;}
 static JsonObject page(String request,String owner){var p=new JsonObject();p.addProperty("kind","community");p.addProperty("request",request);p.addProperty("page",0);p.addProperty("nextCursor","");var rows=new JsonArray();for(int i=0;i<20;i++){var row=new JsonObject();row.addProperty("id",owner+"-"+i);row.addProperty("text",owner+" private "+"body ".repeat(60));rows.add(row);}p.add("entries",rows);return p;}
 @Test void patchesRowsOrderRemovalAndPermissionsExactly(){
  var before=page("a","user");before.addProperty("canEdit",true);var next=before.deepCopy();next.addProperty("request","b");next.remove("canEdit");var rows=next.getAsJsonArray("entries");rows.remove(2);rows.get(3).getAsJsonObject().addProperty("text","edited");rows.add(rows.remove(0));
  var encoded=MenuPageDelta.encode(next,MenuPageDelta.known(before));assertTrue(encoded.has("pageDelta"));assertEquals(next,MenuPageDelta.apply(before,encoded));assertFalse(MenuPageDelta.apply(before,encoded).has("canEdit"));
  assertThrows(IllegalArgumentException.class,()->MenuPageDelta.apply(page("x","another"),encoded));
 }
 @Test void corruptionAndUnknownHintsNeverYieldPartialPages()throws Exception{
  var before=page("a","user");var next=page("b","user");var packet=MenuPageDelta.encode(next,MenuPageDelta.known(before));packet.getAsJsonObject("pageDelta").addProperty("hash","invalid");assertThrows(IllegalArgumentException.class,()->MenuPageDelta.apply(before,packet));
  assertEquals(next,MenuPageDelta.encode(next,Json.parse("{\"hash\":\"invalid\"}")));
 }
 @Test void transportRecoversOnceAndReconnectRejectsOldDelta(){
  var t=new MenuTransport<Object>();t.pageDeltas(true);var owner=new Object();t.enqueue(query("a"),owner,false,0);t.dispatch(0,owner);t.receive(page("a","user"),1);
  t.invalidate("notifications","");t.enqueue(query("b"),owner,false,600);var request=t.dispatch(600,owner).getFirst();assertTrue(request.has("knownPage"));var encoded=MenuPageDelta.encode(page("b","user"),request.getAsJsonObject("knownPage"));
  encoded.getAsJsonObject("pageDelta").addProperty("hash","corrupt");assertNull(t.receive(encoded,601));var retry=t.dispatch(1200,owner).getFirst();assertFalse(retry.has("knownPage"));assertEquals("b",Json.str(retry,"request"));assertEquals(page("b","user"),t.receive(page("b","user"),1201));
  t.clear();assertNull(t.receive(encoded,1202));t.enqueue(query("c"),owner,false,1800);assertFalse(t.dispatch(1800,owner).getFirst().has("knownPage"));
 }
 @Test void queuedRecoveryCannotExtendOriginalDeadline(){
  var t=new MenuTransport<Object>();t.pageDeltas(true);var owner=new Object();t.enqueue(query("a"),owner,false,0);t.dispatch(0,owner);t.receive(page("a","user"),1);
  t.enqueue(query("b"),owner,false,600);var request=t.dispatch(600,owner).getFirst();var broken=MenuPageDelta.encode(page("b","user"),request.getAsJsonObject("knownPage"));broken.getAsJsonObject("pageDelta").addProperty("hash","corrupt");
  assertNull(t.receive(broken,15599));assertTrue(t.dispatch(15600,owner).isEmpty());assertNull(t.receive(broken,15601));
 }
 @Test void oneHundredRecipientsStayIsolatedAcrossUpdatesAndReconnects(){
  long full=0,wire=0;var timings=new ArrayList<Long>();int updates=0;
  var clients=new ArrayList<MenuTransport<Object>>();var owners=new ArrayList<Object>();var snapshots=new ArrayList<JsonObject>();
  for(int user=0;user<100;user++){
   var t=new MenuTransport<Object>();t.pageDeltas(true);Object owner=new Object();var initial=page("initial","recipient"+user);t.enqueue(query("initial"),owner,false,0);t.dispatch(0,owner);t.receive(initial,1);clients.add(t);owners.add(owner);snapshots.add(initial);
  }
  for(int n=1;n<=20;n++)for(int user=0;user<100;user++){
    var t=clients.get(user);var owner=owners.get(user);String identity="recipient"+user;long now=n*2000L;String request="r"+n;
    t.invalidate("notifications","");t.enqueue(query(request),owner,false,now);var outbound=t.dispatch(now,owner).getFirst();var next=snapshots.get(user).deepCopy();next.addProperty("request",request);next.getAsJsonArray("entries").get(n%20).getAsJsonObject().addProperty("text",identity+" changed "+n);
    long started=System.nanoTime();var encoded=MenuPageDelta.encode(next,outbound.getAsJsonObject("knownPage"));var decoded=t.receive(encoded,now+1);timings.add(System.nanoTime()-started);assertEquals(next,decoded);assertTrue(Json.GSON.toJson(decoded).contains(identity));
    full+=Json.GSON.toJson(next).length();wire+=Json.GSON.toJson(encoded).length()+Json.GSON.toJson(outbound).length();updates++;
    if(n%5==0){t.clear();t.receive(encoded,now+2);assertNull(t.cached(query(request),now+3));var fresh=query("rejoin"+n);t.enqueue(fresh,owner,false,now+600);assertFalse(t.dispatch(now+600,owner).getFirst().has("knownPage"));var restored=next.deepCopy();restored.addProperty("request",Json.str(fresh,"request"));t.receive(restored,now+601);}
    snapshots.set(user,next);
  }
  Collections.sort(timings);assertTrue(wire<full,"Deltas plus fingerprints must reduce total wire volume");System.out.printf(Locale.ROOT,"RIVET_PAGE_LOAD players=100 updates=%d reconnects=400 fullChars=%d wireChars=%d p50Ms=%.3f p95Ms=%.3f maxMs=%.3f%n",updates,full,wire,timings.get(timings.size()/2)/1e6,timings.get((int)(timings.size()*.95))/1e6,timings.getLast()/1e6);
 }
}
