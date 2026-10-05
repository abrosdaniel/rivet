package dev.abros.rivet.core;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.util.*;
import java.util.function.Function;

/** Immutable, server-only startup snapshot. Never send or log this configuration. */
public final class ServerSettings {
    private final Map<String,Object> values;
    private ServerSettings(Map<String,Object> values){this.values=Map.copyOf(values);validate();}
    @Override public String toString(){return "Rivet server settings (credentials hidden)";}
    public static String template() throws IOException {
        try(var in=ServerSettings.class.getResourceAsStream("/rivet-server.toml")){
            if(in==null)throw new IOException("Missing Rivet configuration template");
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    public static ServerSettings load(Path root)throws IOException {
        Path file=root.resolve("config/rivet-server.toml");
        if(Files.isSymbolicLink(file))throw invalid("файл не должен быть символической ссылкой");
        if(!Files.exists(file)){
            Files.createDirectories(file.getParent());
            try{Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
            catch(UnsupportedOperationException unsupported){Files.createFile(file);}
            Files.writeString(file,template());
        }
        if(Files.size(file)>65536)throw invalid("размер файла превышает 64 КиБ");
        return parse(Files.readString(file));
    }
    public static ServerSettings parse(String text){
        Map<String,Object> values,defaults;
        try{values=flatten(new TomlParser().parse(text));defaults=flatten(new TomlParser().parse(template()));}
        catch(Exception failure){throw invalid("ошибка TOML: проверьте кавычки, типы и повторяющиеся параметры (значения скрыты)");}
        // Obsolete punishment defaults are ignored, so existing production configs still load.
        values.remove("chat.allowItems");values.remove("integrations.luckperms");values.remove("display.chatMode");values.remove("moderationVotes.actions.banMinutes");values.remove("moderationVotes.actions.muteMinutes");
        for(var e:defaults.entrySet())if(!values.containsKey(e.getKey())&&List.of("statistics.","skins.","moderationVotes.","community.","menu.","updates.","retention.","chat.","display.","tasks.","spark.").stream().anyMatch(e.getKey()::startsWith))values.put(e.getKey(),e.getValue());
        var missing=new TreeSet<>(defaults.keySet());missing.removeAll(values.keySet());
        if(!missing.isEmpty())throw invalid("отсутствуют обязательные параметры: "+String.join(", ",missing)+"; сверяйтесь с SERVER_GUIDE.md");
        var unknown=new TreeSet<>(values.keySet());unknown.removeAll(defaults.keySet());
        if(!unknown.isEmpty()){String keys=unknown.stream().limit(10).map(key->key.length()<=80&&key.matches("[A-Za-z][A-Za-z0-9_.-]*")?key:"<некорректное имя>").collect(java.util.stream.Collectors.joining(", "));throw invalid("неизвестные параметры: "+keys+"; проверьте имя параметра и заголовок [раздела]");}
        for(var e:defaults.entrySet()){
            Object v=values.get(e.getKey()),d=e.getValue();
            if(d instanceof String&&!(v instanceof String)||d instanceof Boolean&&!(v instanceof Boolean)||d instanceof Number&&!(v instanceof Integer||v instanceof Long)||d instanceof List&&!(v instanceof List))throw invalid("неверный тип поля "+e.getKey());
        }
        return new ServerSettings(values);
    }
    private static Map<String,Object> flatten(UnmodifiableConfig config){var out=new HashMap<String,Object>();flatten(config,"",out);return out;}
    private static void flatten(UnmodifiableConfig c,String prefix,Map<String,Object> out){for(var e:c.entrySet()){String key=prefix+e.getKey();Object v=e.getValue();if(v instanceof UnmodifiableConfig child)flatten(child,key+".",out);else out.put(key,v);}}
    public JsonObject preview(ServerSettings next){var result=new JsonObject();var changes=new JsonArray();for(String key:new TreeSet<>(values.keySet()))if(!Objects.equals(values.get(key),next.values.get(key))){var entry=new JsonObject();entry.addProperty("key",key);boolean live=key.startsWith("menu.")||key.startsWith("community.");entry.addProperty("live",live);if(live){entry.add("before",key.equals("menu.links")?menu().get("links"):Json.GSON.toJsonTree(values.get(key)));entry.add("after",key.equals("menu.links")?next.menu().get("links"):Json.GSON.toJsonTree(next.values.get(key)));}changes.add(entry);}result.add("changes",changes);return result;}
    public ServerSettings liveFrom(ServerSettings next){var merged=new HashMap<>(values);next.values.forEach((key,value)->{if(key.startsWith("menu.")||key.startsWith("community."))merged.put(key,value);});return new ServerSettings(merged);}
    public String text(String key){return (String)values.get(key);}
    public boolean flag(String key){return (Boolean)values.get(key);}
    public int number(String key){return Math.toIntExact(((Number)values.get(key)).longValue());}
    private void range(String key,int min,int max){long n=((Number)values.get(key)).longValue();if(n<min||n>max)throw invalid(key+": допустимо "+min+"–"+max);}
    private void validate(){
        range("retention.reportDays",1,365);range("retention.trashDays",1,365);range("retention.auditEntries",100,100000);skins();range("connection.handshakeTimeoutSeconds",3,60);range("auth.minimumPasswordLength",6,128);
        range("tasks.maxPerOwner",0,Integer.MAX_VALUE);range("tasks.maxSubtasks",0,Integer.MAX_VALUE);range("tasks.maxComments",0,Integer.MAX_VALUE);
        range("spark.minimumTps",1,20);range("spark.maximumMspt",1,1000);range("spark.sustainedSeconds",1,3600);range("spark.cooldownSeconds",1,86400);
        range("database.port",1,65535);range("database.poolSize",2,32);
        if(!Set.of("false","base","hybrid").contains(text("auth.mode")))throw invalid("auth.mode: ожидается строка false, base или hybrid");
        if(text("menu.helpText").length()>2000)throw invalid("menu.helpText: максимум 2000 символов");
        String env=text("database.passwordEnvironment");if(!env.isEmpty()&&!env.matches("[A-Za-z_][A-Za-z0-9_]*"))throw invalid("database.passwordEnvironment: неверное имя переменной");
        for(String key:List.of("display.tabMode"))if(!Set.of("auto","rivet","compatible").contains(text(key)))throw invalid(key+": auto, rivet или compatible");
        for(String key:List.of("chat.localName","chat.globalName"))if(text(key).codePointCount(0,text(key).length())>40||text(key).codePoints().anyMatch(Character::isISOControl))throw invalid(key+": до 40 символов, без переводов строк");
        if(number("chat.localRadius")<1||number("chat.localRadius")>1000)throw invalid("chat.localRadius: 1–1000");
        try{votes();community();menu();new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),"validation",text("database.sslMode"),text("database.sslRootCert"),number("database.poolSize"));}
        catch(Exception failure){throw invalid("проверьте диапазоны moderationVotes, списки community, ссылки menu и параметры database; значения скрыты");}
    }
    public DatabaseSettings database(){return database(System::getenv);}
    DatabaseSettings database(Function<String,String> environment){
        String env=text("database.passwordEnvironment"),password=env.isEmpty()?text("database.password"):environment.apply(env);
        if(password==null||password.isBlank())throw invalid("заполните database.password или заданную переменную database.passwordEnvironment");
        return new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),password,text("database.sslMode"),text("database.sslRootCert"),number("database.poolSize"));
    }
    public dev.abros.rivet.core.skins.SkinSettings skins(){return new dev.abros.rivet.core.skins.SkinSettings(flag("skins.enabled"),number("skins.maxFileSizeMiB"),number("skins.maxSkinsPerPlayer"),text("skins.mojangFallback"));}
    public TaskLimits taskLimits(){return new TaskLimits(number("tasks.maxPerOwner"),number("tasks.maxSubtasks"),number("tasks.maxComments"));}
    public SparkTimeline.AlertSettings sparkAlerts(){return new SparkTimeline.AlertSettings(number("spark.minimumTps"),number("spark.maximumMspt"),number("spark.sustainedSeconds"),number("spark.cooldownSeconds"));}
    public PlayerStatistics.Settings statistics(){return new PlayerStatistics.Settings(flag("statistics.firstJoin"),flag("statistics.lastActivity"),flag("statistics.totalPlayTime"),flag("statistics.currentSession"),flag("statistics.deaths"));}
    public ModerationVotes.Settings votes(){return new ModerationVotes.Settings(flag("moderationVotes.enabled"),number("moderationVotes.minimumPlayers"),number("moderationVotes.durationSeconds"),number("moderationVotes.minimumPlayMinutes"),number("moderationVotes.initiatorCooldownMinutes"),number("moderationVotes.targetCooldownMinutes"),flag("moderationVotes.actions.kick"),flag("moderationVotes.actions.ban"),flag("moderationVotes.actions.mute"));}
    public JsonObject community(){var j=new JsonObject();j.addProperty("groupsTitle",text("community.groupsTitle"));j.addProperty("maxMemberships",number("community.maxMemberships"));for(String key:List.of("categories","groupTypes","sections")){var a=new JsonArray();for(Object v:(List<?>)values.get("community."+key)){if(!(v instanceof String s))throw invalid("community."+key+": ожидаются строки");a.add(s);}j.add(key,a);}return CommunityStore.validateConfig(j);}
    public JsonObject menu(){var j=new JsonObject();var a=new JsonArray();for(Object v:(List<?>)values.get("menu.links")){if(!(v instanceof UnmodifiableConfig c))throw invalid("menu.links: ожидаются name и url");var link=new JsonObject();for(var e:c.entrySet()){if(!(e.getValue() instanceof String s))throw invalid("menu.links: ожидаются строки");link.addProperty(e.getKey(),s);}a.add(link);}j.add("links",a);return ServerMenuData.validate(j);}
    private static IllegalArgumentException invalid(String detail){return new IllegalArgumentException("Rivet: config/rivet-server.toml — "+detail+". Запуск остановлен.");}
}
