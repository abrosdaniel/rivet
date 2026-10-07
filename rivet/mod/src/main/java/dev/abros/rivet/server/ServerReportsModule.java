package dev.abros.rivet.server;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
import java.util.concurrent.*;
import static dev.abros.rivet.server.ServerFeatures.*;
/** Owns report storage, request handlers and retention independently of tasks and groups. */
final class ServerReportsModule {
 private static CommunityReports reports;private static long lastPrune;
 static void start(){reports=new CommunityReports(communityStore());lastPrune=0;}
 static void stop(){reports=null;lastPrune=0;}
 static CommunityReports store(){return reports;}
 static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event)->{
  var store=reports;long now=System.currentTimeMillis();if(store==null||now-lastPrune<3600000)return;
  try{storage(()->{try{store.prune(now);}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Rivet report cleanup failed",failure);}});lastPrune=now;}catch(RejectedExecutionException busy){lastPrune=now-3600000+60000;}
 });}
 static boolean handle(ServerPlayer p,JsonObject j)throws Exception{
  String action=Json.opt(j,"action","");if(!Set.of("report","myReport","myReports","reportManage","reply","reports").contains(action))return false;
  ServerDatabase.settings().modules().require("reports");
  var resource=reports;
            if(action.equals("report")){UUID.fromString(Json.str(j,"operationId"));var store=reports;var copy=j.deepCopy();storage(()->{try{var receipt=store.submitRequest(p.getUUID(),p.getGameProfile().getName(),copy,System.currentTimeMillis());p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(copy,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});return true;}
            if(action.equals("myReport")){String id=Json.str(j,"id");var store=reports;read(()->{try{var data=message("myReports","");data.addProperty("page",0);data.addProperty("request",Json.opt(j,"request",""));var entries=new JsonArray();entries.add(store.get(p.getUUID(),id));data.add("reports",entries);p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return true;}
            if(action.equals("myReports")){int page=bounded(j,"page",1000);read(()->{try{var data=message("myReports","");data.addProperty("page",page);data.addProperty("request",Json.opt(j,"request",""));var batch=resource.cursor(p.getUUID(),Json.opt(j,"cursor",""));data.add("reports",batch.get("entries"));data.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->send(p,data));}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});return true;}
            if(!allowedAction(p,action))throw new IllegalArgumentException("Недостаточно прав");
            switch(action){
                case "reportManage" -> {var copy=j.deepCopy();if(Json.opt(copy,"operation","").startsWith("bulk")&&!ServerIntegration.supports(p,"community-extensions"))throw new IllegalArgumentException("Обновите Rivet для массовых действий");if(Json.opt(copy,"operation","").equals("staff")){var result=message("community","");result.addProperty("request",Json.opt(copy,"request",""));var staff=new JsonArray();for(var candidate:p.server.getPlayerList().getPlayers())if(AuthServer.authenticated(candidate)&&allowedAction(candidate,"reply")){var row=new JsonObject();row.addProperty("id",candidate.getUUID().toString());row.addProperty("name",candidate.getGameProfile().getName());staff.add(row);}result.add("staff",staff);send(p,result);return true;}if(Json.opt(copy,"operation","").equals("assign")){var target=p.server.getPlayerList().getPlayer(UUID.fromString(Json.str(copy,"target")));if(target==null||!AuthServer.authenticated(target)||!allowedAction(target,"reply"))throw new IllegalArgumentException("Администратор недоступен или не может работать с обращениями");copy.addProperty("targetName",target.getGameProfile().getName());}var author=p.getGameProfile().getName();storage(()->{try{if(!p.server.submit(()->AuthServer.authenticated(p)&&allowedAction(p,"reply")).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException("Недостаточно прав");var response=resource.manageRequest(p.getUUID().toString(),author,copy);response.addProperty("request",Json.opt(copy,"request",""));p.server.execute(()->send(p,response));}catch(Exception ex){p.server.execute(()->sendFailure(p,copy,ex));}});}
                case "reply" -> {UUID.fromString(Json.str(j,"operationId"));String id=Json.str(j,"id"),text=Json.str(j,"text");boolean resolved=j.get("resolved").getAsBoolean();var store=reports;String author=p.getGameProfile().getName();storage(()->{try{if(!p.server.submit(()->p.connection.getConnection().isConnected()&&AuthServer.authenticated(p)&&allowedAction(p,action)).get(2,TimeUnit.SECONDS))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,"Недостаточно прав");var receipt=store.replyRequest(p.getUUID().toString(),author,j);p.server.execute(()->{receipt.addProperty("kind","result");receipt.addProperty("request",Json.opt(j,"request",""));send(p,receipt);invalidate(p.server);});}catch(Exception ex){p.server.execute(()->sendFailure(p,j,ex));}});}
                case "reports" -> {int page=j.get("page").getAsInt();var store=reports;storage(()->{try{var result=message("reports","");result.addProperty("page",page);result.addProperty("request",Json.opt(j,"request",""));var batch=store.queue(p.getUUID().toString(),Json.opt(j,"filter","all"),Json.opt(j,"query",""),Json.opt(j,"cursor",""));result.add("reports",batch.get("entries"));result.add("nextCursor",batch.get("nextCursor"));p.server.execute(()->{if(allowedAction(p,action))send(p,result);});}catch(Exception ex){p.server.execute(()->send(p,message("result",safeError(ex))));}});}
            }
            return true;
 }
}
