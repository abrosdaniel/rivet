package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Explicit fields only: never infer a diff from an unrestricted account/configuration object. */
public final class ChangeSummary {
 private ChangeSummary(){}
 public static JsonArray between(JsonObject before,JsonObject after,Map<String,String> fields){var rows=new JsonArray();for(var field:fields.entrySet()){var a=before.get(field.getKey());var b=after.get(field.getKey());if(Objects.equals(a,b))continue;var row=new JsonObject();row.addProperty("field",field.getKey());row.addProperty("label",field.getValue());if(a!=null&&!a.isJsonNull())row.add("before",a.deepCopy());if(b!=null&&!b.isJsonNull())row.add("after",b.deepCopy());rows.add(row);}return rows;}
 public static JsonArray betweenLocalized(JsonObject before,JsonObject after,Map<String,LocalizedText> fields){
  var labels=new LinkedHashMap<String,String>();fields.forEach((key,value)->labels.put(key,value.render()));
  var rows=between(before,after,labels);for(var item:rows){var row=item.getAsJsonObject();fields.get(Json.str(row,"field")).put(row,"label");}return rows;
 }
 public static String text(JsonElement value){if(value==null||value.isJsonNull())return Messages.text("rivet.ui.not_set_6ddd51c3");return value.isJsonPrimitive()?value.getAsString():value.toString();}
}
