package dev.abros.rivet.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class DatabaseReliabilityTest {
    @TempDir Path identity;
    PgDatabase db;
    @BeforeEach void open() throws Exception { db=TestDatabase.database(identity); }
    private long scalar(String sql) throws Exception {
        return db.transaction(() -> {
            try(var statement=db.connection().createStatement();var row=statement.executeQuery(sql)) {
                row.next();return row.getLong(1);
            }
        });
    }
    @Test void failedTransactionRollsBackDataAndOutboxTogether() throws Exception {
        var store=new CommunityStore(db,CommunityStore.defaults());
        assertThrows(SQLException.class,()->db.transaction(()->{
            store.externalNotice(UUID.randomUUID().toString(),"not committed");
            try(var statement=db.connection().createStatement()){statement.execute("SELECT 1/0");}
            return null;
        }));
        assertEquals(0,scalar("SELECT count(*) FROM notices"));
        assertEquals(0,scalar("SELECT count(*) FROM community_events"));
        assertFalse(db.inTransaction());
    }
    @Test void forcedWorkerStopRollsBackBeforeReturningAndReleasesConnection() throws Exception {
        var store=new CommunityStore(db,CommunityStore.defaults());
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var workers=java.util.concurrent.Executors.newSingleThreadExecutor();
        var clean=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            var future=workers.submit(()->{
                try {db.communityTransaction(()->{
                    store.externalNotice(UUID.randomUUID().toString(),"must roll back");
                    entered.countDown();release.await();return null;
                });}
                catch(InterruptedException expected){clean.set(!db.inTransaction());Thread.currentThread().interrupt();}
                catch(Exception failure){throw new RuntimeException(failure);}
            });
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(42,scalar("SELECT 42"));
            assertTrue(WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),workers));
            future.get(1,java.util.concurrent.TimeUnit.SECONDS);assertTrue(clean.get());
            assertEquals(0,db.activeConnections());
            assertEquals(0,scalar("SELECT count(*) FROM notices"));
            assertEquals(0,scalar("SELECT count(*) FROM community_events"));
            assertEquals(42,db.communityTransaction(()->42));
        } finally {release.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
    }
    @Test void terminatedConnectionDoesNotPoisonNextTransaction() throws Exception {
        assertThrows(SQLException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.execute("SELECT pg_terminate_backend(pg_backend_pid())");
            }
            return null;
        }));
        assertFalse(db.inTransaction());
        assertEquals(42,scalar("SELECT 42"));
    }
    @Test void changedMigrationIsRejectedWithoutChangingSchema() throws Exception {
        assertThrows(IllegalStateException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.executeUpdate("UPDATE schema_versions SET checksum='modified' WHERE version=1");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
        assertEquals(8,scalar("SELECT count(*) FROM schema_versions"));
    }
    @Test void newerDatabaseIsRejectedAndTransactionIsCleanedUp() throws Exception {
        assertThrows(IllegalStateException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.executeUpdate("INSERT INTO schema_versions(version,checksum) VALUES(999,'future')");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        assertEquals(8,scalar("SELECT max(version) FROM schema_versions"));
    }
    @Test void migrationSqlFailureRollsBackEarlierDdl() throws Exception {
        assertThrows(SQLException.class,()->db.transaction(()->{
            try(var statement=db.connection().createStatement()) {
                statement.execute("CREATE TABLE migration_rollback_probe(id INT)");
                // Force the pending migration to encounter its already existing columns.
                statement.executeUpdate("DELETE FROM schema_versions WHERE version>=7");
            }
            DatabaseMigrations.apply(db);return null;
        }));
        assertEquals(0,scalar("SELECT count(*) FROM pg_tables WHERE schemaname='rivet' AND tablename='migration_rollback_probe'"));
        assertEquals(8,scalar("SELECT count(*) FROM schema_versions"));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
    }
    @Test void menuLoadLeavesConnectionsForAuth() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(6);
        var release=new java.util.concurrent.CountDownLatch(1);
        var workers=java.util.concurrent.Executors.newFixedThreadPool(6);
        var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
        try {
            for(int i=0;i<6;i++)futures.add(workers.submit(()->{
                try { db.communityTransaction(()->{entered.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS);return null;}); }
                catch(Exception failure){throw new RuntimeException(failure);}
            }));
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(42,scalar("SELECT 42"));
        } finally { release.countDown();workers.shutdown(); }
        for(var future:futures)future.get(5,java.util.concurrent.TimeUnit.SECONDS);
    }
    @Test void incompleteHistoryAndMissingColumnsFailWithoutRepairingData()throws Exception{
        assertThrows(IllegalStateException.class,()->db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("DELETE FROM schema_versions WHERE version=1");}DatabaseMigrations.apply(db);return null;}));
        assertThrows(SQLException.class,()->db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("ALTER TABLE people RENAME COLUMN name TO broken_name");}DatabaseMigrations.apply(db);return null;}));
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});
    }
    @Test void integrationNoticesAndOutboxCommitTogether()throws Exception{
        var store=new CommunityStore(db,CommunityStore.defaults());String player=UUID.randomUUID().toString();
        assertThrows(SQLException.class,()->db.transaction(()->{store.integrationNotice(player,"spark warning","load","spark","spark");try(var q=db.connection().createStatement()){q.execute("SELECT 1/0");}return null;}));
        assertEquals(0,scalar("SELECT count(*) FROM notices"));assertEquals(0,scalar("SELECT count(*) FROM community_events"));
        store.integrationNotice(player,"Report ready","Profile","spark","spark");assertEquals(1,store.unread(player));assertEquals(1,scalar("SELECT count(*) FROM community_events"));
        db.transaction(()->{try(var q=db.connection().createStatement();var row=q.executeQuery("SELECT body->>'routeSection',body->>'priority' FROM notices")){assertTrue(row.next());assertEquals("spark",row.getString(1));assertEquals("important",row.getString(2));}return null;});
    }
    @Test void taskSearchProjectionMigratesAndTracksEveryBodyUpdate()throws Exception{
        db.transaction(()->{try(var q=db.connection().createStatement()){
            q.execute("INSERT INTO community_group_items(id,kind,owner,body) VALUES('projection-task','task','reader','{\"title\":\"Мост\",\"description\":\"100% _готово\",\"status\":\"open\",\"comments\":[\"Keep\"]}')");
            q.execute("ALTER TABLE community_group_items DROP COLUMN task_status,DROP COLUMN task_archived,DROP COLUMN task_search");q.execute("DELETE FROM schema_versions WHERE version>=7");q.execute("DROP TABLE chat_history");
        }DatabaseMigrations.apply(db);DatabaseMigrations.apply(db);
        try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT task_search,task_status,task_archived,body->'comments',revision FROM community_group_items WHERE id='projection-task'")){assertTrue(r.next());assertEquals("Мост 100% _готово",r.getString(1));assertEquals("open",r.getString(2));assertNull(r.getString(3));assertEquals("[\"Keep\"]",r.getString(4));assertEquals(1,r.getLong(5));}
        try(var q=db.connection().createStatement()){q.execute("UPDATE community_group_items SET body=jsonb_build_object('title','Новый','status','done','archived',true) WHERE id='projection-task'");}
        try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT task_search,task_status,task_archived FROM community_group_items WHERE id='projection-task'")){assertTrue(r.next());assertEquals("Новый ",r.getString(1));assertEquals("done",r.getString(2));assertEquals("true",r.getString(3));}
        return null;});
    }
    @Test void v5FixtureMigratesWithIdentityActiveSkinAndOrderingPreserved()throws Exception{
        db.transaction(()->{try(var q=db.connection().createStatement()){q.execute("ALTER TABLE skin_library DROP COLUMN position CASCADE");q.execute("ALTER TABLE community_group_items DROP COLUMN task_status,DROP COLUMN task_archived,DROP COLUMN task_search");q.execute("DELETE FROM schema_versions WHERE version>=6");q.execute("DROP TABLE chat_history");try(var input=getClass().getResourceAsStream("/fixtures/skins-v5.sql")){q.execute(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}DatabaseMigrations.apply(db);return null;});
        db.transaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT name,position,owner::text FROM skin_library ORDER BY position")){assertTrue(rows.next());assertEquals("Builder",rows.getString(1));assertEquals(0,rows.getInt(2));assertEquals("10000000-0000-0000-0000-000000000001",rows.getString(3));assertTrue(rows.next());assertEquals("Explorer",rows.getString(1));assertEquals(1,rows.getInt(2));assertFalse(rows.next());}try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT active::text,slim FROM skin_profiles")){assertTrue(rows.next());assertEquals("00000000-0000-0000-0000-000000000002",rows.getString(1));assertTrue(rows.getBoolean(2));}return null;});
        db.transaction(()->{DatabaseMigrations.apply(db);return null;});assertEquals(8,scalar("SELECT count(*) FROM schema_versions"));
    }
}
