package dev.abros.rivet.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in dense SQL fixture; never uses a live server database. */
@Tag("postgres")
@EnabledIfEnvironmentVariable(named="RIVET_LARGE_READ",matches="true")
class CommunityLargeReadTest {
 @TempDir Path temp;
 @Test void lists()throws Exception{
  var db=TestDatabase.database(temp);long now=1800000000000L;
  db.transaction(()->{try(var s=db.connection().createStatement()){
   s.execute("INSERT INTO documents(id,section,body) SELECT 'large-'||n,section,jsonb_build_object('id','large-'||n,'section',section,'title',CASE WHEN n%503=0 THEN 'Needle ' ELSE 'Entry ' END||n,'description',repeat('description ',30),'owner','owner','author','Owner','status','open','createdAt',1800000000000::bigint,'startsAt',1800000000000::bigint+n*1000::bigint,'endsAt',1900000000000::bigint,'capacity',20,'visibility','public','type','test') FROM (SELECT n,(ARRAY['groups','events','board'])[1+n%3] section FROM generate_series(1,6000) n) fixture");
   s.execute("INSERT INTO community_relations(document,kind,actor,value) SELECT id,CASE section WHEN 'groups' THEN 'members' WHEN 'events' THEN 'participants' ELSE 'responses' END,CASE WHEN m=1 THEN 'reader' ELSE 'member-'||m END,to_jsonb('Member '||m) FROM documents CROSS JOIN generate_series(1,16) m");
   s.execute("ANALYZE documents");s.execute("ANALYZE community_relations");
  }return null;});
  var actor=new CommunityStore.Actor("reader","Reader",false,false);var out=new JsonArray();
  for(String section:List.of("groups","events","board","home"))for(String search:List.of("","Needle")){
   var q=new JsonObject();q.addProperty("op","list");q.addProperty("section",section);q.addProperty("query",search);
   var baseline=db.transaction(()->new CommunityQueries(db).list(actor,q,section,now,CommunityStore.defaults()));assertFalse(baseline.isEmpty());
   var times=new long[5];for(int i=0;i<times.length;i++){long start=System.nanoTime();var rows=db.transaction(()->new CommunityQueries(db).list(actor,q,section,now,CommunityStore.defaults()));times[i]=System.nanoTime()-start;assertEquals(baseline,rows);}
   Arrays.sort(times);var row=new JsonObject();row.addProperty("section",section);row.addProperty("search",search);row.addProperty("medianMs",times[2]/1e6);row.addProperty("maxMs",times[4]/1e6);row.addProperty("digest",Hashes.sha256(baseline.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));out.add(row);
  }
  Files.writeString(Path.of(System.getenv("RIVET_LARGE_READ_OUTPUT")),Json.GSON.toJson(out));System.out.println("RIVET_LARGE_READ_OK documents=6000 relations=96000 scenarios=8");
 }
}
