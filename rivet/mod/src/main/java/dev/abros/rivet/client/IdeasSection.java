package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import static dev.abros.rivet.client.CommunityScreen.*;
/** ideas entry layout and contextual actions. */
final class IdeasSection {
 static void render(CommunityScreen host,JsonObject j){boolean manage=j.get("manage").getAsBoolean();host.text(Client.text("ui.supporters_209905f6")+j.get("supportersCount").getAsInt());if(can(j,"support"))host.action(j.getAsJsonObject("supporters").has(me())?Client.text("ui.withdraw_support_f971b2db"):Client.text("ui.support_5438df47"),()->host.send("support",new JsonObject()));host.text(Client.text("ui.status_156979c7")+status(Json.str(j,"status")));if(!Json.opt(j,"answer","").isBlank()){int card=host.beginCard();host.text(Client.text("ui.staff_reply_e9f5e193")+Json.str(j,"answer"));host.endCard(card);}if(can(j,"status"))host.secondary(Client.text("ui.change_status_and_reply_d6933535"),()->{var states=List.of("new","discussion","planned","done","declined");net.minecraft.client.Minecraft.getInstance().setScreen(new ChoicePopup(host.surface(),Client.text("ui.suggestion_decision_e21fe942"),states.stream().map(CommunityScreen::status).toList(),i->{var body=new JsonObject();body.addProperty("status",states.get(i));host.form(Client.text("ui.official_response_f8d050fc"),"status",List.of(new CommunityScreen.Field("text",Client.text("ui.explanation_dba444b5"),1000)),body);}));});
 }
}
