package dev.abros.rivet.core.auth;
import dev.abros.rivet.core.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class AuthFactorStoreTest {
 @TempDir Path root;
 static final String ID="00000000-0000-0000-0000-000000000001";
 static char[] password(){return "a sufficiently long password".toCharArray();}
 AuthStore store()throws Exception{return new AuthStore(TestDatabase.database(root),6,AuthFactorVault.open(root));}
 @Test void enrollmentReplayBackupAndReopen()throws Exception{
  String secret;List<String> backup;
  try(var auth=store()){
   auth.register("Alice",ID,password(),0);secret=auth.beginFactor("Alice",ID,password(),1000);assertFalse(auth.secondFactor("Alice"));
   backup=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,2,6),60000);assertEquals(10,new HashSet<>(backup).size());assertTrue(auth.secondFactor("Alice"));
   assertThrows(Exception.class,()->auth.login("Alice",ID,password(),Totp.code(secret,2,6),60001));
   assertEquals(ID,auth.login("Alice",ID,password(),Totp.code(secret,3,6),90000).uuid());
   assertThrows(Exception.class,()->auth.apiLogin("Alice",password(),120000));
   assertEquals(ID,auth.apiLogin("Alice",password(),backup.get(0),180000).uuid());
  }
  try(var auth=store()){
   assertThrows(Exception.class,()->auth.login("Alice",ID,password(),backup.get(0),240000));
   assertEquals(ID,auth.login("Alice",ID,password(),backup.get(1),300000).uuid());
   auth.rename("console","Alice","Bob",ID,310000);assertTrue(auth.secondFactor("Bob"));
   assertEquals(ID,auth.login("Bob",ID,password(),backup.get(2),360000).uuid());
  }
 }
 @Test void deviceOfficialAndPasswordResetDoNotBypassFactor()throws Exception{
  try(var auth=store()){
   auth.register("Alice",ID,password(),0);auth.linkOfficial("Alice",ID,UUID.randomUUID().toString(),password(),1000);
   String secret=auth.beginFactor("Alice",ID,password(),2000);var codes=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,2,6),60000);
   var device=auth.remember("Alice","laptop",61000);
   assertThrows(Exception.class,()->auth.deviceLogin("Alice",ID,device.token(),90000));
   assertThrows(Exception.class,()->auth.official("Alice",ID,auth.account("Alice").official(),120000));
   assertEquals(ID,auth.deviceLogin("Alice",ID,device.token(),codes.get(0),180000).uuid());
   String invitation=auth.invite("console","Alice",190000);auth.reset("Alice",ID,invitation,password(),200000);
   assertTrue(auth.secondFactor("Alice"));assertThrows(Exception.class,()->auth.login("Alice",ID,password(),240000));
   assertEquals(ID,auth.login("Alice",ID,password(),codes.get(1),300000).uuid());
  }
 }
 @Test void concurrentBackupConsumptionHasOneWinner()throws Exception{
  try(var auth=store()){
   auth.register("Alice",ID,password(),0);String secret=auth.beginFactor("Alice",ID,password(),1000);String code=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,2,6),60000).getFirst();
   try(var workers=Executors.newFixedThreadPool(2)){
    var gate=new CountDownLatch(1);Callable<Boolean> call=()->{gate.await();try{auth.login("Alice",ID,password(),code,120000);return true;}catch(IllegalArgumentException denied){return false;}};
    var a=workers.submit(call);var b=workers.submit(call);gate.countDown();assertNotEquals(a.get(),b.get());
   }
  }
 }
 @Test void requiredPolicyAndSessionRevocation()throws Exception{
  var required=new java.util.concurrent.atomic.AtomicBoolean();
  try(var auth=store().requireSecondFactor(a->required.get())){
   var account=auth.register("Alice",ID,password(),0);var session=new AuthStore.SessionKey("Alice",account.generation(),"");
   assertTrue(auth.validSessions(List.of(session),1000).contains(session));required.set(true);
   assertFalse(auth.factorPolicySatisfied("Alice"));assertTrue(auth.validSessions(List.of(session),2000).isEmpty());assertThrows(Exception.class,()->auth.login("Alice",ID,password(),3000));
   String secret=auth.beginFactor("Alice",ID,password(),4000);var codes=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,2,6),60000);
   assertTrue(auth.factorPolicySatisfied("Alice"));assertTrue(auth.validSessions(List.of(session),61000).isEmpty());
   assertThrows(Exception.class,()->auth.removeFactor("Alice",ID,password(),codes.get(0),90000));assertTrue(auth.secondFactor("Alice"));
   auth.resetFactor("console","Alice",120000);assertFalse(auth.factorPolicySatisfied("Alice"));assertThrows(Exception.class,()->auth.login("Alice",ID,password(),codes.get(1),180000));
  }
 }
 @Test void browserSessionsRequireFactorAndAreRevokedOnFactorChanges()throws Exception{
  var db=TestDatabase.database(root);var clock=java.time.Clock.fixed(java.time.Instant.ofEpochMilli(180000),java.time.ZoneOffset.UTC);
  try(var auth=store();var keys=new dev.abros.rivet.core.api.ApiKeys(db,new dev.abros.rivet.core.api.ApiKeys.Limits(30,5,1000,30,1,100),clock)){
   auth.register("Alice",ID,password(),0);String secret=auth.beginFactor("Alice",ID,password(),1000);var codes=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,2,6),60000);
   var issued=keys.create(root,"website",Set.of("sessions.password"),1,1000);var grant=keys.authorize(java.nio.file.Files.readString(issued.deliveryFile()).trim(),"sessions.password");
   try(var sessions=new dev.abros.rivet.core.api.PlayerSessions(keys,auth,true,clock,1,100)){
    var session=sessions.login(grant,"Alice",password(),codes.get(0));assertEquals(ID,sessions.validate(grant,session.token()).uuid());
    auth.renewBackupCodes("Alice",ID,password(),codes.get(1),240000);assertThrows(Exception.class,()->sessions.validate(grant,session.token()));
    assertThrows(Exception.class,()->sessions.login(grant,"Alice",password()));
   }
  }
 }

 @Test void pendingExpiryCiphertextAndBackupRotation()throws Exception{
  try(var auth=store()){
   auth.register("Alice",ID,password(),0);String expired=auth.beginFactor("Alice",ID,password(),1000);
   assertThrows(Exception.class,()->auth.confirmFactor("Alice",ID,password(),Totp.code(expired,21,6),630000));assertFalse(auth.secondFactor("Alice"));
   String secret=auth.beginFactor("Alice",ID,password(),660000);var original=auth.confirmFactor("Alice",ID,password(),Totp.code(secret,23,6),690000);
   var db=TestDatabase.database(root);db.transaction(()->{try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT secret,pending,last_step FROM auth_factors")){assertTrue(r.next());assertFalse(r.getString(1).contains(secret));assertNull(r.getString(2));assertEquals(23,r.getLong(3));}try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT digest FROM auth_backup_codes")){int count=0;while(r.next()){assertFalse(original.contains(r.getString(1)));assertEquals(64,r.getString(1).length());count++;}assertEquals(10,count);}return null;});
   var fresh=auth.renewBackupCodes("Alice",ID,password(),original.get(0),720000);
   assertThrows(Exception.class,()->auth.login("Alice",ID,password(),original.get(1),750000));
   assertEquals(ID,auth.login("Alice",ID,password(),fresh.get(0),810000).uuid());
   auth.removeFactor("Alice",ID,password(),fresh.get(1),840000);assertFalse(auth.secondFactor("Alice"));assertEquals(ID,auth.login("Alice",ID,password(),870000).uuid());
  }
 }

}
