package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Stateless page deltas: ordered IDs plus changed public rows, never unfiltered database records. */
public final class RowDelta {
 private RowDelta(){}
 private static String hash(JsonObject row){return Hashes.sha256(Json.GSON.toJson(row).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 public static JsonObject known(JsonArray rows){var hashes=new JsonObject();for(var e:rows){var row=e.getAsJsonObject();hashes.addProperty(Json.str(row,"id"),hash(row));}return hashes;}
 public static JsonObject encode(JsonArray rows,JsonObject known){if(rows.size()>100||known.size()>100)throw new IllegalArgumentException("Delta page too large");var out=new JsonObject();var order=new JsonArray();var changed=new JsonArray();for(var e:rows){var row=e.getAsJsonObject();String id=Json.str(row,"id");order.add(id);if(!hash(row).equals(Json.opt(known,id,"")))changed.add(row.deepCopy());}out.add("order",order);out.add("rows",changed);return out;}
 public static JsonArray apply(JsonArray previous,JsonObject delta){var order=delta.getAsJsonArray("order");var changed=delta.getAsJsonArray("rows");if(order.size()>100||changed.size()>100)throw new IllegalArgumentException("Delta page too large");var indexed=new HashMap<String,JsonObject>();for(var e:previous){var row=e.getAsJsonObject();indexed.put(Json.str(row,"id"),row);}for(var e:changed){var row=e.getAsJsonObject();indexed.put(Json.str(row,"id"),row);}var seen=new HashSet<String>();var out=new JsonArray();for(var e:order){String id=e.getAsString();if(!seen.add(id)||!indexed.containsKey(id))throw new IllegalArgumentException("Delta baseline missing");out.add(indexed.get(id).deepCopy());}return out;}
}
