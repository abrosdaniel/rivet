package dev.abros.rivet.server;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.auth.*;
import net.minecraft.commands.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.*;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class AuthServer {
    private static final ConfigurationTask.Type TASK=new ConfigurationTask.Type(ResourceLocation.fromNamespaceAndPath("rivet","auth"));
    private static ThreadPoolExecutor WORK;
    private static ExecutorService OFFICIAL;
    private static ScheduledExecutorService DEADLINES;
    private static ExecutorService VALIDATION;
    private static final AtomicBoolean validating=new AtomicBoolean();
    private static final Map<Connection,Session> SESSIONS=new ConcurrentHashMap<>();
    private static volatile AuthStore store;private static AuthTls.Identity identity;private static MinecraftServer server;private static Path root;private static volatile String mode="false";private static long lastCheck;
    private static volatile ServerIdentities identities;
    /** Cache lookups also serve synthetic mod profiles; only player profiles are remapped. */
    public static com.mojang.authlib.GameProfile lookupProfile(com.mojang.authlib.GameProfile original){
        if(original==null||original.getId()==null||!AuthStore.validName(original.getName()))return original;
        return profile(original);
    }
    public static com.mojang.authlib.GameProfile profile(com.mojang.authlib.GameProfile original){
        return profile(original,null);
    }
    public static com.mojang.authlib.GameProfile profile(com.mojang.authlib.GameProfile original,UUID claimedOfficial){
        var index=identities;if(!enabled()||index==null)return original;var identity=index.resolve(original.getName(),original.getId(),claimedOfficial);
        var result=new com.mojang.authlib.GameProfile(identity.uuid(),identity.name());result.getProperties().putAll(original.getProperties());return result;
    }
    private static void remember(AuthStore.Account account){identities.remember(new AuthStore.Profile(account.name(),UUID.fromString(account.uuid()),account.official()==null?null:UUID.fromString(account.official())));}
    public static Optional<AuthStore.Profile> knownIdentity(String name){var index=identities;return index==null?Optional.empty():index.known(name);}
    public static Optional<AuthStore.Profile> knownIdentity(UUID id){var index=identities;return index==null?Optional.empty():index.known(id);}
    public static Optional<AuthStore.Profile> verifiedAlias(UUID id){var index=identities;return index==null?Optional.empty():index.verifiedAlias(id);}
    public static boolean enabled(){return !mode.equals("false");}
    public static void install(IEventBus bus,ModContainer container){
        bus.addListener(AuthServer::tasks);AuthProtocol.server=AuthServer::receive;AuthProtocol.upgrade=AuthServer::upgrade;
        NeoForge.EVENT_BUS.addListener(AuthServer::tick);NeoForge.EVENT_BUS.addListener(AuthServer::commands);

    }
    static void stop(){SESSIONS.values().forEach(Session::close);SESSIONS.clear();ModuleWorkers.stop(WORK,OFFICIAL,DEADLINES,VALIDATION);WORK=null;OFFICIAL=null;DEADLINES=null;VALIDATION=null;validating.set(false);store=null;identity=null;identities=null;mode="false";server=null;}
    static void start(net.neoforged.neoforge.event.server.ServerStartingEvent e){
        server=e.getServer();mode="false";store=null;identity=null;lastCheck=0;
        String requestedMode=ServerDatabase.settings().text("auth.mode");if(requestedMode.equals("false"))return;
        WORK=new dev.abros.rivet.core.LocalizedExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Rivet auth");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());OFFICIAL=new dev.abros.rivet.core.LocalizedExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"Rivet official verification");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());DEADLINES=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Rivet auth deadlines");t.setDaemon(true);return t;});VALIDATION=new dev.abros.rivet.core.LocalizedExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"Rivet session checks");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        var database=ServerDatabase.get();
        if(ModList.get().isLoaded("authlogic"))throw new IllegalStateException("Remove AuthLogic before enabling Rivet Auth");
        root=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        try{
            store=new AuthStore(database,ServerDatabase.settings().number("auth.minPasswordLength"));identities=new ServerIdentities(store.profiles());identity=AuthTls.identity(root.resolve("rivet"));mode=requestedMode;
            com.mojang.logging.LogUtils.getLogger().info("Rivet Auth TLS fingerprint: {}",identity.fingerprint());
        }catch(Exception ex){throw new IllegalStateException("Cannot initialize Rivet Auth; startup aborted",ex);}
    }
    public static boolean mayReset(ServerPlayer p){return enabled()&&authenticated(p)&&LuckPermsAdapter.profile(p).getAsJsonObject("capabilities").get("rivet.auth.reset").getAsBoolean();}
    public static boolean authenticated(ServerPlayer p){if(!enabled())return true;var s=SESSIONS.get(p.connection.getConnection());return s!=null&&s.joined&&s.account!=null;}
    private static void tasks(RegisterConfigurationTasksEvent e){
        if(!enabled())return;
        if(!(e.getListener() instanceof ServerConfigurationPacketListenerImpl listener))return;
        if(!listener.hasChannel(AuthProtocol.Upgrade.TYPE)||!listener.hasChannel(AuthProtocol.Ready.TYPE)||!listener.hasChannel(AuthProtocol.Hello.TYPE)||!listener.hasChannel(AuthProtocol.ToServer.TYPE)||!listener.hasChannel(AuthProtocol.ToClient.TYPE)){listener.disconnect(Component.translatable("rivet.core.rivet_with_the_auth_module_is_94b77602"));return;}
        e.register(new ICustomConfigurationTask(){public ConfigurationTask.Type type(){return TASK;}public void run(java.util.function.Consumer<net.minecraft.network.protocol.common.custom.CustomPacketPayload> send){
            if(SESSIONS.values().stream().filter(s->!s.joined).count()>=128||SESSIONS.values().stream().filter(s->!s.joined&&sameAddress(s.connection,listener.getConnection())).count()>=8){listener.disconnect(Component.translatable("rivet.core.too_many_connections_try_again_later_8527f3ad"));return;}
            try{Session session=new Session(listener);var previous=SESSIONS.put(listener.getConnection(),session);if(previous!=null)previous.close();send.accept(new AuthProtocol.Hello(identity.fingerprint()));}catch(Exception ex){listener.disconnect(Component.translatable("rivet.core.could_not_start_secure_sign_in_c01a4595"));}
        }});
    }
    private static void upgrade(IPayloadContext context){
        var session=SESSIONS.get(context.connection());
        if(session==null||!session.active()||session.upgrading){context.disconnect(Component.literal(dev.abros.rivet.core.Messages.text("rivet.message.error_0b7439e3372e")));return;}
        session.upgrading=true;var tlsContext=identity.context();
        // Flush the final plaintext marker before inserting TLS at the front of the channel.
        context.connection().channel().writeAndFlush(new ClientboundCustomPayloadPacket(new AuthProtocol.Ready())).addListener(sent->{
            if(!session.active())return;
            if(!sent.isSuccess()){session.disconnect("TLS upgrade failed");return;}
            AuthTransport.install(session.connection,tlsContext,false).whenComplete((unused,error)->{
                if(!session.active())return;
                if(error!=null)session.disconnect("TLS transport failed");else session.transportReady=true;
            });
        });
    }
    private static boolean sameAddress(Connection a,Connection b){return a.getRemoteAddress() instanceof java.net.InetSocketAddress x&&b.getRemoteAddress() instanceof java.net.InetSocketAddress y&&Objects.equals(x.getAddress(),y.getAddress());}
    private static void receive(byte[] bytes,IPayloadContext c){
        var s=SESSIONS.get(c.connection());if(s==null||!s.active()||!s.transportReady){c.disconnect(Component.translatable("rivet.core.no_auth_session_ea803515"));return;}
        long now=System.currentTimeMillis();if(now-s.window>1000){s.window=now;s.packets=0;}if(++s.packets>40){s.disconnectKey("rivet.ui.too_many_auth_requests_d641eafa");return;}if(s.queued.incrementAndGet()>4){s.queued.decrementAndGet();s.disconnectKey("rivet.ui.too_many_auth_requests_d641eafa");return;}
        // Capture current permission from the game thread; no global admin/OP fallback.
        boolean reset=c.listener() instanceof ServerGamePacketListenerImpl play&&mayReset(play.player);
        try{s.serial.execute(()->{try{if(!s.active())return;s.resetAllowed=reset;s.tunnel.receive(bytes);if(s.tunnel.ready()&&!s.offered){s.offered=true;s.offer();}}catch(Exception ex){s.disconnectKey("rivet.core.auth_secure_connection_error_fa7fff43");}finally{s.queued.decrementAndGet();}});}catch(RejectedExecutionException ex){s.queued.decrementAndGet();s.disconnectKey("rivet.core.auth_busy_try_again_later_cddb40da");}
    }
    private static final class Session {
        final Connection connection;final ServerConfigurationPacketListenerImpl listener;final String name,uuid,challenge=AuthSecrets.token().substring(0,32);final long created=System.currentTimeMillis();final AtomicInteger queued=new AtomicInteger();final AuthStore database=store;
        final dev.abros.rivet.core.SerialExecutor serial=new dev.abros.rivet.core.SerialExecutor(WORK,4);
        final MinecraftServer sessionServer=server;volatile boolean closed;
        boolean officialPending;volatile Future<?> officialRequest;private ScheduledFuture<?> officialTimeout;
        final AuthTls.Tunnel tunnel;volatile AuthStore.Account account;volatile boolean joined,transportReady;boolean upgrading;boolean offered,resetAllowed;volatile String device="";volatile String language="ru_ru";long window;int packets,attempts;long lastAction;
        Session(ServerConfigurationPacketListenerImpl listener)throws Exception{this.listener=listener;connection=listener.getConnection();name=AuthStore.name(listener.getOwner().getName());uuid=listener.getOwner().getId().toString();tunnel=new AuthTls.Tunnel(identity.context(),false,b->connection.send(new ClientboundCustomPayloadPacket(new AuthProtocol.ToClient(b))),this::message);}
        boolean active(){return !closed&&server==sessionServer&&SESSIONS.get(connection)==this&&connection.isConnected();}
        void send(JsonObject j)throws Exception{if(!active())return;tunnel.send(Json.GSON.toJson(j));}
        void offer()throws Exception{var j=result("offer","");j.addProperty("mode",mode);j.addProperty("name",name);j.addProperty("challenge",challenge);var a=database.account(name);j.addProperty("type",a==null?"new":a.type());j.addProperty("linked",a!=null&&a.official()!=null);j.addProperty("registration",ServerDatabase.settings().flag("auth.registration"));j.addProperty("minimumPasswordLength",database.minimumPasswordLength());send(j);}
        void message(String json){message(json,null);}
        void message(String json,String verified){
            try{var input=Json.parse(json);String requested=Json.opt(input,"language",language);language=requested.startsWith("ru")?"ru_ru":"en_us";}catch(Exception ignored){}
            try(var locale=dev.abros.rivet.core.Messages.locale(language)){messageLocalized(json,verified);}
        }
        void messageLocalized(String json,String verified){if(!active())return;try{
            var j=Json.parse(json);String action=Json.str(j,"action");long now=System.currentTimeMillis();
            if(officialPending&&verified==null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_for_minecraft_account_verification_a357d4e6"));
            if(action.equals("accept")){if(joined||account==null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_2590e723469e"));validate();joined=true;sessionServer.execute(dev.abros.rivet.core.Messages.capture(()->{if(active())listener.finishCurrentTask(TASK);}));return;}
            if(verified==null&&now-lastAction<700)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.wait_before_the_next_action_96d41f81"));lastAction=now;
            if(verified==null&&(action.equals("official")||action.equals("link"))){
                if(!mode.equals("hybrid")||action.equals("link")&&!joined||action.equals("official")&&joined)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.operation_unavailable_4ee0fa69"));
                if(++attempts>10){disconnectKey("rivet.core.too_many_sign_in_attempts_55fa623e");return;}database.checkRate(name,now);
                verifyLater(json,j,action.equals("link")?"link":"login");return;
            }
            if(!joined){
                if(account!=null)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.awaiting_sign_in_confirmation_764b7e1e"));if(verified==null){if(++attempts>10){disconnectKey("rivet.core.too_many_sign_in_attempts_55fa623e");return;}database.checkRate(name,now);}
                switch(action){
                    case "login" -> account=withPassword(j,"password",p->database.login(name,uuid,p,now));
                    case "register" -> {if(!ServerDatabase.settings().flag("auth.registration"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.registration_is_closed_contact_an_administrator_d7b5317c"));account=withPassword(j,"password",p->database.register(name,uuid,p,now));}
                    case "device" -> {String token=Json.str(j,"token");account=database.deviceLogin(name,uuid,token,now);device=database.deviceId(name,token);}
                    case "official" -> {
                        if(!mode.equals("hybrid"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.official_sign_in_unavailable_60384b11"));
                        account=database.official(name,uuid,verified,now);
                    }
                    case "reset" -> {withPassword(j,"password",p->{database.reset(name,uuid,Json.str(j,"invitation"),p,now);return null;});remember(database.account(name));invalidate(name);send(result("resetDone",dev.abros.rivet.core.Messages.text("rivet.core.password_changed_sign_in_with_your_bf6cf1e3")));return;}
                    default -> throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f5a758644e64"));
                }
                remember(account);var out=result("authenticated","");out.addProperty("type",account.type());
                if(account.type().equals("local")&&j.has("remember")&&j.get("remember").getAsBoolean()&&!action.equals("device")){var saved=database.remember(name,Json.opt(j,"label",dev.abros.rivet.core.Messages.text("rivet.core.my_computer_709726d5")),now);device=saved.id();out.addProperty("token",saved.token());}
                send(out);return;
            }
            validate();switch(action){
                case "devices" -> {var out=result("devices","");out.addProperty("type",account.type());out.addProperty("current",device);out.addProperty("linked",account.official()!=null);out.addProperty("mode",mode);out.add("devices",database.devices(name,now));send(out);}
                case "link" -> {
                    String official=verified;
                    account=withPassword(j,"password",p->database.linkOfficial(name,uuid,official,p,now));device="";remember(account);
                    invalidate(name);send(result("updated",dev.abros.rivet.core.Messages.text("rivet.core.minecraft_account_linked_other_sessions_revoked_4024573d")));
                }
                case "unlink" -> {
                    account=withPassword(j,"password",p->database.unlinkOfficial(name,uuid,p,now));device="";remember(account);
                    invalidate(name);send(result("updated",dev.abros.rivet.core.Messages.text("rivet.core.minecraft_account_unlinked_other_sessions_revoked_2c969a41")));
                }
                case "change" -> {withPassword(j,"oldPassword",old->withPassword(j,"password",p->{database.change(name,uuid,old,p,now);return null;}));invalidate(name);send(result("changed",dev.abros.rivet.core.Messages.text("rivet.core.password_changed_sign_in_again_1482c4fc")));disconnectKey("rivet.core.password_changed_reconnect_3a65b7a1");}
                case "revoke" -> {database.revoke(name,Json.str(j,"device"),now);invalidate(name);send(result("updated",dev.abros.rivet.core.Messages.text("rivet.core.device_access_revoked_cdb49eef")));}
                case "invite" -> {
                    if(!resetAllowed)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.rivet_auth_reset_permission_required_fc0935f2"));
                    String target=AuthStore.name(Json.str(j,"target"));String token=database.invite(name,target,now);
                    // Permission can be revoked while the database work is queued. Recheck before disclosing.
                    deliverInvitation(target,token,now);
                }
                default -> throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_16f4fb50ee99"));
            }
        }catch(Exception ex){try{send(result("error",ex instanceof IllegalArgumentException?ex.getMessage():dev.abros.rivet.core.Messages.text("rivet.core.could_not_perform_the_auth_action_c272c643")));}catch(Exception ignored){disconnectKey("rivet.ui.auth_error_deb64113");}}}
        synchronized void verifyLater(String json,JsonObject request,String purpose)throws Exception{
            String launcherName=AuthStore.name(Json.str(request,"officialName"));
            if(!active())return;String digest=proof(challenge,identity.fingerprint(),purpose);var service=sessionServer.getSessionService();
            officialPending=true;
            var result=new CompletableFuture<String>();
            try{officialRequest=OFFICIAL.submit(()->{try{
                var profile=service.hasJoinedServer(launcherName,digest,null);
                if(profile==null||!launcherName.equalsIgnoreCase(profile.profile().getName()))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f607a44a23c3"));
                result.complete(profile.profile().getId().toString());
            }catch(Exception failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException busy){officialPending=false;throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.minecraft_verification_is_busy_try_again_2edc3897"));}
            var officialJob=officialRequest;ScheduledFuture<?> timeout;
            try{timeout=DEADLINES.schedule(()->{if(result.completeExceptionally(new TimeoutException()))officialJob.cancel(true);},10,TimeUnit.SECONDS);officialTimeout=timeout;}
            catch(RejectedExecutionException stopped){officialJob.cancel(true);officialPending=false;throw stopped;}
            result.whenComplete((uuid,error)->{timeout.cancel(false);if(!active())return;try{serial.execute(()->{
                officialPending=false;if(!active())return;
                try(var locale=dev.abros.rivet.core.Messages.locale(language)){if(error==null)message(json,uuid);else try{database.failure(name,System.currentTimeMillis());send(result("error",dev.abros.rivet.core.Messages.text("rivet.core.could_not_verify_minecraft_account_try_3dffeb83")));}catch(Exception failure){disconnectKey("rivet.core.minecraft_account_verification_error_c1f28883");}}
            });}catch(RejectedExecutionException busy){disconnectKey("rivet.core.auth_busy_try_again_later_cddb40da");}});
        }
        void deliverInvitation(String target,String token,long issuedAt){
            sessionServer.execute(dev.abros.rivet.core.Messages.capture(()->{
                if(!active())return;var player=sessionServer.getPlayerList().getPlayer(UUID.fromString(uuid));
                if(player==null||player.connection.getConnection()!=connection||!mayReset(player))return;
                try{serial.execute(()->{if(!active())return;try{var out=result("invitation","");out.addProperty("target",target);out.addProperty("token",token);out.addProperty("expires",issuedAt+900000);send(out);}catch(Exception ex){disconnectKey("rivet.core.could_not_issue_an_invitation_3fc9bc59");}});}catch(RejectedExecutionException ignored){}
            }));
        }
        void validate()throws Exception{var current=database.account(name);if(current==null||current.blocked()||current.generation()!=account.generation()||!device.isEmpty()&&!database.hasDevice(name,device,System.currentTimeMillis())){disconnectKey("rivet.core.access_revoked_sign_in_again_b2d0179e");throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.access_revoked_9374312a"));}}
        void disconnectKey(String key){disconnect(Component.translatable(key));}
        void disconnect(String text){disconnect(Component.literal(text));}
        void disconnect(Component text){joined=false;if(!active())return;sessionServer.execute(dev.abros.rivet.core.Messages.capture(()->{if(active())connection.disconnect(text);}));}
        synchronized void close(){if(closed)return;closed=true;joined=false;var request=officialRequest;if(request!=null)request.cancel(true);if(officialTimeout!=null)officialTimeout.cancel(false);serial.close(()->{tunnel.close();account=null;});}
    }
    private static String proof(String challenge,String fingerprint,String purpose){return AuthSecrets.digest("rivet-auth-"+dev.abros.rivet.core.WireProtocols.version("auth")+"\n"+purpose+"\n"+fingerprint+"\n"+challenge).substring(0,40);}
    private interface PasswordWork<T>{T run(char[] password)throws Exception;}
    private static <T>T withPassword(JsonObject j,String field,PasswordWork<T> work)throws Exception{String value=Json.str(j,field);if(value.length()>128)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.password_is_too_long_c616603e"));char[] password=value.toCharArray();j.remove(field);try{return work.run(password);}finally{Arrays.fill(password,'\0');}}
    private static JsonObject result(String kind,String text){var j=new JsonObject();j.addProperty("kind",kind);j.addProperty("text",text==null?"":text);return j;}
    private static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(!enabled()||System.currentTimeMillis()-lastCheck<1000)return;lastCheck=System.currentTimeMillis();for(var s:SESSIONS.values()){
        if(!s.connection.isConnected()){SESSIONS.remove(s.connection,s);s.close();continue;}
        if(!s.joined&&lastCheck-s.created>180000){s.disconnectKey("rivet.core.sign_in_timed_out_579103a8");continue;}
    }
        var checks=new HashMap<Session,AuthStore.SessionKey>();for(var session:SESSIONS.values()){var account=session.account;if(session.joined&&account!=null)checks.put(session,new AuthStore.SessionKey(session.name,account.generation(),session.device));}
        if(!checks.isEmpty()&&validating.compareAndSet(false,true)){var database=store;try{VALIDATION.execute(()->{try{var valid=database.validSessions(checks.values(),System.currentTimeMillis());checks.forEach((session,key)->{if(!valid.contains(key))session.disconnectKey("rivet.core.access_revoked_sign_in_again_b2d0179e");});}catch(Exception ex){checks.keySet().forEach(session->session.disconnectKey("rivet.core.could_not_verify_auth_session_eafe641a"));}finally{validating.set(false);}});}catch(RejectedExecutionException busy){validating.set(false);checks.keySet().forEach(session->session.disconnectKey("rivet.core.could_not_verify_auth_session_reconnect_cc88e81b"));}}
    }
    // Called on the auth worker immediately after committing a credential change.
    private static void invalidate(String name){for(var session:SESSIONS.values())if(session.account!=null&&session.name.equalsIgnoreCase(name))try{session.validate();}catch(Exception ex){session.disconnectKey("rivet.core.access_revoked_sign_in_again_b2d0179e");}}
    private static boolean console(CommandSourceStack s){return s.getEntity()==null&&s.hasPermission(4);}
    private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
        var reset=Commands.literal("reset").requires(s->enabled()&&(console(s)||s.getEntity() instanceof ServerPlayer p&&mayReset(p)))
            .then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->ServerCommands.suggest(b)).executes(c->{var source=c.getSource();String target=StringArgumentType.getString(c,"player");if(source.getEntity() instanceof ServerPlayer p)return playerInvitation(source,p,target);return consoleJob(source,(database,index)->{String token=database.invite("console",target,System.currentTimeMillis());return dev.abros.rivet.core.Messages.text("rivet.core.recovery_code_for_6a7b63d0")+AuthStore.name(target)+": "+token+dev.abros.rivet.core.Messages.text("rivet.core.valid_for_15_minutes_single_use_e2005e55");});}));
        var auth=Commands.literal("auth").then(reset);
        for(boolean block:List.of(true,false))auth.then(Commands.literal(block?"block":"unblock").requires(s->enabled()&&console(s)).then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->ServerCommands.suggest(b)).executes(c->consoleJob(c.getSource(),(database,index)->{database.block("console",StringArgumentType.getString(c,"player"),block,System.currentTimeMillis());invalidate(StringArgumentType.getString(c,"player"));return dev.abros.rivet.core.Messages.text("rivet.core.account_status_updated_13647c8c");}))));
        auth.then(Commands.literal("reserve").requires(s->enabled()&&console(s)).then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->ServerCommands.suggest(b)).executes(c->consoleJob(c.getSource(),(database,index)->{String name=AuthStore.name(StringArgumentType.getString(c,"player"));database.reserve(name,index.resolve(name).uuid().toString());var account=database.account(name);index.remember(new AuthStore.Profile(account.name(),UUID.fromString(account.uuid()),account.official()==null?null:UUID.fromString(account.official())));return dev.abros.rivet.core.Messages.text("rivet.core.name_reserved_create_an_invitation_using_6ca5a363")+name;}))));
        e.getDispatcher().register(Commands.literal("rivet").then(auth));
    }
    private static int playerInvitation(CommandSourceStack source,ServerPlayer player,String target){
        var session=SESSIONS.get(player.connection.getConnection());
        if(session==null||!session.active()){source.sendFailure(Component.translatable("rivet.core.auth_session_ended_d0274b94"));return 0;}
        try{session.serial.execute(()->{
            if(!session.active())return;
            try{long issuedAt=System.currentTimeMillis();String token=session.database.invite(session.name,AuthStore.name(target),issuedAt);session.deliverInvitation(target,token,issuedAt);}
            catch(Exception failure){session.sessionServer.execute(dev.abros.rivet.core.Messages.capture(()->{if(session.active())source.sendFailure(Component.translatable("rivet.core.could_not_create_an_invitation_for_648b4cf2"));}));}
        });return 1;}catch(RejectedExecutionException busy){source.sendFailure(Component.translatable("rivet.core.auth_busy_7cbb8545"));return 0;}
    }
    private interface CommandWork{String run(AuthStore database,ServerIdentities index)throws Exception;}
    private static int consoleJob(CommandSourceStack source,CommandWork work){
        var owner=source.getServer();var database=store;var index=identities;var workers=WORK;
        if(database==null||index==null||workers==null||server!=owner){source.sendFailure(Component.translatable("rivet.core.auth_unavailable_42d16463"));return 0;}
        try{workers.execute(()->{
            if(store!=database||identities!=index||server!=owner)return;
            try{String message=work.run(database,index);owner.execute(()->{if(store==database&&identities==index&&server==owner)source.sendSuccess(()->Component.literal(message),false);});}
            catch(Exception ex){owner.execute(()->{if(store==database&&identities==index&&server==owner)source.sendFailure(Component.literal(ex instanceof IllegalArgumentException?ex.getMessage():dev.abros.rivet.core.Messages.text("rivet.ui.auth_error_deb64113")));});}
        });return 1;}catch(RejectedExecutionException ex){source.sendFailure(Component.translatable("rivet.core.auth_busy_7cbb8545"));return 0;}
    }
}
