package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatChannelsTest {
 @Test void globalPrefixIsRemovedAndLocalTextIsPreserved(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GLOBAL,"привет",""),ChatChannels.parse("!привет",true));assertEquals("привет",ChatChannels.parse("! привет",true).text());assertEquals(ChatChannels.Channel.LOCAL,ChatChannels.parse("привет",true).channel());assertEquals(ChatChannels.Channel.GLOBAL,ChatChannels.parse("привет",false).channel());assertEquals("!важно",ChatChannels.parse("!!важно",true).text());}
 @Test void groupNamesMayContainSpaces(){assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет","Город у моря"),ChatChannels.parse("#Город у моря: привет",true));assertEquals(new ChatChannels.Message(ChatChannels.Channel.GROUP,"привет",""),ChatChannels.parse("#привет",true));assertTrue(ChatChannels.parse("!",true).text().isEmpty());assertTrue(ChatChannels.parse("#Город:",true).text().isEmpty());}
}
