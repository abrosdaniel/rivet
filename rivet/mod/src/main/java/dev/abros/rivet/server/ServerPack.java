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
 public static void stop()throws Exception{var server=transport;transport=null;if(server!=null)server.close();var executor=publications;publications=null;if(executor!=null){executor.shutdownNow();executor.awaitTermination(5,TimeUnit.SECONDS);}publisher=null;}
 public static JsonObject advertisement(){var server=transport;if(server==null)return null;var settings=ServerDatabase.settings();var out=new JsonObject();out.addProperty("protocol",dev.abros.rivet.core.WireProtocols.version("pack"));out.addProperty("host",settings.text("pack.network.host"));out.addProperty("port",settings.number("pack.network.publicPort"));out.addProperty("fingerprint",server.fingerprint());out.addProperty("required",server.required());return out;}
 public static JsonObject status(){var out=new JsonObject();var service=publisher;var server=transport;out.addProperty("enabled",server!=null);out.addProperty("required",server!=null&&server.required());out.addProperty("hash","");if(server==null){out.addProperty("status","disabled");return out;}try{var manifest=service==null?null:service.current();out.addProperty("status",manifest==null?"unpublished":"published");if(manifest!=null)out.addProperty("hash",manifest.hash());}catch(Exception ex){out.addProperty("status","invalid");}return out;}
 public static String requiredHash(){try{var p=publisher;var server=transport;var current=p==null?null:p.current();return server!=null&&server.required()?(current==null?"unpublished":current.hash()):"";}catch(Exception ex){return "invalid";}}
 public static void install(){NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent e)->e.getDispatcher().register(Commands.literal("rivet").then(Commands.literal("pack").requires(source->ServerCommands.allowed(source,"rivet.pack"))
  .then(Commands.literal("check").executes(c->publish(c.getSource(),false,false)))
  .then(Commands.literal("publish").executes(c->publish(c.getSource(),true,false)).then(Commands.literal("restart").executes(c->publish(c.getSource(),true,true)))))));
 }
 private static int publish(net.minecraft.commands.CommandSourceStack source,boolean activate,boolean restart){var executor=publications;var service=publisher;if(executor==null||service==null){source.sendFailure(Component.literal("Модуль сборки выключен"));return 0;}source.sendSuccess(()->Component.literal("Проверка файлов сборки…"),false);try{executor.submit(()->{try{String loader=ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString();var manifest=service.prepare("1.21.1",loader);if(activate)service.activate(manifest,restart);String answer=(activate?restart?"Сборка подготовлена к следующему запуску":"Сборка опубликована":"Сборка проверена")+": "+manifest.files().size()+" файлов, "+manifest.components().size()+" необязательных компонентов";source.getServer().execute(()->source.sendSuccess(()->Component.literal(answer),false));}catch(Exception ex){source.getServer().execute(()->source.sendFailure(Component.literal(ex.getMessage()==null?"Не удалось подготовить сборку":ex.getMessage())));}});}catch(RejectedExecutionException busy){source.sendFailure(Component.literal("Проверка сборки уже выполняется. Дождитесь завершения."));return 0;}return 1;}
}
