package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import static dev.abros.rivet.client.CommunityScreen.*;
/** board entry layout and contextual actions. */
final class BoardSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();
                if(j.has("trade")){var t=j.getAsJsonObject("trade");host.text(switch(Json.str(t,"type")){case "buy"->Client.text("ui.buying_d5030427");case "sell"->Client.text("ui.selling_1105ce7b");default->Client.text("ui.trading_9eb8da1b");});host.text(Json.str(t,"item")+" × "+t.get("quantity").getAsInt());host.text(Client.text("ui.terms_ec217f23")+Json.str(t,"terms"));}
                host.text(Client.text("ui.valid_until_7b28e452")+local(j.get("endsAt").getAsLong()));if(!Json.opt(j,"category","").isEmpty())host.text(Client.text("ui.category_b351c87b")+Json.str(j,"category"));
                var responses=j.getAsJsonObject("responses");if(can(j,"respond"))host.action(Client.text("ui.respond_4236aefe"),()->host.form(Client.text("ui.response_ef04ab37"),"respond",List.of(new CommunityScreen.Field("text",Client.text("ui.message_dc72346a"),1000)),new JsonObject()));
                for(var entry:responses.entrySet()){int card=host.beginCard();String target=entry.getKey();var reply=entry.getValue().getAsJsonObject();host.text(Json.str(reply,"name")+" · "+status(Json.str(reply,"status"))+"\n"+Json.str(reply,"text"));if(!Json.opt(reply,"answer","").isEmpty())host.text(Client.text("ui.reply_f439b06a")+Json.str(reply,"answer"));if(can(j,"respondDecision"))host.action(Client.text("ui.reply_7bac10ce")+Json.str(reply,"name"),()->{var decisions=List.of("accepted","declined","pending");net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.reply_to_response_a1134e6c"),decisions.stream().map(CommunityScreen::status).toList(),i->{var body=new JsonObject();body.addProperty("target",target);body.addProperty("decision",decisions.get(i));host.form(Client.text("ui.reply_to_response_a1134e6c"),"respondDecision",List.of(new CommunityScreen.Field("text",Client.text("ui.reply_e9d7bdd8"),1000)),body);}));});host.endCard(card);}
                if(can(j,"withdrawResponse")){var own=responses.getAsJsonObject(me());boolean accepted=own!=null&&Json.opt(own,"status","").equals("accepted");host.secondary(Client.text("ui.withdraw_my_response_91aeefad"),()->{var body=new JsonObject();body.addProperty("confirmAccepted",accepted);host.confirm(accepted?Client.text("ui.response_accepted_cancel_and_notify_the_9d081052"):Client.text("ui.withdraw_response_4046ccfd"),"withdrawResponse",body);});}
                if(can(j,"close")){host.secondary(Client.text("ui.close_notice_2057f09c"),()->host.confirm(Client.text("ui.close_notice_9715c8ce"),"close",new JsonObject()));host.secondary(Client.text("ui.mark_as_completed_3df4881b"),()->host.confirm(Client.text("ui.mark_as_completed_84562147"),"complete",new JsonObject()));}

 }
}
