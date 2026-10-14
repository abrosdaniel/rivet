package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Real network publication workflow and local territory UI; no synthetic server replies. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapPublicationHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_PUBLICATION_ADDRESS");
 private static final List<VisualMatrixHarness.Frame> FRAMES="true".equals(System.getenv("RIVET_MAP_NETWORK_SMOKE"))?VisualMatrixHarness.flows(1,1):VisualMatrixHarness.samples(1,0);
 private static int phase=-1,ticks,frame;private static long deadline;private static boolean done;
 private static WorldMapScreen map;private static MapMarker marker;private static MapTerritory area;
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static Button button(String key){var screen=Minecraft.getInstance().screen;return screen==null?null:screen.children().stream().filter(c->c instanceof Button b&&b.active&&b.getMessage().equals(Client.tr(key))).map(c->(Button)c).findFirst().orElse(null);}
 private static boolean press(String key){var b=button(key);if(b==null)return false;b.onPress();return true;}
 private static void confirm(){var screen=Minecraft.getInstance().screen;check(screen instanceof UiConfirmDialog,"Expected confirmation");((Button)screen.children().stream().filter(c->c instanceof Button b&&b.active&&UiActions.style(b).tone()==UiActions.Tone.PRIMARY).findFirst().orElseThrow()).onPress();}
 private static Optional<MapMarker> published(){return MapPublications.markers().stream().filter(m->m.id().equals(marker.id())).findFirst();}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map publication check",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Publication timeout phase="+phase);
   if(!WorldMapClient.ready()||!WorldMapClient.markersReady()||++ticks%5!=0)return;
   switch(phase){
    case 0->{FRAMES.get(frame).apply();map=new WorldMapScreen(null);mc.setScreen(map);var dim=mc.level.dimension().location().toString();marker=new MapMarker(UUID.randomUUID(),dim,"Publication test "+frame,mc.player.getBlockX(),mc.player.getBlockY(),mc.player.getBlockZ(),0x88cc44,"pin");WorldMapClient.put(marker);area=new MapTerritory(UUID.randomUUID(),dim,"Personal area",0x7799cc,List.of(new MapTerritory.Point(0,0),new MapTerritory.Point(20,0),new MapTerritory.Point(0,20)));
     mc.setScreen(new MapTerritoryScreen(map,new MapTerritories.Draft(area)));UiGeometryHarness.verify(mc.screen);
     check(press("map.save"),"Personal territory save missing");check(WorldMapClient.territories().contains(area),"Personal territory not saved");mc.setScreen(new MapPublicationsScreen(map,marker));phase++;
    }
    case 1->{if(press("map.submitPublication"))phase++;}
    case 2->{if(button("map.approve")==null)return;check(published().isEmpty(),"Draft leaked before approval");UiGeometryHarness.verify(mc.screen);press("map.approve");confirm();phase++;}
    case 3->{if(published().isEmpty())return;check(published().get().name().equals("Publication test "+frame),"Wrong public marker");marker=new MapMarker(marker.id(),marker.dimension(),"Edited publication "+frame,marker.x(),marker.y(),marker.z(),marker.color(),marker.icon());WorldMapClient.put(marker);mc.setScreen(new MapPublicationsScreen(map,marker));phase++;}
    case 4->{if(press("map.submitPublication"))phase++;}
    case 5->{if(button("map.approve")==null)return;check(published().orElseThrow().name().equals("Publication test "+frame),"Unapproved edit replaced snapshot");check(button("map.withdraw")!=null,"Cannot withdraw while pending");press("map.approve");confirm();phase++;}
    case 6->{if(published().isEmpty()||!published().get().name().equals(marker.name()))return;if(!press("map.withdraw"))return;confirm();phase++;}
    case 7->{if(published().isPresent())return;mc.setScreen(map);mc.getConnection().sendCommand("rivet shareMarker "+MapMarkerLink.encode(marker));phase++;}
    case 8->{var history=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();String[] link={null};for(var message:history)if(message.content().getString().contains(marker.name()))message.content().visit((style,text)->{var click=style.getClickEvent();if(click!=null&&click.getValue().startsWith("/rivet addMarker "))link[0]=click.getValue();return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);if(link[0]==null)return;mc.getConnection().sendCommand(link[0].substring(1));phase++;}
    case 9->{if(!(mc.screen instanceof UiConfirmDialog))return;confirm();check(WorldMapClient.markers().stream().filter(m->m.name().equals(marker.name())).count()==2,"Chat marker was not added");
     mc.setScreen(map);MapCaves.set(marker.dimension(),new MapCaves.View(2,false,20,30,false));mc.setScreen(new MapTerritoriesScreen(map));check(MapCaves.top(marker.dimension())==20,"Modal changed cave layer");mc.screen.onClose();MapCaves.set(marker.dimension(),new MapCaves.View(0,false,64,30,false));map.closeMarker();map.keyPressed(256,0,0);check(mc.screen==null,"Escape failed to close map");
     WorldMapClient.deleteTerritory(area.id());for(var m:List.copyOf(WorldMapClient.markers()))if(m.name().equals(marker.name()))WorldMapClient.delete(m.id());WorldMapClient.flush();if(++frame<FRAMES.size()){deadline=System.currentTimeMillis()+120000;phase=0;break;}System.out.println("RIVET_MAP_PUBLICATIONS_OK personal=true approve=true revisions=true withdraw=true chat=true modal=true escape=true scales=1,2,3 profiles=default,light,contrast frames="+frame);done=true;mc.stop();
    }
   }
  }catch(Throwable ex){done=true;System.out.println("RIVET_MAP_PUBLICATIONS_FAILED phase="+phase);ex.printStackTrace();mc.stop();}
 }
}
