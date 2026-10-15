package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import com.google.gson.JsonParser;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestApiTest {
    static RestSettings settings(Set<String> resources){return new RestSettings(true,"127.0.0.1",24464,16,16,2,8,2,1000,1000,Set.of("https://site.example"),resources);}
    static StatusFeed feed(){return new StatusFeed(2,Clock.systemUTC(),new ServerStatus(0,100,false,0,Set.of("server.status","server.status.events")));}
    static final class Auth implements RestApi.Authorization{
        boolean revoked;boolean onlyRead;final AtomicInteger calls=new AtomicInteger();
        public Object authorize(String token,String scope){calls.incrementAndGet();if(!token.equals("secret")||revoked)throw new ApiException(ApiException.Code.UNAUTHENTICATED,"do not echo secret");if(onlyRead&&!scope.equals("server.status.read"))throw new ApiException(ApiException.Code.FORBIDDEN,"wrong scope");return this;}
        public void revalidate(Object grant,String scope){if(revoked)throw new ApiException(ApiException.Code.UNAUTHENTICATED,"secret");}
    }
    static RestApi.Request request(String method,String path,String... pairs){var h=new HashMap<String,List<String>>();for(int i=0;i<pairs.length;i+=2)h.computeIfAbsent(pairs[i],x->new ArrayList<>()).add(pairs[i+1]);return new RestApi.Request(method,path,h);}
    static String code(RestApi.Response r){return JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("error").get("code").getAsString();}
    @Test void syntheticRequestsCannotBypassFramingThroughExtensions(){
        try(var feed=feed()){
            var calls=new AtomicInteger();var api=new RestApi(feed,new Auth(),settings(Set.of()),(r,p,q)->{calls.incrementAndGet();return new com.google.gson.JsonObject();});
            for(String path:List.of("/api/v1/server/status","/api/v1/events")){
                assertEquals(400,api.handle(new RestApi.Request("GET",path,Map.of(),"{}")).status());
                assertEquals(400,api.handle(request("GET",path,"Content-Length","0","content-length","0")).status());
                assertEquals(400,api.handle(request("GET",path,"Transfer-Encoding","chunked")).status());
                assertEquals(400,api.handle(request("GET",path,"X-Rivet-Session","one","x-rivet-session","two")).status());
            }
            assertEquals(0,calls.get());
        }
    }
    @Test void privateByDefaultAndErrorsNeverEchoCredentials(){
        var auth=new Auth();try(var feed=feed()){
            var api=new RestApi(feed,auth,settings(Set.of()));
            assertEquals(401,api.handle(request("GET","/api/v1/server/status")).status());
            var invalid=api.handle(request("GET","/api/v1/server/status","Authorization","Bearer wrong-secret"));
            assertEquals(401,invalid.status());assertFalse(invalid.body().contains("secret"));assertTrue(invalid.headers().containsKey("WWW-Authenticate"));
            assertEquals(400,api.handle(request("GET","/api/v1/server/status?api_key=secret")).status());
            assertEquals(400,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret","authorization","Bearer secret")).status());
        }
    }
    @Test void authenticationPrecedesEtagAndResponseFieldsAreExplicit(){
        var auth=new Auth();try(var feed=feed()){
            var api=new RestApi(feed,auth,settings(Set.of()));
            var response=api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret"));assertEquals(200,response.status());
            var body=JsonParser.parseString(response.body()).getAsJsonObject();
            assertEquals(Set.of("cursor","capturedAtEpochMillis","status"),body.keySet());
            assertEquals(Set.of("onlinePlayers","maximumPlayers","maintenance","shutdownAtEpochMillis","capabilities"),body.getAsJsonObject("status").keySet());
            String etag=response.headers().get("ETag");assertEquals(304,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret","If-None-Match",etag)).status());
            auth.revoked=true;assertEquals(401,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret","If-None-Match",etag)).status());
            assertEquals(3,auth.calls.get());assertEquals("private, no-cache",response.headers().get("Cache-Control"));
        }
    }
    @Test void publicResourceDoesNotMakeOtherResourcesPublicOrIgnoreInvalidKey(){
        var auth=new Auth();try(var feed=feed()){
            var api=new RestApi(feed,auth,settings(Set.of("server.status")));
            assertEquals(200,api.handle(request("GET","/api/v1/server/status")).status());
            assertEquals(401,api.handle(request("GET","/api/v1/capabilities")).status());
            assertEquals(401,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer bad")).status());
        }
    }
    @Test void corsOnlyAllowsExactOriginForExplicitPublicResources(){
        try(var feed=feed()){
            var api=new RestApi(feed,new Auth(),settings(Set.of("server.status")));
            var ok=api.handle(request("GET","/api/v1/server/status","Origin","https://site.example"));assertEquals(200,ok.status());assertEquals("https://site.example",ok.headers().get("Access-Control-Allow-Origin"));
            assertEquals(403,api.handle(request("GET","/api/v1/server/status","Origin","https://site.example.evil")).status());
            assertEquals(403,api.handle(request("GET","/api/v1/server/status","Origin","https://site.example","Authorization","Bearer secret")).status());
            assertEquals(204,api.handle(request("OPTIONS","/api/v1/server/status","Origin","https://site.example","Access-Control-Request-Method","GET","Access-Control-Request-Headers","if-none-match")).status());
            assertEquals(403,api.handle(request("OPTIONS","/api/v1/server/status","Origin","https://site.example","Access-Control-Request-Method","GET","Access-Control-Request-Headers","Authorization")).status());
            assertEquals(403,api.handle(request("OPTIONS","/api/v1/capabilities","Origin","https://site.example","Access-Control-Request-Method","GET")).status());
        }
    }
    @Test void replayPaginatesAndExpiredCursorExplicitlyRequiresResync(){
        var auth=new Auth();try(var feed=feed()){
            var api=new RestApi(feed,auth,settings(Set.of()));var cursor=feed.serverStatus().cursor();
            feed.publish(new ServerStatus(1,100,false,0,Set.of()));feed.publish(new ServerStatus(2,100,false,0,Set.of()));
            String url="/api/v1/server/status/events?stream="+cursor.stream()+"&after=0&limit=1";
            var response=api.handle(request("GET",url,"Authorization","Bearer secret"));assertEquals(200,response.status());
            var body=JsonParser.parseString(response.body()).getAsJsonObject();assertEquals(1,body.getAsJsonArray("changes").size());assertTrue(body.get("hasMore").getAsBoolean());
            auth.onlyRead=true;assertEquals(403,api.handle(request("GET",url,"Authorization","Bearer secret")).status());auth.onlyRead=false;
            feed.publish(new ServerStatus(3,100,false,0,Set.of()));var old=api.handle(request("GET",url,"Authorization","Bearer secret"));assertEquals(409,old.status());assertEquals("RESYNC_REQUIRED",code(old));
            assertEquals(400,api.handle(request("GET",url+"&limit=2","Authorization","Bearer secret")).status());
        }
    }
    @Test void noWritesKeysRoutesOrPrivateDiagnostics(){
        try(var feed=feed()){
            var api=new RestApi(feed,new Auth(),settings(RestSettings.RESOURCES));
            assertEquals(405,api.handle(request("POST","/api/v1/server/status")).status());
            assertEquals(404,api.handle(request("GET","/api/v1/keys")).status());
            assertEquals(400,api.handle(request("GET","/api/v1/server/status%3fapi_key=secret")).status());
            assertEquals(400,api.handle(request("GET","/api/v1/server/status/events?stream=x&after=-1","Authorization","Bearer secret")).status());
            assertEquals(200,api.handle(request("GET","/api/v1/openapi.json")).status());
            feed.close();assertEquals(503,api.handle(request("GET","/api/v1/capabilities")).status());
        }
    }
    @Test void revalidationRejectsRevocationDuringRead(){
        var auth=new Auth();var delegate=feed();
        RivetApi revoker=new RivetApi(){public StatusSnapshot serverStatus(){auth.revoked=true;return delegate.serverStatus();}public StatusEvents serverStatusEvents(ApiCursor after,int limit){throw new AssertionError();}};
        var api=new RestApi(revoker,auth,settings(Set.of()));assertEquals(401,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret")).status());delegate.close();
    }
    @Test void ingressIsBoundedAndUsesMonotonicTime(){
        var time=new java.util.concurrent.atomic.AtomicLong();var budget=new RestIngress(3,2,time::get);
        assertTrue(budget.take("a"));assertTrue(budget.take("a"));assertFalse(budget.take("a"));assertTrue(budget.take("b"));assertFalse(budget.take("c"));
        time.set(60_000_000_000L);assertTrue(budget.take("a"));
    }
}
