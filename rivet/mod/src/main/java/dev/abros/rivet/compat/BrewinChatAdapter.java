package dev.abros.rivet.compat;

import dev.abros.rivet.client.ClientChat;
import net.minecraft.network.chat.*;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import java.util.function.Consumer;

/** Isolates B&C's vanilla-envelope assumption; signatures and original effect logic stay untouched. */
public final class BrewinChatAdapter {
 private BrewinChatAdapter(){}
 public static void receive(ClientChatReceivedEvent.Player event,Consumer<ClientChatReceivedEvent.Player> effect){
  var original=event.getMessage();var bound=event.getBoundChatType();
  if(event.isCanceled()||bound==null||!(bound.chatType().is(ChatType.CHAT)||bound.chatType().is(ChatType.MSG_COMMAND_INCOMING)||bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING))||!ClientChat.formatted(original)){effect.accept(event);return;}
  var envelope=bound.decorate(event.getPlayerChatMessage().decoratedContent());
  var nativeEvent=new ClientChatReceivedEvent.Player(bound,envelope,event.getPlayerChatMessage(),event.getSender());
  effect.accept(nativeEvent);
  if(nativeEvent.isCanceled()){event.setCanceled(true);return;}
  if(nativeEvent.getMessage().equals(envelope))return;
  // B&C appends its transformed body to getStyledChatPrefix; keep the body's rich styles/actions.
  var parts=nativeEvent.getMessage().getSiblings();
  if(parts.isEmpty()){event.setMessage(nativeEvent.getMessage());return;}
  if(bound.chatType().is(ChatType.CHAT))ClientChat.formatPublic(event,parts.getLast());
  else ClientChat.formatPersonal(event,parts.getLast());
 }
}
