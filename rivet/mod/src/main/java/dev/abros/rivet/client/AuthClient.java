package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.auth.*;
import dev.abros.rivet.server.AuthProtocol;
import dev.abros.rivet.server.AuthTransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

final class AuthClient {
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    private static final ExecutorService WORK=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Rivet client auth");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static volatile Connection connection;private static AuthTls.Tunnel tunnel;private static String key;private static Path file;private static volatile boolean authenticated;
    private static JsonObject saved=new JsonObject();static JsonObject offer=new JsonObject();
    private static Runnable pendingTransport;
    static void install(){AuthProtocol.ready=AuthClient::transportReady;AuthProtocol.hello=AuthClient::hello;AuthProtocol.client=AuthClient::receive;
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{if(connection!=null&&!connection.isConnected()){var closed=connection;connection=null;authenticated=false;pendingTransport=null;offer=new JsonObject();var expired=tunnel;tunnel=null;closed.handleDisconnection();execute(()->{if(expired!=null)expired.close();});}});
    }
    static boolean available(){return authenticated&&connection!=null&&connection.isConnected();}
    private static void hello(String fingerprint,IPayloadContext c){var mc=Minecraft.getInstance();authenticated=false;connection=c.connection();Connection current=connection;pendingTransport=null;offer=new JsonObject();
        try{
            // Auth runs during configuration, before Minecraft.getConnection() exposes a play listener.
            var listener=current.getPacketListener();
            var server=listener instanceof net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl common?common.serverData:null;
            if(!fingerprint.matches("[a-f0-9]{64}")||server==null)throw new IllegalArgumentException(Client.text("message.invalid_auth_server"));
            Path dir=mc.gameDirectory.toPath().resolve("rivet/auth");Files.createDirectories(dir);if(Files.isSymbolicLink(dir))throw new IllegalArgumentException(Client.text("message.unsafe_auth_directory"));
            key=AuthSecrets.digest(server.ip.toLowerCase(Locale.ROOT)+"\n"+mc.getUser().getName().toLowerCase(Locale.ROOT));file=dir.resolve(key+".json");if(Files.isSymbolicLink(file))throw new IllegalArgumentException(Client.text("message.unsafe_auth_file"));saved=Files.exists(file)?Json.read(file):new JsonObject();
            String pinned=Json.opt(saved,"fingerprint","");if(!pinned.isEmpty()&&!pinned.equals(fingerprint)){c.disconnect(Client.tr("ui.rivet_the_server_key_has_changed_7d69b3fd"));return;}
            Runnable start=()->{try{
                saved.addProperty("fingerprint",fingerprint);save();
                pendingTransport=()->{try{
                    AuthTransport.install(current,AuthTls.client(fingerprint),true).whenComplete((unused,error)->{
                        if(error!=null){current.disconnect(Client.tr("ui.could_not_secure_the_minecraft_connection_0ddffb91"));return;}
                        execute(()->{try{if(connection!=current||!current.isConnected())return;if(tunnel!=null)tunnel.close();tunnel=new AuthTls.Tunnel(AuthTls.client(fingerprint),true,b->current.send(new ServerboundCustomPayloadPacket(new AuthProtocol.ToServer(b))),text->message(text,current));tunnel.receive(new byte[0]);}catch(Exception ex){current.disconnect(Client.tr("ui.could_not_start_auth_84996555"));}});
                    });
                }catch(Exception ex){current.disconnect(Client.tr("ui.tls_setup_error_9d5b59a6"));}};
                current.send(new ServerboundCustomPayloadPacket(new AuthProtocol.Upgrade()));
            }catch(Exception ex){c.disconnect(Client.tr("ui.could_not_save_server_trust_192299f9"));}};

            // Trust on first use; the mismatch check above rejects later key changes.
            mc.setScreen(new GenericMessageScreen(Client.tr("ui.securing_connection_e12a9851")));
            start.run();
        }catch(Exception ex){
            // Do not log exception messages: JSON parsing errors may contain saved device credentials.
            LOGGER.error("Rivet Auth preparation failed: {} at {}",ex.getClass().getName(),java.util.Arrays.toString(ex.getStackTrace()));
            c.disconnect(Component.literal(Client.text("ui.could_not_prepare_rivet_auth_392160a4")+ex.getClass().getSimpleName()+Client.text("ui.see_latest_log_for_details_425784c0")));
        }
    }
    private static void transportReady(IPayloadContext context){
        if(context.connection()!=connection||pendingTransport==null){context.disconnect(Component.literal(Client.text("message.unexpected_tls_marker")));return;}
        var start=pendingTransport;pendingTransport=null;start.run();
    }
    private static void receive(byte[] data,IPayloadContext c){if(c.connection()!=connection||tunnel==null){c.disconnect(Component.literal(Client.text("message.unexpected_auth_response")));return;}var current=tunnel;execute(()->{try{if(c.connection()!=connection||current!=tunnel)return;current.receive(data);}catch(Exception ex){c.disconnect(Client.tr("ui.secure_auth_connection_interrupted_2e6b679c"));}});}
    private static void message(String text,Connection current){var j=JsonParser.parseString(text).getAsJsonObject();var mc=Minecraft.getInstance();mc.execute(()->{if(connection!=current||current==null||!current.isConnected())return;try{
        String kind=Json.str(j,"kind");
        switch(kind){
            case "offer" -> {offer=j;mc.setScreen(new AuthScreen());String token=Json.opt(saved,"token","");if(!token.isEmpty()&&Json.opt(j,"type","").equals("local")){var request=new JsonObject();request.addProperty("action","device");request.addProperty("token",token);send(request);}else if(Json.str(j,"mode").equals("hybrid")&&mc.getUser().getType()==net.minecraft.client.User.Type.MSA&&j.has("linked")&&j.get("linked").getAsBoolean())official();}
            case "authenticated" -> {if(j.has("token")){saved.addProperty("token",Json.str(j,"token"));save();}authenticated=true;var request=new JsonObject();request.addProperty("action","accept");send(request);mc.setScreen(new GenericMessageScreen(Client.tr("ui.joining_server_a3c424fb")));}
            case "invitation" -> mc.setScreen(AuthAccountScreen.invitation(mc.screen,j));
            case "devices","updated","changed" -> {if(mc.screen instanceof AuthAccountScreen screen)screen.receive(j);}
            default -> {if(mc.screen instanceof AuthScreen screen)screen.receive(j);else if(mc.screen instanceof AuthAccountScreen screen)screen.receive(j);else ServerMenuClient.result=Json.opt(j,"text","");}
        }
    }catch(Exception ex){current.disconnect(Client.tr("ui.auth_error_deb64113"));}});}
    static boolean officialLauncher(){return Minecraft.getInstance().getUser().getType()==net.minecraft.client.User.Type.MSA;}
    static void official(){officialRequest("official",null);}
    static void link(String password){officialRequest("link",password);}
    private static void officialRequest(String action,String password){
        var mc=Minecraft.getInstance();if(!officialLauncher())return;
        String purpose=action.equals("link")?"link":"login";
        String challenge=AuthSecrets.digest("rivet-auth-"+dev.abros.rivet.core.WireProtocols.version("auth")+"\n"+purpose+"\n"+Json.str(saved,"fingerprint")+"\n"+Json.str(offer,"challenge")).substring(0,40);
        Connection current=connection;var screen=mc.screen;
        execute(()->{try{
            if(connection!=current||current==null||!current.isConnected())return;
            mc.getMinecraftSessionService().joinServer(mc.getUser().getProfileId(),mc.getUser().getAccessToken(),challenge);
            mc.execute(()->{if(connection!=current||!current.isConnected()||mc.screen!=screen)return;var request=new JsonObject();request.addProperty("action",action);request.addProperty("officialName",mc.getUser().getName());if(password!=null)request.addProperty("password",password);send(request);});
        }catch(Exception ex){mc.execute(()->{if(connection==current&&mc.screen==screen){var error=new JsonObject();error.addProperty("kind","error");error.addProperty("text",Client.text("ui.could_not_verify_your_minecraft_account_d631c0dc"));if(screen instanceof AuthScreen auth)auth.receive(error);else if(screen instanceof AuthAccountScreen auth)auth.receive(error);}});}});
    }
    static void send(JsonObject j){j.addProperty("language",Minecraft.getInstance().getLanguageManager().getSelected());String encoded=Json.GSON.toJson(j);Connection current=connection;var currentTunnel=tunnel;j.remove("password");j.remove("oldPassword");execute(()->{try{if(current!=connection||currentTunnel!=tunnel||current==null||!current.isConnected())return;if(currentTunnel==null)throw new IllegalStateException();currentTunnel.send(encoded);}catch(Exception ex){var c=connection;if(c!=null)c.disconnect(Client.tr("ui.secure_auth_request_failed_ccb6de6b"));}});}
    private static void execute(Runnable task){try{WORK.execute(task);}catch(RejectedExecutionException ex){var c=connection;if(c!=null)c.disconnect(Client.tr("ui.too_many_auth_requests_d641eafa"));}}
    static int minimumPasswordLength(){return offer.has("minimumPasswordLength")?Math.max(6,Math.min(128,offer.get("minimumPasswordLength").getAsInt())):6;}
    static void cancel(){
        var c=connection;connection=null;authenticated=false;pendingTransport=null;offer=new JsonObject();
        var expired=tunnel;tunnel=null;
        if(c!=null){c.disconnect(Client.tr("ui.sign_in_cancelled_d3133f1f"));c.handleDisconnection();}
        var mc=Minecraft.getInstance();mc.clearDownloadedResourcePacks();
        mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new TitleScreen()));
        execute(()->{if(expired!=null)expired.close();});
    }
    static void forget(){saved.remove("token");try{save();}catch(Exception ignored){}}
    private static void save()throws Exception{Path temp=Files.createTempFile(file.getParent(),"auth-",".tmp");try{AuthTls.privateFile(temp);Files.writeString(temp,Json.GSON.toJson(saved));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
}
