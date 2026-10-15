package dev.abros.rivet.core.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.Hashes;
import dev.abros.rivet.core.Json;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared HTTP and backend WebSocket request contract. No request JSON is forwarded to game/menu handlers. */
public final class RestApi {
    public interface Authorization {
        Object authorize(String token,String scope)throws Exception;
        void revalidate(Object grant,String scope)throws Exception;
    }
    public static Authorization authorization(ApiKeys keys){return new Authorization(){
        public Object authorize(String token,String scope)throws Exception{return keys.authorize(token,scope);}
        public void revalidate(Object grant,String scope)throws Exception{keys.revalidate((ApiKeys.Grant)grant,scope);}
    };}
    public interface Extension { JsonObject handle(Request request,String path,Map<String,String> query)throws Exception; default Set<String> capabilities(){return Set.of();} default Response binary(Request request,String path,Map<String,String> query)throws Exception{return null;} }
    public record Request(String method,String target,Map<String,List<String>> headers,String body) {
        public Request(String method,String target,Map<String,List<String>> headers){this(method,target,headers,"");}
        public Request {
            var copy=new HashMap<String,List<String>>();
            headers.forEach((key,value)->copy.merge(key.toLowerCase(Locale.ROOT),List.copyOf(value),(a,b)->{var merged=new ArrayList<>(a);merged.addAll(b);return List.copyOf(merged);}));
            headers=Map.copyOf(copy);
        }
        String header(String name){var values=headers.getOrDefault(name,List.of());if(values.size()>1)throw new ApiException(ApiException.Code.INVALID_ARGUMENT,"Duplicate singleton header");return values.isEmpty()?null:values.getFirst();}
    }
    public record Response(int status,Map<String,String> headers,String body,byte[] binary) {
        public Response(int status,Map<String,String> headers,String body){this(status,headers,body,null);}
        public Response{headers=Map.copyOf(headers);binary=binary==null?null:binary.clone();}
        @Override public byte[] binary(){return binary==null?null:binary.clone();}
        public byte[] payload(){return binary==null?body.getBytes(StandardCharsets.UTF_8):binary.clone();}
    }
    private record Route(String resource,String scope) {}
    private static final Map<String,Route> ROUTES=Map.of(
        "/api/v1/capabilities",new Route("capabilities","server.status.read"),
        "/api/v1/server/status",new Route("server.status","server.status.read"),
        "/api/v1/server/status/events",new Route("server.status.events","server.status.subscribe"),
        "/api/v1/openapi.json",new Route("openapi","server.status.read"));
    private final RivetApi api;
    private final Authorization auth;
    private final RestSettings settings;
    private final String schema;
    private final Extension extension;
    public RestApi(RivetApi api,Authorization auth,RestSettings settings){
        this(api,auth,settings,(request,path,query)->null);
    }
    public RestApi(RivetApi api,Authorization auth,RestSettings settings,Extension extension){
        this.extension=extension;this.api=Objects.requireNonNull(api);this.auth=Objects.requireNonNull(auth);this.settings=Objects.requireNonNull(settings);
        try(var input=RestApi.class.getResourceAsStream("/rivet/api/openapi.json")){
            schema=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        }catch(java.io.IOException failure){throw new IllegalStateException("Missing OpenAPI contract",failure);}
    }
    public Response handle(Request request){
        String id=UUID.randomUUID().toString();var headers=base(id);
        try{
            // Validate before extensions too: synthetic WebSocket requests bypass HTTP framing.
            for(String header:List.of("host","content-length","authorization","origin","x-rivet-session","if-match","if-none-match"))request.header(header);
            if(request.header("transfer-encoding")!=null)return error(400,"INVALID_ARGUMENT",headers);
            if(Set.of("GET","DELETE","OPTIONS").contains(request.method())&&(!request.body().isEmpty()||request.header("content-length")!=null&&!request.header("content-length").equals("0")))return error(400,"INVALID_ARGUMENT",headers);
            String target=request.target();
            if(target==null||target.length()>2048||!target.startsWith("/")||target.indexOf('#')>=0||target.indexOf('%')>=0||target.chars().anyMatch(c->c<=32||c>=127))
                return error(400,"INVALID_ARGUMENT",headers);
            int queryAt=target.indexOf('?');String path=queryAt<0?target:target.substring(0,queryAt);
            String query=queryAt<0?"":target.substring(queryAt+1);
            var params=query(query);
            if(!ROUTES.containsKey(path)){var binary=extension.binary(request,path,params);if(binary!=null){headers.putAll(binary.headers());return new Response(binary.status(),headers,binary.body(),binary.binary());}var extra=extension.handle(request,path,params);if(extra!=null)return new Response(200,headers,Json.GSON.toJson(extra));}
            Route route=ROUTES.get(path);if(route==null)return error(404,"NOT_FOUND",headers);
            if(!route.resource.equals("server.status.events")&&!params.isEmpty())return error(400,"INVALID_ARGUMENT",headers);
            if(!Set.of("stream","after","limit").containsAll(params.keySet()))return error(400,"INVALID_ARGUMENT",headers);
            String origin=request.header("origin"),authorization=request.header("authorization");
            // HTTP framing and Host duplicates are rejected even when called without the socket adapter.
            request.header("host");request.header("content-length");
            if(origin!=null){
                if(!settings.origins().contains(origin))return error(403,"FORBIDDEN",headers);
                headers.put("Access-Control-Allow-Origin",origin);
                // A permanent application credential is never a browser session.
                if(authorization!=null)return error(403,"FORBIDDEN",headers);
            }
            if(request.method().equals("OPTIONS")){
                if(origin==null||!"GET".equals(request.header("access-control-request-method")))return error(403,"FORBIDDEN",headers);
                String requested=request.header("access-control-request-headers");
                if(requested!=null)for(String header:requested.split(","))if(!header.trim().equalsIgnoreCase("if-none-match"))return error(403,"FORBIDDEN",headers);
                if(!settings.publicResources().contains(route.resource))return error(403,"FORBIDDEN",headers);
                headers.put("Access-Control-Allow-Methods","GET, OPTIONS");headers.put("Access-Control-Allow-Headers","If-None-Match");
                return new Response(204,headers,"");
            }
            if(!request.method().equals("GET")){headers.put("Allow","GET, OPTIONS");return error(405,"METHOD_NOT_ALLOWED",headers);}
            if(request.header("transfer-encoding")!=null||request.header("content-length")!=null&&!request.header("content-length").equals("0"))return error(400,"INVALID_ARGUMENT",headers);
            Object grant=null;
            if(authorization!=null){
                if(!authorization.startsWith("Bearer ")||authorization.length()>128)return error(401,"UNAUTHENTICATED",headers);
                grant=auth.authorize(authorization.substring(7),route.scope);
            }else if(!settings.publicResources().contains(route.resource))return error(401,"UNAUTHENTICATED",headers);
            String body;
            if(route.resource.equals("server.status.events")){
                if(!params.containsKey("stream")||!params.containsKey("after"))return error(400,"INVALID_ARGUMENT",headers);
                var stream=UUID.fromString(params.get("stream"));if(!stream.toString().equals(params.get("stream")))return error(400,"INVALID_ARGUMENT",headers);
                var cursor=new ApiCursor(stream,Long.parseLong(params.get("after")));
                int limit=Integer.parseInt(params.getOrDefault("limit","64"));
                var events=api.serverStatusEvents(cursor,limit);var data=new JsonObject();var changes=new JsonArray();
                events.changes().forEach(change->changes.add(snapshot(change)));data.add("changes",changes);
                data.add("nextCursor",cursor(events.nextCursor()));data.addProperty("hasMore",events.hasMore());body=Json.GSON.toJson(data);
            }else{
                var current=api.serverStatus(); // Also rejects a stopped server for schema/capability requests.
                body=switch(route.resource){
                    case "server.status" -> Json.GSON.toJson(snapshot(current));
                    case "capabilities" -> {var data=new JsonObject();data.addProperty("apiVersion",RivetApi.MAJOR_VERSION);var capabilities=new JsonArray();var available=new TreeSet<>(current.status().capabilities());available.addAll(extension.capabilities());available.forEach(capabilities::add);data.add("capabilities",capabilities);yield Json.GSON.toJson(data);}
                    case "openapi" -> schema;
                    default -> throw new IllegalStateException("Unknown route");
                };
            }
            if(grant!=null)auth.revalidate(grant,route.scope);
            // Authenticate even a conditional GET. Revocation must never be bypassed by an ETag.
            String etag="\""+Hashes.sha256(body.getBytes(StandardCharsets.UTF_8))+"\"";
            headers.put("ETag",etag);headers.put("Cache-Control","private, no-cache");
            String condition=request.header("if-none-match");
            if(condition!=null){if(condition.length()>1024)return error(400,"INVALID_ARGUMENT",headers);
                for(String tag:condition.split(",")){tag=tag.trim();if(tag.equals("*")||tag.equals(etag)||tag.equals("W/"+etag))return new Response(304,headers,"");}}
            return new Response(200,headers,body);
        }catch(ApiException failure){
            int status=switch(failure.code()){
                case UNAUTHENTICATED -> 401;case FORBIDDEN -> 403;case NOT_FOUND -> 404;
                case INVALID_ARGUMENT -> 400;case CONFLICT,RESYNC_REQUIRED -> 409;case RATE_LIMITED -> 429;
                case UNAVAILABLE,MODULE_DISABLED,BUSY -> 503;
            };return error(status,failure.code().name(),headers);
        }catch(IllegalArgumentException invalid){return error(400,"INVALID_ARGUMENT",headers);}
        catch(Exception failure){return error(503,"UNAVAILABLE",headers);}
    }
    private static Map<String,String> query(String raw){
        var result=new HashMap<String,String>();if(raw.isEmpty())return result;
        for(String pair:raw.split("&",-1)){int i=pair.indexOf('=');if(i<=0||i==pair.length()-1||result.putIfAbsent(pair.substring(0,i),pair.substring(i+1))!=null)throw new IllegalArgumentException("Invalid query");}
        return result;
    }
    private static JsonObject cursor(ApiCursor cursor){var j=new JsonObject();j.addProperty("stream",cursor.stream().toString());j.addProperty("sequence",cursor.sequence());return j;}
    static JsonObject snapshot(StatusSnapshot snapshot){
        var j=new JsonObject();j.add("cursor",cursor(snapshot.cursor()));j.addProperty("capturedAtEpochMillis",snapshot.capturedAtEpochMillis());
        var data=new JsonObject();var status=snapshot.status();data.addProperty("onlinePlayers",status.onlinePlayers());data.addProperty("maximumPlayers",status.maximumPlayers());
        data.addProperty("maintenance",status.maintenance());data.addProperty("shutdownAtEpochMillis",status.shutdownAtEpochMillis());
        var capabilities=new JsonArray();new TreeSet<>(status.capabilities()).forEach(capabilities::add);data.add("capabilities",capabilities);j.add("status",data);return j;
    }
    private static HashMap<String,String> base(String id){var h=new HashMap<String,String>();h.put("Content-Type","application/json; charset=utf-8");h.put("Cache-Control","no-store");h.put("X-Content-Type-Options","nosniff");h.put("X-Request-ID",id);h.put("Vary","Origin, Authorization");h.put("Access-Control-Expose-Headers","ETag, X-Request-ID, Retry-After");return h;}
    public static Response error(int status,String code){return error(status,code,base(UUID.randomUUID().toString()));}
    private static Response error(int status,String code,Map<String,String> headers){
        headers.put("Cache-Control","no-store");headers.remove("ETag");
        if(status==401)headers.put("WWW-Authenticate","Bearer realm=\"Rivet\"");
        if(status==429||status==503)headers.put("Retry-After",status==429?"60":"1");
        var value=new JsonObject();value.addProperty("code",code);value.addProperty("requestId",headers.get("X-Request-ID"));var root=new JsonObject();root.add("error",value);
        return new Response(status,headers,Json.GSON.toJson(root));
    }
}
