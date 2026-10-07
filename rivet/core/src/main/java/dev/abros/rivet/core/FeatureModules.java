package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Immutable feature policy shared by request routing, background work and clients. */
public final class FeatureModules {
 public static final List<String> OPTIONAL=List.of("groups","tasks","storage","board","events","polls","ideas","reports","server");
 private final Set<String> active;
 private FeatureModules(Set<String> active){this.active=Set.copyOf(active);if(enabled("storage")&&!enabled("tasks"))throw new IllegalArgumentException("storage.enabled требует tasks.enabled = true; отключите storage.enabled вместе с задачами");}
 public static FeatureModules all(){return new FeatureModules(new HashSet<>(OPTIONAL));}
 public static FeatureModules from(ServerSettings settings){var active=new HashSet<String>();for(String id:OPTIONAL)if(settings.flag(id+".enabled"))active.add(id);return new FeatureModules(active);}
 public boolean enabled(String id){return !OPTIONAL.contains(id)||active.contains(id);}
 public void require(String id){if(!enabled(id))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Модуль "+id+" выключен на сервере");}
 public JsonObject state(){var out=new JsonObject();for(String id:OPTIONAL)out.addProperty(id,enabled(id));return out;}
 public JsonObject sections(JsonObject configuration){var out=configuration.deepCopy();var sections=new JsonArray();for(var section:out.getAsJsonArray("sections"))if(enabled(section.getAsString()))sections.add(section);out.add("sections",sections);return out;}
 public void communityRequest(JsonObject request){
  String section=Json.opt(request,"section",""),op=Json.opt(request,"op","");require(section);
  if(op.equals("toolsTemporary")||op.equals("toolsMapPeers"))require("groups");
  if(op.equals("workReserve"))require("storage");
  if(op.startsWith("work")||op.equals("plusTaskStatus"))require("tasks");
  if(op.equals("toolsAttendance")||op.equals("plusReminder")||op.equals("plusCancelSeries"))require("events");
  if(op.equals("plusRemoveMember")||op.equals("plusAssistantRemoval")||op.startsWith("plusItem"))require("groups");
  if(!Json.opt(request,"group","").isEmpty())require("groups");
 }
 public void serverRequest(JsonObject request){
  String action=Json.opt(request,"action","");
  if(Set.of("report","myReport","myReports","reports","reply","reportManage","moderate","moderationHistory").contains(action))require("reports");
  if(Set.of("maintenance","restart","announce","scheduledAnnouncements","serverControl","pinAnnouncement").contains(action))require("server");
  if(action.equals("community"))communityRequest(request);
 }
}
