package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.api.*;
import java.util.*;

/** Desired control state. Runtime application is owned by ServerManagement, never the HTTP thread. */
public final class ApiServerControl {
 private final PgDatabase db;private final CommunityStore store;
 public ApiServerControl(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 public static String permission(String action){return switch(action){case "restart"->"rivet.restart";case "maintenance"->"rivet.maintenance";case "announcement"->"rivet.announce";default->throw ApiJson.invalid();};}
 public JsonObject read()throws Exception{return db.communityTransaction(()->{store.modules().require("server");var out=new JsonObject();for(String key:List.of("maintenance","restart","pinned-announcement")){var value=store.record("state",key);out.add(key,value==null?JsonNull.INSTANCE:value);}out.addProperty("revision",Hashes.sha256(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));return out;});}
 public JsonObject change(String actor,String action,String match,JsonObject data)throws Exception{
  store.modules().require("server");db.lock("server-control");var current=read();if(!Objects.equals(match,"\""+current.get("revision").getAsString()+"\""))throw new ApiException(ApiException.Code.CONFLICT,"Server control changed");
  long now=System.currentTimeMillis();var value=new JsonObject();
  switch(action){
   case "restart"->{ApiJson.fields(data,"seconds","reason");int seconds=integer(data,"seconds",0,86400);String reason=text(data,"reason");value.addProperty("at",seconds==0?0:now+seconds*1000L);value.addProperty("reason",reason);store.record("state","restart",value);}
   case "maintenance"->{ApiJson.fields(data,"enabled","minutes","reason");var enabled=data.get("enabled");if(!enabled.isJsonPrimitive()||!enabled.getAsJsonPrimitive().isBoolean())throw ApiJson.invalid();boolean on=enabled.getAsBoolean();int minutes=integer(data,"minutes",0,10080);String reason=text(data,"reason");value.addProperty("maintenance",on);value.addProperty("until",on&&minutes>0?now+minutes*60000L:0);value.addProperty("reason",on?reason:"");store.record("state","maintenance",value);}
   case "announcement"->{ApiJson.fields(data,"text","minutes");new CommunityAdministration(db,store).pin(actor,text(data,"text"),integer(data,"minutes",1,10080));}
   default->throw ApiJson.invalid();
  }
  store.audit(actor,"api server "+action,data.toString());var result=new JsonObject();result.add("revision",read().get("revision"));return result;
 }
 private static String text(JsonObject data,String key){String value=ApiJson.string(data,key);if(value.length()>500||value.codePoints().anyMatch(Character::isISOControl))throw ApiJson.invalid();return value;}
 private static int integer(JsonObject data,String key,int min,int max){var v=data.get(key);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw ApiJson.invalid();int value=v.getAsBigDecimal().intValueExact();if(value<min||value>max)throw ApiJson.invalid();return value;}
}
