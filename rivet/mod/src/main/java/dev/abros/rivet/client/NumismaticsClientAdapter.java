package dev.abros.rivet.client;

import dev.abros.rivet.compat.NumismaticsBindings;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;
import java.util.*;

public final class NumismaticsClientAdapter {
 public static boolean active(){return Minecraft.getInstance().isSameThread()&&Minecraft.getInstance().getConnection()!=null&&Protocol.supportedFeatures.contains("compatibility-adapters")&&NumismaticsBindings.CAPABILITY.available();}
 public static String name(UUID id,String fallback)throws ReflectiveOperationException{
  if(id==null)return fallback;
  // Named bank accounts take precedence over player names, as in Numismatics itself.
  var labels=(Map<?,?>)Class.forName(NumismaticsBindings.BASE+"NumismaticsClient").getField("bankAccountLabels").get(null);
  if(labels.get(id) instanceof String label)return label;
  var profile=CompatibilityClient.profile(id,"numismatics");
  return profile.getName().equals(id.toString())?fallback:profile.getName();
 }
 private NumismaticsClientAdapter(){}
}
