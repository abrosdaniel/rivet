package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Bounded, server-thread timeline. Missing or non-finite measurements remain missing. */
public final class SparkTimeline {
    public record AlertSettings(int minimumTps,int maximumMspt,int sustainedSeconds,int cooldownSeconds) {
        public static AlertSettings defaults(){return new AlertSettings(18,50,30,300);}
        public AlertSettings {if(minimumTps<1||minimumTps>20||maximumMspt<1||maximumMspt>1000||sustainedSeconds<1||sustainedSeconds>3600||cooldownSeconds<1||cooldownSeconds>86400)throw new IllegalArgumentException("Invalid spark alert thresholds");}
    }
    private final AlertSettings alerts;
    public SparkTimeline(){this(AlertSettings.defaults());}
    public SparkTimeline(AlertSettings alerts){this.alerts=Objects.requireNonNull(alerts);}
    public AlertSettings alerts(){return alerts;}
    private final ArrayDeque<JsonObject> samples = new ArrayDeque<>();
    private long unhealthySince, lastAlert;
    public void clear() { samples.clear(); unhealthySince=0; lastAlert=0; }
    public boolean add(JsonObject sample, long now) {
        samples.addLast(sample.deepCopy());
        while(samples.size()>450 || !samples.isEmpty() && now-samples.peekFirst().get("at").getAsLong()>900000) samples.removeFirst();
        boolean bad = number(sample,"tps")<alerts.minimumTps() || number(sample,"mspt")>alerts.maximumMspt();
        if(!bad){unhealthySince=0;return false;}
        if(unhealthySince==0) unhealthySince=now;
        if(now-unhealthySince>=alerts.sustainedSeconds()*1000L && (lastAlert==0 || now-lastAlert>=alerts.cooldownSeconds()*1000L)){lastAlert=now;return true;}
        return false;
    }
    public JsonArray snapshot(){
        var result=new JsonArray();var list=List.copyOf(samples);int stride=Math.max(1,(list.size()+119)/120);
        for(int i=0;i<list.size();i+=stride){var sample=list.get(Math.min(list.size()-1,i+stride-1));var compact=new JsonObject();compact.add("at",sample.get("at"));for(String key:List.of("tps","mspt","cpuProcess"))if(sample.has(key))compact.add(key,sample.get(key));result.add(compact);}
        return result;
    }
    public static double number(JsonObject row,String key){try{return row.has(key)?row.get(key).getAsDouble():Double.NaN;}catch(RuntimeException invalid){return Double.NaN;}}
    public static void metric(JsonObject row,String key,double value){if(Double.isFinite(value)&&value>=0)row.addProperty(key,value);}
    /** Read only the known command responses; unknown responses must never imply idle. */
    public static String profilerState(String text){
        if(text.contains("The profiler isn't running!"))return "idle";
        if(text.contains("Profiler is already running!") || text.contains(" is now running!"))return "running";
        return "unknown";
    }
    public static Optional<String> reportUrl(String text){
        var matcher=java.util.regex.Pattern.compile("https://spark\\.lucko\\.me/[A-Za-z0-9_-]+(?:[?#][A-Za-z0-9_=&%.-]+)?").matcher(text);
        return matcher.find()?Optional.of(matcher.group()):Optional.empty();
    }
}
