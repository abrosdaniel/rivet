package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Backend application publisher; ownership comes exclusively from the authenticated key. */
public final class NotificationRest implements RestApi.Extension,AutoCloseable {
    private static final String PREFIX="/api/v1/notifications/";
    private final ApiKeys keys;
    private final NotificationPublisher publisher;
    private final RestApi.Extension delegate;
    private final Map<String,NotificationsApi> owners=new HashMap<>();
    private final Map<String,NotificationsApi.Type> types=new HashMap<>();
    private boolean closed;
    public NotificationRest(ApiKeys keys,NotificationPublisher publisher,RestApi.Extension delegate){this.keys=keys;this.publisher=publisher;this.delegate=delegate;}
    @Override public Set<String> capabilities(){var result=new HashSet<>(delegate.capabilities());result.add("rest.notifications.publish");result.add("rest.notifications.revoke");return Set.copyOf(result);}
    @Override public JsonObject handle(RestApi.Request request,String path,Map<String,String> query)throws Exception {
        if(!path.startsWith(PREFIX))return delegate.handle(request,path,query);
        if(request.header("origin")!=null)throw new ApiException(FORBIDDEN,"Backend credentials only");
        if(!query.isEmpty())throw ApiJson.invalid();
        String scope=switch(request.method()){case "PUT"->"notifications.publish";case "DELETE"->"notifications.revoke";default->throw ApiJson.invalid();};
        String bearer=request.header("authorization");
        if(bearer==null||!bearer.startsWith("Bearer ")||bearer.length()>128)throw new ApiException(UNAUTHENTICATED,"Application key required");
        var grant=keys.authorize(bearer.substring(7),scope);
        String[] parts=path.substring(PREFIX.length()).split("/",-1);
        if(parts.length!=3||!parts[0].matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}")||!parts[2].matches("[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}"))throw ApiJson.invalid();
        UUID recipient;long revision;
        try {
            recipient=UUID.fromString(parts[1]);if(!recipient.toString().equals(parts[1]))throw ApiJson.invalid();
            String match=request.header("if-match");if(match==null||!match.matches("\"(?:0|[1-9][0-9]{0,18})\""))throw ApiJson.invalid();
            revision=Long.parseLong(match.substring(1,match.length()-1));
        }catch(IllegalArgumentException invalid){throw ApiJson.invalid();}
        NotificationsApi.Message message=null;
        if(request.method().equals("PUT"))message=message(ApiJson.object(request.body()));
        else if(!request.body().isEmpty()||revision==0)throw ApiJson.invalid();
        var type=type(grant.application(),parts[0]);
        Callable<Void> authorization=()->{keys.revalidateForWrite(grant,scope);return null;};
        CompletableFuture<?> result=message==null?publisher.revokeAuthorized(type,recipient,parts[2],revision,authorization)
            :publisher.putAuthorized(type,recipient,parts[2],message,revision,authorization);
        try {
            var receipt=result.get(5,TimeUnit.SECONDS);var out=new JsonObject();
            if(receipt instanceof NotificationsApi.Receipt value){out.addProperty("id",value.id());out.addProperty("revision",value.revision());}
            else out.addProperty("revoked",true);
            return out;
        }catch(ExecutionException failure){if(failure.getCause() instanceof ApiException api)throw api;throw new ApiException(UNAVAILABLE,"Notification publication unavailable");}
        catch(TimeoutException timeout){result.cancel(false);throw new ApiException(BUSY,"Notification publication timed out; retry with the same key and revision");}
        catch(InterruptedException interrupted){result.cancel(false);Thread.currentThread().interrupt();throw new ApiException(UNAVAILABLE,"Notification publication interrupted");}
    }
    private synchronized NotificationsApi.Type type(String application,String local){
        if(closed)throw new ApiException(UNAVAILABLE,"Notification publisher stopped");
        String owner="api_"+Hashes.sha256(application.getBytes(StandardCharsets.UTF_8)).substring(0,56),id=owner+":"+local;
        var type=types.get(id);if(type!=null)return type;
        var lease=owners.get(owner);boolean created=lease==null;if(created)lease=publisher.owner(owner);
        try{type=lease.register(local);owners.put(owner,lease);types.put(id,type);return type;}
        catch(RuntimeException failure){if(created)lease.close();throw failure;}
    }
    private static NotificationsApi.Message message(JsonObject value){
        if(!Set.of("title","body","expiresAt","translations","action").containsAll(value.keySet()))throw ApiJson.invalid();
        String title=ApiJson.string(value,"title"),body=ApiJson.string(value,"body");long expires=0;
        try{if(value.has("expiresAt")){if(!value.get("expiresAt").isJsonPrimitive()||!value.getAsJsonPrimitive("expiresAt").isNumber())throw ApiJson.invalid();expires=value.get("expiresAt").getAsBigDecimal().longValueExact();}}catch(ArithmeticException invalid){throw ApiJson.invalid();}
        var translations=new HashMap<String,NotificationsApi.Translation>();
        if(value.has("translations")){
            if(!value.get("translations").isJsonObject()||value.getAsJsonObject("translations").size()>8)throw ApiJson.invalid();
            for(var entry:value.getAsJsonObject("translations").entrySet()){
                if(!entry.getValue().isJsonObject())throw ApiJson.invalid();var text=entry.getValue().getAsJsonObject();ApiJson.fields(text,"title","body");
                translations.put(entry.getKey(),new NotificationsApi.Translation(ApiJson.string(text,"title"),ApiJson.string(text,"body")));
            }
        }
        NotificationsApi.Action action=null;
        if(value.has("action")){
            if(!value.get("action").isJsonObject())throw ApiJson.invalid();var target=value.getAsJsonObject("action");ApiJson.fields(target,"type","id");
            UUID id;try{id=UUID.fromString(ApiJson.string(target,"id"));}catch(IllegalArgumentException invalid){throw ApiJson.invalid();}
            action=switch(ApiJson.string(target,"type")){case "openGroup"->new NotificationsApi.OpenGroup(id);case "openReport"->new NotificationsApi.OpenReport(id);case "openOwnPublication"->new NotificationsApi.OpenOwnPublication(id);default->throw ApiJson.invalid();};
        }
        return new NotificationsApi.Message(title,body,translations,expires,action);
    }
    @Override public synchronized void close(){closed=true;owners.values().forEach(NotificationsApi::close);owners.clear();types.clear();}
}
