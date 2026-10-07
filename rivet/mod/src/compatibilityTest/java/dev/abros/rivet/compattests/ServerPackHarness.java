package dev.abros.rivet.compattests;
import dev.abros.rivet.core.pack.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Packaged-server discovery check, excluded from release artifacts. */
@EventBusSubscriber(modid="rivet_compat_tests")
public final class ServerPackHarness {
 private static java.util.concurrent.CompletableFuture<Void> work;private static int ticks;
 @SubscribeEvent public static void started(ServerStartedEvent e)throws Exception{
  if(System.getenv("RIVET_PACK_SMOKE")==null)return;
  var field=dev.abros.rivet.server.ServerPack.class.getDeclaredField("publisher");field.setAccessible(true);var publisher=(PackPublisher)field.get(null);if(publisher==null)throw new IllegalStateException("Pack module is off");var manifest=publisher.prepare("1.21.1",net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString());publisher.activate(manifest,false);
  work=java.util.concurrent.CompletableFuture.runAsync(()->{try{var endpoint=PackDiscovery.query(new java.net.InetSocketAddress("127.0.0.1",e.getServer().getPort()),"127.0.0.1",e.getServer().getPort(),"127.0.0.1");if(endpoint==null||endpoint.port()!=Integer.parseInt(System.getenv().getOrDefault("RIVET_PACK_TEST_PORT","25578"))||!endpoint.required())throw new IllegalStateException("Missing status advertisement");try(var client=new PackClient(endpoint.host(),endpoint.port(),endpoint.fingerprint())){if(!client.manifest().hash().equals(manifest.hash()))throw new IllegalStateException("Wrong publication");}System.out.println("RIVET_PACK_DISCOVERY_OK");}catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}});
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(work==null)return;if(++ticks>=400&&!work.isDone()){System.out.println("RIVET_PACK_DISCOVERY_FAILED timeout");e.getServer().halt(false);return;}if(work.isDone()){try{work.join();}catch(Exception ex){System.out.println("RIVET_PACK_DISCOVERY_FAILED");ex.printStackTrace();}work=null;e.getServer().halt(false);}}
}
