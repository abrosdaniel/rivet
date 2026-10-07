package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
/** Data is fetched while TAB is visible; request epochs and connection resets discard stale replies. */
public final class SocialClient {
 static final Map<UUID,JsonObject> players=new HashMap<>();private static final Map<UUID,JsonObject> staging=new HashMap<>();
 static List<String> chatGroups=List.of();static boolean chatGroupsReady;
 public static UUID chatHead(net.minecraft.util.FormattedCharSequence sequence){return ClientChat.chatHead(sequence);}
 public static void drawChatHead(net.minecraft.client.gui.GuiGraphics g,net.minecraft.client.gui.Font font,net.minecraft.util.FormattedCharSequence sequence,int x,int y){ClientChat.drawChatHead(g,font,sequence,x,y);}
 public static void drawHead(net.minecraft.client.gui.GuiGraphics g,UUID id,int x,int y){ClientChat.drawHead(g,id,x,y);}
 private static boolean tabOpen,chatOpen;
 private static Object connection;private static long next,pendingAt;private static String pending="";private static int page;
 static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->tick());NeoForge.EVENT_BUS.addListener(SocialClient::layer);PlayerNameplates.install();NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent e)->{if(scrollTab(e.getScrollDeltaY(),visible()))e.setCanceled(true);});}
 static boolean scrollTab(double amount,boolean shown){if(!canScrollTab(shown,Minecraft.getInstance().screen!=null))return false;RivetTab.scroll(amount);return true;}
 static boolean canScrollTab(boolean shown,boolean screenOpen){return shown&&!screenOpen&&SocialSettings.INSTANCE.tab();}
 static boolean available(){return ServerMenuClient.available()&&ServerMenuClient.supports("social-display");}
 static boolean visible(){var mc=Minecraft.getInstance();return available()&&mc.player!=null&&mc.options.keyPlayerList.isDown();}
 private static void tick(){var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){connection=mc.getConnection();players.clear();chatGroups=List.of();chatGroupsReady=false;staging.clear();pending="";next=0;page=0;tabOpen=chatOpen=false;RivetTab.reset();}if(!available())return;long now=System.currentTimeMillis();boolean shown=visible()||mc.screen instanceof HudInteractionScreen cursor&&cursor.tabVisible();if(shown&&!tabOpen)next=now+300;tabOpen=shown;boolean chatting=SocialSettings.chatEnabled()&&mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen;if(chatting&&!chatOpen)next=Math.min(next,now+300);chatOpen=chatting;if(!pending.isEmpty()&&now-pendingAt>8000){pending="";page=0;staging.clear();next=now+3000;}if((visible()||mc.screen instanceof HudInteractionScreen cursor&&cursor.tabVisible()||SocialSettings.chatEnabled()&&mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen||players.isEmpty()||PlayerNameplates.enabled()&&mc.level!=null&&mc.level.players().size()>1)&&pending.isEmpty()&&now>=next){var j=new JsonObject();pending=UUID.randomUUID().toString();pendingAt=now;j.addProperty("request",pending);j.addProperty("action","social");j.addProperty("page",page);ServerMenuClient.requestBackground(j);next=now+(PlayerNameplates.enabled()&&mc.level!=null&&mc.level.players().size()>1?5000:visible()||mc.screen instanceof HudInteractionScreen cursor&&cursor.tabVisible()?5000:mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen?2000:30000);}}
 static boolean receive(JsonObject j){if(!Json.opt(j,"kind","").equals("social"))return false;if(!Json.opt(j,"request","").equals(pending))return true;pending="";if(j.has("chatGroups")){var previousGroups=chatGroups;boolean previousReady=chatGroupsReady;chatGroups=j.getAsJsonArray("chatGroups").asList().stream().map(com.google.gson.JsonElement::getAsString).toList();chatGroupsReady=j.has("chatGroupsReady")&&j.get("chatGroupsReady").getAsBoolean();if((!previousGroups.equals(chatGroups)||previousReady!=chatGroupsReady)&&Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen chat)((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$suggestions().updateCommandInfo();}for(var e:j.getAsJsonArray("players")){var row=e.getAsJsonObject();try{staging.put(UUID.fromString(Json.str(row,"id")),row.deepCopy());}catch(IllegalArgumentException ignored){}}if(j.get("more").getAsBoolean()&&page<100){page++;next=System.currentTimeMillis()+300;}else{players.clear();players.putAll(staging);staging.clear();page=0;}return true;}
 private static void layer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre e){if(e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.TAB_LIST)&&SocialSettings.INSTANCE.tab()&&visible()&&Minecraft.getInstance().screen==null){e.setCanceled(true);RivetTab.draw(e.getGuiGraphics(),-1,-1);}}

}
