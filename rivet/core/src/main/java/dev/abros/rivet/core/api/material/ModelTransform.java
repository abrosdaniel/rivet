package dev.abros.rivet.core.api.material;

import com.google.gson.*;
import java.io.IOException;

/** Headless NeoForge root transforms, in block units; element vertices use model units. */
final class ModelTransform {
 private ModelTransform(){}
 static void apply(double[] point,JsonElement transform)throws IOException{
  double[] p={point[0]/16,point[1]/16,point[2]/16};
  if(transform.isJsonArray()){
   var rows=transform.getAsJsonArray();if(rows.size()!=3)throw new IOException("Transform matrix rows");double[] out=new double[3];
   for(int r=0;r<3;r++){var row=rows.get(r).getAsJsonArray();if(row.size()!=4)throw new IOException("Transform matrix columns");out[r]=number(row.get(3));for(int c=0;c<3;c++)out[r]+=number(row.get(c))*p[c];}p=out;
  }else{
   var o=transform.getAsJsonObject();double[] origin={1,1,1};if(o.has("origin")){var value=o.get("origin");if(value.isJsonArray())origin=vector(value);else origin=switch(value.getAsString()){case "corner"->new double[3];case "center"->new double[]{.5,.5,.5};case "opposing-corner"->new double[]{1,1,1};default->throw new IOException("Transform origin");};}
   for(int i=0;i<3;i++)p[i]-=origin[i];
   rotation(p,o.has("post_rotation")?o.get("post_rotation"):o.get("right_rotation"),0);
   if(o.has("scale")){var scale=vector(o.get("scale"));for(int i=0;i<3;i++)p[i]*=scale[i];}
   rotation(p,o.has("rotation")?o.get("rotation"):o.get("left_rotation"),0);
   var translation=o.has("translation")?vector(o.get("translation")):new double[3];for(int i=0;i<3;i++)p[i]+=origin[i]+translation[i];
  }
  for(int i=0;i<3;i++){if(!Double.isFinite(p[i])||p[i]<-1||p[i]>2)throw new IOException("Transform outside supported bounds");point[i]=p[i]*16;}
 }
 private static double number(JsonElement e)throws IOException{double n=e.getAsDouble();if(!Double.isFinite(n)||Math.abs(n)>4096)throw new IOException("Invalid transform number");return n;}
 private static double[] vector(JsonElement e)throws IOException{var a=e.getAsJsonArray();if(a.size()!=3)throw new IOException("Transform vector");return new double[]{number(a.get(0)),number(a.get(1)),number(a.get(2))};}
 private static void rotation(double[] p,JsonElement e,int depth)throws IOException{
  if(e==null)return;if(depth>8)throw new IOException("Transform rotation depth");
  if(e.isJsonObject()){var object=e.getAsJsonObject();if(object.size()!=1)throw new IOException("Rotation axis");var axis=object.entrySet().iterator().next();int i=switch(axis.getKey()){case "x"->0;case "y"->1;case "z"->2;default->throw new IOException("Rotation axis");};axis(p,i,number(axis.getValue()));return;}
  var a=e.getAsJsonArray();if(a.size()>16)throw new IOException("Rotation limit");if(!a.isEmpty()&&a.get(0).isJsonObject()){for(var part:a)rotation(p,part,depth+1);return;}
  if(a.size()==3){for(int i=0;i<3;i++)axis(p,i,number(a.get(i)));return;}if(a.size()!=4)throw new IOException("Rotation quaternion");
  double x=number(a.get(0)),y=number(a.get(1)),z=number(a.get(2)),w=number(a.get(3));double norm=Math.sqrt(x*x+y*y+z*z+w*w);if(norm<1e-12)throw new IOException("Zero quaternion");x/=norm;y/=norm;z/=norm;w/=norm;
  double tx=2*(y*p[2]-z*p[1]),ty=2*(z*p[0]-x*p[2]),tz=2*(x*p[1]-y*p[0]);p[0]+=w*tx+y*tz-z*ty;p[1]+=w*ty+z*tx-x*tz;p[2]+=w*tz+x*ty-y*tx;
 }
 private static void axis(double[] p,int axis,double degrees){int a=(axis+1)%3,b=(axis+2)%3;double c=Math.cos(Math.toRadians(degrees)),s=Math.sin(Math.toRadians(degrees)),x=p[a],y=p[b];p[a]=c*x-s*y;p[b]=s*x+c*y;}
}
