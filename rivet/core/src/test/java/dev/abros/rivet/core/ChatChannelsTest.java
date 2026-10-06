package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatChannelsTest {
 @Test void globalPrefixIsRemovedAndLocalTextIsPreserved(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GLOBAL,"привет",""),ChatChannels.parse("!привет",true));assertEquals("привет",ChatChannels.parse("! привет",true).text());assertEquals(ChatChannels.Channel.LOCAL,ChatChannels.parse("привет",true).channel());assertEquals(ChatChannels.Channel.GLOBAL,ChatChannels.parse("привет",false).channel());assertEquals("!важно",ChatChannels.parse("!!важно",true).text());}
 @Test void groupNamesMayContainSpaces(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет","Город у моря"),ChatChannels.parse("#Город у моря: привет",true));assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет",""),ChatChannels.parse("#привет",true));assertTrue(ChatChannels.parse("!",true).text().isEmpty());assertTrue(ChatChannels.parse("#Город:",true).text().isEmpty());}
 @Test void channelColorsAreStrictHex(){assertEquals(0x83C6C4,ChatChannels.color("#83c6c4"));for(String value:new String[]{"red","83C6C4","#123","#12345678","#xx0000"})assertThrows(IllegalArgumentException.class,()->ChatChannels.color(value));}
 @Test void settingsRejectMalformedChannelColor()throws Exception{assertEquals("#83C6C4",ServerSettings.parse(ServerSettings.template()).text("chat.localColor"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace("localColor = \"#83C6C4\"","localColor = \"red\"")));}
}
