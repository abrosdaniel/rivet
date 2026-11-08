package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.AuthStore;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class RetiredDomainApiTest {
 @TempDir Path root;
 @Test void retiredDomainsRejectReadsWritesAndSubscriptionsWithoutRemovingData()throws Exception{
  var db=TestDatabase.database(root);var store=new CommunityStore(db,CommunityStore.defaults());var settings=ServerSettings.parse(ServerSettings.template());UUID player=UUID.randomUUID();store.seen(new CommunityStore.Actor(player.toString(),"Player",false,false));
  try(var keys=new ApiKeys(db,new ApiKeys.Limits(100,8,1000,30,30,1000),Clock.systemUTC());var sessions=new PlayerSessions(keys,new AuthStore(db),false,Clock.systemUTC(),24,100);var java=new JavaDomains(db,store,settings)){
   var issued=keys.create(root,"legacyapp",ApiKeys.SCOPES,1,1000);String secret=Files.readString(issued.deliveryFile()).trim();var grant=keys.authorize(secret,"sessions.link");var challenge=sessions.challenge(grant);sessions.confirm(challenge.code(),"legacyapp",player.toString(),"Player");String token=sessions.exchange(grant,challenge.code(),challenge.pollToken()).token();
   var domain=new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,settings)).commands(new CommunityApiCommands(db,store,settings));var rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),domain);var headers=Map.of("Authorization",List.of("Bearer "+secret),"X-Rivet-Session",List.of(token));var watch=new DomainSubscriptions(domain);
   for(String id:List.of("tasks","events","board","polls","ideas")){
    assertFalse(domain.capabilities().contains("rest."+id+".read"));
    var read=rest.handle(new RestApi.Request("GET","/api/v1/"+id,headers));assertEquals(503,read.status(),read.body());assertTrue(read.body().contains("UNAVAILABLE"));
    var write=rest.handle(new RestApi.Request("POST","/api/v1/"+id,headers,"{}"));assertEquals(503,write.status(),write.body());
    assertEquals(ApiException.Code.UNAVAILABLE,assertThrows(ApiException.class,()->watch.snapshot(grant,new DomainSubscriptions.Target(id,Map.of(),token),null)).code());
   }
   var view=java.connect("legacy_mod",player);
   var failure=assertThrows(CompletionException.class,()->view.tasks(Optional.empty(),"").join());assertInstanceOf(ApiException.class,failure.getCause());assertEquals(ApiException.Code.UNAVAILABLE,((ApiException)failure.getCause()).code());
   assertNotNull(view.community(DomainData.Community.GROUPS,"").join());
  }
 }
}
