package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatFormatTest {
 @Test void defaultsAndCustomOrder(){assertEquals(7,ChatFormat.parse(ChatFormat.DEFAULT,false).size());assertEquals("nickname",ChatFormat.parse("<$nickname> $message",false).get(1).token());assertDoesNotThrow(()->ChatFormat.parse("$message ← $nickname",false));assertDoesNotThrow(()->ChatFormat.parse("",true));}
 @Test void literalDollarAndNonRecursiveTokens(){assertEquals("$ ",ChatFormat.parse("$$ $nickname: $message",false).getFirst().literal());assertEquals(1,ChatFormat.parse("$channel",true).size());}
 @Test void malformedTemplatesRejected(){for(String value:new String[]{"$nickname", "$message", "$unknown $nickname $message", "$head$head$nickname$message", "$nickname$message\n", "x".repeat(513)+"$nickname$message", "$nickname$message$", "$nicknameX$message"})assertThrows(IllegalArgumentException.class,()->ChatFormat.parse(value,false),value);assertThrows(IllegalArgumentException.class,()->ChatFormat.parse("$head",true));}
 @Test void settingsValidateAndFillNewFields()throws Exception{String old=ServerSettings.template().replace("\r\n","\n").replace("format = \""+ChatFormat.DEFAULT+"\"\n","").replace("channelFormat = \""+ChatFormat.CHANNEL_DEFAULT+"\"\n","");assertEquals(ChatFormat.DEFAULT,ServerSettings.parse(old).text("chat.format"));assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(ServerSettings.template().replace(ChatFormat.DEFAULT,"$nickname: $typo")));}
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"\n","\r\n"})
 void upgradeWritesNewFieldsAndPreservesOwnerTemplate(String newline,@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)throws Exception{
  var dir=java.nio.file.Files.createDirectories(root.resolve("config"));var file=dir.resolve("rivet-server.toml");
  String custom="$message ← $nickname";
  String old=ServerSettings.template().replace("\r\n","\n").replace(ChatFormat.DEFAULT,custom).replace("channelFormat = \""+ChatFormat.CHANNEL_DEFAULT+"\"\n","").replace("\n",newline);
  assertFalse(old.contains("channelFormat ="));
  java.nio.file.Files.writeString(file,old);
  assertEquals(custom,ServerSettings.load(root).text("chat.format"));
  String updated=java.nio.file.Files.readString(file);assertTrue(updated.contains("format = \""+custom+"\""));assertTrue(updated.contains("channelFormat = \""+ChatFormat.CHANNEL_DEFAULT+"\""));
  try(var files=java.nio.file.Files.list(dir)){var backup=files.filter(path->path.toString().endsWith(".toml.bak")).findFirst().orElseThrow();assertEquals(old,java.nio.file.Files.readString(backup));}
  ServerSettings.load(root);assertEquals(updated,java.nio.file.Files.readString(file));
 }
}
