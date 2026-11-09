package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.UUID;

/** Resolves persisted destinations for both legacy and typed notices. Never trusts client routes. */
final class NotificationNavigation {
    static boolean available(JsonObject notice) {
        String section=Json.opt(notice,"routeSection",Json.opt(notice,"section",""));
        if(!notice.has("sourceType")&&(section.equals("home")||RetiredFeatures.contains(section)))return false;
        return notice.has("sourceType") ? notice.has("notificationAction")
            : !Json.opt(notice,"routeId",Json.opt(notice,"target","")).isEmpty()
                || Set.of("spark","server").contains(Json.opt(notice,"routeSection",""));
    }
    static JsonObject resolve(CommunityStore store, CommunityStore.Actor actor, JsonObject notice, JsonObject request) throws Exception {
        String section,id;
        if(notice.has("sourceType")) {
            if(!request.has("sourceRevision") || request.get("sourceRevision").getAsBigDecimal().longValueExact()!=notice.get("sourceRevision").getAsLong())
                throw new CommunityFailure(CommunityFailure.Code.CONFLICT,Messages.text("rivet.core.entry_contents_changed_refreshing_the_card_da2e09dc"));
            var value=notice.get("notificationAction");
            if(value==null||!value.isJsonObject())throw unavailable();
            section=switch(Json.opt(value.getAsJsonObject(),"type","")) {
                case "openGroup" -> "groups";
                case "openReport" -> "help";
                case "openOwnPublication" -> "map-publications";
                default -> throw unavailable();
            };
            id=Json.str(value.getAsJsonObject(),"id");
        } else {
            section=Json.opt(notice,"routeSection",Json.opt(notice,"section",""));
            id=Json.opt(notice,"routeId",Json.opt(notice,"target",""));
            if(section.equals("home"))section="tasks";
        }
        RetiredFeatures.requireActive(section);
        var route=new JsonObject();route.addProperty("section",section);route.addProperty("id",id);
        if(section.equals("trade")){
            // The owned persisted notice supplies only an ID; opening checks participation again.
            route.addProperty("id",UUID.fromString(id).toString());
        } else if(section.equals("map-publications")) {
            var publication=store.record("map-object",UUID.fromString(id).toString());
            if(publication==null||!actor.id().equals(Json.opt(publication,"owner","")))throw unavailable();
            route.addProperty("type",Json.str(publication,"type"));
        } else if(section.equals("help")) {
            store.modules().require("reports");
            new CommunityReports(store).get(UUID.fromString(actor.id()),id);
        } else if(section.equals("server")) {
            store.modules().require("server");
            // A public server-information screen, never the administration workspace.
            route.addProperty("id","");
        } else if(section.equals("spark")) {
            store.modules().require("server");
            if(!actor.admin())throw unavailable();
        } else if(section.equals("tasks")) {
            route.addProperty("group",Json.opt(store.sharedReference(actor,section,id),"group",""));
        } else if(Set.of("groups","board","events","polls","ideas").contains(section)) {
            store.sharedReference(actor,section,id);
        } else throw unavailable();
        return route;
    }
    private static CommunityFailure unavailable() {
        return new CommunityFailure(CommunityFailure.Code.FORBIDDEN,Messages.text("rivet.core.quick_action_unavailable_e3aeeebd"));
    }
    private NotificationNavigation(){}
}
