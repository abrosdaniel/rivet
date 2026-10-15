package dev.abros.rivet.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.WorkerShutdown;
import dev.abros.rivet.core.Messages;
import dev.abros.rivet.core.api.ApiKeys;
import dev.abros.rivet.core.api.ApiSecretFiles;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Infrastructure only. No player/permission fallback and no secret in command arguments or output. */
final class ServerApiKeys {
    private static volatile ApiKeys keys;
    private static ThreadPoolExecutor worker;
    static ApiKeys authorization(){var current=keys;if(current==null)throw new ApiException(ApiException.Code.UNAVAILABLE,"API stopped");return current;}
    static boolean console(CommandSourceStack source) {
        return source.getEntity()==null && source.source==source.getServer();
    }
    static void start(MinecraftServer server) {
        keys=new ApiKeys(ServerDatabase.get(),ApiKeys.Limits.from(ServerDatabase.settings()),Clock.systemUTC());
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"Rivet API administration");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    }
    static void stop() {
        var previous=keys;keys=null;
        if(previous!=null)previous.close();
        WorkerShutdown.stop(Duration.ofSeconds(2),Duration.ofSeconds(5),worker);worker=null;
    }
    static void install() { NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event)->register(event.getDispatcher())); }
    static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        var command=Commands.literal("api").requires(ServerApiKeys::console);
        command.then(Commands.literal("keys").executes(c->run(c,"list"))
            .then(Commands.literal("list").executes(c->run(c,"list")))
            .then(Commands.literal("scopes").executes(c->run(c,"scopes")))
            .then(Commands.literal("create").then(Commands.argument("app",StringArgumentType.word())
                .then(Commands.argument("scopes",StringArgumentType.string())
                .then(Commands.argument("days",IntegerArgumentType.integer(1,3650))
                .then(Commands.argument("rpm",IntegerArgumentType.integer(1,100000)).executes(c->run(c,"create")))))))
            .then(Commands.literal("rotate").then(Commands.argument("id",StringArgumentType.word())
                .then(Commands.argument("days",IntegerArgumentType.integer(1,3650)).executes(c->run(c,"rotate")))))
            .then(Commands.literal("revoke").then(Commands.argument("id",StringArgumentType.word()).executes(c->run(c,"revoke"))))
            .then(Commands.literal("disable").then(Commands.argument("id",StringArgumentType.word()).executes(c->run(c,"disable"))))
            .then(Commands.literal("enable").then(Commands.argument("id",StringArgumentType.word()).executes(c->run(c,"enable"))))
            .then(Commands.literal("limit").then(Commands.argument("app",StringArgumentType.word())
                .then(Commands.argument("rpm",IntegerArgumentType.integer(1,100000)).executes(c->run(c,"limit"))))));
        dispatcher.register(Commands.literal("rivet").then(command));
    }
    private static int run(CommandContext<CommandSourceStack> context,String action) {
        var source=context.getSource();
        if(!console(source))return 0;
        var current=keys;var executor=worker;
        if(current==null||executor==null){ServerCommands.reply(source,Messages.text("rivet.api.keys.error").formatted("UNAVAILABLE"));return 0;}
        try {
            executor.execute(()->{
                String answer;
                try {
                    if(keys!=current||!console(source))throw new ApiException(ApiException.Code.UNAVAILABLE,"API stopped");
                    var root=FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
                    answer=switch(action) {
                        case "create" -> issued(current.create(root,text(context,"app"),new HashSet<>(Arrays.asList(text(context,"scopes").split(",",-1))),number(context,"days"),number(context,"rpm")));
                        case "rotate" -> {var id=UUID.fromString(text(context,"id"));var result=current.rotate(root,id,number(context,"days"));String cleanup=removeDelivery(root,id);yield issued(result)+cleanup;}
                        case "disable","enable","revoke" -> {var id=UUID.fromString(text(context,"id"));current.state(id,action);String cleanup=action.equals("revoke")?removeDelivery(root,id):"";yield Messages.text("rivet.api.keys."+action).formatted(id)+cleanup;}
                        case "limit" -> {current.applicationLimit(text(context,"app"),number(context,"rpm"));yield Messages.text("rivet.api.keys.limit");}
                        case "scopes" -> {var scopes=new TreeSet<>(ApiKeys.SCOPES);for(var profile:ServerDatabase.settings().terrainProfiles())scopes.add(dev.abros.rivet.core.api.TerrainProfiles.scope(profile.id()));yield Messages.text("rivet.api.keys.scopes").formatted(String.join(", ",scopes));}
                        default -> {var rows=current.list();var lines=new ArrayList<String>();for(var key:rows)lines.add(key.id()+" app="+key.application()+" scopes="+String.join(",",new TreeSet<>(key.scopes()))+" expires="+Instant.ofEpochMilli(key.expires())+" revoked="+(key.revoked()!=0)+" disabled="+key.disabled()+" rpm="+key.requestsPerMinute());yield lines.isEmpty()?Messages.text("rivet.api.keys.empty"):String.join("\n",lines);}
                    };
                } catch(Exception failure) {
                    // Exception messages/drivers/paths may contain credentials; return only a stable category.
                    answer=Messages.text("rivet.api.keys.error").formatted(failure instanceof ApiException api?api.code():failure instanceof IllegalArgumentException?"INVALID_ARGUMENT":"FAILED");
                    com.mojang.logging.LogUtils.getLogger().warn("Rivet API administration failed: {}",failure.getClass().getSimpleName());
                }
                String reply=answer;source.getServer().execute(()->{if(console(source))ServerCommands.reply(source,reply);});
            });
            return 1;
        } catch(RejectedExecutionException full) {ServerCommands.reply(source,Messages.text("rivet.api.keys.error").formatted("BUSY"));return 0;}
    }
    private static String removeDelivery(java.nio.file.Path root,UUID id) {
        try { ApiSecretFiles.remove(root,id);return ""; }
        catch(Exception failure) { return "\n"+Messages.text("rivet.api.keys.cleanup").formatted(id); }
    }
    private static String issued(ApiKeys.Issued result) {
        return Messages.text("rivet.api.keys.issued").formatted(result.key().id(),result.key().application(),result.key().id());
    }
    private static String text(CommandContext<CommandSourceStack> c,String name){return StringArgumentType.getString(c,name);}
    private static int number(CommandContext<CommandSourceStack> c,String name){return IntegerArgumentType.getInteger(c,name);}
}
