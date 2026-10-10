package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;
final class PlayerActionsScreen extends ScrollScreen {
 private record Action(String label,Runnable run,List<Action> pair){Action(String label,Runnable run){this(label,run,List.of());}}
 private boolean profileActions;private boolean moderationOnly;private final Screen parent;private final JsonObject player;private JsonArray actions;private boolean resetPermission;
 private int panelTop=6,panelBottom;
 private int panelWidth(){return Math.min(340,width-40);}
 private int panelLeft(){return (width-panelWidth())/2;}
 private int rowsTop(){return panelTop+8+UiPlayerIdentity.height(font,player,panelWidth())+(moderationOnly?8:34);}
 private Screen surface(){return this;}
 private <T extends AbstractWidget>T add(T widget){return addRenderableWidget(widget);}
 private String reason="",minutes="10",status="",banMinutes="0";private final List<Action> rows=new ArrayList<>();
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions){super(PlayerText.name(player));this.parent=parent;this.player=player;this.actions=actions;}
 PlayerActionsScreen(Screen parent,JsonObject player,JsonArray actions,boolean moderationOnly){this(parent,player,actions);this.moderationOnly=moderationOnly;}
 private void pair(Action a,Action b){rows.add(new Action("actions",null,List.of(a,b)));}
 @Override protected void init(){rows.clear();resetPermission=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(ServerMenuClient.state.has("actions"))actions=ServerMenuClient.state.getAsJsonArray("actions").deepCopy();boolean online=player.has("online")&&player.get("online").getAsBoolean();boolean self=Json.str(player,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""));
  if(moderationOnly){if(ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools"))rows.add(new Action(Client.text("ui.permissions_and_moderation_history_e05c1c35"),()->minecraft.setScreen(new PlayerAdministrationScreen(surface(),player))));if(AuthClient.available()&&resetPermission)rows.add(new Action(Client.text("ui.reset_password_f6537c08"),()->AuthAccountScreen.invite(surface(),Json.str(player,"name"))));}
  else{
   if(!profileActions){
   var bio=PlayerStatisticsText.biography(player,panelWidth());for(int i=0;i<bio.size();i+=2){var paragraph=new ArrayList<Action>();for(int n=i;n<Math.min(i+2,bio.size());n++)paragraph.add(new Action(bio.get(n),null));rows.add(new Action("bio",null,paragraph));}
   var stats=PlayerStatisticsText.statistics(player);for(int i=0;i<stats.size();i+=2){var tiles=new ArrayList<Action>();for(int n=i;n<Math.min(i+2,stats.size());n++)tiles.add(new Action(stats.get(n),null));rows.add(new Action("stats",null,tiles));}
   }else{
   rows.add(new Action(Client.text("ui.stat_communication_and_groups_d89172ed"),null));
   if(!self){var social=new ArrayList<Action>();if(online&&actions.contains(new JsonPrimitive("tell")))social.add(new Action(Client.text("ui.write_bda589de"),()->openChat(surface(),Json.str(player,"name"))));if(ServerMenuClient.module("groups"))social.add(new Action(Client.text("ui.invite_e50b37ed"),()->CommunityScreen.invite(surface(),player)));if(social.size()==2)pair(social.get(0),social.get(1));else rows.addAll(social);}
   if(ServerMenuClient.module("groups"))rows.add(new Action(Client.text("ui.player_groups_b296fea9"),()->CommunityScreen.groupsFor(surface(),Json.str(player,"uuid"))));
   if(!self)rows.add(new Action(Client.text("ui.stat_security_5cee1483"),null));
   if(!self&&!ServerMenuClient.module("reports"))rows.add(new Action(Client.text("ui.communication_1e034481"),()->PersonalProfileScreen.ignores(surface(),Json.str(player,"name"))));
   if(!self&&ServerMenuClient.module("reports"))pair(new Action(Client.text("ui.report_5405a3bc"),()->minecraft.setScreen(ReportScreen.player(surface(),Json.str(player,"name")))),new Action(Client.text("ui.communication_1e034481"),()->PersonalProfileScreen.ignores(surface(),Json.str(player,"name"))));
   if(!self&&ModerationVoteScreen.enabled())rows.add(new Action(Client.text("ui.report_a_violation_for_voting_ad38d9e1"),()->minecraft.setScreen(new ModerationVoteScreen(surface(),player))));
   if(ServerMenuClient.module("reports")&&(ServerMenuClient.admin()||actions.size()>1)||resetPermission)rows.add(new Action(Client.text("ui.moderation_12f343d1"),()->minecraft.setScreen(new PlayerActionsScreen(surface(),player,actions,true))));
  }
  }
  var available=new ArrayList<String>();for(var entry:actions){if(!moderationOnly)continue;String action=entry.getAsString();if(action.equals("tell"))continue;if(online||Set.of("ban","pardon").contains(action))available.add(action);}
  int reasonRow=-1,minutesRow=-1,banRow=-1;if(!available.isEmpty()){reasonRow=rows.size();rows.add(new Action("reason",null));if(available.contains("vmute")){minutesRow=rows.size();rows.add(new Action("minutes",null));}if(available.contains("ban")){banRow=rows.size();rows.add(new Action("banMinutes",null));}for(int n=0;n<available.size();n+=2){var pair=new ArrayList<Action>();for(int k=n;k<Math.min(n+2,available.size());k++){String action=available.get(k);pair.add(new Action(action.equals("warn")?Client.text("ui.warn_bdbb0566"):Client.tr("server.command."+action).getString(),()->run(action)));}rows.add(new Action("actions",null,pair));}}
  int header=8+UiPlayerIdentity.height(font,player,panelWidth())+(moderationOnly?8:34);
  int panelHeight=Math.min(height-16,Math.max(220,header+rows.size()*28+48));panelTop=(height-panelHeight)/2;panelBottom=panelTop+panelHeight;
  int w=panelWidth(),x=panelLeft();scrollArea(rows.size(),new dev.abros.rivet.core.NativeLayout.Box(x,rowsTop(),Math.max(0,w),Math.max(0,panelBottom-rowsTop()-40)),28);
  w=scrollLayout().content().width();
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){int y=rowsTop()+(i-firstRow)*28;if(i==reasonRow){var edit=add(UiFields.text(font,x,y,w,20,Client.tr("server.reason")));edit.setMaxLength(300);edit.setHint(Client.tr("server.reason"));edit.setValue(reason);edit.setResponder(v->reason=v);}else if(i==banRow){add(UiActions.button(Component.literal(Client.text("ui.ban_duration_5ebec3c8")+(banMinutes.equals("0")?Client.text("ui.indefinite_46c576ae"):banMinutes+Client.text("ui.min_04530f57"))+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.ban_duration_0aee044b"),List.of(Client.text("ui.indefinite_519bb302"),Client.text("ui.30_minutes_f3791edb"),Client.text("ui.1_hour_e5f1687b"),Client.text("ui.1_day_3cd409a4"),Client.text("ui.7_days_e65da6ce")),n->{banMinutes=List.of("0","30","60","1440","10080").get(n);rebuildWidgets();},b))).bounds(x,y,w,20).build());}else if(i==minutesRow){var edit=add(UiFields.text(font,x,y,70,20,Client.tr("server.muteMinutes")));edit.setMaxLength(5);edit.setFilter(v->v.matches("[0-9]*"));edit.setValue(minutes);edit.setResponder(v->minutes=v);}else if(!rows.get(i).label.startsWith("stat:")&&!rows.get(i).label.equals("stats")&&!rows.get(i).label.equals("bio")){var action=rows.get(i);var buttons=action.pair.isEmpty()?List.of(action):action.pair;int bw=buttons.size()>1?(w-6*(buttons.size()-1))/buttons.size():w;for(int n=0;n<buttons.size();n++){var button=buttons.get(n);add(UiActions.button(Component.literal(button.label),UiActions.Tone.NORMAL,"",b->button.run.run()).bounds(x+n*(bw+6),y,bw,20).build());}}}
  if(!moderationOnly){UiTabs.build(surface(),font,new dev.abros.rivet.core.NativeLayout.Box(x,rowsTop()-28,w,20),List.of(Client.text("server.tab.profile"),Client.text("ui.actions_9978ac34")),profileActions?1:0,this::add,n->{profileActions=n==1;resetScroll();rebuildWidgets();},true);}
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(panelLeft(),panelBottom-26,w,20),this::add,this::onClose);
 }
 static void openChat(Screen parent,String name){
  var mc=net.minecraft.client.Minecraft.getInstance();
  if(mc.player==null||mc.getConnection()==null){mc.setScreen(new TextScreen(parent,Client.tr("ui.private_message_a27a85da"),Client.text("ui.private_messages_are_available_after_connecting_e35dc338")));return;}
  mc.setScreen(new ChatScreen("/tell "+name+" "));
 }
 private void run(String action){var j=new JsonObject();j.addProperty("action","moderate");j.addProperty("operation",action);j.addProperty("target",Json.str(player,"uuid"));j.addProperty("reason",reason);
  if((action.equals("kick")||action.equals("ban")||action.equals("warn"))&&reason.isBlank()){status=Client.tr("server.reason").getString();return;}
  if(action.equals("vmute"))try{int duration=Integer.parseInt(minutes);if(duration<1||duration>10080)throw new NumberFormatException();j.addProperty("minutes",duration);}catch(NumberFormatException ex){status=Client.tr("server.invalidnumber").getString();return;}
  if(action.equals("ban"))j.addProperty("minutes",Integer.parseInt(banMinutes));
  Component summary=title.copy().append("\n").append(reason);if(action.equals("ban"))summary=summary.copy().append(" · "+(banMinutes.equals("0")?Client.text("ui.indefinite_46c576ae"):banMinutes+Client.text("ui.min_04530f57")));if(action.equals("vmute"))summary=summary.copy().append(" · "+minutes+" min");
  minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(surface());if(yes){ServerMenuClient.result="";ServerMenuClient.request(j);}},action.equals("warn")?Client.tr("ui.warn_bdbb0566"):Client.tr("server.command."+action),summary));
 }
 @Override public void tick(){if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}boolean reset=ServerMenuClient.state.has("authReset")&&ServerMenuClient.state.get("authReset").getAsBoolean();if(reset!=resetPermission||ServerMenuClient.state.has("actions")&&!actions.equals(ServerMenuClient.state.getAsJsonArray("actions")))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,panelWidth(),panelTop,panelBottom);}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);
   UiPlayerIdentity.draw(g,font,player,panelLeft(),panelTop+8,panelWidth());
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("minutes"))Ui.text(g,font,Client.tr("server.muteMinutes"),panelLeft()+78,rowsTop()+6+(i-firstRow)*28,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.startsWith("stat:"))Ui.text(g,font,font.plainSubstrByWidth(rows.get(i).label.substring(5),panelWidth()),panelLeft(),rowsTop()+6+(i-firstRow)*28,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
   for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("bio")){int yy=rowsTop()+4+(i-firstRow)*28;for(int n=0;n<rows.get(i).pair.size();n++)Ui.text(g,font,rows.get(i).pair.get(n).label,panelLeft(),yy+n*12,UiPalette.color(0xBAC7D2),false);}
   int statX=panelLeft(),statW=panelWidth();for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++)if(rows.get(i).label.equals("stats")){int tileY=rowsTop()+(i-firstRow)*28;var tiles=rows.get(i).pair;int tw=(statW-6)/2;for(int n=0;n<tiles.size();n++){int tx=statX+n*(tw+6);String[] parts=tiles.get(n).label.split(": ",2);g.fill(tx,tileY,tx+tw,tileY+26,UiKit.surface(UiKit.Surface.PANEL));Ui.text(g,font,font.plainSubstrByWidth(parts[0],tw-10),tx+5,tileY+3,UiKit.muted(),false);if(parts.length>1)Ui.text(g,font,font.plainSubstrByWidth(parts[1],tw-10),tx+5,tileY+13,UiKit.text(),false);}}
   Ui.status(g,font,status.isEmpty()?ServerMenuClient.result:status,panelLeft(),panelBottom-39,panelWidth(),panelBottom-28);
   g.flush();
  });
 }
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){if(parent instanceof FeatureListScreen screen)screen.invalidate();UiNavigation.back(this,parent);}
}
