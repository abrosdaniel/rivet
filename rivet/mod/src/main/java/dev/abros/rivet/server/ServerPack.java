package dev.abros.rivet.server;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.pack.*;
import com.google.gson.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import java.nio.file.*;
import java.util.concurrent.*;

/** Server-owned pack lifecycle; no GitHub lookup is involved in preparation or serving. */
public final class ServerPack {
 private static volatile PackServer transport;private static volatile PackPublisher publisher;private static ExecutorService publications;
 public static void start()throws Exception{var root=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();var settings=ServerDatabase.settings();publisher=new PackPublisher(root.resolve(settings.text("pack.directory")),root.resolve("rivet/pack-data"));publisher.startup();transport=new PackServer(publisher,root.resolve("rivet/pack-data/identity"),settings.text("pack.network.bind"),settings.number("pack.network.port"),settings.flag("pack.required"),settings.number("pack.downloads.concurrent"),settings.number("pack.downloads.totalMiB"),settings.number("pack.downloads.clientMiB"));publications=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"Rivet pack publication");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());}
 public static void stop()throws Exception{
  var server=transport;var executor=publications;transport=null;publications=null;publisher=null;
  try{if(server!=null)server.close();}
  finally{if(!WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(5),executor))com.mojang.logging.LogUtils.getLogger().warn("Rivet: pack publication worker did not stop in time");}
 }
 public static JsonObject advertisement(){var server=transport;if(server==null)return null;var settings=ServerDatabase.settings();var out=new JsonObject();out.addProperty("protocol",dev.abros.rivet.core.WireProtocols.version("pack"));out.addProperty("host",settings.text("pack.network.host"));out.addProperty("port",settings.number("pack.network.publicPort"));out.addProperty("fingerprint",server.fingerprint());out.addProperty("required",server.required());return out;}
 public static JsonObject status(){var out=new JsonObject();var service=publisher;var server=transport;out.addProperty("enabled",server!=null);out.addProperty("required",server!=null&&server.required());out.addProperty("hash","");if(server==null){out.addProperty("status","disabled");return out;}try{var manifest=service==null?null:service.current();out.addProperty("status",manifest==null?"unpublished":"published");if(manifest!=null)out.addProperty("hash",manifest.hash());}catch(Exception ex){out.addProperty("status","invalid");}return out;}
 public static String requiredHash(){try{var p=publisher;var server=transport;var current=p==null?null:p.current();return server!=null&&server.required()?(current==null?"unpublished":current.hash()):"";}catch(Exception ex){return "invalid";}}
 public static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->e.getDispatcher().register(Commands.literal("rivet").then(Commands.literal("pack").requires(source->ServerCommands.allowed(source,"rivet.pack"))
  .then(Commands.literal("check").executes(c->publish(c.getSource(),false,false)))
  .then(Commands.literal("publish").executes(c->publish(c.getSource(),true,false)).then(Commands.literal("restart").executes(c->publish(c.getSource(),true,true)))))));
 }
 private static int publish(net.minecraft.commands.CommandSourceStack source,boolean activate,boolean restart){var executor=publications;var service=publisher;if(executor==null||service==null){source.sendFailure(Component.translatable("rivet.core.modpack_module_is_disabled_34f2b70c"));return 0;}source.sendSuccess(()->Component.translatable("rivet.core.verifying_modpack_files_dcc5df0d"),false);try{executor.submit(()->{try{String loader=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();var manifest=service.prepare("1.21.1",loader);if(activate)service.activate(manifest,restart);String answer=(activate?restart?dev.abros.rivet.core.Messages.text("rivet.core.modpack_prepared_for_next_launch_23059fa6"):dev.abros.rivet.core.Messages.text("rivet.core.modpack_published_4ec81488"):dev.abros.rivet.core.Messages.text("rivet.core.modpack_verified_d8185294"))+": "+manifest.files().size()+dev.abros.rivet.core.Messages.text("rivet.core.files_e854e1bf")+manifest.components().size()+dev.abros.rivet.core.Messages.text("rivet.core.optional_components_bc12bbfc");source.getServer().execute(()->{if(publisher==service)source.sendSuccess(()->Component.literal(answer),false);});}catch(Exception ex){source.getServer().execute(()->{if(publisher==service)source.sendFailure(Component.literal(ex.getMessage()==null?dev.abros.rivet.core.Messages.text("rivet.core.could_not_prepare_modpack_59a90b5f"):ex.getMessage()));});}});}catch(RejectedExecutionException busy){source.sendFailure(Component.translatable("rivet.core.modpack_verification_is_already_running_wait_34b86ae3"));return 0;}return 1;}
}
