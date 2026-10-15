package dev.abros.rivet.core.api;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.*;
import dev.abros.rivet.api.ApiException;
import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class DomainRestTest {
 @TempDir Path root;
 PgDatabase db;ApiKeys keys;AuthStore auth;CommunityStore store;PlayerSessions sessions;RestApi rest;
 String token;ApiKeys.Grant grant;final String player=UUID.randomUUID().toString();
 @BeforeEach void setup()throws Exception{
  db=TestDatabase.database(root);keys=new ApiKeys(db,new ApiKeys.Limits(100,8,1000,30,30,1000),Clock.systemUTC());auth=new AuthStore(db);
  store=new CommunityStore(db,CommunityStore.defaults());
  var key=keys.create(root,"website",ApiKeys.SCOPES,1,1000);token=Files.readString(key.deliveryFile()).trim();grant=keys.authorize(token,"events.read");
  configure(false);
 }
 void configure(boolean enabled)throws Exception{if(sessions!=null)sessions.close();sessions=new PlayerSessions(keys,auth,enabled,Clock.systemUTC(),24,100);rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,ServerSettings.parse(ServerSettings.template()))).commands(new CommunityApiCommands(db,store,ServerSettings.parse(ServerSettings.template()))));}
 @AfterEach void close(){sessions.close();keys.close();}
 RestApi.Response get(String path,String session){var h=new HashMap<String,List<String>>();h.put("Authorization",List.of("Bearer "+token));if(session!=null)h.put("X-Rivet-Session",List.of(session));return rest.handle(new RestApi.Request("GET","/api/v1/"+path,h));}
 RestApi.Response post(String path,String body){return rest.handle(new RestApi.Request("POST","/api/v1/sessions/"+path,Map.of("Authorization",List.of("Bearer "+token)),body));}
 JsonObject json(RestApi.Response response){assertEquals(200,response.status(),response.body());return JsonParser.parseString(response.body()).getAsJsonObject();}
 @Test void revocationDuringStorageAuthorizationCannotReturnAValidSession()throws Exception{
  class InterleavingClock extends Clock {
   Runnable duringRead;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}
   public Instant instant(){return Instant.ofEpochMilli(millis());}
   public long millis(){var action=duringRead;duringRead=null;if(action!=null)action.run();return System.currentTimeMillis();}
  }
  var clock=new InterleavingClock();keys.close();keys=new ApiKeys(db,new ApiKeys.Limits(100,8,1000,30,30,1000),clock);grant=keys.authorize(token,"events.read");
  for(String operation:List.of("token","player","stop","expire")){
   var sessionClock=new ApiKeysTest.Time();sessionClock.now=System.currentTimeMillis();sessions.close();sessions=new PlayerSessions(keys,auth,false,sessionClock,1,100);
   var challenge=sessions.challenge(grant);sessions.confirm(challenge.code(),"website",player,"Alice");var issued=sessions.exchange(grant,challenge.code(),challenge.pollToken());
   clock.duringRead=()->{switch(operation){case "token"->sessions.revoke(grant,issued.token());case "player"->sessions.revokePlayer(player);case "stop"->sessions.close();case "expire"->sessionClock.now+=3600001;}};
   var failure=assertThrows(ApiException.class,()->sessions.validate(grant,issued.token()),operation);
   assertEquals(operation.equals("stop")?ApiException.Code.UNAVAILABLE:ApiException.Code.UNAUTHENTICATED,failure.code());
  }
 }
 @Test void passwordIdentityGenerationAndApplicationBinding()throws Exception{
  auth.register("Alice",player,"secret-password".toCharArray(),System.currentTimeMillis());configure(true);
  assertEquals(401,post("password","{\"login\":\"Nobody\",\"password\":\"wrong\"}").status());
  var result=json(post("password","{\"login\":\"Alice\",\"password\":\"secret-password\"}"));String session=result.get("session").getAsString();assertEquals(player,sessions.validate(grant,session).uuid());
  var other=keys.create(root,"other",Set.of("sessions.password","events.read"),1,100);var otherGrant=keys.authorize(Files.readString(other.deliveryFile()).trim(),"events.read");assertThrows(ApiException.class,()->sessions.validate(otherGrant,session));
  auth.change("Alice",player,"secret-password".toCharArray(),"new-password".toCharArray(),System.currentTimeMillis());assertThrows(ApiException.class,()->sessions.validate(grant,session));
  assertEquals(403,post("link","{}").status());assertFalse(result.toString().contains("secret-password"));
 }
 @Test void linkingNeedsConfirmationAndSingleUsePollSecret()throws Exception{
  assertEquals(403,post("password","{\"login\":\"Alice\",\"password\":\"x\"}").status());
  var c=sessions.challenge(grant);assertNull(sessions.exchange(grant,c.code(),c.pollToken()));
  assertThrows(ApiException.class,()->sessions.confirm(c.code(),"evil",player,"Alice"));
  sessions.confirm(c.code(),"website",player,"Alice");assertThrows(ApiException.class,()->sessions.exchange(grant,c.code(),"wrong"));
  var issued=sessions.exchange(grant,c.code(),c.pollToken());assertEquals(player,sessions.validate(grant,issued.token()).uuid());
  assertThrows(ApiException.class,()->sessions.exchange(grant,c.code(),c.pollToken()));sessions.revokePlayer(player);assertThrows(ApiException.class,()->sessions.validate(grant,issued.token()));
 }
 String event(String visibility)throws Exception{
  String id=UUID.randomUUID().toString();var body=new JsonObject();body.addProperty("id",id);body.addProperty("section","events");body.addProperty("owner",player);body.addProperty("title","Event");body.addProperty("description","Details");body.addProperty("status","open");body.addProperty("visibility",visibility);body.addProperty("startsAt",System.currentTimeMillis()+3600000);body.addProperty("createdAt",System.currentTimeMillis());body.addProperty("capacity",10);body.addProperty("revision",1);body.add("eventInvites",new JsonArray());
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'events',?::jsonb)")){q.setString(1,id);q.setString(2,body.toString());q.executeUpdate();}return null;});return id;
 }
 @Test void domainsDoNotImpersonatePlayersOrExposeHiddenFields()throws Exception{
  String privateId=event("invited"),publicId=event("public");
  assertEquals(404,get("events/"+privateId,null).status());assertEquals(1,json(get("events",null)).getAsJsonArray("items").size());
  var publicItem=json(get("events/"+publicId,null)).getAsJsonObject("item");assertFalse(publicItem.has("owner"));assertFalse(publicItem.has("eventInvites"));
  var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",player,"Alice");var s=sessions.exchange(grant,c.code(),c.pollToken());assertEquals(200,get("events/"+privateId,s.token()).status());
  assertEquals(400,get("events?uuid="+player,null).status());
  keys.state(grant.keyId(),"revoke");assertEquals(401,get("events/"+privateId,s.token()).status());
 }
 @Test void insufficientScopeMalformedJsonAndDisabledModuleFailClosed()throws Exception{
  var key=keys.create(root,"limited",Set.of("server.status.read"),1,100);String prior=token;token=Files.readString(key.deliveryFile()).trim();assertEquals(403,get("groups",null).status());assertEquals(403,post("password","{}").status());token=prior;
  assertEquals(400,post("link","{\"x\":\"a\",\"x\":\"b\"}").status());assertEquals(400,post("link","{} trailing").status());
  var cfg=ServerSettings.parse(ServerSettings.template().replace("[events]\n# События, участники и напоминания.\nenabled = true","[events]\n# События, участники и напоминания.\nenabled = false"));assertFalse(cfg.modules().enabled("events"));
  var disabled=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),cfg.modules());
  rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),new DomainRest(keys,sessions,disabled));assertEquals(503,get("events",null).status());
 }
 @Test void groupMembershipIsCheckedAgainOnEveryRead()throws Exception{
  String group=UUID.randomUUID().toString(),event=event("group");String owner=UUID.randomUUID().toString();
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups',jsonb_build_object('id',?::text,'section','groups','status','open'))")){q.setString(1,group);q.setString(2,group);q.executeUpdate();}
   try(var q=db.connection().prepareStatement("UPDATE documents SET body=body||jsonb_build_object('group',?::text,'owner',?::text) WHERE id=?")){q.setString(1,group);q.setString(2,owner);q.setString(3,event);q.executeUpdate();}
   try(var q=db.connection().prepareStatement("INSERT INTO community_relations VALUES(?,'members',?,'\"member\"'::jsonb)")){q.setString(1,group);q.setString(2,player);q.executeUpdate();}return null;});
  var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",player,"Alice");var issued=sessions.exchange(grant,c.code(),c.pollToken());assertEquals(200,get("events/"+event,issued.token()).status());
  db.transaction(()->{try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=? AND actor=?")){q.setString(1,group);q.setString(2,player);q.executeUpdate();}return null;});
  assertEquals(404,get("events/"+event,issued.token()).status());
 }
 @Test void expiredChallengesSessionsAndRestartHandlesAreRejected()throws Exception{
  var clock=new ApiKeysTest.Time();clock.now=System.currentTimeMillis();sessions.close();sessions=new PlayerSessions(keys,auth,false,clock,1,100);
  var expired=sessions.challenge(grant);clock.now+=300001;assertThrows(ApiException.class,()->sessions.confirm(expired.code(),"website",player,"Alice"));
  var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",player,"Alice");var issued=sessions.exchange(grant,c.code(),c.pollToken());clock.now+=3600001;assertThrows(ApiException.class,()->sessions.validate(grant,issued.token()));
  sessions.close();assertThrows(ApiException.class,()->sessions.challenge(grant));
 }

 String linked()throws Exception{store.seen(new CommunityStore.Actor(player,"Alice",false,false));var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",player,"Alice");return sessions.exchange(grant,c.code(),c.pollToken()).token();}
 @Test void personalTasksNeverAllowAnotherOwnerOrBareKey()throws Exception{
  String own=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
  db.transaction(()->{for(String id:List.of(own,other))try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body,revision,owner) VALUES(?,NULL,'task','{\"title\":\"Work\",\"status\":\"open\",\"description\":\"Details\",\"comments\":[\"secret\"]}'::jsonb,1,?)")){q.setString(1,id);q.setString(2,id.equals(own)?player:UUID.randomUUID().toString());q.executeUpdate();}return null;});
  String session=linked();assertEquals(401,get("tasks",null).status());var list=json(get("tasks",session));assertEquals(1,list.getAsJsonArray("items").size());assertFalse(list.toString().contains("secret"));assertEquals(200,get("tasks/"+own,session).status());assertEquals(404,get("tasks/"+other,session).status());assertEquals(400,get("tasks?owner="+player,session).status());
  db.transaction(()->{try(var q=db.connection().createStatement();var r=q.executeQuery("SELECT count(*) FROM community_task_codes")){r.next();assertEquals(0,r.getInt(1));}return null;});
 }
 @Test void privateProfilesAndNotificationsUseTheSessionOwner()throws Exception{
  String other=UUID.randomUUID().toString();store.seen(new CommunityStore.Actor(other,"Bob",false,false));
  db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES('profile-privacy',?,'{\"mode\":\"private\"}'::jsonb)")){q.setString(1,other);q.executeUpdate();}return null;});
  store.externalNotice(player,"Mine");store.externalNotice(other,"Other secret");String session=linked();
  assertEquals(404,get("profiles/"+other,session).status());assertEquals(401,get("profiles/"+other,null).status());var notices=json(get("me/notifications",session));assertEquals(1,notices.getAsJsonArray("items").size());assertFalse(notices.toString().contains("Other secret"));assertFalse(notices.getAsJsonArray("items").get(0).getAsJsonObject().get("read").getAsBoolean());
  assertEquals(400,get("me/notifications?player="+other,session).status());
 }
 @Test void mapReturnsPublishedSnapshotWithoutReviewDraft()throws Exception{
  String id=UUID.randomUUID().toString();db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES('map-object',?,'{\"type\":\"marker\",\"revision\":1,\"owner\":\"hidden-owner\",\"draft\":{\"name\":\"private-draft\"},\"published\":{\"name\":\"Approved\"}}'::jsonb)")){q.setString(1,id);q.executeUpdate();}return null;});
  var response=json(get("map/objects/"+id,null));assertTrue(response.toString().contains("Approved"));assertFalse(response.toString().contains("private-draft"));assertFalse(response.toString().contains("hidden-owner"));assertEquals(400,get("map/objects?manage=true",null).status());
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE records SET body=body-'published' WHERE namespace='map-object' AND id=?")){q.setString(1,id);q.executeUpdate();}return null;});assertEquals(404,get("map/objects/"+id,null).status());
 }
 @Test void chatOnlyReturnsHistoricRecipientsAndStripsActions()throws Exception{
  var history=new ChatHistory(db,100,7);long now=System.currentTimeMillis()-1000;
  history.append("global",now,"{\"text\":\"Hello\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"hidden-command\"}}",List.of());
  history.append("local",now,"{\"text\":\"Near\"}",List.of(player));history.append("local",now,"{\"text\":\"Other secret\"}",List.of(UUID.randomUUID().toString()));
  String session=linked();assertEquals(401,get("chat/history",null).status());var response=json(get("chat/history",session));assertEquals(2,response.getAsJsonArray("items").size());assertFalse(response.toString().contains("Other secret"));assertFalse(response.toString().contains("hidden-command"));assertEquals(400,get("chat/history?channel=admin",session).status());
 }

 @Test void groupChatAndChannelsDisappearAfterLeaving()throws Exception{
  String group=UUID.randomUUID().toString();db.transaction(()->{try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"section\":\"groups\",\"status\":\"open\",\"title\":\"Team\"}'::jsonb||jsonb_build_object('id',?::text))")){q.setString(1,group);q.setString(2,group);q.executeUpdate();}try(var q=db.connection().prepareStatement("INSERT INTO community_relations VALUES(?,'members',?,'\"member\"')")){q.setString(1,group);q.setString(2,player);q.executeUpdate();}return null;});
  new ChatHistory(db,100,7).append(group,System.currentTimeMillis()-1000,"{\"text\":\"Team history\"}",List.of());String session=linked();assertTrue(json(get("chat/history",session)).toString().contains("Team history"));assertTrue(json(get("chat/channels",session)).toString().contains(group));var push=subscriptions();var watched=new DomainSubscriptions.Target("chat/history",Map.of(),session);var prior=push.snapshot(grant,watched,null);json(write("POST","chat/messages",session,null,command("{\"channel\":\""+group+"\",\"text\":\"Queued before leaving\"}")));
  db.transaction(()->{try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=?")){q.setString(1,group);q.executeUpdate();}return null;});assertFalse(json(get("chat/history",session)).toString().contains("Team history"));assertFalse(json(get("chat/channels",session)).toString().contains(group));assertFalse(push.snapshot(grant,watched,prior.getAsJsonObject("cursor")).toString().contains("Team history"));new ApiChat(db,store,ServerSettings.parse(ServerSettings.template()),who->List.of()).drain((channel,sender,text,audience)->fail("Former member message delivered"));
 }
 @Test void newResourcesRespectModuleSwitchesAndMissingScopes()throws Exception{
  String session=linked();var text=ServerSettings.template();for(String name:List.of("chat","map")){int start=text.indexOf("["+name+"]");text=text.substring(0,start)+text.substring(start).replaceFirst("enabled = true","enabled = false");}var cfg=ServerSettings.parse(text);assertFalse(cfg.flag("chat.enabled"));assertFalse(cfg.flag("map.enabled"));
  rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg)));assertEquals(503,get("chat/history",session).status());assertEquals(503,get("map/objects",null).status());
  var limited=keys.create(root,"limited",Set.of("server.status.read"),1,100);token=Files.readString(limited.deliveryFile()).trim();for(String route:List.of("tasks","profiles/"+player,"me/notifications","chat/history","map/objects"))assertEquals(403,get(route,session).status(),route);
 }

 @Test void groupTaskReadRevokesWhenMembershipIsRemoved()throws Exception{
  String group=UUID.randomUUID().toString(),task=UUID.randomUUID().toString();db.transaction(()->{
   try(var q=db.connection().prepareStatement("INSERT INTO documents(id,section,body) VALUES(?,'groups','{\"status\":\"open\"}')")){q.setString(1,group);q.executeUpdate();}
   try(var q=db.connection().prepareStatement("INSERT INTO community_relations VALUES(?,'members',?,'\"member\"')")){q.setString(1,group);q.setString(2,player);q.executeUpdate();}
   try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body,revision,owner) VALUES(?,?,'task','{\"title\":\"Group work\",\"status\":\"open\"}',1,?)")){q.setString(1,task);q.setString(2,group);q.setString(3,UUID.randomUUID().toString());q.executeUpdate();}return null;});
  String session=linked();assertEquals(1,json(get("tasks?group="+group,session)).getAsJsonArray("items").size());assertEquals(200,get("tasks/"+task,session).status());
  db.transaction(()->{try(var q=db.connection().prepareStatement("DELETE FROM community_relations WHERE document=?")){q.setString(1,group);q.executeUpdate();}return null;});assertEquals(404,get("tasks?group="+group,session).status());assertEquals(404,get("tasks/"+task,session).status());
 }


 JsonObject command(String data){var out=new JsonObject();out.addProperty("operationId",UUID.randomUUID().toString());out.addProperty("issuedAt",System.currentTimeMillis());out.add("data",JsonParser.parseString(data));return out;}
 RestApi.Response write(String method,String path,String session,String revision,JsonObject body){var headers=new HashMap<String,List<String>>();headers.put("Authorization",List.of("Bearer "+token));if(session!=null)headers.put("X-Rivet-Session",List.of(session));if(revision!=null)headers.put("If-Match",List.of("\""+revision+"\""));return rest.handle(new RestApi.Request(method,"/api/v1/"+path,headers,body.toString()));}
 @Test void commandsAreAtomicIdempotentAndRequireCurrentRevision()throws Exception{
  String session=linked();var body=command("{\"title\":\"First\",\"description\":\"Details\",\"group\":\"\"}");
  assertEquals(401,write("POST","tasks",null,null,body).status());var created=json(write("POST","tasks",session,null,body));String id=created.get("id").getAsString(),revision=created.get("revision").getAsString();
  var replay=json(write("POST","tasks",session,null,body));assertTrue(replay.get("replayed").getAsBoolean());assertEquals(id,replay.get("id").getAsString());assertEquals(1,json(get("tasks",session)).getAsJsonArray("items").size());
  body.getAsJsonObject("data").addProperty("title","Changed retry");assertEquals(400,write("POST","tasks",session,null,body).status());
  var edit=command("{\"title\":\"Edited\",\"description\":\"Updated\"}");assertEquals(400,write("PUT","tasks/"+id,session,null,edit).status());json(write("PUT","tasks/"+id,session,revision,edit));
  assertEquals(409,write("PUT","tasks/"+id,session,revision,command("{\"title\":\"Lost update\",\"description\":\"\"}")).status());
  assertEquals("Edited",json(get("tasks/"+id,session)).getAsJsonObject("item").get("title").getAsString());
 }
 @Test void commandsCannotInjectPermissionsOrWriteOtherPlayersTasks()throws Exception{
  String session=linked();var create=command("{\"title\":\"First\",\"description\":\"\",\"group\":\"\"}");create.getAsJsonObject("data").addProperty("admin",true);assertEquals(400,write("POST","tasks",session,null,create).status());
  create.getAsJsonObject("data").remove("admin");var task=json(write("POST","tasks",session,null,create));String other=UUID.randomUUID().toString();var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",other,"Other");String otherSession=sessions.exchange(grant,c.code(),c.pollToken()).token();
  assertEquals(403,write("POST","tasks/"+task.get("id").getAsString()+"/delete",otherSession,task.get("revision").getAsString(),command("{}")).status());
  var limited=keys.create(root,"read-only",Set.of("sessions.link","tasks.read"),1,100);token=Files.readString(limited.deliveryFile()).trim();assertEquals(403,write("POST","tasks",session,null,command("{} ")).status());
 }
 @Test void profileCompareAndSetAndNoticeOwnership()throws Exception{
  store.seen(new CommunityStore.Actor(player,"Alice",false,false));String session=linked();String revision=json(get("profiles/"+player,session)).getAsJsonObject("item").get("revision").getAsString();
  json(write("PUT","me/profile",session,revision,command("{\"about\":\"Hello\",\"interests\":\"Maps\"}")));
  assertEquals(409,write("PUT","me/profile",session,revision,command("{\"about\":\"Old\",\"interests\":\"\"}")).status());
  store.externalNotice(player,"Mine");String id=json(get("me/notifications",session)).getAsJsonArray("items").get(0).getAsJsonObject().get("id").getAsString();
  json(write("POST","me/notifications/"+id+"/read",session,null,command("{}")));assertTrue(json(get("me/notifications",session)).getAsJsonArray("items").get(0).getAsJsonObject().get("read").getAsBoolean());
 }
 @Test void apiMapDraftNeedsApprovalAndCanBeWithdrawn()throws Exception{
  String session=linked(),id=UUID.randomUUID().toString();var body=command("{\"type\":\"marker\",\"draft\":{\"name\":\"Place\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"color\":16711680,\"icon\":\"flag\"}}");
  var saved=json(write("PUT","map/objects/"+id+"/draft",session,"0",body));assertEquals(404,get("map/objects/"+id,null).status());assertEquals(401,get("me/map/objects",null).status());assertTrue(json(get("me/map/objects/"+id,session)).getAsJsonObject("item").has("draft"));
  assertEquals(400,write("POST","map/objects/"+id+"/approve",session,saved.get("revision").getAsString(),command("{}")).status());
  json(write("POST","map/objects/"+id+"/withdraw",session,saved.get("revision").getAsString(),command("{}")));
 }
 @Test void websocketStatusReplayAndSameCommandReceipt()throws Exception{
  var feed=RestApiTest.feed();int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
  try(var server=new ApiWebSocketServer(new WebSocketSettings(true,"127.0.0.1",port,2,600),rest,feed,keys)){
   server.start();var listener=new WsClient();var ws=java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(java.net.URI.create("ws://127.0.0.1:"+port+"/api/v1/ws"),listener).get(5,java.util.concurrent.TimeUnit.SECONDS);
   ws.sendText("{\"type\":\"subscribe\",\"topic\":\"server.status\",\"cursor\":null}",true).join();var snapshot=listener.next();assertEquals("snapshot",snapshot.get("type").getAsString());
   feed.publish(new dev.abros.rivet.api.ServerStatus(2,100,false,0,Set.of("server.status")));assertEquals("event",listener.next().get("type").getAsString());
   ws.abort();listener=new WsClient();ws=java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(java.net.URI.create("ws://127.0.0.1:"+port+"/api/v1/ws"),listener).get(5,java.util.concurrent.TimeUnit.SECONDS);
   var resume=new JsonObject();resume.addProperty("type","subscribe");resume.addProperty("topic","server.status");resume.add("cursor",snapshot.getAsJsonObject("data").get("cursor"));ws.sendText(resume.toString(),true).join();assertEquals("event",listener.next().get("type").getAsString());
   String session=linked();var body=command("{\"title\":\"From WS\",\"description\":\"\",\"group\":\"\"}");var request=new JsonObject();request.addProperty("type","request");request.addProperty("id","create1");request.addProperty("method","POST");request.addProperty("path","/api/v1/tasks");request.addProperty("session",session);request.add("ifMatch",JsonNull.INSTANCE);request.add("body",body);ws.sendText(request.toString(),true).join();var response=listener.next().getAsJsonObject("data");assertEquals(200,response.get("status").getAsInt(),response.toString());
   assertTrue(json(write("POST","tasks",session,null,body)).get("replayed").getAsBoolean());keys.state(grant.keyId(),"revoke");assertTrue(listener.closed.await(5,java.util.concurrent.TimeUnit.SECONDS));ws.abort();
  }
 }
 @Test void simultaneousRetriesCommitOneTask()throws Exception{
  String session=linked();var body=command("{\"title\":\"Concurrent\",\"description\":\"\",\"group\":\"\"}");
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->write("POST","tasks",session,null,body));var b=pool.submit(()->write("POST","tasks",session,null,body));
   assertEquals(json(a.get()).get("id"),json(b.get()).get("id"));assertEquals(1,json(get("tasks",session)).getAsJsonArray("items").size());
  }
 }
 @Test void communityActionsReuseGamePoliciesAndRejectStaleRevisions()throws Exception{
  String session=linked();var owner=new CommunityStore.Actor(UUID.randomUUID().toString(),"Organizer",true,true);store.seen(owner);
  for(String section:List.of("board","groups","events","polls","ideas")){
   var create=new JsonObject();create.addProperty("section",section);create.addProperty("op","create");create.addProperty("title","Example");create.addProperty("description","Details");create.addProperty("days",14);create.addProperty("type","Команда");create.addProperty("startsAt",Instant.now().plusSeconds(600).toString());create.addProperty("endsAt",Instant.now().plusSeconds(600).toString());create.addProperty("capacity",10);create.add("options",JsonParser.parseString("[\"One\",\"Two\"]"));
   var item=store.request(owner,create).getAsJsonObject("detail");String id=item.get("id").getAsString(),revision=item.get("revision").getAsString();
   String action=switch(section){case "board"->"respond";case "groups"->"apply";case "events"->"join";case "polls"->"vote";default->"support";};
   var body=command(switch(section){case "board","groups"->"{\"text\":\"Interested\"}";case "polls"->"{\"choices\":[0]}";default->"{}";});
   String path=section+"/"+id+"/actions/"+action;json(write("POST",path,session,revision,body));assertTrue(json(write("POST",path,session,revision,body)).get("replayed").getAsBoolean());
   var newer=body.deepCopy();newer.addProperty("operationId",UUID.randomUUID().toString());assertEquals(409,write("POST",path,session,revision,newer).status());
  }
 }
 @Test void malformedAndExpiredCommandsHaveNoSideEffects()throws Exception{
  String session=linked();var body=command("{\"title\":\"Expired\",\"description\":\"\",\"group\":\"\"}");body.addProperty("issuedAt",System.currentTimeMillis()-86400001);assertEquals(400,write("POST","tasks",session,null,body).status());
  body.addProperty("issuedAt",System.currentTimeMillis());body.add("data",JsonNull.INSTANCE);assertEquals(400,write("POST","tasks",session,null,body).status());assertTrue(json(get("tasks",session)).getAsJsonArray("items").isEmpty());
 }

 DomainSubscriptions subscriptions()throws Exception{return new DomainSubscriptions(new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,ServerSettings.parse(ServerSettings.template()))),db::communityRevision);}
 @Test void pushInvalidationOnlyFollowsCommittedTransactions()throws Exception{
  long before=db.communityRevision();
  assertThrows(IllegalStateException.class,()->db.transaction(()->{store.externalNotice(player,"Rolled back");throw new IllegalStateException("rollback");}));
  assertEquals(before,db.communityRevision());
  db.transaction(()->{store.externalNotice(player,"Committed");assertEquals(before,db.communityRevision());return null;});
  assertEquals(before+1,db.communityRevision());
 }
 @Test void subscriptionSnapshotsFollowGameWritesAndNeverReplayPrivateHistory()throws Exception{
  String session=linked();var subscriptions=subscriptions();var target=new DomainSubscriptions.Target("me/notifications",Map.of(),session);
  var first=subscriptions.snapshot(grant,target,null);assertTrue(first.getAsJsonObject("resource").getAsJsonArray("items").isEmpty());
  store.externalNotice(player,"New notice");store.externalNotice(UUID.randomUUID().toString(),"Other secret");
  var changed=subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor"));assertTrue(changed.toString().contains("New notice"));assertFalse(changed.toString().contains("Other secret"));assertNotEquals(first.get("cursor"),changed.get("cursor"));
  assertEquals(changed,subscriptions.snapshot(grant,target,changed.getAsJsonObject("cursor")));
  var chat=new DomainSubscriptions.Target("chat/history",Map.of(),session);var empty=subscriptions.snapshot(grant,chat,null);
  new ChatHistory(db,100,7).append("local",System.currentTimeMillis()-10,"{\"text\":\"Nearby\"}",List.of(player));
  assertTrue(subscriptions.snapshot(grant,chat,empty.getAsJsonObject("cursor")).toString().contains("Nearby"));
  sessions.revokePlayer(player);assertEquals(ApiException.Code.UNAUTHENTICATED,assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,changed.getAsJsonObject("cursor"))).code());
 }
 @Test void subscriptionCursorsBindResourceAndIdentityAndRequireResyncAfterRestart()throws Exception{
  String session=linked();var subscriptions=subscriptions();var target=new DomainSubscriptions.Target("tasks",Map.of(),session);var first=subscriptions.snapshot(grant,target,null);
  var other=new DomainSubscriptions.Target("me/notifications",Map.of(),session);
  assertEquals(ApiException.Code.RESYNC_REQUIRED,assertThrows(ApiException.class,()->subscriptions.snapshot(grant,other,first.getAsJsonObject("cursor"))).code());
  var future=first.getAsJsonObject("cursor").deepCopy();future.addProperty("sequence",9999);
  assertEquals(ApiException.Code.RESYNC_REQUIRED,assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,future)).code());
  subscriptions.clear();assertEquals(ApiException.Code.RESYNC_REQUIRED,assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor"))).code());
  assertThrows(ApiException.class,()->new DomainSubscriptions.Target("tasks",Map.of("cursor","anything"),session));
  assertThrows(ApiException.class,()->subscriptions.snapshot(grant,new DomainSubscriptions.Target("sessions/link",Map.of(),session),null));
 }
 @Test void subscriptionsCoverAllReadDomainsAndRespectScopeAndUnpublishing()throws Exception{
  String session=linked();var subscriptions=subscriptions();
  for(String path:List.of("tasks","profiles/"+player,"me/notifications","chat/history","chat/channels","map/objects","me/map/objects","map/territories","groups","events","board","polls","ideas"))assertNotNull(subscriptions.snapshot(grant,new DomainSubscriptions.Target(path,Map.of(),session),null),path);
  String id=event("public");var target=new DomainSubscriptions.Target("events/"+id,Map.of(),null);var first=subscriptions.snapshot(grant,target,null);
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=body||'{\"visibility\":\"invited\"}'::jsonb WHERE id=?")){q.setString(1,id);q.executeUpdate();}return null;});
  assertEquals(ApiException.Code.NOT_FOUND,assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor"))).code());
  var limited=keys.create(root,"limited",Set.of("websocket.connect"),1,100);var g=keys.authorize(Files.readString(limited.deliveryFile()).trim(),"websocket.connect");
  assertEquals(ApiException.Code.FORBIDDEN,assertThrows(ApiException.class,()->subscriptions.snapshot(g,new DomainSubscriptions.Target("events",Map.of(),null),null)).code());
 }
 @Test void websocketPushReconnectUnwatchAndSessionRevocation()throws Exception{
  String session=linked();int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
  try(var server=new ApiWebSocketServer(new WebSocketSettings(true,"127.0.0.1",port,2,600),rest,RestApiTest.feed(),keys,subscriptions())){
   server.start();var listener=new WsClient();var ws=java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(java.net.URI.create("ws://127.0.0.1:"+port+"/api/v1/ws"),listener).get(5,java.util.concurrent.TimeUnit.SECONDS);
   var watch=new JsonObject();watch.addProperty("type","watch");watch.addProperty("id","notices");watch.addProperty("path","me/notifications");watch.add("query",new JsonObject());watch.addProperty("session",session);watch.add("cursor",JsonNull.INSTANCE);
   ws.sendText(watch.toString(),true).join();var initial=listener.next();assertEquals("resource.snapshot",initial.get("type").getAsString());
   store.externalNotice(player,"Push from game");var pushed=listener.next();assertTrue(pushed.toString().contains("Push from game"));assertNotEquals(initial.getAsJsonObject("data").get("cursor"),pushed.getAsJsonObject("data").get("cursor"));
   ws.abort();listener=new WsClient();ws=java.net.http.HttpClient.newHttpClient().newWebSocketBuilder().header("Authorization","Bearer "+token).buildAsync(java.net.URI.create("ws://127.0.0.1:"+port+"/api/v1/ws"),listener).get(5,java.util.concurrent.TimeUnit.SECONDS);
   watch.add("cursor",initial.getAsJsonObject("data").get("cursor"));ws.sendText(watch.toString(),true).join();assertTrue(listener.next().toString().contains("Push from game"));
   ws.sendText("{\"type\":\"unwatch\",\"id\":\"notices\"}",true).join();assertEquals("unwatched",listener.next().get("type").getAsString());
   var history=new ChatHistory(db,100,7);for(int i=0;i<6;i++)history.append("global",System.currentTimeMillis()-1,"{\"text\":\""+"x".repeat(10000)+"\"}",List.of());
   var chat=watch.deepCopy();chat.addProperty("id","chat");chat.addProperty("path","chat/history");chat.add("cursor",JsonNull.INSTANCE);ws.sendText(chat.toString(),true).join();assertEquals("resource.invalidated",listener.next().get("type").getAsString());
   ws.sendText("{\"type\":\"unwatch\",\"id\":\"chat\"}",true).join();listener.next();
   ws.sendText(watch.toString(),true).join();listener.next();sessions.revokePlayer(player);var denied=listener.next();assertEquals("watch.closed",denied.get("type").getAsString());assertEquals("UNAUTHENTICATED",denied.getAsJsonObject("data").get("code").getAsString());ws.abort();
  }
 }

 @Test void javaDomainReadsAreTypedPrivateAndLifecycleBound()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());
  try(var java=new JavaDomains(db,store,cfg)){
   var publicView=java.connect("example_mod",null);var own=java.connect("example_mod",UUID.fromString(player));
   for(var d:dev.abros.rivet.api.DomainData.Community.values())assertNotNull(publicView.community(d,"").get());
   var eventId=UUID.fromString(event("public"));assertEquals("Event",publicView.document(dev.abros.rivet.api.DomainData.Community.EVENTS,eventId).get().title());
   assertTrue(own.capabilities().get().contains("tasks.write"));assertFalse(publicView.capabilities().get().contains("tasks.write"));
   store.externalNotice(player,"Mine");store.externalNotice(UUID.randomUUID().toString(),"Private other");
   var notices=own.notifications("").get();assertEquals(1,notices.items().size());assertThrows(UnsupportedOperationException.class,()->notices.items().clear());
   assertEquals(ApiException.Code.UNAUTHENTICATED,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->publicView.notifications("").get()).getCause()).code());
   assertEquals(player,own.profile(UUID.fromString(player)).get().uuid().toString());
   assertNotNull(own.chat("").get());assertNotNull(own.channels("").get());assertNotNull(publicView.publications(false,"",OptionalLong.empty()).get());assertNotNull(publicView.territories("").get());
   java.close();assertEquals(ApiException.Code.UNAVAILABLE,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->own.tasks(Optional.empty(),"").get()).getCause()).code());
  }
 }
 @Test void javaCommandsReuseOwnershipRevisionAndModScopedReceipts()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());
  try(var service=new JavaDomains(db,store,cfg)){
   var own=service.connect("example_mod",UUID.fromString(player));var id=UUID.randomUUID();long at=System.currentTimeMillis();var create=new dev.abros.rivet.api.DomainCommand.CreateTask("Java task","Details",Optional.empty());
   var first=own.execute(id,at,create).get();assertTrue(own.execute(id,at,create).get().replayed());
   var task=own.task(UUID.fromString(first.id())).get();assertEquals("Java task",task.title());
   var update=new dev.abros.rivet.api.DomainCommand.UpdateTask(task.id(),task.revision(),"Changed","New");own.execute(UUID.randomUUID(),at,update).get();
   assertEquals(ApiException.Code.CONFLICT,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->own.execute(UUID.randomUUID(),at,update).get()).getCause()).code());
   var other=UUID.randomUUID();store.seen(new CommunityStore.Actor(other.toString(),"Bob",false,false));
   assertEquals(ApiException.Code.NOT_FOUND,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->service.connect("example_mod",other).task(task.id()).get()).getCause()).code());
   var p=own.profile(UUID.fromString(player)).get();own.execute(UUID.randomUUID(),at,new dev.abros.rivet.api.DomainCommand.SaveProfile(p.revision(),"About","Interests")).get();assertEquals("About",own.profile(p.uuid()).get().about());
   store.externalNotice(player,"Read me");var notice=own.notifications("").get().items().getFirst();own.execute(UUID.randomUUID(),at,new dev.abros.rivet.api.DomainCommand.ReadNotice(notice.id())).get();assertTrue(own.notifications("").get().items().getFirst().read());
   var marker=new dev.abros.rivet.api.DomainData.Marker("minecraft:overworld","API marker",1,64,2,0xabcdef,"pin");var mapId=UUID.randomUUID();own.execute(UUID.randomUUID(),at,new dev.abros.rivet.api.DomainCommand.SubmitPublication(mapId,0,marker)).get();
   var draft=own.publication(mapId,true).get();assertEquals(marker,draft.draft().orElseThrow());assertTrue(draft.published().isEmpty());
   assertEquals(ApiException.Code.NOT_FOUND,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->service.connect("example_mod",null).publication(mapId,false).get()).getCause()).code());
  }
 }

 @Test void javaQueueRejectsOverloadAndCancelledQueuedWritesDoNotRun()throws Exception{
  linked();var gate=new java.util.concurrent.CountDownLatch(1);var started=new java.util.concurrent.CountDownLatch(1);
  var pool=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1));
  pool.execute(()->{started.countDown();try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));
  try(var service=new JavaDomains(db,store,ServerSettings.parse(ServerSettings.template()),pool)){
   var view=service.connect("example_mod",UUID.fromString(player));
   var queued=view.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.CreateTask("Cancelled","",Optional.empty()));
   assertEquals(ApiException.Code.BUSY,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->view.tasks(Optional.empty(),"").get()).getCause()).code());
   assertTrue(queued.cancel(false));gate.countDown();pool.shutdown();assertTrue(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));
   assertTrue(json(get("tasks",linked())).getAsJsonArray("items").isEmpty());
  }finally{gate.countDown();pool.shutdownNow();}
 }
 @Test void javaDomainModuleSwitchesAndInvalidInputsFailClosed()throws Exception{
  linked();String template=ServerSettings.template();int start=template.indexOf("[map]");var cfg=ServerSettings.parse(template.substring(0,start)+template.substring(start).replaceFirst("enabled = true","enabled = false"));
  try(var service=new JavaDomains(db,store,cfg)){
   var view=service.connect("example_mod",UUID.fromString(player));
   assertFalse(view.capabilities().get().contains("map.read"));assertEquals(ApiException.Code.MODULE_DISABLED,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->view.publications(false,"",OptionalLong.empty()).get()).getCause()).code());
   assertEquals(ApiException.Code.INVALID_ARGUMENT,((ApiException)assertThrows(java.util.concurrent.ExecutionException.class,()->view.task(null).get()).getCause()).code());
   assertThrows(ApiException.class,()->service.connect("../bad",null));
  }
 }

 static <T> dev.abros.rivet.api.DomainSubscription.Event<T> javaEvent(dev.abros.rivet.api.DomainSubscription<T> watch)throws Exception{
  long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
  while(System.nanoTime()<until){var next=watch.poll();if(next.isPresent())return next.get();Thread.sleep(10);}throw new AssertionError("Missing Java subscription event");
 }
 @Test void javaSubscriptionsReceiveGameChangesAndCloseWithRuntime()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());
  try(var service=new JavaDomains(db,store,cfg)){
   var view=service.connect("example_mod",UUID.fromString(player));assertTrue(view.capabilities().get().contains("notifications.watch"));
   try(var watch=view.watch(new dev.abros.rivet.api.DomainWatch.Notifications());var chat=view.watch(new dev.abros.rivet.api.DomainWatch.Chat())){
    assertTrue(((dev.abros.rivet.api.DomainSubscription.Snapshot<dev.abros.rivet.api.DomainData.Page<dev.abros.rivet.api.DomainData.Notice>>)javaEvent(watch)).value().items().isEmpty());javaEvent(chat);
    store.externalNotice(player,"Game notice");store.externalNotice(UUID.randomUUID().toString(),"Other private notice");
    var pushed=(dev.abros.rivet.api.DomainSubscription.Snapshot<dev.abros.rivet.api.DomainData.Page<dev.abros.rivet.api.DomainData.Notice>>)javaEvent(watch);
    assertEquals(1,pushed.value().items().size());assertEquals("Game notice",pushed.value().items().getFirst().title());
    new ChatHistory(db,100,7).append("local",System.currentTimeMillis()-1,"{\"text\":\"Nearby Java subscriber\"}",List.of(player));
    var message=(dev.abros.rivet.api.DomainSubscription.Snapshot<dev.abros.rivet.api.DomainData.Page<dev.abros.rivet.api.DomainData.ChatMessage>>)javaEvent(chat);assertEquals("Nearby Java subscriber",message.value().items().getFirst().text());
    service.close();assertEquals(Optional.of(ApiException.Code.UNAVAILABLE),((dev.abros.rivet.api.DomainSubscription.Closed<?>)javaEvent(watch)).error());assertTrue(chat.isClosed());
    assertThrows(ApiException.class,()->view.watch(new dev.abros.rivet.api.DomainWatch.Notifications()));
   }
  }
 }
 @Test void javaSubscriptionsRecheckPrivacyAndDisabledModules()throws Exception{
  String id=event("public");var cfg=ServerSettings.parse(ServerSettings.template());
  try(var service=new JavaDomains(db,store,cfg)){
   var view=service.connect("example_mod",null);
   try(var detail=view.watch(new dev.abros.rivet.api.DomainWatch.CommunityDocument(dev.abros.rivet.api.DomainData.Community.EVENTS,UUID.fromString(id)))){
    assertInstanceOf(dev.abros.rivet.api.DomainSubscription.Snapshot.class,javaEvent(detail));
    db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=body||'{\"visibility\":\"invited\"}'::jsonb WHERE id=?")){q.setString(1,id);q.executeUpdate();}db.communityChanged();return null;});
    assertEquals(Optional.of(ApiException.Code.NOT_FOUND),((dev.abros.rivet.api.DomainSubscription.Closed<?>)javaEvent(detail)).error());assertTrue(detail.poll().isEmpty());
   }
   try(var list=view.watch(new dev.abros.rivet.api.DomainWatch.CommunityList(dev.abros.rivet.api.DomainData.Community.EVENTS))){
    javaEvent(list);var config=store.configuration();config.getAsJsonArray("sections").remove(new JsonPrimitive("events"));store.configure(config);
    assertEquals(Optional.of(ApiException.Code.MODULE_DISABLED),((dev.abros.rivet.api.DomainSubscription.Closed<?>)javaEvent(list)).error());
   }
   try(var rejected=view.watch(new dev.abros.rivet.api.DomainWatch.Notifications())){assertEquals(Optional.of(ApiException.Code.UNAUTHENTICATED),((dev.abros.rivet.api.DomainSubscription.Closed<?>)javaEvent(rejected)).error());}
  }
 }
 static final class WsClient implements java.net.http.WebSocket.Listener {
  final java.util.concurrent.BlockingQueue<String> messages=new java.util.concurrent.LinkedBlockingQueue<>();final java.util.concurrent.CountDownLatch closed=new java.util.concurrent.CountDownLatch(1);final StringBuilder pending=new StringBuilder();
  public void onOpen(java.net.http.WebSocket ws){ws.request(1);}
  public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws,CharSequence data,boolean last){pending.append(data);if(last){messages.add(pending.toString());pending.setLength(0);}ws.request(1);return null;}
  public java.util.concurrent.CompletionStage<?> onClose(java.net.http.WebSocket ws,int code,String reason){closed.countDown();return null;}
  public void onError(java.net.http.WebSocket ws,Throwable error){closed.countDown();}
  JsonObject next()throws Exception{String message=messages.poll(8,java.util.concurrent.TimeUnit.SECONDS);assertNotNull(message,"Missing WS message");return JsonParser.parseString(message).getAsJsonObject();}
 }

 @Test void serverControlChecksPermissionRevisionAndReplay()throws Exception{
  String session=linked();assertEquals(403,get("admin/server",session).status());var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);access((who,right)->who.toString().equals(player)&&right.equals("rivet.restart")&&allowed.get());
  String revision=json(get("admin/server",session)).getAsJsonObject("item").get("revision").getAsString();var request=command("{\"seconds\":120,\"reason\":\"Deploy\"}");var saved=json(write("POST","admin/server/restart",session,revision,request));long at=store.record("state","restart").get("at").getAsLong();assertTrue(at>System.currentTimeMillis());assertTrue(json(write("POST","admin/server/restart",session,revision,request)).get("replayed").getAsBoolean());assertEquals(at,store.record("state","restart").get("at").getAsLong());
  assertEquals(403,write("POST","admin/server/maintenance",session,saved.get("revision").getAsString(),command("{\"enabled\":true,\"minutes\":5,\"reason\":\"x\"}")).status());
  assertEquals(409,write("POST","admin/server/restart",session,revision,command("{\"seconds\":0,\"reason\":\"\"}")).status());json(write("POST","admin/server/restart",session,saved.get("revision").getAsString(),command("{\"seconds\":0,\"reason\":\"\"}")));assertEquals(0,store.record("state","restart").get("at").getAsLong());allowed.set(false);assertEquals(403,write("POST","admin/server/restart",session,revision,request).status());
 }
 @Test void maintenanceAnnouncementsAndTypedControlUseSameState()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());try(var java=new JavaDomains(db,store,cfg).access((who,right)->true)){var api=java.connect("control_test",UUID.fromString(player));var before=api.serverControl().get();var saved=api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.SetMaintenance(before.revision(),true,5,"Maintenance")).get();assertTrue(api.serverControl().get().maintenance());api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.PinAnnouncement(saved.revision(),"News",30)).get();assertEquals("News",api.serverControl().get().announcement());}
 }
 @Test void taskSubtasksResourcesAndPaginationUseOwnershipAndRevision()throws Exception{
  String session=linked();var saved=json(write("POST","tasks",session,null,command("{\"title\":\"Task\",\"description\":\"\",\"group\":\"\"}")));String id=saved.get("id").getAsString();
  saved=json(write("POST","tasks/"+id+"/subtask-add",session,saved.get("revision").getAsString(),command("{\"title\":\"Step\",\"assignee\":\"\"}")));var details=json(get("tasks/"+id+"/details?subtaskOffset=0&commentOffset=0",session)).getAsJsonObject("item");assertEquals(1,details.get("subtaskCount").getAsInt());assertEquals("Step",details.getAsJsonArray("subtasks").get(0).getAsJsonObject().get("title").getAsString());
  saved=json(write("POST","tasks/"+id+"/subtask-toggle",session,saved.get("revision").getAsString(),command("{\"index\":0}")));assertEquals(409,get("tasks/"+id+"/details?revision=1",session).status());
  json(write("POST","tasks/"+id+"/resources",session,saved.get("revision").getAsString(),command("{\"resources\":[{\"item\":\"minecraft:stone\",\"amount\":10,\"alternatives\":[]}]}")));assertEquals(1,json(get("tasks/"+id+"/details",session)).getAsJsonObject("item").getAsJsonArray("resources").size());assertEquals(400,get("tasks/"+id+"/details?subtaskOffset=-1",session).status());
 }
 @Test void groupTerritoryAndEventRemindersReuseDomainRules()throws Exception{
  String session=linked();
  var group=json(write("POST","groups",session,null,command("{\"title\":\"Team\",\"description\":\"Details\",\"type\":\"Город\"}")));
  String id=group.get("id").getAsString();
  var saved=json(write("POST","groups/"+id+"/actions/territory",session,group.get("revision").getAsString(),command("{\"territory\":{\"dimension\":\"minecraft:overworld\",\"color\":16711680,\"points\":[{\"x\":0,\"z\":0},{\"x\":20,\"z\":0},{\"x\":0,\"z\":20}]}}")));
  assertEquals(1,json(get("map/territories",session)).getAsJsonArray("items").size());
  json(write("POST","groups/"+id+"/actions/territory",session,saved.get("revision").getAsString(),command("{\"territory\":null}")));
  assertEquals(0,json(get("map/territories",session)).getAsJsonArray("items").size());
  String event=event("public");
  json(write("POST","events/"+event+"/actions/reminder",session,"1",command("{\"minutes\":15}")));
  assertEquals(400,write("POST","events/"+event+"/actions/reminder",session,"1",command("{\"minutes\":3}")).status());
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE documents SET body=body||jsonb_build_object('series',?::text) WHERE id=?")){q.setString(1,event);q.setString(2,event);q.executeUpdate();}return null;});
  json(write("POST","events/"+event+"/actions/cancelSeries",session,"1",command("{}")));assertEquals("cancelled",json(get("events/"+event,session)).getAsJsonObject("item").get("status").getAsString());
 }
 @Test void ownReportsAreIdempotentAndNeverExposeStaffNotes()throws Exception{
  String session=linked();var body=command("{\"message\":\"Help\",\"category\":\"technical\"}");var created=json(write("POST","reports",session,null,body));String id=created.get("id").getAsString();json(write("POST","reports",session,null,body));assertEquals(1,json(get("reports",session)).getAsJsonArray("items").size());
  var report=store.record("reports",id);report.addProperty("priority","high");report.addProperty("audit","private diagnostics");report.add("internalNotes",JsonParser.parseString("[{\"text\":\"staff only\"}]"));store.record("reports",id,report);
  String view=json(get("reports/"+id,session)).toString();assertFalse(view.contains("staff only"));assertFalse(view.contains("private diagnostics"));assertFalse(view.contains("priority"));
  assertEquals(401,get("reports",null).status());String stranger=UUID.randomUUID().toString();var input=JsonParser.parseString("{\"message\":\"secret\",\"category\":\"other\"}").getAsJsonObject();String foreign=new CommunityReports(store).submit(UUID.fromString(stranger),"Other",input,System.currentTimeMillis());assertEquals(404,get("reports/"+foreign,session).status());
 }
 void access(DomainAccess permissions)throws Exception{var cfg=ServerSettings.parse(ServerSettings.template());rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg).access(permissions)).commands(new CommunityApiCommands(db,store,cfg).access(permissions)));}
 @Test void staffReportsNeedBothScopeAndCurrentPermissionIncludingReplay()throws Exception{
  String session=linked();String id=json(write("POST","reports",session,null,command("{\"message\":\"Help\",\"category\":\"technical\"}"))).get("id").getAsString();assertEquals(403,get("admin/reports",session).status());
  var permitted=new java.util.concurrent.atomic.AtomicBoolean(true);access((who,right)->who.toString().equals(player)&&right.equals("rivet.reports")&&permitted.get());assertEquals(1,json(get("admin/reports",session)).getAsJsonArray("items").size());
  var reply=command("{\"text\":\"Fixed\",\"resolved\":true}");json(write("POST","admin/reports/"+id+"/actions/reply",session,"1",reply));assertEquals("Fixed",json(get("reports/"+id,session)).getAsJsonObject("item").get("reply").getAsString());
  assertEquals(409,write("POST","admin/reports/"+id+"/actions/reply",session,"1",command("{\"text\":\"Stale\",\"resolved\":false}")).status());permitted.set(false);assertEquals(403,write("POST","admin/reports/"+id+"/actions/reply",session,"1",reply).status());assertEquals(403,get("admin/reports/"+id,session).status());
 }
 @Test void mapModerationRequiresLiveRightsAndPublishesOnlyApprovedDraft()throws Exception{
  String session=linked(),id=UUID.randomUUID().toString();var draft=command("{\"type\":\"marker\",\"draft\":{\"name\":\"Place\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"color\":16711680,\"icon\":\"flag\"}}");
  var saved=json(write("PUT","map/objects/"+id+"/draft",session,"0",draft));String revision=saved.get("revision").getAsString();var approval=command("{}");
  assertEquals(403,write("POST","admin/map/objects/"+id+"/approve",session,revision,approval).status());assertEquals(404,get("map/objects/"+id,null).status());
  var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);access((who,right)->who.toString().equals(player)&&right.equals("rivet.admin")&&allowed.get());assertTrue(json(get("admin/map/objects/"+id,session)).getAsJsonObject("item").has("draft"));
  json(write("POST","admin/map/objects/"+id+"/approve",session,revision,approval));assertEquals(200,get("map/objects/"+id,null).status());allowed.set(false);assertEquals(403,write("POST","admin/map/objects/"+id+"/approve",session,revision,approval).status());assertEquals(403,get("admin/map/objects",session).status());
 }
 @Test void metadataUsesExistingPrivacyAndNeverExportsRawSkinProfile()throws Exception{
  String session=linked();var stats=new PlayerStatistics(db,PlayerStatistics.Settings.defaults());stats.checkpoint(new PlayerStatistics.Checkpoint(UUID.fromString(player),UUID.randomUUID(),10,20,10,2,false));var result=json(get("statistics/"+player,session));assertTrue(result.toString().contains("totalMillis"));assertFalse(result.toString().contains("sessionId"));assertFalse(result.toString().contains("checkpoint"));
  var skin=json(get("skins/"+player,session)).getAsJsonObject("item");assertEquals(Set.of("uuid","hash","slim"),skin.keySet());assertEquals(401,get("statistics/"+player,null).status());
 }
 @Test void externalChatIsUnsignedDeduplicatedRateLimitedAndDrained()throws Exception{
  String session=linked();var message=command("{\"channel\":\"global\",\"text\":\"Hello\"}");json(write("POST","chat/messages",session,null,message));json(write("POST","chat/messages",session,null,message));var cfg=ServerSettings.parse(ServerSettings.template());var chat=new ApiChat(db,store,cfg,who->{throw new dev.abros.rivet.api.ApiException(dev.abros.rivet.api.ApiException.Code.FORBIDDEN,"offline");});var lines=new ArrayList<String>();long queuedRevision=db.communityRevision();chat.drain((channel,sender,text,audience)->lines.add(text));assertEquals(List.of("[API website] Alice: Hello"),lines);assertTrue(db.communityRevision()>queuedRevision,"Drain must wake the next batch");chat.drain((channel,sender,text,audience)->fail("Duplicate delivery"));
  for(int i=0;i<4;i++)json(write("POST","chat/messages",session,null,command("{\"channel\":\"global\",\"text\":\"Hello\"}")));assertEquals(429,write("POST","chat/messages",session,null,command("{\"channel\":\"global\",\"text\":\"Hello\"}")).status());
  assertTrue(json(get("chat/history",session)).toString().contains("[API website] Alice: Hello"));assertEquals(400,write("POST","chat/messages",session,null,command("{\"channel\":\"global\",\"text\":\"x\",\"sender\":\"Admin\"}")).status());
 }
 @Test void createAndManageCommunityReuseOwnershipAndRevision()throws Exception{
  String session=linked();var created=json(write("POST","ideas",session,null,command("{\"title\":\"Idea\",\"description\":\"Details\"}")));String id=created.get("id").getAsString();String revision=json(get("ideas/"+id,session)).getAsJsonObject("item").get("revision").getAsString();json(write("POST","ideas/"+id+"/actions/edit",session,revision,command("{\"title\":\"Updated\",\"description\":\"Details\"}")));assertEquals("Updated",json(get("ideas/"+id,session)).getAsJsonObject("item").get("title").getAsString());assertEquals(409,write("POST","ideas/"+id+"/actions/edit",session,revision,command("{\"title\":\"Stale\",\"description\":\"Details\"}")).status());
 }

 @Test void typedJavaReportsWatchesAndPackProjection()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());try(var java=new JavaDomains(db,store,cfg).pack(()->new dev.abros.rivet.core.pack.PackManifest("1.21.1","21.1.250",List.of(),List.of()))){var api=java.connect("consumer",UUID.fromString(player));var receipt=api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.SubmitReport("Question","other")).get();assertEquals("Question",api.report(UUID.fromString(receipt.id())).get().message());assertEquals(1,api.reports("").get().items().size());assertEquals("1.21.1",api.pack().get().minecraft());assertTrue(api.statistics(UUID.fromString(player)).get().totalMillis().isEmpty());assertNotNull(api.skin(UUID.fromString(player)).get());assertThrows(java.util.concurrent.ExecutionException.class,()->api.staffReports("all","").get());}
 }
 @Test void notificationPreferencesUseContentRevision()throws Exception{
  String session=linked();String revision=json(get("me/notification-preferences",session)).getAsJsonObject("item").get("revision").getAsString();var body=command("{\"muted\":[\"events\"],\"snoozeUntil\":0}");json(write("PUT","me/notification-preferences",session,revision,body));assertTrue(json(get("me/notification-preferences",session)).toString().contains("events"));assertEquals(409,write("PUT","me/notification-preferences",session,revision,command("{\"muted\":[],\"snoozeUntil\":0}")).status());
 }
 @Test void taskAssignmentAndDueDateUseTaskRules()throws Exception{
  String session=linked();String id=json(write("POST","tasks",session,null,command("{\"title\":\"Task\",\"description\":\"\",\"group\":\"\"}"))).get("id").getAsString();String revision=json(get("tasks/"+id,session)).getAsJsonObject("item").get("revision").getAsString();assertEquals(403,write("POST","tasks/"+id+"/assign",session,revision,command("{\"assignee\":\""+UUID.randomUUID()+"\"}")).status());json(write("POST","tasks/"+id+"/due",session,revision,command("{\"dueAt\":0}")));assertEquals(409,write("POST","tasks/"+id+"/comment",session,revision,command("{\"text\":\"stale\"}")).status());
 }
 @Test void staffSubscriptionStopsAfterPermissionWithdrawal()throws Exception{
  String session=linked();var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);var cfg=ServerSettings.parse(ServerSettings.template());var domains=new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg).access((id,right)->allowed.get()));var subscriptions=new DomainSubscriptions(domains);var target=new DomainSubscriptions.Target("admin/reports",Map.of(),session);var first=subscriptions.snapshot(grant,target,null);allowed.set(false);assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor")));
 }
 @Test void internalMarkersArePrivateVersionedAndCannotDeleteTasks()throws Exception{
  String session=linked();var group=json(write("POST","groups",session,null,command("{\"title\":\"Team\",\"description\":\"Details\",\"type\":\"Город\"}")));String id=group.get("id").getAsString(),rev=group.get("revision").getAsString();
  var data=Json.parse("{\"itemId\":\"\",\"itemRevision\":0,\"title\":\"Base\",\"description\":\"Secret\",\"location\":{\"name\":\"Base\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2,\"membersOnly\":false}}");var request=command(data.toString());var saved=json(write("POST","groups/"+id+"/actions/markerSave",session,rev,request));json(write("POST","groups/"+id+"/actions/markerSave",session,rev,request));
  var list=json(get("groups/"+id+"/markers",session));assertEquals(1,list.getAsJsonArray("items").size());assertTrue(list.getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonObject("location").get("membersOnly").getAsBoolean());assertEquals(401,get("groups/"+id+"/markers",null).status());
  String stranger=UUID.randomUUID().toString();store.seen(new CommunityStore.Actor(stranger,"Bob",false,false));var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",stranger,"Bob");String other=sessions.exchange(grant,c.code(),c.pollToken()).token();assertEquals(403,get("groups/"+id+"/markers",other).status());
  data.addProperty("itemId",saved.get("id").getAsString());data.addProperty("itemRevision",0);assertEquals(409,write("POST","groups/"+id+"/actions/markerSave",session,list.get("revision").getAsString(),command(data.toString())).status());
  var deletion=new JsonObject();deletion.add("itemId",saved.get("id"));deletion.addProperty("itemRevision",1);json(write("POST","groups/"+id+"/actions/markerDelete",session,list.get("revision").getAsString(),command(deletion.toString())));assertEquals(0,json(get("groups/"+id+"/markers",session)).getAsJsonArray("items").size());
  String task=json(write("POST","tasks",session,null,command("{\"title\":\"Task\",\"description\":\"\",\"group\":\""+id+"\"}"))).get("id").getAsString();deletion.addProperty("itemId",task);String current=json(get("groups/"+id,session)).getAsJsonObject("item").get("revision").getAsString();assertEquals(404,write("POST","groups/"+id+"/actions/markerDelete",session,current,command(deletion.toString())).status());
 }
 @Test void groupPeoplePagesPreservePrivateApplicationsAndRejectStaleCursors()throws Exception{
  String session=linked();String id=json(write("POST","groups",session,null,command("{\"title\":\"Team\",\"description\":\"Details\",\"type\":\"Город\"}"))).get("id").getAsString();
  db.transaction(()->{for(int i=0;i<21;i++)try(var q=db.connection().prepareStatement("INSERT INTO community_relations VALUES(?,'applications',?,?::jsonb)")){q.setString(1,id);q.setString(2,UUID.randomUUID().toString());q.setString(3,"{\"name\":\"Applicant\",\"text\":\"Private reason\"}");q.executeUpdate();}return null;});
  var first=json(get("groups/"+id+"/applications",session));assertEquals(20,first.getAsJsonArray("items").size());String cursor=first.get("nextCursor").getAsString();assertEquals(1,json(get("groups/"+id+"/applications?cursor="+cursor,session)).getAsJsonArray("items").size());
  String stranger=UUID.randomUUID().toString();store.seen(new CommunityStore.Actor(stranger,"Bob",false,false));var c=sessions.challenge(grant);sessions.confirm(c.code(),"website",stranger,"Bob");String other=sessions.exchange(grant,c.code(),c.pollToken()).token();assertEquals(0,json(get("groups/"+id+"/applications",other)).getAsJsonArray("items").size());assertEquals(1,json(get("groups/"+id+"/members",other)).getAsJsonArray("items").size());
  json(write("POST","groups/"+id+"/actions/recruiting",session,first.get("revision").getAsString(),command("{}")));assertEquals(409,get("groups/"+id+"/applications?cursor="+cursor,session).status());
 }
 @Test void privateRecurringEventsAreAtomicAndKeepLocalTimeAcrossDst()throws Exception{
  String session=linked();access((who,right)->right.equals("rivet.events"));
  var start=java.time.ZonedDateTime.of(2028,3,19,18,0,0,0,java.time.ZoneId.of("Europe/Warsaw"));
  var data=Json.parse("{\"title\":\"Private\",\"description\":\"Details\",\"capacity\":10,\"visibility\":\"invited\",\"group\":\"\",\"invitees\":\"\",\"durationMinutes\":90,\"repeat\":\"weekly\",\"occurrences\":3,\"timezone\":\"Europe/Warsaw\"}");data.addProperty("startsAt",start.toInstant().toString());var request=command(data.toString());var receipt=json(write("POST","events",session,null,request));json(write("POST","events",session,null,request));String id=receipt.get("id").getAsString();var schedule=json(get("events/"+id+"/schedule",session)).getAsJsonObject("item");assertEquals(3,schedule.getAsJsonArray("occurrences").size());int i=0;for(var row:schedule.getAsJsonArray("occurrences"))assertEquals(start.plusWeeks(i++).toInstant().toEpochMilli(),row.getAsJsonObject().get("startsAt").getAsLong());assertEquals(0,json(get("events",null)).getAsJsonArray("items").size());
  data.addProperty("timezone","Not/AZone");assertEquals(400,write("POST","events",session,null,command(data.toString())).status());data.addProperty("timezone","Europe/Warsaw");
  data.addProperty("visibility","group");data.addProperty("group",UUID.randomUUID().toString());assertNotEquals(200,write("POST","events",session,null,command(data.toString())).status());assertEquals(3,json(get("events",session)).getAsJsonArray("items").size());
 }
 @Test void scheduledAnnouncementsReuseDeliveryRightsAndReceipts()throws Exception{
  String session=linked();var allowed=new java.util.concurrent.atomic.AtomicBoolean(false);access((who,right)->allowed.get()&&right.equals("rivet.announce"));var request=command("{\"text\":\"News\",\"startsAt\":\""+Instant.now().plusSeconds(60)+"\"}");assertEquals(403,write("POST","admin/server/announcements",session,null,request).status());allowed.set(true);long before=db.communityRevision();var receipt=json(write("POST","admin/server/announcements",session,null,request));assertTrue(db.communityRevision()>before);json(write("POST","admin/server/announcements",session,null,request));assertEquals(1,json(get("admin/server/announcements",session)).getAsJsonArray("items").size());
  assertEquals(409,write("POST","admin/server/announcements/"+receipt.get("id").getAsString()+"/cancel",session,"0",command("{}")).status());json(write("POST","admin/server/announcements/"+receipt.get("id").getAsString()+"/cancel",session,"1",command("{}")));assertEquals(0,new ScheduledAnnouncements(store).publish(System.currentTimeMillis()+120000,List.of(player)));
  var second=json(write("POST","admin/server/announcements",session,null,command("{\"text\":\"Published\",\"startsAt\":\""+Instant.now().plusSeconds(60)+"\"}")));assertEquals(1,new ScheduledAnnouncements(store).publish(System.currentTimeMillis()+120000,List.of(player)));assertEquals(0,new ScheduledAnnouncements(store).publish(System.currentTimeMillis()+120000,List.of(player)));allowed.set(false);assertEquals(403,write("POST","admin/server/announcements",session,null,request).status());assertEquals(403,get("admin/server/announcements",session).status());
 }
 @Test void typedExtendedReadsAndCommandsSharePolicies()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template());try(var java=new JavaDomains(db,store,cfg).access((who,right)->true)){var api=java.connect("details_test",UUID.fromString(player));var group=api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.CreateGroup("Team","Details","Город")).get();var id=UUID.fromString(group.id());var people=api.groupPeople(id,dev.abros.rivet.api.DomainData.GroupPeople.MEMBERS,"").get();assertEquals(player,people.items().getFirst().id().toString());api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.SaveGroupMarker(id,people.revision(),Optional.empty(),0,"Base","",new dev.abros.rivet.api.DomainData.Location("Base","minecraft:overworld",1,64,2,true))).get();assertEquals(1,api.groupMarkers(id,"").get().items().size());api.execute(UUID.randomUUID(),System.currentTimeMillis(),new dev.abros.rivet.api.DomainCommand.ScheduleAnnouncement("News",Instant.now().plusSeconds(60).toString())).get();assertEquals(1,api.scheduledAnnouncements().get().items().size());}
 }
 @Test void markerSubscriptionLosesAccessAfterLeavingAndInvitationsAreOwnOnly()throws Exception{
  String session=linked();var created=json(write("POST","groups",session,null,command("{\"title\":\"Team\",\"description\":\"Details\",\"type\":\"Город\"}")));String id=created.get("id").getAsString();String bob=UUID.randomUUID().toString();store.seen(new CommunityStore.Actor(bob,"Bob",false,false));var challenge=sessions.challenge(grant);sessions.confirm(challenge.code(),"website",bob,"Bob");String other=sessions.exchange(grant,challenge.code(),challenge.pollToken()).token();
  var invitation=json(write("POST","groups/"+id+"/actions/invite",session,created.get("revision").getAsString(),command("{\"target\":\""+bob+"\"}")));assertEquals(1,json(get("groups/"+id+"/invitations",other)).getAsJsonArray("items").size());json(write("POST","groups/"+id+"/actions/invitation",other,invitation.get("revision").getAsString(),command("{\"accept\":true}")));
  var cfg=ServerSettings.parse(ServerSettings.template());var domains=new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg));var subscriptions=new DomainSubscriptions(domains,db::communityRevision);var target=new DomainSubscriptions.Target("groups/"+id+"/markers",Map.of(),other);var first=subscriptions.snapshot(grant,target,null);String revision=json(get("groups/"+id,other)).getAsJsonObject("item").get("revision").getAsString();json(write("POST","groups/"+id+"/actions/leave",other,revision,command("{}")));assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor")));
  try(var java=new JavaDomains(db,store,cfg)){var api=java.connect("member_test",UUID.fromString(bob));assertThrows(java.util.concurrent.ExecutionException.class,()->api.groupMarkers(UUID.fromString(id),"").get());}
 }
 @Test void liveMapRequiresSessionAndSeparateScopesAndReauthorizesSubscriptions()throws Exception{
  String session=linked();var enabled=new java.util.concurrent.atomic.AtomicBoolean(true);var reads=new java.util.concurrent.atomic.AtomicInteger();LiveMapAccess live=(viewer,path,query)->{assertEquals(UUID.fromString(player),viewer);reads.incrementAndGet();if(!enabled.get())throw new ApiException(ApiException.Code.FORBIDDEN,"offline");var out=new JsonObject();out.add("items",JsonParser.parseString("[{\"uuid\":\""+player+"\",\"name\":\"Alice\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":64,\"z\":2}]"));out.addProperty("nextCursor","");return out;};
  var cfg=ServerSettings.parse(ServerSettings.template());var domains=new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg).live(live));rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),domains);
  for(String path:List.of("players/online","map/positions","map/waystones")){assertEquals(401,get(path,null).status());assertEquals(200,get(path,session).status());assertEquals(400,get(path+"?uuid="+player,session).status());}
  String prior=token;var limited=keys.create(root,"map_only",Set.of("map.read"),1,100);token=Files.readString(limited.deliveryFile()).trim();assertEquals(403,get("map/positions",session).status());token=prior;
  var subscriptions=new DomainSubscriptions(domains);var target=new DomainSubscriptions.Target("map/positions",Map.of(),session);var first=subscriptions.snapshot(grant,target,null);enabled.set(false);assertThrows(ApiException.class,()->subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor")));assertEquals(403,get("map/positions",session).status());
  enabled.set(true);try(var java=new JavaDomains(db,store,cfg).live(live)){var api=java.connect("live_test",UUID.fromString(player));assertEquals(1,api.positions("minecraft:overworld","").get().items().size());assertEquals(1,api.waystones("","").get().items().size());enabled.set(false);assertThrows(java.util.concurrent.ExecutionException.class,()->api.positions("","").get());}
 }
 @Test void disabledPositionsRejectBeforeRuntimeAccess()throws Exception{
  linked();var cfg=ServerSettings.parse(ServerSettings.template().replaceAll("(?ms)(\\[map\\][^\\[]*?)^positions = true","$1positions = false"));assertFalse(cfg.flag("map.positions"));var resources=new CommunityApiResources(db,store,cfg).live((viewer,path,q)->{fail("Disabled positions reached game thread");return null;});var actor=new CommunityStore.Actor(player,"Alice",false,false);assertThrows(ApiException.class,()->resources.read(actor,"map/positions",Map.of()));assertThrows(ApiException.class,()->resources.read(actor,"players/online",Map.of()));
 }
 @Test void integrationLayersUsePublicFilteringAndCloseSnapshotsAcrossTransports()throws Exception{
  String session=linked();var cfg=ServerSettings.parse(ServerSettings.template());var layers=new MapLayerRegistry(true);var owner=layers.owner("integration_mod");var publicAudience=dev.abros.rivet.api.MapLayersApi.Audience.publicAccess();var layer=owner.register(new dev.abros.rivet.api.MapLayersApi.LayerSpec("test","Integration",publicAudience));
  var marker=new dev.abros.rivet.api.DomainData.Marker("minecraft:overworld","Private",1,64,2,1,"flag");layer.put(new dev.abros.rivet.api.MapLayersApi.ObjectSpec("secret",marker,dev.abros.rivet.api.MapLayersApi.Audience.restricted(Set.of(UUID.fromString(player)))),0);
  var domains=new DomainRest(keys,sessions,store,new CommunityApiResources(db,store,cfg).layers(layers));rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),domains);
  assertEquals(1,json(get("map/layers",null)).getAsJsonArray("items").size());assertEquals(0,json(get("map/layers/integration_mod/test/objects",null)).getAsJsonArray("items").size());assertEquals(1,json(get("map/layers/integration_mod/test/objects",session)).getAsJsonArray("items").size());
  var subscriptions=new DomainSubscriptions(domains,layers::revision);var target=new DomainSubscriptions.Target("map/layers",Map.of(),session);var first=subscriptions.snapshot(grant,target,null);long before=layers.revision();
  try(var java=new JavaDomains(db,store,cfg).layers(layers)){var api=java.connect("reader_mod",UUID.fromString(player));assertEquals(1,api.mapLayerObjects("integration_mod:test","",OptionalLong.empty()).get().items().size());assertTrue(java.connect("reader_mod",null).mapLayerObjects("integration_mod:test","",OptionalLong.empty()).get().items().isEmpty());owner.close();assertTrue(api.mapLayers("",OptionalLong.empty()).get().items().isEmpty());}
  assertTrue(layers.revision()>before);var changed=subscriptions.snapshot(grant,target,first.getAsJsonObject("cursor"));assertEquals(0,changed.getAsJsonObject("resource").getAsJsonArray("items").size());assertEquals(404,get("map/layers/integration_mod/test/objects",session).status());
 }
}
