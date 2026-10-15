package dev.abros.rivet.core.api.material;

import dev.abros.rivet.api.TerrainMaterialProvider.*;
import java.util.*;

/** Static vanilla fluid surfaces. The sampler must expose only already masked snapshots. */
public final class FluidGeometry {
 @FunctionalInterface public interface States {String at(int dx,int dy,int dz);}
 private FluidGeometry(){}
 public static String fluid(String state){if(state==null)return null;if(state.equals("minecraft:water")||state.startsWith("minecraft:water[")||state.contains("waterlogged=true"))return "water";if(state.equals("minecraft:lava")||state.startsWith("minecraft:lava["))return "lava";return null;}
 public static boolean pure(String state){return state!=null&&(state.equals("minecraft:water")||state.startsWith("minecraft:water[")||state.equals("minecraft:lava")||state.startsWith("minecraft:lava["));}
 private static double height(String state,String fluid){if(!Objects.equals(fluid(state),fluid))return state==null||state.equals("minecraft:air")?0:-1;int level=0;int at=state.indexOf("level=");if(at>=0){int end=at+6;while(end<state.length()&&Character.isDigit(state.charAt(end)))end++;try{level=Integer.parseInt(state.substring(at+6,end));}catch(NumberFormatException invalid){level=0;}}return level>=8?8./9:(8-Math.min(7,level))/9.;}
 private static double corner(States states,String fluid,int sx,int sz){double sum=0,weight=0;for(int dx:new int[]{0,sx})for(int dz:new int[]{0,sz}){if(fluid.equals(fluid(states.at(dx,1,dz))))return 1;double h=height(states.at(dx,0,dz),fluid);if(h<0)continue;double w=h>=.8?10:1;sum+=h*w;weight+=w;}return weight==0?0:sum/weight;}
 public static Model model(String state,States states,Texture still,Texture flowing){String fluid=fluid(state);if(fluid==null)return new Model(List.of());var out=new ArrayList<Quad>();double[] h={corner(states,fluid,-1,-1),corner(states,fluid,-1,1),corner(states,fluid,1,1),corner(states,fluid,1,-1)};
  if(!fluid.equals(fluid(states.at(0,1,0)))){
   double gx=(h[3]+h[2]-h[0]-h[1]),gz=(h[1]+h[2]-h[0]-h[3]);boolean moving=Math.abs(gx)+Math.abs(gz)>1e-6;double angle=Math.atan2(gz,gx)-Math.PI/2;var vertices=new ArrayList<Vertex>();double[][] points={{0,0},{0,1},{1,1},{1,0}};
   for(int i=0;i<4;i++){double x=points[i][0],z=points[i][1],u=x,v=z;if(moving){u=.5+((x-.5)*Math.cos(angle)-(z-.5)*Math.sin(angle))*.5;v=.5+((x-.5)*Math.sin(angle)+(z-.5)*Math.cos(angle))*.5;}vertices.add(new Vertex(x,Math.max(0,h[i]-.001),z,u,v));}out.add(new Quad(vertices,moving?flowing:still,fluid.equals("water"),false));
  }
  // No internal faces between contiguous liquid cells, including falling columns.
  if(!fluid.equals(fluid(states.at(1,0,0))))side(out,flowing,fluid,1,h[2],h[3]);
  if(!fluid.equals(fluid(states.at(0,0,1))))side(out,flowing,fluid,2,h[1],h[2]);
  if(!fluid.equals(fluid(states.at(-1,0,0))))side(out,flowing,fluid,3,h[0],h[1]);
  if(!fluid.equals(fluid(states.at(0,0,-1))))side(out,flowing,fluid,0,h[3],h[0]);
  return new Model(out);
 }
 private static void side(List<Quad> out,Texture texture,String fluid,int side,double h1,double h2){double[][] p=switch(side){case 0->new double[][]{{1,h1,0},{1,0,0},{0,0,0},{0,h2,0}};case 1->new double[][]{{1,h1,1},{1,0,1},{1,0,0},{1,h2,0}};case 2->new double[][]{{0,h1,1},{0,0,1},{1,0,1},{1,h2,1}};default->new double[][]{{0,h1,0},{0,0,0},{0,0,1},{0,h2,1}};};var vertices=new ArrayList<Vertex>();for(int i=0;i<4;i++)vertices.add(new Vertex(p[i][0],p[i][1],p[i][2],i>=2?.5:0,(1-p[i][1])*.5));out.add(new Quad(vertices,texture,fluid.equals("water"),fluid.equals("water")));}
}
