package dev.abros.rivet.core;

import com.google.gson.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Persistent community commands. All mutations and their notifications share one transaction. */
public final class CommunityStore implements AutoCloseable {
    public record Actor(String id,String name,boolean admin,boolean events,boolean seeHiddenPlayers) {public Actor(String id,String name,boolean admin,boolean events){this(id,name,admin,events,false);}}
    public static final List<String> SECTIONS=List.of("home","players","board","groups","events","polls","ideas","notifications");
    private final PgDatabase database;
    private final NotificationStore notifications;
    private volatile int reportDays=30,trashDays=30,auditEntries=1000;
    public void retention(int reports,int trash,int audit){if(reports<1||reports>365||trash<1||trash>365||audit<100||audit>100000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_retention_period_7b838cf9"));reportDays=reports;trashDays=trash;auditEntries=audit;}
    public JsonObject mapReviewSummary(Actor actor)throws Exception{
        if(!actor.admin())throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,Messages.text("rivet.core.insufficient_permissions_3f48d3d9"));
        return database.communityTransaction(()->{
            var result=new JsonObject();var entries=new JsonArray();
            try(var q=connection().createStatement();var rows=q.executeQuery("SELECT count(*) FROM records WHERE namespace='map-object' AND jsonb_typeof(body->'draft')='object'")){rows.next();result.addProperty("pendingPublications",rows.getLong(1));}
            try(var q=connection().createStatement();var rows=q.executeQuery("SELECT id,body->>'author',body->>'type',body->'draft'->>'name' FROM records WHERE namespace='map-object' AND jsonb_typeof(body->'draft')='object' ORDER BY (body->>'revision')::bigint,id LIMIT 6")){
                while(rows.next()){var item=new JsonObject();item.addProperty("id",rows.getString(1));item.addProperty("author",rows.getString(2));item.addProperty("type",rows.getString(3));item.addProperty("name",rows.getString(4));entries.add(item);}
            }result.add("attentionPublications",entries);return result;
        });
    }
    public JsonObject retentionPreview(long now)throws Exception{return database.communityTransaction(()->{var result=new JsonObject();for(String type:List.of("reports","trash","audit")){if(type.equals("reports")&&!modules.enabled("reports")){result.addProperty(type,0);continue;}String sql=switch(type){case "reports"->"SELECT count(*) FROM records WHERE namespace='reports' AND body->>'status'='resolved' AND coalesce((body->>'repliedAt')::bigint,(body->>'createdAt')::bigint)<?";case "trash"->"SELECT count(*) FROM documents WHERE body->>'section' IN (SELECT jsonb_array_elements_text(?::jsonb)) AND body->>'status'='deleted' AND (body->>'deletedAt')::bigint<=?";default->"SELECT greatest(0,count(*)-?) FROM records WHERE namespace='audit'";};try(var q=connection().prepareStatement(sql)){if(type.equals("trash"))q.setString(1,retentionSections().toString());q.setLong(type.equals("trash")?2:1,type.equals("audit")?auditEntries:now-(type.equals("reports")?reportDays:trashDays)*86400000L);try(var rows=q.executeQuery()){rows.next();result.addProperty(type,rows.getLong(1));}}}return result;});}
    private final CommunityModules services;
    Set<String> activeModules(){return services.active();}
    private volatile JsonObject config;private volatile long lastReminderSweep;
    private final TaskLimits taskLimits;
    private final FeatureModules modules;
    public FeatureModules modules(){return modules;}
    public CommunityStore(PgDatabase database,JsonObject settings){this(database,settings,TaskLimits.defaults());}
    public CommunityStore(PgDatabase database,JsonObject settings,TaskLimits taskLimits){this(database,settings,taskLimits,FeatureModules.all());}
    public CommunityStore(PgDatabase database,JsonObject settings,TaskLimits taskLimits,FeatureModules modules){this.database=database;this.notifications=new NotificationStore(database);this.modules=Objects.requireNonNull(modules);config=modules.sections(validateConfig(settings));this.taskLimits=Objects.requireNonNull(taskLimits);services=new CommunityModules(database,this,modules);}
    TaskLimits taskLimits(){return taskLimits;}
    Connection connection(){return database.connection();}
    public static JsonObject defaults(){var j=new JsonObject();j.addProperty("groupsTitle","Объединения");j.addProperty("maxMemberships",3);j.add("categories",new JsonArray());var types=new JsonArray();for(String t:List.of("Город","Команда","Гильдия","Поселение"))types.add(t);j.add("groupTypes",types);var sections=new JsonArray();SECTIONS.forEach(sections::add);j.add("sections",sections);return j;}
    public static JsonObject validateConfig(JsonObject j){Json.keys(j,"groupsTitle","maxMemberships","categories","groupTypes","sections");text(j,"groupsTitle",40);int max=number(j,"maxMemberships",1,20);for(String key:List.of("categories","groupTypes","sections")){var a=j.getAsJsonArray(key);if(a==null||a.size()>20)throw new IllegalArgumentException("Invalid "+key);var seen=new HashSet<String>();for(var e:a){String v=e.getAsString();if(v.isBlank()||v.length()>40||!seen.add(v)||key.equals("sections")&&!SECTIONS.contains(v))throw new IllegalArgumentException("Invalid "+key);}}if(!j.getAsJsonArray("sections").contains(new JsonPrimitive("home"))||!j.getAsJsonArray("sections").contains(new JsonPrimitive("notifications")))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_fb67d26c84e4"));if(j.getAsJsonArray("groupTypes").isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_2539a660c1d1"));return j.deepCopy();}
    public void configure(JsonObject j){config=modules.sections(validateConfig(j));}
    public JsonObject configuration(){return config.deepCopy();}
    public void seen(Actor a)throws Exception{database.communityTransaction(()->{seen(a,a.admin?"Администратор":"Игрок");return null;});}
    public void seen(Actor a,String role)throws Exception{database.communityTransaction(()->{database.lock("person:"+a.id);try(var s=connection().prepareStatement("INSERT INTO people VALUES(?,COALESCE((SELECT name FROM auth_accounts WHERE uuid=?),?),?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,seen=excluded.seen,role=excluded.role")){s.setString(1,a.id);s.setString(2,a.id);s.setString(3,a.name);s.setLong(4,System.currentTimeMillis());s.setString(5,role);s.executeUpdate();}return null;});}
    public JsonArray people(String query,int page)throws Exception{return new CommunityPeople(database).people(query,page);}
    public JsonObject peopleCursor(String query,String cursor)throws Exception{return new CommunityPeople(database).peopleCursor(query,cursor);}
    public void filterProfiles(JsonArray entries,String viewer)throws Exception{database.communityTransaction(()->{
        var settings=new HashMap<String,JsonObject>();var shared=new HashSet<String>();var ids=new HashSet<String>();for(var e:entries)ids.add(Json.str(e.getAsJsonObject(),"uuid"));if(ids.isEmpty())return null;
        try(var q=connection().prepareStatement("SELECT id,body FROM records WHERE namespace='profile-privacy' AND id=ANY(?)")){q.setArray(1,connection().createArrayOf("text",ids.toArray()));try(var rs=q.executeQuery()){while(rs.next())settings.put(rs.getString(1),Json.parse(rs.getString(2)));}}
        if(modules.enabled("groups")&&settings.values().stream().anyMatch(p->Json.opt(p,"mode","public").equals("members")))try(var q=connection().prepareStatement("SELECT DISTINCT a.actor FROM community_relations a JOIN community_relations b ON a.document=b.document JOIN documents d ON d.id=a.document WHERE a.kind='members' AND b.kind='members' AND a.actor=ANY(?) AND b.actor=? AND d.body->>'status'='open'")){q.setArray(1,connection().createArrayOf("text",ids.toArray()));q.setString(2,viewer);try(var rs=q.executeQuery()){while(rs.next())shared.add(rs.getString(1));}}
        for(var e:entries){var row=e.getAsJsonObject();String target=Json.str(row,"uuid");if(target.equals(viewer))continue;var prefs=settings.get(target);String mode=prefs==null?"public":Json.opt(prefs,"mode","public");boolean visible=mode.equals("public")||mode.equals("members")&&shared.contains(target);if(!visible){row.remove("about");row.remove("interests");}if(!visible||prefs!=null&&prefs.has("statistics")&&!prefs.get("statistics").getAsBoolean())row.remove("statistics");}return null;});}

    public String sharedEntry(Actor actor,String section,String id)throws Exception{return Json.str(sharedReference(actor,section,id),"title");}
    public JsonObject sharedReference(Actor actor,String section,String id)throws Exception{modules.require(section);return database.communityTransaction(()->{
        require(enabled(section)&&Set.of("board","groups","events","polls","ideas").contains(section));var j=get(id);require(section.equals(str(j,"section"))&&!Set.of("hidden","deleted").contains(str(j,"status")));new CommunityPlus(database,this).requireVisible(actor,j);var result=new JsonObject();result.addProperty("title",str(j,"title"));return result;
    });}
    public void profiles(JsonArray entries)throws Exception{database.communityTransaction(()->{new CommunityPlus(database,this).profiles(entries);return null;});}
    public String personName(String id)throws Exception{return database.communityTransaction(()->{try(var q=connection().prepareStatement("SELECT name FROM people WHERE id=?")){q.setString(1,id);try(var rs=q.executeQuery()){if(!rs.next())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f5a29b325d43"));return rs.getString(1);}}});}
    JsonObject get(String id)throws Exception{return get(id,false);}
    JsonObject get(String id,boolean lock)throws Exception{if(lock)try(var q=connection().prepareStatement("SELECT id FROM documents WHERE id=? FOR UPDATE")){q.setString(1,id);q.executeQuery().close();}try(var q=connection().prepareStatement("SELECT body FROM community_documents WHERE id=?")){q.setString(1,id);try(var rows=q.executeQuery()){if(!rows.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,dev.abros.rivet.core.Messages.text("rivet.core.entry_not_found_82764bdc"));return document(rows.getString(1));}}}
    static JsonObject document(String json){var j=JsonParser.parseString(json).getAsJsonObject();if(!j.has("revision")||j.get("revision").isJsonNull())j.addProperty("revision",0);if(j.has("participantOrder")){var ordered=new JsonObject();for(var id:j.getAsJsonArray("participantOrder"))ordered.add(id.getAsString(),j.getAsJsonObject("participants").get(id.getAsString()));j.add("participants",ordered);}return j;}
    void put(JsonObject j)throws Exception{put(j,true);}
    void put(JsonObject j,boolean visible)throws Exception{if(visible)j.addProperty("revision",j.has("revision")?j.get("revision").getAsLong()+1:1);CommunityDocuments.put(database,j);if(visible)ChangeOutbox.add(database,str(j,"section"),str(j,"id"),"");}
    boolean enabled(String section){return config.getAsJsonArray("sections").contains(new JsonPrimitive(section));}
    private static boolean owner(JsonObject j,Actor a){return a.id.equals(str(j,"owner"));}
    private static void require(boolean ok){if(!ok)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.action_unavailable_or_insufficient_permissions_be2b2971"));}
    private static String str(JsonObject j,String k){return Json.str(j,k);}
    private static String text(JsonObject j,String k,int max){String v=str(j,k).strip();if(v.isEmpty()||v.length()>max||v.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_field_1184c511")+k);return v;}
    private static int number(JsonObject j,String k,int min,int max){int v=j.get(k).getAsInt();if(v<min||v>max)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_field_1184c511")+k);return v;}
    private static JsonObject map(JsonObject j,String k){return j.getAsJsonObject(k);}
    private static String value(JsonObject j,String k){return Json.opt(j,k,"");}
    private static boolean contains(JsonObject j,String k,String id){return map(j,k).has(id);}
    private static long time(JsonObject j,String k){return j.get(k).getAsLong();}
    private static void boundedMap(JsonObject j,String k,String id){if(map(j,k).size()>=300&&!map(j,k).has(id))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.member_limit_reached_a22a69f7"));}
    private final ThreadLocal<String> notificationEvent=new ThreadLocal<>();
    void note(String recipient,String section,String target,String title)throws Exception{note(recipient,section,target,LocalizedText.literal(title));}
    void note(String recipient,String section,String target,LocalizedText title)throws Exception{
        String event=Objects.requireNonNullElse(notificationEvent.get(),section.equals("help")?"reply":"reminder");
        note(recipient,section,target,title,event);
    }
    void note(String recipient,String section,String target,String title,String event)throws Exception{note(recipient,section,target,LocalizedText.literal(title),event,null);}
    void note(String recipient,String section,String target,LocalizedText title,String event)throws Exception{note(recipient,section,target,title,event,null);}
    void lockAnnouncements()throws Exception{database.lock("scheduled-announcements");}
    void announcementNotice(String recipient,String id,String text)throws Exception{var route=new JsonObject();route.addProperty("routeSection","server");route.addProperty("routeId","");note(recipient,"home",id,text,"important",route);}
    private void note(String recipient,String section,String target,String title,String event,JsonObject route)throws Exception{note(recipient,section,target,LocalizedText.literal(title),event,route);}
    private void note(String recipient,String section,String target,LocalizedText title,String event,JsonObject route)throws Exception{
        var j=new JsonObject();j.addProperty("section",section);j.addProperty("target",target);title.put(j,"title");j.addProperty("at",System.currentTimeMillis());j.addProperty("event",event);j.addProperty("priority",section.equals("help")||Set.of("invite","application","apply","response","workAssign","cancel","reschedule","role","plusRemoveMember","plusEventInvite").contains(event)?"important":"ordinary");
        if(route!=null)route.entrySet().forEach(e->j.add(e.getKey(),e.getValue()));
        notifications.append(recipient,j);
    }
    public void externalNotice(String recipient,String title)throws Exception{database.communityTransaction(()->{externalNotice(recipient,title,"");return null;});}
    public void externalNotice(String recipient,LocalizedText title,String target)throws Exception{database.communityTransaction(()->{note(recipient,"help",target,title);return null;});}
    public void externalNotice(String recipient,String title,String target)throws Exception{database.communityTransaction(()->{note(recipient,"help",target,title);return null;});}
    /** External integration notices use the same transaction and outbox as community events. */
    public void integrationNotice(String recipient,String title,String body,String route,String event)throws Exception{
        if(title.length()>300||body.length()>1000||!Set.of("spark").contains(event))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_0f302b468f99"));
        database.communityTransaction(()->{var extra=new JsonObject();extra.addProperty("body",body);extra.addProperty("routeSection",route);extra.addProperty("routeId","");extra.addProperty("priority","important");note(recipient,"notifications","",title,event,extra);return null;});
    }
    @FunctionalInterface public interface ChatDelivery {void send(String title,Set<String> members)throws Exception;}
    /** Membership cannot change between authorization and actual delivery. */
    public void deliverGroupChat(String group,String actor,ChatDelivery delivery)throws Exception{
        modules.require("groups");database.communityTransaction(()->{
            try(var q=connection().prepareStatement("SELECT id FROM documents WHERE id=? FOR SHARE")){q.setString(1,group);try(var rows=q.executeQuery()){while(rows.next()){} }}
            var members=chatMembers(group,actor);var row=get(group);delivery.send(str(row,"title"),members);return null;
        });
    }
    public Set<String> chatMembers(String group,String actor)throws Exception{modules.require("groups");return database.communityTransaction(()->{var row=get(group);if(!str(row,"section").equals("groups")||!str(row,"status").equals("open")||!map(row,"members").has(actor))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.you_are_not_a_member_of_1b1da0f4"));return Set.copyOf(map(row,"members").keySet());});}
    public List<JsonObject> playerGroups(String id)throws Exception{if(!modules.enabled("groups"))return List.of();return database.communityTransaction(()->{var rows=new ArrayList<JsonObject>();try(var q=connection().prepareStatement("SELECT d.id,d.body->>'title' FROM documents d JOIN community_relations r ON r.document=d.id AND r.kind='members' AND r.actor=? WHERE d.section='groups' AND d.body->>'status'='open' ORDER BY d.id LIMIT 20")){q.setString(1,id);try(var rs=q.executeQuery()){while(rs.next()){var row=new JsonObject();row.addProperty("id",rs.getString(1));row.addProperty("title",rs.getString(2));rows.add(row);}}}return rows;});}
    void readNotice(String recipient,String id,boolean exact)throws Exception{notifications.read(recipient,id,exact);}
    public int unread(String id)throws Exception{return notifications.unread(id);}
    public long popupSequence(String id)throws Exception{return notifications.popupSequence(id);}
    public JsonObject preferences(String id)throws Exception{return notifications.preferences(id);}
    JsonObject publicView(JsonObject source,Actor a,int page)throws Exception{RetiredFeatures.requireActive(str(source,"section"));var j=source.deepCopy();j.add("actions",CommunityPolicy.actions(source,a,System.currentTimeMillis(),trashDays));String section=str(j,"section");j.addProperty("manage",owner(j,a)||a.admin);
        if(section.equals("groups")){boolean manager=manager(j,a);j.addProperty("manage",manager);if(!manager)map(j,"applications").keySet().removeIf(k->!k.equals(a.id));}
        if(section.equals("groups")){j.addProperty("isMember",contains(source,"members",a.id));j.addProperty("myRole",contains(source,"members",a.id)?map(source,"members").get(a.id).getAsString():"");j.addProperty("hasApplication",contains(source,"applications",a.id));j.addProperty("hasInvitation",contains(source,"invitations",a.id));slice(j,"members",page,20);slice(j,"applications",page,5);if(!manager(source,a))map(j,"invitations").keySet().removeIf(k->!k.equals(a.id));slice(j,"invitations",page,20);}
        if(!owner(source,a)&&!a.admin)j.remove("moderationReason");
        if(j.has("history")){var history=j.getAsJsonArray("history");var recent=new JsonArray();for(int i=Math.max(0,history.size()-5);i<history.size();i++)recent.add(history.get(i));j.add("history",recent);}
        if(j.has("location")&&j.getAsJsonObject("location").has("membersOnly")&&j.getAsJsonObject("location").get("membersOnly").getAsBoolean()){
            boolean allowed=a.admin||owner(source,a)||str(source,"section").equals("groups")&&contains(source,"members",a.id);
            if(!allowed&&!value(source,"group").isEmpty()){var group=get(value(source,"group"));allowed=str(group,"status").equals("open")&&contains(group,"members",a.id);}
            if(!allowed)j.remove("location");
        }if(section.equals("groups")){j.remove("location");if(j.has("territory"))j.add("territory",dev.abros.rivet.core.map.MapTerritoryJson.write(dev.abros.rivet.core.map.MapTerritoryJson.read(j.getAsJsonObject("territory"),UUID.fromString(str(j,"id")),str(j,"title"))));}return j;
    }
    private static void slice(JsonObject j,String key,int page,int size){var source=map(j,key);j.addProperty(key+"Count",source.size());var limited=new JsonObject();source.entrySet().stream().skip((long)page*size).limit(size).forEach(e->limited.add(e.getKey(),e.getValue()));j.add(key,limited);}
    /** Read-only API entry point; avoids menu preferences, actions, notifications and relation enrichment. */
    public JsonObject apiPositionPolicies(Actor actor,JsonObject trustedCandidates)throws Exception{return database.communityTransaction(()->new CommunityMapPositions(database,this).request(actor,trustedCandidates));}
    public JsonObject apiRead(Actor actor,String section,String id,String cursor)throws Exception{
        if(!Set.of("groups","events","board","polls","ideas").contains(section))throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,"Unknown domain");
        modules.require(section);require(enabled(section));
        return database.communityTransaction(()->{
            var out=new JsonObject();
            if(!id.isEmpty()){
                JsonObject value;
                try(var q=connection().prepareStatement("SELECT body FROM documents WHERE id=? AND section=? AND body->>'status' NOT IN ('hidden','deleted')")){
                    q.setString(1,id);q.setString(2,section);try(var rows=q.executeQuery()){if(!rows.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,"Unavailable");value=Json.parse(rows.getString(1));}
                }
                new CommunityPlus(database,this).requireVisible(actor,value);out.add("detail",value);
            }else{
                var input=new JsonObject();input.addProperty("cursor",cursor);
                var entries=new CommunityQueries(database).list(actor,input,section,System.currentTimeMillis(),config);String next="";
                for(var entry:entries){var row=entry.getAsJsonObject();if(row.has("_nextCursor")){next=Json.str(row,"_nextCursor");row.remove("_nextCursor");}}
                out.add("entries",entries);out.addProperty("nextCursor",next);
            }
            return out;
        });
    }
    public JsonObject request(Actor a,JsonObject input)throws Exception{modules.communityRequest(input);return database.communityTransaction(()->{
        String op=text(input,"op",30),section=text(input,"section",30);require(enabled(section));
        if(op.equals("noticeOpen")||op.equals("noticeNavigate")) {
            require(section.equals("notifications"));
            var notice=notifications.owned(a.id,Long.parseLong(value(input,"id")));
            notice.addProperty("canNavigate",NotificationNavigation.available(notice));
            JsonObject route=null;
            if(op.equals("noticeNavigate"))route=NotificationNavigation.resolve(this,a,notice,input);
            else {notifications.read(a.id,value(input,"id"),true);notice.addProperty("read",true);}
            var list=new JsonObject();list.addProperty("section","notifications");list.addProperty("op","list");list.addProperty("request",value(input,"request"));
            var out=execute(a,list,"list","notifications");
            if(route!=null)out.add("noticeRoute",route);else out.add("notice",notice);
            return out;
        }
        if(op.equals("mapObjects"))return new CommunityMapObjects(database,this).request(a,input);
        if(op.startsWith("mapPosition"))return new CommunityMapPositions(database,this).request(a,input);
        if(op.startsWith("mapExchange"))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.explored_map_sharing_has_been_removed_9cefb8da"));
        if(op.equals("groupMap"))return new CommunityGroupMap(database,this).read(a,input);
        if(op.equals("mapActivities"))return new CommunityMapActivities(database,this).request(a,input);
        if(op.startsWith("space"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_action_ea069893"));
        // Reads always recheck current access; caller-supplied operation IDs cannot cache private responses.
        if(MenuCommands.communityRead(op))return execute(a,input,op,section);
        if(section.equals("groups")&&!Set.of("list","detail").contains(op))database.lock("community:groups");
        if(op.equals("create"))database.lock("community:create:"+section);
        var receipt=new RequestJournal(database).execute(a.id,receiptInput(a,input),()->{JsonObject result=execute(a,input,op,section);if(!Set.of("hud","list","detail","read","plusProfile","plusIgnores","plusItemRead","workList","workGet","workMembers","workArchivePreview","globalSearch","toolsPrivacy","toolsPlaces","toolsFollowing","toolsMapSettings","toolsMapPeers","mapActivities").contains(op))audit(a.name,"community "+op,section+" "+value(input,"id"));return result;});
        if(receipt.has("replayed")&&(op.equals("noticeAction")||op.startsWith("plus")||op.startsWith("work")||op.startsWith("tools")||op.startsWith("mapObject")))return receipt;
        if(receipt.has("replayed")){var read=input.deepCopy();read.addProperty("op",receipt.has("detail")?"detail":"list");if(receipt.has("detail"))read.addProperty("id",Json.str(receipt.getAsJsonObject("detail"),"id"));var current=execute(a,read,Json.str(read,"op"),section);current.addProperty("replayed",true);return current;}return receipt;
    });}
    private JsonObject receiptInput(Actor a,JsonObject input){var copy=input.deepCopy();copy.addProperty("permissions",a.admin+":"+a.events);return copy;}
    JsonObject execute(Actor a,JsonObject in,String op,String section)throws Exception{
        String previous=notificationEvent.get();notificationEvent.set(op);try{var reply=services.request(this,a,in,op,section);reply.addProperty("trashDays",trashDays);if(reply.has("detail")){var detail=reply.getAsJsonObject("detail");if(Json.opt(detail,"status","").equals("deleted")&&detail.has("deletedAt"))detail.addProperty("restoreUntil",detail.get("deletedAt").getAsLong()+trashDays*86400000L);}return reply;}finally{if(previous==null)notificationEvent.remove();else notificationEvent.set(previous);}
    }
    JsonObject documentOperation(Actor a,JsonObject in,String op,String section)throws Exception{
        long now=System.currentTimeMillis();String id=value(in,"id");
        if(op.equals("noticeAction"))return NoticeActions.apply(database,this,a,in);
        if(op.equals("hud"))return new CommunityHud(database,this).snapshot(a,in,config);
        if(op.equals("globalSearch")){var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.addProperty("request",value(in,"request"));out.add("entries",new CommunitySearch(database).search(this,a,in,text(in,"query",100),now,config));return out;}
        if(op.startsWith("mapObject"))return new CommunityMapObjects(database,this).request(a,in);
        if(op.startsWith("tools"))return new CommunityPlayerTools(database,this).request(a,in);
        var plus=new CommunityPlus(database,this);if(op.startsWith("plus"))return plus.request(a,in,section);
        if(op.equals("preferences")){var p=in.has("preferencePatch")?notifications.patchPreferences(a.id,in.getAsJsonObject("preferencePatch")):in.getAsJsonObject("preferences");Json.keys(p,"muted","snoozeUntil");if(p.has("snoozeUntil")){long until=p.get("snoozeUntil").getAsLong();require(until>=0&&until<=now+7*86400000L);}for(String k:List.of("muted")){if(!p.has(k)||p.getAsJsonArray(k).size()>256)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_be7104288140"));for(var v:p.getAsJsonArray(k))require(NotificationCategories.valid(v.getAsString()));}notifications.savePreferences(a.id,p);}
        else if(op.equals("read")){notifications.read(a.id,id,in.has("exact")&&in.get("exact").getAsBoolean());}
        else if(op.equals("create")){
            require(section.equals("groups"));
            checkCreate(section,a,now);
            var j=new JsonObject();id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("section",section);j.addProperty("owner",a.id);j.addProperty("author",a.name);j.addProperty("title",text(in,"title",100));j.addProperty("description",text(in,"description",1500));j.addProperty("createdAt",now);j.addProperty("status","open");
            require(memberships(a.id)<config.get("maxMemberships").getAsInt());String type=text(in,"type",40);require(config.getAsJsonArray("groupTypes").contains(new JsonPrimitive(type)));j.addProperty("type",type);j.addProperty("recruiting",true);var members=new JsonObject();members.addProperty(a.id,"leader");j.add("members",members);j.add("applications",new JsonObject());j.add("invitations",new JsonObject());
            if(in.has("location")&&!in.get("location").isJsonNull())setLocation(j,in);EntryHistory.append(j,op,a.name,now);put(j);
        }else if(!op.equals("list")&&!op.equals("detail")){
            var j=get(id,true);var beforeChange=j.deepCopy();plus.requireVisible(a,j);require(section.equals(str(j,"section")));require(!str(j,"status").equals("hidden")||a.admin||owner(j,a));String title=str(j,"title");boolean own=owner(j,a);CommunityPolicy.check(j,a,op,now,trashDays);
            if(Set.of("respondDecision","close","complete","status","reschedule","cancel","role","application","recruiting","hide","delete","restore","location","edit","withdrawResponse","withdrawApplication","revokeInvitation").contains(op)&&(in.has("operationId")&&!in.has("revision")||in.has("revision")&&in.get("revision").getAsLong()!=(j.has("revision")?j.get("revision").getAsLong():0)))throw new CommunityFailure(CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.core.entry_has_changed_refresh_it_your_f939ea87"));
            if(op.equals("delete")){j.addProperty("previousStatus",str(j,"status"));j.addProperty("status","deleted");j.addProperty("deletedAt",now);j.addProperty("deletedBy",a.id);
                try(var q=connection().prepareStatement("DELETE FROM notices WHERE body->>'target'=?")){q.setString(1,id);q.executeUpdate();}
            }else if(op.equals("restore")){if(section.equals("groups"))for(String member:map(j,"members").keySet())if(memberships(member)>=config.get("maxMemberships").getAsInt())throw new CommunityFailure(CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.core.member_has_reached_the_group_limit_b171409c"));j.addProperty("status",str(j,"previousStatus"));j.remove("previousStatus");j.remove("deletedAt");j.remove("deletedBy");
            }else if(op.equals("edit")){
                j.addProperty("title",text(in,"title",100));j.addProperty("description",text(in,"description",1500));
                if(section.equals("groups")){String type=text(in,"type",40);require(config.getAsJsonArray("groupTypes").contains(new JsonPrimitive(type)));j.addProperty("type",type);
                }
                if(in.has("location")||in.has("clearLocation"))setLocation(j,in);
            }else if(op.equals("location")){setLocation(j,in);
            }else if(op.equals("hide")){require(a.admin);String reason=text(in,"text",500);j.addProperty("status","hidden");j.addProperty("moderationReason",reason);note(str(j,"owner"),section,id,LocalizedText.parts(LocalizedText.key("rivet.core.hidden_df459262"),LocalizedText.literal(title),LocalizedText.literal(" · "),LocalizedText.literal(reason)));}
            else if(section.equals("groups")){
                var members=map(j,"members");String target=value(in,"target");
                switch(op){
                    case "apply" -> {require(j.get("recruiting").getAsBoolean()&&!members.has(a.id)&&str(j,"status").equals("open"));boundedMap(j,"applications",a.id);require(!contains(j,"applications",a.id));var application=new JsonObject();application.addProperty("name",a.name);application.addProperty("text",text(in,"text",500));map(j,"applications").add(a.id,application);note(str(j,"owner"),section,id,LocalizedText.parts(LocalizedText.key("rivet.core.application_to_89897f74"),LocalizedText.literal(title)));}
                    case "withdrawApplication" -> {require(contains(j,"applications",a.id));map(j,"applications").remove(a.id);note(str(j,"owner"),section,id,LocalizedText.parts(LocalizedText.literal(a.name),LocalizedText.key("rivet.core.withdrew_an_application_4f934985"),LocalizedText.literal(title)));}
                    case "revokeInvitation" -> {require(manager(j,a)&&contains(j,"invitations",target));map(j,"invitations").remove(target);note(target,section,id,LocalizedText.parts(LocalizedText.key("rivet.core.invitation_cancelled_a182b06b"),LocalizedText.literal(title)));}
                    case "application" -> {require(manager(j,a)&&contains(j,"applications",target));if(in.get("accept").getAsBoolean()){addMember(j,target);map(j,"invitations").remove(target);}map(j,"applications").remove(target);note(target,section,id,LocalizedText.parts(LocalizedText.key(in.get("accept").getAsBoolean()?"rivet.flow.applicationAccepted":"rivet.flow.applicationDeclined"),LocalizedText.literal(title)));}
                    case "invite" -> {require(manager(j,a)&&!members.has(target));UUID.fromString(target);plus.allowContact(target,a.id);boundedMap(j,"invitations",target);require(!contains(j,"invitations",target));map(j,"invitations").addProperty(target,true);note(target,section,id,LocalizedText.parts(LocalizedText.key("rivet.core.invitation_a5d79c85"),LocalizedText.literal(title)));}
                    case "invitation" -> {require(contains(j,"invitations",a.id));if(in.get("accept").getAsBoolean()){addMember(j,a.id);map(j,"applications").remove(a.id);}map(j,"invitations").remove(a.id);}
                    case "leave" -> {require(members.has(a.id)&&!own);members.remove(a.id);}
                    case "role" -> {require(own&&members.has(target)&&!target.equals(a.id));String role=text(in,"role",20);require(Set.of("member","assistant","leader").contains(role));members.addProperty(target,role);if(role.equals("leader")){members.addProperty(a.id,"member");j.addProperty("owner",target);}note(target,section,id,LocalizedText.parts(LocalizedText.key("rivet.core.role_changed_015139cc"),LocalizedText.literal(title)));}
                    case "recruiting" -> {require(manager(j,a));j.addProperty("recruiting",!j.get("recruiting").getAsBoolean());}
                    default -> throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_action_ea069893"));
                }
            }else throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_action_ea069893"));
            EntryHistory.append(j,op,a.name,now);if(j.has("history")&&!j.getAsJsonArray("history").isEmpty()&&Set.of("edit","close","complete","status","reschedule","cancel","recruiting","delete","restore").contains(op)){var h=j.getAsJsonArray("history").get(j.getAsJsonArray("history").size()-1).getAsJsonObject();h.add("changes",ChangeSummary.betweenLocalized(beforeChange,j,Map.of("title",LocalizedText.key("rivet.map.name"),"description",LocalizedText.key("rivet.ui.description_f5441f6a"),"status",LocalizedText.key("rivet.ui.status_f7f293b5"),"startsAt",LocalizedText.key("rivet.core.start_cb26bdc6"),"recruiting",LocalizedText.key("rivet.core.modpack_eef72181"),"type",LocalizedText.key("rivet.ui.type_d25691ca"),"location",LocalizedText.key("rivet.ui.location_8e2fe025"))));}put(j);
        }
        var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section",section);out.addProperty("request",value(in,"request"));out.add("config",config.deepCopy());out.addProperty("trashDays",trashDays);out.addProperty("unread",unread(a.id));out.add("preferences",preferences(a.id));if(section.equals("home")&&modules.enabled("server")){var pinned=record("state","pinned-announcement");if(pinned!=null&&pinned.get("until").getAsLong()>now&&!Json.opt(pinned,"text","").isBlank())out.add("pinnedAnnouncement",pinned);}var ownGroups=new JsonArray();for(var group:memberGroups(a.id)){var option=new JsonObject();option.addProperty("value",str(group,"id"));option.addProperty("label",str(group,"title"));ownGroups.add(option);}if(section.equals("home"))plus.home(a,out,ownGroups);out.add("groups",ownGroups);out.addProperty("canCreate",a.admin||section.equals("groups"));
        if(!id.isEmpty()&&!section.equals("notifications")){var j=get(id);plus.requireVisible(a,j);require(section.equals(str(j,"section")));require(!Set.of("hidden","deleted").contains(str(j,"status"))||owner(j,a)||a.admin);int detailPage=0;String token=value(in,"cursor"),scope=a.id+":"+id+":"+section;long revision=j.get("revision").getAsLong();if(op.equals("detail")&&!token.isEmpty()){var cursor=CommunityCursor.decode(token,scope);if(cursor.time()!=revision)throw new CommunityFailure(CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.core.entry_contents_changed_refreshing_the_card_da2e09dc"));if(cursor.sequence()>60)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f1ffce8d7e58"));detailPage=(int)cursor.sequence();if(detailPage>60)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f1ffce8d7e58"));}else if(!in.has("action")&&in.has("page"))detailPage=number(in,"page",0,1000);
        var detail=publicView(j,a,detailPage);plus.enrich(a,j,detail,detailPage);out.add("detail",detail);boolean more=false;for(String field:List.of("applications","members","invitations","groupItems"))if(detail.has(field+"Count")){int size=Set.of("applications","groupItems").contains(field)?5:20;more|=(detailPage+1)*size<detail.get(field+"Count").getAsInt();}out.addProperty("nextCursor",more?new CommunityCursor(detailPage+1,revision,1000,scope).encode():"");var names=new JsonObject();if(section.equals("groups")){try(var q=connection().prepareStatement("SELECT id,name FROM people WHERE id=ANY(?)")){q.setArray(1,connection().createArrayOf("text",groupPeople(detail).toArray()));try(var r=q.executeQuery()){while(r.next())names.addProperty(r.getString(1),r.getString(2));}}}out.add("names",names);if(section.equals("groups"))out.add("related",new JsonArray());return out;}
        int page=in.has("page")?number(in,"page",0,1000):0;out.addProperty("page",page);var entries=new JsonArray();
        if(section.equals("notifications")){entries=new CommunityQueries(database).notices(a,in);out.add("notificationCategories",notifications.categories(a.id));}
        else entries=listPage(a,in,section,page,now);
        String next="";for(var entry:entries){var row=entry.getAsJsonObject();if(row.has("_nextCursor")){next=Json.str(row,"_nextCursor");row.remove("_nextCursor");}}out.addProperty("nextCursor",next);out.add("entries",entries);return out;
    }
    private static Set<String> groupPeople(JsonObject detail){var ids=new HashSet<String>();for(String key:List.of("members","applications","invitations"))ids.addAll(map(detail,key).keySet());if(detail.has("groupItems"))for(var item:detail.getAsJsonArray("groupItems")){String assignee=Json.opt(item.getAsJsonObject(),"assignee","");if(!assignee.isBlank())ids.add(assignee);}return ids;}
    boolean manager(JsonObject j,Actor a){return a.admin||owner(j,a)||contains(j,"members",a.id)&&map(j,"members").get(a.id).getAsString().equals("assistant");}
    private int memberships(String id)throws Exception{try(var q=connection().prepareStatement("SELECT count(*) FROM community_documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'members',?)")){q.setString(1,id);try(var r=q.executeQuery()){r.next();return r.getInt(1);}}}
    private void addMember(JsonObject j,String id)throws Exception{require(memberships(id)<config.get("maxMemberships").getAsInt());boundedMap(j,"members",id);map(j,"members").addProperty(id,"member");}
    public JsonArray pendingChanges()throws Exception{return database.communityTransaction(()->{notifications.expire();return ChangeOutbox.pending(database);});}
    public void acknowledgeChanges(JsonArray events)throws Exception{if(events.isEmpty())return;database.communityTransaction(()->{ChangeOutbox.acknowledge(database,events);return null;});}
    private void setLocation(JsonObject document,JsonObject input){
        if(str(document,"section").equals("groups"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.groups_use_territories_instead_of_locations_40d44870"));
        if(!Set.of("board","events","polls","ideas").contains(str(document,"section")))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.location_unavailable_in_this_section_1f2c505e"));
        if(input.has("clearLocation")&&input.get("clearLocation").getAsBoolean()||!input.has("location")||input.get("location").isJsonNull()){document.remove("location");return;}
        var location=CommunityLocation.read(input.getAsJsonObject("location"));
        if(location.membersOnly()&&!str(document,"section").equals("groups")&&value(document,"group").isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.private_locations_are_only_available_to_b701e540"));
        document.add("location",location.json());
    }
    private JsonArray retentionSections(){var result=new JsonArray();for(var item:config.getAsJsonArray("sections")){String section=item.getAsString();if(section.equals("groups")&&(!modules.enabled("tasks")||!modules.enabled("storage")))continue;result.add(item);}return result;}
    public void purgeDeleted(long now)throws Exception{database.communityTransaction(()->{
        database.lock("community:groups");
        String enabledSections=retentionSections().toString();
        try(var q=connection().prepareStatement("DELETE FROM notices WHERE body->>'target' IN (SELECT id FROM documents WHERE body->>'section' IN (SELECT jsonb_array_elements_text(?::jsonb)) AND body->>'status'='deleted' AND (body->>'deletedAt')::bigint<=?)")){q.setString(1,enabledSections);q.setLong(2,now-trashDays*86400000L);q.executeUpdate();}
        try(var q=connection().prepareStatement("DELETE FROM documents WHERE body->>'section' IN (SELECT jsonb_array_elements_text(?::jsonb)) AND body->>'status'='deleted' AND (body->>'deletedAt')::bigint<=?")){q.setString(1,enabledSections);q.setLong(2,now-trashDays*86400000L);q.executeUpdate();}return null;
    });}
    public void reminders(long now)throws Exception{database.communityTransaction(()->{
        database.lock("community:reminders");if(lastReminderSweep!=0&&now-lastReminderSweep<30000)return null;
        try{new CommunityPlayerTools(database,this).expire(now);lastReminderSweep=now;}catch(Exception ex){lastReminderSweep=0;throw ex;}return null;
    });}
    public <T>T inTransaction(PgDatabase.Work<T> work)throws Exception{return database.communityTransaction(work);}
    public interface Transaction {void run()throws Exception;}
    public void transaction(Transaction work)throws Exception{database.communityTransaction(()->{work.run();return null;});}
    public JsonObject record(String namespace,String id)throws Exception{return database.communityTransaction(()->{try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? AND id=? FOR UPDATE")){q.setString(1,namespace);q.setString(2,id);try(var r=q.executeQuery()){return r.next()?JsonParser.parseString(r.getString(1)).getAsJsonObject():null;}}});}
    public void record(String namespace,String id,JsonObject body)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES(?,?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,namespace);q.setString(2,id);q.setString(3,Json.GSON.toJson(body));q.executeUpdate();if(namespace.equals("map-position")||namespace.equals("map-object")||namespace.equals("profile-privacy")||namespace.equals("reports")||namespace.equals("api-chat")||namespace.equals("scheduled-announcements")||namespace.equals("state")&&Set.of("maintenance","restart","pinned-announcement").contains(id))database.communityChanged();}return null;});}
    public List<JsonObject> records(String namespace)throws Exception{return database.communityTransaction(()->{var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement("SELECT body FROM records WHERE namespace=? ORDER BY sequence DESC")){q.setString(1,namespace);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;});}
    public void deleteRecord(String namespace,String id)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace=? AND id=?")){q.setString(1,namespace);q.setString(2,id);q.executeUpdate();if(namespace.equals("map-position")||namespace.equals("map-object")||namespace.equals("profile-privacy")||namespace.equals("reports")||namespace.equals("api-chat")||namespace.equals("scheduled-announcements")||namespace.equals("state")&&Set.of("maintenance","restart","pinned-announcement").contains(id))database.communityChanged();}return null;});}
    public void audit(String actor,String action,String detail)throws Exception{database.communityTransaction(()->{var j=new JsonObject();String id=UUID.randomUUID().toString();j.addProperty("id",id);j.addProperty("at",System.currentTimeMillis());j.addProperty("actor",actor);j.addProperty("action",action);j.addProperty("detail",detail.substring(0,Math.min(1000,detail.length())));record("audit",id,j);try(var q=connection().createStatement()){q.executeUpdate("DELETE FROM records WHERE namespace='audit' AND sequence NOT IN (SELECT sequence FROM records WHERE namespace='audit' ORDER BY sequence DESC LIMIT "+auditEntries+")");}return null;});}

    private void checkCreate(String section,Actor actor,long now)throws Exception{
        try(var q=connection().prepareStatement("SELECT count(*),count(*) FILTER (WHERE body->>'owner'=? AND (body->>'createdAt')::bigint>?) FROM documents WHERE section=?")){
            q.setString(1,actor.id);q.setLong(2,now-60000);q.setString(3,section);
            try(var r=q.executeQuery()){r.next();if(r.getInt(1)>=2000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.section_is_full_c52c5998"));if(!actor.admin&&r.getInt(2)>0)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_one_minute_before_posting_again_ee8e835c"));}
        }
    }
    List<JsonObject> memberGroups(String id)throws Exception{
        if(!modules.enabled("groups"))return List.of();
        return bodies("SELECT body FROM community_documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'members',?) ORDER BY sequence DESC",id);
    }
    private List<JsonObject> bodies(String sql,Object...args)throws Exception{
        var out=new ArrayList<JsonObject>();try(var q=connection().prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try(var r=q.executeQuery()){while(r.next())out.add(JsonParser.parseString(r.getString(1)).getAsJsonObject());}}return out;
    }
    private JsonArray listPage(Actor a,JsonObject in,String section,int page,long now)throws Exception {
        return new CommunityQueries(database).list(a,in,section,now,configuration());
    }
    public record NoticeSummary(int unread,long sequence){}
    public Map<UUID,NoticeSummary> notificationSummary(Collection<UUID> players)throws Exception{
        var result=new HashMap<UUID,NoticeSummary>();
        notifications.summary(players).forEach((id,value)->result.put(id,new NoticeSummary(value.unread(),value.sequence())));
        return result;
    }
    public List<JsonObject> recordPage(String namespace,String owner,int page,int size)throws Exception{return database.communityTransaction(()->{
        if(page<0||page>1000||size<1||size>20)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_e83e2de9b3f0"));return bodies("SELECT body FROM records WHERE namespace=? AND (?='' OR body->>'uuid'=?) ORDER BY sequence DESC LIMIT ? OFFSET ?",namespace,owner,owner,size,page*size);
    });}
    public JsonObject recordCursor(String namespace,String owner,String token,int size)throws Exception{return database.communityTransaction(()->{
        String scope="records:"+namespace+":"+owner;var cursor=token.isEmpty()?null:CommunityCursor.decode(token,scope);long ceiling=cursor==null?Long.MAX_VALUE:cursor.ceiling();var result=new JsonObject();var entries=new JsonArray();long last=0;boolean more=false;
        try(var q=connection().prepareStatement("SELECT sequence,body FROM records WHERE namespace=? AND (?='' OR body->>'uuid'=?) AND sequence<=? AND sequence<? ORDER BY sequence DESC LIMIT ?")){q.setString(1,namespace);q.setString(2,owner);q.setString(3,owner);q.setLong(4,ceiling);q.setLong(5,cursor==null?Long.MAX_VALUE:cursor.sequence());q.setInt(6,size+1);try(var rs=q.executeQuery()){while(rs.next()){if(entries.size()==size){more=true;break;}last=rs.getLong(1);if(entries.isEmpty()&&cursor==null)ceiling=last;entries.add(Json.parse(rs.getString(2)));}}}
        result.add("entries",entries);result.addProperty("nextCursor",more?new CommunityCursor(last,0,ceiling,scope).encode():"");return result;
    });}
    public JsonObject receipt(String actor,JsonObject input,PgDatabase.Work<JsonObject> work)throws Exception{return database.communityTransaction(()->new RequestJournal(database).execute(actor,input,work));}
    public void checkReport(String owner,long now)throws Exception{database.lock("report-create");try(var q=connection().prepareStatement("SELECT count(*),count(*) FILTER (WHERE body->>'uuid'=? AND (body->>'createdAt')::bigint>?) FROM records WHERE namespace='reports'")){q.setString(1,owner);q.setLong(2,now-60000);try(var r=q.executeQuery()){r.next();if(r.getInt(1)>=5000)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.report_storage_is_full_aa109cf6"));if(r.getInt(2)>0)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_one_minute_before_submitting_another_46faf42d"));}}}
    public void pruneReports(long now)throws Exception{database.communityTransaction(()->{try(var q=connection().prepareStatement("DELETE FROM records WHERE namespace='reports' AND body->>'status'='resolved' AND coalesce((body->>'repliedAt')::bigint,(body->>'createdAt')::bigint)<?")){q.setLong(1,now-reportDays*86400000L);q.executeUpdate();}return null;});}

    @Override public void close()throws Exception{}
}
