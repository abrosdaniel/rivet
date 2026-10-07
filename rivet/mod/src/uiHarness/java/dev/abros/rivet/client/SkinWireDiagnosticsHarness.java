package dev.abros.rivet.client;

/** Checks the actual network boundary with intentionally failing handlers. */
final class SkinWireDiagnosticsHarness {
 static void verify()throws Exception{
  var type=dev.abros.rivet.network.SkinWire.class;
  var request=type.getDeclaredMethod("request",String.class,net.neoforged.neoforge.network.handling.IPayloadContext.class);
  var response=type.getDeclaredMethod("response",String.class);
  request.setAccessible(true);response.setAccessible(true);
  var before=dev.abros.rivet.network.SkinWire.client;
  try{
   request.invoke(null,"{",null);
   dev.abros.rivet.network.SkinWire.client=j->{throw new IllegalStateException("RIVET_PRIVATE_PACKET_TEST");};
   response.invoke(null,"{}");response.invoke(null,"{}");
  }finally{dev.abros.rivet.network.SkinWire.client=before;}
  System.out.println("RIVET_SKIN_DIAGNOSTICS_OK");
 }
 private SkinWireDiagnosticsHarness(){}
}
