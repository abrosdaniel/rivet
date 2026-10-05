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
    private String health="Ещё загружается", profiler="unknown", notice="", ownerName="";
    private UUID owner;
    private long nextSample, until, probeAt, operationAt, probeSequence;
    private Object lifecycle=new Object();
    private Object lease=new Object();
    private boolean busy, dispatching;
    private Path historyFile;
    private final ExecutorService files=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"Rivet spark history");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    public JsonObject diagnostics(){var row=CompatibilityAdapter.super.diagnostics();row.addProperty("ready",!current.entrySet().isEmpty());if(row.get("reason").getAsString().isEmpty()&&installed()&&!commandsSupported())row.addProperty("reason","Профилирование не проверено для этой версии");var caps=new JsonArray();caps.add("metrics");if(commandsSupported()){caps.add("profiling");caps.add("reports");}row.add("capabilities",caps);return row;}
    public String id(){return "spark";}
    public String status(){return !integration.available()||!integration.diagnostics().get("reason").getAsString().isEmpty()?integration.status():health;}
    private boolean installed(){return ModList.get().isLoaded("spark");}
    private String version(){return ModList.get().getModContainerById("spark").map(c->c.getModInfo().getVersion().toString()).orElse("");}
    private boolean commandsSupported(){return version().startsWith("1.10.124");}
    @Override public void clear(){integration.reset();startupDeadline=System.currentTimeMillis()+60000;timeline.clear();reports.clear();rates.clear();muted.clear();current=new JsonObject();health="Ещё загружается";profiler="unknown";notice="";owner=null;ownerName="";lease=new Object();lifecycle=new Object();busy=false;nextSample=until=probeAt=operationAt=probeSequence=0;historyFile=null;}
    public void start(MinecraftServer server){
        timeline=new SparkTimeline(ServerFeatures.sparkAlerts());
        historyFile=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("rivet/spark-reports.json");
        var file=historyFile;Object generation=lifecycle;
        files.execute(()->{try{if(Files.isRegularFile(file)&&!Files.isSymbolicLink(file)&&Files.size(file)<=262144){var array=JsonParser.parseString(Files.readString(file)).getAsJsonArray();server.execute(()->{if(lifecycle!=generation)return;for(var e:array){if(reports.size()>=30)break;if(e.isJsonObject()){var row=e.getAsJsonObject();String url=Json.opt(row,"url","");if(url.length()<=512&&SparkTimeline.reportUrl(url).filter(url::equals).isPresent()&&row.has("at")){try{long at=row.get("at").getAsLong();if(at<0||at>System.currentTimeMillis()+60000)continue;var clean=new JsonObject();clean.addProperty("at",at);clean.addProperty("url",url);clean.addProperty("actor",shortText(Json.opt(row,"actor","")));clean.addProperty("label",shortText(Json.opt(row,"label","Отчёт")));reports.addLast(clean);}catch(RuntimeException invalid){/* Ignore malformed history entries. */}}}}});}}catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Rivet: cannot load spark report history",error);}});
    }
    public boolean sampleDue(){return installed()&&integration.available()&&System.currentTimeMillis()>=nextSample;}
    public void tick(MinecraftServer server)throws Exception{
        long now=System.currentTimeMillis();
        if(busy&&now-operationAt>20000){busy=false;notice="spark не подтвердил действие. Проверьте журнал сервера.";profiler="unknown";}
        if(until>0&&now>until+30000){owner=null;ownerName="";until=0;profiler="unknown";}
        if(now<nextSample||!installed())return;nextSample=now+2000;
        try{current=SparkMetrics.read(now);}catch(OptionalIntegration.NotReadyException pending){if(now<startupDeadline)throw pending;throw new IllegalStateException("spark API не загрузился за 60 секунд");}health="Работает";
        if(timeline.add(current,now))for(var p:server.getPlayerList().getPlayers())if(!muted.contains(p.getUUID())&&ServerFeatures.mayDiagnose(p))ServerFeatures.integrationNotice(p,"Сервер испытывает нагрузку","TPS ниже "+timeline.alerts().minimumTps()+" или MSPT выше "+timeline.alerts().maximumMspt()+" мс не менее "+timeline.alerts().sustainedSeconds()+" секунд. Откройте диагностику spark.","spark","spark");
    }
    /** Any profiler mutation outside this adapter invalidates our ownership immediately. */
    public void command(net.neoforged.neoforge.event.CommandEvent event){
        if(dispatching)return;
        String input=event.getParseResults().getReader().getString().replaceFirst("^/","");
        if(input.matches("(?i)(?:spark|spark:spark) (?:profiler|sampler) (?:start|stop|cancel)(?: .*|$)")){lease=new Object();owner=null;ownerName="";until=0;profiler="unknown";busy=false;notice="Запись управляется вне Rivet. Проверьте её состояние.";}
    }
    @Override public JsonObject execute(ServerPlayer actor,JsonObject request,ServerIdentityDirectory identities)throws Exception{
        if(!ServerFeatures.mayDiagnose(actor))throw new IllegalArgumentException("Нет права rivet.diagnostics");
        String op=Json.opt(request,"op","snapshot");long now=System.currentTimeMillis();
        if(op.equals("alerts")){if(request.has("enabled")&&request.get("enabled").getAsBoolean())muted.remove(actor.getUUID());else muted.add(actor.getUUID());return snapshot(actor);}
        if(!installed()||!integration.available())return snapshot(actor);
        if(op.equals("snapshot")){if(!current.entrySet().isEmpty()&&commandsSupported()&&permission(actor,"spark.profiler")&&!busy&&now-probeAt>5000){probeAt=now;probe(actor,null);}return snapshot(actor);}
        if(!Set.of("health","start","slow","open","stop","cancel").contains(op))throw new IllegalArgumentException("Неизвестное действие spark");
        if(!permission(actor,op.equals("health")?"spark.healthreport":"spark.profiler"))throw new IllegalArgumentException("Нет права spark."+(op.equals("health")?"healthreport":"profiler"));
        if(!commandsSupported())throw new IllegalArgumentException("Управление не проверено для spark "+version());
        if(current.entrySet().isEmpty())throw new IllegalArgumentException("spark ещё не готов: "+status());
        if(busy)throw new IllegalArgumentException("Дождитесь ответа spark");
        if(now-rates.getOrDefault(actor.getUUID(),0L)<2000)throw new IllegalArgumentException("Подождите две секунды между действиями");
        if(rates.size()>512)rates.entrySet().removeIf(e->now-e.getValue()>60000);rates.put(actor.getUUID(),now);busy=true;operationAt=now;notice="Проверяем состояние spark…";
        int duration=request.has("seconds")?request.get("seconds").getAsInt():60;
        if((op.equals("start")||op.equals("slow"))&&duration!=60&&duration!=120){busy=false;throw new IllegalArgumentException("Выберите 60 или 120 секунд");}
        int threshold=request.has("threshold")?request.get("threshold").getAsInt():50;
        if(op.equals("slow")&&!Set.of(50,100,200).contains(threshold)){busy=false;throw new IllegalArgumentException("Выберите порог 50, 100 или 200 мс");}
        if(op.equals("health")){dispatch(actor,"spark health --upload","Состояние сервера",null);return snapshot(actor);}
        Object generation=lease;probe(actor,state->{
            if(generation!=lease){busy=false;return;}
            if(!actor.connection.getConnection().isConnected()||!ServerFeatures.mayDiagnose(actor)){busy=false;return;}
            if(op.equals("start")||op.equals("slow")){
                if(!state.equals("idle")){busy=false;notice=state.equals("running")?"spark уже записывает профиль. Откройте текущий отчёт; чужую запись Rivet не прерывает.":"Не удалось подтвердить отсутствие записи. Новая запись не запущена.";return;}
                owner=actor.getUUID();ownerName=actor.getGameProfile().getName();until=now+duration*1000L;
                dispatch(actor,"spark profiler start --force-java-sampler --timeout "+duration+(op.equals("slow")?" --only-ticks-over "+threshold:""),op.equals("slow")?"Медленные тики · "+threshold+" мс":"Профиль · "+duration+" с",generation);
            }else if(op.equals("open")){
                if(!state.equals("running")){busy=false;notice="Активная запись не подтверждена";return;}
                dispatch(actor,"spark profiler open","Текущий профиль",null);
            }else{
                if(!state.equals("running")||!actor.getUUID().equals(owner)){busy=false;notice="Остановить или отменить можно только свою активную запись, запущенную из Rivet.";return;}
                dispatch(actor,"spark profiler "+op,op.equals("stop")?"Завершённый профиль":"Отменённый профиль",generation);
            }
        });return snapshot(actor);
    }
    private void probe(ServerPlayer actor,Consumer<String> after){
        Object generation=lease;long sequence=++probeSequence;
        run(actor,"spark profiler info",text->{String state=SparkTimeline.profilerState(text);if(generation!=lease||sequence!=probeSequence)return;if(state.equals("unknown")){if(text.toLowerCase(Locale.ROOT).contains("permission")){busy=false;notice=shortText(text);}return;}profiler=state;if(state.equals("idle")){owner=null;ownerName="";until=0;}if(after!=null)after.accept(state);});
    }
    private void dispatch(ServerPlayer actor,String command,String label,Object generation){
        notice="Команда передана spark";
        run(actor,command,text->{
            if(generation!=null&&generation!=lease)return;
            String state=SparkTimeline.profilerState(text);if(!state.equals("unknown")){profiler=state;busy=false;}
            if(text.contains("is now running!"))notice="Запись запущена";
            if(text.contains("cancelled")||text.contains("canceled")||text.contains("Profiler stopped")){owner=null;ownerName="";until=0;profiler="idle";busy=false;notice="Запись отменена";}
            if(text.contains("You do not have")||text.toLowerCase(Locale.ROOT).contains("permission")||text.toLowerCase(Locale.ROOT).contains("error")||text.toLowerCase(Locale.ROOT).contains("failed")){busy=false;notice=shortText(text);if(command.contains(" start ")){owner=null;ownerName="";until=0;}}
            SparkTimeline.reportUrl(text).ifPresent(url->{remember(actor,label,url);notice="Отчёт готов. Откройте его в истории.";busy=false;if(command.contains(" start ")||command.endsWith(" stop")){owner=null;ownerName="";until=0;profiler="idle";}});
        });
    }
    private static boolean permission(ServerPlayer actor,String requested){
        for(var node:net.neoforged.neoforge.server.permission.PermissionAPI.getRegisteredNodes())if(node.getType()==net.neoforged.neoforge.server.permission.nodes.PermissionTypes.BOOLEAN&&(node.getNodeName().equals("spark.all")||node.getNodeName().equals(requested))){
            @SuppressWarnings("unchecked") var booleanNode=(net.neoforged.neoforge.server.permission.nodes.PermissionNode<Boolean>)node;
            if(net.neoforged.neoforge.server.permission.PermissionAPI.getPermission(actor,booleanNode))return true;
        }return false;
    }
    private void run(ServerPlayer actor,String command,Consumer<String> response){
        if(!ServerFeatures.mayDiagnose(actor)||!permission(actor,command.startsWith("spark health")?"spark.healthreport":"spark.profiler")){busy=false;notice="Недостаточно прав spark";return;}
        Object active=lifecycle;var output=new Output(actor.server,text->{if(lifecycle==active)response.accept(text);});var source=new CommandSourceStack(output,actor.position(),actor.getRotationVector(),actor.serverLevel(),0,actor.getGameProfile().getName(),actor.getDisplayName(),actor.server,null);
        try{dispatching=true;actor.server.getCommands().performPrefixedCommand(source,command);}catch(RuntimeException failure){busy=false;notice="Команда spark недоступна. Проверьте права и журнал.";}finally{dispatching=false;}
    }
    private static String shortText(String text){return text.length()>300?text.substring(0,300):text;}
    private static final class Output implements CommandSource {
        private final MinecraftServer server;private final Consumer<String> response;
        Output(MinecraftServer server,Consumer<String> response){this.server=server;this.response=response;}
        @Override public void sendSystemMessage(Component message){String text=message.getString();var links=new StringBuilder(text);message.visit((style,string)->{var click=style.getClickEvent();if(click!=null&&click.getAction()==net.minecraft.network.chat.ClickEvent.Action.OPEN_URL)links.append(" ").append(click.getValue());return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);server.execute(()->response.accept(links.toString()));}
        @Override public boolean acceptsSuccess(){return true;}
        @Override public boolean acceptsFailure(){return true;}
        @Override public boolean shouldInformAdmins(){return false;}
    }
    private void remember(ServerPlayer actor,String label,String url){
        if(url.length()>512)return;
        if(reports.stream().anyMatch(r->Json.opt(r,"url","").equals(url)))return;
        var row=new JsonObject();row.addProperty("at",System.currentTimeMillis());row.addProperty("actor",actor.getGameProfile().getName());row.addProperty("label",label);row.addProperty("url",url);reports.addFirst(row);while(reports.size()>30)reports.removeLast();
        if(ServerFeatures.mayDiagnose(actor))ServerFeatures.integrationNotice(actor,"Отчёт spark готов",label+" · Откройте историю отчётов.","spark","spark");
        if(historyFile==null)return;var file=historyFile;var data=history();files.execute(()->{try{Files.createDirectories(file.getParent());if(Files.isSymbolicLink(file)||Files.isSymbolicLink(file.getParent()))throw new java.io.IOException("Symlink history");var tmp=file.resolveSibling("spark-reports.json.tmp");Files.writeString(tmp,Json.GSON.toJson(data));Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Rivet: cannot save spark reports",failure);}});
    }
    private JsonArray history(){var array=new JsonArray();reports.forEach(r->array.add(r.deepCopy()));return array;}
    private JsonObject snapshot(ServerPlayer actor){
        var reply=new JsonObject();reply.addProperty("installed",installed());reply.addProperty("version",version());reply.addProperty("status",status());reply.add("adapter",diagnostics());reply.addProperty("commands",integration.available()&&!current.entrySet().isEmpty()&&commandsSupported());reply.addProperty("canProfile",integration.available()&&!current.entrySet().isEmpty()&&permission(actor,"spark.profiler"));reply.addProperty("canHealth",integration.available()&&!current.entrySet().isEmpty()&&permission(actor,"spark.healthreport"));reply.addProperty("profiler",profiler);reply.addProperty("busy",busy);reply.addProperty("notice",notice);reply.addProperty("owner",ownerName);reply.addProperty("until",until);reply.addProperty("owned",actor.getUUID().equals(owner));reply.addProperty("alerts",!muted.contains(actor.getUUID()));reply.add("rivet",ServerFeatures.sparkRuntime());reply.add("current",current.deepCopy());reply.add("samples",timeline.snapshot());var wireReports=history();while(wireReports.toString().length()>10000&&!wireReports.isEmpty())wireReports.remove(wireReports.size()-1);reply.add("reports",wireReports);return reply;
    }
}
