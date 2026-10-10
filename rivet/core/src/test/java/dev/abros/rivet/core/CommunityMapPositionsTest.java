package dev.abros.rivet.core;
import com.google.gson.*;
import dev.abros.rivet.core.map.MapPositionPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("postgres") class CommunityMapPositionsTest {
 @TempDir Path temp;PgDatabase db;CommunityStore store;
 CommunityStore.Actor owner=new CommunityStore.Actor(UUID.randomUUID().toString(),"Owner",false,false),viewer=new CommunityStore.Actor(UUID.randomUUID().toString(),"Viewer",false,false),admin=new CommunityStore.Actor(UUID.randomUUID().toString(),"Admin",true,true);
 @BeforeEach void setup()throws Exception{db=TestDatabase.database(temp);store=new CommunityStore(db,CommunityStore.defaults());for(var a:List.of(owner,viewer,admin))store.seen(a);}
 JsonObject cmd(String op){var q=new JsonObject();q.addProperty("action","community");q.addProperty("section","home");q.addProperty("op",op);return q;}
 JsonObject save(String mode,String audience)throws Exception{var q=MenuCommands.prepare(cmd("mapPositionSave"),System.currentTimeMillis());q.add("settings",new MapPositionPolicy(mode,audience).json());store.request(owner,q);return q;}
 JsonArray peers(CommunityStore.Actor a)throws Exception{var q=cmd("mapPositionPeers");var ids=new JsonArray();ids.add(owner.id());q.add("positionCandidates",ids);return store.request(a,q).getAsJsonArray("positionPolicies");}
 @Test void newPlayerVisibleToAllAndNoCoordinatesPersisted()throws Exception{assertEquals(MapPositionPolicy.defaults().json(),store.request(owner,cmd("mapPositionSettings")).get("positionSettings"));assertEquals(1,peers(viewer).size());assertEquals(Set.of("uuid","mode","audience"),peers(viewer).get(0).getAsJsonObject().keySet());assertNull(store.record("map-position",owner.id()));}
 @Test void hiddenAndNobodyProtectEvenFromAdminAndRetryCannotUndoNewerSave()throws Exception{var old=save("full","all");save("hidden","all");assertTrue(peers(viewer).isEmpty());assertTrue(peers(admin).isEmpty());assertEquals("hidden",Json.str(store.request(owner,old).getAsJsonObject("positionSettings"),"mode"));save("full","none");assertTrue(peers(admin).isEmpty());store=new CommunityStore(db,CommunityStore.defaults());assertEquals("none",Json.str(store.request(owner,cmd("mapPositionSettings")).getAsJsonObject("positionSettings"),"audience"));}
 @Test void groupsRequireCurrentMembershipAndEnabledModule()throws Exception{var q=cmd("create");q.addProperty("section","groups");q.addProperty("title","Group");q.addProperty("description","Description");q.addProperty("type","Команда");String group=Json.str(store.request(owner,q).getAsJsonObject("detail"),"id");save("full","groups");assertTrue(peers(viewer).isEmpty());q=cmd("invite");q.addProperty("section","groups");q.addProperty("id",group);q.addProperty("target",viewer.id());store.request(owner,q);q=cmd("invitation");q.addProperty("section","groups");q.addProperty("id",group);q.addProperty("accept",true);store.request(viewer,q);assertEquals(1,peers(viewer).size());assertTrue(peers(admin).isEmpty());q=cmd("leave");q.addProperty("section","groups");q.addProperty("id",group);store.request(viewer,q);assertTrue(peers(viewer).isEmpty());store=new CommunityStore(db,CommunityStore.defaults(),TaskLimits.defaults(),FeatureModules.from(ServerSettings.parse(ServerSettings.template().replaceAll("(?ms)(\\[groups\\][^\\[]*?)^enabled = true","$1enabled = false"))));assertTrue(peers(viewer).isEmpty());save("full","all");assertEquals(1,peers(viewer).size());}
 @Test void candidateInputMustBeTrustedAndBounded(){assertThrows(CommunityFailure.class,()->store.request(viewer,cmd("mapPositionPeers")));var q=cmd("mapPositionPeers");var ids=new JsonArray();for(int i=0;i<65;i++)ids.add(UUID.randomUUID().toString());q.add("positionCandidates",ids);assertThrows(IllegalArgumentException.class,()->store.request(viewer,q));}
 @Test void defaultAudienceLoadUsesOnePolicyQueryPerPageAndNoMembershipQueries()throws Exception{
  PerformanceMetrics.clear();PerformanceMetrics.detailed(true);
  try{for(int count:new int[]{10,50,100}){PerformanceMetrics.clear();int pages=0,total=0;
   for(int first=0;first<count;first+=64){var q=cmd("mapPositionPeers");var ids=new JsonArray();for(int n=first;n<Math.min(count,first+64);n++)ids.add(new UUID(0,n+1).toString());q.add("positionCandidates",ids);total+=store.request(viewer,q).getAsJsonArray("positionPolicies").size();pages++;}
   assertEquals(count,total);assertEquals(pages,PerformanceMetrics.snapshot().get("map.positions.policies").count());assertFalse(PerformanceMetrics.snapshot().containsKey("map.positions.groups"));
  }}finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }
 @Test void emptyCandidatesAvoidBothPositionQueries()throws Exception{
  PerformanceMetrics.clear();PerformanceMetrics.detailed(true);try{var q=cmd("mapPositionPeers");q.add("positionCandidates",new JsonArray());assertTrue(store.request(viewer,q).getAsJsonArray("positionPolicies").isEmpty());assertFalse(PerformanceMetrics.snapshot().containsKey("map.positions.policies"));assertFalse(PerformanceMetrics.snapshot().containsKey("map.positions.groups"));}finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }
 @Test void hiddenGroupAudienceDoesNotReadMembership()throws Exception{
  save("hidden","groups");PerformanceMetrics.clear();PerformanceMetrics.detailed(true);try{assertTrue(peers(viewer).isEmpty());assertFalse(PerformanceMetrics.snapshot().containsKey("map.positions.groups"));}finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }

 @Test void trustedCompactSnapshotResolvesAllPoliciesInOneQuery()throws Exception{
  var q=cmd("mapPositionPeers");q.addProperty("positionCompact",true);var ids=new JsonArray();for(int i=0;i<4096;i++)ids.add(new UUID(0,i+1).toString());q.add("positionCandidates",ids);
  PerformanceMetrics.clear();PerformanceMetrics.detailed(true);try{assertEquals(4096,store.request(viewer,q).getAsJsonArray("positionPolicies").size());assertEquals(1,PerformanceMetrics.snapshot().get("map.positions.policies").count());assertFalse(PerformanceMetrics.snapshot().containsKey("map.positions.groups"));ids.add(new UUID(0,4097).toString());assertThrows(IllegalArgumentException.class,()->store.request(viewer,q));}finally{PerformanceMetrics.detailed(false);PerformanceMetrics.clear();}
 }

}
