package dev.abros.rivet.core;

import com.google.gson.*;

/** Bounded server search; private marker names use the same audience as the group map. */
final class CommunitySearch {
    private final PgDatabase database;
    CommunitySearch(PgDatabase database){this.database=database;}
    JsonArray search(CommunityStore store,CommunityStore.Actor actor,JsonObject input,String query,long now,JsonObject config)throws Exception{
        var entries=new JsonArray();
        if(query.strip().length()<2)return entries;
        if(store.enabled("groups")){
            var search=input.deepCopy();search.addProperty("op","list");search.addProperty("section","groups");search.remove("cursor");
            for(var row:new CommunityQueries(database).list(actor,search,"groups",now,config))if(entries.size()<30)entries.add(row);
            try(var statement=database.connection().prepareStatement("SELECT i.id,i.group_id,i.body->>'title' FROM community_group_items i JOIN documents g ON g.id=i.group_id WHERE i.kind='place' AND g.body->>'status'='open' AND strpos(lower(i.body->>'title'),lower(?))>0 AND (? OR EXISTS(SELECT 1 FROM community_relations r WHERE r.document=g.id AND r.kind='members' AND r.actor=?)) ORDER BY i.id LIMIT 5")){
                statement.setString(1,query);statement.setBoolean(2,actor.admin());statement.setString(3,actor.id());
                try(var rows=statement.executeQuery()){while(rows.next()){
                    var row=new JsonObject();row.addProperty("section","groups");row.addProperty("kind","group-place");row.addProperty("id",rows.getString(2));row.addProperty("itemId",rows.getString(1));row.addProperty("title",rows.getString(3));entries.add(row);
                }}
            }
        }
        if(store.modules().enabled("players"))try(var statement=database.connection().prepareStatement("SELECT id,name FROM people WHERE strpos(lower(name),lower(?))>0 ORDER BY name,id LIMIT 5")){
            statement.setString(1,query);
            try(var rows=statement.executeQuery()){while(rows.next()){
                var row=new JsonObject();row.addProperty("section","players");row.addProperty("id",rows.getString(1));row.addProperty("title",rows.getString(2));entries.add(row);
            }}
        }
        return entries;
    }
}
