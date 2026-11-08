package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FeatureModulesTest {
 @TempDir Path root;
 static String disabled(String... ids)throws Exception{String text;  text=ServerSettings.template().replace("\r\n","\n");for(String id:ids){int start=text.indexOf("["+id+"]");if(start<0){assertTrue(RetiredFeatures.contains(id));text+="\n["+id+"]\nenabled = false\n";continue;}int end=text.indexOf("\n[",start+1);if(end<0)end=text.length();text=text.substring(0,start)+text.substring(start,end).replace("enabled = true","enabled = false")+text.substring(end);}return text;}
 @Test void retiredFeaturesCannotBeReenabledByOldConfiguration()throws Exception{
  for(var policy:List.of(FeatureModules.all(),ServerSettings.parse(ServerSettings.template()).modules(),ServerSettings.parse(disabled("tasks")).modules())){
   for(String id:RetiredFeatures.IDS){assertFalse(policy.enabled(id));assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->policy.require(id)).code());}
   assertTrue(policy.enabled("groups"));assertTrue(policy.enabled("reports"));assertTrue(policy.enabled("server"));
   var sections=policy.sections(CommunityStore.defaults()).getAsJsonArray("sections");for(var section:sections)assertFalse(RetiredFeatures.contains(section.getAsString()));
  }
 }
 @Test void legacyTaskRequestsRemainUnavailableEvenThroughHome()throws Exception{var policy=FeatureModules.all();for(String op:List.of("workList","workGet","plusTaskStatus","toolsAttendance","plusReminder"))assertEquals(CommunityFailure.Code.UNAVAILABLE,assertThrows(CommunityFailure.class,()->policy.communityRequest(Json.parse("{\"section\":\"home\",\"op\":\""+op+"\"}"))).code());}
 @Test void directRequestsCannotBypassDisabledModules()throws Exception{var policy=ServerSettings.parse(disabled("reports","server","storage","tasks","events","groups")).modules();for(String action:List.of("report","reply","moderate","maintenance","restart","scheduledAnnouncements","pinAnnouncement"))assertThrows(CommunityFailure.class,()->policy.serverRequest(Json.parse("{\"action\":\""+action+"\"}")),action);for(String op:List.of("workGet","plusTaskStatus","toolsAttendance","plusReminder","plusItemSave"))assertThrows(CommunityFailure.class,()->policy.communityRequest(Json.parse("{\"section\":\"home\",\"op\":\""+op+"\"}")),op);assertDoesNotThrow(()->policy.serverRequest(Json.parse("{\"action\":\"players\"}")));}
 @Test void moduleChangesNeedRestart()throws Exception{var original=ServerSettings.parse(ServerSettings.template());var changed=ServerSettings.parse(disabled("groups"));assertTrue(original.liveFrom(changed).modules().enabled("groups"));assertFalse(changed.modules().enabled("groups"));}
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"\n","\r\n"})
 void upgradesPopulateEditableSettingsAndKeepOwnersChoices(String newline)throws Exception{
  String old=disabled("chat");
  for(String id:FeatureModules.OPTIONAL){
   if(RetiredFeatures.contains(id))continue;
   int start=old.indexOf("["+id+"]"),end=old.indexOf("\n[",start+1);if(end<0)end=old.length();
   String section=old.substring(start,end),without=section.replaceFirst("enabled = true\n", "");
   assertNotEquals(section,without,"Fixture must omit "+id+".enabled");
   old=old.substring(0,start)+without+old.substring(end);
  }
  old=old.replace("\n",newline);var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old);
  var loaded=ServerSettings.load(root);assertFalse(loaded.flag("chat.enabled"));for(String id:FeatureModules.OPTIONAL)assertEquals(!RetiredFeatures.contains(id),loaded.modules().enabled(id));
  String upgraded=Files.readString(file);for(String id:RetiredFeatures.IDS)assertFalse(upgraded.contains("["+id+"]"));assertFalse(upgraded.replace(newline,"").contains("\r"));assertFalse(upgraded.replace(newline,"").contains("\n"));
  ServerSettings.load(root);assertEquals(upgraded,Files.readString(file));
  String original=old;try(var files=Files.list(file.getParent())){var backups=files.filter(p->p.toString().endsWith(".toml.bak")).toList();assertEquals(1,backups.size());assertEquals(original,Files.readString(backups.getFirst()));}
 }
}
