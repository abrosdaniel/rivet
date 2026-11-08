package dev.abros.rivet.core.api;

import dev.abros.rivet.core.*;
import dev.abros.rivet.api.NotificationsApi;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("postgres")
class NotificationRestTest {
 @TempDir Path root;
 PgDatabase db;ApiKeys keys;NotificationPublisher publisher;NotificationRest endpoint;RestApi rest;CommunityStore store;
 final String recipient=UUID.randomUUID().toString();
 @BeforeEach void setup()throws Exception{
  db=TestDatabase.database(root);store=new CommunityStore(db,CommunityStore.defaults());
  keys=new ApiKeys(db,new ApiKeys.Limits(30,5,1000,30,1,100),Clock.systemUTC());publisher=new NotificationPublisher(db);
  endpoint=new NotificationRest(keys,publisher,(request,path,query)->null);
  rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),endpoint);
 }
 @AfterEach void close(){endpoint.close();publisher.close();keys.close();}
 ApiKeys.Issued key(String app,Set<String> scopes)throws Exception{return keys.create(root,app,scopes,7,1000);}
 String token(ApiKeys.Issued key)throws Exception{return Files.readString(key.deliveryFile()).trim();}
 RestApi.Response request(String method,String token,long revision,String body){return rest.handle(new RestApi.Request(method,"/api/v1/notifications/mail/"+recipient+"/parcel",Map.of("Authorization",List.of("Bearer "+token),"If-Match",List.of("\""+revision+"\"")),body));}
 long revision(RestApi.Response response)throws Exception{assertEquals(200,response.status(),response.body());return Json.parse(response.body()).get("revision").getAsLong();}
 @Test void publicationRotationIsolationReadStateAndRevocation()throws Exception{
  var issued=key("website",Set.of("notifications.publish","notifications.revoke"));String first=token(issued);
  String body="{\"title\":\"Parcel\",\"body\":\"Ready\",\"translations\":{\"ru_ru\":{\"title\":\"Посылка\",\"body\":\"Готова\"}}}";
  long created=revision(request("PUT",first,0,body));assertEquals(created,revision(request("PUT",first,0,body)));assertEquals(1,store.unread(recipient));
  var foreign=token(key("another",Set.of("notifications.publish","notifications.revoke")));
  assertEquals(409,request("PUT",foreign,created,body).status());assertEquals(200,request("DELETE",foreign,created,"").status());assertEquals(1,store.unread(recipient));
  String rotated=token(keys.rotate(root,issued.key().id(),7));assertEquals(401,request("PUT",first,created,body).status());
  long updated=revision(request("PUT",rotated,created,"{\"title\":\"Parcel\",\"body\":\"Changed\"}"));assertTrue(updated>created);assertEquals(1,store.unread(recipient));
  assertEquals(409,request("DELETE",rotated,created,"").status());assertEquals(200,request("DELETE",rotated,updated,"").status());assertEquals(0,store.unread(recipient));
  assertEquals(200,request("DELETE",rotated,updated,"").status());
 }
 @Test void restartAndLostUpdateReplyPreserveReceiptAndReadState()throws Exception{
  String token=token(key("website",Set.of("notifications.publish","notifications.revoke")));
  String first="{\"title\":\"Parcel\",\"body\":\"Ready\"}",second="{\"title\":\"Parcel\",\"body\":\"Updated\"}";
  var response=request("PUT",token,0,first);long before=revision(response),id=Json.parse(response.body()).get("id").getAsLong();
  db.transaction(()->{try(var q=db.connection().prepareStatement("UPDATE notices SET read=1 WHERE id=?")){q.setLong(1,id);q.executeUpdate();}return null;});
  long updated=revision(request("PUT",token,before,second));assertEquals(updated,revision(request("PUT",token,before,second)));assertEquals(0,store.unread(recipient));
  endpoint.close();publisher.close();publisher=new NotificationPublisher(db);endpoint=new NotificationRest(keys,publisher,(r,p,q)->null);rest=new RestApi(RestApiTest.feed(),RestApi.authorization(keys),RestApiTest.settings(Set.of()),endpoint);
  assertEquals(updated,revision(request("PUT",token,before,second)));assertEquals(0,store.unread(recipient));
  assertEquals(409,request("PUT",token,before,first).status());
 }
 @Test void rightsAndPayloadNeverGrantPlayerReadOrForeignNamespace()throws Exception{
  var publish=token(key("website",Set.of("notifications.publish")));
  assertEquals(403,request("DELETE",publish,1,"").status());
  assertEquals(400,request("PUT",publish,0,"{\"title\":\"T\",\"body\":\"B\",\"read\":true}").status());
  assertEquals(400,request("PUT",publish,0,"{\"title\":\"T\",\"body\":\"B\",\"namespace\":\"another\"}").status());
  assertEquals(400,request("PUT",publish,0,"{\"title\":\"T\",\"body\":\"B\",\"action\":{\"type\":\"command\",\"id\":\"op player\"}}").status());
  assertEquals(400,request("GET",publish,0,"").status());assertEquals(0,store.unread(recipient));
  var denied=token(key("readonly",Set.of("notifications.read")));assertEquals(403,request("PUT",denied,0,"{\"title\":\"T\",\"body\":\"B\"}").status());
 }
 @Test void keyRevokedWhileWaitingCannotPublish()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var blocker=publisher.owner("test_blocker").register("mail");
  var held=publisher.putAuthorized(blocker,UUID.randomUUID(),"hold",new NotificationsApi.Message("Hold",""),0,()->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Barrier timed out");return null;});
  assertTrue(entered.await(2,TimeUnit.SECONDS));
  var issued=key("website",Set.of("notifications.publish"));String secret=token(issued);
  try(var executor=Executors.newSingleThreadExecutor()){
   var response=executor.submit(()->request("PUT",secret,0,"{\"title\":\"T\",\"body\":\"B\"}"));
   var field=NotificationPublisher.class.getDeclaredField("worker");field.setAccessible(true);var worker=(ThreadPoolExecutor)field.get(publisher);
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(worker.getQueue().isEmpty()&&System.nanoTime()<deadline)Thread.sleep(5);
   assertFalse(worker.getQueue().isEmpty());keys.state(issued.key().id(),"revoke");release.countDown();held.get(2,TimeUnit.SECONDS);
   assertEquals(401,response.get(2,TimeUnit.SECONDS).status());assertEquals(0,store.unread(recipient));
  }finally{release.countDown();}
 }
}
