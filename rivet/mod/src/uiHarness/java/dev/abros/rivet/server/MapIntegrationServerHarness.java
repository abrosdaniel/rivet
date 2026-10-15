package dev.abros.rivet.server;
import dev.abros.rivet.api.*;
import java.util.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet")
public final class MapIntegrationServerHarness {
 private static MapLayersApi owner;private static boolean closed;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(!"true".equals(System.getenv("RIVET_MAP_INTEGRATION_FIXTURE"))||closed)return;
  var player=event.getServer().getPlayerList().getPlayerByName("RivetMapTest");if(player==null||!AuthServer.authenticated(player))return;
  if(owner==null&&!player.getTags().contains("rivet_layers_fixture"))return;
  if(owner==null){player.removeTag("rivet_layers_close");owner=new ServerApi().connect("rivet").orElseThrow().mapLayers();String dim=player.level().dimension().location().toString();int x=player.getBlockX(),z=player.getBlockZ();
   for(int i=0;i<12;i++){
    var layer=owner.register(new MapLayersApi.LayerSpec("fixture_"+i,"Integration "+i,MapLayersApi.Audience.restricted(Set.of(player.getUUID()))));
    layer.put(new MapLayersApi.ObjectSpec("point",new DomainData.Marker(dim,"Station "+i,x+i*3,player.getBlockY(),z+8,0xff55ff55,"home"),MapLayersApi.Audience.publicAccess()),0);
    if(i==0){layer.put(new MapLayersApi.ObjectSpec("area",new DomainData.Territory(dim,"Area",0xff33aaff,List.of(new DomainData.Point(x-20,z-20),new DomainData.Point(x+20,z-20),new DomainData.Point(x+20,z+20),new DomainData.Point(x-20,z+20))),MapLayersApi.Audience.publicAccess()),0);}
    layer.put(new MapLayersApi.ObjectSpec("secret",new DomainData.Marker(dim,"SECRET",0,64,0,0xff000000,"pin"),MapLayersApi.Audience.restricted(Set.of(UUID.randomUUID()))),0);
   }
   owner.register(new MapLayersApi.LayerSpec("secret_layer","SECRET",MapLayersApi.Audience.restricted(Set.of(UUID.randomUUID()))));
   System.out.println("RIVET_MAP_INTEGRATION_SERVER_REGISTERED");
  }
  if(player.getTags().contains("rivet_layers_close")){owner.close();closed=true;System.out.println("RIVET_MAP_INTEGRATION_SERVER_CLOSED");}
 }
}
