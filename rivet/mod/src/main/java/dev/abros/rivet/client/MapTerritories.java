package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;

/** Viewport-clipped scanlines support concave polygons without triangulation artifacts. */
final class MapTerritories {
 static final class Draft {
  final String group,name;final long revision;final UUID id;final String dimension;int color;final List<MapTerritory.Point> points=new ArrayList<>();
  Draft(MapGroupClient.Group g,String dimension){id=UUID.fromString(g.id());group=g.id();revision=g.revision();var t=g.territory();this.dimension=t==null?dimension:t.dimension();name=g.title();color=t==null?MapMarkerPanel.COLOURS[0]:t.color();if(t!=null)points.addAll(t.points());}
  MapTerritory value(){return new MapTerritory(id,dimension,name,color,points);}
 }
 static void world(GuiGraphics g,String dimension,MapViewport view,int width,int height,Draft draft){
  if(MapLayers.visible(MapLayers.Layer.TERRITORIES,false))for(var t:MapGroupClient.territories())if(t.dimension().equals(dimension)&&(draft==null||!draft.id.equals(t.id())))draw(g,t.points(),t.color(),t.name(),p->new double[]{view.screenX(p.x(),width/2d),view.screenZ(p.z(),height/2d)},0,0,width,height,false,true);
  if(draft!=null&&draft.dimension.equals(dimension))draw(g,draft.points,draft.color,"",p->new double[]{view.screenX(p.x(),width/2d),view.screenZ(p.z(),height/2d)},0,0,width,height,false,false);
 }
 static void minimap(GuiGraphics g,MinimapProjection projection,String dimension,int x,int y,int side,boolean round){
  if(!MapLayers.visible(MapLayers.Layer.TERRITORIES,true))return;
  for(var t:MapGroupClient.territories())if(t.dimension().equals(dimension))draw(g,t.points(),t.color(),"",p->new double[]{x+side/2d+projection.screenX(p.x(),p.z()),y+side/2d+projection.screenY(p.x(),p.z())},x,y,side,side,round,true);
 }
 private static void draw(GuiGraphics g,List<MapTerritory.Point> vertices,int color,String name,java.util.function.Function<MapTerritory.Point,double[]> project,int left,int top,int width,int height,boolean round,boolean closed){
  if(vertices.isEmpty())return;var points=vertices.stream().map(project).toList();
  double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
  for(var p:points){minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
  if(maxX<left||minX>=left+width||maxY<top||minY>=top+height)return;
  if(closed){double[] cuts=new double[points.size()];
   for(int y=Math.max(top,(int)Math.floor(minY));y<Math.min(top+height,(int)Math.ceil(maxY));y++){
    int count=0;double scan=y+.5;
    for(int i=0,j=points.size()-1;i<points.size();j=i++){var a=points.get(j);var b=points.get(i);if((a[1]>scan)!=(b[1]>scan))cuts[count++]=a[0]+(scan-a[1])*(b[0]-a[0])/(b[1]-a[1]);}
    Arrays.sort(cuts,0,count);double inset=round?width/2d-Math.sqrt(Math.max(0,width*width/4d-Math.pow(scan-top-height/2d,2))):0;
    for(int i=0;i+1<count;i+=2){int x0=(int)Math.ceil(Math.max(left+inset,cuts[i])-.5),x1=(int)Math.ceil(Math.min(left+width-inset,cuts[i+1])-.5);if(x1>x0)g.fill(x0,y,x1,y+1,0x33000000|(color&0xffffff));}
   }
  }
  for(int i=0;i<points.size()-(closed?0:1);i++)line(g,points.get(i),points.get((i+1)%points.size()),color,left,top,width,height,round);
  if(!closed)for(var p:points)if(p[0]>=left+3&&p[0]<left+width-3&&p[1]>=top+3&&p[1]<top+height-3)g.fill((int)p[0]-2,(int)p[1]-2,(int)p[0]+3,(int)p[1]+3,color);
  if(!closed&&points.size()>=3){var p=points.getFirst();if(p[0]>=left+4&&p[0]<left+width-4&&p[1]>=top+4&&p[1]<top+height-4)g.renderOutline((int)p[0]-4,(int)p[1]-4,9,9,color);}
  if(!name.isEmpty()){var font=net.minecraft.client.Minecraft.getInstance().font;String label=UiKit.fit(font,name,120);int w=font.width(label),x=(int)((minX+maxX-w)/2),y=(int)(w+8>maxX-minX?minY-12:(minY+maxY)/2);if(x>=left&&x+w<left+width&&y>=top&&y+10<top+height)g.drawString(font,label,x,y,color,true);}
 }
 private static void line(GuiGraphics g,double[] a,double[] b,int color,int left,int top,int width,int height,boolean round){
  double dx=b[0]-a[0],dy=b[1]-a[1],lo=0,hi=1;double[] p={-dx,dx,-dy,dy},q={a[0]-left,left+width-1-a[0],a[1]-top,top+height-1-a[1]};
  for(int i=0;i<4;i++){if(p[i]==0){if(q[i]<0)return;}else if(p[i]<0)lo=Math.max(lo,q[i]/p[i]);else hi=Math.min(hi,q[i]/p[i]);}if(lo>hi)return;
  int steps=(int)Math.ceil(Math.max(Math.abs(dx*(hi-lo)),Math.abs(dy*(hi-lo))));
  for(int i=0;i<=steps;i++){double t=lo+(hi-lo)*i/Math.max(1,steps);int x=(int)Math.round(a[0]+t*dx),y=(int)Math.round(a[1]+t*dy);if(!round||MinimapProjection.contains(x+.5-left-width/2d,y+.5-top-height/2d,width/2d,true))g.fill(x,y,x+1,y+1,color);}
 }
 private MapTerritories(){}
}
