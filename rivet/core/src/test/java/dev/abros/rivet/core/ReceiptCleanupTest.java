package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class ReceiptCleanupTest {
 @TempDir Path root;
 @Test void cleanupBacklogDoesNotBecomeOneUnboundedUserTransaction()throws Exception{
  var db=TestDatabase.database(root);long now=System.currentTimeMillis();
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO request_receipts(actor,id,digest,response,created) SELECT 'expired',n::text,'d','{}',? FROM generate_series(1,20000) n")){q.setLong(1,now-3*86400000L);q.executeUpdate();}return null;});
  class Rollback extends Exception{}
  var all=new ArrayList<Long>();var bounded=new ArrayList<Long>();
  for(int n=0;n<5;n++)for(boolean batch:List.of(false,true))assertThrows(Rollback.class,()->db.transaction(()->{
   String sql=batch?"DELETE FROM request_receipts WHERE (actor,id) IN (SELECT actor,id FROM request_receipts WHERE created<? ORDER BY created LIMIT 256 FOR UPDATE SKIP LOCKED)":"DELETE FROM request_receipts WHERE created<?";
   long started=System.nanoTime();try(var q=db.connection().prepareStatement(sql)){q.setLong(1,now-2*86400000L);int count=q.executeUpdate();assertEquals(batch?256:20000,count);}(batch?bounded:all).add(System.nanoTime()-started);throw new Rollback();
  }));
  Collections.sort(all);Collections.sort(bounded);System.out.println("RECEIPT_CLEANUP_SAMPLE fullMedianMs="+all.get(2)/1e6+" boundedMedianMs="+bounded.get(2)/1e6);
  var journal=new RequestJournal(db);var request=new JsonObject();request.addProperty("operationId",UUID.randomUUID().toString());request.addProperty("issuedAt",now);int[] effects={0};
  db.transaction(()->journal.execute("actor",request,()->{effects[0]++;return new JsonObject();}));db.transaction(()->journal.execute("actor",request,()->{effects[0]++;return new JsonObject();}));assertEquals(1,effects[0]);
  db.transaction(()->{try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT count(*) FROM request_receipts WHERE actor='expired'")){r.next();assertEquals(19744,r.getInt(1));}return null;});
  request.addProperty("issuedAt",now-3*86400000L);assertThrows(CommunityFailure.class,()->db.transaction(()->journal.execute("expired",request,()->{fail("Expired command executed");return null;})));
 }
}
