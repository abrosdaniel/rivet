package dev.abros.rivet.api;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.abros.rivet.api.DomainData.*;

/** Player-scoped or public reads. Futures finish on API workers; use async callbacks for other work. */
public interface DomainApi {
 default CompletableFuture<MapLayersApi.Page<MapLayersApi.LayerView>> mapLayers(String cursor,OptionalLong snapshot){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Map layers unavailable"));}
 default CompletableFuture<MapLayersApi.Page<MapLayersApi.ObjectView>> mapLayerObjects(String layer,String cursor,OptionalLong snapshot){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Map layers unavailable"));}

 default CompletableFuture<Page<OnlinePlayer>> onlinePlayers(String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Presence unavailable"));}
 default CompletableFuture<Page<PlayerPosition>> positions(String dimension,String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Positions unavailable"));}
 default CompletableFuture<Page<Waystone>> waystones(String dimension,String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Waystones unavailable"));}

 default CompletableFuture<GroupPage<GroupMarker>> groupMarkers(UUID group,String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Resource unavailable"));}
 default CompletableFuture<GroupPage<GroupPerson>> groupPeople(UUID group,GroupPeople kind,String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Resource unavailable"));}
 default CompletableFuture<EventSchedule> eventSchedule(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Resource unavailable"));}
 default CompletableFuture<Page<ScheduledAnnouncement>> scheduledAnnouncements(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Resource unavailable"));}

 default CompletableFuture<ServerControl> serverControl(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Server control unavailable"));}
 default CompletableFuture<TaskDetails> taskDetails(UUID id,int subtaskOffset,int commentOffset,OptionalLong revision){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Task details unavailable"));}

 /** Starts with a fresh snapshot. Close the returned handle when no longer needed. */
 default <T> DomainSubscription<T> watch(DomainWatch<T> target){
  throw new ApiException(ApiException.Code.MODULE_DISABLED,"Java subscriptions unavailable");
 }
 default CompletableFuture<Publications> moderationPublications(String cursor,OptionalLong snapshot){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Moderation unavailable"));}
 default CompletableFuture<Pack> pack(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Pack unavailable"));}
 default CompletableFuture<Page<StaffReport>> staffReports(String filter,String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Reports unavailable"));}
 default CompletableFuture<StaffReport> staffReport(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Reports unavailable"));}
 default CompletableFuture<Page<Report>> reports(String cursor){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Reports unavailable"));}
 default CompletableFuture<Report> report(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Reports unavailable"));}
 default CompletableFuture<Statistics> statistics(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Statistics unavailable"));}
 default CompletableFuture<Skin> skin(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Skins unavailable"));}
 CompletableFuture<Set<String>> capabilities();
 CompletableFuture<Page<Document>> community(Community domain,String cursor);
 CompletableFuture<Document> document(Community domain,UUID id);
 /** Empty group selects personal tasks. */
 CompletableFuture<Page<Task>> tasks(Optional<UUID> group,String cursor);
 CompletableFuture<Task> task(UUID id);
 CompletableFuture<Profile> profile(UUID id);
 default CompletableFuture<NotificationPreferences> notificationPreferences(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Preferences unavailable"));}
 CompletableFuture<Page<Notice>> notifications(String cursor);
 CompletableFuture<Page<ChatMessage>> chat(String cursor);
 CompletableFuture<Page<Channel>> channels(String cursor);
 /** First page: empty cursor and empty snapshot. Later pages must retain the returned snapshot. */
 CompletableFuture<Publications> publications(boolean own,String cursor,OptionalLong snapshot);
 CompletableFuture<Publication> publication(UUID id,boolean own);
 CompletableFuture<Page<GroupTerritory>> territories(String cursor);
 /** Retry the same operationId/issuedAt/command after ambiguous timeout; never invent a second ID. */
 CompletableFuture<Receipt> execute(UUID operationId,long issuedAtEpochMillis,DomainCommand command);
}
