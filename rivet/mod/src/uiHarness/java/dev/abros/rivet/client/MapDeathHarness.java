package dev.abros.rivet.client;
import dev.abros.rivet.core.map.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in real death/respawn and UI lifetime checks; never packaged. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapDeathHarness {
 private static int stage=-1,ticks,checks,profile;private static boolean done;private static long deadline;
 private static Set<UUID> before,normalBefore;private static MapMarker first,second;private static UUID home;private static int expectedY;
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.flows(1,1);
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  String address=System.getenv("RIVET_MAP_DEATH_TEST_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(stage==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+240000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Death test",address,ServerData.Type.OTHER),false,null);stage=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Death test timed out at "+stage);
   if(!WorldMapClient.ready()||!WorldMapClient.markersReady()||++ticks<30)return;ticks=0;
   switch(stage){
    case 0->{if(mc.player.isDeadOrDying()){mc.player.respawn();mc.setScreen(null);return;}before=new HashSet<>();normalBefore=new HashSet<>();for(var m:WorldMapClient.markers()){before.add(m.id());if(!m.death())normalBefore.add(m.id());}mc.player.connection.sendCommand("gamemode creative");mc.player.connection.sendCommand("tp @s 14 90 -35");stage++;}
    case 1->{expectedY=mc.player.getBlockY();mc.player.connection.sendCommand("kill @s");stage++;}
    case 2->{var list=fresh();if(list.size()!=1||!mc.player.isDeadOrDying())throw new IllegalStateException("Death must create exactly one point: "+list.size());first=list.getFirst();if(first.color()!=0xff606876||!MapDeathLifecycle.countdown(first,System.currentTimeMillis()).matches("[0-9]+:[0-9]{2}"))throw new IllegalStateException("Wrong death color or countdown");checks+=2;if(first.x()!=14||Math.abs(first.y()-expectedY)>1||first.z()!=-35||!first.icon().equals("skull"))throw new IllegalStateException("Wrong death position: "+first);checks++;stage++;}
    case 3->{if(fresh().size()!=1)throw new IllegalStateException("Duplicate or removed point while dead");mc.player.respawn();mc.setScreen(null);checks++;stage++;}
    case 4->{if(mc.player.isDeadOrDying())throw new IllegalStateException("Did not respawn");mc.player.connection.sendCommand("tp @s 100 90 -35");home=UUID.randomUUID();WorldMapClient.put(new MapMarker(home,first.dimension(),"Persistent home",first.x(),first.y(),first.z(),0xffffff,"home"));stage++;}
    case 5->{if(fresh().size()!=1)throw new IllegalStateException("Point disappeared before arrival");mc.player.connection.sendCommand("tp @s "+(first.x()+.5)+" "+first.y()+" "+(first.z()+.5));stage++;}
    case 6->{if(!fresh().isEmpty()||WorldMapClient.markers().stream().noneMatch(m->m.id().equals(home)))throw new IllegalStateException("Arrival did not remove death only");checks++;mc.player.connection.sendCommand("kill @s");stage++;}
    case 7->{var list=fresh();if(list.size()!=1||list.getFirst().id().equals(first.id())||list.getFirst().deathAt()<=first.deathAt())throw new IllegalStateException("Second death not recorded");second=list.getFirst();mc.player.respawn();mc.setScreen(null);checks++;stage++;}
    case 8->{mc.player.connection.sendCommand("tp @s 100 90 -35");stage++;}
    case 9->{if(fresh().size()!=1)throw new IllegalStateException("Respawn removed distant point");stage++;}
    case 10->{
     if(profile<FRAMES.size())FRAMES.get(profile).apply();else{mc.options.guiScale().set(3);mc.getWindow().setWindowed(profile==FRAMES.size()?960:640,profile==FRAMES.size()?540:480);mc.resizeDisplay();}
     mc.setScreen(new WorldMapScreen(null));press("map.markers");press("map.markerFilter");if(!(mc.screen instanceof ChoicePopup))throw new IllegalStateException("No marker filter popup");stage++;
    }
    case 11->{press("map.deaths");var map=(WorldMapScreen)mc.screen;var method=WorldMapScreen.class.getDeclaredMethod("markerRows");method.setAccessible(true);@SuppressWarnings("unchecked")var rows=(List<MapMarker>)method.invoke(map);if(rows.isEmpty()||rows.stream().anyMatch(m->!m.death()))throw new IllegalStateException("History filter includes ordinary markers");map.openMarker(second,false);if(!(mc.screen instanceof UiContextPopup)||map.markerOpen())throw new IllegalStateException("Death opened editor");widgets(mc.screen);if(mc.screen.children().stream().anyMatch(c->c instanceof EditBox||c instanceof UiColorSwatch||c instanceof Button b&&(b.getMessage().getString().equals(Client.tr("map.edit").getString())||b.getMessage().getString().equals(Client.tr("map.save").getString()))))throw new IllegalStateException("Death editing controls exposed");checks+=3;stage++;}
    case 12->{mc.screen.onClose();var map=(WorldMapScreen)mc.screen;try{WorldMapClient.put(new MapMarker(second.id(),second.dimension(),"Changed",second.x()+1,second.y(),second.z(),second.color(),second.icon(),second.deathAt()));throw new IllegalStateException("Death mutation accepted");}catch(IllegalStateException expected){if(!expected.getMessage().equals("Death points cannot be edited"))throw expected;}var normal=WorldMapClient.markers().stream().filter(m->m.id().equals(home)).findFirst().orElseThrow();map.openMarker(normal,false);if(MapGlyphs.CHOICES.contains("skull"))throw new IllegalStateException("Skull selectable");map.closeMarker();MapSettings.INSTANCE.deathMap=false;var visible=WorldMapScreen.class.getDeclaredMethod("visibleMarkers");visible.setAccessible(true);@SuppressWarnings("unchecked")var rows=(List<MapMarker>)visible.invoke(map);if(rows.stream().anyMatch(MapMarker::death))throw new IllegalStateException("World map death visibility ignored");MapSettings.INSTANCE.deathMap=true;mc.setScreen(new MapSettingsScreen(null));for(var entry:SettingsCatalog.section(7)){((MapSettingsScreen)mc.screen).revealSetting(entry.id());widgets(mc.screen);}checks+=2;stage++;}
    case 13->{if(++profile<FRAMES.size()+2)stage=10;else{var map=new WorldMapScreen(null);mc.setScreen(map);map.openMarker(second,false);stage++;}}
    case 14->{DirectionCue.start(second);if(!DirectionCue.hasTarget())throw new IllegalStateException("Death navigation did not start");var aged=new MapMarker(second.id(),second.dimension(),second.name(),second.x(),second.y(),second.z(),second.color(),second.icon(),System.currentTimeMillis()-MapDeathLifecycle.LIFETIME_MILLIS-1);var rows=new ArrayList<>(WorldMapClient.markers());rows.replaceAll(m->m.id().equals(second.id())?aged:m);var persist=WorldMapClient.class.getDeclaredMethod("persist",List.class);persist.setAccessible(true);persist.invoke(null,rows);stage++;}
    case 15->{if(!fresh().isEmpty()||((WorldMapScreen)mc.screen).markerOpen()||DirectionCue.hasTarget())throw new IllegalStateException("Expired death/card still present");WorldMapClient.delete(home);checks++;stage++;}
    case 16->{var repo=new MapRepository(mc.gameDirectory.toPath().resolve("rivet/maps"),UUID.fromString(ServerMenuClient.state.getAsJsonObject("map").get("world").getAsString()),mc.player.getUUID());var ids=repo.markers().stream().map(MapMarker::id).toList();if(ids.contains(first.id())||ids.contains(second.id())||ids.contains(home)||!ids.containsAll(normalBefore))throw new IllegalStateException("Cleanup not persisted or unrelated markers lost");MapSettings.INSTANCE.reset();UiPalette.preview(0);checks++;System.out.println("RIVET_MAP_DEATH_UI_OK checks="+checks+" actualDeaths=2 arrival=true expiry=true profiles=default,light,contrast scales=1,2,3 compact=true");done=true;mc.stop();}
   }
  }catch(Throwable failure){System.out.println("RIVET_MAP_DEATH_UI_FAILED stage="+stage+" profile="+profile);failure.printStackTrace();done=true;mc.stop();}
 }
 private static int captured=-1;
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){String folder=System.getenv("RIVET_MAP_DEATH_SCREENSHOTS");if(folder==null||done||stage!=12||captured==profile)return;captured=profile;var output=new java.io.File(folder);output.mkdirs();UiCaptureHarness.grab(output,"death-"+profile+".png",Minecraft.getInstance().getMainRenderTarget(),m->{});}
 private static List<MapMarker> fresh(){return WorldMapClient.markers().stream().filter(m->m.death()&&!before.contains(m.id())).toList();}
 private static void press(String key){var mc=Minecraft.getInstance();String text=Client.tr(key).getString();((Button)mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new IllegalStateException("No control: "+text))).onPress();}
 private static void widgets(Screen screen){var a=screen.children().stream().filter(c->c instanceof AbstractWidget w&&w.visible).map(c->(AbstractWidget)c).toList();for(var w:a){if(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>screen.width||w.getY()+w.getHeight()>screen.height)throw new IllegalStateException("Out of bounds: "+w.getMessage());for(var v:a)if(v!=w&&w.getX()<v.getX()+v.getWidth()&&v.getX()<w.getX()+w.getWidth()&&w.getY()<v.getY()+v.getHeight()&&v.getY()<w.getY()+w.getHeight())throw new IllegalStateException("Overlap: "+w.getMessage()+" / "+v.getMessage());}checks++;}
}
