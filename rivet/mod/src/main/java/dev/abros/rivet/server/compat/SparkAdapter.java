package dev.abros.rivet.server.compat;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.server.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Optional spark diagnostic adapter. Commands retain the requesting player's permissions. */
public final class SparkAdapter implements CompatibilityAdapter {
    private final OptionalIntegration integration=dev.abros.rivet.compat.IntegrationSupport.capability("spark","diagnostics",SparkMetrics::probe);
    public OptionalIntegration lifecycle(){return integration;}
    private long startupDeadline;
    public void unavailable(){current=new JsonObject();health=integration.status();}
    private SparkTimeline timeline=new SparkTimeline();
    private final ArrayDeque<JsonObject> reports=new ArrayDeque<>();
    private final Map<UUID,Long> rates=new HashMap<>();
    private final Set<UUID> muted=new HashSet<>();
    private JsonObject current=new JsonObject();
    private String health=dev.abros.rivet.core.Messages.text("rivet.core.still_loading_b735bb24"), profiler="unknown", notice="", ownerName="";
    private UUID owner;
    private long nextSample, until, probeAt, operationAt, probeSequence;
    private Object lifecycle=new Object();
    private Object lease=new Object();
    private boolean busy, dispatching;
    private Path historyFile;
    private final ExecutorService files=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"Rivet spark history");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    public JsonObject diagnostics(){var row=CompatibilityAdapter.super.diagnostics();row.addProperty("ready",!current.entrySet().isEmpty());if(row.get("reason").getAsString().isEmpty()&&installed()&&!commandsSupported())dev.abros.rivet.core.LocalizedText.key("rivet.core.profiling_has_not_been_verified_for_15517f78").put(row,"reason");var caps=new JsonArray();caps.add("metrics");if(commandsSupported()){caps.add("profiling");caps.add("reports");}row.add("capabilities",caps);return row;}
    public String id(){return "spark";}
    public String status(){return !integration.available()||!integration.diagnostics().get("reason").getAsString().isEmpty()?integration.status():health;}
    private boolean installed(){return ModList.get().isLoaded("spark");}
    private String version(){return ModList.get().getModContainerById("spark").map(c->c.getModInfo().getVersion().toString()).orElse("");}
    private boolean commandsSupported(){return version().startsWith("1.10.124");}
    @Override public void clear(){integration.reset();startupDeadline=System.currentTimeMillis()+60000;timeline.clear();reports.clear();rates.clear();muted.clear();current=new JsonObject();health=dev.abros.rivet.core.Messages.text("rivet.core.still_loading_b735bb24");profiler="unknown";notice="";owner=null;ownerName="";lease=new Object();lifecycle=new Object();busy=false;nextSample=until=probeAt=operationAt=probeSequence=0;historyFile=null;}
    public void start(MinecraftServer server){
        timeline=new SparkTimeline(ServerFeatures.sparkAlerts());
        historyFile=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("rivet/spark-reports.json");
        var file=historyFile;Object generation=lifecycle;
        files.execute(()->{try{if(Files.isRegularFile(file)&&!Files.isSymbolicLink(file)&&Files.size(file)<=262144){var array=JsonParser.parseString(Files.readString(file)).getAsJsonArray();server.execute(dev.abros.rivet.core.Messages.capture(()->{if(lifecycle!=generation)return;for(var e:array){if(reports.size()>=30)break;if(e.isJsonObject()){var row=e.getAsJsonObject();String url=Json.opt(row,"url","");if(url.length()<=512&&SparkTimeline.reportUrl(url).filter(url::equals).isPresent()&&row.has("at")){try{long at=row.get("at").getAsLong();if(at<0||at>System.currentTimeMillis()+60000)continue;var clean=new JsonObject();clean.addProperty("at",at);clean.addProperty("url",url);clean.addProperty("actor",shortText(Json.opt(row,"actor","")));clean.addProperty("label",shortText(Json.opt(row,"label",dev.abros.rivet.core.Messages.text("rivet.ui.report_d7748bfa"))));reports.addLast(clean);}catch(RuntimeException invalid){/* Ignore malformed history entries. */}}}}}));}}catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Rivet: cannot load spark report history",error);}});
    }
    public boolean sampleDue(){return installed()&&integration.available()&&System.currentTimeMillis()>=nextSample;}
    public void tick(MinecraftServer server)throws Exception{
        long now=System.currentTimeMillis();
        if(busy&&now-operationAt>20000){busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.spark_did_not_confirm_the_action_f4b3448b");profiler="unknown";}
        if(until>0&&now>until+30000){owner=null;ownerName="";until=0;profiler="unknown";}
        if(now<nextSample||!installed())return;nextSample=now+2000;
        try{current=SparkMetrics.read(now);}catch(OptionalIntegration.NotReadyException pending){if(now<startupDeadline)throw pending;throw new IllegalStateException(dev.abros.rivet.core.Messages.text("rivet.core.spark_api_did_not_load_within_c539eb17"));}health=dev.abros.rivet.core.Messages.text("rivet.core.running_e6156da3");
        if(timeline.add(current,now))for(var p:server.getPlayerList().getPlayers())if(!muted.contains(p.getUUID())&&ServerFeatures.mayDiagnose(p))ServerFeatures.integrationNotice(p,dev.abros.rivet.core.Messages.text("rivet.core.server_is_under_load_07e1e35e"),dev.abros.rivet.core.Messages.text("rivet.core.tps_below_9c94822a")+timeline.alerts().minimumTps()+dev.abros.rivet.core.Messages.text("rivet.core.or_mspt_above_fc1d99aa")+timeline.alerts().maximumMspt()+dev.abros.rivet.core.Messages.text("rivet.core.ms_for_at_least_25e6717f")+timeline.alerts().sustainedSeconds()+dev.abros.rivet.core.Messages.text("rivet.core.seconds_open_spark_diagnostics_018b22ae"),"spark","spark");
    }
    /** Any profiler mutation outside this adapter invalidates our ownership immediately. */
    public void command(net.neoforged.neoforge.event.CommandEvent event){
        if(dispatching)return;
        String input=event.getParseResults().getReader().getString().replaceFirst("^/","");
        if(input.matches("(?i)(?:spark|spark:spark) (?:profiler|sampler) (?:start|stop|cancel)(?: .*|$)")){lease=new Object();owner=null;ownerName="";until=0;profiler="unknown";busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.recording_is_managed_outside_rivet_check_c0f4c8e0");}
    }
    @Override public JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception{
        if(!ServerFeatures.mayDiagnose(actor))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.missing_rivet_diagnostics_permission_6fb4f97b"));
        String op=Json.opt(request,"op","snapshot");long now=System.currentTimeMillis();
        if(op.equals("alerts")){if(request.has("enabled")&&request.get("enabled").getAsBoolean())muted.remove(actor.getUUID());else muted.add(actor.getUUID());return snapshot(actor);}
        if(!installed()||!integration.available())return snapshot(actor);
        if(op.equals("snapshot")){if(!current.entrySet().isEmpty()&&commandsSupported()&&permission(actor,"spark.profiler")&&!busy&&now-probeAt>5000){probeAt=now;probe(actor,null);}return snapshot(actor);}
        if(!Set.of("health","start","slow","open","stop","cancel").contains(op))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_spark_action_4f87897d"));
        if(!permission(actor,op.equals("health")?"spark.healthreport":"spark.profiler"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.missing_spark_permission_98dad873")+(op.equals("health")?"healthreport":"profiler"));
        if(!commandsSupported())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.management_has_not_been_verified_for_04c87c91")+version());
        if(current.entrySet().isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.spark_is_not_ready_yet_a5e313e8")+status());
        if(busy)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_for_spark_to_respond_69bc5ee6"));
        if(now-rates.getOrDefault(actor.getUUID(),0L)<2000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_two_seconds_between_actions_c384dcee"));
        if(rates.size()>512)rates.entrySet().removeIf(e->now-e.getValue()>60000);rates.put(actor.getUUID(),now);busy=true;operationAt=now;notice=dev.abros.rivet.core.Messages.text("rivet.core.checking_spark_status_d4e28d6c");
        int duration=request.has("seconds")?request.get("seconds").getAsInt():60;
        if((op.equals("start")||op.equals("slow"))&&duration!=60&&duration!=120){busy=false;throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.select_60_or_120_seconds_f35eb4bf"));}
        int threshold=request.has("threshold")?request.get("threshold").getAsInt():50;
        if(op.equals("slow")&&!Set.of(50,100,200).contains(threshold)){busy=false;throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.select_a_threshold_of_50_100_062a0a7b"));}
        if(op.equals("health")){dispatch(actor,"spark health --upload",dev.abros.rivet.core.Messages.text("rivet.ui.server_status_6081a1b2"),null);return snapshot(actor);}
        Object generation=lease;probe(actor,state->{
            if(generation!=lease){busy=false;return;}
            if(!actor.connection.getConnection().isConnected()||!ServerFeatures.mayDiagnose(actor)){busy=false;return;}
            if(op.equals("start")||op.equals("slow")){
                if(!state.equals("idle")){busy=false;notice=state.equals("running")?dev.abros.rivet.core.Messages.text("rivet.core.spark_is_already_recording_a_profile_550336e5"):dev.abros.rivet.core.Messages.text("rivet.core.could_not_confirm_that_no_recording_2c6d0374");return;}
                owner=actor.getUUID();ownerName=actor.getGameProfile().getName();until=now+duration*1000L;
                dispatch(actor,"spark profiler start --force-java-sampler --timeout "+duration+(op.equals("slow")?" --only-ticks-over "+threshold:""),op.equals("slow")?dev.abros.rivet.core.Messages.text("rivet.core.slow_ticks_f4e9606f")+threshold+dev.abros.rivet.core.Messages.text("rivet.ui.ms_5160aea6"):dev.abros.rivet.core.Messages.text("rivet.core.profile_3d564d9d")+duration+dev.abros.rivet.core.Messages.text("rivet.ui.s_488d98ca"),generation);
            }else if(op.equals("open")){
                if(!state.equals("running")){busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.active_recording_not_confirmed_9ac7cfdb");return;}
                dispatch(actor,"spark profiler open",dev.abros.rivet.core.Messages.text("rivet.core.current_profile_85a97c48"),null);
            }else{
                if(!state.equals("running")||!actor.getUUID().equals(owner)){busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.you_can_only_stop_or_cancel_5a097334");return;}
                dispatch(actor,"spark profiler "+op,op.equals("stop")?dev.abros.rivet.core.Messages.text("rivet.core.completed_profile_589f8153"):dev.abros.rivet.core.Messages.text("rivet.core.cancelled_profile_c49e809f"),generation);
            }
        });return snapshot(actor);
    }
    private void probe(ServerPlayer actor,Consumer<String> after){
        Object generation=lease;long sequence=++probeSequence;
        run(actor,"spark profiler info",text->{String state=SparkTimeline.profilerState(text);if(generation!=lease||sequence!=probeSequence)return;if(state.equals("unknown")){if(text.toLowerCase(Locale.ROOT).contains("permission")){busy=false;notice=shortText(text);}return;}profiler=state;if(state.equals("idle")){owner=null;ownerName="";until=0;}if(after!=null)after.accept(state);});
    }
    private void dispatch(ServerPlayer actor,String command,String label,Object generation){
        notice=dev.abros.rivet.core.Messages.text("rivet.core.command_sent_to_spark_dd706245");
        run(actor,command,text->{
            if(generation!=null&&generation!=lease)return;
            String state=SparkTimeline.profilerState(text);if(!state.equals("unknown")){profiler=state;busy=false;}
            if(text.contains("is now running!"))notice=dev.abros.rivet.core.Messages.text("rivet.core.recording_started_51200552");
            if(text.contains("cancelled")||text.contains("canceled")||text.contains("Profiler stopped")){owner=null;ownerName="";until=0;profiler="idle";busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.registration_cancelled_3c511987");}
            if(text.contains("You do not have")||text.toLowerCase(Locale.ROOT).contains("permission")||text.toLowerCase(Locale.ROOT).contains("error")||text.toLowerCase(Locale.ROOT).contains("failed")){busy=false;notice=shortText(text);if(command.contains(" start ")){owner=null;ownerName="";until=0;}}
            SparkTimeline.reportUrl(text).ifPresent(url->{remember(actor,label,url);notice=dev.abros.rivet.core.Messages.text("rivet.core.report_ready_open_it_in_the_9938ac7c");busy=false;if(command.contains(" start ")||command.endsWith(" stop")){owner=null;ownerName="";until=0;profiler="idle";}});
        });
    }
    private static boolean permission(ServerPlayer actor,String requested){
        for(var node:net.neoforged.neoforge.server.permission.PermissionAPI.getRegisteredNodes())if(node.getType()==net.neoforged.neoforge.server.permission.nodes.PermissionTypes.BOOLEAN&&(node.getNodeName().equals("spark.all")||node.getNodeName().equals(requested))){
            @SuppressWarnings("unchecked") var booleanNode=(net.neoforged.neoforge.server.permission.nodes.PermissionNode<Boolean>)node;
            if(net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(actor,booleanNode))return true;
        }return false;
    }
    private void run(ServerPlayer actor,String command,Consumer<String> response){
        if(!ServerFeatures.mayDiagnose(actor)||!permission(actor,command.startsWith("spark health")?"spark.healthreport":"spark.profiler")){busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.insufficient_spark_permissions_2a4fc219");return;}
        Object active=lifecycle;var output=new Output(actor.server,text->{if(lifecycle==active)response.accept(text);});var source=new CommandSourceStack(output,actor.position(),actor.getRotationVector(),actor.serverLevel(),0,actor.getGameProfile().getName(),actor.getDisplayName(),actor.server,null);
        try{dispatching=true;actor.server.getCommands().performPrefixedCommand(source,command);}catch(RuntimeException failure){busy=false;notice=dev.abros.rivet.core.Messages.text("rivet.core.spark_command_unavailable_check_permissions_and_d99af440");}finally{dispatching=false;}
    }
    private static String shortText(String text){return text.length()>300?text.substring(0,300):text;}
    private static final class Output implements CommandSource {
        private final MinecraftServer server;private final Consumer<String> response;
        Output(MinecraftServer server,Consumer<String> response){this.server=server;this.response=response;}
        @Override public void sendSystemMessage(Component message){String text=message.getString();var links=new StringBuilder(text);message.visit((style,string)->{var click=style.getClickEvent();if(click!=null&&click.getAction()==net.minecraft.network.chat.ClickEvent.Action.OPEN_URL)links.append(" ").append(click.getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);server.execute(dev.abros.rivet.core.Messages.capture(()->response.accept(links.toString())));}
        @Override public boolean acceptsSuccess(){return true;}
        @Override public boolean acceptsFailure(){return true;}
        @Override public boolean shouldInformAdmins(){return false;}
    }
    private void remember(ServerPlayer actor,String label,String url){
        if(url.length()>512)return;
        if(reports.stream().anyMatch(r->Json.opt(r,"url","").equals(url)))return;
        var row=new JsonObject();row.addProperty("at",System.currentTimeMillis());row.addProperty("actor",actor.getGameProfile().getName());row.addProperty("label",label);row.addProperty("url",url);reports.addFirst(row);while(reports.size()>30)reports.removeLast();
        if(ServerFeatures.mayDiagnose(actor))ServerFeatures.integrationNotice(actor,dev.abros.rivet.core.Messages.text("rivet.core.spark_report_ready_0bb138f2"),label+dev.abros.rivet.core.Messages.text("rivet.core.open_report_history_9349a2f4"),"spark","spark");
        if(historyFile==null)return;var file=historyFile;var data=history();files.execute(()->{try{Files.createDirectories(file.getParent());if(Files.isSymbolicLink(file)||Files.isSymbolicLink(file.getParent()))throw new java.io.IOException("Symlink history");var tmp=file.resolveSibling("spark-reports.json.tmp");Files.writeString(tmp,Json.GSON.toJson(data));Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Rivet: cannot save spark reports",failure);}});
    }
    private JsonArray history(){var array=new JsonArray();reports.forEach(r->array.add(r.deepCopy()));return array;}
    private JsonObject snapshot(ServerPlayer actor){
        var reply=new JsonObject();reply.addProperty("installed",installed());reply.addProperty("version",version());reply.addProperty("status",status());reply.add("adapter",diagnostics());reply.addProperty("commands",integration.available()&&!current.entrySet().isEmpty()&&commandsSupported());reply.addProperty("canProfile",integration.available()&&!current.entrySet().isEmpty()&&permission(actor,"spark.profiler"));reply.addProperty("canHealth",integration.available()&&!current.entrySet().isEmpty()&&permission(actor,"spark.healthreport"));reply.addProperty("profiler",profiler);reply.addProperty("busy",busy);reply.addProperty("notice",notice);reply.addProperty("owner",ownerName);reply.addProperty("until",until);reply.addProperty("owned",actor.getUUID().equals(owner));reply.addProperty("alerts",!muted.contains(actor.getUUID()));reply.add("rivet",ServerFeatures.sparkRuntime());reply.add("current",current.deepCopy());reply.add("samples",timeline.snapshot());var wireReports=history();while(wireReports.toString().length()>10000&&!wireReports.isEmpty())wireReports.remove(wireReports.size()-1);reply.add("reports",wireReports);return reply;
    }
}
