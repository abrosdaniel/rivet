package dev.abros.rivet.core.api;
import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.*;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;
/** Transactional unsigned chat with bounded delivery queue. No client component/command payloads. */
public final class ApiChat {
 @FunctionalInterface public interface LocalAudience {List<String> recipients(UUID player)throws Exception;}
 @FunctionalInterface public interface Delivery {void send(String channel,String sender,String text,Set<String> recipients)throws Exception;}
 private final PgDatabase db;private final CommunityStore store;private final ServerSettings settings;private final LocalAudience local;
 public ApiChat(PgDatabase db,CommunityStore store,ServerSettings settings,LocalAudience local){this.db=db;this.store=store;this.settings=settings;this.local=local;}
 public void send(CommunityStore.Actor actor,String application,JsonObject data)throws Exception{
  if(!settings.flag("chat.enabled"))throw new ApiException(MODULE_DISABLED,"Chat disabled");ApiJson.fields(data,"channel","text");String channel=ApiJson.string(data,"channel"),text=ApiJson.string(data,"text").strip();
  if(text.isEmpty()||text.length()>256||text.codePoints().anyMatch(Character::isISOControl))throw ApiJson.invalid();long now=System.currentTimeMillis();
  Set<String> recipients=Set.of();
  if(channel.equals("local")){if(!settings.flag("chat.local"))throw new ApiException(MODULE_DISABLED,"Local chat disabled");recipients=Set.copyOf(local.recipients(UUID.fromString(actor.id())));}
  else if(!channel.equals("global")){if(!settings.flag("chat.group"))throw new ApiException(MODULE_DISABLED,"Group chat disabled");UUID.fromString(channel);lockGroup(channel);recipients=store.chatMembers(channel,actor.id());}
  db.lock("api-chat");try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT count(*) FROM records WHERE namespace='api-chat'")){rows.next();if(rows.getInt(1)>=1024)throw new ApiException(BUSY,"Chat delivery queue full");}
  var rate=store.record("api-chat-rate",actor.id());if(rate==null||now-rate.get("at").getAsLong()>=10000){rate=new JsonObject();rate.addProperty("at",now);rate.addProperty("count",0);}int count=rate.get("count").getAsInt();if(count>=5)throw new ApiException(RATE_LIMITED,"Chat rate limit");rate.addProperty("count",count+1);store.record("api-chat-rate",actor.id(),rate);
  String line="[API "+application+"] "+actor.name()+": "+text;var component=new JsonObject();component.addProperty("text",line);
  new ChatHistory(db,settings.number("chat.history"),settings.number("chat.historyDays")).append(channel,now,component.toString(),recipients);
  var pending=new JsonObject();pending.addProperty("sender",actor.id());pending.addProperty("channel",channel);pending.addProperty("text",line);pending.addProperty("at",now);pending.add("recipients",Json.GSON.toJsonTree(recipients));store.record("api-chat",UUID.randomUUID().toString(),pending);
 }
 private void lockGroup(String id)throws Exception{try(var q=db.connection().prepareStatement("SELECT id FROM documents WHERE id=? FOR SHARE")){q.setString(1,id);try(var rows=q.executeQuery()){while(rows.next()){} }}}
 public void drain(Delivery delivery)throws Exception{
  if(!settings.flag("chat.enabled"))return;
  db.communityTransaction(()->{try(var q=db.connection().createStatement();var rows=q.executeQuery("SELECT id,body FROM records WHERE namespace='api-chat' ORDER BY sequence LIMIT 16 FOR UPDATE SKIP LOCKED")){
   while(rows.next()){String id=rows.getString(1);var item=Json.parse(rows.getString(2));String channel=Json.str(item,"channel"),sender=Json.str(item,"sender");Set<String> recipients=new HashSet<>();boolean permitted=System.currentTimeMillis()-item.get("at").getAsLong()<60000;
    if(channel.equals("local")){permitted&=settings.flag("chat.local");if(permitted)try{recipients.addAll(local.recipients(UUID.fromString(sender)));var original=new HashSet<String>();for(var e:item.getAsJsonArray("recipients"))original.add(e.getAsString());recipients.retainAll(original);}catch(ApiException absent){permitted=false;}}
    else if(!channel.equals("global")){permitted&=settings.flag("chat.group")&&store.modules().enabled("groups");if(permitted)try{lockGroup(channel);recipients.addAll(store.chatMembers(channel,sender));}catch(CommunityFailure denied){permitted=false;}}
    if(permitted)delivery.send(channel,sender,Json.str(item,"text"),Set.copyOf(recipients));
    try(var delete=db.connection().prepareStatement("DELETE FROM records WHERE namespace='api-chat' AND id=?")){delete.setString(1,id);if(delete.executeUpdate()>0)db.communityChanged();}
   }
  }try(var cleanup=db.connection().prepareStatement("DELETE FROM records WHERE namespace='api-chat-rate' AND (body->>'at')::bigint<?")){cleanup.setLong(1,System.currentTimeMillis()-86400000L);cleanup.executeUpdate();}return null;});
 }
}
