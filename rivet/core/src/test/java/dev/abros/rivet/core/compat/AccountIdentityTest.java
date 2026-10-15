package dev.abros.rivet.core.compat;

import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.core.api.IntegrationRegistry;
import dev.abros.rivet.core.auth.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountIdentityTest {
 @Test void storedIdentitySurvivesLauncherUuidAndCaseChanges(){
  UUID account=UUID.randomUUID(),official=UUID.randomUUID();
  var index=new ServerIdentities(List.of(new AuthStore.Profile("Alice",account,official)));
  var profiles=new HashMap<UUID,IntegrationApi.Player>();
  profiles.put(account,new IntegrationApi.Player(account,"Alice"));
  try(var registry=new IntegrationRegistry()){
   registry.owner("rivet").register(IntegrationApi.IDENTITY,"rivet",identity(profiles));
   var api=registry.owner("numismatics").find(IntegrationApi.IDENTITY,"rivet").orElseThrow();
   assertEquals(account,index.resolve("ALICE",UUID.randomUUID(),null).uuid());
   assertEquals(account,index.resolve("OfficialName",official,official).uuid());
   assertEquals("Alice",AccountIdentity.find(api,account).orElseThrow().name());
   assertTrue(AccountIdentity.find(api,official).isEmpty());
   assertTrue(AccountIdentity.find(api,UUID.randomUUID()).isEmpty());
   assertTrue(AccountIdentity.find(api,null).isEmpty());
   profiles.put(account,new IntegrationApi.Player(account,"Renamed"));
   assertEquals("Renamed",AccountIdentity.find(api,account).orElseThrow().name());
  }
 }
 @Test void aliasCannotSubstituteAnAccountOwner(){
  UUID old=UUID.randomUUID(),current=UUID.randomUUID();
  assertTrue(AccountIdentity.find(identity(Map.of(old,new IntegrationApi.Player(current,"Alice"))),old).isEmpty());
 }
 private static IntegrationApi.Identity identity(Map<UUID,IntegrationApi.Player> profiles){return new IntegrationApi.Identity(){
  public Optional<IntegrationApi.Player> byId(UUID id){return Optional.ofNullable(profiles.get(id));}
  public Optional<IntegrationApi.Player> byName(String name){throw new AssertionError("Do not infer ownership from names");}
  public Optional<UUID> verifiedAlias(UUID id){throw new AssertionError("Do not migrate accounts during name lookup");}
 };}
}
