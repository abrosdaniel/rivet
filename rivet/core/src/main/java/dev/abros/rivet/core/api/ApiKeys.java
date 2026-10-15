package dev.abros.rivet.core.api;

import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.PgDatabase;
import dev.abros.rivet.core.ServerSettings;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** PostgreSQL is authoritative on every authorization, including already issued grants. */
public final class ApiKeys implements AutoCloseable {
    // Do not grant prospective scopes until their domain authorization exists.
    public static final Set<String> SCOPES = Set.of("players.presence.read","map.positions.read","map.waystones.read","server.control.read","server.control.write","server.status.read", "server.status.subscribe", "sessions.password", "sessions.link", "groups.read", "events.read", "board.read", "polls.read", "ideas.read", "tasks.read", "map.read", "map.tiles.read", "map.render.manage", "profiles.read", "notifications.read", "chat.read", "chat.write", "tasks.write", "map.write", "profiles.write", "notifications.write", "groups.write", "events.write", "board.write", "polls.write", "ideas.write", "reports.read", "reports.write", "reports.manage", "moderation.manage", "statistics.read", "skins.read", "pack.read", "websocket.connect", "websocket.ticket");
    public record Limits(int keys, int appKeys, int requestsPerMinute, int maxDays, int retentionDays, int auditEntries) {
        public Limits {
            if (keys<1||keys>4096||appKeys<1||appKeys>64||requestsPerMinute<1||requestsPerMinute>100000||maxDays<1||maxDays>3650||retentionDays<1||retentionDays>365||auditEntries<100||auditEntries>100000)
                throw new IllegalArgumentException("Invalid API limits");
        }
        public static Limits from(ServerSettings s) { return new Limits(s.number("api.keyLimit"),s.number("api.appKeyLimit"),s.number("api.requestsPerMinute"),s.number("api.maxKeyDays"),s.number("api.keyRetentionDays"),s.number("api.auditEntries")); }
    }
    public record Key(UUID id, String application, Set<String> scopes, long created, long expires,
                      long revoked, boolean disabled, long lastUsed, int requestsPerMinute) {
        public Key { scopes = Set.copyOf(scopes); }
    }
    public record Issued(Key key, Path deliveryFile) {}
    /** Cannot be constructed from a caller-supplied UUID. Does not represent a player. */
    public static final class Grant {
        private final ApiKeys issuer;
        private final UUID key;
        private final String app;
        private Grant(ApiKeys issuer, UUID key, String app) { this.issuer=issuer;this.key=key;this.app=app; }
        public UUID keyId() { return key; }
        public String application() { return app; }
        @Override public String toString() { return "ApiGrant["+key+"]"; }
    }
    private final PgDatabase db;
    private final Limits limits;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean closed;
    public ApiKeys(PgDatabase db, Limits limits, Clock clock) { this.db=Objects.requireNonNull(db);this.limits=Objects.requireNonNull(limits);this.clock=Objects.requireNonNull(clock); }
    private void open() { if(closed) throw new ApiException(UNAVAILABLE,"API authorization stopped"); }
    @Override public void close() { closed=true; }

    public Issued create(Path root, String app, Set<String> scopes, int days, int perMinute) throws Exception {
        return issue(root, app, scopes, days, perMinute, null);
    }
    public Issued rotate(Path root, UUID id, int days) throws Exception {
        // Rotation preserves scopes and limits and revokes the previous key in the same transaction.
        return issue(root, null, null, days, 0, Objects.requireNonNull(id));
    }
    private Issued issue(Path root, String app, Set<String> scopes, int days, int rpm, UUID replace) throws Exception {
        open(); if(days<1||days>limits.maxDays) throw new ApiException(INVALID_ARGUMENT,"Invalid key lifetime");
        Path[] created = new Path[1];
        try {
            return db.communityTransaction(() -> {
                open(); db.lock("api-key-administration"); long now=clock.millis(); cleanup(root,now);
                String application=app; Set<String> rights=scopes; int rate=rpm;
                if(replace!=null) { var previous=read(replace,false);active(previous,now);application=previous.application;rights=previous.scopes;rate=previous.requestsPerMinute; }
                if(application==null||!application.matches("[a-z][a-z0-9_-]{1,63}")) throw new ApiException(INVALID_ARGUMENT,"Invalid application ID");
                if(rights==null||rights.isEmpty()||rights.size()>64||rights.stream().anyMatch(s->!supportedScope(s))||rate<1||rate>100000) throw new ApiException(INVALID_ARGUMENT,"Invalid scopes or rate");
                if(count("SELECT count(*) FROM api_keys")>=limits.keys) throw new ApiException(BUSY,"Key storage limit reached");
                try(var q=db.connection().prepareStatement("SELECT count(*) FROM api_keys WHERE app=? AND revoked=0 AND expires>?")) {
                    q.setString(1,application);q.setLong(2,now);try(var rows=q.executeQuery()){rows.next();if(rows.getInt(1)-(replace==null?0:1)>=limits.appKeys)throw new ApiException(BUSY,"Application key limit reached");}
                }
                try(var q=db.connection().prepareStatement("INSERT INTO api_apps(id,requests_per_minute) VALUES(?,?) ON CONFLICT(id) DO NOTHING")) { q.setString(1,application);q.setInt(2,limits.requestsPerMinute);q.executeUpdate(); }
                UUID id=UUID.randomUUID(); byte[] entropy=new byte[32];random.nextBytes(entropy);
                String token="rvk_"+id.toString().replace("-","")+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);Arrays.fill(entropy,(byte)0);
                created[0]=ApiSecretFiles.write(root,id,token);
                try(var q=db.connection().prepareStatement("INSERT INTO api_keys(id,app,digest,scopes,created,expires,requests_per_minute) VALUES(?,?,?,?,?,?,?)")) {
                    q.setObject(1,id);q.setString(2,application);q.setString(3,digest(token));q.setArray(4,db.connection().createArrayOf("text",rights.toArray()));q.setLong(5,now);q.setLong(6,now+days*86400000L);q.setInt(7,rate);q.executeUpdate();
                }
                if(replace!=null){updateState(replace,"revoked",now);audit("rotate-old",application,replace,now);}
                audit(replace==null?"create":"rotate-new",application,id,now);
                open(); return new Issued(read(id,false),created[0]);
            });
        } catch(Exception|Error failure) {
            if(created[0]!=null)try{Files.deleteIfExists(created[0]);}catch(Exception cleanup){failure.addSuppressed(new java.io.IOException("Cannot remove failed API key delivery"));}
            throw failure;
        }
    }
    public List<Key> list() throws Exception {
        open();return db.communityTransaction(()->{open();var result=new ArrayList<Key>();try(var q=db.connection().prepareStatement("SELECT * FROM api_keys ORDER BY created DESC,id LIMIT ?")){q.setInt(1,limits.keys);try(var rows=q.executeQuery()){while(rows.next())result.add(key(rows));}}return List.copyOf(result);});
    }
    public void state(UUID id, String action) throws Exception {
        if(!Set.of("disable","enable","revoke").contains(action))throw new ApiException(INVALID_ARGUMENT,"Invalid key action");
        open();db.communityTransaction(()->{open();db.lock("api-key-administration");var key=read(id,true);long now=clock.millis();
            if(!action.equals("revoke")&&(key.revoked!=0||key.expires<=now))throw new ApiException(CONFLICT,"Inactive key cannot be changed");
            if(action.equals("revoke")){if(key.revoked==0)updateState(id,"revoked",now);}else updateState(id,"disabled",action.equals("disable"));
            audit(action,key.application,id,now);return null;});
    }
    public void applicationLimit(String app, int rpm) throws Exception {
        if(rpm<1||rpm>100000)throw new ApiException(INVALID_ARGUMENT,"Invalid application limit");
        open();db.communityTransaction(()->{open();db.lock("api-key-administration");try(var q=db.connection().prepareStatement("UPDATE api_apps SET requests_per_minute=? WHERE id=?")){q.setInt(1,rpm);q.setString(2,app);if(q.executeUpdate()!=1)throw new ApiException(NOT_FOUND,"Unknown application");}audit("limit",app,null,clock.millis());return null;});
    }
    public Grant authorize(String token, String scope) throws Exception {
        open();requireScope(scope);
        if(token==null||!token.matches("rvk_[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}"))throw denied();
        String raw=token.substring(4,36);UUID id=UUID.fromString(raw.substring(0,8)+"-"+raw.substring(8,12)+"-"+raw.substring(12,16)+"-"+raw.substring(16,20)+"-"+raw.substring(20));
        String expected=digest(token);
        return db.communityTransaction(()->{
            open();Key key;
            try { key=read(id,false); } catch(ApiException missing){throw denied();}
            // Consistent app -> key locking prevents issuing more keys from bypassing the shared budget.
            int appLimit;long appBucket;int appRequests;
            try(var q=db.connection().prepareStatement("SELECT requests_per_minute,bucket,requests FROM api_apps WHERE id=? FOR UPDATE")){q.setString(1,key.application);try(var rows=q.executeQuery()){if(!rows.next())throw denied();appLimit=rows.getInt(1);appBucket=rows.getLong(2);appRequests=rows.getInt(3);}}
            long now=clock.millis(),bucket=now/60000;long keyBucket;int keyRequests;
            try(var q=db.connection().prepareStatement("SELECT * FROM api_keys WHERE id=? FOR UPDATE")){q.setObject(1,id);try(var rows=q.executeQuery()){if(!rows.next())throw denied();
                if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),rows.getString("digest").getBytes(StandardCharsets.US_ASCII)))throw denied();
                key=key(rows);keyBucket=rows.getLong("bucket");keyRequests=rows.getInt("requests");}}
            active(key,now);if(!key.scopes.contains(scope))throw new ApiException(FORBIDDEN,"Scope not granted");
            if(appBucket!=bucket)appRequests=0;if(keyBucket!=bucket)keyRequests=0;
            if(appRequests>=appLimit||keyRequests>=key.requestsPerMinute)throw new ApiException(RATE_LIMITED,"API request budget exhausted");
            try(var q=db.connection().prepareStatement("UPDATE api_apps SET bucket=?,requests=? WHERE id=?")){q.setLong(1,bucket);q.setInt(2,appRequests+1);q.setString(3,key.application);q.executeUpdate();}
            try(var q=db.connection().prepareStatement("UPDATE api_keys SET bucket=?,requests=?,used=? WHERE id=?")){q.setLong(1,bucket);q.setInt(2,keyRequests+1);q.setLong(3,now);q.setObject(4,id);q.executeUpdate();}
            open();return new Grant(this,id,key.application);
        });
    }
    /** Recheck before queued work or delivery. Does not consume a second request token. */
    public void revalidate(Grant grant, String scope) throws Exception {
        open();requireScope(scope);if(grant==null||grant.issuer!=this)throw denied();
        db.communityTransaction(()->{open();Key key;try{key=read(grant.key,false);}catch(ApiException missing){throw denied();}active(key,clock.millis());if(!key.scopes.contains(scope))throw new ApiException(FORBIDDEN,"Scope not granted");return null;});
    }
    public static boolean supportedScope(String scope){return scope!=null&&(SCOPES.contains(scope)||scope.startsWith("map.tiles.profile.")&&TerrainProfiles.privateName(scope.substring("map.tiles.profile.".length())));}
    private static void requireScope(String scope){if(!supportedScope(scope))throw new ApiException(FORBIDDEN,"Unsupported scope");}
    private static ApiException denied(){return new ApiException(UNAUTHENTICATED,"Invalid or inactive API key");}
    private static void active(Key key,long now){if(key.revoked!=0||key.disabled||key.expires<=now)throw denied();}
    private Key read(UUID id,boolean lock)throws Exception{try(var q=db.connection().prepareStatement("SELECT * FROM api_keys WHERE id=?"+(lock?" FOR UPDATE":""))){q.setObject(1,id);try(var rows=q.executeQuery()){if(!rows.next())throw new ApiException(NOT_FOUND,"Unknown key ID");return key(rows);}}}
    private static Key key(ResultSet r)throws SQLException{return new Key(r.getObject("id",UUID.class),r.getString("app"),Set.of((String[])r.getArray("scopes").getArray()),r.getLong("created"),r.getLong("expires"),r.getLong("revoked"),r.getBoolean("disabled"),r.getLong("used"),r.getInt("requests_per_minute"));}
    private void updateState(UUID id,String column,Object value)throws SQLException{try(var q=db.connection().prepareStatement("UPDATE api_keys SET "+column+"=? WHERE id=?")){q.setObject(1,value);q.setObject(2,id);q.executeUpdate();}}
    private int count(String sql)throws SQLException{try(var q=db.connection().createStatement();var r=q.executeQuery(sql)){r.next();return r.getInt(1);}}
    private static String digest(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private void cleanup(Path root,long now)throws Exception{
        try(var q=db.connection().prepareStatement("SELECT id FROM api_keys WHERE expires<=? OR revoked>0")){q.setLong(1,now);try(var rows=q.executeQuery()){while(rows.next())ApiSecretFiles.remove(root,rows.getObject(1,UUID.class));}}
        try(var q=db.connection().prepareStatement("DELETE FROM api_keys WHERE expires<? OR (revoked>0 AND revoked<?)")){long cutoff=now-limits.retentionDays*86400000L;q.setLong(1,cutoff);q.setLong(2,cutoff);q.executeUpdate();}
        try(var q=db.connection().createStatement()){q.executeUpdate("DELETE FROM api_apps WHERE NOT EXISTS (SELECT 1 FROM api_keys WHERE api_keys.app=api_apps.id)");}
    }
    private void audit(String action,String app,UUID id,long now)throws SQLException{
        try(var q=db.connection().prepareStatement("INSERT INTO api_key_audit(at,action,app,key_id) VALUES(?,?,?,?)")){q.setLong(1,now);q.setString(2,action);q.setString(3,app);q.setObject(4,id);q.executeUpdate();}
        try(var q=db.connection().prepareStatement("DELETE FROM api_key_audit WHERE id IN (SELECT id FROM api_key_audit ORDER BY id DESC OFFSET ?)")){q.setInt(1,limits.auditEntries);q.executeUpdate();}
    }
}
