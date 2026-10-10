package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;
final class LocationActions {
 static void render(CommunityScreen screen,JsonObject record){
  if(!record.has("location"))return;var place=CommunityLocation.read(record.getAsJsonObject("location"));screen.text(Client.text("ui.location_41fb2029")+place.name());screen.text(place.coordinates());
  var actions=new java.util.ArrayList<CommunityScreen.Row>();
  actions.add(new CommunityScreen.Row(Client.text("map.navigate"),()->{DirectionCue.start(place);Minecraft.getInstance().setScreen(null);}));
  actions.add(new CommunityScreen.Row(Client.text("ui.coordinates_bd036452"),()->Minecraft.getInstance().keyboardHandler.setClipboard(place.coordinates())));
  if(WorldMapClient.allowed()){
   actions.add(new CommunityScreen.Row(Client.text("ui.on_the_map_ba867271"),()->ClientMap.openLocation(screen.surface(),place,false)));
   actions.add(new CommunityScreen.Row(Client.text("ui.save_waypoint_ba8ec24a"),()->ClientMap.openLocation(screen.surface(),place,true)));
  }
  screen.actionRow(actions);
 }
}
