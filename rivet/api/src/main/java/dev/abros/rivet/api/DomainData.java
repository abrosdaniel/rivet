package dev.abros.rivet.api;

import java.util.*;

/** Immutable domain projections. Times are Unix milliseconds (zero means absent); colors are opaque ARGB. */
public final class DomainData {
 public record OnlinePlayer(UUID uuid,String name){}
 public record PlayerPosition(UUID uuid,String name,String dimension,int x,int y,int z){}
 public record Waystone(UUID uuid,String name,String dimension,int x,int y,int z){}

 public enum GroupPeople { MEMBERS, APPLICATIONS, INVITATIONS }
 public record GroupPerson(UUID id,String name,String role,String text){}
 public record GroupMarker(UUID id,long revision,String title,String description,Location location){}
 public record GroupPage<T>(List<T> items,String nextCursor,long revision){public GroupPage{items=List.copyOf(items);}}
 public record ScheduledAnnouncement(UUID id,long revision,String text,long at,String author,String status){}
 public record EventOccurrence(UUID id,long revision,long startsAt,String status){}
 public record EventSchedule(UUID id,long revision,String visibility,String group,long startsAt,int durationMinutes,String repeat,String series,List<UUID> invitees,List<EventOccurrence> occurrences){public EventSchedule{invitees=List.copyOf(invitees);occurrences=List.copyOf(occurrences);}}

 public record Subtask(String title,boolean done,String assignee){}
 public record TaskComment(String author,String text,long at){}
 public record TaskDetails(UUID id,long revision,String assignee,long dueAt,int subtaskOffset,int subtaskCount,int commentOffset,int commentCount,List<Subtask> subtasks,List<TaskComment> comments,List<Resource> resources){public TaskDetails{subtasks=List.copyOf(subtasks);comments=List.copyOf(comments);resources=List.copyOf(resources);}}
 public record Resource(String item,int amount,List<String> alternatives){public Resource{alternatives=List.copyOf(alternatives);}}
 public record ServerControl(String revision,boolean maintenance,long maintenanceUntil,String maintenanceReason,long restartAt,String restartReason,String announcement,long announcementUntil){}

 private DomainData(){}
 public enum Community { GROUPS, EVENTS, BOARD, POLLS, IDEAS }
 public record Page<T>(List<T> items,String nextCursor){public Page{items=List.copyOf(items);Objects.requireNonNull(nextCursor);}}
 public record Document(UUID id,String title,String description,String preview,String status,long revision,
                        long createdAt,long startsAt,long endsAt,String type,boolean recruiting,int capacity){}
 public record Location(String name,String dimension,int x,int y,int z,boolean membersOnly){}
 public record Task(UUID id,String code,Optional<UUID> group,String title,String description,String status,long revision,
                    long dueAt,long completedAt,boolean archived,int stagesTotal,int stagesDone,Optional<Location> location){}
 public record Profile(UUID uuid,String name,String about,String interests,String revision){}
 public record NotificationPreferences(List<String> muted,long snoozeUntil,String revision){public NotificationPreferences{muted=List.copyOf(muted);}}
 public record Notice(String id,String title,String body,String section,long at,boolean read){}
 public record ChatMessage(String id,long at,String channel,String text){}
 public record Channel(String id,String name){}
 public sealed interface Shape permits Marker,Territory {}
 public record Marker(String dimension,String name,int x,int y,int z,int color,String icon) implements Shape {public Marker{color=0xff000000|(color&0xffffff);}}
 public record Point(int x,int z){}
 public record Territory(String dimension,String name,int color,List<Point> points) implements Shape {
  public Territory{points=List.copyOf(points);color=0xff000000|(color&0xffffff);}
 }
 public record Publication(UUID id,String type,long revision,Optional<Shape> published,Optional<Shape> draft){}
 public record Publications(List<Publication> items,String nextCursor,long snapshot){public Publications{items=List.copyOf(items);}}
 public record GroupTerritory(UUID id,String title,long revision,Territory territory){}
 public record PackComponent(String id,String name,String description,boolean selected,boolean optional){}
 public record Pack(String hash,String minecraft,String loader,int files,List<PackComponent> components){public Pack{components=List.copyOf(components);}}
 public record ReportNote(String author,String text,long at){}
 public record StaffReport(Report report,UUID author,String player,String assigned,String assignedName,String priority,String nextStep,List<ReportNote> internalNotes){public StaffReport{internalNotes=List.copyOf(internalNotes);}}
 public record Report(UUID id,String message,String category,String status,long revision,long createdAt,String reply,String replyAuthor,long repliedAt){}
 public record Statistics(UUID uuid,OptionalLong firstJoin,OptionalLong lastActivity,OptionalLong totalMillis,OptionalLong timeSince,OptionalLong deaths,OptionalLong deathsSince){}
 public record Skin(UUID uuid,String hash,boolean slim){}
 public record Receipt(UUID operationId,String id,String revision,boolean replayed){}
}
