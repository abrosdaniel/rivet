package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ServerMenuClient {
    static JsonObject state=new JsonObject();
    private static final MenuTransport<Screen> transport=new MenuTransport<>();
    static JsonObject cached(JsonObject request){return transport.cached(request,System.currentTimeMillis());}
    private static String requiredVersion="";
    static java.util.function.Consumer<JsonObject> previewTransport;
    static String result="";
    private static final KeyMapping SKINS=new KeyMapping("key.rivet.quickSkins",GLFW.GLFW_KEY_UNKNOWN,"key.categories.rivet");
    private static final KeyMapping OPEN=new KeyMapping("key.rivet.menu",GLFW.GLFW_KEY_F8,"key.categories.rivet");
    static ServerData lastServer;
    private static Object connection;
    private static long lastPopup;private static final MenuStateBootstrap stateBootstrap=new MenuStateBootstrap();
    static JsonObject moderationVote=new JsonObject();

    private static boolean lastServerSupported;private static String subscription="";private static long subscriptionAt;
    private static boolean notices=true,sound=true,restartNotices=true;
    public static void install(IEventBus bus){
        bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->{e.register(OPEN);e.register(SKINS);});
        NeoForge.EVENT_BUS.addListener(ServerMenuClient::tick);
        NeoForge.EVENT_BUS.addListener(ServerMenuClient::screen);
        Protocol.featureState=ServerMenuClient::receive;
        Protocol.incompatible=version->requiredVersion=version;
        try{var p=Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/menu-settings.json");if(java.nio.file.Files.exists(p)){var j=Json.read(p);notices=j.get("notices").getAsBoolean();sound=j.get("sound").getAsBoolean();restartNotices=!j.has("restartNotices")||j.get("restartNotices").getAsBoolean();}}catch(Exception ignored){}
    }
    static boolean available(){if(previewTransport!=null)return true;var c=Minecraft.getInstance().getConnection();return !Minecraft.getInstance().hasSingleplayerServer()&&c!=null&&Protocol.supportedFeatures.contains("menu")&&c.hasChannel(Protocol.FeatureRequest.TYPE);}
    static void request(JsonObject packet){
        long now=System.currentTimeMillis();if(previewTransport!=null){if(MenuRequests.mutation(packet))transport.clearCache();previewTransport.accept(MenuCommands.prepare(packet,now));return;}
        if(available()&&!transport.enqueue(packet,requestOwner(Minecraft.getInstance().screen),false,now))result=Client.text("ui.too_many_requests_wait_for_the_861728e7");
    }
    private static Screen requestOwner(Screen screen){while(screen instanceof ChoicePopup popup)screen=popup.parentScreen();return screen;}

    static void cancelReads(Screen owner){transport.cancel(owner);}
    static void requestBackground(JsonObject packet){if(previewTransport!=null){previewTransport.accept(packet.deepCopy());return;}if(available())transport.enqueue(packet,null,true,System.currentTimeMillis());}
    private static JsonObject stateRequest(){var j=new JsonObject();j.addProperty("action","state");j.addProperty("menuProtocol",MenuProtocol.VERSION);var info=new JsonObject();info.addProperty("coreVersion",dev.abros.rivet.Rivet.VERSION);info.addProperty("packHash",Client.hub==null?"":Client.hub.activeHash());j.add("client",info);return j;
    }
    static void request(String action){if(action.equals("state")){request(stateRequest());return;}var j=new JsonObject();j.addProperty("action",action);request(j);}

    static void open(){if(!available())return;request("state");Minecraft.getInstance().setScreen(new CommunityScreen(null,"home",""));}
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){CompatibilityClient.tick();
        var mc=Minecraft.getInstance();if(previewTransport!=null){RivetHud.tick();return;}Object current=mc.getConnection();
        if(current!=connection){connection=current;state=new JsonObject();moderationVote=new JsonObject();result="";Protocol.profile=new JsonObject();stateBootstrap.reset();lastPopup=0;UiNavigation.clear();subscription="";subscriptionAt=0;transport.clear();PerformanceMetrics.clear();}
        RivetHud.tick();
        if(stateBootstrap.requestDue(System.currentTimeMillis(),available(),mc.player!=null))requestBackground(stateRequest());
        if(available())for(var packet:transport.dispatch(System.currentTimeMillis(),requestOwner(mc.screen)))PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(packet)));

        if(available()){
            String topic=requestOwner(mc.screen) instanceof CommunityScreen menu?menu.section+"|"+menu.itemId:requestOwner(mc.screen) instanceof TaskScreen tasks?tasks.subscription():mc.screen instanceof NotificationPopup?"notifications|":mc.screen instanceof FeatureListScreen list?list.kind+"|":"|";
            long now=System.currentTimeMillis();if(!topic.equals(subscription)||now-subscriptionAt>10000){subscription=topic;subscriptionAt=now;var parts=topic.split("\\|",-1);var sub=new JsonObject();sub.addProperty("action","subscribe");sub.addProperty("section",parts[0]);sub.addProperty("id",parts[1]);request(sub);}
        }
        if(current!=null){lastServerSupported=available();lastServer=mc.getCurrentServer();}
        if(!available()&&(mc.screen instanceof ServerMenuScreen||mc.screen instanceof ReportScreen))mc.setScreen(null);
        while(SKINS.consumeClick())if(mc.player!=null&&(mc.screen==null||mc.screen instanceof CommunityScreen||mc.screen instanceof FeatureListScreen||mc.screen instanceof TaskScreen)&&SkinClient.available())mc.setScreen(new QuickSkinsScreen(mc.screen));
        while(OPEN.consumeClick())if(mc.player!=null&&mc.screen==null)open();

    }
    static void receive(JsonObject j){
        if(CompatibilityClient.receive(j))return;
        var mc=Minecraft.getInstance();if(!available())return;String kind=Json.opt(j,"kind","");transport.receive(j,System.currentTimeMillis());if(ClientChat.receive(j)||SocialClient.receive(j)||ClientMap.receive(j)||RivetHud.receive(j))return;
        if(kind.equals("incompatible")||kind.equals("state")&&(!j.has("menuProtocol")||j.get("menuProtocol").getAsInt()!=MenuProtocol.VERSION)){stateBootstrap.confirm();result=kind.equals("incompatible")?Json.opt(j,"text",Client.text("ui.this_server_s_menu_is_temporarily_43cf0ca3")):Client.text("ui.this_server_s_menu_is_temporarily_43cf0ca3");if(mc.screen instanceof CommunityScreen||mc.screen instanceof FeatureListScreen)mc.setScreen(new TextScreen(null,Client.tr("ui.rivet_update_5591c964"),result));return;}
        if(kind.equals("moderationVoteStatus")){if(moderationVote.equals(j.getAsJsonObject("vote")))return;moderationVote=j.getAsJsonObject("vote").deepCopy();if(mc.screen instanceof CommunityScreen screen)screen.refreshUi();if(mc.screen instanceof NotificationPopup popup)popup.refreshVote();if(mc.screen instanceof ModerationVoteScreen screen)screen.invalidate();return;}
        if(kind.equals("moderationVote")){if(mc.screen instanceof ModerationVoteScreen screen)screen.receiveCommunity(j);return;}
        if(kind.equals("playerAdministration")){if(mc.screen instanceof PlayerAdministrationScreen screen)screen.receive(j);return;}
        if(kind.equals("openCommunity")){if(Json.str(j,"section").equals("tasks")){if(TaskScreen.available())mc.setScreen(new TaskScreen(mc.screen,Json.opt(j,"group",""),Json.str(j,"id")));}else mc.setScreen(new CommunityScreen(mc.screen,Json.str(j,"section"),Json.str(j,"id")));return;}
        if(kind.equals("changed")){if(MenuChangeHint.activities(j))MapActivities.invalidate();if(MenuChangeHint.territories(j))MapGroupClient.reset();transport.invalidate(Json.opt(j,"section",""),Json.opt(j,"id",""));if(mc.screen instanceof CommunityScreen screen)screen.invalidate(Json.opt(j,"section",""),Json.opt(j,"id",""));else if(mc.screen instanceof FeatureListScreen screen)screen.invalidate();else if(mc.screen instanceof NotificationPopup screen)screen.invalidate();else if(mc.screen instanceof TaskScreen tasks)tasks.invalidate(Json.opt(j,"section",""),Json.opt(j,"id",""));else if(mc.screen instanceof ChoicePopup popup)popup.invalidate();return;}
        if(kind.equals("community")){if(mc.screen instanceof CommunityScreen screen)screen.receive(j);else if(mc.screen instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(j);return;}
        if(kind.equals("reports")&&mc.screen instanceof ReportQueueScreen queue){queue.receiveCommunity(j);return;}
        if(java.util.Set.of("reports","myReports","players","history","menuData").contains(kind)){if(mc.screen instanceof FeatureListScreen list&&(list.kind.equals(kind)||kind.equals("menuData")&&list.kind.equals("links")))list.receive(j);return;}
        if(kind.equals("state")){if(!j.has("menuReady")||j.get("menuReady").getAsBoolean())stateBootstrap.confirm();String previousPermissions=permissions(state);long previousSequence=state.has("popupSequence")?state.get("popupSequence").getAsLong():0;int previousUnread=state.has("unread")?state.get("unread").getAsInt():0;state=j;long sequence=j.has("popupSequence")?j.get("popupSequence").getAsLong():0;if(mc.screen instanceof NotificationPopup popup&&(sequence!=previousSequence||j.get("unread").getAsInt()!=previousUnread))popup.invalidate();if(sequence>lastPopup){lastPopup=sequence;RivetHud.refresh();}if(!previousPermissions.equals(permissions(state))){transport.clearCache();UiNavigation.clear();if(mc.screen instanceof ServerMenuScreen menu)menu.refreshPermissions();else if(mc.screen instanceof CommunityScreen menu)menu.refreshPermissions();}if(j.has("profile"))Protocol.profile=j.getAsJsonObject("profile");if(j.get("open").getAsBoolean())mc.setScreen(new CommunityScreen(null,"home",""));}
        else if(kind.equals("notice")){if(notices)RivetHud.offer(new HudNoticeQueue.Notice("",Json.opt(j,"section","server"),Json.opt(j,"target",""),Json.opt(j,"event","server"),Json.opt(j,"title",Client.text("ui.server_message_bf8024ee")),Json.str(j,"text"),Json.opt(j,"priority","important").equals("urgent")?HudNoticeQueue.Priority.URGENT:HudNoticeQueue.Priority.IMPORTANT));}
        else if(kind.equals("diagnostics")){StringBuilder text=new StringBuilder();if(j.has("version")){text.append("Rivet ").append(Json.str(j,"version")).append("\nPostgreSQL: ").append(Json.opt(j,"database","—")).append(" · ").append((j.has("databaseMillis")?j.get("databaseMillis").getAsString():"—")).append(Client.text("ui.ms_luckperms_bec82b0c")).append(j.get("luckPerms").getAsBoolean()?Client.text("ui.enabled_bbbf3850"):Client.text("ui.disabled_0b48cc87")).append("\nPlasmo Voice: ").append(j.get("plasmoVoice").getAsBoolean()?Client.text("ui.installed_62843b26"):Client.text("ui.not_installed_32ab765c")).append("\nSimple Voice Chat: ").append(j.has("simpleVoiceChat")&&j.get("simpleVoiceChat").getAsBoolean()?Client.text("ui.installed_62843b26"):Client.text("ui.not_installed_32ab765c")).append("\n\n");for(var error:j.getAsJsonArray("recentErrors")){var row=error.getAsJsonObject();text.append(Json.str(row,"id")).append(" · ").append(Json.str(row,"operation")).append(" · ").append(Json.str(row,"type")).append(" × ").append(row.get("count")).append("\n");}text.append(Client.text("ui.clients_dbf82e31"));}for(var entry:j.getAsJsonArray("players")){var p=entry.getAsJsonObject();text.append(Json.str(p,"player")).append(" · Rivet ").append(Json.opt(p,"coreVersion","—")).append(" · ").append(p.get("matching").getAsBoolean()?"✓":"≠").append("\n").append(Json.opt(p,"packHash","")).append("\n");}mc.setScreen(new TextScreen(mc.screen,Client.tr("server.diagnostics"),text.toString()));}
        else {if(mc.screen instanceof CommunityScreen.Receiver receiver)receiver.receiveCommunity(j);result=Json.opt(j,"text","");if(j.has("error")&&mc.screen instanceof FeatureListScreen list)list.receive(j);}
    }
    private static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){
        if(e.getScreen() instanceof TitleScreen||e.getScreen() instanceof ConnectScreen){requiredVersion="";Protocol.supportedFeatures=java.util.Set.of();lastServer=null;lastServerSupported=false;}
        if(!(e.getScreen() instanceof DisconnectedScreen))return;
        var mc=Minecraft.getInstance();var parent=e.getScreen();
        if(!requiredVersion.isEmpty()){String version=requiredVersion;e.addListener(UiActions.button(Client.tr("ui.select_rivet_version_19266384"),UiActions.Tone.NORMAL,"",b->mc.setScreen(new CoreVersionsPopup(parent,version))).bounds(parent.width/2-100,parent.height-30,200,20).build());return;}
        if(lastServer!=null&&lastServerSupported){var server=lastServer;e.addListener(UiActions.button(Client.tr("server.reconnect"),UiActions.Tone.NORMAL,"",b->ConnectScreen.startConnecting(parent,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip),server,false,null)).bounds(parent.width/2-100,parent.height-54,200,20).build());}
    }
    static String header(){
        String text=Minecraft.getInstance().getCurrentServer()==null?"Rivet":Minecraft.getInstance().getCurrentServer().name;
        if(!available())return text+" · "+Client.tr("server.unavailable").getString();
        if(state.has("maintenance")&&state.get("maintenance").getAsBoolean())text+=" · "+Client.tr("server.maintenance").getString();
        long restart=state.has("restartAt")?state.get("restartAt").getAsLong():0;
        if(restart>0)text+=" · "+Client.tr("server.countdown",Math.max(0,(restart-System.currentTimeMillis()+999)/1000)).getString();return text;
    }
    private static String permissions(JsonObject value){var snapshot=new JsonObject();for(String key:java.util.List.of("admin","staff","authReset","actions","capabilities","communityConfig","modules"))if(value.has(key))snapshot.add(key,value.get(key));if(value.has("profile")){var profile=value.getAsJsonObject("profile");if(profile.has("capabilities"))snapshot.add("profileCapabilities",profile.get("capabilities"));}return snapshot.toString();}
    static boolean supports(String feature){return state.has("features")&&state.getAsJsonArray("features").contains(new JsonPrimitive(feature));}
    static boolean staff(){return admin()||state.has("staff")&&state.get("staff").getAsBoolean();}
    static boolean module(String id){return !state.has("modules")||!state.getAsJsonObject("modules").has(id)||state.getAsJsonObject("modules").get(id).getAsBoolean();}
    static boolean may(String right){if((right.startsWith("rivet.report")||right.startsWith("rivet.moderat"))&&!module("reports"))return false;if((right.equals("rivet.restart")||right.equals("rivet.maintenance")||right.equals("rivet.announce")||right.equals("rivet.pinAnnouncement"))&&!module("server"))return false;return admin()||state.has("capabilities")&&state.getAsJsonObject("capabilities").has(right)&&state.getAsJsonObject("capabilities").get(right).getAsBoolean();}
    static boolean admin(){return state.has("admin")&&state.get("admin").getAsBoolean();}
    private static void saveSettings(){try{Json.write(Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/menu-settings.json"),java.util.Map.of("notices",notices,"sound",sound,"restartNotices",restartNotices));}catch(Exception e){result=Errors.message(e);}}
    static String toggle(int setting){if(setting==1)sound=!sound;else if(setting==2)restartNotices=!restartNotices;else{notices=!notices;if(!notices)RivetHud.QUEUE.clear();}saveSettings();return Client.tr(enabled(setting)?"server.on":"server.off").getString();}
    static boolean enabled(int setting){return setting==1?sound:setting==2?restartNotices:notices;}
}
