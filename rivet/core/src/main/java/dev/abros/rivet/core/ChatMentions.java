package dev.abros.rivet.core;

import java.util.regex.Pattern;

/** Exact account names only: no prefix matches, email addresses, or mass-mention syntax. */
public final class ChatMentions {
 private static final Pattern NAME=Pattern.compile("(?<![\\p{L}\\p{N}_@])@([A-Za-z0-9_]{1,16})(?![\\p{L}\\p{N}_])");
 public static boolean contains(String message,String name){
  if(message==null||name==null||!dev.abros.rivet.core.auth.AuthStore.validName(name))return false;
  var matches=NAME.matcher(message);while(matches.find())if(matches.group(1).equalsIgnoreCase(name))return true;return false;
 }
 private ChatMentions(){}
}
