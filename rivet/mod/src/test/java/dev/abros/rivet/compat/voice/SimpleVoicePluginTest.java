package dev.abros.rivet.compat.voice;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.events.*;
import dev.abros.rivet.core.VoiceMutes;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleVoicePluginTest {
 @TempDir Path root;
 private final Map<Class<?>,Consumer<?>> events=new HashMap<>();
 @BeforeEach void register(){new SimpleVoicePlugin().registerEvents(new EventRegistration(){public <T extends Event>void registerEvent(Class<T> type,Consumer<T> listener,int priority){events.put(type,listener);}});}
 @AfterEach void reset(){SimpleVoiceBridge.client=null;SimpleVoiceBridge.mutes=null;SimpleVoiceBridge.registered=false;SimpleVoiceBridge.serverReady=false;}
 @SuppressWarnings("unchecked") private <T extends Event>void fire(Class<T> type,T event){((Consumer<T>)events.get(type)).accept(event);}
 @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Function<String,Object> value){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,args)->value.apply(m.getName()));}
 private boolean microphone(UUID sender,boolean alreadyCancelled){
  boolean[] cancelled={alreadyCancelled};var player=proxy(ServerPlayer.class,m->m.equals("getUuid")?sender:null);var connection=proxy(VoicechatConnection.class,m->m.equals("getPlayer")?player:null);
  var event=proxy(MicrophonePacketEvent.class,m->switch(m){case "getSenderConnection"->sender==null?null:connection;case "cancel"->{cancelled[0]=true;yield true;}case "isCancelled"->cancelled[0];case "isCancellable"->true;default->null;});
  fire(MicrophonePacketEvent.class,event);return cancelled[0];
 }
 @Test void suppressesOnlyMutedSenderBeforeAnyVoiceRouting()throws Exception{
  var player=UUID.randomUUID();SimpleVoiceBridge.mutes=new VoiceMutes(root.resolve("mutes.json"),System.currentTimeMillis());SimpleVoiceBridge.mutes.mute(player,1,System.currentTimeMillis());
  assertTrue(microphone(player,false));assertFalse(microphone(UUID.randomUUID(),false));assertFalse(microphone(null,false));assertTrue(microphone(UUID.randomUUID(),true));
 }
 @Test void reconnectAndNewPluginInstanceDoNotClearTheDeadline()throws Exception{
  var file=root.resolve("mutes.json");var player=UUID.randomUUID();SimpleVoiceBridge.mutes=new VoiceMutes(file,System.currentTimeMillis());SimpleVoiceBridge.mutes.mute(player,1,System.currentTimeMillis());
  register();SimpleVoiceBridge.mutes=new VoiceMutes(file,System.currentTimeMillis());assertTrue(microphone(player,false));
 }
 @Test void clientDiagnosticsReadCurrentApiStateAndLifecycleClearsReady(){
  boolean[] flags={false,true,false};var api=proxy(VoicechatClientApi.class,m->switch(m){case "isDisconnected"->flags[0];case "isMuted"->flags[1];case "isDisabled"->flags[2];default->null;});
  fire(ClientVoicechatInitializationEvent.class,proxy(ClientVoicechatInitializationEvent.class,m->m.equals("getVoicechat")?api:null));
  assertEquals(new SimpleVoiceBridge.ClientState(false,true,false),SimpleVoiceBridge.client.get());flags[0]=true;flags[1]=false;assertEquals(new SimpleVoiceBridge.ClientState(true,false,false),SimpleVoiceBridge.client.get());
  fire(VoicechatServerStartedEvent.class,null);assertTrue(SimpleVoiceBridge.serverReady);fire(VoicechatServerStoppedEvent.class,null);assertFalse(SimpleVoiceBridge.serverReady);
 }
}
