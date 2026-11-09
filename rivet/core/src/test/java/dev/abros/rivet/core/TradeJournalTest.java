package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class TradeJournalTest {
 @TempDir Path dir;
 JsonObject book(int count){var j=new JsonObject();j.addProperty("count",count);return j;}
 @Test void crashBeforeInventorySaveDoesNotCreateEscrow()throws Exception{
  var journal=new TradeJournal(dir);journal.prepare(book(64),"player","one");
  assertFalse(new TradeJournal(dir).recover((p,r)->false).has("count"));
 }
 @Test void crashAfterInventorySaveCommitsEscrowExactlyOnce()throws Exception{
  var journal=new TradeJournal(dir);journal.prepare(book(64),"player","one");
  assertEquals(64,new TradeJournal(dir).recover((p,r)->p.equals("player")&&r.equals("one")).get("count").getAsInt());
  assertEquals(64,new TradeJournal(dir).recover((p,r)->false).get("count").getAsInt());
 }
 @Test void bookOnlyOperationsRecoverWithoutPlayer()throws Exception{
  var journal=new TradeJournal(dir);journal.prepare(book(128),"","");
  assertEquals(128,journal.recover((p,r)->{throw new AssertionError();}).get("count").getAsInt());
 }
 @Test void readableJournalUsesSameCapacityAsWriter()throws Exception{
  var value=book(1);value.addProperty("payload","x".repeat(9*1024*1024));var journal=new TradeJournal(dir);journal.prepare(value,"","");journal.commit();assertEquals(9*1024*1024,journal.load().get("payload").getAsString().length());
 }
 @Test void unresolvedOperationCannotBeOverwritten()throws Exception{
  var journal=new TradeJournal(dir);journal.prepare(book(64),"player","one");
  assertThrows(java.io.IOException.class,()->journal.prepare(book(128),"player","two"));
  journal.abort();journal.prepare(book(32),"","");journal.commit();assertEquals(32,journal.load().get("count").getAsInt());
 }
}
