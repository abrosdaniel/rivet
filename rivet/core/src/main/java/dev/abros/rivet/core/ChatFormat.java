package dev.abros.rivet.core;
import java.util.*;
/** Server-owned template. Token values are inserted once, never parsed as templates. */
public final class ChatFormat {
 public static final String DEFAULT = "$channel$head$prefix$nickname$suffix: $message";
 public static final String CHANNEL_DEFAULT = "[$channel] | ";
 public record Part(String literal,String token) {}
 public static List<Part> parse(String format,boolean channel){
  String key=channel?"chat.channelFormat":"chat.format";
  if(format.length()>512||format.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(key+dev.abros.rivet.core.Messages.text("rivet.core.up_to_512_characters_no_line_520cd4c0"));
  var allowed=channel?Set.of("channel"):Set.of("channel","head","prefix","nickname","suffix","message");
  var parts=new ArrayList<Part>();var literal=new StringBuilder();var seen=new HashSet<String>();
  for(int i=0;i<format.length();){char c=format.charAt(i++);if(c!='$'){literal.append(c);continue;}
   if(i<format.length()&&format.charAt(i)=='$'){literal.append('$');i++;continue;}
   int start=i;while(i<format.length()&&(Character.isLetterOrDigit(format.charAt(i))||format.charAt(i)=='_'))i++;
   String token=format.substring(start,i);
   if(!allowed.contains(token))throw new IllegalArgumentException(key+dev.abros.rivet.core.Messages.text("rivet.core.unknown_variable_use_for_a_dollar_3b4330ed"));
   if(!seen.add(token))throw new IllegalArgumentException(key+dev.abros.rivet.core.Messages.text("rivet.core.variables_must_not_be_repeated_9f70b9c6"));
   if(!literal.isEmpty()){parts.add(new Part(literal.toString(),null));literal.setLength(0);}parts.add(new Part(null,token));
  }
  if(!literal.isEmpty())parts.add(new Part(literal.toString(),null));
  if(!channel&&!seen.containsAll(Set.of("nickname","message")))throw new IllegalArgumentException(key+dev.abros.rivet.core.Messages.text("rivet.core.nickname_and_message_are_required_96fa59b5"));
  return List.copyOf(parts);
 }
 private ChatFormat(){}
}
