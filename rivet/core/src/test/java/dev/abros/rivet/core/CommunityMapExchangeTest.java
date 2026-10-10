package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class CommunityMapExchangeTest {
 @TempDir Path temp;
 @Test void removedExchangeRejectsOldClientsWithoutWritingTerrain()throws Exception{
  var db=TestDatabase.database(temp);var store=new CommunityStore(db,CommunityStore.defaults());var actor=new CommunityStore.Actor(UUID.randomUUID().toString(),"Player",true,true);
  for(String op:java.util.List.of("mapExchangeSettings","mapExchangeConsent","mapExchangePut","mapExchangePull")){var q=new JsonObject();q.addProperty("section","home");q.addProperty("op",op);assertThrows(CommunityFailure.class,()->store.request(actor,q));}
  assertTrue(store.records("map-exchange-consent").isEmpty());assertFalse(ConnectionCompatibility.FEATURES.contains("map-exchange"));
 }
}
