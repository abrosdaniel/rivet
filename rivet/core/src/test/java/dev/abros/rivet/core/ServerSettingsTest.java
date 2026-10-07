package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerSettingsTest {
 @TempDir Path root;
 @Test void configReviewHidesSecretsAndReloadsOnlyMenu()throws Exception{var original=ServerSettings.parse(ServerSettings.template());var next=ServerSettings.parse(ServerSettings.template().replace("password = \"\"","password = \"never-send-secret\"").replace("help = \"", "help = \"Изменено. "));assertFalse(original.preview(next).toString().contains("never-send-secret"));var live=original.liveFrom(next);assertEquals("",live.text("database.password"));assertTrue(live.text("menu.help").startsWith("Изменено."));}
 @Test void defaultsCoverAllModules()throws Exception{var s=ServerSettings.load(root);assertEquals("false",s.text("auth.mode"));assertEquals(6,s.number("auth.minPasswordLength"));assertFalse(s.votes().enabled());assertTrue(s.statistics().deaths());assertEquals(8,s.community().getAsJsonArray("sections").size());assertTrue(s.menu().getAsJsonArray("links").isEmpty());}
 @Test void acceptsLinksAndStatsSettings()throws Exception{var s=ServerSettings.parse(ServerSettings.template().replace("links = []","links = [{name = 'Сайт', url = 'https://example.org'}]").replace("playTime = true","playTime = false"));assertFalse(s.statistics().totalTime());assertEquals("Сайт",s.menu().getAsJsonArray("links").get(0).getAsJsonObject().get("name").getAsString());assertFalse(s.menu().has("database"));}
 @Test void rejectsOldFlatConfigWithoutRewriting()throws Exception{Path f=root.resolve("config/rivet-server.toml");Files.createDirectories(f.getParent());String old="project = 'abrosdaniel/example'\nluckperms = true\n";Files.writeString(f,old);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals(old,Files.readString(f));}
 @Test void rejectsMissingAuthAndUnknownKeys()throws Exception{String t=ServerSettings.template();assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("mode = \"false\"","")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t+"\nunknown = true\n"));}
 @Test void missingOptionalSettingsUseDefaults()throws Exception{var s=ServerSettings.parse(ServerSettings.template().replace("maxPerPlayer = 2","").replace("links = []",""));assertEquals(2,s.number("skins.maxPerPlayer"));assertTrue(s.menu().getAsJsonArray("links").isEmpty());}
 @Test void rejectsInvalidTypesAndRanges()throws Exception{String t=ServerSettings.template();for(String invalid:new String[]{t.replace("mode = \"false\"","mode = false"),t.replace("minPlayers = 5","minPlayers = 4"),t.replace("minPlayers = 5","minPlayers = 5.5"),t.replace("minPasswordLength = 6","minPasswordLength = 5"),t.replace("pool = 8","pool = 99999999999"),t.replace("links = []","links = [{name='Bad',url='http://example.org'}]"),t.replace("categories = []","categories = [1]")})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));}
 @Test void syntaxErrorsNeverLeakSecrets()throws Exception{var error=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("password = \"\"","password = \"secret-marker")));assertFalse(error.toString().contains("secret-marker"));assertNull(error.getCause());}
 @Test void rejectsDuplicateKeys()throws Exception{assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template()+"\nnotifyConsole = false\n"));}
 @Test void rejectsSymlink()throws Exception{Path f=root.resolve("config/rivet-server.toml");Files.createDirectories(f.getParent());Path target=root.resolve("secret");Files.writeString(target,"unchanged");Files.createSymbolicLink(f,target);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals("unchanged",Files.readString(target));}
 @Test void obsoletePunishmentDefaultsDoNotPreventProductionStartup()throws Exception{String t=ServerSettings.template().replace("[votes.actions]","[votes.actions]\nbanMinutes = 30\nmuteMinutes = 15");assertNotNull(ServerSettings.parse(t));assertFalse(ServerSettings.template().contains("banMinutes ="));}
 @Test void chatDefaultsPreserveExistingConfigsAndBoundRadius()throws Exception{String t=ServerSettings.template();var old=ServerSettings.parse(t.substring(0,t.indexOf("[chat]")));assertEquals(100,old.number("chat.localRadius"));assertTrue(old.flag("chat.group"));assertTrue(old.flag("chat.enabled"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localRadius = 100","localRadius = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localRadius = 100","localRadius = 1001")));}
 @Test void chatCanBeDisabledWithUnixAndWindowsLineEndings()throws Exception{
  String template=ServerSettings.template().replace("\r\n","\n");
  for(String newline:new String[]{"\n","\r\n"}){
   String text=template.replace("\n",newline);int chat=text.indexOf("[chat]");
   String disabled=text.substring(0,chat)+text.substring(chat).replaceFirst("enabled = true","enabled = false");
   assertNotEquals(text,disabled,"Test must change the chat setting");
   assertFalse(ServerSettings.parse(disabled).flag("chat.enabled"));
  }
 }
 @Test void displayPolicyDefaultsAndValidatesServerModes()throws Exception{String t=ServerSettings.template();var old=ServerSettings.parse(t.substring(0,t.indexOf("[display]")));assertEquals("auto",old.text("display.tab"));assertEquals("compatible",ServerSettings.parse(t.replace("tab = \"auto\"","tab = \"compatible\"")).text("display.tab"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("tab = \"auto\"","tab = \"unknown\"")));assertNotNull(ServerSettings.parse(t.replace("[display]","[display]\nchatMode = \"compatible\"")));}
 @Test void nameplatesAreControlledByServerAndAddedToOlderConfigs()throws Exception{
  String template=ServerSettings.template();assertEquals("rivet",ServerSettings.parse(template).text("display.nameplates"));
  for(String mode:java.util.List.of("base","hidden"))assertEquals(mode,ServerSettings.parse(template.replace("nameplates = \"rivet\"","nameplates = \""+mode+"\"")).text("display.nameplates"));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(template.replace("nameplates = \"rivet\"","nameplates = \"unknown\"")));
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,template.replace("nameplates = \"rivet\"",""));
  assertEquals("rivet",ServerSettings.load(root).text("display.nameplates"));assertTrue(Files.readString(file).contains("nameplates = \"rivet\""));
 }
 @Test void nameplateBooleanUpgradePreservesMeaningCommentsAndBackup()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  for(boolean old:java.util.List.of(true,false)){
   String original=ServerSettings.template().replace("nameplates = \"rivet\"","nameplates = "+old+" # owner's choice").replace("\n","\r\n");Files.writeString(file,original);
   String mode=old?"rivet":"base";assertEquals(mode,ServerSettings.load(root).text("display.nameplates"));
   String upgraded=Files.readString(file);assertTrue(upgraded.contains("nameplates = \""+mode+"\" # owner's choice"));assertFalse(upgraded.replace("\r\n","").contains("\n"));
   try(var files=Files.list(file.getParent())){assertTrue(files.filter(p->p.toString().endsWith(".toml.bak")).anyMatch(p->{try{return Files.readString(p).equals(original);}catch(Exception e){return false;}}));}
   ServerSettings.load(root);assertEquals(upgraded,Files.readString(file));
  }
 }
 @Test void nameplateUpgradeRespectsQuotedKeysAndMultilineText()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String t=ServerSettings.template().replace("nameplates = \"rivet\"","\"nameplates\" = false").replace("[display]","[\"display\"]");
  String help="help = "+String.valueOf((char)39).repeat(3)+"\n[display]\nnameplates = false\n"+String.valueOf((char)39).repeat(3);
  t=t.replace("help = \"Откройте меню командой /rivet. За помощью обратитесь к администрации сервера.\"",help);
  Files.writeString(file,t);assertEquals("base",ServerSettings.load(root).text("display.nameplates"));assertTrue(Files.readString(file).contains(help));assertTrue(Files.readString(file).contains("\"nameplates\" = \"base\""));
  t=ServerSettings.template();t="display.nameplates = true\n"+t.substring(0,t.indexOf("[display]"));Files.writeString(file,t);ServerSettings.load(root);assertTrue(Files.readString(file).contains("display.nameplates = \"rivet\""));
 }
 @Test void obsoleteItemSharingSettingIsIgnored()throws Exception{String template=ServerSettings.template();assertDoesNotThrow(()->ServerSettings.parse(template.replace("[chat]","[chat]\nallowItems = true")));assertDoesNotThrow(()->ServerSettings.parse(template.replace("[chat]","[chat]\nallowItems = false")));}
 @Test void chatLabelsAndSharingPermissions()throws Exception{String t=ServerSettings.template();var s=ServerSettings.parse(t.replace("localName = \"Рядом\"","localName = \"Соседи\""));assertEquals("Соседи",s.text("chat.localName"));assertFalse(ServerSettings.template().contains("allowItems ="));assertTrue(s.flag("chat.coordinates"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localName = \"Рядом\"","localName = \""+"x".repeat(41)+"\"")));}
 @Test void taskAndSparkPoliciesDefaultForOldConfigsAndRejectInvalidValues()throws Exception{
  String t=ServerSettings.template();String old=t.substring(0,t.indexOf("[tasks]"))+t.substring(t.indexOf("[chat]"));var settings=ServerSettings.parse(old);
  assertEquals(TaskLimits.defaults(),settings.taskLimits());assertEquals(SparkTimeline.AlertSettings.defaults(),settings.sparkAlerts());
  var changed=ServerSettings.parse(t.replace("maxPerOwner = 200","maxPerOwner = 10").replace("durationSeconds = 30","durationSeconds = 5"));assertEquals(10,changed.taskLimits().perOwner());assertEquals(5,changed.sparkAlerts().sustainedSeconds());
  for(String replacement:new String[]{"maxPerOwner = -1","maxPerOwner = 2147483648"})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("maxPerOwner = 200",replacement)));
  assertEquals(new TaskLimits(0,0,0),ServerSettings.parse(t.replace("maxPerOwner = 200","maxPerOwner = 0").replace("maxSubtasks = 30","maxSubtasks = 0").replace("maxComments = 100","maxComments = 0")).taskLimits());assertTrue(new TaskLimits(0,0,0).allowsTask(Integer.MAX_VALUE));assertTrue(new TaskLimits(0,0,0).allowsSubtask(Integer.MAX_VALUE));assertTrue(new TaskLimits(0,0,0).allowsComment(Integer.MAX_VALUE));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("minTps = 18","minTps = 21")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("maxMspt = 50","maxMspt = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("durationSeconds = 30","durationSeconds = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("cooldownSeconds = 300","cooldownSeconds = 86401")));
  assertEquals(settings.taskLimits(),settings.liveFrom(changed).taskLimits());assertEquals(settings.sparkAlerts(),settings.liveFrom(changed).sparkAlerts());
 }
 @Test void configurationErrorsPointToKeysWithoutExposingValues()throws Exception{
  String t=ServerSettings.template();
  var missing=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("mode = \"false\"","")));assertTrue(missing.getMessage().contains("auth.mode"));
  var typo=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t+"\nunknown = 'secret-marker'\n"));assertTrue(typo.getMessage().contains("pack.downloads.unknown"));assertFalse(typo.getMessage().contains("secret-marker"));
  var unsafe=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t+"\n\"private secret-marker\" = true\n"));assertFalse(unsafe.getMessage().contains("secret-marker"));
 }
 @Test void legacyIntegrationSwitchIsIgnored()throws Exception{assertNotNull(ServerSettings.parse(ServerSettings.template()+"\n[integrations]\nluckperms = false\n"));assertNotNull(ServerSettings.parse(ServerSettings.template()+"\n[integrations]\nluckperms = true\n"));}
 @Test void upgradeAddsEditableSettingsAndCommentsWithoutChangingOwnersValues()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String original=ServerSettings.template().replace("password = \"\"","password = \"owner-secret\"").replace("localRadius = 100","localRadius = 42").replace("group = true", "# Owner chose the available channels\ngroup = false").replace("maxComments = 100","");
  original=original.substring(0,original.indexOf("[spark]"))+original.substring(original.indexOf("[chat]"));
  Files.writeString(file,original);var settings=ServerSettings.load(root);String expanded=Files.readString(file);
  assertEquals(42,settings.number("chat.localRadius"));assertFalse(settings.flag("chat.group"));assertEquals("owner-secret",settings.text("database.password"));
  assertTrue(expanded.contains("# Owner chose the available channels"));assertTrue(expanded.contains("maxComments = 100"));assertTrue(expanded.contains("[spark]"));assertTrue(expanded.contains("# Как долго нагрузка"));
  try(var files=Files.list(file.getParent())){var backups=files.filter(p->p.toString().endsWith(".toml.bak")).toList();assertEquals(1,backups.size());assertEquals(original,Files.readString(backups.getFirst()));}
  ServerSettings.load(root);assertEquals(expanded,Files.readString(file));
  try(var files=Files.list(file.getParent())){assertEquals(1,files.filter(p->p.toString().endsWith(".toml.bak")).count());}
 }
 @Test void upgradePreservesWindowsLineEndingsAndMultilineStrings()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String original=ServerSettings.template().replace("\r\n","\n").replace("maxComments = 100","").replace("tab = \"auto\"", "tab = \"rivet\"");
  original=original.replace("help = \"Откройте меню командой /rivet. За помощью обратитесь к администрации сервера.\"", "help = '''\n[chat]\nLiteral content, not a section\n''' ");
  original=original.replace("\n","\r\n");Files.writeString(file,original);ServerSettings.load(root);
  String expanded=Files.readString(file);assertFalse(expanded.replace("\r\n", "").contains("\n"));assertTrue(expanded.contains("help = '''\r\n[chat]\r\nLiteral content, not a section\r\n''' "));assertEquals("rivet",ServerSettings.parse(expanded).text("display.tab"));
 }
 @Test void invalidUpgradeLeavesConfigUntouchedAndCreatesNoBackup()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());String invalid=ServerSettings.template().replace("maxComments = 100","").replace("localRadius = 100","localRadius = 0");Files.writeString(file,invalid);
  assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals(invalid,Files.readString(file));try(var files=Files.list(file.getParent())){assertEquals(1,files.count());}
 }
 @Test void upgradeSupportsQuotedSectionsAndRootDottedKeys()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String text=ServerSettings.template().replace("[chat]", "[\"chat\"]").replace("localRadius = 100", "");Files.writeString(file,text);assertEquals(100,ServerSettings.load(root).number("chat.localRadius"));
  text=ServerSettings.template();int start=text.indexOf("[chat]"),end=text.indexOf("[display]");
  String dotted=text.substring(start,end).replace("[chat]", "").lines().map(line->line.contains(" = ")&&!line.stripLeading().startsWith("#")?"chat."+line:line).collect(java.util.stream.Collectors.joining("\n"));
  text=dotted.replace("chat.localRadius = 100", "")+"\n"+text.substring(0,start)+text.substring(end);Files.writeString(file,text);assertEquals(100,ServerSettings.load(root).number("chat.localRadius"));assertFalse(Files.readString(file).contains("[chat]"));
 }
 @Test void removedGithubProjectSettingsAreNeverAddedButExistingOwnerTextSurvives()throws Exception{
  String template=ServerSettings.template();assertFalse(template.contains("[project]"));
  String original="[project]\nrepository = 'https://github.com/owner/old-pack'\nrequirePack = true\n\n"+template;
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,original);
  assertNotNull(ServerSettings.load(root));assertEquals(original,Files.readString(file));
 }
}
