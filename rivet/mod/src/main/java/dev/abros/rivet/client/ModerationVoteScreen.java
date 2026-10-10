package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
final class ModerationVoteScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private String adapterConfiguration="";
 private final Screen parent;private JsonObject target,vote=new JsonObject();private boolean creating,busy,started,reading;private String minutes="30";private String reason="",measure="kick",status="",request="";private long sent,lastRefresh;private final List<String> lines=new ArrayList<>();
 ModerationVoteScreen(Screen parent,JsonObject target){super(Client.tr("ui.rule_violation_64cf60be"));this.parent=parent;this.target=target;creating=target!=null;}
 static boolean enabled(){return ServerMenuClient.supports("moderation-votes")&&ServerMenuClient.state.has("moderationVotes")&&ServerMenuClient.state.getAsJsonObject("moderationVotes").get("enabled").getAsBoolean();}
 private JsonObject config(){return ServerMenuClient.state.has("moderationVotes")?ServerMenuClient.state.getAsJsonObject("moderationVotes"):new JsonObject();}
 private String setting(String key,String fallback){return config().has(key)?config().get(key).getAsString():fallback;}
 private static String measureName(String key){return switch(key){case "kick"->Client.text("ui.kick_3d9a9ad8");case "ban"->Client.text("ui.temporary_ban_3c993a26");case "mute"->Client.text("ui.voice_mute_12c055e2");default->key;};}
 private boolean customDuration(){return ServerMenuClient.supports("moderation-vote-duration");}
 void invalidate(){lastRefresh=0;}
 private int viewTop(){return Math.max(20,(height-290)/2);}
 private int viewBottom(){return Math.min(height-12,viewTop()+290);}
 private int formTop(){return Math.max(24,(height-240)/2);}
 @Override protected void init(){if(!customDuration())minutes=setting(measure.equals("mute")?"muteMinutes":"banMinutes",measure.equals("mute")?"15":"30");int top=formTop();int w=Math.min(440,width-28),left=(width-w)/2;lines.clear();
  if(creating){
   var allowed=config().has("actions")?config().getAsJsonArray("actions"):new JsonArray();if(!allowed.contains(new JsonPrimitive(measure)))measure=allowed.isEmpty()?"":allowed.get(0).getAsString();
   addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.player_420c4d94")+(target==null?Client.text("ui.select_f60bb5f7"):Json.str(target,"name"))),UiActions.Tone.NORMAL,"",b->FeatureListScreen.pick(this,p->{target=p;rebuildWidgets();})).bounds(left,top+26,w,20).build());
   addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.penalty_7826f266")+measureName(measure)+" ▾"),UiActions.Tone.NORMAL,"",b->{var actions=new ArrayList<String>();for(var action:config().getAsJsonArray("actions"))actions.add(action.getAsString());minecraft.setScreen(new ChoicePopup(this,Client.text("ui.penalty_4c246b10"),actions.stream().map(ModerationVoteScreen::measureName).toList(),i->{measure=actions.get(i);rebuildWidgets();},b));}).bounds(left,top+52,w,20).build());
   var edit=addRenderableWidget(UiFields.text(font,left,top+78,w,20,Client.tr("server.reason")));edit.setHint(Client.tr("ui.violation_reason_c05815fa"));edit.setMaxLength(300);edit.setValue(reason);edit.setResponder(v->reason=v);
   if(!measure.equals("kick")){var duration=addRenderableWidget(UiFields.text(font,left,top+118,90,20,Client.tr("ui.punishment_duration_minutes_2db6e9c2")));duration.setMaxLength(4);duration.setFilter(v->v.matches("[0-9]*"));duration.setValue(minutes);duration.active=customDuration();duration.setResponder(v->minutes=v);}
   var create=addRenderableWidget(UiActions.button(Client.tr("ui.start_vote_dd9fb367"),UiActions.Tone.NORMAL,"",b->confirm()).bounds(left,top+184,w/2-3,20).build());create.active=!busy&&enabled()&&!measure.isEmpty();
  }else{
   if(vote.has("id")){String body=Json.str(vote,"name")+" · "+measureName(Json.str(vote,"action"))+(vote.has("minutes")&&vote.get("minutes").getAsInt()>0?" · "+vote.get("minutes").getAsInt()+Client.text("ui.min_2190bd44"):"")+"\n"+Json.str(vote,"reason")+Client.text("ui.for_23c4db47")+vote.get("yes")+Client.text("ui.against_7e14d2b0")+vote.get("no")+Client.text("ui.eligible_voters_30223038")+vote.get("eligibleCount")+"\n"+(vote.has("outcome")?dev.abros.rivet.core.LocalizedText.render(vote,"outcome"):Json.str(vote,"status").equals("open")?Client.text("ui.vote_in_progress_f5fb378c"):Client.text("ui.applying_result_06c9c848"));for(String paragraph:body.split("\n"))for(var line:font.getSplitter().splitLines(paragraph,w-20,net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());
    if(Json.str(vote,"status").equals("open")){
     boolean allowed=vote.get("canVote").getAsBoolean()&&(!busy||reading);
     var yes=addRenderableWidget(UiActions.button(Component.literal(vote.has("myVote")&&vote.get("myVote").getAsBoolean()?Client.text("ui.for_03378c0f"):Client.text("ui.for_a587717b")),UiActions.Tone.NORMAL,"",b->ballot(true)).bounds(left,viewBottom()-76,w/2-3,20).build());yes.active=allowed;
     var no=addRenderableWidget(UiActions.button(Component.literal(vote.has("myVote")&&!vote.get("myVote").getAsBoolean()?Client.text("ui.against_8b52fe41"):Client.text("ui.against_37849f35")),UiActions.Tone.NORMAL,"",b->ballot(false)).bounds(left+w/2+3,viewBottom()-76,w/2-3,20).build());no.active=allowed;
     if(ServerMenuClient.admin())addRenderableWidget(UiActions.button(Client.tr("ui.cancel_with_reason_613af8a2"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new CancelForm(this))).bounds(left,viewBottom()-50,w,20).build()).active=!busy||reading;
    }else addCreate(left,w);
   }else{lines.add(Client.text("ui.no_rule_violation_vote_in_progress_0ab7ee60"));addCreate(left,w);}
   scrollArea(lines.size(),new dev.abros.rivet.core.NativeLayout.Box(left,viewTop()+30,Math.max(0,w),Math.max(0,(viewBottom()-106)-(viewTop()+30))),14);
  }
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(creating?left+w/2+3:left,creating?top+184:viewBottom()-24,creating?w/2-3:w,20),this::addRenderableWidget,this::onClose);if(!started){started=true;if(!creating)send("view",new JsonObject());}
 }
 private void addCreate(int x,int w){if(enabled())addRenderableWidget(UiActions.button(Client.tr("ui.new_poll_e510ccd2"),UiActions.Tone.NORMAL,"",b->{creating=true;resetScroll();rebuildWidgets();}).bounds(x,viewBottom()-50,w,20).build()).active=!busy||reading;}
 private void confirm(){if(target==null||reason.isBlank()){status=Client.text("ui.select_a_player_and_specify_a_b577a28f");return;}var j=new JsonObject();j.addProperty("target",Json.str(target,"uuid"));j.addProperty("measure",measure);j.addProperty("reason",reason);if(!measure.equals("kick")){try{int value=Integer.parseInt(minutes);if(value<1||value>1440)throw new NumberFormatException();if(customDuration())j.addProperty("minutes",value);}catch(NumberFormatException ex){status=Client.text("ui.specify_a_duration_from_1_to_d25de831");return;}}minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)send("start",j);},Client.tr("ui.start_vote_6d674255"),Component.literal(Json.str(target,"name")+" · "+measureName(measure)+(measure.equals("kick")?"":" · "+minutes+Client.text("ui.min_2190bd44"))+"\n"+reason)));}
 private void ballot(boolean yes){var j=new JsonObject();j.addProperty("id",Json.str(vote,"id"));j.addProperty("yes",yes);send("vote",j);}
 private void send(String op,JsonObject j){if(busy)return;busy=true;reading=op.equals("view");sent=lastRefresh=System.currentTimeMillis();request=UUID.randomUUID().toString();j.addProperty("action","moderationVote");j.addProperty("op",op);j.addProperty("request",request);if(op.equals("start")){j.addProperty("operationId",UUID.randomUUID().toString());j.addProperty("issuedAt",sent);}ServerMenuClient.request(j);status="";if(!reading)rebuildWidgets();}
 @Override public void receiveCommunity(JsonObject j){if(!request.equals(Json.opt(j,"request","")))return;busy=false;if(j.has("error")){status=Json.opt(j,"text",Client.text("ui.action_failed_b5d97a28"));rebuildWidgets();return;}if(j.has("vote")){boolean changed=!vote.equals(j.getAsJsonObject("vote"))||creating;vote=j.getAsJsonObject("vote");creating=false;if(changed||!reading)rebuildWidgets();}}
 @Override public void tick(){String configuration=config().toString();if(creating&&!adapterConfiguration.equals(configuration)){adapterConfiguration=configuration;rebuildWidgets();}if(!ServerMenuClient.available()){onClose();return;}if(busy&&System.currentTimeMillis()-sent>15000){busy=false;status=Client.text("ui.no_response_close_and_reopen_the_35cca1c2");lastRefresh=System.currentTimeMillis();rebuildWidgets();}if(!creating&&!busy&&System.currentTimeMillis()-lastRefresh>(ServerMenuClient.supports("moderation-vote-status")?30000:5000))send("view",new JsonObject());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){int w=Math.min(456,width-12),left=(width-w)/2,top=creating?formTop()-8:viewTop()-4,bottom=creating?Math.min(height-8,formTop()+214):viewBottom()+4;UiDialog.surface(g,left,top,w,bottom-top);}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int w=Math.min(440,width-28),left=(width-w)/2;UiHeading.dialog(g,font,title,left,creating?formTop()-10:viewTop(),w);if(creating){if(!measure.equals("kick"))Ui.text(g,font,Client.text("ui.punishment_duration_minutes_1_1440_84ea4da9"),left,formTop()+105,UiPalette.color(0xBAC7D2));}else{for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)Ui.text(g,font,lines.get(i),left+10,viewTop()+30+(i-firstRow)*14,UiPalette.color(0xEEEEEE));if(status.isEmpty()&&vote.has("endsAt")&&Json.opt(vote,"status","").equals("open"))Ui.text(g,font,Client.text("ui.remaining_f341f75d")+Math.max(0,(vote.get("endsAt").getAsLong()-System.currentTimeMillis()+999)/1000)+Client.text("ui.s_488d98ca"),left,viewBottom()-102,UiPalette.color(0xE2BE75));}Ui.status(g,font,status,left,creating?formTop()+148:viewBottom()-102,w,creating?formTop()+178:viewBottom()-80);});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
 private static final class CancelForm extends Screen{
  private int top(){return UiDialog.top(height,180);}
 private int bottom(){return height-top();}
 private final ModerationVoteScreen parent;private EditBox reason;
  CancelForm(ModerationVoteScreen parent){super(Client.tr("ui.cancellation_reason_f8f1caba"));this.parent=parent;}
  @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(400,width-24),top(),bottom());}
 @Override protected void init(){String draft=reason==null?"":reason.getValue();int w=Math.min(400,width-24),x=(width-w)/2;reason=addRenderableWidget(UiFields.text(font,x,top()+42,w,20,title));reason.setMaxLength(300);reason.setValue(draft);addRenderableWidget(UiActions.button(Client.tr("ui.cancel_vote_7f11d299"),UiActions.Tone.NORMAL,"",b->{if(reason.getValue().isBlank())return;var j=new JsonObject();j.addProperty("id",Json.str(parent.vote,"id"));j.addProperty("reason",reason.getValue());minecraft.setScreen(parent);parent.send("cancel",j);}).bounds(x,top()+72,w,20).build());UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,bottom()-28,w,20),this::addRenderableWidget,this::onClose);setInitialFocus(reason);}
  @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,(width-Math.min(400,width-24))/2,top(),Math.min(400,width-24));});}
  @Override public void onClose(){minecraft.setScreen(parent);}
 }
}
