package dev.abros.rivet.core.api;

import com.google.gson.*;
import com.google.gson.stream.*;
import dev.abros.rivet.api.ApiException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Set;

/** Strict bounded command JSON, including duplicate nested fields and depth. */
public final class ApiJson {
 private ApiJson(){}
 public static JsonObject object(String raw){
  if(raw==null||raw.length()>16384)throw invalid();
  try(var r=new JsonReader(new StringReader(raw))){r.setLenient(false);var v=read(r,0);if(!v.isJsonObject()||r.peek()!=JsonToken.END_DOCUMENT)throw invalid();return v.getAsJsonObject();}
  catch(Exception e){throw invalid();}
 }
 private static JsonElement read(JsonReader r,int depth)throws Exception{
  if(depth>12)throw invalid();
  return switch(r.peek()){
   case BEGIN_OBJECT->{var o=new JsonObject();r.beginObject();while(r.hasNext()){String k=r.nextName();if(o.has(k)||o.size()>=64)throw invalid();o.add(k,read(r,depth+1));}r.endObject();yield o;}
   case BEGIN_ARRAY->{var a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>=128)throw invalid();a.add(read(r,depth+1));}r.endArray();yield a;}
   case STRING->new JsonPrimitive(r.nextString());
   case NUMBER->{String n=r.nextString();if(n.length()>32)throw invalid();yield new JsonPrimitive(new BigDecimal(n));}
   case BOOLEAN->new JsonPrimitive(r.nextBoolean());
   case NULL->{r.nextNull();yield JsonNull.INSTANCE;}
   default->throw invalid();
  };
 }
 public static void fields(JsonObject o,String... fields){if(!o.keySet().equals(Set.of(fields)))throw invalid();}
 public static String string(JsonObject o,String name){var v=o.get(name);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw invalid();return v.getAsString();}
 public static ApiException invalid(){return new ApiException(ApiException.Code.INVALID_ARGUMENT,"Invalid command");}
}
