package dev.abros.rivet.server.compat;

import java.lang.reflect.Method;
import java.util.*;
import dev.abros.rivet.network.WaystonesWire.Stone;
import dev.abros.rivet.core.OptionalIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/** Read-only bridge to Waystones' player-specific target selection. No database-wide enumeration. */
public final class WaystonesAdapter {
 public static final OptionalIntegration CAPABILITY=dev.abros.rivet.compat.IntegrationSupport.capability("waystones","map",WaystonesAdapter::probe);
 private static Method targets,uid,name,dimension,pos,valid,transientStone,type;
 private static String probe()throws Exception{
  var manager=Class.forName("net.blay09.mods.waystones.core.PlayerWaystoneManager");
  var stone=Class.forName("net.blay09.mods.waystones.api.Waystone");
  targets=manager.getMethod("getTargetsForPlayer",ServerPlayer.class);
  if(!Collection.class.isAssignableFrom(targets.getReturnType()))throw new NoSuchMethodException("Player target collection");
  uid=stone.getMethod("getWaystoneUid");name=stone.getMethod("getEffectiveName");dimension=stone.getMethod("getDimension");pos=stone.getMethod("getPos");valid=stone.getMethod("isValid");transientStone=stone.getMethod("isTransient");type=stone.getMethod("getWaystoneType");
  return "player targets + Waystone getters (21.1.46)";
 }
 public static List<Stone> points(ServerPlayer player){return CAPABILITY.call(()->read(player),List::of);}
 private static List<Stone> read(ServerPlayer player)throws Exception{
  var result=new LinkedHashMap<UUID,Stone>();
  for(Object stone:(Collection<?>)targets.invoke(null,player)){
   if(!(boolean)valid.invoke(stone)||(boolean)transientStone.invoke(stone)||!type.invoke(stone).toString().equals("waystones:waystone"))continue;
   var id=(UUID)uid.invoke(stone);var position=(BlockPos)pos.invoke(stone);
   String title=((Component)name.invoke(stone)).getString().replaceAll("\\p{Cntrl}","").strip();
   if(title.isEmpty())title="Waystone";if(title.length()>80)title=title.substring(0,Character.isHighSurrogate(title.charAt(79))?79:80);
   String dim=((ResourceKey<?>)dimension.invoke(stone)).location().toString();
   if(dim.length()>160||Math.abs((long)position.getX())>30000000||Math.abs((long)position.getZ())>30000000||Math.abs((long)position.getY())>2048)continue;
   result.put(id,new Stone(id,title,dim,position.getX(),position.getY(),position.getZ()));
   if(result.size()>4096)throw new java.io.IOException("Waystone snapshot exceeds supported capacity");
  }
  return List.copyOf(result.values());
 }
 private WaystonesAdapter(){}
}
