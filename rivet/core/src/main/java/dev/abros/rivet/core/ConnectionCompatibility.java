package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Stable connection envelope. New features are negotiated as optional names. */
public final class ConnectionCompatibility {
    private ConnectionCompatibility(){}
    public static final Set<String> FEATURES=Set.of("menu-deltas","social-display","spark-diagnostics","compatibility-adapters","scheduled-announcements","notice-actions","task-archive","resource-alternatives","hud","menu","auth","pack","home","players","board","groups","events","polls","ideas","notifications","help","admin","player-statistics","admin-tools","moderation-votes","moderation-vote-duration","moderation-vote-status","skins","skin-names","skin-order","community-plus","skin-receipts","task-tools","player-tools","community-extensions");
    public static JsonArray features(){var array=new JsonArray();FEATURES.stream().sorted().forEach(array::add);return array;}
    public static String branch(String version){if(!Versions.sameMajor(version,version))throw new IllegalArgumentException("Invalid Rivet version");return version.split("\\.")[0]+".x";}
    public static Set<String> common(JsonElement value){
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()>64)throw new IllegalArgumentException("Invalid Rivet features");
        var result=new HashSet<String>();for(var item:value.getAsJsonArray()){if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString()||item.getAsString().length()>64)throw new IllegalArgumentException("Invalid Rivet feature");if(FEATURES.contains(item.getAsString()))result.add(item.getAsString());}return Set.copyOf(result);
    }
    public static String failure(String server,String client,JsonObject protocols){
        if(!Versions.isRelease(server)||!Versions.isRelease(client))return "Некорректная версия Rivet при подключении.";
        if(!Versions.sameMajor(server,client))return "Для этого сервера нужна ветка Rivet "+branch(server)+". Выберите версию Rivet.";
        if(Versions.compare(client,server)<0)return "Для этого сервера нужен Rivet не ниже "+server+" в ветке "+branch(server)+". Обновите Rivet.";
        for(String key:List.of("pack","auth","menu"))if(protocols==null||!protocols.has(key)||!WireProtocols.current().get(key).equals(protocols.get(key)))return "Несовместимый выпуск Rivet. Выберите другую версию ветки "+branch(server)+".";
        return "";
    }
}
