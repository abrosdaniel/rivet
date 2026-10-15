package dev.abros.rivet.api;

import java.util.*;
import static dev.abros.rivet.api.DomainData.*;

/** Explicit player actions; no arbitrary routes, menu operations, admin flags or executable commands. */
public sealed interface DomainCommand {
 record SaveGroupMarker(UUID group,long groupRevision,Optional<UUID> id,long revision,String title,String description,Location location) implements DomainCommand {}
 record DeleteGroupMarker(UUID group,long groupRevision,UUID id,long revision) implements DomainCommand {}
 record CreateScheduledEvent(String title,String description,String startsAt,int capacity,String visibility,Optional<UUID> group,List<UUID> invitees,int durationMinutes,String repeat,int occurrences,String timezone) implements DomainCommand {public CreateScheduledEvent{invitees=List.copyOf(invitees);}}
 record ScheduleAnnouncement(String text,String startsAt) implements DomainCommand {}
 record CancelAnnouncement(UUID id,long revision) implements DomainCommand {}

 record GroupTerritory(UUID id,long revision,Optional<Territory> territory) implements DomainCommand {}
 record DocumentLocation(Community domain,UUID id,long revision,Optional<Location> location) implements DomainCommand {}
 record HideDocument(Community domain,UUID id,long revision,String reason) implements DomainCommand {}
 record IdeaStatus(UUID id,long revision,String status,String answer) implements DomainCommand {}
 record ScheduleRestart(String revision,int seconds,String reason) implements DomainCommand {}
 record SetMaintenance(String revision,boolean enabled,int minutes,String reason) implements DomainCommand {}
 record PinAnnouncement(String revision,String text,int minutes) implements DomainCommand {}
 record AddSubtask(UUID id,long revision,String title,Optional<UUID> assignee) implements DomainCommand {}
 record EditSubtask(UUID id,long revision,int index,String title) implements DomainCommand {}
 record ToggleSubtask(UUID id,long revision,int index) implements DomainCommand {}
 record RemoveSubtask(UUID id,long revision,int index) implements DomainCommand {}
 record TaskResources(UUID id,long revision,List<Resource> resources) implements DomainCommand {public TaskResources{resources=List.copyOf(resources);}}
 record EventReminder(UUID id,long revision,int minutes) implements DomainCommand {}
 record CancelEventSeries(UUID id,long revision) implements DomainCommand {}
 record RemoveGroupMember(UUID id,long revision,UUID target,String reason) implements DomainCommand {}
 record GroupAssistantRemoval(UUID id,long revision,boolean enabled) implements DomainCommand {}

 record AssignTask(UUID id,long revision,Optional<UUID> assignee) implements DomainCommand {}
 record TaskDue(UUID id,long revision,long dueAt) implements DomainCommand {}
 record TaskComment(UUID id,long revision,String text) implements DomainCommand {}
 record RestoreTask(UUID id,long revision) implements DomainCommand {}
 record TaskDependencies(UUID id,long revision,String codes) implements DomainCommand {}
 record RepeatTask(UUID id,long revision,int days) implements DomainCommand {}
 record ReserveTask(UUID id,long revision,boolean release) implements DomainCommand {}
 record SaveNotificationPreferences(String revision,List<String> muted,long snoozeUntil) implements DomainCommand {public SaveNotificationPreferences{muted=List.copyOf(muted);}}
 record SendChat(String channel,String text) implements DomainCommand {}
 enum PublicationDecision { APPROVE, REJECT, WITHDRAW }
 record ModeratePublication(UUID id,long revision,PublicationDecision decision) implements DomainCommand {}
 record CreateGroup(String title,String description,String type) implements DomainCommand {}
 record CreateBoard(String title,String description,int days,String category) implements DomainCommand {}
 record CreateEvent(String title,String description,String startsAt,int capacity) implements DomainCommand {}
 record CreateIdea(String title,String description) implements DomainCommand {}
 record CreatePoll(String title,String description,String endsAt,List<String> options,boolean multiple,boolean changeVote,boolean liveResults) implements DomainCommand {public CreatePoll{options=List.copyOf(options);}}
 record EditDocument(Community domain,UUID id,long revision,String title,String description,String groupType) implements DomainCommand {}
 record DeleteDocument(Community domain,UUID id,long revision) implements DomainCommand {}
 record RestoreDocument(Community domain,UUID id,long revision) implements DomainCommand {}
 record ReplyReport(UUID id,long revision,String text,boolean resolved) implements DomainCommand {}
 enum ReportOperation { CLAIM, RELEASE, ASSIGN, CLASSIFY, STATE, NOTE }
 record ManageReport(UUID id,long revision,ReportOperation operation,Optional<UUID> target,String category,String priority,String status,String nextStep,String text) implements DomainCommand {}
 record SubmitReport(String message,String category) implements DomainCommand {}
 enum GroupOperation { INVITE, REVOKE_INVITATION, APPLICATION, INVITATION, ROLE, RECRUITING }
 record GroupAction(UUID id,long revision,GroupOperation operation,Optional<UUID> target,boolean accept,String role) implements DomainCommand {}
 record RescheduleEvent(UUID id,long revision,String startsAt) implements DomainCommand {}
 record CancelEvent(UUID id,long revision,String reason) implements DomainCommand {}
 record WithdrawResponse(UUID id,long revision,boolean confirmAccepted) implements DomainCommand {}
 record DecideResponse(UUID id,long revision,UUID target,String decision,String text) implements DomainCommand {}
 record CreateTask(String title,String description,Optional<UUID> group) implements DomainCommand {}
 record UpdateTask(UUID id,long revision,String title,String description) implements DomainCommand {}
 record TaskStatus(UUID id,long revision,String status) implements DomainCommand {}
 record DeleteTask(UUID id,long revision) implements DomainCommand {}
 record TaskLocation(UUID id,long revision,Optional<Location> location) implements DomainCommand {}
 record SaveProfile(String revision,String about,String interests) implements DomainCommand {}
 record ReadNotice(String id) implements DomainCommand {}
 record SubmitPublication(UUID id,long revision,Shape draft) implements DomainCommand {}
 record WithdrawPublication(UUID id,long revision) implements DomainCommand {}
 enum Action { EVENT_JOIN,EVENT_LEAVE,GROUP_APPLY,GROUP_LEAVE,GROUP_WITHDRAW,BOARD_RESPOND,BOARD_CLOSE,BOARD_COMPLETE,POLL_VOTE,IDEA_SUPPORT }
 record CommunityAction(UUID id,long revision,Action action,String text,List<Integer> choices) implements DomainCommand {
  public CommunityAction{choices=List.copyOf(choices);}
 }
}
