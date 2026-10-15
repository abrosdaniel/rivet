package dev.abros.rivet.compat;

import java.util.UUID;

/** Optional boundary; no Numismatics classes become hard dependencies. */
public final class NumismaticsBindings {
 public static final String BASE="dev.ithundxr.createnumismatics.";
 public static final dev.abros.rivet.core.OptionalIntegration CAPABILITY=IntegrationSupport.capability("numismatics","identity",()->{
  var type=Class.forName(BASE+"util.UsernameUtils",false,NumismaticsBindings.class.getClassLoader());
  if(type.getMethod("getName",UUID.class,String.class).getReturnType()!=String.class)throw new NoSuchMethodException("UsernameUtils.getName(UUID,String)");
  return "UsernameUtils.getName(UUID,String)";
 });
 private NumismaticsBindings(){}
}
