package dev.abros.rivet.core;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 100 synthetic logins, real SQL, one history worker and a modelled 50 ms game tick. */
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_CHAT_LOAD",matches="true")
class ChatHistoryFanoutLoadTest {
 @TempDir Path root;
 @Test void firstReplayPageForOneHundredRecipients()throws Exception{
  var db=TestDatabase.database(root);var history=new ChatHistory(db,100,7);long at=System.currentTimeMillis()-1000;
  var players=new ArrayList<String>();for(int i=0;i<100;i++)players.add(new UUID(0,i+1).toString());
  String group=new UUID(1,1).toString();
  db.transaction(()->{
   try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"status\":\"open\"}')")){q.setString(1,group);q.executeUpdate();}
   try(var q=db.connection().prepareStatement("INSERT INTO community_relations(document,kind,actor,value) VALUES(?,'members',?,'\"member\"')")){for(String player:players){q.setString(1,group);q.setString(2,player);q.addBatch();}q.executeBatch();}return null;
  });
  for(String channel:List.of("global","local",group))for(int i=0;i<4;i++)history.append(channel,at,"{\"text\":\"Representative history message "+i+"\"}",channel.equals("local")?players:List.of());
  var results=new JsonArray();
  for(int batchSize:new int[]{1,16})for(String channel:List.of("global","local","groups")){
   var elapsed=new ArrayList<Long>();long[] bytes={0};long start=System.nanoTime();int[] delivered={0};
   try(var game=Executors.newSingleThreadScheduledExecutor()){
    for(int offset=0;offset<players.size();offset+=batchSize){
     var requests=new ArrayList<ChatHistory.ReplayRequest>();for(String player:players.subList(offset,Math.min(players.size(),offset+batchSize)))requests.add(new ChatHistory.ReplayRequest(player,System.currentTimeMillis(),!channel.equals("global"),channel.equals("groups"),Long.MAX_VALUE,4));
     history.replayBatch(requests,pages->{
      var packets=pages.stream().map(page->ChatHistoryReplay.packets(page.messages(),id->fail("Invalid stored chat message")).stream().map(Json.GSON::toJson).toList()).toList();
      long since=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
      game.schedule(()->{for(int i=0;i<pages.size();i++){assertEquals(4,pages.get(i).messages().size());assertEquals(4,packets.get(i).size());delivered[0]++;elapsed.add(System.nanoTime()-start);bytes[0]+=packets.get(i).stream().mapToLong(text->text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).sum();}},50-since%50,TimeUnit.MILLISECONDS).get(2,TimeUnit.SECONDS);
     });
    }
   }
   assertEquals(100,delivered[0]);Collections.sort(elapsed);var row=new JsonObject();row.addProperty("batchSize",batchSize);row.addProperty("channel",channel);row.addProperty("recipients",100);row.addProperty("messages",400);row.addProperty("p95Millis",elapsed.get(94)/1e6);row.addProperty("totalMillis",elapsed.getLast()/1e6);row.addProperty("bytes",bytes[0]);results.add(row);System.out.println("RIVET_CHAT_FANOUT_SAMPLE "+row);
  }
  var output=new JsonObject();output.addProperty("syntheticRecipients",true);output.addProperty("modelledGameTickMillis",50);output.add("results",results);Files.writeString(Path.of(System.getenv("RIVET_CHAT_LOAD_OUTPUT")),Json.GSON.toJson(output));System.out.println("RIVET_CHAT_FANOUT_OK recipients=100 scenarios=6");
 }
}
