package dev.abros.rivet.core.api;
import com.google.gson.JsonObject;
import dev.abros.rivet.api.ApiException;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Backend-only issuance: browser requests carrying permanent credentials are refused. */
public final class BrowserTicketRest implements RestApi.Extension {
 private final ApiKeys keys;private final BrowserTickets tickets;private final RestApi.Extension delegate;
 public BrowserTicketRest(ApiKeys keys,BrowserTickets tickets,RestApi.Extension delegate){this.keys=keys;this.tickets=tickets;this.delegate=delegate;}
 @Override public Set<String> capabilities(){var all=new HashSet<>(delegate.capabilities());if(tickets!=null)all.add("websocket.browser");return Set.copyOf(all);}
 @Override public RestApi.Response binary(RestApi.Request request,String path,Map<String,String> query)throws Exception{return delegate.binary(request,path,query);}
 @Override public JsonObject handle(RestApi.Request request,String path,Map<String,String> query)throws Exception{
  if(!path.equals("/api/v1/websocket/tickets"))return delegate.handle(request,path,query);
  if(tickets==null)throw new ApiException(MODULE_DISABLED,"WebSocket disabled");
  if(request.header("origin")!=null)throw new ApiException(FORBIDDEN,"Backend request required");
  if(!request.method().equals("POST")||!query.isEmpty()||request.body().length()>1024)throw ApiJson.invalid();
  String auth=request.header("authorization");if(auth==null||!auth.startsWith("Bearer ")||auth.length()>128)throw new ApiException(UNAUTHENTICATED,"Application key required");
  var grant=keys.authorize(auth.substring(7),"websocket.ticket");var body=ApiJson.object(request.body());ApiJson.fields(body,"origin");
  var issued=tickets.issue(grant,request.header("x-rivet-session"),ApiJson.string(body,"origin"));
  var result=new JsonObject();result.addProperty("ticket",issued.ticket());result.addProperty("expiresAtEpochMillis",issued.expiresAtEpochMillis());result.addProperty("protocol","rivet.v1");return result;
 }
}
