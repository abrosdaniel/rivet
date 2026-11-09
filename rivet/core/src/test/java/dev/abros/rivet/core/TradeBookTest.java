package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TradeBookTest {
 @Test void noticesFollowPublishedChangesAndSurviveRestart(){
  var book=new TradeBook();var d=book.create("a","A","b","B",false,0,1000,10);
  book.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",64)),100);assertTrue(book.notices.isEmpty());
  book.action(d.id,"a",d.revision,"send",1);assertEquals(1,book.notices.size());long sequence=book.noticeSequence;
  book.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",32)),100);assertEquals(sequence,book.noticeSequence);
  book.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",64)),100);book.action(d.id,"a",d.revision,"publish",2);assertEquals(sequence,book.noticeSequence);
  book.action(d.id,"b",d.revision,"cancel",3);assertEquals(2,book.notices.size());
  var reopened=Json.GSON.fromJson(Json.GSON.toJson(book),TradeBook.class);assertEquals(book.notices,reopened.notices);assertEquals("cancelled",reopened.notices.get("a:"+d.id).event());
 }
 @Test void expiredPrivateDraftNotifiesOnlyItsOwner(){var book=new TradeBook();var d=book.create("a","A","b","B",false,0,1,10);assertTrue(book.expire(2));assertEquals(1,book.notices.size());assertEquals("a",book.notices.values().iterator().next().recipient());}
 TradeBook b=new TradeBook();
 @Test void inventoryEscrowConservationDetectsClampingAndDuplication(){
  var before=List.of(new TradeBook.Item("diamond",128));
  TradeBook.conserve(before,List.of(new TradeBook.Item("diamond",64),new TradeBook.Item("diamond",64)));
  assertThrows(IllegalArgumentException.class,()->TradeBook.conserve(before,List.of(new TradeBook.Item("diamond",64))));
  assertThrows(IllegalArgumentException.class,()->TradeBook.conserve(before,List.of(new TradeBook.Item("diamond",192))));
  assertThrows(IllegalArgumentException.class,()->TradeBook.conserve(before,List.of(new TradeBook.Item("diamond{different-components}",128))));
 }
 TradeBook.Deal deal(){var d=b.create("a","A","b","B",false,0,1000,10);b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4096)),256);b.action(d.id,"a",d.revision,"send",1);b.edit(d.id,"b",d.revision,List.of(new TradeBook.Item("stone",128)),256);b.action(d.id,"b",d.revision,"publish",1);return d;}
 @Test void offlineConsentExchangesOnce(){var d=deal();assertTrue(d.acceptedB);assertFalse(d.acceptedA);b.action(d.id,"a",d.revision,"accept",2);assertEquals("completed",d.status);assertEquals("stone",d.left.getFirst().data());assertEquals(4096,d.right.getFirst().count());assertThrows(IllegalArgumentException.class,()->b.action(d.id,"a",d.revision,"accept",4));}
 @Test void unsubmittedEditsBlockCompletionAndRestoringKeepsConsent(){var d=deal();long old=d.revision;
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4032)),256);assertTrue(d.acceptedB);assertTrue(d.dirty("a"));assertEquals(4096,d.published("a").getFirst().count());
  assertThrows(IllegalArgumentException.class,()->b.action(d.id,"a",d.revision,"accept",3));assertThrows(IllegalArgumentException.class,()->b.action(d.id,"b",old,"accept",3));
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4096)),256);assertFalse(d.dirty("a"));assertTrue(d.acceptedB);b.action(d.id,"a",d.revision,"accept",4);assertEquals("completed",d.status);
 }
 @Test void publishingNewTermsClearsConsentAndPublishingOldTermsDoesNotRestoreIt(){var d=deal();
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4032)),256);b.action(d.id,"a",d.revision,"publish",3);assertFalse(d.acceptedB);assertEquals(4032,d.published("a").getFirst().count());
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4096)),256);b.action(d.id,"a",d.revision,"publish",4);assertFalse(d.acceptedB);assertEquals(4032,d.previous("a").getFirst().count());
 }
 @Test void orderingAndSplittingDoNotChangeTermsButComponentsDo(){var d=deal();b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",2048),new TradeBook.Item("diamond",2048)),256);assertFalse(d.dirty("a"));assertTrue(d.acceptedB);
  assertTrue(TradeBook.sameItems(List.of(new TradeBook.Item("a",1),new TradeBook.Item("b",2)),List.of(new TradeBook.Item("b",2),new TradeBook.Item("a",1))));
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond{custom_data}",4096)),256);assertTrue(d.dirty("a"));
 }
 @Test void bothSidesCanEditButCannotCompleteUntilBothPublished(){var d=deal();b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("dirt",12)),256);b.edit(d.id,"b",d.revision,List.of(new TradeBook.Item("sand",4)),256);
  b.action(d.id,"a",d.revision,"publish",2);assertThrows(IllegalArgumentException.class,()->b.action(d.id,"a",d.revision,"accept",3));b.action(d.id,"b",d.revision,"publish",4);b.action(d.id,"a",d.revision,"accept",5);assertEquals("sand",d.left.getFirst().data());assertEquals("dirt",d.right.getFirst().data());
 }
 @Test void cancellingOrExpiringDraftReturnsActualItemsNotPublishedDescriptions(){for(boolean expiry:List.of(false,true)){var d=deal();b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4000),new TradeBook.Item("named_shulker{contents}",1)),256);if(expiry)b.expire(1001);else b.action(d.id,"b",d.revision,"cancel",2);assertEquals(4000,d.left.getFirst().count());assertEquals("named_shulker{contents}",d.left.get(1).data());}}
 @Test void draftsAndConsentSurviveSerialization(){var d=deal();b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4000)),256);var restored=Json.GSON.fromJson(Json.GSON.toJson(b),TradeBook.class);var r=restored.require(d.id,"a");assertTrue(r.dirty("a"));assertTrue(r.acceptedB);assertEquals(4096,r.published("a").getFirst().count());restored.edit(r.id,"a",r.revision,List.of(new TradeBook.Item("diamond",4096)),256);assertFalse(r.dirty("a"));assertTrue(r.acceptedB);}
 @Test void legacyEscrowMigratesWithoutChangingOwnership(){var d=deal();d.publishedLeft=d.publishedRight=null;b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4000)),256);assertEquals(4096,d.published("a").getFirst().count());assertEquals(4000,d.left.getFirst().count());assertTrue(d.dirty("a"));}
 @Test void cancellationAndExpiryPreserveOwners(){var d=deal();b.action(d.id,"b",d.revision,"cancel",2);assertEquals("diamond",d.left.getFirst().data());assertEquals("stone",d.right.getFirst().data());var e=deal();assertTrue(b.expire(1001));assertEquals("expired",e.status);assertEquals(4096,e.left.getFirst().count());}
 @Test void withdrawalsCannotCreateItemsOrChangeComponents(){var d=deal();b.action(d.id,"a",d.revision,"cancel",2);assertThrows(IllegalArgumentException.class,()->b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("enchanted-diamond",1)),256));b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",4000)),256);assertEquals(4000,d.left.getFirst().count());}
 @Test void nonParticipantAndBlockedRecipientDenied(){var d=deal();assertThrows(IllegalArgumentException.class,()->b.require(d.id,"c"));b.closedInbox.add("b");assertThrows(IllegalArgumentException.class,()->deal());}
 @Test void completedCursorCanReturnWithoutMintingItems(){var d=deal();b.action(d.id,"a",d.revision,"accept",2);b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("stone",64)),256);b.cursors.put("a",new TradeBook.Item("stone",64));b.cursorDeals.put("a",d.id);b.restoreCursor(d.id,"a");assertEquals(128,d.left.getFirst().count());assertFalse(b.cursors.containsKey("a"));b.restoreCursor(d.id,"a");assertEquals(128,d.left.getFirst().count());assertThrows(IllegalArgumentException.class,()->b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("stone",129)),256));}
 @Test void cursorReturnChecksOwnerAndSurvivesPersistence(){var d=deal();b.action(d.id,"a",d.revision,"cancel",2);b.cursors.put("a",new TradeBook.Item("shulker{contents}",1));b.cursorDeals.put("a",d.id);var restored=Json.GSON.fromJson(Json.GSON.toJson(b),TradeBook.class);restored.restoreCursor(d.id,"a");assertEquals("shulker{contents}",restored.require(d.id,"a").left.getLast().data());assertTrue(restored.cursorDeals.isEmpty());assertFalse(restored.cursors.containsKey("a"));assertEquals(128,restored.require(d.id,"b").right.getFirst().count());}
 @Test void loweredLimitNeverBlocksCollection(){var d=deal();b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("a",1),new TradeBook.Item("b",1)),256);b.action(d.id,"a",d.revision,"cancel",2);b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("a",1)),0);assertEquals(1,d.left.size());}

 @Test void unilateralOfferSurvivesRestartAndTransfersOnce(){
  var d=b.create("a","A","b","B",false,0,1000,10);
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("shulker{contents}",1)),256);
  b.action(d.id,"a",d.revision,"send",1);
  var restored=Json.GSON.fromJson(Json.GSON.toJson(b),TradeBook.class);var r=restored.require(d.id,"b");
  restored.action(r.id,"b",r.revision,"accept",2);
  assertEquals("completed",r.status);assertTrue(r.left.isEmpty());assertEquals(List.of(new TradeBook.Item("shulker{contents}",1)),r.right);
  assertThrows(IllegalArgumentException.class,()->restored.action(r.id,"b",r.revision,"accept",3));
 }
 @Test void legacyGiftAllowsCounterofferAndCancellationKeepsOwners(){
  var d=b.create("a","A","b","B",true,0,1000,10);
  b.edit(d.id,"a",d.revision,List.of(new TradeBook.Item("diamond",64)),256);b.action(d.id,"a",d.revision,"send",1);
  b.edit(d.id,"b",d.revision,List.of(new TradeBook.Item("emerald",32)),256);b.action(d.id,"b",d.revision,"publish",2);
  assertFalse(d.acceptedA);assertTrue(d.acceptedB);b.action(d.id,"a",d.revision,"cancel",3);
  assertEquals(List.of(new TradeBook.Item("diamond",64)),d.left);assertEquals(List.of(new TradeBook.Item("emerald",32)),d.right);
 }
 @Test void removingAllCounterItemsRequiresFreshConsent(){
  var d=deal();b.edit(d.id,"b",d.revision,List.of(),256);b.action(d.id,"b",d.revision,"publish",2);
  assertFalse(d.acceptedA);b.action(d.id,"a",d.revision,"accept",3);
  assertTrue(d.left.isEmpty());assertEquals(4096,d.right.getFirst().count());assertEquals("completed",d.status);
 }
 @Test void emptyDealCannotComplete(){
  var d=deal();b.edit(d.id,"a",d.revision,List.of(),256);b.action(d.id,"a",d.revision,"publish",2);
  b.edit(d.id,"b",d.revision,List.of(),256);b.action(d.id,"b",d.revision,"publish",3);
  assertThrows(IllegalArgumentException.class,()->b.action(d.id,"a",d.revision,"accept",4));assertEquals("open",d.status);
 }

 @Test void freezeBlocksAllParticipantChangesAndPausesExpiryAcrossRestart(){
  var d=deal();b.moderate(d.id,d.revision,"staff","freeze","Dispute",10);
  assertFalse(d.acceptedA);assertFalse(d.acceptedB);
  assertThrows(IllegalArgumentException.class,()->b.edit(d.id,"a",d.revision,List.of(),100));
  for(String action:List.of("accept","publish","cancel"))assertThrows(IllegalArgumentException.class,()->b.action(d.id,"a",d.revision,action,20));
  assertFalse(b.expire(5000));var restored=Json.GSON.fromJson(Json.GSON.toJson(b),TradeBook.class);var r=restored.require(d.id,"a");assertEquals(10,r.frozenAt);
  restored.moderate(r.id,r.revision,"staff","unfreeze","Resolved",5010);assertEquals(6000,r.expires);assertEquals(0,r.frozenAt);assertFalse(r.acceptedA);assertFalse(r.acceptedB);
  assertFalse(restored.expire(5999));assertTrue(restored.expire(6000));assertEquals(4096,r.left.getFirst().count());
 }
 @Test void staffCancellationRequiresReasonAndFreshRevisionAndCannotUndoCompletion(){
  var d=deal();long revision=d.revision;
  assertThrows(IllegalArgumentException.class,()->b.moderate(d.id,revision,"staff","cancel"," ",5));
  b.moderate(d.id,revision,"staff","freeze","Review",5);
  assertThrows(IllegalArgumentException.class,()->b.moderate(d.id,revision,"staff","cancel","Review",6));
  b.moderate(d.id,d.revision,"staff","cancel","Return to owners",7);
  assertEquals("cancelled",d.status);assertEquals(4096,d.left.getFirst().count());assertEquals(128,d.right.getFirst().count());
  var complete=deal();b.action(complete.id,"a",complete.revision,"accept",8);
  assertThrows(IllegalArgumentException.class,()->b.moderate(complete.id,complete.revision,"staff","cancel","Cannot rewind",9));
 }
 @Test void auditPreservesPublishedConditionsAndDeduplicatesUnchangedSnapshots(){
  var d=deal();var first=b.history.get(d.id).getFirst();assertEquals(4096,b.snapshots.get(first.snapshot()).left().getFirst().count());
  int snapshots=b.snapshots.size();b.moderate(d.id,d.revision,"Staff UUID","freeze","Dispute reason",10);assertEquals(snapshots,b.snapshots.size());
  var last=b.history.get(d.id).getLast();assertEquals("admin.freeze",last.action());assertEquals("Staff UUID",last.actor());assertEquals("Dispute reason",last.reason());
  var restored=Json.GSON.fromJson(Json.GSON.toJson(b),TradeBook.class);assertEquals(b.history,restored.history);assertEquals(b.snapshots,restored.snapshots);
 }
}
