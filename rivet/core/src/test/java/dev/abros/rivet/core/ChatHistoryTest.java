package dev.abros.rivet.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres")
class ChatHistoryTest {
 @TempDir Path root;
 @Test void globalLocalAndGroupHistoryUseDifferentAudiencesAndSurviveStoreRestart()throws Exception{
  var db=TestDatabase.database(root);var history=new ChatHistory(db,100,7);String member=UUID.randomUUID().toString(),outside=UUID.randomUUID().toString(),group=UUID.randomUUID().toString();long now=System.currentTimeMillis();
  history.append("global",now,"global",List.of());history.append("local",now,"nearby",List.of(member));history.append(group,now,"group",List.of());
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"status\":\"open\"}')")){q.setString(1,group);q.executeUpdate();}try(var q=db.connection().prepareStatement("INSERT INTO community_relations(document,kind,actor,value) VALUES(?,'members',?,'\"member\"')")){q.setString(1,group);q.setString(2,member);q.executeUpdate();}return null;});
  assertEquals(List.of("group","nearby","global"),new ChatHistory(db,100,7).recent(member,now+1,true,true).stream().map(ChatHistory.Message::body).toList());
  assertEquals(List.of("global"),history.recent(outside,now+1,true,true).stream().map(ChatHistory.Message::body).toList());
  assertEquals(List.of("global"),history.recent(member,now+1,false,false).stream().map(ChatHistory.Message::body).toList());
  db.transaction(()->{try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=?")){q.setString(1,group);q.executeUpdate();}return null;});
  assertEquals(List.of("nearby","global"),history.recent(member,now+1,true,true).stream().map(ChatHistory.Message::body).toList());
 }
 @Test void limitsExpiryAndPagedReplayExcludeLiveMessages()throws Exception{
  var db=TestDatabase.database(root);var history=new ChatHistory(db,2,1);String player=UUID.randomUUID().toString();long now=System.currentTimeMillis();
  history.append("global",now-86400001L,"expired",List.of());history.append("global",now-3,"a",List.of());history.append("global",now-2,"b",List.of());history.append("global",now,"live",List.of());
  var first=history.recent(player,now,true,true,Long.MAX_VALUE,1);assertEquals("b",first.getFirst().body());
  assertTrue(history.recent(player,now,true,true,first.getFirst().id(),1).isEmpty());
  assertTrue(new ChatHistory(db,0,1).recent(player,now+1,true,true).isEmpty());
 }
 @Test void maintenanceDeletesExpiredRowsWithoutNewMessagesEvenWhenDisabled()throws Exception{
  var db=TestDatabase.database(root);long now=System.currentTimeMillis();
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO chat_history(channel,at,body) SELECT 'global',?, 'old' FROM generate_series(1,300)")){q.setLong(1,now-8L*86400000);q.executeUpdate();}return null;});
  var disabled=new ChatHistory(db,0,7);assertEquals(256,disabled.cleanup(now));assertEquals(44,disabled.cleanup(now));assertEquals(0,disabled.cleanup(now));
 }
 @Test void batchedReplayKeepsEachAudienceSeparateAndLocksBothGroups()throws Exception{
  var db=TestDatabase.database(root);var history=new ChatHistory(db,100,7);long now=System.currentTimeMillis();
  String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString(),outside=UUID.randomUUID().toString(),ga=UUID.randomUUID().toString(),gb=UUID.randomUUID().toString();
  db.transaction(()->{for(var member:Map.of(ga,a,gb,b).entrySet()){
   try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"status\":\"open\"}')")){q.setString(1,member.getKey());q.executeUpdate();}
   try(var q=db.connection().prepareStatement("INSERT INTO community_relations(document,kind,actor,value) VALUES(?,'members',?,'\"member\"')")){q.setString(1,member.getKey());q.setString(2,member.getValue());q.executeUpdate();}
  }return null;});
  history.append("global",now,"public",List.of());history.append(ga,now,"group-a",List.of());history.append(gb,now,"group-b",List.of());
  var requests=List.of(a,b,outside).stream().map(id->new ChatHistory.ReplayRequest(id,now+1,true,true,Long.MAX_VALUE,4)).toList();
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var replay=pool.submit(()->{history.replayBatch(requests,pages->{assertEquals(List.of("group-a","public"),pages.get(0).messages().stream().map(ChatHistory.Message::body).toList());assertEquals(List.of("group-b","public"),pages.get(1).messages().stream().map(ChatHistory.Message::body).toList());assertEquals(List.of("public"),pages.get(2).messages().stream().map(ChatHistory.Message::body).toList());entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));});return null;});
   try{
    assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
    var revoke=pool.submit(()->db.transaction(()->{started.countDown();try(var q=db.connection().prepareStatement("UPDATE documents SET body=body WHERE id=?")){q.setString(1,gb);q.executeUpdate();}try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=?")){q.setString(1,gb);q.executeUpdate();}return null;}));
    try{assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));assertThrows(java.util.concurrent.TimeoutException.class,()->revoke.get(150,java.util.concurrent.TimeUnit.MILLISECONDS));}finally{release.countDown();}
    replay.get(5,java.util.concurrent.TimeUnit.SECONDS);revoke.get(5,java.util.concurrent.TimeUnit.SECONDS);
   }finally{release.countDown();}
  }
  history.replayBatch(requests,pages->assertEquals(List.of("public"),pages.get(1).messages().stream().map(ChatHistory.Message::body).toList()));
  assertThrows(IllegalArgumentException.class,()->history.replayBatch(java.util.Collections.nCopies(17,requests.getFirst()),pages->fail("Oversized replay batch")));
 }

 @Test void groupRevocationCannotCommitBetweenAuthorizationAndDelivery()throws Exception{
  var db=TestDatabase.database(root);String member=UUID.randomUUID().toString(),group=UUID.randomUUID().toString();long now=System.currentTimeMillis();
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"status\":\"open\"}')")){q.setString(1,group);q.executeUpdate();}try(var q=db.connection().prepareStatement("INSERT INTO community_relations(document,kind,actor,value) VALUES(?,'members',?,'\"member\"')")){q.setString(1,group);q.setString(2,member);q.executeUpdate();}return null;});
  var history=new ChatHistory(db,100,7);history.append(group,now,"private",List.of());
  var authorized=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var replay=pool.submit(()->{history.replay(member,now+1,true,true,Long.MAX_VALUE,4,rows->{assertEquals(1,rows.size());authorized.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));});return null;});
   assertTrue(authorized.await(5,java.util.concurrent.TimeUnit.SECONDS));
   var revoke=pool.submit(()->db.transaction(()->{started.countDown();try(var q=db.connection().prepareStatement("UPDATE documents SET body=body WHERE id=?")){q.setString(1,group);q.executeUpdate();}try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=?")){q.setString(1,group);q.executeUpdate();}return null;}));
   try{assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));assertThrows(java.util.concurrent.TimeoutException.class,()->revoke.get(150,java.util.concurrent.TimeUnit.MILLISECONDS));}finally{release.countDown();}
   replay.get(5,java.util.concurrent.TimeUnit.SECONDS);revoke.get(5,java.util.concurrent.TimeUnit.SECONDS);
   history.replay(member,now+1,true,true,Long.MAX_VALUE,4,rows->assertTrue(rows.isEmpty()));
  }
 }
}
