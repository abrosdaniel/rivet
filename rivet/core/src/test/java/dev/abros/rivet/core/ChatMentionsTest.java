package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatMentionsTest {
 @Test void exactAccountNamesWithBoundaries(){for(String message:new String[]{"@Dan", "привет, @dAn!", "(@Dan)", "@Other @Dan", "строка\n@Dan"})assertTrue(ChatMentions.contains(message,"Dan"),message);assertTrue(ChatMentions.contains("@_ToT_KeKs_", "_tot_keks_"));}
 @Test void noPrefixEmailOrUnicodeWordMatches(){for(String message:new String[]{"@Daniel", "mail@Dan", "mail_@Dan", "@@Dan", "я@Dan", "@Danя", "@Dan_", "@Dan2", "Dan", "@everyone"})assertFalse(ChatMentions.contains(message,"Dan"),message);}
 @Test void boundedNamesAndMissingInput(){assertFalse(ChatMentions.contains(null,"Dan"));assertFalse(ChatMentions.contains("@Dan",null));assertFalse(ChatMentions.contains("@12345678901234567","12345678901234567"));assertTrue(ChatMentions.contains("@1234567890123456","1234567890123456"));}
}
