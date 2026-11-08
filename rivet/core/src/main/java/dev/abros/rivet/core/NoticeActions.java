package dev.abros.rivet.core;
import com.google.gson.*;
final class NoticeActions {
 static JsonObject apply(PgDatabase db,CommunityStore store,CommunityStore.Actor actor,JsonObject input)throws Exception {
  long id=input.get("id").getAsLong();JsonObject notice=new NotificationStore(db).owned(actor.id(),id);
  String section=Json.opt(notice,"routeSection",Json.opt(notice,"section","")),target=Json.opt(notice,"routeId",Json.opt(notice,"target",""));store.modules().require(section);String event=Json.opt(notice,"event","");boolean invitation=section.equals("groups")&&event.equals("invite");boolean join=section.equals("events")&&java.util.Set.of("plusEventInvite","reminder").contains(event);if(!invitation&&!join)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.quick_action_unavailable_e3aeeebd"));var document=store.get(target,true);if(join&&(!input.has("confirmConflicts")||!input.get("confirmConflicts").getAsBoolean())){var conflicts=EventConflicts.read(db,store,actor,document);if(!conflicts.isEmpty()){var out=new JsonObject();out.addProperty("kind","community");out.addProperty("request",Json.opt(input,"request",""));out.add("conflicts",conflicts);return out;}}var command=new JsonObject();command.addProperty("id",target);command.addProperty("section",section);command.addProperty("op",invitation?"invitation":"join");command.addProperty("accept",true);command.add("revision",document.get("revision"));store.execute(actor,command,Json.str(command,"op"),section);store.readNotice(actor.id(),Long.toString(id),true);var out=new JsonObject();out.addProperty("kind","community");out.addProperty("request",Json.opt(input,"request",""));out.addProperty("noticeActionApplied",true);return out;
 }
 private NoticeActions(){}
}
