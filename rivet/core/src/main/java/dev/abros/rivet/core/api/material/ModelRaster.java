package dev.abros.rivet.core.api.material;

import dev.abros.rivet.api.TerrainMaterialProvider.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Orthographic CPU rasterizer. Per-pixel depth handles intersecting multipart geometry.
 * Keeps the nearest eight translucent layers, plus the nearest opaque surface.
 */
public final class ModelRaster {
 private static final int LAYERS=8;
 private final double isoScale;private final boolean topDown;private final int size;private final double left,top;private final double[] depth,opaqueDepth;private final int[] colors,opaque;private final byte[] counts;
 public ModelRaster(int size,double left,double top){this(size,left,top,false);}
 public ModelRaster(int size,double left,double top,boolean topDown){this(size,left,top,topDown,1);}
 public ModelRaster(int size,double left,double top,boolean topDown,double isoScale){this.isoScale=isoScale;this.topDown=topDown;this.size=size;this.left=left;this.top=top;depth=new double[size*size*LAYERS];colors=new int[depth.length];counts=new byte[size*size];opaqueDepth=new double[size*size];Arrays.fill(opaqueDepth,Double.NEGATIVE_INFINITY);opaque=new int[size*size];}
 public void block(Model model,int x,int y,int z,int tint){for(var q:model.quads()){
  var a=q.vertices().get(0);var b=q.vertices().get(1);var c=q.vertices().get(2);double ax=b.x()-a.x(),ay=b.y()-a.y(),az=b.z()-a.z(),bx=c.x()-a.x(),by=c.y()-a.y(),bz=c.z()-a.z();double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
  if((topDown?ny:nx+ny+nz)<=1e-9)continue;double length=Math.sqrt(nx*nx+ny*ny+nz*nz);double shade=q.shade()?(.72*Math.abs(nx)+Math.abs(ny)+.85*Math.abs(nz))/(Math.abs(nx)+Math.abs(ny)+Math.abs(nz)):1;
  triangle(q,0,1,2,x,y,z,tint,shade);triangle(q,0,2,3,x,y,z,tint,shade);
 }}
 private void triangle(Quad quad,int ia,int ib,int ic,int x,int y,int z,int tint,double shade){
  Vertex[] v={quad.vertices().get(ia),quad.vertices().get(ib),quad.vertices().get(ic)};double[] px=new double[3],py=new double[3],d=new double[3];for(int i=0;i<3;i++){px[i]=isoScale*8*((x+v[i].x())-(z+v[i].z()))-left;py[i]=isoScale*(4*((x+v[i].x())+(z+v[i].z()))-8*(y+v[i].y()))-top;d[i]=x+v[i].x()+y+v[i].y()+z+v[i].z();if(topDown){px[i]=16*(x+v[i].x())-left;py[i]=16*(z+v[i].z())-top;d[i]=y+v[i].y();}}
  double area=edge(px[0],py[0],px[1],py[1],px[2],py[2]);if(Math.abs(area)<1e-9)return;
  int minX=Math.max(0,(int)Math.floor(Math.min(px[0],Math.min(px[1],px[2])))),maxX=Math.min(size-1,(int)Math.ceil(Math.max(px[0],Math.max(px[1],px[2]))));int minY=Math.max(0,(int)Math.floor(Math.min(py[0],Math.min(py[1],py[2])))),maxY=Math.min(size-1,(int)Math.ceil(Math.max(py[0],Math.max(py[1],py[2]))));
  for(int yy=minY;yy<=maxY;yy++)for(int xx=minX;xx<=maxX;xx++){double a=edge(px[1],py[1],px[2],py[2],xx+.5,yy+.5)/area,b=edge(px[2],py[2],px[0],py[0],xx+.5,yy+.5)/area,c=1-a-b;if(a< -1e-9||b< -1e-9||c< -1e-9)continue;double u=a*v[0].u()+b*v[1].u()+c*v[2].u(),vv=a*v[0].v()+b*v[1].v()+c*v[2].v();int color=quad.texture().sample(u,vv);if((color>>>24)==0)continue;
   int rgb=0;for(int shift:new int[]{16,8,0}){double value=(color>>>shift&255)*shade;if(quad.tinted())value*=((tint>>>shift)&255)/255.;rgb|=Math.min(255,(int)Math.round(value))<<shift;}put(yy*size+xx,a*d[0]+b*d[1]+c*d[2],color&0xff000000|rgb);
  }
 }
 private static double edge(double ax,double ay,double bx,double by,double x,double y){return (x-ax)*(by-ay)-(y-ay)*(bx-ax);}
 private void put(int pixel,double z,int color){if(z<opaqueDepth[pixel]-1e-7)return;if((color>>>24)==255){if(z>=opaqueDepth[pixel]){opaqueDepth[pixel]=z;opaque[pixel]=color;}return;}
  int count=counts[pixel],offset=pixel*LAYERS,at=0;while(at<count&&depth[offset+at]>z+1e-7)at++;if(at<count&&Math.abs(depth[offset+at]-z)<1e-7)return;if(at>=LAYERS)return;int end=Math.min(count,LAYERS-1);for(int i=end;i>at;i--){depth[offset+i]=depth[offset+i-1];colors[offset+i]=colors[offset+i-1];}depth[offset+at]=z;colors[offset+at]=color;counts[pixel]=(byte)Math.min(LAYERS,count+1);
 }
 public BufferedImage image(){var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);for(int p=0;p<opaque.length;p++){int color=opaque[p];for(int i=counts[p]-1;i>=0;i--){int j=p*LAYERS+i;if(depth[j]>opaqueDepth[p]+1e-7)color=over(colors[j],color);}image.setRGB(p%size,p/size,color);}return image;}
 private static int over(int front,int back){double a=(front>>>24)/255.,b=(back>>>24)/255.,alpha=a+b*(1-a);if(alpha==0)return 0;int color=(int)Math.round(alpha*255)<<24;for(int s:new int[]{16,8,0})color|=(int)Math.round(((front>>>s&255)*a+(back>>>s&255)*b*(1-a))/alpha)<<s;return color;}
 public static boolean opaqueCube(Model model){if(model.quads().size()!=6)return false;var sides=new HashSet<String>();var textures=Collections.newSetFromMap(new IdentityHashMap<Texture,Boolean>());for(var q:model.quads()){
  if(textures.add(q.texture()))for(int color:q.texture().argb())if((color>>>24)!=255)return false;
  String side=null;for(int axis=0;axis<3;axis++)for(int value=0;value<=1;value++){boolean plane=true;var corners=new HashSet<String>();for(var v:q.vertices()){double[] p={v.x(),v.y(),v.z()};plane&=p[axis]==value;double a=p[(axis+1)%3],b=p[(axis+2)%3];plane&=(a==0||a==1)&&(b==0||b==1);corners.add(a+":"+b);}if(plane&&corners.size()==4)side=axis+":"+value;}if(side==null||!sides.add(side))return false;
 }return sides.size()==6;}
 public static Model cube(int color){var t=new Texture(1,1,new int[]{color});var quads=new ArrayList<Quad>();double[][][] faces={{{0,1,0},{0,1,1},{1,1,1},{1,1,0}},{{1,1,1},{1,0,1},{1,0,0},{1,1,0}},{{0,1,1},{0,0,1},{1,0,1},{1,1,1}}};for(var f:faces){var vertices=new ArrayList<Vertex>();for(int i=0;i<4;i++)vertices.add(new Vertex(f[i][0],f[i][1],f[i][2],i>=2?1:0,i==1||i==2?1:0));quads.add(new Quad(vertices,t,false,true));}return new Model(quads);}
}
