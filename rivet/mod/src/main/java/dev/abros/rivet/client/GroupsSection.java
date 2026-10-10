package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import static dev.abros.rivet.client.CommunityScreen.*;
/** Overview, roster and private management queues share one selected group. */
final class GroupsSection {
 static void render(CommunityScreen host,JsonObject j){
  boolean manage=j.get("manage").getAsBoolean();
  if(!manage&&host.groupTab.equals("requests"))host.groupTab="overview";
  if(CommunityScreen.plus()&&Set.of("tasks","places").contains(host.groupTab)){CommunityTools.groupItems(host,j);return;}
  if(host.groupTab.equals("events")){host.text(Client.text("ui.group_events_a5074194"));boolean found=host.data.has("related")&&host.data.getAsJsonArray("related").asList().stream().anyMatch(e->Json.str(e.getAsJsonObject(),"section").equals("events"));if(!found)host.text(Client.text("ui.no_events_yet_69cb057e"));}
  else if(host.groupTab.equals("members")){
   host.text(Client.text("ui.members_2bee8bf5")+j.get("membersCount").getAsInt());

   if(CommunityScreen.plus()&&host.owner(j))host.secondary(Client.text("ui.assistants_remove_members_d425d1e7"),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.assistants_can_remove_regular_members_81f46935"),List.of(Client.text("ui.deny_21aba913"),Client.text("ui.allow_616bb19d")),i->{var b=new JsonObject();b.addProperty("enabled",i==1);host.send("plusAssistantRemoval",b);})));
   for(var entry:j.getAsJsonObject("members").entrySet()){
    int card=host.beginCard();String target=entry.getKey(),role=entry.getValue().getAsString();String label=host.shortName(target)+" · "+switch(role){case "leader"->Client.text("ui.leader_3dc64933");case "assistant"->Client.text("ui.assistant_ffcd1996");default->Client.text("ui.member_76ffe26d");};
    host.text(label);if(j.has("workload")){var work=j.getAsJsonObject("workload").getAsJsonObject(target);host.text(Client.text("ui.active_tasks_24eea9c1")+(work==null?0:work.get("active"))+Client.text("ui.overdue_81fd8e57")+(work==null?0:work.get("overdue")));}
    if(CommunityScreen.plus()&&j.has("canRemoveMember")&&j.get("canRemoveMember").getAsBoolean()&&!target.equals(Json.str(j,"owner"))&&(host.owner(j)||ServerMenuClient.admin()||role.equals("member")))host.action(Client.text("ui.exclude_daa44b7f")+host.shortName(target),()->{var preset=new JsonObject();preset.addProperty("target",target);host.form(Client.text("ui.remove_member_de37aa82"),"plusRemoveMember",List.of(new CommunityScreen.Field("reason",Client.text("server.reason"),300)),preset);});
    if(host.owner(j)&&!target.equals(me()))host.action(Client.text("ui.role_and_leadership_aded0a04"),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),host.shortName(target),List.of(Client.text("ui.make_regular_member_e7dc33e8"),Client.text("ui.appoint_assistant_5ad0ec2a"),Client.text("ui.transfer_leadership_11d9c3a0")),i->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("role",List.of("member","assistant","leader").get(i));host.confirm(i==2?Client.text("ui.transfer_leadership_to_this_member_f4592ca5"):Client.text("ui.change_member_role_96913a6a"),"role",body);})));host.endCard(card);
   }
  }else if(host.groupTab.equals("requests")){
   var applications=j.getAsJsonObject("applications");host.text(Client.text("ui.applications_3424a29d")+j.get("applicationsCount").getAsInt());if(applications.isEmpty())host.text(Client.text("ui.no_pending_applications_5b55a9e8"));
   for(var entry:applications.entrySet()){String target=entry.getKey();var application=entry.getValue().getAsJsonObject();int card=host.beginCard();host.text(Json.str(application,"name"));host.text(Json.str(application,"text"));var controls=new ArrayList<CommunityScreen.Row>();for(boolean accept:List.of(true,false))controls.add(new CommunityScreen.Row(accept?Client.text("ui.accept_8b9ab481"):Client.text("ui.decline_5047a069"),()->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("accept",accept);host.send("application",body);}));host.actionRow(controls);host.endCard(card);}
   host.text(Client.text("ui.invitations_5f1e5d05")+j.get("invitationsCount").getAsInt());
   if(j.getAsJsonObject("invitations").isEmpty())host.text(Client.text("ui.no_pending_invitations_9279ff1a"));
   for(String target:j.getAsJsonObject("invitations").keySet()){int card=host.beginCard();host.text(host.shortName(target));host.action(Client.text("ui.cancel_invitation_748e4a88"),()->{var b=new JsonObject();b.addProperty("target",target);host.confirm(Client.text("ui.cancel_invitation_051bcc1f"),"revokeInvitation",b);});host.endCard(card);}
  }else{
   host.text(Json.str(j,"type")+" · "+(j.get("recruiting").getAsBoolean()?Client.text("ui.registration_open_4bd5a5ce"):Client.text("ui.registration_closed_a0d3acee")));
   host.text(Client.text("ui.members_2bee8bf5")+j.get("membersCount").getAsInt());
   if(MapGroupClient.available()){
    host.text(j.has("territory")?Client.text("ui.group_territory_ff2573fd"):Client.text("ui.territory_has_not_been_created_yet_8aac3009"));
    if(manage||j.has("territory"))host.action(manage?(j.has("territory")?Client.text("ui.edit_territory_f9721380"):Client.text("ui.create_territory_9f394379")):Client.text("ui.show_territory_f0a04588"),()->openTerritory(host,j));
   }
   if(can(j,"invite")&&host.invitationTarget!=null)host.action(Client.text("ui.invite_10ea7ea4")+Json.str(host.invitationTarget,"name"),()->{var invite=new JsonObject();invite.addProperty("target",Json.str(host.invitationTarget,"uuid"));host.send("invite",invite);});
   if(can(j,"apply"))host.action(Client.text("ui.apply_67020d96"),()->host.form(Client.text("ui.application_ca87acdc"),"apply",List.of(new CommunityScreen.Field("text",Client.text("ui.about_me_312416bd"),500)),new JsonObject()));
   if(can(j,"withdrawApplication")){host.text(Client.text("ui.your_application_is_awaiting_review_563f9e72"));host.secondary(Client.text("ui.withdraw_my_application_03f6fac4"),()->host.confirm(Client.text("ui.withdraw_application_69a8c52d"),"withdrawApplication",new JsonObject()));}
   if(j.getAsJsonObject("invitations").has(me()))host.action(Client.text("ui.reply_to_invitation_28b926e9"),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.group_invitation_e6f65664"),List.of(Client.text("ui.accept_8b9ab481"),Client.text("ui.decline_5047a069")),i->{var b=new JsonObject();b.addProperty("accept",i==0);host.send("invitation",b);})));
  }
  if(ServerMenuClient.supports("player-tools")&&manage){if(j.has("expiresAt")&&j.get("expiresAt").getAsLong()>0)host.text(Client.text("ui.ends_22ad3eaf")+local(j.get("expiresAt").getAsLong()));host.secondary(Client.text("ui.group_duration_4f376faf"),()->net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.temporary_group_295e63a2"),List.of(Client.text("ui.indefinite_519bb302"),Client.text("ui.1_day_3cd409a4"),Client.text("ui.7_days_e65da6ce"),Client.text("ui.30_days_fb925c5d")),index->{var body=new JsonObject();body.addProperty("days",List.of(0,1,7,30).get(index));host.send("toolsTemporary",body);})));}
  if(can(j,"leave"))host.secondary(Client.text("ui.leave_group_1a94bdb9"),()->host.confirm(Client.text("ui.leave_group_07602f0c"),"leave",new JsonObject()));
  if(can(j,"invite"))host.secondary(Client.text("ui.invite_player_eb0a66b4"),()->FeatureListScreen.pick(host.surface(),player->{var body=new JsonObject();body.addProperty("target",Json.str(player,"uuid"));host.send("invite",body);}));
  if(can(j,"recruiting"))host.secondary(j.get("recruiting").getAsBoolean()?Client.text("ui.close_registration_b4607a14"):Client.text("ui.open_registration_809ed8bb"),()->host.send("recruiting",new JsonObject()));
 }
 static void openTerritory(CommunityScreen host,JsonObject j){
  var mc=net.minecraft.client.Minecraft.getInstance();if(!MapGroupClient.available()||mc.level==null)return;
  var map=new WorldMapScreen(host.surface());String id=Json.str(j,"id"),name=Json.str(j,"title");
  var t=j.has("territory")?dev.abros.rivet.core.map.MapTerritoryJson.read(j.getAsJsonObject("territory"),java.util.UUID.fromString(id),name):null;
  var group=new MapGroupClient.Group(id,name,j.get("revision").getAsLong(),j.get("manage").getAsBoolean(),t);
  if(group.manage()&&t==null){map.drawTerritory(new MapTerritories.Draft(group,mc.level.dimension().location().toString()));return;}
  if(t!=null)map.focusTerritory(t);mc.setScreen(map);
  if(group.manage())mc.setScreen(new MapTerritoryScreen(map,new MapTerritories.Draft(group,map.selectedDimension())));
 }

}
