package dev.abros.rivet.core.auth;

import dev.abros.rivet.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class AuthRenameTest {
 @TempDir Path root;
 private static final String ID="00000000-0000-0000-0000-000000000001",OTHER="00000000-0000-0000-0000-000000000002";
 private static char[] password(){return "a long test password".toCharArray();}
 @Test void preservesIdentityOwnershipPasswordAndOfficialLinkAndRevokesCredentials()throws Exception{
  var database=TestDatabase.database(root);var auth=new AuthStore(database);var community=new CommunityStore(database,CommunityStore.defaults());
  auth.register("OldName",ID,password(),0);community.seen(new CommunityStore.Actor(ID,"OldName",false,false));
  auth.linkOfficial("OldName",ID,OTHER,password(),1);var device=auth.remember("OldName","Laptop",2);String invitation=auth.invite("console","OldName",3);
  database.transaction(()->{try(var q=database.connection().prepareStatement("INSERT INTO preferences VALUES(?,?::jsonb)")){q.setString(1,ID);q.setString(2,"{\"theme\":\"dark\"}");q.executeUpdate();}return null;});
  long revision=database.communityRevision();var renamed=auth.rename("admin","oldname","NewName",ID,4);
  assertEquals(ID,renamed.uuid());assertEquals(OTHER,renamed.official());assertEquals(2,renamed.generation());assertEquals("local",renamed.type());assertFalse(renamed.blocked());
  assertNull(auth.account("OldName"));assertEquals(renamed,auth.login("NewName",ID,password(),5));assertEquals(renamed,auth.official("NewName",ID,OTHER,6));
  assertEquals("NewName",community.personName(ID));assertTrue(database.communityRevision()>revision);
  assertFalse(auth.hasDevice("NewName",device.id(),7));assertTrue(auth.validSessions(List.of(new AuthStore.SessionKey("OldName",1,"")),7).isEmpty());
  assertThrows(IllegalArgumentException.class,()->auth.reset("NewName",ID,invitation,password(),8));
  // A logout callback queued before rename must never restore the old display name.
  community.seen(new CommunityStore.Actor(ID,"OldName",false,false));assertEquals("NewName",community.personName(ID));
  database.transaction(()->{try(var q=database.connection().prepareStatement("SELECT body FROM preferences WHERE id=?")){q.setString(1,ID);try(var rs=q.executeQuery()){assertTrue(rs.next());assertTrue(rs.getString(1).contains("dark"));}}return null;});
  assertEquals(List.of(new AuthStore.Rename(ID,"OldName","NewName")),auth.pendingRenames());
  assertThrows(IllegalArgumentException.class,()->auth.rename("console","NewName","ThirdName",ID,10));
  auth.finishRename(new AuthStore.Rename(ID,"wrong","NewName"));assertEquals(1,auth.pendingRenames().size());
  auth.finishRename(new AuthStore.Rename(ID,"OldName","NewName"));assertTrue(auth.pendingRenames().isEmpty());
  var restarted=new ServerIdentities(new AuthStore(database).profiles());assertEquals("NewName",restarted.known(UUID.fromString(ID)).orElseThrow().name());assertTrue(restarted.known("OldName").isEmpty());assertEquals(ID,restarted.verifiedAlias(UUID.fromString(OTHER)).orElseThrow().uuid().toString());
 }
 @Test void conflictsInvalidNamesAndWrongIdentityLeaveEverythingUnchanged()throws Exception{
  var db=TestDatabase.database(root);var auth=new AuthStore(db);auth.register("Alice",ID,password(),0);auth.reserve("Reserved",OTHER);var device=auth.remember("Alice","Laptop",1);
  for(String target:List.of("Reserved","RESERVED","Alice","bad-name","a".repeat(17),""))assertThrows(IllegalArgumentException.class,()->auth.rename("console","Alice",target,ID,2));
  assertThrows(IllegalArgumentException.class,()->auth.rename("console","Missing","Free",ID,2));assertThrows(IllegalArgumentException.class,()->auth.rename("console","Alice","Free",OTHER,2));
  assertEquals(0,auth.account("Alice").generation());assertTrue(auth.hasDevice("Alice",device.id(),3));assertEquals(ID,auth.login("Alice",ID,password(),4).uuid());
 }
 @Test void caseOnlyRenameAndBlockedReservedAccountPreserveTheirState()throws Exception{
  var auth=new AuthStore(TestDatabase.database(root));auth.reserve("Alice",ID);auth.block("console","Alice",true,0);
  var renamed=auth.rename("console","ALICE","alice",ID,1);assertEquals("alice",renamed.name());assertEquals("reserved",renamed.type());assertTrue(renamed.blocked());assertEquals(ID,renamed.uuid());assertEquals(2,renamed.generation());
 }
 @Test void failedAuditRollsBackNameDevicesAndProfile()throws Exception{
  var db=TestDatabase.database(root);var auth=new AuthStore(db);auth.register("Alice",ID,password(),0);var device=auth.remember("Alice","Laptop",1);
  assertThrows(Exception.class,()->auth.rename(null,"Alice","Bob",ID,2));assertNotNull(auth.account("Alice"));assertNull(auth.account("Bob"));assertTrue(auth.hasDevice("Alice",device.id(),3));assertEquals(0,auth.account("Alice").generation());
 }
 @Test void competingRegistrationCannotBeOverwritten()throws Exception{
  var auth=new AuthStore(TestDatabase.database(root));auth.register("Alice",ID,password(),0);var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)){
   var rename=pool.submit(()->{start.await();try{auth.rename("console","Alice","Bob",ID,1);return true;}catch(IllegalArgumentException conflict){return false;}});
   var register=pool.submit(()->{start.await();try{auth.register("Bob",OTHER,password(),1);return true;}catch(IllegalArgumentException conflict){return false;}});
   start.countDown();assertNotEquals(rename.get(10,TimeUnit.SECONDS),register.get(10,TimeUnit.SECONDS));assertNotNull(auth.account("Bob"));
   if(auth.account("Alice")==null){assertEquals(ID,auth.account("Bob").uuid());assertEquals(1,auth.profiles().size());}else{assertEquals(ID,auth.account("Alice").uuid());assertEquals(OTHER,auth.account("Bob").uuid());assertEquals(2,auth.profiles().size());}
  }
 }
}
