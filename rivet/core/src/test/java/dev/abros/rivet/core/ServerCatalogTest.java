package dev.abros.rivet.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ServerCatalogTest {
 final ServerCatalog.Entry a=new ServerCatalog.Entry("one","One","one.example:25565");
 ServerCatalog.Row row(ServerCatalog.Entry e){return new ServerCatalog.Row(e.name(),e.address());}
 @Test void addsOnceWithoutDuplicatingPersonalAddresses(){var p=ServerCatalog.reconcile(List.of(new ServerCatalog.Row("My server","one.example")),List.of(),List.of(a));assertTrue(p.changes().isEmpty());assertFalse(p.state().getFirst().managed());var fresh=ServerCatalog.reconcile(List.of(),List.of(),List.of(a));assertEquals(-1,fresh.changes().getFirst().index());assertTrue(ServerCatalog.reconcile(List.of(row(a)),fresh.state(),List.of(a)).changes().isEmpty());}
 @Test void releaseUpdatesOwnedEntryInPlace(){var next=new ServerCatalog.Entry("one","Renamed","two.example:25566");var p=ServerCatalog.reconcile(List.of(new ServerCatalog.Row("Personal","personal.example"),row(a)),List.of(new ServerCatalog.Tracked(a,true)),List.of(next));assertEquals(List.of(new ServerCatalog.Change(1,next)),p.changes());}
 @Test void restoresDeletedRequiredEntry(){var p=ServerCatalog.reconcile(List.of(),List.of(new ServerCatalog.Tracked(a,true)),List.of(a));assertEquals(List.of(new ServerCatalog.Change(-1,a)),p.changes());}
 @Test void editedPersonalEntryIsPreservedAndRequiredEntryRestored(){var p=ServerCatalog.reconcile(List.of(new ServerCatalog.Row("Edited","elsewhere.example")),List.of(new ServerCatalog.Tracked(a,true)),List.of(a));assertEquals(List.of(new ServerCatalog.Change(-1,a)),p.changes());}
 @Test void removedCatalogEntryRemovesOnlyOwnedUnchangedRow(){assertEquals(List.of(new ServerCatalog.Change(0,null)),ServerCatalog.reconcile(List.of(row(a)),List.of(new ServerCatalog.Tracked(a,true)),List.of()).changes());assertTrue(ServerCatalog.reconcile(List.of(new ServerCatalog.Row("Personal name",a.address())),List.of(new ServerCatalog.Tracked(a,true)),List.of()).changes().isEmpty());assertTrue(ServerCatalog.reconcile(List.of(row(a)),List.of(new ServerCatalog.Tracked(a,false)),List.of()).changes().isEmpty());}
 @Test void conflictingNewAddressDoesNotOverwritePersonalServer(){var next=new ServerCatalog.Entry("one","Renamed","personal.example");var p=ServerCatalog.reconcile(List.of(row(a),new ServerCatalog.Row("Personal","personal.example")),List.of(new ServerCatalog.Tracked(a,true)),List.of(next));assertTrue(p.changes().isEmpty());}
 @Test void duplicateIdsOrAddressesRejected(){assertThrows(IllegalArgumentException.class,()->ServerCatalog.reconcile(List.of(),List.of(),List.of(a,a)));assertThrows(IllegalArgumentException.class,()->ServerCatalog.reconcile(List.of(),List.of(),List.of(a,new ServerCatalog.Entry("two","Two","one.example"))));}
}
