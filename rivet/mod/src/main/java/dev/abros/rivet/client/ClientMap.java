package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
/** Composition root for the map feature: terrain, minimap, caves, radar and navigation. */
final class ClientMap {
    static void install(IEventBus bus) {
        MapSettings.initialize();MapWaystones.install();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e)->{if(Minecraft.getInstance().screen==null)draw(e.getGuiGraphics());});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e)->{if(!(e.getScreen() instanceof HudInteractionScreen))draw(e.getGuiGraphics());});
        bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->{e.register(Minimap.EXPAND);e.register(MapCaves.MANUAL);});
        bus.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e)->e.register(WorldMapClient.OPEN));
        bus.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent e)->e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->Minecraft.getInstance().execute(()->{WorldMapClient.reload();})));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{WorldMapClient.tick();MapRenderSettings.tickLight();ClientNavigation.tick();MapWaystones.tick();});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e)->{MapMobIcons.beginFrame();WorldMapClient.frame();});
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load e)->{
            if(e.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel level){
                var pos=e.getChunk().getPos();
                Minecraft.getInstance().execute(()->{if(Minecraft.getInstance().level==level){
                    WorldMapClient.chunkLoaded(level,pos);
                }});
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.GameShuttingDownEvent e)->{WorldMapClient.shutdown();ClientNavigation.reset();MapMobIcons.clear();});
    }

 static void openLocation(net.minecraft.client.gui.screens.Screen parent,dev.abros.rivet.core.CommunityLocation place,boolean save){
  var mc=Minecraft.getInstance();if(!WorldMapClient.ready()){mc.setScreen(new TextScreen(parent,Client.tr("ui.rivet_map_2a4366b7"),Client.text("ui.the_map_is_still_loading_or_8e548a5d")));return;}
  var map=new WorldMapScreen(parent);mc.setScreen(map);map.focusLocation(place);
  if(save)map.openMarker(new dev.abros.rivet.core.map.MapMarker(java.util.UUID.randomUUID(),place.dimension(),place.name(),place.x(),place.y(),place.z(),0xffe1bf73,"flag"),true);
 }
 static boolean receive(com.google.gson.JsonObject packet){return ClientNavigation.receive(packet);}
 static void draw(net.minecraft.client.gui.GuiGraphics g){Minimap.draw(g,false);DirectionCue.draw(g);}
 private ClientMap(){}
}
