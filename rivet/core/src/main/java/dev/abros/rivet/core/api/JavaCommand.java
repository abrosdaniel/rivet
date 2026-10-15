package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Converts only the closed public command vocabulary into existing validated domain commands. */
final class JavaCommand {
 record Request(String method,String path,String match,JsonObject data){}
 static Request encode(DomainCommand command){
  var data=new JsonObject();String method="POST",path,match=null;
  switch(Objects.requireNonNull(command)){
   case DomainCommand.SaveGroupMarker c->{path="groups/"+c.group()+"/actions/markerSave";match=revision(c.groupRevision());data.addProperty("itemId",c.id().map(UUID::toString).orElse(""));data.addProperty("itemRevision",c.revision());data.addProperty("title",c.title());data.addProperty("description",c.description());data.add("location",Json.GSON.toJsonTree(c.location()));}
   case DomainCommand.DeleteGroupMarker c->{path="groups/"+c.group()+"/actions/markerDelete";match=revision(c.groupRevision());data.addProperty("itemId",c.id().toString());data.addProperty("itemRevision",c.revision());}
   case DomainCommand.ScheduleAnnouncement c->{path="admin/server/announcements";data.addProperty("text",c.text());data.addProperty("startsAt",c.startsAt());}
   case DomainCommand.CancelAnnouncement c->{path="admin/server/announcements/"+c.id()+"/cancel";match=revision(c.revision());}
   case DomainCommand.CreateScheduledEvent c->{path="events";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("startsAt",c.startsAt());data.addProperty("capacity",c.capacity());data.addProperty("visibility",c.visibility());data.addProperty("group",c.group().map(UUID::toString).orElse(""));data.addProperty("invitees",String.join(",",c.invitees().stream().map(UUID::toString).toList()));data.addProperty("durationMinutes",c.durationMinutes());data.addProperty("repeat",c.repeat());data.addProperty("occurrences",c.occurrences());data.addProperty("timezone",c.timezone());}
   case DomainCommand.GroupTerritory c->{path="groups/"+c.id()+"/actions/territory";match=revision(c.revision());var shape=c.territory().map(v->Json.GSON.toJsonTree(v)).orElse(JsonNull.INSTANCE);if(shape.isJsonObject())shape.getAsJsonObject().remove("name");data.add("territory",shape);}
   case DomainCommand.DocumentLocation c->{path=c.domain().name().toLowerCase(Locale.ROOT)+"/"+c.id()+"/actions/location";match=revision(c.revision());data.add("location",c.location().map(v->Json.GSON.toJsonTree(v)).orElse(JsonNull.INSTANCE));}
   case DomainCommand.HideDocument c->{path=c.domain().name().toLowerCase(Locale.ROOT)+"/"+c.id()+"/actions/hide";match=revision(c.revision());data.addProperty("text",c.reason());}
   case DomainCommand.IdeaStatus c->{path="ideas/"+c.id()+"/actions/status";match=revision(c.revision());data.addProperty("status",c.status());data.addProperty("text",c.answer());}
   case DomainCommand.ScheduleRestart c->{path="admin/server/restart";match="\""+c.revision()+"\"";data.addProperty("seconds",c.seconds());data.addProperty("reason",c.reason());}
   case DomainCommand.SetMaintenance c->{path="admin/server/maintenance";match="\""+c.revision()+"\"";data.addProperty("enabled",c.enabled());data.addProperty("minutes",c.minutes());data.addProperty("reason",c.reason());}
   case DomainCommand.PinAnnouncement c->{path="admin/server/announcement";match="\""+c.revision()+"\"";data.addProperty("text",c.text());data.addProperty("minutes",c.minutes());}
   case DomainCommand.AddSubtask c->{path="tasks/"+c.id()+"/subtask-add";match=revision(c.revision());data.addProperty("title",c.title());data.addProperty("assignee",c.assignee().map(UUID::toString).orElse(""));}
   case DomainCommand.EditSubtask c->{path="tasks/"+c.id()+"/subtask-edit";match=revision(c.revision());data.addProperty("index",c.index());data.addProperty("title",c.title());}
   case DomainCommand.ToggleSubtask c->{path="tasks/"+c.id()+"/subtask-toggle";match=revision(c.revision());data.addProperty("index",c.index());}
   case DomainCommand.RemoveSubtask c->{path="tasks/"+c.id()+"/subtask-remove";match=revision(c.revision());data.addProperty("index",c.index());}
   case DomainCommand.TaskResources c->{path="tasks/"+c.id()+"/resources";match=revision(c.revision());data.add("resources",Json.GSON.toJsonTree(c.resources()));}
   case DomainCommand.EventReminder c->{path="events/"+c.id()+"/actions/reminder";match=revision(c.revision());data.addProperty("minutes",c.minutes());}
   case DomainCommand.CancelEventSeries c->{path="events/"+c.id()+"/actions/cancelSeries";match=revision(c.revision());}
   case DomainCommand.RemoveGroupMember c->{path="groups/"+c.id()+"/actions/removeMember";match=revision(c.revision());data.addProperty("target",c.target().toString());data.addProperty("reason",c.reason());}
   case DomainCommand.GroupAssistantRemoval c->{path="groups/"+c.id()+"/actions/assistantRemoval";match=revision(c.revision());data.addProperty("enabled",c.enabled());}

   case DomainCommand.AssignTask c->{path="tasks/"+c.id()+"/assign";match=revision(c.revision());data.addProperty("assignee",c.assignee().map(UUID::toString).orElse(""));}
   case DomainCommand.TaskDue c->{path="tasks/"+c.id()+"/due";match=revision(c.revision());data.addProperty("dueAt",c.dueAt());}
   case DomainCommand.TaskComment c->{path="tasks/"+c.id()+"/comment";match=revision(c.revision());data.addProperty("text",c.text());}
   case DomainCommand.RestoreTask c->{path="tasks/"+c.id()+"/restore";match=revision(c.revision());}
   case DomainCommand.TaskDependencies c->{path="tasks/"+c.id()+"/dependencies";match=revision(c.revision());data.addProperty("codes",c.codes());}
   case DomainCommand.RepeatTask c->{path="tasks/"+c.id()+"/repeat";match=revision(c.revision());data.addProperty("days",c.days());}
   case DomainCommand.ReserveTask c->{path="tasks/"+c.id()+"/reserve";match=revision(c.revision());data.addProperty("release",c.release());}
   case DomainCommand.SaveNotificationPreferences c->{method="PUT";path="me/notification-preferences";match="\""+c.revision()+"\"";data.add("muted",Json.GSON.toJsonTree(c.muted()));data.addProperty("snoozeUntil",c.snoozeUntil());}
   case DomainCommand.SendChat c->{path="chat/messages";data.addProperty("channel",c.channel());data.addProperty("text",c.text());}
   case DomainCommand.ModeratePublication c->{path="admin/map/objects/"+c.id()+"/"+c.decision().name().toLowerCase(Locale.ROOT);match=revision(c.revision());}
   case DomainCommand.CreateGroup c->{path="groups";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("type",c.type());}
   case DomainCommand.CreateBoard c->{path="board";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("days",c.days());data.addProperty("category",c.category());}
   case DomainCommand.CreateEvent c->{path="events";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("startsAt",c.startsAt());data.addProperty("capacity",c.capacity());}
   case DomainCommand.CreateIdea c->{path="ideas";data.addProperty("title",c.title());data.addProperty("description",c.description());}
   case DomainCommand.CreatePoll c->{path="polls";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("endsAt",c.endsAt());data.add("options",Json.GSON.toJsonTree(c.options()));data.addProperty("multiple",c.multiple());data.addProperty("changeVote",c.changeVote());data.addProperty("liveResults",c.liveResults());}
   case DomainCommand.EditDocument c->{path=c.domain().name().toLowerCase(Locale.ROOT)+"/"+c.id()+"/actions/edit";match=revision(c.revision());data.addProperty("title",c.title());data.addProperty("description",c.description());if(c.domain()==DomainData.Community.GROUPS)data.addProperty("type",c.groupType());}
   case DomainCommand.DeleteDocument c->{path=c.domain().name().toLowerCase(Locale.ROOT)+"/"+c.id()+"/actions/delete";match=revision(c.revision());}
   case DomainCommand.RestoreDocument c->{path=c.domain().name().toLowerCase(Locale.ROOT)+"/"+c.id()+"/actions/restore";match=revision(c.revision());}
   case DomainCommand.ReplyReport c->{path="admin/reports/"+c.id()+"/actions/reply";match=revision(c.revision());data.addProperty("text",c.text());data.addProperty("resolved",c.resolved());}
   case DomainCommand.ManageReport c->{path="admin/reports/"+c.id()+"/actions/"+c.operation().name().toLowerCase(Locale.ROOT);match=revision(c.revision());switch(c.operation()){case CLAIM,RELEASE->{}case ASSIGN->data.addProperty("target",c.target().orElseThrow().toString());case CLASSIFY->{data.addProperty("category",c.category());data.addProperty("priority",c.priority());}case STATE->{data.addProperty("status",c.status());data.addProperty("nextStep",c.nextStep());}case NOTE->data.addProperty("text",c.text());}}
   case DomainCommand.SubmitReport c->{path="reports";data.addProperty("message",c.message());data.addProperty("category",c.category());}
   case DomainCommand.RescheduleEvent c->{path="events/"+c.id()+"/actions/reschedule";match=revision(c.revision());data.addProperty("startsAt",c.startsAt());}
   case DomainCommand.CancelEvent c->{path="events/"+c.id()+"/actions/cancel";match=revision(c.revision());data.addProperty("reason",c.reason());}
   case DomainCommand.WithdrawResponse c->{path="board/"+c.id()+"/actions/withdrawResponse";match=revision(c.revision());data.addProperty("confirmAccepted",c.confirmAccepted());}
   case DomainCommand.DecideResponse c->{path="board/"+c.id()+"/actions/respondDecision";match=revision(c.revision());data.addProperty("target",c.target().toString());data.addProperty("decision",c.decision());data.addProperty("text",c.text());}
   case DomainCommand.GroupAction c->{match=revision(c.revision());String action=switch(c.operation()){case INVITE->"invite";case REVOKE_INVITATION->"revokeInvitation";case APPLICATION->"application";case INVITATION->"invitation";case ROLE->"role";case RECRUITING->"recruiting";};path="groups/"+c.id()+"/actions/"+action;
    if(Set.of(DomainCommand.GroupOperation.INVITE,DomainCommand.GroupOperation.REVOKE_INVITATION,DomainCommand.GroupOperation.APPLICATION,DomainCommand.GroupOperation.ROLE).contains(c.operation()))data.addProperty("target",c.target().orElseThrow().toString());
    if(c.operation()==DomainCommand.GroupOperation.APPLICATION||c.operation()==DomainCommand.GroupOperation.INVITATION)data.addProperty("accept",c.accept());if(c.operation()==DomainCommand.GroupOperation.ROLE)data.addProperty("role",c.role());}
   case DomainCommand.CreateTask c->{path="tasks";data.addProperty("title",c.title());data.addProperty("description",c.description());data.addProperty("group",c.group().map(UUID::toString).orElse(""));}
   case DomainCommand.UpdateTask c->{method="PUT";path="tasks/"+c.id();match=revision(c.revision());data.addProperty("title",c.title());data.addProperty("description",c.description());}
   case DomainCommand.TaskStatus c->{path="tasks/"+c.id()+"/status";match=revision(c.revision());data.addProperty("status",c.status());}
   case DomainCommand.DeleteTask c->{path="tasks/"+c.id()+"/delete";match=revision(c.revision());}
   case DomainCommand.TaskLocation c->{path="tasks/"+c.id()+"/location";match=revision(c.revision());data.add("location",c.location().map(v->Json.GSON.toJsonTree(v)).orElse(JsonNull.INSTANCE));}
   case DomainCommand.SaveProfile c->{method="PUT";path="me/profile";match="\""+c.revision()+"\"";data.addProperty("about",c.about());data.addProperty("interests",c.interests());}
   case DomainCommand.ReadNotice c->{path="me/notifications/"+c.id()+"/read";}
   case DomainCommand.SubmitPublication c->{method="PUT";path="map/objects/"+c.id()+"/draft";match=revision(c.revision());data.addProperty("type",c.draft() instanceof DomainData.Marker?"marker":"territory");data.add("draft",Json.GSON.toJsonTree(Objects.requireNonNull(c.draft())));}
   case DomainCommand.WithdrawPublication c->{path="map/objects/"+c.id()+"/withdraw";match=revision(c.revision());}
   case DomainCommand.CommunityAction c->{match=revision(c.revision());String route=switch(c.action()){
    case EVENT_JOIN->"events/join";case EVENT_LEAVE->"events/leave";
    case GROUP_APPLY->"groups/apply";case GROUP_LEAVE->"groups/leave";case GROUP_WITHDRAW->"groups/withdrawApplication";
    case BOARD_RESPOND->"board/respond";case BOARD_CLOSE->"board/close";case BOARD_COMPLETE->"board/complete";
    case POLL_VOTE->"polls/vote";case IDEA_SUPPORT->"ideas/support";};
    String[] parts=route.split("/");path=parts[0]+"/"+c.id()+"/actions/"+parts[1];
    if(c.action()==DomainCommand.Action.GROUP_APPLY||c.action()==DomainCommand.Action.BOARD_RESPOND)data.addProperty("text",c.text());
    if(c.action()==DomainCommand.Action.POLL_VOTE)data.add("choices",Json.GSON.toJsonTree(c.choices()));
   }
  }
  return new Request(method,path,match,data);
 }
 private static String revision(long value){if(value<0)throw ApiJson.invalid();return "\""+value+"\"";}
}
