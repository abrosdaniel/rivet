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
    public static synchronized ServerSettings load(Path root)throws IOException {
        Path file=root.resolve("config/rivet-server.toml");
        if(Files.isSymbolicLink(file))throw invalid("файл не должен быть символической ссылкой");
        if(!Files.exists(file)){
            Files.createDirectories(file.getParent());
            try{Files.createFile(file,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
            catch(UnsupportedOperationException unsupported){Files.createFile(file);}
            Files.writeString(file,template());
        }
        if(Files.size(file)>65536)throw invalid("размер файла превышает 64 КиБ");
        String original=Files.readString(file);
        var settings=parse(original); // Validate before changing an owner's file.
        String expanded=expand(upgradeNameplates(original));
        if(!expanded.equals(original)){
            parse(expanded);
            if(expanded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65536)throw invalid("дополненный файл превышает 64 КиБ");
            if(!Files.readString(file).equals(original))throw new IOException("Rivet configuration changed while preparing update; retry startup");
            Path backup=Files.createTempFile(file.getParent(),"rivet-server-",".toml.bak");
            Files.copy(file,backup,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
            Path pending=Files.createTempFile(file.getParent(),".rivet-server-",".tmp");
            try{
                Files.copy(file,pending,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
                Files.writeString(pending,expanded);
                try{Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unsupported){Files.move(pending,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(pending);}
        }
        return settings;
    }
    /** Add only absent template fields; existing text, including secrets and comments, stays intact. */
    private static String expand(String original)throws IOException{
        var present=flatten(new TomlParser().parse(original));
        var additions=new LinkedHashMap<String,StringBuilder>();
        String section="";var comments=new StringBuilder();
        for(String line:template().replace("\r\n","\n").split("\n")){
            if(line.startsWith("[")){section=line.substring(1,line.indexOf(']'));comments.setLength(0);}
            else if(line.isBlank()||line.stripLeading().startsWith("#"))comments.append(line).append('\n');
            else{
                String key=line.substring(0,line.indexOf('=')).strip();
                if(!present.containsKey(section+"."+key))additions.computeIfAbsent(section,k->new StringBuilder()).append(comments).append(line).append('\n');
                comments.setLength(0);
            }
        }
        if(additions.isEmpty())return original;
        String newline=original.contains("\r\n")?"\r\n":"\n";
        var headers=new ArrayList<ConfigHeader>();String quote="";int offset=0;
        for(String line:original.split("(?<=\n)",-1)){
            if(quote.isEmpty()){
                var match=java.util.regex.Pattern.compile("^\\s*\\[([^\\[].*)]\\s*(?:#.*)?$").matcher(line.strip());
                if(match.matches()){
                    var parsed=flatten(new TomlParser().parse("["+match.group(1)+"]\n__rivet_section_probe = true\n"));
                    String path=parsed.keySet().iterator().next();headers.add(new ConfigHeader(path.substring(0,path.lastIndexOf('.')),offset));
                }
            }
            quote=multilineQuote(line,quote);offset+=line.length();
        }
        var insertions=new TreeMap<Integer,StringBuilder>();
        for(var entry:additions.entrySet()){
            int index=-1;for(int n=0;n<headers.size();n++)if(headers.get(n).section().equals(entry.getKey())){index=n;break;}
            if(index>=0){int at=index+1<headers.size()?headers.get(index+1).offset():original.length();insertions.computeIfAbsent(at,k->new StringBuilder()).append(newline).append(entry.getValue().toString().replace("\n",newline));}
            else if(present.keySet().stream().anyMatch(key->key.startsWith(entry.getKey()+"."))){
                // A section written as root-level dotted keys must keep that TOML representation.
                int at=headers.isEmpty()?original.length():headers.getFirst().offset();
                StringBuilder text=insertions.computeIfAbsent(at,k->new StringBuilder());text.append(newline);
                for(String line:entry.getValue().toString().split("\n"))text.append(line.isBlank()||line.stripLeading().startsWith("#")?line:entry.getKey()+"."+line).append(newline);
            }else insertions.computeIfAbsent(original.length(),k->new StringBuilder()).append(newline).append('[').append(entry.getKey()).append(']').append(newline).append(entry.getValue().toString().replace("\n",newline));
        }
        var result=new StringBuilder(original);for(var entry:insertions.descendingMap().entrySet())result.insert(entry.getKey(),entry.getValue());return result.toString();
    }
    /** Preserve the meaning of older booleans and the owner's TOML formatting. */
    private static String upgradeNameplates(String original){
        var out=new StringBuilder();String section="",quote="";
        for(String line:original.split("(?<=\n)",-1)){
            if(quote.isEmpty()){
                String trimmed=line.strip();
                if(trimmed.matches("\\[([^\\[].*)]\\s*(?:#.*)?")){
                    var parsed=flatten(new TomlParser().parse(trimmed+"\n__rivet_section_probe = true\n"));
                    String path=parsed.keySet().iterator().next();section=path.substring(0,path.lastIndexOf('.'));
                }else{
                    var match=java.util.regex.Pattern.compile("^(\\s*[^=]+?\\s*=\\s*)(true|false)(\\s*(?:#.*)?(?:\\r?\\n)?)$").matcher(line);
                    if(match.matches()&&!trimmed.startsWith("#")){
                        String header=section.isEmpty()?"":"["+section+"]\n";
                        var parsed=flatten(new TomlParser().parse(header+line));
                        if(parsed.containsKey("display.nameplates")){
                            String newline=line.endsWith("\r\n")?"\r\n":"\n";
                            out.append("# nameplates: \"rivet\", \"base\" или \"hidden\" (клиенты Rivet)."+newline);
                            line=match.group(1)+"\""+(match.group(2).equals("true")?"rivet":"base")+"\""+match.group(3);
                        }
                    }
                }
            }
            out.append(line);quote=multilineQuote(line,quote);
        }
        return out.toString();
    }
    private record ConfigHeader(String section,int offset){}
    private static String multilineQuote(String line,String quote){
        for(int n=0;n<line.length();n++){
            if(!quote.isEmpty()){
                if(quote.equals("\"\"\"")&&line.charAt(n)==92){n++;continue;}
                if(line.startsWith(quote,n)){n+=2;quote="";}
            }else{
                char c=line.charAt(n);if(c=='#')break;
                if(c=='\''||c=='"'){
                    String triple=String.valueOf(c).repeat(3);
                    if(line.startsWith(triple,n)){quote=triple;n+=2;}
                    else while(++n<line.length()){if(c=='"'&&line.charAt(n)==92)n++;else if(line.charAt(n)==c)break;}
                }
            }
        }return quote;
    }
    public static ServerSettings parse(String text){
        Map<String,Object> values,defaults;
        try{values=flatten(new TomlParser().parse(text));defaults=flatten(new TomlParser().parse(template()));}
        catch(Exception failure){throw invalid("ошибка TOML: проверьте кавычки, типы и повторяющиеся параметры (значения скрыты)");}
        // Preserve old boolean semantics while the file is upgraded to explicit modes.
        if(values.get("display.nameplates") instanceof Boolean old)values.put("display.nameplates",old?"rivet":"base");
        // Obsolete punishment defaults are ignored, so existing production configs still load.
        values.remove("project.repository");values.remove("project.requirePack");
        values.remove("chat.allowItems");values.remove("integrations.luckperms");values.remove("display.chatMode");values.remove("votes.actions.banMinutes");values.remove("votes.actions.muteMinutes");
        for(var e:defaults.entrySet())if(!values.containsKey(e.getKey())&&List.of("statistics.","skins.","votes.","community.","menu.","updates.","retention.","chat.","display.","tasks.","spark.","groups.","storage.","board.","events.","polls.","ideas.","reports.","server.","pack.").stream().anyMatch(e.getKey()::startsWith))values.put(e.getKey(),e.getValue());
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
    public FeatureModules modules(){return FeatureModules.from(this);}
    private void validate(){
        modules();
        range("pack.network.port",1,65535);range("pack.network.publicPort",1,65535);
        range("pack.downloads.totalMiB",0,1000000);range("pack.downloads.clientMiB",0,1000000);range("pack.downloads.concurrent",0,Integer.MAX_VALUE);
        String packDirectory=text("pack.directory");
        if(packDirectory.isBlank()||Path.of(packDirectory).isAbsolute()||Path.of(packDirectory).normalize().startsWith(".."))throw invalid("pack.directory: ожидается относительная папка внутри сервера");
        if(text("pack.network.bind").isBlank()||text("pack.network.host").length()>253||text("pack.network.host").contains("/")||text("pack.network.host").chars().anyMatch(Character::isWhitespace))throw invalid("pack.network: ожидается адрес без протокола и пути");
        range("retention.reportDays",1,365);range("retention.trashDays",1,365);range("retention.auditEntries",100,100000);skins();range("connection.handshakeTimeout",3,60);range("auth.minPasswordLength",6,128);
        range("tasks.maxPerOwner",0,Integer.MAX_VALUE);range("tasks.maxSubtasks",0,Integer.MAX_VALUE);range("tasks.maxComments",0,Integer.MAX_VALUE);
        range("spark.minTps",1,20);range("spark.maxMspt",1,1000);range("spark.durationSeconds",1,3600);range("spark.cooldownSeconds",1,86400);
        range("database.port",1,65535);range("database.pool",2,32);
        if(!Set.of("false","base","hybrid").contains(text("auth.mode")))throw invalid("auth.mode: ожидается строка false, base или hybrid");
        if(text("menu.help").length()>2000)throw invalid("menu.help: максимум 2000 символов");
        String env=text("database.passwordEnv");if(!env.isEmpty()&&!env.matches("[A-Za-z_][A-Za-z0-9_]*"))throw invalid("database.passwordEnv: неверное имя переменной");
        for(String key:List.of("display.tab"))if(!Set.of("auto","rivet","compatible").contains(text(key)))throw invalid(key+": auto, rivet или compatible");
        if(!Set.of("rivet","base","hidden").contains(text("display.nameplates")))throw invalid("display.nameplates: rivet, base или hidden в кавычках");
        for(String key:List.of("chat.localName","chat.globalName"))if(text(key).codePointCount(0,text(key).length())>40||text(key).codePoints().anyMatch(Character::isISOControl))throw invalid(key+": до 40 символов, без переводов строк");
        for(String key:List.of("chat.localColor","chat.globalColor","chat.groupColor"))if(!text(key).matches("#[0-9a-fA-F]{6}"))throw invalid(key+": ожидается цвет #RRGGBB в кавычках");
        ChatFormat.parse(text("chat.format"),false);ChatFormat.parse(text("chat.channelFormat"),true);
        if(number("chat.localRadius")<1||number("chat.localRadius")>1000)throw invalid("chat.localRadius: 1–1000");
        try{votes();community();menu();new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),"validation",text("database.sslMode"),text("database.sslCert"),number("database.pool"));}
        catch(Exception failure){throw invalid("проверьте диапазоны votes, списки community, ссылки menu и параметры database; значения скрыты");}
    }
    public DatabaseSettings database(){return database(System::getenv);}
    DatabaseSettings database(Function<String,String> environment){
        String env=text("database.passwordEnv"),password=env.isEmpty()?text("database.password"):environment.apply(env);
        if(password==null||password.isBlank())throw invalid("заполните database.password или заданную переменную database.passwordEnv");
        return new DatabaseSettings(text("database.host"),number("database.port"),text("database.database"),text("database.username"),password,text("database.sslMode"),text("database.sslCert"),number("database.pool"));
    }
    public dev.abros.rivet.core.skins.SkinSettings skins(){return new dev.abros.rivet.core.skins.SkinSettings(flag("skins.enabled"),number("skins.maxSizeMiB"),number("skins.maxPerPlayer"),text("skins.fallback"));}
    public TaskLimits taskLimits(){return new TaskLimits(number("tasks.maxPerOwner"),number("tasks.maxSubtasks"),number("tasks.maxComments"));}
    public SparkTimeline.AlertSettings sparkAlerts(){return new SparkTimeline.AlertSettings(number("spark.minTps"),number("spark.maxMspt"),number("spark.durationSeconds"),number("spark.cooldownSeconds"));}
    public PlayerStatistics.Settings statistics(){return new PlayerStatistics.Settings(flag("statistics.firstJoin"),flag("statistics.lastActivity"),flag("statistics.playTime"),flag("statistics.session"),flag("statistics.deaths"));}
    public ModerationVotes.Settings votes(){return new ModerationVotes.Settings(flag("votes.enabled"),number("votes.minPlayers"),number("votes.durationSeconds"),number("votes.minPlayMinutes"),number("votes.initiatorCooldownMinutes"),number("votes.targetCooldownMinutes"),flag("votes.actions.kick"),flag("votes.actions.ban"),flag("votes.actions.mute"));}
    public JsonObject community(){var j=new JsonObject();j.addProperty("groupsTitle",text("community.groupsTitle"));j.addProperty("maxMemberships",number("community.maxMemberships"));for(String key:List.of("categories","groupTypes","sections")){var a=new JsonArray();for(Object v:(List<?>)values.get("community."+key)){if(!(v instanceof String s))throw invalid("community."+key+": ожидаются строки");a.add(s);}j.add(key,a);}return CommunityStore.validateConfig(j);}
    public JsonObject menu(){var j=new JsonObject();var a=new JsonArray();for(Object v:(List<?>)values.get("menu.links")){if(!(v instanceof UnmodifiableConfig c))throw invalid("menu.links: ожидаются name и url");var link=new JsonObject();for(var e:c.entrySet()){if(!(e.getValue() instanceof String s))throw invalid("menu.links: ожидаются строки");link.addProperty(e.getKey(),s);}a.add(link);}j.add("links",a);return ServerMenuData.validate(j);}
    private static IllegalArgumentException invalid(String detail){return new IllegalArgumentException("Rivet: config/rivet-server.toml — "+detail+". Запуск остановлен.");}
}
