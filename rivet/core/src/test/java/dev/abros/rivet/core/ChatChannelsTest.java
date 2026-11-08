package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatChannelsTest {
 @Test void personalChannelIsAddressedAndMayBeDefault()throws Exception{
  assertEquals(new ChatChannels.Message(ChatChannels.Channel.PERSONAL,"hello","Player"),ChatChannels.parse("@Player: hello",true));
  assertEquals("@Player: hello",ChatChannels.draft("!hello",true,ChatChannels.Channel.PERSONAL,"Player",256).orElseThrow());
  assertEquals(ChatChannels.Channel.PERSONAL,ChatChannels.parse("Player: hello",true,"!","#","~","").channel());
  String t=ServerSettings.template().replace("personalTrigger = \"@\"","personalTrigger = \"\"").replace("localTrigger = \"\"","localTrigger = \"~\"");assertNotNull(ServerSettings.parse(t));
  assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("personalTrigger = \"@\"","personalTrigger = \"#\"")));
 }
 @Test void anySingleChannelCanUseEmptyTrigger()throws Exception{
  String template=ServerSettings.template();
  assertNotNull(ServerSettings.parse(template.replace("globalTrigger = \"!\"","globalTrigger = \"\"").replace("localTrigger = \"\"","localTrigger = \"~\"")));
  assertNotNull(ServerSettings.parse(template.replace("groupTrigger = \"#\"","groupTrigger = \"\"").replace("localTrigger = \"\"","localTrigger = \"~\"")));
  assertEquals(ChatChannels.Channel.GLOBAL,ChatChannels.parse("hello",true,"","#","~").channel());
  assertEquals(ChatChannels.Channel.GROUP,ChatChannels.parse("#Town: hello",true,"","#","~").channel());
  assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"hello","Town"),ChatChannels.parse("Town: hello",true,"!","","~"));
  assertEquals(ChatChannels.Channel.GLOBAL,ChatChannels.parse("!hello",true,"!","","~").channel());
  assertEquals("hello",ChatChannels.draft("~hello",true,ChatChannels.Channel.GLOBAL,"",256,"","#","~").orElseThrow());
  assertTrue(ChatChannels.draft("~#private",true,ChatChannels.Channel.GLOBAL,"",256,"","#","~").isEmpty());
  assertEquals("Town: hello",ChatChannels.draft("!hello",true,ChatChannels.Channel.GROUP,"Town",256,"!","","~").orElseThrow());
 }
 @Test void everyChannelRejectsDuplicateOverlappingAndWhitespaceTriggers()throws Exception{
  String template=ServerSettings.template();
  String[][] invalid={{"!","!",""},{"!","!!",""},{"!","#","!"},{"!","#","##"},{" "," "," "},{"!","#"," "},{"!","#","\u00a0"}};
  for(String[] values:invalid){String config=template.replace("globalTrigger = \"!\"","globalTrigger = \""+values[0]+"\"").replace("groupTrigger = \"#\"","groupTrigger = \""+values[1]+"\"").replace("localTrigger = \"\"","localTrigger = \""+values[2]+"\"");assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(config));}
 }
 @Test void explicitLocalTriggerNeverFallsBackToGlobal(){
  assertEquals(new ChatChannels.Message(ChatChannels.Channel.LOCAL,"hello",""),ChatChannels.parse("~hello",false,"!","#","~"));
  assertTrue(ChatChannels.draft("hello",false,ChatChannels.Channel.LOCAL,"",256,"!","#","~").isEmpty());
  assertEquals("~ hello",ChatChannels.draft("!hello",true,ChatChannels.Channel.LOCAL,"",256,"!","#","~").orElseThrow());
  assertEquals(ChatChannels.Channel.LOCAL,ChatChannels.parse("hello",true,"!","#","~").channel());
 }
 @Test void customTriggersRouteAndPreserveAudience()throws Exception{
  assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"hello","My Town"),ChatChannels.parse("##My Town: hello",true,"!!","##"));
  assertEquals(ChatChannels.Channel.LOCAL,ChatChannels.parse("!hello",true,"!!","##").channel());
  assertEquals("##Town: hello",ChatChannels.draft("!!hello",true,ChatChannels.Channel.GROUP,"Town",256,"!!","##").orElseThrow());
  assertEquals("!! hello",ChatChannels.draft("##Town: hello",true,ChatChannels.Channel.GLOBAL,"",256,"!!","##").orElseThrow());
  assertTrue(ChatChannels.draft("!!##private",true,ChatChannels.Channel.LOCAL,"",256,"!!","##").isEmpty());
  assertTrue(ChatChannels.draft("hello",true,ChatChannels.Channel.GLOBAL,"",6,"!!","##").isEmpty());
  String template=ServerSettings.template();
  for(String invalid:new String[]{"", "#", "##", "/", "a b", ":", "123456789"})assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(template.replace("globalTrigger = \"!\"","globalTrigger = \""+invalid+"\"")));
  assertEquals("!",ServerSettings.parse(template.replace("globalTrigger = \"!\"","")).text("chat.globalTrigger"));
 }
 @Test void globalPrefixIsRemovedAndLocalTextIsPreserved(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GLOBAL,"привет",""),ChatChannels.parse("!привет",true));assertEquals("привет",ChatChannels.parse("! привет",true).text());assertEquals(ChatChannels.Channel.LOCAL,ChatChannels.parse("привет",true).channel());assertEquals(ChatChannels.Channel.GLOBAL,ChatChannels.parse("привет",false).channel());assertEquals("!важно",ChatChannels.parse("!!важно",true).text());}
 @Test void groupNamesMayContainSpaces(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет","Город у моря"),ChatChannels.parse("#Город у моря: привет",true));assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет",""),ChatChannels.parse("#привет",true));assertTrue(ChatChannels.parse("!",true).text().isEmpty());assertTrue(ChatChannels.parse("#Город:",true).text().isEmpty());}
 @Test void channelColorsAreStrictHex(){assertEquals(0x83C6C4,ChatChannels.color("#83c6c4"));for(String value:new String[]{"red","83C6C4","#123","#12345678","#xx0000"})assertThrows(IllegalArgumentException.class,()->ChatChannels.color(value));}
 @Test void settingsRejectMalformedChannelColor()throws Exception{assertEquals("#83C6C4",ServerSettings.parse(ServerSettings.template()).text("chat.localColor"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("localColor = \"#83C6C4\"","localColor = \"red\"")));}
 @Test void switchingAudiencePreservesBodyWithoutSendingCommandsOrTruncating(){
  assertEquals("#Town: hello",ChatChannels.draft("! hello",true,ChatChannels.Channel.GROUP,"Town",256).orElseThrow());
  assertEquals("! hello",ChatChannels.draft("#Town: hello",true,ChatChannels.Channel.GLOBAL,"",256).orElseThrow());
  assertEquals("hello",ChatChannels.draft("#Town: hello",true,ChatChannels.Channel.LOCAL,"",256).orElseThrow());
  assertTrue(ChatChannels.draft("/tp name",true,ChatChannels.Channel.GLOBAL,"",256).isEmpty());
  assertTrue(ChatChannels.draft("  /tp name",true,ChatChannels.Channel.GLOBAL,"",256).isEmpty());
  for(String body:new String[]{"!! text","!#text","!/command"})assertTrue(ChatChannels.draft(body,true,ChatChannels.Channel.LOCAL,"",256).isEmpty());
  assertTrue(ChatChannels.draft("hello",false,ChatChannels.Channel.LOCAL,"",256).isEmpty());
  assertTrue(ChatChannels.draft("hello",true,ChatChannels.Channel.GROUP,"bad:name",256).isEmpty());
  assertTrue(ChatChannels.draft("x".repeat(256),true,ChatChannels.Channel.GLOBAL,"",256).isEmpty());
 }
}
