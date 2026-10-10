package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Server-owned menu data and permission-preserving moderation. */
final class ServerExtras {
    private static dev.abros.rivet.core.CommunityStore database;
    private static JsonObject menu=new JsonObject();
    static void start(Path root,dev.abros.rivet.core.CommunityStore db)throws Exception{database=db;reload();}
    static void stop(){database=null;menu=new JsonObject();}
    static void reload()throws Exception{
        menu=ServerDatabase.settings().menu();
    }
    static void menu(JsonObject value){menu=value.deepCopy();}
    static JsonObject menu(){return menu.deepCopy();}
    static JsonArray actions(ServerPlayer p){var result=new JsonArray();for(String action:List.of("tell","kick","warn","ban","pardon","kill","vmute","vunmute")){
        if(!action.equals("tell")&&!ServerDatabase.settings().modules().enabled("reports"))continue;var node=p.server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);if(node!=null&&node.canUse(p.createCommandSourceStack()))result.add(action);
    }return result;}
    static void moderate(ServerPlayer p,JsonObject j)throws Exception{
        ServerDatabase.settings().modules().require("reports");
        String action=Json.str(j,"operation");if(!List.of("kick","warn","ban","pardon","kill","vmute","vunmute").contains(action))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_29e4b3f71339"));
        String targetId=UUID.fromString(Json.str(j,"target")).toString();var target=p.server.getPlayerList().getPlayer(UUID.fromString(targetId));
        if(target==null&&!List.of("ban","pardon").contains(action))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_is_offline_3f21f49e"));
        String onlineName=target==null?null:target.getGameProfile().getName();String reason=Json.opt(j,"reason","").strip();
        if(reason.length()>300||reason.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_664d5a57425b"));
        String suffix="";
        if(action.equals("kick")||action.equals("ban")||action.equals("warn")){if(reason.isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f1d1c0ea6b81"));suffix=" "+reason;}
        if(action.equals("vmute")){int minutes=j.get("minutes").getAsInt();if(minutes<1||minutes>10080)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_92731397cab1"));suffix=" "+minutes+"m"+(reason.isEmpty()?"":" "+reason);}
        int banMinutes=action.equals("ban")&&j.has("minutes")?j.get("minutes").getAsBigDecimal().intValueExact():0;if(banMinutes<0||banMinutes>10080)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.ban_duration_0_permanent_or_1_03b70e77"));
        String arguments=suffix,actor=p.getGameProfile().getName();var connection=p.connection;var server=p.server;
        var node=server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);if(node==null||!node.canUse(p.createCommandSourceStack()))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f811092ff6a0"));
        ServerFeatures.storage(()->{try{
            var entry=new JsonObject();entry.addProperty("uuid",targetId);entry.addProperty("actor",actor);entry.addProperty("action",action);entry.addProperty("reason",reason);entry.addProperty("at",System.currentTimeMillis());dev.abros.rivet.core.LocalizedText.key("rivet.core.accepted_result_not_yet_confirmed_bf9224e5").put(entry,"outcome");if(action.equals("ban")&&banMinutes>0)entry.addProperty("durationMinutes",banMinutes);if(action.equals("vmute"))entry.addProperty("durationMinutes",j.get("minutes").getAsInt());
            String operationId=Json.str(j,"operationId");
            var receipt=database.receipt(p.getUUID().toString(),j,()->{database.record("moderation-history",operationId,entry);var claimed=new JsonObject();claimed.addProperty("accepted",true);return claimed;});
            if(receipt.has("replayed")){server.execute(dev.abros.rivet.core.Messages.capture(()->p.sendSystemMessage(Component.translatable("rivet.core.this_request_was_already_accepted_check_50d51ecc"))));return;}
            String name=onlineName==null?database.personName(targetId):onlineName;if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_73b323c0deed"));
            String command=action+" "+name+arguments;audit(actor,"moderation requested",command);
            server.execute(dev.abros.rivet.core.Messages.capture(()->{if(p.connection!=connection||!connection.getConnection().isConnected()||!AuthServer.authenticated(p)){finishModeration(operationId,entry,dev.abros.rivet.core.Messages.text("rivet.core.cancelled_moderator_session_ended_82e3837a"));return;}
                var current=server.getCommands().getDispatcher().getRoot().getChild(action.equals("warn")?"kick":action);var source=p.createCommandSourceStack();
                if(current==null||!current.canUse(source)){finishModeration(operationId,entry,dev.abros.rivet.core.Messages.text("rivet.core.denied_permissions_changed_a2e063ce"));p.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.message.error_59b77dbd5bd2")));return;}
                try{if(action.equals("warn")){var recipient=server.getPlayerList().getPlayer(UUID.fromString(targetId));if(recipient==null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_d4983d734743"));recipient.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.core.staff_warning_9f3e1c18")+reason));finishModeration(operationId,entry,dev.abros.rivet.core.Messages.text("rivet.core.applied_769ed90a"));return;}if(action.equals("ban")&&banMinutes>0){var profile=new com.mojang.authlib.GameProfile(UUID.fromString(targetId),name);if(server.getPlayerList().getBans().isBanned(profile))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_ffbaa1828683"));server.getPlayerList().getBans().add(new net.minecraft.server.players.UserBanListEntry(profile,new java.util.Date(),actor,new java.util.Date(System.currentTimeMillis()+banMinutes*60000L),reason));var recipient=server.getPlayerList().getPlayer(UUID.fromString(targetId));if(recipient!=null)recipient.connection.disconnect(Component.literal(reason));finishModeration(operationId,entry,dev.abros.rivet.core.Messages.text("rivet.core.applied_769ed90a"));return;}int result=server.getCommands().getDispatcher().execute(command,source);finishModeration(operationId,entry,result>0?dev.abros.rivet.core.Messages.text("rivet.core.applied_769ed90a"):dev.abros.rivet.core.Messages.text("rivet.core.command_execution_was_not_confirmed_ef8dda1c"));}catch(Exception ex){finishModeration(operationId,entry,dev.abros.rivet.core.Messages.text("rivet.core.application_failed_510a9bc4"));p.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.message.error_e526575316f9")));}
            }));
        }catch(Exception ex){server.execute(dev.abros.rivet.core.Messages.capture(()->p.sendSystemMessage(Component.literal(dev.abros.rivet.core.Messages.text("rivet.message.error_f12f7715bf80")))));}});
    }
    private static void finishModeration(String id,JsonObject original,String outcome){
        var entry=original.deepCopy();entry.addProperty("outcome",outcome);entry.addProperty("finishedAt",System.currentTimeMillis());
        if(outcome.equals(dev.abros.rivet.core.Messages.text("rivet.core.applied_769ed90a"))&&entry.has("durationMinutes"))entry.addProperty("until",System.currentTimeMillis()+entry.get("durationMinutes").getAsLong()*60000);
        try{ServerFeatures.storage(()->{try{database.record("moderation-history",id,entry);audit(Json.str(entry,"actor"),"moderation result",Json.str(entry,"action")+" "+Json.str(entry,"uuid")+" · "+outcome);}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Rivet: cannot persist moderation result [{}]",id);}});}catch(java.util.concurrent.RejectedExecutionException busy){com.mojang.logging.LogUtils.getLogger().error("Rivet: moderation result queue is full [{}]",id);}
    }
    static void audit(String actor,String action,String detail)throws Exception{database.audit(actor,action,detail);}
    static JsonArray history(int page)throws Exception{if(page<0||page>100)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_e83e2de9b3f0"));var out=new JsonArray();database.recordPage("audit","",page,10).forEach(out::add);return out;}
}
