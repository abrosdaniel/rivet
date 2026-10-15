package dev.abros.rivet.core.api;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class ApiKeysTest {
    @TempDir Path root;
    PgDatabase db;ApiKeys keys;
    final Set<String> read=Set.of("server.status.read");
    static final class Time extends Clock {
        long now=1800000000000L;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return Instant.ofEpochMilli(now);}
    }
    Time clock=new Time();
    @BeforeEach void setup()throws Exception{db=TestDatabase.database(root);keys=new ApiKeys(db,new ApiKeys.Limits(20,3,4,30,1,100),clock);}
    @AfterEach void close(){keys.close();}
    String token(ApiKeys.Issued issued)throws Exception{return Files.readString(issued.deliveryFile()).trim();}
    ApiKeys.Issued create(String app)throws Exception{return keys.create(root,app,read,1,10);}
    interface Checked {void run()throws Exception;}
    void code(ApiException.Code code,Checked action){assertEquals(code,assertThrows(ApiException.class,action::run).code());}

    @Test void privateProfileScopesAreExactAndRevocable()throws Exception{
        var issued=keys.create(root,"terrain",Set.of("map.tiles.read","map.tiles.profile.staff"),1,10);var grant=keys.authorize(token(issued),"map.tiles.profile.staff");
        code(ApiException.Code.FORBIDDEN,()->keys.revalidate(grant,"map.tiles.profile.builders"));
        for(String bad:List.of("map.tiles.profile.*","map.tiles.profile.../staff","map.tiles.profile.surface"))code(ApiException.Code.INVALID_ARGUMENT,()->keys.create(root,"bad",Set.of(bad),1,10));
        keys.state(issued.key().id(),"disable");code(ApiException.Code.UNAUTHENTICATED,()->keys.revalidate(grant,"map.tiles.profile.staff"));
    }
    @Test void secretOnlyInProtectedDeliveryAndStorageSurvivesNewService()throws Exception{
        var issued=create("website");String token=token(issued);
        assertFalse(issued.toString().contains(token));assertFalse(keys.list().toString().contains(token));
        db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT digest FROM api_keys")){assertTrue(rows.next());assertEquals(64,rows.getString(1).length());assertFalse(rows.getString(1).contains(token));}return null;});
        if(Files.getFileStore(root).supportsFileAttributeView("posix")){
            assertEquals(PosixFilePermissions.fromString("rw-------"),Files.getPosixFilePermissions(issued.deliveryFile()));
            assertEquals(PosixFilePermissions.fromString("rwx------"),Files.getPosixFilePermissions(issued.deliveryFile().getParent()));
        }
        keys.close();keys=new ApiKeys(db,new ApiKeys.Limits(20,3,4,30,1,100),clock);
        var grant=keys.authorize(token,"server.status.read");assertEquals("website",grant.application());
        assertEquals(clock.now,keys.list().getFirst().lastUsed());
    }
    @Test void invalidScopeSecretExpiryDisableAndRevokeRejectExistingGrant()throws Exception{
        var issued=create("website");String token=token(issued);var grant=keys.authorize(token,"server.status.read");
        code(ApiException.Code.FORBIDDEN,()->keys.authorize(token,"server.status.subscribe"));
        code(ApiException.Code.FORBIDDEN,()->keys.authorize(token,"admin.*"));
        code(ApiException.Code.UNAUTHENTICATED,()->keys.authorize(token.substring(0,37)+"A".repeat(43),"server.status.read"));
        code(ApiException.Code.UNAUTHENTICATED,()->keys.authorize("garbage","server.status.read"));
        keys.state(issued.key().id(),"disable");code(ApiException.Code.UNAUTHENTICATED,()->keys.revalidate(grant,"server.status.read"));
        keys.state(issued.key().id(),"enable");keys.revalidate(grant,"server.status.read");
        clock.now+=86400000L;code(ApiException.Code.UNAUTHENTICATED,()->keys.revalidate(grant,"server.status.read"));
        clock.now-=86400000L;keys.state(issued.key().id(),"revoke");code(ApiException.Code.UNAUTHENTICATED,()->keys.revalidate(grant,"server.status.read"));
        code(ApiException.Code.CONFLICT,()->keys.state(issued.key().id(),"enable"));
    }
    @Test void rotationIsAtomicAndDeliveryFailureKeepsOldKey()throws Exception{
        var issued=create("website");var old=keys.authorize(token(issued),"server.status.read");
        Path invalid=root.resolve("not-a-directory");Files.writeString(invalid,"x");
        assertThrows(Exception.class,()->keys.rotate(invalid,issued.key().id(),1));keys.revalidate(old,"server.status.read");
        assertEquals(1,keys.list().size());
        var rotated=keys.rotate(root,issued.key().id(),2);
        assertEquals(read,rotated.key().scopes());code(ApiException.Code.UNAUTHENTICATED,()->keys.revalidate(old,"server.status.read"));
        keys.authorize(token(rotated),"server.status.read");
    }
    @Test void rateBudgetIsSharedAcrossKeysAndRetainedAcrossServiceRestart()throws Exception{
        var one=create("website");var two=create("website");
        for(int i=0;i<2;i++){keys.authorize(token(one),"server.status.read");keys.authorize(token(two),"server.status.read");}
        code(ApiException.Code.RATE_LIMITED,()->keys.authorize(token(one),"server.status.read"));
        keys.close();keys=new ApiKeys(db,new ApiKeys.Limits(20,3,4,30,1,100),clock);
        code(ApiException.Code.RATE_LIMITED,()->keys.authorize(token(two),"server.status.read"));
        clock.now+=60000;keys.authorize(token(two),"server.status.read");
        keys.applicationLimit("website",1);code(ApiException.Code.RATE_LIMITED,()->keys.authorize(token(one),"server.status.read"));
    }
    @Test void keyBudgetAndKeyLimitAreIndependentOfApplicationBudget()throws Exception{
        var one=keys.create(root,"website",read,1,1);var two=create("website");create("website");
        code(ApiException.Code.BUSY,()->create("website"));
        keys.authorize(token(one),"server.status.read");code(ApiException.Code.RATE_LIMITED,()->keys.authorize(token(one),"server.status.read"));
        keys.authorize(token(two),"server.status.read");
        code(ApiException.Code.INVALID_ARGUMENT,()->keys.create(root,"website",Set.of("*"),1,10));
        code(ApiException.Code.INVALID_ARGUMENT,()->keys.create(root,"website",read,31,10));
    }
    @Test void cleanupBoundsAuditMetadataAndRemovesExpiredDelivery()throws Exception{
        var first=create("website");
        for(int i=0;i<110;i++)keys.state(first.key().id(),i%2==0?"disable":"enable");
        db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT count(*) FROM api_key_audit")){rows.next();assertEquals(100,rows.getInt(1));}return null;});
        clock.now+=3*86400000L;create("other");
        assertFalse(Files.exists(first.deliveryFile()));assertEquals(1,keys.list().size());
    }
    @Test void concurrentRequestsCannotOverspendApplicationBudget()throws Exception{
        var one=create("website");var two=create("website");String a=token(one),b=token(two);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<12;i++){String token=i%2==0?a:b;futures.add(workers.submit(()->{try{keys.authorize(token,"server.status.read");return true;}catch(ApiException failure){assertEquals(ApiException.Code.RATE_LIMITED,failure.code());return false;}}));}
            int accepted=0;for(var future:futures)if(future.get())accepted++;assertEquals(4,accepted);
        }
    }
    @Test void shutdownAndForeignGrantAreRejected()throws Exception{
        var issued=create("website");var grant=keys.authorize(token(issued),"server.status.read");
        try(var other=new ApiKeys(db,new ApiKeys.Limits(20,3,4,30,1,100),clock)){code(ApiException.Code.UNAUTHENTICATED,()->other.revalidate(grant,"server.status.read"));}
        keys.close();code(ApiException.Code.UNAVAILABLE,()->keys.revalidate(grant,"server.status.read"));
        code(ApiException.Code.UNAVAILABLE,()->create("other"));
    }
    @Test void symbolicLinkDeliveryIsRejectedWithoutDatabaseKey()throws Exception{
        Files.createDirectory(root.resolve("rivet"));Path elsewhere=Files.createDirectory(root.resolve("elsewhere"));
        try {Files.createSymbolicLink(root.resolve("rivet/api-keys"),elsewhere);}catch(UnsupportedOperationException|java.io.IOException notSupported){Assumptions.abort("No symlink support");}
        assertThrows(Exception.class,()->create("website"));assertTrue(keys.list().isEmpty());
        try(var files=Files.list(elsewhere)){assertEquals(0,files.count());}
    }
}
