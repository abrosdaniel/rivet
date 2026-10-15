package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.api.MapLayerGameSync;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Server-authorized transient overlays; never merged into editable personal objects. */
final class MapIntegrationLayers {
 private static final MapIntegrationState state=new MapIntegrationState();
 private static List<MapIntegrationState.ObjectView> indexed=List.of();
 private static MapTerritoryIndex territories=new MapTerritoryIndex(List.of());
 private static final Map<String,List<dev.abros.rivet.api.DomainData.Marker>> markers=new HashMap<>();
 private static long retry;
 static List<MapIntegrationState.Layer> layers(){return WorldMapClient.allowed()?state.layers(System.currentTimeMillis()):List.of();}
 static boolean shown(String id){return MapSettings.INSTANCE.layers.integrationShown(id);}
 static void toggle(String id){MapSettings.INSTANCE.layers.toggleIntegration(id);MapSettings.INSTANCE.save();indexed=null;}
 static void reset(){state.clear();retry=0;indexed=null;markers.clear();territories=new MapTerritoryIndex(List.of());}
 static void tick(){long now=System.currentTimeMillis();if(!state.fresh(now)&&now>=retry&&ServerMenuClient.supports(MapLayerGameSync.FEATURE)){
  retry=now+3000;var j=new JsonObject();j.addProperty("action","mapLayerResync");
  if(ServerMenuClient.previewTransport!=null)ServerMenuClient.previewTransport.accept(j);else if(Minecraft.getInstance().getConnection()!=null)net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.abros.rivet.network.Protocol.FeatureRequest(j.toString()));
 }}
 static boolean receive(JsonObject packet){if(!Json.opt(packet,"kind","").equals(MapLayerGameSync.FEATURE))return false;
  try{state.receive(packet,System.currentTimeMillis());}catch(RuntimeException invalid){state.clear();}return true;
 }
 private static List<MapIntegrationState.ObjectView> objects(){return WorldMapClient.allowed()?state.objects(System.currentTimeMillis()):List.of();}
 private static void index(){var current=objects();if(current==indexed)return;indexed=current;markers.clear();var areas=new ArrayList<MapTerritory>();for(var object:current)if(shown(object.layer())){if(object.territory()!=null)areas.add(object.territory());else if(object.marker()!=null)markers.computeIfAbsent(object.marker().dimension(),k->new ArrayList<>()).add(object.marker());}territories=new MapTerritoryIndex(areas);}
 static void worldAreas(GuiGraphics g,String dimension,MapViewport view,int width,int height){
  index();for(var t:territories.visible(dimension,view.worldX(0,width/2d),view.worldZ(0,height/2d),view.worldX(width,width/2d),view.worldZ(height,height/2d)))MapTerritories.draw(g,t.points(),t.color(),t.name(),p->new double[]{view.screenX(p.x(),width/2d),view.screenZ(p.z(),height/2d)},0,0,width,height,false,true);
 }
 static void world(GuiGraphics g,String dimension,MapViewport view,int width,int height,BoxIntersectionIndex occupied,List<NativeLayout.Box> reserved){
  int size=Math.max(4,Math.round(24*MapRenderSettings.INSTANCE.markerScale));var font=Minecraft.getInstance().font;
  index();
  for(var m:markers.getOrDefault(dimension,List.of())){
   int x=(int)view.screenX(m.x()+.5,width/2d),y=(int)view.screenZ(m.z()+.5,height/2d);if(x<size/2||y<size/2||x>=width-size/2||y>=height-size/2)continue;
   glyph(g,m,x,y,size);occupied.add(new NativeLayout.Box(x-size/2,y-size/2,size,size));
   String label=UiKit.fit(font,m.name(),120);int w=font.width(label)+8;var box=new NativeLayout.Box(x-w/2,y+size/2+2,w,13);
   if(box.x()>=0&&box.right()<width&&box.bottom()<height&&!occupied.intersects(box)&&reserved.stream().noneMatch(b->b.x()<box.right()&&b.right()>box.x()&&b.y()<box.bottom()&&b.bottom()>box.y())){
    g.fill(box.x(),box.y(),box.right(),box.bottom(),0xee10151a);g.drawString(font,label,box.x()+4,box.y()+2,UiKit.text(),false);occupied.add(box);
   }
  }
 }
 static void minimap(GuiGraphics g,MinimapProjection projection,String dimension,int left,int top,int side,boolean round){
  int size=Math.max(3,Math.round(12*MapRenderSettings.INSTANCE.markerScale));double radius=Math.max(1,side/2d-size);
  index();double range=Math.sqrt(2)*side/(2*projection.zoom());for(var t:territories.visible(dimension,projection.x()-range,projection.z()-range,projection.x()+range,projection.z()+range))MapTerritories.draw(g,t.points(),t.color(),"",p->new double[]{left+side/2d+projection.screenX(p.x(),p.z()),top+side/2d+projection.screenY(p.x(),p.z())},left,top,side,side,round,true);
  for(var m:markers.getOrDefault(dimension,List.of())){double x=projection.screenX(m.x()+.5,m.z()+.5),y=projection.screenY(m.x()+.5,m.z()+.5);
   if(MinimapProjection.contains(x,y,radius,round))glyph(g,m,left+side/2+(int)Math.round(x),top+side/2+(int)Math.round(y),size);
  }
 }
 private static void glyph(GuiGraphics g,dev.abros.rivet.api.DomainData.Marker m,int x,int y,int size){int left=x-size/2,top=y-size/2;g.fill(left,top,left+size,top+size,0xee10151a);g.renderOutline(left,top,size,size,m.color());MapGlyphs.marker(g,MapGlyphs.MARKERS.contains(m.icon())?m.icon():"pin",left+1,top+1,Math.max(1,size-2));}
 private MapIntegrationLayers(){}
}
