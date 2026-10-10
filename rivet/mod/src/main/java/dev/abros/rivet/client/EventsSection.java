package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import static dev.abros.rivet.client.CommunityScreen.*;
/** events entry layout and contextual actions. */
final class EventsSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();int card=host.beginCard();
                host.text(Client.text("ui.starts_2af19085")+local(j.get("startsAt").getAsLong()));host.text(Client.text("ui.duration_99aab1a2")+(j.has("durationMinutes")?j.get("durationMinutes").getAsString():"60")+Client.text("ui.min_2190bd44"));if(j.has("conflicts")&&!j.getAsJsonArray("conflicts").isEmpty()){host.text(Client.text("ui.conflicts_with_events_you_have_joined_97248efe"));for(var e:j.getAsJsonArray("conflicts")){var conflict=e.getAsJsonObject();host.text(Json.str(conflict,"title")+" · "+local(conflict.get("startsAt").getAsLong()));}}int capacity=j.get("capacity").getAsInt();var participants=j.getAsJsonObject("participants");host.text(Client.text("ui.members_2bee8bf5")+j.get("participantsCount").getAsInt()+(capacity>0?" / "+capacity:""));int n=j.has("participantOffset")?j.get("participantOffset").getAsInt():0;for(var entry:participants.entrySet()){host.text((capacity>0&&n++>=capacity?Client.text("ui.queue_f4020e77"):"✓ ")+entry.getValue().getAsString());}
                if(j.has("waitlistPosition")&&j.get("waitlistPosition").getAsInt()>0)host.text(Client.text("ui.your_queue_position_af0e8da6")+j.get("waitlistPosition").getAsInt());
                long remaining=j.get("startsAt").getAsLong()-System.currentTimeMillis();if(remaining>0)host.text(Client.text("ui.starts_in_f0791955")+Math.max(1,(remaining+59999)/60000)+Client.text("ui.min_2190bd44"));

                if(can(j,"join")||can(j,"leave"))host.action(j.get("isParticipant").getAsBoolean()?Client.text("ui.withdraw_participation_79b97dec"):Client.text("ui.join_2a62b76f"),()->{if(j.get("isParticipant").getAsBoolean())host.send("leave",new JsonObject());else if(j.has("conflicts")&&!j.getAsJsonArray("conflicts").isEmpty())host.confirm(Client.text("ui.join_despite_the_schedule_conflict_e4dbc6a6"),"join",new JsonObject());else host.send("join",new JsonObject());});
                if(CommunityScreen.plus()){
                 int reminder=j.has("reminderMinutes")?j.get("reminderMinutes").getAsInt():5;
                 host.action(Client.text("ui.remind_3b9ca6a5")+(reminder<0?Client.text("ui.disabled_bedfedb4"):reminder==0?Client.text("ui.at_the_start_0cc17251"):reminder+Client.text("ui.min_2190bd44")),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.event_reminder_8249eb44"),List.of(Client.text("ui.1_hour_before_9af53cf2"),Client.text("ui.15_minutes_before_7bb780fd"),Client.text("ui.5_minutes_before_b3eb5bb9"),Client.text("ui.at_the_start_1eebd264"),Client.text("ui.no_reminder_7319fc9a")),i->{var body=new JsonObject();body.addProperty("minutes",List.of(60,15,5,0,-1).get(i));host.send("plusReminder",body);})));
                 if(j.has("series")&&manage)host.secondary(Client.text("ui.cancel_future_events_in_series_10c3ee8e"),()->host.confirm(Client.text("ui.cancel_all_future_events_in_this_17c3e9ec"),"plusCancelSeries",new JsonObject()));
                }
                if(j.has("cancelReason"))host.text(Client.text("ui.cancellation_reason_1a8033dd")+Json.str(j,"cancelReason"));
                if(ServerMenuClient.supports("player-tools")&&manage&&remaining<=0)for(var person:participants.entrySet()){String target=person.getKey();host.secondary(Client.text("ui.attendance_70dc278f")+person.getValue().getAsString()+(j.has("attendance")&&j.getAsJsonObject("attendance").has(target)?(j.getAsJsonObject("attendance").getAsJsonObject(target).get("present").getAsBoolean()?Client.text("ui.attended_fda5d41a"):Client.text("ui.absent_29556f4a")):Client.text("ui.not_marked_fe839543")),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.attendance_b60ea336")+person.getValue().getAsString(),List.of(Client.text("ui.attended_e996fb26"),Client.text("ui.did_not_attend_fdaf93c6")),index->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("present",index==0);host.send("toolsAttendance",body);})));}
                host.endCard(card);
                if(can(j,"reschedule")){host.secondary(Client.text("ui.reschedule_081b1d81"),()->host.form(Client.text("ui.event_rescheduled_0ad917f6"),"reschedule",List.of(new CommunityScreen.Field("startsAt",Client.text("ui.start_local_time_90299f1c"),30)),new JsonObject()));host.secondary(Client.text("ui.cancel_event_059d7f83"),()->host.form(Client.text("ui.event_cancellation_c7cd0253"),"cancel",List.of(new CommunityScreen.Field("reason",Client.text("ui.cancellation_reason_f8f1caba"),500)),new JsonObject()));}

 }
}
