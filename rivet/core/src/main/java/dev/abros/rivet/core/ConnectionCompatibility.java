package dev.abros.rivet.core;
import com.google.gson.*;
import java.util.*;
/** Stable connection envelope. New features are negotiated as optional names. */
public final class ConnectionCompatibility {
    private ConnectionCompatibility(){}
    public static final Set<String> FEATURES=Set.of("group-map-pages","chat-history","map-position-pages","map-positions","map-activities","group-map","world-map","menu-deltas","social-display","spark-diagnostics","compatibility-adapters","scheduled-announcements","notice-actions","task-archive","resource-alternatives","hud","menu","auth","pack","home","players","board","groups","events","polls","ideas","notifications","help","admin","player-statistics","admin-tools","moderation-votes","moderation-vote-duration","moderation-vote-status","skins","skin-names","skin-order","community-plus","skin-receipts","task-tools","player-tools","community-extensions");
    public static JsonArray features(){var array=new JsonArray();FEATURES.stream().sorted().forEach(array::add);return array;}
    public static String branch(String version){if(!Versions.sameMajor(version,version))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_295eba40e7fd"));return version.split("\\.")[0]+".x";}
    public static Set<String> common(JsonElement value){
        if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()>64)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_223e38c45baa"));
        var result=new HashSet<String>();for(var item:value.getAsJsonArray()){if(!item.isJsonPrimitive()||!item.getAsJsonPrimitive().isString()||item.getAsString().length()>64)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_65fb066f2bc0"));if(FEATURES.contains(item.getAsString()))result.add(item.getAsString());}return Set.copyOf(result);
    }
    public static String failure(String server,String client,JsonObject protocols){
        if(!Versions.isRelease(server)||!Versions.isRelease(client))return dev.abros.rivet.core.Messages.text("rivet.core.invalid_rivet_version_during_connection_09c39f30");
        if(!Versions.sameMajor(server,client))return dev.abros.rivet.core.Messages.text("rivet.core.this_server_requires_the_rivet_branch_0fe37ee0")+branch(server)+dev.abros.rivet.core.Messages.text("rivet.core.select_a_rivet_version_360c541c");
        if(Versions.compare(client,server)<0)return dev.abros.rivet.core.Messages.text("rivet.core.this_server_requires_rivet_version_dd515847")+server+dev.abros.rivet.core.Messages.text("rivet.core.in_branch_a939c92b")+branch(server)+dev.abros.rivet.core.Messages.text("rivet.core.update_rivet_f7655d27");
        for(String key:List.of("pack","auth","menu"))if(protocols==null||!protocols.has(key)||!WireProtocols.current().get(key).equals(protocols.get(key)))return dev.abros.rivet.core.Messages.text("rivet.core.incompatible_rivet_release_select_another_version_7a410f75")+branch(server)+".";
        return "";
    }
}
