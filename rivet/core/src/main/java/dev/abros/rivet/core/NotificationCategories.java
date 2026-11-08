package dev.abros.rivet.core;

import com.google.gson.JsonObject;

/** Stable category IDs preserve old section preferences while isolating external sources. */
public final class NotificationCategories {
    public static boolean valid(String value) {
        return java.util.Set.of("help","server","spark").contains(value)||CommunityStore.SECTIONS.contains(value)
            || value.matches("[a-z][a-z0-9_]{1,63}:[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}");
    }
    public static String source(JsonObject notice) {
        if(notice.has("sourceType"))return Json.str(notice,"sourceType");
        String route=Json.opt(notice,"routeSection","");
        return java.util.Set.of("server","spark").contains(route)?route:Json.opt(notice,"section","notifications");
    }
    public static boolean muted(JsonObject preferences,JsonObject notice) {
        var values=preferences.getAsJsonArray("muted");
        return values!=null&&(values.contains(new com.google.gson.JsonPrimitive(source(notice)))
            ||values.contains(new com.google.gson.JsonPrimitive(Json.opt(notice,"section","notifications"))));
    }
    static String sql(String alias){String body=alias+"body";return "coalesce("+body+"->>'sourceType',CASE WHEN "+body+"->>'routeSection' IN ('server','spark') THEN "+body+"->>'routeSection' ELSE "+body+"->>'section' END,'notifications')";}
    private NotificationCategories(){}
}
