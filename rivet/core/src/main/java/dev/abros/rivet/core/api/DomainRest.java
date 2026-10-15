package dev.abros.rivet.core.api;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.*;
import java.io.StringReader;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Backend-only domain facade; builds its own requests and never accepts menu operations or actor UUIDs. */
public final class DomainRest implements RestApi.Extension {
    public static final Set<String> DOMAINS=Set.of("groups","events","board","polls","ideas");
    private final CommunityApiResources resources;
    private CommunityApiCommands commands;
    public DomainRest commands(CommunityApiCommands commands){this.commands=commands;return this;}
    private final ApiKeys keys;private final PlayerSessions sessions;private final CommunityStore store;
    public DomainRest(ApiKeys keys,PlayerSessions sessions,CommunityStore store){this(keys,sessions,store,null);}
    public DomainRest(ApiKeys keys,PlayerSessions sessions,CommunityStore store,CommunityApiResources resources){this.keys=keys;this.sessions=sessions;this.store=store;this.resources=resources;}
    @Override public Set<String> capabilities(){var result=new HashSet<String>();result.add("rest.sessions");if(resources!=null)result.addAll(resources.capabilities());for(String domain:DOMAINS)if(store.modules().enabled(domain)&&store.configuration().getAsJsonArray("sections").contains(new JsonPrimitive(domain)))result.add("rest."+domain+".read");if(commands!=null)for(String capability:new HashSet<>(result))if(capability.endsWith(".read")&&!Set.of("rest.players.presence.read","rest.map.positions.read","rest.map.waystones.read","rest.statistics.read","rest.skins.read","rest.pack.read").contains(capability))result.add(capability.replace(".read",".write"));return Set.copyOf(result);}
    @Override public JsonObject handle(RestApi.Request request,String path,Map<String,String> query)throws Exception{
        return handle(request,path,query,null);
    }
    JsonObject subscription(ApiKeys.Grant grant,String path,Map<String,String> query,String token)throws Exception{
        String scope=CommunityApiResources.scope(path);
        if(scope==null&&DOMAINS.contains(path.split("/",-1)[0]))scope=path.split("/",-1)[0]+".read";
        if(scope==null||query.containsKey("cursor"))throw ApiJson.invalid();
        keys.revalidate(grant,scope);
        var headers=new HashMap<String,List<String>>();headers.put("authorization",List.of("Bearer subscription"));
        if(token!=null)headers.put("x-rivet-session",List.of(token));
        return handle(new RestApi.Request("GET","/api/v1/"+path,headers,""),"/api/v1/"+path,query,grant);
    }
    private ApiKeys.Grant authorize(String secret,String scope,ApiKeys.Grant subscription)throws Exception{
        if(subscription==null)return keys.authorize(secret,scope);
        keys.revalidate(subscription,scope);return subscription;
    }
    private JsonObject handle(RestApi.Request request,String path,Map<String,String> query,ApiKeys.Grant subscription)throws Exception{
        String relative=path.startsWith("/api/v1/")?path.substring(8):"";
        String[] parts=relative.split("/",-1);boolean session=relative.startsWith("sessions/");
        String resourceScope=CommunityApiResources.scope(relative);
        String writeScope=CommunityApiCommands.scope(relative);
        if(!session&&resourceScope==null&&writeScope==null&&!DOMAINS.contains(parts[0]))return null;
        if(request.header("origin")!=null)throw new ApiException(FORBIDDEN,"Backend credentials only");
        String header=request.header("authorization");if(header==null||!header.startsWith("Bearer ")||header.length()>128)throw new ApiException(UNAUTHENTICATED,"Application key required");
        if(!session&&!request.method().equals("GET")&&writeScope!=null){
            if(commands==null)throw new ApiException(MODULE_DISABLED,"Commands unavailable");
            if(!query.isEmpty())throw new ApiException(INVALID_ARGUMENT,"Command query prohibited");
            var grant=keys.authorize(header.substring(7),writeScope);String token=request.header("x-rivet-session");
            var player=sessions.validate(grant,token);var actor=new CommunityStore.Actor(player.uuid(),player.name(),false,false);
            JsonObject result;
            try{result=commands.execute(actor,grant.application(),request.method(),relative,request.header("if-match"),ApiJson.object(request.body()));}
            catch(CommunityFailure e){throw new ApiException(switch(e.code()){case NOT_FOUND->NOT_FOUND;case FORBIDDEN->FORBIDDEN;case CONFLICT->CONFLICT;case UNAVAILABLE->UNAVAILABLE;default->INVALID_ARGUMENT;},"Command rejected");}catch(ArithmeticException|IllegalStateException invalid){throw ApiJson.invalid();}
            keys.revalidate(grant,writeScope);sessions.validate(grant,token);return result;
        }
        if(resourceScope!=null){
            if(resources==null)throw new ApiException(MODULE_DISABLED,"Resources unavailable");
            if(!request.method().equals("GET")||!request.body().isEmpty())throw new ApiException(INVALID_ARGUMENT,"Read only resource");
            var grant=authorize(header.substring(7),resourceScope,subscription);String token=request.header("x-rivet-session");
            if(token!=null&&token.length()!=43||token==null&&(!Set.of("map.read","pack.read").contains(resourceScope)||relative.startsWith("me/")))throw new ApiException(UNAUTHENTICATED,"Player session required");
            var player=token==null?null:sessions.validate(grant,token);
            var actor=new CommunityStore.Actor(player==null?"__api_public__":player.uuid(),player==null?"":player.name(),false,false);
            JsonObject result;
            try{result=resources.read(actor,relative,query);}catch(CommunityFailure e){throw new ApiException(switch(e.code()){case NOT_FOUND->NOT_FOUND;case FORBIDDEN->FORBIDDEN;case CONFLICT->CONFLICT;case UNAVAILABLE->UNAVAILABLE;default->INVALID_ARGUMENT;},"Resource unavailable");}
            keys.revalidate(grant,resourceScope);if(token!=null)sessions.validate(grant,token);return result;
        }
        if(session){
            if(!query.isEmpty()||!request.method().equals("POST"))throw new ApiException(INVALID_ARGUMENT,"POST without query required");
            String scope=switch(relative){case "sessions/password"->"sessions.password";case "sessions/link","sessions/exchange"->"sessions.link";default->throw new ApiException(NOT_FOUND,"Unknown session route");};
            var grant=keys.authorize(header.substring(7),scope);var body=body(request.body());var result=new JsonObject();
            switch(relative){
                case "sessions/password"->{fields(body,"login","password");issued(result,sessions.login(grant,body.get("login"),body.get("password").toCharArray()));}
                case "sessions/link"->{fields(body);var c=sessions.challenge(grant);result.addProperty("code",c.code());result.addProperty("pollToken",c.pollToken());result.addProperty("expiresAtEpochMillis",c.expires());result.addProperty("application",c.application());}
                case "sessions/exchange"->{fields(body,"code","pollToken");var value=sessions.exchange(grant,body.get("code"),body.get("pollToken"));result.addProperty("pending",value==null);if(value!=null)issued(result,value);}
            }
            keys.revalidate(grant,scope);return result;
        }
        String domain=parts[0];if(!request.method().equals("GET")||!request.body().isEmpty()||parts.length>2)throw new ApiException(INVALID_ARGUMENT,"Read-only domain");
        if(!Set.of("cursor").containsAll(query.keySet())||parts.length==2&&!query.isEmpty())throw new ApiException(INVALID_ARGUMENT,"Invalid filters");
        if(!store.modules().enabled(domain)||!store.configuration().getAsJsonArray("sections").contains(new JsonPrimitive(domain)))throw new ApiException(MODULE_DISABLED,"Domain disabled");
        var grant=authorize(header.substring(7),domain+".read",subscription);String token=request.header("x-rivet-session");
        if(token!=null&&(token.length()!=43))throw new ApiException(UNAUTHENTICATED,"Invalid session");
        var player=token==null?null:sessions.validate(grant,token);
        var actor=new CommunityStore.Actor(player==null?"__api_public__":player.uuid(),player==null?"":player.name(),false,false);
        var input=new JsonObject();input.addProperty("section",domain);input.addProperty("op",parts.length==1?"list":"detail");
        if(parts.length==2){if(!parts[1].matches("[A-Za-z0-9_-]{1,80}"))throw new ApiException(INVALID_ARGUMENT,"Invalid ID");input.addProperty("id",parts[1]);}
        if(query.containsKey("cursor"))input.addProperty("cursor",query.get("cursor"));
        JsonObject source;
        try{source=store.apiRead(actor,domain,parts.length==2?parts[1]:"",query.getOrDefault("cursor",""));}catch(CommunityFailure e){throw new ApiException(switch(e.code()){case NOT_FOUND->NOT_FOUND;case FORBIDDEN->FORBIDDEN;case CONFLICT->CONFLICT;case UNAVAILABLE->UNAVAILABLE;default->INVALID_ARGUMENT;},"Domain request rejected");}
        var result=new JsonObject();
        if(parts.length==1){var entries=new JsonArray();for(var e:source.getAsJsonArray("entries"))entries.add(project(e.getAsJsonObject()));result.add("items",entries);result.addProperty("nextCursor",Json.opt(source,"nextCursor",""));}
        else result.add("item",project(source.getAsJsonObject("detail")));
        keys.revalidate(grant,domain+".read");if(token!=null)sessions.validate(grant,token);return result;
    }
    private static JsonObject project(JsonObject source){
        var result=new JsonObject();for(String field:List.of("id","title","description","preview","status","revision","createdAt","startsAt","endsAt","type","recruiting","capacity"))if(source.has(field))result.add(field,source.get(field).deepCopy());return result;
    }
    private static void issued(JsonObject result,PlayerSessions.Issued value){result.addProperty("session",value.token());result.addProperty("expiresAtEpochMillis",value.expires());var player=new JsonObject();player.addProperty("uuid",value.player().uuid());player.addProperty("name",value.player().name());result.add("player",player);}
    private static void fields(Map<String,String> value,String... names){if(!value.keySet().equals(Set.of(names)))throw new ApiException(INVALID_ARGUMENT,"Invalid fields");}
    private static Map<String,String> body(String raw)throws Exception{
        if(raw.length()>4096)throw new ApiException(INVALID_ARGUMENT,"Body too large");
        var result=new HashMap<String,String>();try(var reader=new JsonReader(new StringReader(raw))){reader.setLenient(false);reader.beginObject();while(reader.hasNext()){String name=reader.nextName();if(reader.peek()!=JsonToken.STRING||result.putIfAbsent(name,reader.nextString())!=null)throw new ApiException(INVALID_ARGUMENT,"Invalid JSON fields");}reader.endObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new ApiException(INVALID_ARGUMENT,"Trailing JSON");}catch(java.io.IOException|IllegalStateException invalid){throw new ApiException(INVALID_ARGUMENT,"Malformed JSON");}return result;
    }
}
