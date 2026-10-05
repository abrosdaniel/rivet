package dev.abros.rivet.server;
import java.util.*;
/** Optional Plasmo Voice 2 API. No command-name or nickname based punishment. */
public final class PlasmoVoiceAdapter implements dev.abros.rivet.server.compat.CompatibilityAdapter {
 private static final dev.abros.rivet.core.OptionalIntegration LIFECYCLE=dev.abros.rivet.compat.IntegrationSupport.capability("plasmovoice","moderation",PlasmoVoiceAdapter::contract);
 private static String contract()throws ReflectiveOperationException{Class.forName("su.plo.voice.server.ModVoiceServer").getField("INSTANCE");Class.forName("su.plo.voice.api.server.PlasmoVoiceServer").getMethod("getMuteManager");Class<?> api=Class.forName("su.plo.voice.api.server.mute.MuteManager"),unit=Class.forName("su.plo.voice.api.server.mute.MuteDurationUnit");api.getMethod("getMute",UUID.class);try{api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class,boolean.class);}catch(NoSuchMethodException older){api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class);}return "Plasmo Voice 2 · MuteManager";}
 public com.google.gson.JsonObject diagnostics(){var row=LIFECYCLE.diagnostics();row.addProperty("id",id());row.add("capabilities",dev.abros.rivet.core.Json.GSON.toJsonTree(java.util.List.of("voice-mute")));return row;}
 public dev.abros.rivet.core.OptionalIntegration lifecycle(){return LIFECYCLE;}
 public String id(){return "plasmovoice";}public String status(){return LIFECYCLE.status();}public void clear(){LIFECYCLE.reset();}
 public com.google.gson.JsonObject execute(net.minecraft.server.level.ServerPlayer actor,com.google.gson.JsonObject request,dev.abros.rivet.server.compat.ServerIdentityDirectory identities){throw new IllegalArgumentException("Plasmo Voice: используйте защищённые операции модерации Rivet");}
 static boolean available(){return LIFECYCLE.available();}
 static void mute(UUID target,int minutes,String reason)throws Exception{LIFECYCLE.required(()->{applyMute(target,minutes,reason);return true;});}

 private static Object manager()throws ReflectiveOperationException{Object instance=Class.forName("su.plo.voice.server.ModVoiceServer").getField("INSTANCE").get(null);if(instance==null)throw new IllegalStateException("Plasmo Voice unavailable");return Class.forName("su.plo.voice.api.server.PlasmoVoiceServer").getMethod("getMuteManager").invoke(instance);}
 @SuppressWarnings({"unchecked","rawtypes"}) static void applyMute(UUID target,int minutes,String reason)throws Exception{
  var m=manager();Class<?> api=Class.forName("su.plo.voice.api.server.mute.MuteManager"),unit=Class.forName("su.plo.voice.api.server.mute.MuteDurationUnit");
  if(((Optional<?>)api.getMethod("getMute",UUID.class).invoke(m,target)).isPresent())throw new IllegalArgumentException("голос уже отключён; существующее наказание сохранено");
  Object minute=Enum.valueOf((Class)unit,"MINUTE");Object result;
  try{try{result=api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class,boolean.class).invoke(m,target,null,(long)minutes,minute,reason,false);}catch(NoSuchMethodException older){result=api.getMethod("mute",UUID.class,UUID.class,long.class,unit,String.class).invoke(m,target,null,(long)minutes,minute,reason);}}catch(java.lang.reflect.InvocationTargetException rejected){if(rejected.getCause() instanceof IllegalArgumentException expected)throw expected;throw rejected;}
  if(!(result instanceof Optional<?> applied)||applied.isEmpty())throw new IllegalArgumentException("Plasmo Voice не подтвердил mute");
 }
}
