package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ServerSettingsTest {
 // Git may check out the resource with CRLF. Construct fixtures in LF, then explicitly
 // select their wire/file line endings in the tests that exercise Windows configuration.
 private static String fixtureTemplate()throws java.io.IOException{return ServerSettings.template().replace("\r\n","\n");}
 @Test void hdDefaultsAreAddedWithoutChangingExistingLimits()throws Exception{
  String old=fixtureTemplate().replace("maxResolution = 256\n", "").replace("maxCapeResolution = 256\n", "");var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old);var settings=ServerSettings.load(root);assertEquals(256,settings.skins().maxResolution());assertEquals(256,settings.skins().maxCapeResolution());String updated=Files.readString(file);assertTrue(updated.contains("maxResolution = 256"));assertTrue(updated.contains("maxCapeResolution = 256"));Files.writeString(file,updated.replace("maxResolution = 256","maxResolution = 512"));assertEquals(512,ServerSettings.load(root).skins().maxResolution());assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(updated.replace("maxCapeResolution = 256","maxCapeResolution = 300")));
 }
 @Test void actualPackagedTemplateLoadsWithoutRewriting()throws Exception{
  String raw=ServerSettings.template();var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,raw);
  assertNotNull(ServerSettings.load(root));assertEquals(raw,Files.readString(file));
 }
 @TempDir Path root;
 @Test void chatSettingsAreAddedAndCustomValuesSurviveRestart()throws Exception{
  String current=fixtureTemplate();String old=current.replaceAll("(?m)^(globalTrigger|groupTrigger|localTrigger|localEmptyMessage) = .*\n","");
  var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old);
  var loaded=ServerSettings.load(root);assertEquals("!",loaded.text("chat.globalTrigger"));assertEquals("#",loaded.text("chat.groupTrigger"));assertEquals("",loaded.text("chat.localTrigger"));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(current.replace("localTrigger = \"\"","localTrigger = \"!\"")));
  String custom=Files.readString(file).replace("globalTrigger = \"!\"","globalTrigger = \"!!\"").replace("groupTrigger = \"#\"","groupTrigger = \"##\"");Files.writeString(file,custom);
  assertEquals("!!",ServerSettings.load(root).text("chat.globalTrigger"));assertEquals(custom,Files.readString(file));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(current.replaceAll("(?m)^localEmptyMessage = .*","localEmptyMessage = \"\"")));
 }
 @Test void cleanupPreservesMultilineHelpAndDottedSettings()throws Exception{
  String literal="[tasks]\n# Сохранённых скинов на игрока: 1–5.\nenabled = true";
  String source="tasks.enabled = true\n"+fixtureTemplate().replaceFirst("(?m)^help = .*", "help = '''"+literal+"'''");
  var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,source);
  var result=ServerSettings.load(root);assertEquals(literal,result.text("menu.help"));
  assertFalse(Files.readString(file).contains("tasks.enabled"));assertFalse(Files.readString(file).contains("community.categories"));
 }
 @Test void cleansRetiredConfigWithoutChangingActiveValues()throws Exception{
  String original=fixtureTemplate().replace("\n[community]\n","\n[community]\ncategories = [\n 'Trade',\n]\n")
   .replace("sections = [\"home\", \"players\", \"groups\", \"notifications\"]","sections = [\n 'home', 'board', 'groups', 'events', 'polls', 'ideas', 'notifications',\n]")
   .replace("password = \"\"","password = 'test-preserve-secret'")
   +"\n[tasks]\nenabled = true\nmaxPerOwner = 42\nmaxSubtasks = 10\nmaxComments = 3\n[board]\nenabled = true\n";
  for(String ending:new String[]{"\n","\r\n"}){
   var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());String input=original.replace("\n",ending);assertFalse(input.contains("\r\r\n"));assertTrue(input.contains("categories = ["));Files.writeString(file,input);
   ServerSettings.load(root);String updated=Files.readString(file);
   assertFalse(updated.contains("categories ="));assertFalse(updated.contains("[tasks]"));assertFalse(updated.contains("[board]"));
   assertTrue(updated.contains("password = 'test-preserve-secret'"));
   assertEquals(java.util.List.of("home","groups","notifications"),ServerSettings.parse(updated).strings("community.sections"));
   try(var backups=Files.list(file.getParent())){assertTrue(backups.filter(p->p.toString().endsWith(".bak")).anyMatch(p->{try{return Files.readString(p).equals(input);}catch(Exception e){return false;}}));}
   ServerSettings.load(root);assertEquals(updated,Files.readString(file));
  }
 }
 @Test void capeSettingsUpgradeAndLimits()throws Exception{
  String old=fixtureTemplate().replace("capes = true\n", "").replace("maxCapesPerPlayer = 8\n", "").replace("maxOutfitsPerPlayer = 8\n", "").replace("maxCapeSizeKiB = 256\n", "");
  var file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old);var settings=ServerSettings.load(root);assertTrue(settings.skins().capes());assertEquals(8,settings.skins().maxCapesPerPlayer());assertEquals(8,settings.skins().maxOutfitsPerPlayer());assertEquals(262144,settings.skins().maxCapeBytes());String updated=Files.readString(file);assertTrue(updated.contains("maxCapeSizeKiB = 256"));ServerSettings.load(root);assertEquals(updated,Files.readString(file));
  for(String invalid:new String[]{updated.replace("maxCapesPerPlayer = 8","maxCapesPerPlayer = 9"),updated.replace("maxCapeSizeKiB = 256","maxCapeSizeKiB = 0"),updated.replace("maxOutfitsPerPlayer = 8","maxOutfitsPerPlayer = 9")})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));
 }
 @Test void browserTicketLimitsAndUpgrade()throws Exception{
  String template=fixtureTemplate();
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  Files.writeString(file,template.replace("ticketSeconds = 30", "").replace("ticketLimit = 1024", "").replace("ticketsPerKey = 16", ""));
  var settings=ServerSettings.load(root);assertEquals(30,settings.number("api.websocket.ticketSeconds"));assertEquals(1024,settings.number("api.websocket.ticketLimit"));assertEquals(16,settings.number("api.websocket.ticketsPerKey"));
  assertTrue(Files.readString(file).contains("ticketSeconds = 30"));
  for(String invalid:new String[]{template.replace("ticketSeconds = 30","ticketSeconds = 4"),template.replace("ticketSeconds = 30","ticketSeconds = 121"),template.replace("ticketLimit = 1024","ticketLimit = 0"),template.replace("ticketsPerKey = 16","ticketsPerKey = 1025")})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));
 }
 @Test void terrainDefaultsAndMaskValidation()throws Exception{
  var defaults=ServerSettings.parse(fixtureTemplate());assertFalse(defaults.flag("api.terrain.enabled"));assertFalse(defaults.flag("api.terrain.isometric"));assertEquals(4096,defaults.number("api.terrain.maxTiles"));
  for(String invalid:new String[]{fixtureTemplate().replace("maxTiles = 4096","maxTiles = 1"),fixtureTemplate().replace("hidden = []","hidden = [\"10,0,0,0\"]"),fixtureTemplate().replace("maxZoom = 4","maxZoom = 7")})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));
 }
 @Test void apiSettingsUpgradeWithBackupAndKeepOwnerValues()throws Exception{
  String original=fixtureTemplate().split("\\[api\\]",2)[0]+"[api]\nrequestsPerMinute = 77 # chosen budget\n";
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,original);
  var settings=ServerSettings.load(root);assertEquals(77,settings.number("api.requestsPerMinute"));assertEquals(256,settings.number("api.keyLimit"));
  String upgraded=Files.readString(file);assertTrue(upgraded.contains("# chosen budget"));assertTrue(upgraded.contains("keyRetentionDays = 30"));assertTrue(upgraded.contains("[api.terrain]"));assertTrue(upgraded.contains("columnsPerTick = 64"));
  try(var files=Files.list(file.getParent())){assertTrue(files.filter(p->p.toString().endsWith(".toml.bak")).anyMatch(p->{try{return Files.readString(p).equals(original);}catch(Exception e){return false;}}));}
  ServerSettings.load(root);assertEquals(upgraded,Files.readString(file));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(upgraded.replace("keyLimit = 256","keyLimit = 0")));
 }
 @Test void restDefaultsAndNetworkBoundariesAreExplicit()throws Exception{
  var defaults=ServerSettings.parse(fixtureTemplate());assertFalse(defaults.flag("api.http.enabled"));assertEquals("127.0.0.1",defaults.text("api.http.bind"));assertTrue(defaults.strings("api.http.publicResources").isEmpty());
  for(String bad:new String[]{"0.0.0.0","localhost","192.0.2.1"})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate().replace("bind = \"127.0.0.1\"","bind = \""+bad+"\"")));
  for(String bad:new String[]{"*","http://example.org","https://example.org/path","https://user@example.org","https://example.org:0"})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate().replace("origins = []","origins = [\""+bad+"\"]")));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate().replace("publicResources = []","publicResources = [\"players\"]")));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate().replace("origins = []","origins = [1]")));
 }
 @Test void configReviewHidesSecretsAndReloadsOnlyMenu()throws Exception{var original=ServerSettings.parse(fixtureTemplate());var next=ServerSettings.parse(fixtureTemplate().replace("password = \"\"","password = \"never-send-secret\"").replace("help = \"", "help = \"Изменено. "));assertFalse(original.preview(next).toString().contains("never-send-secret"));var live=original.liveFrom(next);assertEquals("",live.text("database.password"));assertTrue(live.text("menu.help").startsWith("Изменено."));}
 @Test void defaultsCoverAllModules()throws Exception{var s=ServerSettings.load(root);assertEquals("false",s.text("auth.mode"));assertEquals(6,s.number("auth.minPasswordLength"));assertFalse(s.votes().enabled());assertTrue(s.statistics().deaths());assertEquals(java.util.List.of("home","players","groups","notifications"),s.community().getAsJsonArray("sections").asList().stream().map(e->e.getAsString()).toList());assertTrue(s.menu().getAsJsonArray("links").isEmpty());}
 @Test void acceptsLinksAndStatsSettings()throws Exception{var s=ServerSettings.parse(fixtureTemplate().replace("links = []","links = [{name = 'Сайт', url = 'https://example.org'}]").replace("playTime = true","playTime = false"));assertFalse(s.statistics().totalTime());assertEquals("Сайт",s.menu().getAsJsonArray("links").get(0).getAsJsonObject().get("name").getAsString());assertFalse(s.menu().has("database"));}
 @Test void rejectsOldFlatConfigWithoutRewriting()throws Exception{Path f=root.resolve("config/rivet-server.toml");Files.createDirectories(f.getParent());String old="project = 'abrosdaniel/example'\nluckperms = true\n";Files.writeString(f,old);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals(old,Files.readString(f));}
 @Test void rejectsMissingAuthAndUnknownKeys()throws Exception{String t=fixtureTemplate();assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("mode = \"false\"","")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t+"\nunknown = true\n"));}
 @Test void skinLimitAllowsEightAndKeepsDefault()throws Exception{assertEquals(8,ServerSettings.parse(fixtureTemplate().replace("maxPerPlayer = 2","maxPerPlayer = 8")).skins().maxSkinsPerPlayer());assertEquals(2,ServerSettings.parse(fixtureTemplate()).skins().maxSkinsPerPlayer());}
 @Test void missingOptionalSettingsUseDefaults()throws Exception{var s=ServerSettings.parse(fixtureTemplate().replace("maxPerPlayer = 2","").replace("links = []",""));assertEquals(2,s.number("skins.maxPerPlayer"));assertTrue(s.menu().getAsJsonArray("links").isEmpty());}
 @Test void rejectsInvalidTypesAndRanges()throws Exception{String t=fixtureTemplate();for(String invalid:new String[]{t.replace("mode = \"false\"","mode = false"),t.replace("minPlayers = 5","minPlayers = 4"),t.replace("minPlayers = 5","minPlayers = 5.5"),t.replace("minPasswordLength = 6","minPasswordLength = 5"),t.replace("pool = 8","pool = 99999999999"),t.replace("links = []","links = [{name='Bad',url='http://example.org'}]"),t.replace("\n[community]\n","\n[community]\ncategories = [1]\n")}){assertNotEquals(t,invalid,"Invalid fixture replacement must actually change the template");assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(invalid));}}
 @Test void syntaxErrorsNeverLeakSecrets()throws Exception{var error=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate().replace("password = \"\"","password = \"secret-marker")));assertFalse(error.toString().contains("secret-marker"));assertNull(error.getCause());}
 @Test void rejectsDuplicateKeys()throws Exception{assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(fixtureTemplate()+"\nnotifyConsole = false\n"));}
 @Test void rejectsSymlink()throws Exception{Path f=root.resolve("config/rivet-server.toml");Files.createDirectories(f.getParent());Path target=root.resolve("secret");Files.writeString(target,"unchanged");Files.createSymbolicLink(f,target);assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals("unchanged",Files.readString(target));}
 @Test void obsoletePunishmentDefaultsDoNotPreventProductionStartup()throws Exception{String t=fixtureTemplate().replace("[votes.actions]","[votes.actions]\nbanMinutes = 30\nmuteMinutes = 15");assertNotNull(ServerSettings.parse(t));assertFalse(fixtureTemplate().contains("banMinutes ="));}
 @Test void chatDefaultsPreserveExistingConfigsAndBoundRadius()throws Exception{String t=fixtureTemplate();var old=ServerSettings.parse(t.substring(0,t.indexOf("[chat]")));assertEquals(100,old.number("chat.localRadius"));assertTrue(old.flag("chat.group"));assertTrue(old.flag("chat.enabled"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localRadius = 100","localRadius = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localRadius = 100","localRadius = 1001")));}
 @Test void chatCanBeDisabledWithUnixAndWindowsLineEndings()throws Exception{
  String template=fixtureTemplate().replace("\r\n","\n");
  for(String newline:new String[]{"\n","\r\n"}){
   String text=template.replace("\n",newline);int chat=text.indexOf("[chat]");
   String disabled=text.substring(0,chat)+text.substring(chat).replaceFirst("enabled = true","enabled = false");
   assertNotEquals(text,disabled,"Test must change the chat setting");
   assertFalse(ServerSettings.parse(disabled).flag("chat.enabled"));
  }
 }
 @Test void displayPolicyDefaultsAndValidatesServerModes()throws Exception{String t=fixtureTemplate();var old=ServerSettings.parse(t.substring(0,t.indexOf("[display]")));assertEquals("auto",old.text("display.tab"));assertEquals("compatible",ServerSettings.parse(t.replace("tab = \"auto\"","tab = \"compatible\"")).text("display.tab"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("tab = \"auto\"","tab = \"unknown\"")));assertNotNull(ServerSettings.parse(t.replace("[display]","[display]\nchatMode = \"compatible\"")));}
 @Test void nameplatesAreControlledByServerAndAddedToOlderConfigs()throws Exception{
  String template=fixtureTemplate();assertEquals("rivet",ServerSettings.parse(template).text("display.nameplates"));
  for(String mode:java.util.List.of("base","hidden"))assertEquals(mode,ServerSettings.parse(template.replace("nameplates = \"rivet\"","nameplates = \""+mode+"\"")).text("display.nameplates"));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(template.replace("nameplates = \"rivet\"","nameplates = \"unknown\"")));
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,template.replace("nameplates = \"rivet\"",""));
  assertEquals("rivet",ServerSettings.load(root).text("display.nameplates"));assertTrue(Files.readString(file).contains("nameplates = \"rivet\""));
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"\n","\r\n"})
 void nameplateBooleanUpgradePreservesMeaningCommentsAndBackup(String newline)throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  for(boolean old:java.util.List.of(true,false)){
   String original=fixtureTemplate().replace("\r\n","\n").replace("nameplates = \"rivet\"","nameplates = "+old+" # owner's choice").replace("\n",newline);Files.writeString(file,original);
   String mode=old?"rivet":"base";assertEquals(mode,ServerSettings.load(root).text("display.nameplates"));
   String upgraded=Files.readString(file);assertTrue(upgraded.contains("nameplates = \""+mode+"\" # owner's choice"));assertFalse(upgraded.replace(newline,"").contains("\r"));assertFalse(upgraded.replace(newline,"").contains("\n"));
   try(var files=Files.list(file.getParent())){assertTrue(files.filter(p->p.toString().endsWith(".toml.bak")).anyMatch(p->{try{return Files.readString(p).equals(original);}catch(Exception e){return false;}}));}
   ServerSettings.load(root);assertEquals(upgraded,Files.readString(file));
  }
 }
 @Test void nameplateUpgradeRespectsQuotedKeysAndMultilineText()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String t=fixtureTemplate().replace("nameplates = \"rivet\"","\"nameplates\" = false").replace("[display]","[\"display\"]");
  String help="help = "+String.valueOf((char)39).repeat(3)+"\n[display]\nnameplates = false\n"+String.valueOf((char)39).repeat(3);
  t=t.replace("help = \"Откройте меню командой /rivet. За помощью обратитесь к администрации сервера.\"",help);
  Files.writeString(file,t);assertEquals("base",ServerSettings.load(root).text("display.nameplates"));assertTrue(Files.readString(file).contains(help));assertTrue(Files.readString(file).contains("\"nameplates\" = \"base\""));
  t=fixtureTemplate();t="display.nameplates = true\n"+t.substring(0,t.indexOf("[display]"));Files.writeString(file,t);ServerSettings.load(root);assertTrue(Files.readString(file).contains("display.nameplates = \"rivet\""));
 }
 @Test void obsoleteItemSharingSettingIsIgnored()throws Exception{String template=fixtureTemplate();assertDoesNotThrow(()->ServerSettings.parse(template.replace("[chat]","[chat]\nallowItems = true")));assertDoesNotThrow(()->ServerSettings.parse(template.replace("[chat]","[chat]\nallowItems = false")));}
 @Test void chatLabelsAndSharingPermissions()throws Exception{String t=fixtureTemplate();var s=ServerSettings.parse(t.replace("localName = \"Рядом\"","localName = \"Соседи\""));assertEquals("Соседи",s.text("chat.localName"));assertFalse(fixtureTemplate().contains("allowItems ="));assertTrue(s.flag("chat.coordinates"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("localName = \"Рядом\"","localName = \""+"x".repeat(41)+"\"")));}
 @Test void taskAndSparkPoliciesDefaultForOldConfigsAndRejectInvalidValues()throws Exception{
  String current=fixtureTemplate();String t=current+"\n[tasks]\nenabled = true\nmaxPerOwner = 200\nmaxSubtasks = 30\nmaxComments = 100\n";String old=current.substring(0,current.indexOf("[spark]"))+current.substring(current.indexOf("[chat]"));var settings=ServerSettings.parse(old);
  assertEquals(TaskLimits.defaults(),settings.taskLimits());assertEquals(SparkTimeline.AlertSettings.defaults(),settings.sparkAlerts());
  var changed=ServerSettings.parse(t.replace("maxPerOwner = 200","maxPerOwner = 10").replace("durationSeconds = 30","durationSeconds = 5"));assertEquals(10,changed.taskLimits().perOwner());assertEquals(5,changed.sparkAlerts().sustainedSeconds());
  for(String replacement:new String[]{"maxPerOwner = -1","maxPerOwner = 2147483648"})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("maxPerOwner = 200",replacement)));
  assertEquals(new TaskLimits(0,0,0),ServerSettings.parse(t.replace("maxPerOwner = 200","maxPerOwner = 0").replace("maxSubtasks = 30","maxSubtasks = 0").replace("maxComments = 100","maxComments = 0")).taskLimits());assertTrue(new TaskLimits(0,0,0).allowsTask(Integer.MAX_VALUE));assertTrue(new TaskLimits(0,0,0).allowsSubtask(Integer.MAX_VALUE));assertTrue(new TaskLimits(0,0,0).allowsComment(Integer.MAX_VALUE));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("minTps = 18","minTps = 21")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("maxMspt = 50","maxMspt = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("durationSeconds = 30","durationSeconds = 0")));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("cooldownSeconds = 300","cooldownSeconds = 86401")));
  assertEquals(settings.taskLimits(),settings.liveFrom(changed).taskLimits());assertEquals(settings.sparkAlerts(),settings.liveFrom(changed).sparkAlerts());
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"\n","\r\n"})
 void configurationErrorsPointToKeysWithoutExposingValues(String newline)throws Exception{
  String t=fixtureTemplate().replace("\r\n","\n").replace("\n",newline);
  var missing=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("mode = \"false\"","")));assertTrue(missing.getMessage().contains("auth.mode"));
  var typo=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("[pack.downloads]","[pack.downloads]"+newline+"unknown = 'secret-marker'")));assertTrue(typo.getMessage().contains("pack.downloads.unknown"));assertFalse(typo.getMessage().contains("secret-marker"));
  var unsafe=assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("[pack.downloads]","[pack.downloads]"+newline+"\"private secret-marker\" = true")));assertFalse(unsafe.getMessage().contains("secret-marker"));
 }
 @Test void legacyIntegrationSwitchIsIgnored()throws Exception{assertNotNull(ServerSettings.parse(fixtureTemplate()+"\n[integrations]\nluckperms = false\n"));assertNotNull(ServerSettings.parse(fixtureTemplate()+"\n[integrations]\nluckperms = true\n"));}
 @Test void upgradeAddsEditableSettingsAndCommentsWithoutChangingOwnersValues()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String original=fixtureTemplate().replace("password = \"\"","password = \"owner-secret\"").replace("localRadius = 100","localRadius = 42").replace("group = true", "# Owner chose the available channels\ngroup = false").replace("totp = \"optional\"","");
  original=original.substring(0,original.indexOf("[spark]"))+original.substring(original.indexOf("[chat]"));
  Files.writeString(file,original);var settings=ServerSettings.load(root);String expanded=Files.readString(file);
  assertEquals(42,settings.number("chat.localRadius"));assertFalse(settings.flag("chat.group"));assertEquals("owner-secret",settings.text("database.password"));
  assertTrue(expanded.contains("# Owner chose the available channels"));assertTrue(expanded.contains("totp = \"optional\""));assertTrue(expanded.contains("[spark]"));assertTrue(expanded.contains("# Как долго нагрузка"));
  try(var files=Files.list(file.getParent())){var backups=files.filter(p->p.toString().endsWith(".toml.bak")).toList();assertEquals(1,backups.size());assertEquals(original,Files.readString(backups.getFirst()));}
  ServerSettings.load(root);assertEquals(expanded,Files.readString(file));
  try(var files=Files.list(file.getParent())){assertEquals(1,files.filter(p->p.toString().endsWith(".toml.bak")).count());}
 }
 @Test void upgradePreservesWindowsLineEndingsAndMultilineStrings()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String original=fixtureTemplate().replace("\r\n","\n").replace("totp = \"optional\"","").replace("tab = \"auto\"", "tab = \"rivet\"");
  original=original.replace("help = \"Откройте меню командой /rivet. За помощью обратитесь к администрации сервера.\"", "help = '''\n[chat]\nLiteral content, not a section\n''' ");
  original=original.replace("\n","\r\n");Files.writeString(file,original);ServerSettings.load(root);
  String expanded=Files.readString(file);assertFalse(expanded.replace("\r\n", "").contains("\n"));assertTrue(expanded.contains("help = '''\r\n[chat]\r\nLiteral content, not a section\r\n''' "));assertEquals("rivet",ServerSettings.parse(expanded).text("display.tab"));
 }
 @Test void invalidUpgradeLeavesConfigUntouchedAndCreatesNoBackup()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());String invalid=fixtureTemplate().replace("totp = \"optional\"","").replace("localRadius = 100","localRadius = 0");Files.writeString(file,invalid);
  assertThrows(IllegalArgumentException.class,()->ServerSettings.load(root));assertEquals(invalid,Files.readString(file));try(var files=Files.list(file.getParent())){assertEquals(1,files.count());}
 }
 @Test void upgradeSupportsQuotedSectionsAndRootDottedKeys()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());
  String text=fixtureTemplate().replace("[chat]", "[\"chat\"]").replace("localRadius = 100", "");Files.writeString(file,text);assertEquals(100,ServerSettings.load(root).number("chat.localRadius"));
  text=fixtureTemplate();int start=text.indexOf("[chat]"),end=text.indexOf("[display]");
  String dotted=text.substring(start,end).replace("[chat]", "").lines().map(line->line.contains(" = ")&&!line.stripLeading().startsWith("#")?"chat."+line:line).collect(java.util.stream.Collectors.joining("\n"));
  text=dotted.replace("chat.localRadius = 100", "")+"\n"+text.substring(0,start)+text.substring(end);Files.writeString(file,text);assertEquals(100,ServerSettings.load(root).number("chat.localRadius"));assertFalse(Files.readString(file).contains("[chat]"));
 }
 @Test void removedGithubProjectSettingsAreNeverAddedButExistingOwnerTextSurvives()throws Exception{
  String template=fixtureTemplate();assertFalse(template.contains("[project]"));
  String original="[project]\nrepository = 'https://github.com/owner/old-pack'\nrequirePack = true\n\n"+template;
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,original);
  assertNotNull(ServerSettings.load(root));assertEquals(original,Files.readString(file));
 }
 @Test void cavePermissionUpgradePreservesOwnerValues()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());String old=fixtureTemplate().replace("caves = true", "");Files.writeString(file,old);
  assertTrue(ServerSettings.load(root).flag("map.caves"));assertTrue(Files.readString(file).contains("caves = true"));Files.writeString(file,Files.readString(file).replace("caves = true","caves = false"));assertFalse(ServerSettings.load(root).flag("map.caves"));assertTrue(Files.readString(file).contains("caves = false"));
 }
 @Test void obsoleteExchangeSettingsAreIgnoredAndNeverAdded()throws Exception{
  String template=fixtureTemplate();assertFalse(template.contains("shareTiles"));assertFalse(template.contains("sharing ="));
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());String old=template.replace("[map]","[map]\nsharing = true\nshareTiles = 16384");Files.writeString(file,old);assertNotNull(ServerSettings.load(root));assertEquals(old,Files.readString(file));
 }

 @Test void positionSettingsUpgradePreservesOwnerChoice()throws Exception{
  Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,fixtureTemplate().replace("positions = true", "").replace("nearbyRadius = 128", ""));
  var settings=ServerSettings.load(root);assertTrue(settings.flag("map.positions"));assertEquals(128,settings.number("map.nearbyRadius"));
  String chosen=Files.readString(file).replace("positions = true","positions = false").replace("nearbyRadius = 128","nearbyRadius = 64");Files.writeString(file,chosen);assertFalse(ServerSettings.load(root).flag("map.positions"));assertEquals(chosen,Files.readString(file));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(chosen.replace("nearbyRadius = 64","nearbyRadius = 0")));
 }

 @Test void packProtectionMigratesWithCommentsAndKeepsCustomLimits()throws Exception{
  String t=fixtureTemplate();int start=t.indexOf("[pack.protection]"),end=t.indexOf("[pack.downloads]",start);String old=t.substring(0,start)+t.substring(end);Path file=root.resolve("config/rivet-server.toml");Files.createDirectories(file.getParent());Files.writeString(file,old);
  assertEquals(16,ServerSettings.load(root).number("pack.protection.connectionsPerAddress"));assertTrue(Files.readString(file).contains("[pack.protection]"));assertTrue(Files.readString(file).contains("NAT"));
  Files.writeString(file,Files.readString(file).replace("connectionsPerAddress = 16","connectionsPerAddress = 32"));assertEquals(32,ServerSettings.load(root).number("pack.protection.connectionsPerAddress"));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(t.replace("idleSeconds = 30","idleSeconds = 0")));
 }
}
