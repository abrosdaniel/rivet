package dev.abros.rivet.server;

import dev.abros.rivet.core.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.*;
import java.util.*;
import java.util.concurrent.*;

final class ServerCommands {
 static final List<String> RIGHTS=List.of("rivet.stats.edit","rivet.stats.view","rivet.announce","rivet.maintenance","rivet.restart","rivet.reports","rivet.diagnostics");
 static boolean allowed(CommandSourceStack s,String right){return s.getEntity() instanceof ServerPlayer p?allowed(p,right):s.getEntity()==null&&s.hasPermission(4);}
 static boolean allowed(ServerPlayer p,String right){if(!p.connection.getConnection().isConnected()||!AuthServer.authenticated(p))return false;if(p.hasPermissions(2))return true;if(!ServerIntegration.luckPermsEnabled())return false;var caps=LuckPermsAdapter.profile(p).getAsJsonObject("capabilities");return caps.has("rivet.admin")&&caps.get("rivet.admin").getAsBoolean()||caps.has(right)&&caps.get(right).getAsBoolean();}
 static boolean staff(ServerPlayer p){return ServerFeatures.admin(p)||RIGHTS.stream().filter(k->!k.startsWith("rivet.stats.")).anyMatch(k->allowed(p,k));}
 static void reply(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal(text),false);}
 static void error(CommandSourceStack s,Exception ex){try(var locale=Messages.locale(s.getEntity() instanceof ServerPlayer player?player.clientInformation().language():"ru_ru")){errorLocalized(s,ex);}}
 static void errorLocalized(CommandSourceStack s,Exception ex){s.sendFailure(Component.literal(ex instanceof IllegalArgumentException?ex.getMessage():dev.abros.rivet.core.Messages.text("rivet.core.could_not_perform_the_action_check_dfd9dd25")));if(!(ex instanceof IllegalArgumentException))com.mojang.logging.LogUtils.getLogger().warn("Rivet command failed ({})",ex.getClass().getSimpleName());}
 private static RequiredArgumentBuilder<CommandSourceStack,String> player(){return Commands.argument("player",StringArgumentType.word()).suggests((c,b)->suggest(b));}
 static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder){
  var future=new CompletableFuture<Suggestions>();try{ServerPlayerStatistics.task(()->{try{for(var p:ServerPlayerStatistics.store().find(builder.getRemaining(),false))builder.suggest(p.name(),Component.literal(p.id().toString()));future.complete(builder.build());}catch(Exception ex){future.complete(builder.build());}});}catch(Exception ex){future.complete(builder.build());}return future;
 }
 static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
  var root=Commands.literal("rivet");
  root.then(Commands.literal("help").executes(c->help(c.getSource(),""))
   .then(Commands.argument("section",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(sections(c.getSource()),b)).executes(c->help(c.getSource(),StringArgumentType.getString(c,"section")))));
  var stats=Commands.literal("stats").requires(s->allowed(s,"rivet.stats.view")||allowed(s,"rivet.stats.edit"));
  stats.then(Commands.literal("show").then(player().executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"","",0))));
  var time=Commands.literal("time").requires(s->allowed(s,"rivet.stats.edit"));
  for(String op:List.of("set","add","subtract"))time.then(Commands.literal(op).then(player().then(Commands.argument("duration",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("5h","30m","120h30m"),b)).executes(c->{try(var locale=Messages.locale(c.getSource().getEntity() instanceof ServerPlayer p?p.clientInformation().language():"ru_ru")){return run(c.getSource(),StringArgumentType.getString(c,"player"),"totalMillis",op,CommandValues.duration(StringArgumentType.getString(c,"duration")));}catch(Exception ex){error(c.getSource(),ex);return 0;}}))));
  stats.then(time);
  stats.then(Commands.literal("since").requires(s->allowed(s,"rivet.stats.edit")).then(Commands.literal("set").then(player().then(Commands.argument("date",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of(java.time.LocalDate.now().toString()),b)).executes(c->{try(var locale=Messages.locale(c.getSource().getEntity() instanceof ServerPlayer p?p.clientInformation().language():"ru_ru")){return run(c.getSource(),StringArgumentType.getString(c,"player"),"firstJoin","set",CommandValues.date(StringArgumentType.getString(c,"date")));}catch(Exception ex){error(c.getSource(),ex);return 0;}})))));
  stats.then(Commands.literal("deaths").requires(s->allowed(s,"rivet.stats.edit")).then(Commands.literal("set").then(player().then(Commands.argument("count",LongArgumentType.longArg(0)).executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"deaths","set",LongArgumentType.getLong(c,"count")))))));
  root.then(stats);
  root.then(Commands.literal("player").requires(s->allowed(s,"rivet.stats.view")||allowed(s,"rivet.stats.edit")).then(Commands.literal("info").then(player().executes(c->run(c.getSource(),StringArgumentType.getString(c,"player"),"","",0)))));

  dispatcher.register(root);
 }
 private static boolean access(CommandSourceStack s,boolean edit){return allowed(s,"rivet.stats.edit")||!edit&&allowed(s,"rivet.stats.view");}
 private static int run(CommandSourceStack source,String query,String field,String op,long value){
  try(var locale=Messages.locale(source.getEntity() instanceof ServerPlayer player?player.clientInformation().language():"ru_ru")){if(!access(source,!field.isEmpty()))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.insufficient_permissions_3f48d3d9"));
   var store=ServerPlayerStatistics.store();ServerPlayerStatistics.task(()->{try{
    var people=store.find(query,true);if(people.isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.no_player_with_saved_statistics_found_0c471f3f"));if(people.size()!=1)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.name_is_ambiguous_specify_the_server_9afb390e"));var person=people.getFirst();
    source.getServer().execute(Messages.capture(()->{try{if(!access(source,!field.isEmpty()))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.permissions_changed_980f6f62"));boolean online=source.getServer().getPlayerList().getPlayer(person.id())!=null;
     var checkpoint=ServerPlayerStatistics.capture(person.id());ServerPlayerStatistics.task(()->{try{
      if(!source.getServer().submit(()->access(source,!field.isEmpty())).get(2,TimeUnit.SECONDS))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.permissions_changed_980f6f62"));
      String text;if(field.isEmpty()){if(checkpoint!=null)store.checkpoint(checkpoint);var j=store.snapshot(person.id());text=person.name()+" · "+person.id()+"\n"+(online?dev.abros.rivet.core.Messages.text("rivet.ui.online_e858f4e3"):dev.abros.rivet.core.Messages.text("rivet.ui.offline_67b99cc9"));if(j.has("firstJoin"))text+=dev.abros.rivet.core.Messages.text("rivet.core.playing_since_cb1f89bc")+CommandValues.dateText(j.get("firstJoin").getAsLong());if(j.has("totalMillis"))text+=dev.abros.rivet.core.Messages.text("rivet.core.play_time_44a23601")+CommandValues.durationText(j.get("totalMillis").getAsLong());if(checkpoint!=null)text+=dev.abros.rivet.core.Messages.text("rivet.core.session_511ac3ab")+CommandValues.durationText(checkpoint.elapsedMillis());if(j.has("deaths"))text+=dev.abros.rivet.core.Messages.text("rivet.core.deaths_a4d0931c")+j.get("deaths").getAsLong();}
      else text=person.name()+": "+store.correct(person.id(),field,op,value,source.getTextName(),checkpoint);String answer=text;source.getServer().execute(dev.abros.rivet.core.Messages.capture(()->{if(access(source,!field.isEmpty()))reply(source,answer);}));
     }catch(Exception ex){source.getServer().execute(dev.abros.rivet.core.Messages.capture(()->error(source,ex)));}});
    }catch(Exception ex){error(source,ex);}}));
   }catch(Exception ex){source.getServer().execute(Messages.capture(()->error(source,ex)));}});return 1;
  }catch(Exception ex){error(source,ex);return 0;}
 }
 private static List<String> sections(CommandSourceStack s){var out=new ArrayList<String>();out.add("menu");if(access(s,false))out.add("stats");for(String key:List.of("announce","maintenance","restart","reports","diagnostics"))if(allowed(s,"rivet."+key))out.add(key);if(AuthServer.enabled()&&(s.getEntity()==null&&s.hasPermission(4)||s.getEntity() instanceof ServerPlayer p&&AuthServer.mayReset(p)))out.add("auth");return out;}
 private static int help(CommandSourceStack s,String section){try(var locale=Messages.locale(s.getEntity() instanceof ServerPlayer player?player.clientInformation().language():"ru_ru")){return helpLocalized(s,section);}}
 private static int helpLocalized(CommandSourceStack s,String section){var available=sections(s);if(!section.isEmpty()&&!available.contains(section)){s.sendFailure(Component.translatable("rivet.core.section_unavailable_rivet_help_738432bc"));return 0;}
  var lines=new ArrayList<String>();for(String key:available){if(!section.isEmpty()&&!section.equals(key))continue;lines.add(switch(key){
   case "stats" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_stats_show_player_rivet_player_a62551c6")+(allowed(s,"rivet.stats.edit")?dev.abros.rivet.core.Messages.text("rivet.core.rivet_stats_time_set_add_subtract_f314f17c"):"");
   case "announce" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_announce_text_rivet_announce_pin_b0527ca0");
   case "maintenance" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_maintenance_on_minutes_reason_0_5323986d");
   case "restart" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_restart_seconds_reason_rivet_restart_b86ecdf4");
   case "reports" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_reports_reports_c8ce6def");
   case "diagnostics" -> "/rivet status · /rivet project status";
   case "auth" -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_auth_reset_player_24808553")+(s.getEntity()==null?dev.abros.rivet.core.Messages.text("rivet.core.rivet_auth_reserve_block_unblock_player_06752364"):"");
   default -> dev.abros.rivet.core.Messages.text("rivet.core.rivet_server_menu_rivet_help_section_d8a52605");});}reply(s,String.join("\n\n",lines));return 1;
 }
}
