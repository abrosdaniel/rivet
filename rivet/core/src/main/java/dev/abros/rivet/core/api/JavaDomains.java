package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import static dev.abros.rivet.api.DomainData.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** One bounded executor per API incarnation; no world access, transport credentials or public JSON. */
public final class JavaDomains implements AutoCloseable {
 private volatile MapLayerRegistry layers;
 public JavaDomains layers(MapLayerRegistry value){layers=Objects.requireNonNull(value);resources.layers(value);return this;}

 private final CommunityStore store;private final CommunityApiResources resources;private final CommunityApiCommands commands;
 private final ThreadPoolExecutor workers;private final JavaWatches watches;
 private static ThreadPoolExecutor executor(){return new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Rivet Java API");t.setDaemon(true);return t;});}
 private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();private volatile boolean closed;
 public JavaDomains(PgDatabase db,CommunityStore store,ServerSettings settings){this(db,store,settings,executor());}
 JavaDomains(PgDatabase db,CommunityStore store,ServerSettings settings,ThreadPoolExecutor workers){this.workers=workers;this.watches=new JavaWatches(()->db.communityRevision()+(layers==null?0:layers.revision()));this.store=store;resources=new CommunityApiResources(db,store,settings);commands=new CommunityApiCommands(db,store,settings);workers.allowCoreThreadTimeOut(true);}
 public JavaDomains live(LiveMapAccess value){resources.live(value);return this;}
 public JavaDomains local(ApiChat.LocalAudience value){commands.local(value);return this;}
 public JavaDomains pack(PgDatabase.Work<dev.abros.rivet.core.pack.PackManifest> value){resources.pack(value);return this;}
 public JavaDomains access(DomainAccess value){resources.access(value);commands.access(value);return this;}
 public DomainApi connect(String modId,UUID player){
  if(closed)throw new ApiException(UNAVAILABLE,"API stopped");
  if(modId==null||!modId.matches("[a-z][a-z0-9_]{1,63}"))throw new ApiException(INVALID_ARGUMENT,"Invalid consumer");
  return new View(modId,player);
 }
 private <T> CompletableFuture<T> submit(Callable<T> work){
  var future=new CompletableFuture<T>();if(closed){future.completeExceptionally(new ApiException(UNAVAILABLE,"API stopped"));return future;}
  pending.add(future);future.whenComplete((v,e)->pending.remove(future));future.orTimeout(10,TimeUnit.SECONDS);
  try{workers.execute(()->{if(future.isDone())return;try{if(closed)throw new ApiException(UNAVAILABLE,"API stopped");var result=work.call();if(closed)throw new ApiException(UNAVAILABLE,"API stopped");future.complete(result);}catch(Exception e){future.completeExceptionally(failure(e));}});}
  catch(RejectedExecutionException busy){future.completeExceptionally(new ApiException(closed?UNAVAILABLE:BUSY,"API worker capacity unavailable"));}
  return future;
 }
 private static ApiException failure(Exception e){
  if(e instanceof ApiException api)return api;
  if(e instanceof CommunityFailure f)return new ApiException(switch(f.code()){case NOT_FOUND->NOT_FOUND;case FORBIDDEN->FORBIDDEN;case CONFLICT->CONFLICT;case UNAVAILABLE->UNAVAILABLE;default->INVALID_ARGUMENT;},"Domain operation rejected");
  if(e instanceof IllegalArgumentException||e instanceof NullPointerException)return new ApiException(INVALID_ARGUMENT,"Invalid domain request");
  return new ApiException(UNAVAILABLE,"Domain operation unavailable");
 }
 private final class View implements DomainApi {
  private final String mod;private final UUID player;
  View(String mod,UUID player){this.mod=mod;this.player=player;}
  public <T> DomainSubscription<T> watch(DomainWatch<T> target){if(target==null)throw new ApiException(INVALID_ARGUMENT,"Subscription target required");return watches.add(mod,()->target.read(this));}
  public CompletableFuture<GroupPage<GroupMarker>> groupMarkers(UUID group,String cursor){return submit(()->{var j=read("groups/"+group+"/markers",query(cursor));var p=page(j,"items",v->new GroupMarker(UUID.fromString(s(v,"id")),n(v,"revision"),s(v,"title"),s(v,"description"),Json.GSON.fromJson(v.get("location"),Location.class)));return new GroupPage<>(p.items(),p.nextCursor(),n(j,"revision"));});}
  public CompletableFuture<GroupPage<GroupPerson>> groupPeople(UUID group,GroupPeople kind,String cursor){return submit(()->{var j=read("groups/"+group+"/"+kind.name().toLowerCase(Locale.ROOT),query(cursor));var p=page(j,"items",v->new GroupPerson(UUID.fromString(s(v,"id")),s(v,"name"),s(v,"role"),s(v,"text")));return new GroupPage<>(p.items(),p.nextCursor(),n(j,"revision"));});}
  public CompletableFuture<EventSchedule> eventSchedule(UUID id){return submit(()->{var j=read("events/"+id+"/schedule",Map.of()).getAsJsonObject("item");var invites=new ArrayList<UUID>();if(j.has("invitees"))j.getAsJsonArray("invitees").forEach(e->invites.add(UUID.fromString(e.getAsString())));var occurrences=new ArrayList<EventOccurrence>();for(var e:j.getAsJsonArray("occurrences")){var v=e.getAsJsonObject();occurrences.add(new EventOccurrence(UUID.fromString(s(v,"id")),n(v,"revision"),n(v,"startsAt"),s(v,"status")));}return new EventSchedule(id,n(j,"revision"),s(j,"visibility"),s(j,"group"),n(j,"startsAt"),(int)n(j,"durationMinutes"),s(j,"repeat"),s(j,"series"),invites,occurrences);});}
  public CompletableFuture<Page<ScheduledAnnouncement>> scheduledAnnouncements(){return submit(()->page(read("admin/server/announcements",Map.of()),"items",v->new ScheduledAnnouncement(UUID.fromString(s(v,"id")),n(v,"revision"),s(v,"text"),n(v,"at"),s(v,"author"),s(v,"status"))));}
  public CompletableFuture<Page<OnlinePlayer>> onlinePlayers(String cursor){return submit(()->page(read("players/online",query(cursor)),"items",j->new OnlinePlayer(UUID.fromString(s(j,"uuid")),s(j,"name"))));}
  public CompletableFuture<Page<PlayerPosition>> positions(String dimension,String cursor){return submit(()->{var q=new HashMap<>(query(cursor));if(!dimension.isEmpty())q.put("dimension",dimension);return page(read("map/positions",q),"items",j->new PlayerPosition(UUID.fromString(s(j,"uuid")),s(j,"name"),s(j,"dimension"),(int)n(j,"x"),(int)n(j,"y"),(int)n(j,"z")));});}
  public CompletableFuture<Page<Waystone>> waystones(String dimension,String cursor){return submit(()->{var q=new HashMap<>(query(cursor));if(!dimension.isEmpty())q.put("dimension",dimension);return page(read("map/waystones",q),"items",j->new Waystone(UUID.fromString(s(j,"uuid")),s(j,"name"),s(j,"dimension"),(int)n(j,"x"),(int)n(j,"y"),(int)n(j,"z")));});}
  public CompletableFuture<MapLayersApi.Page<MapLayersApi.LayerView>> mapLayers(String cursor,OptionalLong snapshot){return submit(()->{actor(false);if(layers==null)throw new ApiException(MODULE_DISABLED,"Map layers unavailable");return layers.layers(player,cursor,snapshot);});}
  public CompletableFuture<MapLayersApi.Page<MapLayersApi.ObjectView>> mapLayerObjects(String layer,String cursor,OptionalLong snapshot){return submit(()->{actor(false);if(layers==null)throw new ApiException(MODULE_DISABLED,"Map layers unavailable");return layers.objects(player,layer,cursor,snapshot);});}
  public CompletableFuture<Set<String>> capabilities(){return submit(()->{var result=new HashSet<String>();for(String c:resources.capabilities())result.add(c.substring(5));for(var d:Community.values()){String name=d.name().toLowerCase(Locale.ROOT);if(store.modules().enabled(name)&&store.configuration().getAsJsonArray("sections").contains(new JsonPrimitive(name)))result.add(name+".read");}if(player!=null)for(String c:new HashSet<>(result))if(c.endsWith(".read")&&!Set.of("players.presence.read","map.positions.read","map.waystones.read","statistics.read","skins.read","pack.read").contains(c))result.add(c.replace(".read",".write"));for(String c:new HashSet<>(result))if(c.endsWith(".read"))result.add(c.replace(".read",".watch"));return Set.copyOf(result);});}
  private CommunityStore.Actor actor(boolean required)throws Exception{
   if(player==null){if(required)throw new ApiException(UNAUTHENTICATED,"Player context required");return new CommunityStore.Actor("__api_public__","",false,false);}
   return new CommunityStore.Actor(player.toString(),store.personName(player.toString()),false,false);
  }
  private JsonObject read(String path,Map<String,String> query)throws Exception{return resources.read(actor(!path.startsWith("map/")&&!path.equals("pack")),path,query);}
  public CompletableFuture<ServerControl> serverControl(){return submit(()->{var j=read("admin/server",Map.of()).getAsJsonObject("item");var m=j.get("maintenance").isJsonObject()?j.getAsJsonObject("maintenance"):new JsonObject();var r=j.get("restart").isJsonObject()?j.getAsJsonObject("restart"):new JsonObject();var p=j.get("pinned-announcement").isJsonObject()?j.getAsJsonObject("pinned-announcement"):new JsonObject();return new ServerControl(s(j,"revision"),b(m,"maintenance"),n(m,"until"),s(m,"reason"),n(r,"at"),s(r,"reason"),s(p,"text"),n(p,"until"));});}
  public CompletableFuture<TaskDetails> taskDetails(UUID id,int subtaskOffset,int commentOffset,OptionalLong revision){return submit(()->{var q=new HashMap<String,String>();q.put("subtaskOffset",Integer.toString(subtaskOffset));q.put("commentOffset",Integer.toString(commentOffset));if(revision.isPresent())q.put("revision",Long.toString(revision.getAsLong()));var j=read("tasks/"+id+"/details",q).getAsJsonObject("item");var subtasks=new ArrayList<Subtask>();for(var e:j.getAsJsonArray("subtasks")){var row=e.getAsJsonObject();subtasks.add(new Subtask(s(row,"title"),b(row,"done"),s(row,"assignee")));}var comments=new ArrayList<TaskComment>();for(var e:j.getAsJsonArray("comments")){var row=e.getAsJsonObject();comments.add(new TaskComment(s(row,"author"),s(row,"text"),n(row,"at")));}var resources=new ArrayList<Resource>();for(var e:j.getAsJsonArray("resources")){var row=e.getAsJsonObject();var alternatives=new ArrayList<String>();if(row.has("alternatives"))row.getAsJsonArray("alternatives").forEach(v->alternatives.add(v.getAsString()));resources.add(new Resource(s(row,"item"),(int)n(row,"amount"),alternatives));}return new TaskDetails(id,n(j,"revision"),s(j,"assignee"),n(j,"dueAt"),(int)n(j,"subtaskOffset"),(int)n(j,"subtaskCount"),(int)n(j,"commentOffset"),(int)n(j,"commentCount"),subtasks,comments,resources);});}
  public CompletableFuture<Page<Document>> community(Community domain,String cursor){return submit(()->{
   String name=domain.name().toLowerCase(Locale.ROOT);check(name);var source=store.apiRead(actor(false),name,"",cursor(cursor));return page(source,"entries",JavaDomains::document);
  });}
  public CompletableFuture<Document> document(Community domain,UUID id){return submit(()->{String name=domain.name().toLowerCase(Locale.ROOT);check(name);return JavaDomains.document(store.apiRead(actor(false),name,id.toString(),"").getAsJsonObject("detail"));});}
  private void check(String domain){if(!store.modules().enabled(domain)||!store.configuration().getAsJsonArray("sections").contains(new JsonPrimitive(domain)))throw new ApiException(MODULE_DISABLED,"Domain disabled");}
  public CompletableFuture<Publications> moderationPublications(String cursor,OptionalLong snapshot){return submit(()->{var q=new HashMap<>(query(cursor));snapshot.ifPresent(v->q.put("snapshot",Long.toString(v)));var j=read("admin/map/objects",q);var p=page(j,"items",JavaDomains::publication);return new Publications(p.items(),p.nextCursor(),n(j,"snapshot"));});}
  public CompletableFuture<Pack> pack(){return submit(()->{var j=read("pack",Map.of()).getAsJsonObject("item");var components=new ArrayList<PackComponent>();for(var e:j.getAsJsonArray("components")){var c=e.getAsJsonObject();components.add(new PackComponent(s(c,"id"),s(c,"name"),s(c,"description"),b(c,"selected"),b(c,"optional")));}return new Pack(s(j,"hash"),s(j,"minecraft"),s(j,"loader"),(int)n(j,"files"),components);});}
  public CompletableFuture<Page<StaffReport>> staffReports(String filter,String cursor){return submit(()->{var q=new HashMap<>(query(cursor));q.put("filter",Objects.requireNonNull(filter));return page(read("admin/reports",q),"items",JavaDomains::staffReport);});}
  public CompletableFuture<StaffReport> staffReport(UUID id){return submit(()->JavaDomains.staffReport(read("admin/reports/"+id,Map.of()).getAsJsonObject("item")));}
  public CompletableFuture<Page<Report>> reports(String cursor){return submit(()->page(read("reports",query(cursor)),"items",JavaDomains::report));}
  public CompletableFuture<Report> report(UUID id){return submit(()->JavaDomains.report(read("reports/"+id,Map.of()).getAsJsonObject("item")));}
  public CompletableFuture<Statistics> statistics(UUID id){return submit(()->{var j=read("statistics/"+id,Map.of()).getAsJsonObject("item");return new Statistics(id,optionalNumber(j,"firstJoin"),optionalNumber(j,"lastActivity"),optionalNumber(j,"totalMillis"),optionalNumber(j,"timeSince"),optionalNumber(j,"deaths"),optionalNumber(j,"deathsSince"));});}
  public CompletableFuture<Skin> skin(UUID id){return submit(()->{var j=read("skins/"+id,Map.of()).getAsJsonObject("item");return new Skin(id,s(j,"hash"),b(j,"slim"));});}
  public CompletableFuture<Page<Task>> tasks(Optional<UUID> group,String cursor){return submit(()->{var query=new HashMap<>(query(cursor));group.ifPresent(id->query.put("group",id.toString()));return page(read("tasks",query),"items",JavaDomains::task);});}
  public CompletableFuture<Task> task(UUID id){return submit(()->JavaDomains.task(read("tasks/"+id.toString(),Map.of()).getAsJsonObject("item")));}
  public CompletableFuture<Profile> profile(UUID id){return submit(()->{var j=read("profiles/"+id.toString(),Map.of()).getAsJsonObject("item");return new Profile(UUID.fromString(s(j,"uuid")),s(j,"name"),s(j,"about"),s(j,"interests"),s(j,"revision"));});}
  public CompletableFuture<NotificationPreferences> notificationPreferences(){return submit(()->{var j=read("me/notification-preferences",Map.of()).getAsJsonObject("item");var muted=new ArrayList<String>();j.getAsJsonArray("muted").forEach(e->muted.add(e.getAsString()));return new NotificationPreferences(muted,n(j,"snoozeUntil"),s(j,"revision"));});}
  public CompletableFuture<Page<Notice>> notifications(String cursor){return submit(()->page(read("me/notifications",query(cursor)),"items",j->new Notice(s(j,"id"),s(j,"title"),s(j,"body"),s(j,"section"),n(j,"at"),b(j,"read"))));}
  public CompletableFuture<Page<ChatMessage>> chat(String cursor){return submit(()->page(read("chat/history",query(cursor)),"items",j->new ChatMessage(s(j,"id"),n(j,"at"),s(j,"channel"),s(j,"text"))));}
  public CompletableFuture<Page<Channel>> channels(String cursor){return submit(()->page(read("chat/channels",query(cursor)),"items",j->new Channel(s(j,"id"),s(j,"name"))));}
  public CompletableFuture<Publications> publications(boolean own,String cursor,OptionalLong snapshot){return submit(()->{var q=new HashMap<>(query(cursor));if(snapshot.isPresent())q.put("snapshot",Long.toString(snapshot.getAsLong()));var j=read((own?"me/":"")+"map/objects",q);var p=page(j,"items",JavaDomains::publication);return new Publications(p.items(),p.nextCursor(),n(j,"snapshot"));});}
  public CompletableFuture<Publication> publication(UUID id,boolean own){return submit(()->JavaDomains.publication(read((own?"me/":"")+"map/objects/"+id.toString(),Map.of()).getAsJsonObject("item")));}
  public CompletableFuture<Page<GroupTerritory>> territories(String cursor){return submit(()->page(read("map/territories",query(cursor)),"items",j->new GroupTerritory(UUID.fromString(s(j,"id")),s(j,"title"),n(j,"revision"),(Territory)shape(j.getAsJsonObject("territory")))));}
  public CompletableFuture<Receipt> execute(UUID operationId,long issuedAt,DomainCommand command){return submit(()->{
   var a=actor(true);var request=JavaCommand.encode(command);var body=new JsonObject();body.addProperty("operationId",operationId.toString());body.addProperty("issuedAt",issuedAt);body.add("data",request.data());
   // Namespaces differ from network applications, so receipts cannot collide across transports.
   var result=commands.execute(a,"java/"+mod,request.method(),request.path(),request.match(),ApiJson.object(body.toString()));
   return new Receipt(operationId,s(result,"id"),s(result,"revision"),b(result,"replayed"));
  });}
 }
 private static String cursor(String value){if(value==null||value.length()>256)throw new ApiException(INVALID_ARGUMENT,"Invalid cursor");return value;}
 private static Map<String,String> query(String value){return cursor(value).isEmpty()?Map.of():Map.of("cursor",value);}
 private static <T> Page<T> page(JsonObject j,String field,Function<JsonObject,T> convert){var items=new ArrayList<T>();for(var item:j.getAsJsonArray(field))items.add(convert.apply(item.getAsJsonObject()));return new Page<>(items,s(j,"nextCursor"));}
 private static String s(JsonObject j,String field){var v=j.get(field);return v==null||v.isJsonNull()?"":v.isJsonPrimitive()?v.getAsString():v.toString();}
 private static long n(JsonObject j,String field){return j.has(field)&&!j.get(field).isJsonNull()?j.get(field).getAsLong():0;}
 private static boolean b(JsonObject j,String field){return j.has(field)&&!j.get(field).isJsonNull()&&j.get(field).getAsBoolean();}
 private static OptionalLong optionalNumber(JsonObject j,String field){return j.has(field)?OptionalLong.of(n(j,field)):OptionalLong.empty();}
 private static StaffReport staffReport(JsonObject j){var notes=new ArrayList<ReportNote>();if(j.has("internalNotes"))for(var e:j.getAsJsonArray("internalNotes")){var note=e.getAsJsonObject();notes.add(new ReportNote(s(note,"author"),s(note,"text"),n(note,"at")));}return new StaffReport(report(j),UUID.fromString(s(j,"uuid")),s(j,"player"),s(j,"assigned"),s(j,"assignedName"),s(j,"priority"),s(j,"nextStep"),notes);}
 private static Report report(JsonObject j){return new Report(UUID.fromString(s(j,"id")),s(j,"message"),s(j,"category"),s(j,"status"),n(j,"revision"),n(j,"createdAt"),s(j,"reply"),s(j,"replyAuthor"),n(j,"repliedAt"));}
 private static Document document(JsonObject j){return new Document(UUID.fromString(s(j,"id")),s(j,"title"),s(j,"description"),s(j,"preview"),s(j,"status"),n(j,"revision"),n(j,"createdAt"),n(j,"startsAt"),n(j,"endsAt"),s(j,"type"),b(j,"recruiting"),(int)n(j,"capacity"));}
 private static Task task(JsonObject j){String group=s(j,"group");return new Task(UUID.fromString(s(j,"id")),s(j,"code"),group.isEmpty()?Optional.empty():Optional.of(UUID.fromString(group)),s(j,"title"),s(j,"description"),s(j,"status"),n(j,"revision"),n(j,"dueAt"),n(j,"completedAt"),b(j,"archived"),(int)n(j,"stagesTotal"),(int)n(j,"stagesDone"),j.has("location")&&!j.get("location").isJsonNull()?Optional.of(location(j.getAsJsonObject("location"))):Optional.empty());}
 private static Location location(JsonObject j){return new Location(s(j,"name"),s(j,"dimension"),(int)n(j,"x"),(int)n(j,"y"),(int)n(j,"z"),b(j,"membersOnly"));}
 private static Publication publication(JsonObject j){return new Publication(UUID.fromString(s(j,"id")),s(j,"type"),n(j,"revision"),optionalShape(j,"published"),optionalShape(j,"draft"));}
 private static Optional<Shape> optionalShape(JsonObject j,String field){return j.has(field)&&j.get(field).isJsonObject()?Optional.of(shape(j.getAsJsonObject(field))):Optional.empty();}
 private static Shape shape(JsonObject j){if(j.has("points")){var points=new ArrayList<Point>();for(var p:j.getAsJsonArray("points"))points.add(new Point((int)n(p.getAsJsonObject(),"x"),(int)n(p.getAsJsonObject(),"z")));return new Territory(s(j,"dimension"),s(j,"name"),(int)n(j,"color"),points);}return new Marker(s(j,"dimension"),s(j,"name"),(int)n(j,"x"),(int)n(j,"y"),(int)n(j,"z"),(int)n(j,"color"),s(j,"icon"));}
 @Override public void close(){closed=true;watches.close();for(var future:pending)future.completeExceptionally(new ApiException(UNAVAILABLE,"API stopped"));workers.shutdownNow();WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),workers);}
}
