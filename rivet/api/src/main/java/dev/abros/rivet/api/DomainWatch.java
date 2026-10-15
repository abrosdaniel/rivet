package dev.abros.rivet.api;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import static dev.abros.rivet.api.DomainData.*;

/** Closed vocabulary of live reads. Lists watch their first page; use normal reads for pagination. */
public sealed interface DomainWatch<T> {
 CompletableFuture<T> read(DomainApi api);
 record MapLayers() implements DomainWatch<MapLayersApi.Page<MapLayersApi.LayerView>>{public CompletableFuture<MapLayersApi.Page<MapLayersApi.LayerView>> read(DomainApi api){return api.mapLayers("",OptionalLong.empty());}}
 record MapLayerObjects(String layer) implements DomainWatch<MapLayersApi.Page<MapLayersApi.ObjectView>>{public CompletableFuture<MapLayersApi.Page<MapLayersApi.ObjectView>> read(DomainApi api){return api.mapLayerObjects(layer,"",OptionalLong.empty());}}

 record OnlinePlayers() implements DomainWatch<Page<OnlinePlayer>>{public CompletableFuture<Page<OnlinePlayer>> read(DomainApi api){return api.onlinePlayers("");}}
 record Positions(String dimension) implements DomainWatch<Page<PlayerPosition>>{public CompletableFuture<Page<PlayerPosition>> read(DomainApi api){return api.positions(dimension,"");}}
 record Waystones(String dimension) implements DomainWatch<Page<Waystone>>{public CompletableFuture<Page<Waystone>> read(DomainApi api){return api.waystones(dimension,"");}}

 record GroupMarkers(UUID group) implements DomainWatch<GroupPage<GroupMarker>>{public CompletableFuture<GroupPage<GroupMarker>> read(DomainApi api){return api.groupMarkers(group,"");}}
 record GroupMembers(UUID group,GroupPeople kind) implements DomainWatch<GroupPage<GroupPerson>>{public CompletableFuture<GroupPage<GroupPerson>> read(DomainApi api){return api.groupPeople(group,kind,"");}}
 record EventSchedule(UUID id) implements DomainWatch<DomainData.EventSchedule>{public CompletableFuture<DomainData.EventSchedule> read(DomainApi api){return api.eventSchedule(id);}}
 record ScheduledAnnouncements() implements DomainWatch<Page<ScheduledAnnouncement>>{public CompletableFuture<Page<ScheduledAnnouncement>> read(DomainApi api){return api.scheduledAnnouncements();}}

 record ServerControl() implements DomainWatch<DomainData.ServerControl>{public CompletableFuture<DomainData.ServerControl> read(DomainApi api){return api.serverControl();}}
 record ModerationPublications() implements DomainWatch<DomainData.Publications> {public CompletableFuture<DomainData.Publications> read(DomainApi api){return api.moderationPublications("",OptionalLong.empty());}}
 record Pack() implements DomainWatch<DomainData.Pack> {public CompletableFuture<DomainData.Pack> read(DomainApi api){return api.pack();}}
 record StaffReports(String filter) implements DomainWatch<Page<DomainData.StaffReport>> {public StaffReports{Objects.requireNonNull(filter);}public CompletableFuture<Page<DomainData.StaffReport>> read(DomainApi api){return api.staffReports(filter,"");}}
 record StaffReport(UUID id) implements DomainWatch<DomainData.StaffReport> {public StaffReport{Objects.requireNonNull(id);}public CompletableFuture<DomainData.StaffReport> read(DomainApi api){return api.staffReport(id);}}
 record Reports() implements DomainWatch<Page<DomainData.Report>> {public CompletableFuture<Page<DomainData.Report>> read(DomainApi api){return api.reports("");}}
 record Report(UUID id) implements DomainWatch<DomainData.Report> {public Report{Objects.requireNonNull(id);}public CompletableFuture<DomainData.Report> read(DomainApi api){return api.report(id);}}
 record Statistics(UUID id) implements DomainWatch<DomainData.Statistics> {public Statistics{Objects.requireNonNull(id);}public CompletableFuture<DomainData.Statistics> read(DomainApi api){return api.statistics(id);}}
 record Skin(UUID id) implements DomainWatch<DomainData.Skin> {public Skin{Objects.requireNonNull(id);}public CompletableFuture<DomainData.Skin> read(DomainApi api){return api.skin(id);}}
 record CommunityList(Community domain) implements DomainWatch<Page<Document>> {
  public CommunityList{Objects.requireNonNull(domain);}
  public CompletableFuture<Page<Document>> read(DomainApi api){return api.community(domain,"");}
 }
 record CommunityDocument(Community domain,UUID id) implements DomainWatch<Document> {
  public CommunityDocument{Objects.requireNonNull(domain);Objects.requireNonNull(id);}
  public CompletableFuture<Document> read(DomainApi api){return api.document(domain,id);}
 }
 record Tasks(Optional<UUID> group) implements DomainWatch<Page<DomainData.Task>> {
  public Tasks{Objects.requireNonNull(group);}
  public CompletableFuture<Page<DomainData.Task>> read(DomainApi api){return api.tasks(group,"");}
 }
 record Task(UUID id) implements DomainWatch<DomainData.Task> {
  public Task{Objects.requireNonNull(id);}
  public CompletableFuture<DomainData.Task> read(DomainApi api){return api.task(id);}
 }
 record Profile(UUID id) implements DomainWatch<DomainData.Profile> {
  public Profile{Objects.requireNonNull(id);}
  public CompletableFuture<DomainData.Profile> read(DomainApi api){return api.profile(id);}
 }
 record Notifications() implements DomainWatch<Page<Notice>> {
  public CompletableFuture<Page<Notice>> read(DomainApi api){return api.notifications("");}
 }
 record Chat() implements DomainWatch<Page<ChatMessage>> {
  public CompletableFuture<Page<ChatMessage>> read(DomainApi api){return api.chat("");}
 }
 record Channels() implements DomainWatch<Page<Channel>> {
  public CompletableFuture<Page<Channel>> read(DomainApi api){return api.channels("");}
 }
 record Publications(boolean own) implements DomainWatch<DomainData.Publications> {
  public CompletableFuture<DomainData.Publications> read(DomainApi api){return api.publications(own,"",OptionalLong.empty());}
 }
 record Publication(UUID id,boolean own) implements DomainWatch<DomainData.Publication> {
  public Publication{Objects.requireNonNull(id);}
  public CompletableFuture<DomainData.Publication> read(DomainApi api){return api.publication(id,own);}
 }
 record Territories() implements DomainWatch<Page<GroupTerritory>> {
  public CompletableFuture<Page<GroupTerritory>> read(DomainApi api){return api.territories("");}
 }
}
