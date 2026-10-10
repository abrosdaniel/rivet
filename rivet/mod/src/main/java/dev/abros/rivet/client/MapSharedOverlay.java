package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.abros.rivet.core.map.*;
import java.util.List;
/** Server-authorized live places and peers. Never writes positions into personal map storage. */
final class MapSharedOverlay {
 private record MiniHit(MapLayerClient.Point point,int x,int y,int radius){}
 private static final java.util.List<MiniHit> miniHits=new java.util.ArrayList<>();
 private static List<MapLayerClient.Point> points(boolean mini){return java.util.stream.Stream.of(MapLayerClient.points(),MapWaystones.points(),MapActivities.points(),MapPositions.points()).flatMap(List::stream).filter(p->MapLayers.visible(p.activity()?(p.source().equals("tasks")?MapLayers.Layer.TASKS:p.source().equals("events")?MapLayers.Layer.EVENTS:MapLayers.Layer.GROUPMARKERS):p.waystone()?MapLayers.Layer.WAYSTONES:p.player()?MapLayers.Layer.PLAYERS:MapLayers.Layer.PLACES,mini)).toList();}
 private static void glyph(GuiGraphics g,MapLayerClient.Point point,int x,int y,int size){
  if(point.player())net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(java.util.UUID.fromString(point.id())),x-size/2,y-size/2,size);
  else if(point.waystone())MapWaystones.drawIcon(g,x,y,size);
  else MapGlyphs.marker(g,point.source().equals("tasks")?"pickaxe":point.source().equals("events")?"star":"flag",x-size/2,y-size/2,size);
 }
 static void world(GuiGraphics g,String dimension,MapViewport view,int width,int height,dev.abros.rivet.core.BoxIntersectionIndex occupied,List<dev.abros.rivet.core.NativeLayout.Box> reserved){
  var font=Minecraft.getInstance().font;int size=Math.round(16*MapRenderSettings.INSTANCE.markerScale);
  var visible=points(false).stream().filter(p->p.location().dimension().equals(dimension)).toList();
  for(var point:visible){var p=point.location();int x=(int)view.screenX(p.x()+.5,width/2d),y=(int)view.screenZ(p.z()+.5,height/2d);occupied.add(new dev.abros.rivet.core.NativeLayout.Box(x-size/2-1,y-size/2-1,size+2,size+2));}
  for(var point:visible){var p=point.location();
   int x=(int)view.screenX(p.x()+.5,width/2d),y=(int)view.screenZ(p.z()+.5,height/2d);if(x< -size||y< -size||x>width+size||y>height+size)continue;
   glyph(g,point,x,y,size);String name=UiKit.fit(font,p.name(),120);int w=font.width(name)+10;
   for(int top:new int[]{y+size/2+2,y-size/2-16}){
    var label=new dev.abros.rivet.core.NativeLayout.Box(x-w/2,top,w,14);
    if(label.x()<0||label.right()>width||label.y()<0||label.bottom()>height||occupied.intersects(label)||reserved.stream().anyMatch(b->overlaps(b,label)))continue;
    g.fill(label.x(),label.y(),label.right(),label.bottom(),0xee10151a);g.drawString(font,name,label.x()+5,label.y()+3,UiKit.text(),false);occupied.add(label);break;
   }
  }
 }
 static boolean clickWorld(Screen parent,String dimension,MapViewport view,double x,double y,int button){
  if(button!=0&&button!=1)return false;
  double radius=10*MapRenderSettings.INSTANCE.markerScale;
  var hit=points(false).stream().filter(p->p.location().dimension().equals(dimension)&&Math.abs(view.screenX(p.location().x()+.5,parent.width/2d)-x)<=radius&&Math.abs(view.screenZ(p.location().z()+.5,parent.height/2d)-y)<=radius).findFirst();
  if(hit.isEmpty())return false;return open(parent,hit.get(),x,y);
 }
 private static boolean open(Screen parent,MapLayerClient.Point point,double x,double y){
  var p=point.location();var mc=Minecraft.getInstance();var actions=new java.util.ArrayList<UiContextPopup.Item>();
  actions.add(new UiContextPopup.Item(Component.literal(Client.text("ui.navigate_here_bd1f9053")+p.name()),()->{if(point.activity())MapActivities.navigate(point);else if(point.player())MapPositions.navigate(point);else DirectionCue.start(p);mc.setScreen(null);},false));
  if(point.activity())actions.add(new UiContextPopup.Item(Component.literal(point.source().equals("tasks")?Client.text("ui.open_task_60587630"):point.source().equals("events")?Client.text("ui.open_event_4b435091"):Client.text("ui.open_group_63d4261f")),()->mc.setScreen(point.source().equals("tasks")?new TaskScreen(parent,point.group(),point.id()):new CommunityScreen(parent,point.source().equals("events")?"events":"groups",point.source().equals("events")?point.id():point.group())),false));
  if(!point.player()&&!point.waystone()&&!point.activity())actions.add(new UiContextPopup.Item(Client.tr("ui.save_personal_waypoint_88facb1a"),()->ClientMap.openLocation(parent,p,true),false));
  mc.setScreen(new UiContextPopup(parent,(int)x,(int)y,actions));return true;
 }
 static boolean clickMinimap(Screen parent,double x,double y){
  var current=points(true);return miniHits.stream().filter(h->current.contains(h.point())&&Math.abs(h.x()-x)<=h.radius()&&Math.abs(h.y()-y)<=h.radius()).min(java.util.Comparator.comparingDouble(h->Math.hypot(h.x()-x,h.y()-y))).map(h->open(parent,h.point(),x,y)).orElse(false);
 }
 static void minimap(GuiGraphics g,MinimapProjection projection,int left,int top,int side,boolean round){
  miniHits.clear();var mc=Minecraft.getInstance();if(mc.level==null)return;String dim=mc.level.dimension().location().toString();int size=Math.round(12*MapRenderSettings.INSTANCE.markerScale);
  for(var point:points(true)){var p=point.location();if(!p.dimension().equals(dim))continue;double x=projection.screenX(p.x()+.5,p.z()+.5),y=projection.screenY(p.x()+.5,p.z()+.5);var pin=MinimapProjection.pin(x,y,Math.max(1,side/2-size/2-2),round);int sx=left+side/2+(int)Math.round(pin[0]),sy=top+side/2+(int)Math.round(pin[1]);glyph(g,point,sx,sy,size);if(point.activity()||point.player())miniHits.add(new MiniHit(point,sx,sy,Math.max(5,size/2+2)));}
 }
 private static boolean overlaps(dev.abros.rivet.core.NativeLayout.Box a,dev.abros.rivet.core.NativeLayout.Box b){return a.x()<b.right()&&a.right()>b.x()&&a.y()<b.bottom()&&a.bottom()>b.y();}
 private MapSharedOverlay(){}
}
