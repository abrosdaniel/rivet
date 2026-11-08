package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.List;

/** Allowlisted response fields; apply again at delivery after asynchronous reads. */
public final class AdminDashboardView {
 public record Access(boolean reports,boolean diagnostics,boolean pack,boolean restart,boolean maintenance,boolean map){}
 public static JsonObject filter(JsonObject input,Access access){
  var out=new JsonObject();copy(input,out,List.of("kind","request"));
  if(access.reports())copy(input,out,List.of("openReports","unassignedReports","highReports","attentionReports"));
  if(access.diagnostics())copy(input,out,List.of("databaseLatencyMs","databaseHealthy","latencyHistory","recentErrors","retention","storageQueue","readQueue"));
  if(access.pack())copy(input,out,List.of("packReady","packStatus"));
  if(access.map())copy(input,out,List.of("pendingPublications","attentionPublications"));
  if(access.restart())copy(input,out,List.of("restartAt"));
  if(access.maintenance())copy(input,out,List.of("maintenanceUntil"));
  return out;
 }
 private static void copy(JsonObject from,JsonObject to,List<String> fields){for(String field:fields)if(from.has(field))to.add(field,from.get(field).deepCopy());}
 private AdminDashboardView(){}
}
