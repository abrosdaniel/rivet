package dev.abros.rivet.core;

import java.util.*;

/** Server-owned unsigned replay. Local audiences are frozen; group membership is checked on reads. */
public final class ChatHistory {
 public record Message(long id,long at,String body){}
 private final PgDatabase db;private final int limit,days;
 public ChatHistory(PgDatabase db,int limit,int days){if(limit<0||limit>1000||days<1||days>365)throw new IllegalArgumentException();this.db=db;this.limit=limit;this.days=days;}
 public void append(String channel,long at,String body,Collection<String> recipients)throws Exception{
  if(limit==0)return;if(!Set.of("global","local").contains(channel))UUID.fromString(channel);
  if(body.length()>12000||recipients.size()>4096)throw new IllegalArgumentException("Chat history entry too large");
  db.communityTransaction(()->{
   db.lock("chat-history");
   try(var q=db.connection().prepareStatement("INSERT INTO chat_history(channel,at,body,recipients) VALUES(?,?,?,?)")){
    q.setString(1,channel);q.setLong(2,at);q.setString(3,body);var array=db.connection().createArrayOf("text",recipients.toArray(String[]::new));try{q.setArray(4,array);q.executeUpdate();}finally{array.free();}
   }
   try(var q=db.connection().prepareStatement("DELETE FROM chat_history WHERE channel=? AND id NOT IN (SELECT id FROM chat_history WHERE channel=? ORDER BY id DESC LIMIT ?)")){q.setString(1,channel);q.setString(2,channel);q.setInt(3,limit);q.executeUpdate();}
   return null;
  });
 }
 /** Bounded maintenance, independent of new messages and history being enabled. */
 public int cleanup(long now)throws Exception{
  return db.communityTransaction(()->{try(var q=db.connection().prepareStatement("DELETE FROM chat_history WHERE id IN (SELECT id FROM chat_history WHERE at<? ORDER BY at LIMIT 256 FOR UPDATE SKIP LOCKED)")){q.setLong(1,now-days*86400000L);return q.executeUpdate();}});
 }
 @FunctionalInterface public interface Delivery {void send(List<Message> messages)throws Exception;}
 public record ReplayRequest(String player,long before,boolean local,boolean groups,long cursor,int count){}
 public record ReplayPage(ReplayRequest request,List<Message> messages){public ReplayPage{messages=List.copyOf(messages);}}
 @FunctionalInterface public interface BatchDelivery {void send(List<ReplayPage> pages)throws Exception;}
 /** Hold group document locks through delivery. Membership writes update that document first. */
 public void replay(String player,long before,boolean local,boolean groups,long cursor,int count,Delivery delivery)throws Exception{
  replayBatch(List.of(new ReplayRequest(player,before,local,groups,cursor,count)),pages->delivery.send(pages.getFirst().messages()));
 }
 /** A bounded batch shares one game-thread handoff; access is still checked separately for every recipient. */
 public void replayBatch(List<ReplayRequest> requests,BatchDelivery delivery)throws Exception{
  var batch=List.copyOf(requests);if(batch.isEmpty()||batch.size()>16)throw new IllegalArgumentException("Replay batch must contain 1–16 recipients");
  db.communityTransaction(()->{
   var first=new ArrayList<ReplayPage>();
   for(var request:batch)first.add(new ReplayPage(request,recent(request.player,request.before,request.local,request.groups,request.cursor,request.count)));
   var ids=first.stream().flatMap(page->page.messages.stream()).map(Message::id).distinct().toArray(Long[]::new);
   if(ids.length>0){
    var array=db.connection().createArrayOf("bigint",ids);
    try(var q=db.connection().prepareStatement("SELECT d.id FROM documents d WHERE d.id IN (SELECT channel FROM chat_history WHERE id=ANY(?)) ORDER BY d.id FOR SHARE")){
     q.setArray(1,array);try(var rows=q.executeQuery()){while(rows.next()){} }
    }finally{array.free();}
   }
   // A revocation may have committed while the shared locks were being acquired.
   var pages=new ArrayList<ReplayPage>();
   for(var page:first){var request=page.request;var permitted=new HashSet<>(recent(request.player,request.before,request.local,request.groups,request.cursor,request.count).stream().map(Message::id).toList());pages.add(new ReplayPage(request,page.messages.stream().filter(m->permitted.contains(m.id())).toList()));}
   delivery.send(List.copyOf(pages));return null;
  });
 }
 public List<Message> recent(String player,long before,boolean local,boolean groups)throws Exception{
  return recent(player,before,local,groups,Long.MAX_VALUE,limit);
 }
 public List<Message> recent(String player,long before,boolean local,boolean groups,long cursor,int pageSize)throws Exception{
  if(limit==0)return List.of();UUID.fromString(player);
  return db.communityTransaction(()->{
   var out=new ArrayList<Message>();
   String sql="SELECT h.id,h.at,h.body FROM chat_history h WHERE h.id<? AND h.at<? AND h.at>=? AND (h.channel='global' OR (h.channel='local' AND ? AND ?=ANY(h.recipients)) OR (? AND EXISTS(SELECT 1 FROM documents d JOIN community_relations r ON r.document=d.id AND r.kind='members' AND r.actor=? WHERE d.id=h.channel AND d.section='groups' AND d.body->>'status'='open'))) ORDER BY h.id DESC LIMIT ?";
   try(var q=db.connection().prepareStatement(sql)){q.setLong(1,cursor);q.setLong(2,before);q.setLong(3,System.currentTimeMillis()-days*86400000L);q.setBoolean(4,local);q.setString(5,player);q.setBoolean(6,groups);q.setString(7,player);q.setInt(8,Math.clamp(pageSize,1,limit));try(var r=q.executeQuery()){while(r.next())out.add(new Message(r.getLong(1),r.getLong(2),r.getString(3)));}}
   return List.copyOf(out);
  });
 }
}
