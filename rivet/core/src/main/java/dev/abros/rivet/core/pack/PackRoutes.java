package dev.abros.rivet.core.pack;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.*;
import java.util.*;

/** Additive status extension: legacy clients continue to use host/port. */
public final class PackRoutes {
 public record Route(String host,int port,String gameHost){
  public Route{host=checkHost(host,false);gameHost=checkHost(gameHost,true);if(port<1||port>65535)throw new IllegalArgumentException("Invalid pack route port");}
 }
 private PackRoutes(){}
 private static String checkHost(String host,boolean empty){
  if(host==null||(!empty&&host.isBlank())||host.length()>253||host.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)||"/@?#\\".indexOf(c)>=0))throw new IllegalArgumentException("Invalid pack route host");
  return host.toLowerCase(Locale.ROOT);
 }
 public static List<Route> config(List<?> values){
  if(values.size()>8)throw new IllegalArgumentException("pack.network.routes: maximum eight routes");var out=new ArrayList<Route>();
  for(var value:values){if(!(value instanceof UnmodifiableConfig c)||!Set.of("host","port","gameHost").containsAll(c.valueMap().keySet()))throw new IllegalArgumentException("pack.network.routes: expected host, port, gameHost");
   Object host=c.get("host"),port=c.get("port"),game=c.getOrElse("gameHost","");if(!(host instanceof String h)||!(port instanceof Integer||port instanceof Long)||!(game instanceof String g))throw new IllegalArgumentException("Invalid pack route");out.add(new Route(h,Math.toIntExact(((Number)port).longValue()),g));
  }return List.copyOf(out);
 }
 public static JsonArray json(List<Route> routes){var out=new JsonArray();for(var r:routes){var j=new JsonObject();j.addProperty("host",r.host());j.addProperty("port",r.port());j.addProperty("gameHost",r.gameHost());out.add(j);}return out;}
 public static List<PackDiscovery.Endpoint> endpoints(JsonObject pack,String gameHost,PackDiscovery.Endpoint legacy){
  if(!pack.has("routes"))return List.of(legacy);
  var values=pack.getAsJsonArray("routes");if(values.size()>8)throw new IllegalArgumentException("Too many pack routes");var preferred=new ArrayList<PackDiscovery.Endpoint>();var others=new ArrayList<PackDiscovery.Endpoint>();
  for(var v:values){var j=v.getAsJsonObject();var r=new Route(j.get("host").getAsString(),j.get("port").getAsBigDecimal().intValueExact(),j.has("gameHost")?j.get("gameHost").getAsString():"");var e=new PackDiscovery.Endpoint(r.host(),r.port(),legacy.fingerprint(),legacy.required());(r.gameHost().equalsIgnoreCase(gameHost)?preferred:others).add(e);}
  preferred.add(legacy);preferred.addAll(others);return preferred.stream().distinct().toList();
 }
}
