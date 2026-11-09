package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Notification persistence, independent of menu lifetime and community domain handlers. */
public final class NotificationStore {
    static final int MAX_PER_RECIPIENT = 200;
    static String active(String alias) {
        String column = alias + "expires_at";
        return "(" + column + "=0 OR " + column + ">extract(epoch from statement_timestamp())*1000)";
    }
    public record Summary(int unread, long sequence) {}
    private final PgDatabase database;

    public NotificationStore(PgDatabase database) { this.database = database; }

    // Mutations join the caller's transaction so domain changes and delivery cannot diverge.
    long append(String recipient, JsonObject body) throws Exception {
        database.lock("notice:" + recipient);
        long id;
        try (var query = database.connection().prepareStatement("INSERT INTO notices(recipient,body,expires_at) VALUES(?,?::jsonb,?) RETURNING id")) {
            query.setString(1, recipient);
            query.setString(2, body.toString());
            query.setLong(3, body.has("expiresAt") ? body.get("expiresAt").getAsLong() : 0);
            try (var rows = query.executeQuery()) { rows.next(); id = rows.getLong(1); }
        }
        try (var query = database.connection().prepareStatement("DELETE FROM notices WHERE recipient=? AND id NOT IN (SELECT id FROM notices WHERE recipient=? ORDER BY id DESC LIMIT ?)")) {
            query.setString(1, recipient);
            query.setString(2, recipient);
            query.setInt(3, MAX_PER_RECIPIENT);
            query.executeUpdate();
        }
        ChangeOutbox.add(database, "notifications", Json.opt(body, "target", ""), recipient);
        return id;
    }

    /** Internal journal projection; no item data or private draft is copied to notifications. */
    public void trade(TradeBook.Notice notice)throws Exception {
        UUID.fromString(notice.recipient());UUID.fromString(notice.deal());
        database.communityTransaction(()->{
            database.lock("notice:"+notice.recipient());
            try(var q=database.connection().prepareStatement("SELECT 1 FROM notices WHERE recipient=? AND body->>'section'='trade' AND body->>'target'=? AND (body->>'tradeSequence')::bigint>=?")){
                q.setString(1,notice.recipient());q.setString(2,notice.deal());q.setLong(3,notice.sequence());try(var rows=q.executeQuery()){if(rows.next())return null;}
            }
            // One current card per exchange, with a fresh unread sequence for each published update.
            try(var q=database.connection().prepareStatement("DELETE FROM notices WHERE recipient=? AND body->>'section'='trade' AND body->>'target'=?")){q.setString(1,notice.recipient());q.setString(2,notice.deal());q.executeUpdate();}
            var body=new JsonObject();body.addProperty("section","trade");body.addProperty("target",notice.deal());body.addProperty("tradeSequence",notice.sequence());body.addProperty("at",System.currentTimeMillis());body.addProperty("event",notice.event());body.addProperty("priority","important");
            LocalizedText.parts(LocalizedText.key("rivet.trade.notice."+notice.event()),LocalizedText.literal(" · "+notice.other())).put(body,"title");
            LocalizedText.key("rivet.trade.notice.open").put(body,"body");append(notice.recipient(),body);return null;
        });
    }

    /** Owner/type/key are internal, validated by NotificationPublisher, never accepted from clients. */
    dev.abros.rivet.api.NotificationsApi.Receipt publish(String recipient, String type, String key,
            dev.abros.rivet.api.NotificationsApi.Message message, long expected, boolean revoke) throws Exception {
        return database.communityTransaction(() -> {
            database.lock("notice:" + recipient);
            long id = 0, revision = 0;
            JsonObject previous = null;
            try (var query = database.connection().prepareStatement("SELECT id,body FROM notices WHERE "+active("")+" AND recipient=? AND body->>'sourceType'=? AND body->>'sourceKey'=?")) {
                query.setString(1, recipient); query.setString(2, type); query.setString(3, key);
                try (var rows = query.executeQuery()) {
                    if (rows.next()) { id = rows.getLong(1); previous = Json.parse(rows.getString(2)); revision = previous.get("sourceRevision").getAsLong(); }
                }
            }
            if (revoke && previous == null) return null;
            if (!revoke && previous != null && (expected == 0 || previous.has("sourcePreviousRevision") && expected == previous.get("sourcePreviousRevision").getAsLong())
                    && Json.str(previous, "title").equals(message.title()) && Json.str(previous, "body").equals(message.body())
                    && (previous.has("expiresAt") ? previous.get("expiresAt").getAsLong() : 0) == message.expiresAt()
                    && java.util.Objects.equals(previous.get("titleI18n"), translations(message, true))
                    && java.util.Objects.equals(previous.get("bodyI18n"), translations(message, false))
                    && java.util.Objects.equals(previous.get("notificationAction"), action(message)))
                return new dev.abros.rivet.api.NotificationsApi.Receipt(id, revision);
            if (expected != revision) throw new dev.abros.rivet.api.ApiException(dev.abros.rivet.api.ApiException.Code.CONFLICT, "Notification changed");
            long nextRevision = 0;
            if (!revoke) {
                // Reuse the persistent monotonic sequence so a retained key that is later
                // recreated cannot accept a stale revision from its previous incarnation.
                try (var query = database.connection().createStatement();
                     var rows = query.executeQuery("SELECT nextval(pg_get_serial_sequence('notices','id'))")) {
                    rows.next(); nextRevision = rows.getLong(1);
                }
            }
            if (revoke) {
                try (var query = database.connection().prepareStatement("DELETE FROM notices WHERE recipient=? AND id=?")) {
                    query.setString(1, recipient); query.setLong(2, id); query.executeUpdate();
                }
            } else {
                var body = previous == null ? new JsonObject() : previous.deepCopy();
                body.addProperty("section", "notifications"); body.addProperty("target", "");
                body.addProperty("event", "external"); body.addProperty("priority", "ordinary");
                body.addProperty("title", message.title()); body.addProperty("body", message.body());
                body.remove("titleI18n"); body.remove("bodyI18n");
                if (!message.translations().isEmpty()) {
                    body.add("titleI18n", translations(message, true)); body.add("bodyI18n", translations(message, false));
                }
                body.addProperty("sourceType", type); body.addProperty("sourceKey", key);
                body.addProperty("sourceRevision", nextRevision);
                body.addProperty("sourcePreviousRevision", expected);
                body.addProperty("expiresAt", message.expiresAt());
                body.remove("notificationAction");
                if(message.action()!=null)body.add("notificationAction",action(message));
                if (previous == null) {
                    body.addProperty("at", System.currentTimeMillis());
                    return new dev.abros.rivet.api.NotificationsApi.Receipt(append(recipient, body), nextRevision);
                }
                try (var query = database.connection().prepareStatement("UPDATE notices SET body=?::jsonb,expires_at=? WHERE recipient=? AND id=?")) {
                    query.setString(1, body.toString()); query.setLong(2, message.expiresAt()); query.setString(3, recipient); query.setLong(4, id); query.executeUpdate();
                }
            }
            ChangeOutbox.add(database, "notifications", Long.toString(id), recipient);
            return revoke ? null : new dev.abros.rivet.api.NotificationsApi.Receipt(id, nextRevision);
        });
    }

    private static JsonObject action(dev.abros.rivet.api.NotificationsApi.Message message) {
        if(message.action()==null)return null;
        var value=new JsonObject();
        if(message.action() instanceof dev.abros.rivet.api.NotificationsApi.OpenGroup group) {
            value.addProperty("type","openGroup");value.addProperty("id",group.group().toString());
        } else if(message.action() instanceof dev.abros.rivet.api.NotificationsApi.OpenReport report) {
            value.addProperty("type","openReport");value.addProperty("id",report.report().toString());
        } else if(message.action() instanceof dev.abros.rivet.api.NotificationsApi.OpenOwnPublication publication) {
            value.addProperty("type","openOwnPublication");value.addProperty("id",publication.publication().toString());
        } else throw new IllegalArgumentException("Unsupported notification action");
        return value;
    }

    JsonObject owned(String recipient,long id) throws Exception {
        try(var query=database.connection().prepareStatement("SELECT body,read FROM notices WHERE "+active("")+" AND recipient=? AND id=?")) {
            query.setString(1,recipient);query.setLong(2,id);
            try(var rows=query.executeQuery()) {
                if(!rows.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,Messages.text("rivet.core.notification_not_found_ff1265df"));
                var body=Json.parse(rows.getString(1));body.addProperty("id",Long.toString(id));body.addProperty("read",rows.getBoolean(2));return body;
            }
        }
    }

    private static JsonObject translations(dev.abros.rivet.api.NotificationsApi.Message message, boolean title) {
        if (message.translations().isEmpty()) return null;
        var result = new JsonObject();
        message.translations().forEach((locale, value) -> result.addProperty(locale, title ? value.title() : value.body()));
        return result;
    }

    /** Bounded cleanup also publishes invalidations for clients that already display a notice. */
    int expire() throws Exception {
        return database.communityTransaction(() -> {
            var recipients = new java.util.HashSet<String>();
            int count = 0;
            try (var query = database.connection().createStatement();
                 var rows = query.executeQuery("WITH expired AS (SELECT id FROM notices WHERE expires_at>0 AND expires_at<=extract(epoch from statement_timestamp())*1000 ORDER BY expires_at,id LIMIT 256 FOR UPDATE SKIP LOCKED) DELETE FROM notices n USING expired e WHERE n.id=e.id RETURNING n.recipient")) {
                while (rows.next()) { count++; recipients.add(rows.getString(1)); }
            }
            for (String recipient : recipients) ChangeOutbox.add(database, "notifications", "", recipient);
            return count;
        });
    }

    JsonObject preferences(String recipient) throws Exception {
        return database.communityTransaction(() -> {
            try (var query = database.connection().prepareStatement("SELECT body FROM preferences WHERE id=?")) {
                query.setString(1, recipient);
                try (var rows = query.executeQuery()) {
                    return rows.next() ? Json.parse(rows.getString(1)) : new JsonObject();
                }
            }
        });
    }

    /** Input policy is checked by the calling domain before entering persistence. */
    JsonObject patchPreferences(String recipient, JsonObject patch) throws Exception {
        database.lock("preferences:" + recipient);
        Json.keys(patch,"category","muted","snoozeUntil");
        var current=preferences(recipient);
        if(!current.has("muted"))current.add("muted",new com.google.gson.JsonArray());
        if(patch.has("category")) {
            if(patch.has("snoozeUntil")||!patch.has("muted")||!patch.get("muted").isJsonPrimitive()||!patch.getAsJsonPrimitive("muted").isBoolean())throw new IllegalArgumentException("Invalid notification preference patch");
            String category=Json.str(patch,"category");
            if(!NotificationCategories.valid(category))throw new IllegalArgumentException("Invalid notification category");
            var value=new com.google.gson.JsonPrimitive(category);var muted=current.getAsJsonArray("muted");
            while(muted.contains(value))muted.remove(value);
            if(patch.get("muted").getAsBoolean())muted.add(value);
        } else {
            if(patch.has("muted")||!patch.has("snoozeUntil"))throw new IllegalArgumentException("Invalid notification preference patch");
            current.add("snoozeUntil",patch.get("snoozeUntil").deepCopy());
        }
        return current;
    }

    /** Called in the same transaction as validation and patchPreferences. */
    void savePreferences(String recipient, JsonObject preferences) throws Exception {
        database.lock("preferences:" + recipient);
        try (var query = database.connection().prepareStatement("INSERT INTO preferences VALUES(?,?::jsonb) ON CONFLICT(id) DO UPDATE SET body=excluded.body")) {
            query.setString(1, recipient);
            query.setString(2, preferences.toString());
            query.executeUpdate();
        }
        ChangeOutbox.add(database, "notifications", "", recipient);
    }

    void read(String recipient, String id, boolean exact) throws Exception {
        if (exact && id.isEmpty()) throw new IllegalArgumentException(Messages.text("rivet.message.error_a5a30fec49b3"));
        String sql = exact ? "UPDATE notices SET read=1 WHERE "+active("")+" AND recipient=? AND read=0 AND id=?"
                : "UPDATE notices SET read=1 WHERE "+active("")+" AND recipient=? AND read=0 AND (?='' OR id=? OR (coalesce(body->>'target','')<>'' AND body->>'target'=(SELECT body->>'target' FROM notices WHERE id=? AND recipient=?) AND body->>'section'=(SELECT body->>'section' FROM notices WHERE id=? AND recipient=?)))";
        long key = id.isEmpty() ? 0 : Long.parseLong(id);
        try (var query = database.connection().prepareStatement(sql)) {
            query.setString(1, recipient);
            if (exact) query.setLong(2, key);
            else {
                query.setString(2, id);
                query.setLong(3, key);
                query.setLong(4, key);
                query.setString(5, recipient);
                query.setLong(6, key);
                query.setString(7, recipient);
            }
            if (query.executeUpdate() > 0) ChangeOutbox.add(database, "notifications", id, recipient);
        }
    }

    int unread(String recipient) throws Exception {
        return database.communityTransaction(() -> {
            try (var query = database.connection().prepareStatement("SELECT count(*) FROM notices WHERE "+active("")+" AND recipient=? AND read=0")) {
                query.setString(1, recipient);
                try (var rows = query.executeQuery()) { rows.next(); return rows.getInt(1); }
            }
        });
    }

    JsonArray page(String recipient, String category, String token, String scope) throws Exception {
        return database.communityTransaction(() -> {
            CommunityCursor after = token.isEmpty() ? null : CommunityCursor.decode(token, scope);
            long ceiling;
            if (after != null) ceiling = after.ceiling();
            else try (var query = database.connection().createStatement();
                      var rows = query.executeQuery("SELECT coalesce(max(id),0) FROM notices")) {
                rows.next(); ceiling = rows.getLong(1);
            }
            var result = new JsonArray();
            long last = 0;
            boolean more = false;
            try (var query = database.connection().prepareStatement("SELECT id,body,read FROM notices WHERE "+active("")+" AND recipient=? AND id<=? AND id<? AND (?='' OR "+NotificationCategories.sql("")+"=? OR body->>'section'=?) ORDER BY id DESC LIMIT 11")) {
                query.setString(1, recipient);
                query.setLong(2, ceiling);
                query.setLong(3, after == null ? Long.MAX_VALUE : after.sequence());
                query.setString(4, category);
                query.setString(5, category);
                query.setString(6, category);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        if (result.size() == 10) { more = true; break; }
                        last = rows.getLong(1);
                        var body = Json.parse(rows.getString(2));
                        body.addProperty("id", Long.toString(last));
                        body.addProperty("read", rows.getBoolean(3));
                        result.add(body);
                    }
                }
            }
            if (more) result.get(result.size() - 1).getAsJsonObject()
                    .addProperty("_nextCursor", new CommunityCursor(last, 0, ceiling, scope).encode());
            return result;
        });
    }

    JsonArray categories(String recipient) throws Exception {
        return database.communityTransaction(()->{
            var values=new java.util.TreeSet<String>();
            try(var query=database.connection().prepareStatement("SELECT DISTINCT "+NotificationCategories.sql("")+" FROM notices WHERE recipient=? AND "+active(""))) {
                query.setString(1,recipient);try(var rows=query.executeQuery()){while(rows.next())values.add(rows.getString(1));}
            }
            var prefs=preferences(recipient);if(prefs.has("muted"))for(var value:prefs.getAsJsonArray("muted"))values.add(value.getAsString());
            var result=new JsonArray();for(String value:values)if(NotificationCategories.valid(value))result.add(value);return result;
        });
    }

    private static final String SUMMARY = "SELECT n.recipient,count(*),coalesce(max(n.id) FILTER (WHERE coalesce((p.body->>'snoozeUntil')::bigint,0)<=extract(epoch from now())*1000 AND NOT EXISTS (SELECT 1 FROM jsonb_array_elements_text(coalesce(p.body->'muted','[]'::jsonb)) AS muted(section) WHERE (muted.section=n.body->>'section' OR muted.section="+NotificationCategories.sql("n.")+"))),0) FROM notices n LEFT JOIN preferences p ON p.id=n.recipient WHERE "+active("n.")+" AND n.read=0 AND n.recipient=ANY(?) GROUP BY n.recipient";

    private Map<String, Summary> summaries(Collection<String> recipients) throws Exception {
        return database.communityTransaction(() -> {
            var result = new HashMap<String, Summary>();
            if (recipients.isEmpty()) return result;
            var ids = database.connection().createArrayOf("text", recipients.toArray());
            try (var query = database.connection().prepareStatement(SUMMARY)) {
                query.setArray(1, ids);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.put(rows.getString(1), new Summary(rows.getInt(2), rows.getLong(3)));
                }
            } finally { ids.free(); }
            for (String id : recipients) result.putIfAbsent(id, new Summary(0, 0));
            return result;
        });
    }

    long popupSequence(String recipient) throws Exception {
        return summaries(java.util.List.of(recipient)).get(recipient).sequence();
    }

    public Map<UUID, Summary> summary(Collection<UUID> recipients) throws Exception {
        var result = new HashMap<UUID, Summary>();
        summaries(recipients.stream().map(UUID::toString).toList())
                .forEach((id, summary) -> result.put(UUID.fromString(id), summary));
        return result;
    }
}
