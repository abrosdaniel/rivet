package dev.abros.rivet.client;

import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** Transient live-chat attention; history never invokes this path. */
final class ChatMentionNotice {
 private static long next;
 static void reset(){next=0;}
 static void received(UUID sender,String body){
  var mc=Minecraft.getInstance();if(mc.player==null||!SocialSettings.INSTANCE.mentions||!SocialSettings.chatEnabled()||sender.equals(mc.player.getUUID())||mc.getPlayerSocialManager().shouldHideMessageFrom(sender))return;
  if(!ChatMentions.contains(body,mc.player.getGameProfile().getName()))return;
  var preferences=RivetHud.snapshot.getAsJsonObject("preferences");if(preferences!=null&&preferences.has("snoozeUntil")&&preferences.get("snoozeUntil").getAsLong()>System.currentTimeMillis())return;
  long now=net.minecraft.Util.getMillis();if(now<next)return;next=now+5000;
  RivetHud.offer(new HudNoticeQueue.Notice("","chat",sender.toString(),"mention",Client.text("chat.mentioned"),body.substring(0,Math.min(160,body.length())),HudNoticeQueue.Priority.ORDINARY,"",now+15000));
 }
 static void structured(UUID sender,Component message){
  if(ChatEffectEnvelope.read(message.getStyle().getInsertion()).isPresent()){received(sender,message.getString());return;}
  for(var child:message.getSiblings())structured(sender,child);
 }
 private ChatMentionNotice(){}
}
