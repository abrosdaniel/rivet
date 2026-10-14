package dev.abros.rivet.core.pack;
/** Entry gate only: each selected JAR's actual dependency ranges are validated before commit. */
public final class PackRuntime {
 public static boolean supported(String minecraft,String recommended,String installed){
  return "1.21.1".equals(minecraft)&&stable(recommended)&&stable(installed);
 }
 private static boolean stable(String version){if(version==null||!version.matches("21\\.1\\.[0-9]{1,6}"))return false;return Integer.parseInt(version.substring(5))>=250;}
 private PackRuntime(){}
}
