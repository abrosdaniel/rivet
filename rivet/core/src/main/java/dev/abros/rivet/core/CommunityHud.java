package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Bounded actor-scoped HUD read model; no subscriptions or screen state required. */
final class CommunityHud {
 private final PgDatabase db; private final CommunityStore store;
 CommunityHud(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject snapshot(CommunityStore.Actor actor,JsonObject input,JsonObject config)throws Exception {
  long now=System.currentTimeMillis(),since=input.has("since")?input.get("since").getAsBigDecimal().longValueExact():0;
  if(since<0)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_c502b2d51fe7"));
  var out=new JsonObject();out.addProperty("kind","hud");out.addProperty("serverTime",now);out.addProperty("request",Json.opt(input,"request",""));out.addProperty("unread",store.unread(actor.id()));out.add("preferences",store.preferences(actor.id()));
  var groups=new JsonArray();for(var group:store.memberGroups(actor.id())){var row=new JsonObject();row.addProperty("id",Json.str(group,"id"));row.addProperty("title",Json.str(group,"title"));groups.add(row);}out.add("groups",groups);
  String pin=Json.opt(input,"pin","");if(pin.length()>100)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_b6b111b16740"));
  // A pin never expands visibility: only own or assigned tasks in an active membership qualify.
  out.add("tasks",new JsonArray());out.add("holograms",new JsonArray());
  var filter=new JsonObject();filter.addProperty("participating",true);var events=new CommunityQueries(db).list(actor,filter,"home",now,config);var nearest=new JsonArray();if(!events.isEmpty())nearest.add(events.get(0));out.add("events",nearest);
  long cap;try(var q=db.connection().prepareStatement("SELECT coalesce(max(id),0) FROM notices WHERE recipient=?")){q.setString(1,actor.id());try(var rows=q.executeQuery()){rows.next();cap=rows.getLong(1);}}
  var notices=new JsonArray();long next=since;boolean initial=!input.has("since"),more=false;
  if(!initial)try(var q=db.connection().prepareStatement("SELECT id,body,read FROM notices WHERE "+NotificationStore.active("")+" AND recipient=? AND id>? AND id<=? ORDER BY id ASC LIMIT 51")){q.setString(1,actor.id());q.setLong(2,since);q.setLong(3,cap);try(var rows=q.executeQuery()){while(rows.next()){if(notices.size()==50){more=true;break;}next=rows.getLong(1);var notice=Json.parse(rows.getString(2));notice.addProperty("id",Long.toString(next));notice.addProperty("read",rows.getBoolean(3));notices.add(notice);}}}
  if(initial){var digest=new JsonArray();try(var q=db.connection().prepareStatement("SELECT coalesce(body->>'section','notifications'),count(*),"+NotificationCategories.sql("")+" FROM notices WHERE "+NotificationStore.active("")+" AND recipient=? AND read=0 GROUP BY body->>'section',"+NotificationCategories.sql("")+" ORDER BY count(*) DESC,coalesce(body->>'section','notifications') LIMIT 20")){q.setString(1,actor.id());try(var rows=q.executeQuery()){while(rows.next()){var row=new JsonObject();row.addProperty("section",rows.getString(1));row.addProperty("count",rows.getInt(2));row.addProperty("sourceType",rows.getString(3));digest.add(row);}}}out.add("digest",digest);}
  out.add("notices",notices);out.addProperty("sequence",initial?cap:more?next:Math.max(next,cap));out.addProperty("more",more);return out;
 }
}
