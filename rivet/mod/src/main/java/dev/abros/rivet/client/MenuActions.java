package dev.abros.rivet.client;

import com.google.gson.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Searchable entry points use the same availability as navigation, including at activation. */
final class MenuActions {
 static Map<String,String> available(){
  var actions=new LinkedHashMap<String,String>();
  for(String section:MenuSidebar.sections())actions.put(section,CommunityScreen.name(section));
  if(ServerMenuClient.module("notifications")&&(!ServerMenuClient.state.has("features")||ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive("notifications"))))actions.put("notifications",CommunityScreen.name("notifications"));
  if(SkinClient.available()&&ServerMenuClient.state.has("features")&&ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive("skins")))actions.put("skins",Client.text("wardrobe.title"));
  if(net.minecraft.client.Minecraft.getInstance().getConnection()!=null&&net.minecraft.client.Minecraft.getInstance().getConnection().hasChannel(dev.abros.rivet.network.TradeWire.State.TYPE))actions.put("trades",Client.text("trade.list"));
  return actions;
 }
 static JsonArray search(String query){
  var rows=new JsonArray();String needle=query.strip().toLowerCase(Locale.ROOT);if(needle.length()<2)return rows;
  available().forEach((key,label)->{if(label.toLowerCase(Locale.ROOT).contains(needle)||key.contains(needle)){var row=new JsonObject();row.addProperty("section","actions");row.addProperty("id",key);row.addProperty("title",label);rows.add(row);}});return rows;
 }
 static void open(Screen parent,String key){if(!available().containsKey(key))return;if(key.equals("trades"))TradeClient.command("rivet trades");else if(key.equals("skins"))SkinsScreen.open(parent);else UiNavigation.open(parent,key);}
 private MenuActions(){}
}
