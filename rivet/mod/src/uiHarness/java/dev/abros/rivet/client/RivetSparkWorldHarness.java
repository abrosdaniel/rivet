package dev.abros.rivet.client;

import com.google.gson.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in local-world fixture, excluded from release jars. Uses real packets and actual spark. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RivetSparkWorldHarness {
    private static boolean loading,opened,started,verified;private static long ready;private static int stage,audit;private static long nextAudit;
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(System.getenv("RIVET_SPARK_WORLD")==null)return;var mc=Minecraft.getInstance();
        if(!loading&&mc.screen instanceof TitleScreen){loading=true;
            var data=new net.minecraft.client.multiplayer.ServerData("Rivet · spark test","127.0.0.1:25569",net.minecraft.client.multiplayer.ServerData.Type.OTHER);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip),data,false,null);
        }
        if(mc.player==null||!ServerMenuClient.supports("spark-diagnostics")||!ServerMenuClient.may("rivet.diagnostics"))return;
        if(!opened){opened=true;ready=System.currentTimeMillis()+7000;mc.setScreen(new SparkDiagnosticsScreen(new ServerMenuScreen(null,"admin")));System.out.println("RIVET_SPARK_WORLD_READY");}
        if(System.getenv("RIVET_SPARK_PREVIEW_ONLY")!=null)return;
        if(System.getenv("RIVET_SPARK_UI_ONLY")!=null){stage=6;if(System.currentTimeMillis()>ready)audit(mc);capture(mc);return;}
        if(!started&&System.currentTimeMillis()>=ready){started=true;query("snapshot").whenComplete((reply,error)->{
            if(error!=null){error.printStackTrace();return;}
            check(reply.get("installed").getAsBoolean(),"spark missing");check(reply.get("status").getAsString().equals("Работает"),"API not working: "+reply);
            var sample=reply.getAsJsonObject("current");check(sample.has("tps")&&sample.has("mspt")&&sample.has("cpuProcess"),"Missing metrics");
            check(reply.toString().length()<32767,"Packet oversized");System.out.println("RIVET_SPARK_API_OK "+sample);
            if(dev.abros.rivet.core.Json.opt(reply,"profiler","").equals("idle"))query("start").whenComplete((result,failure)->{if(failure!=null)failure.printStackTrace();else System.out.println("RIVET_SPARK_START_REQUEST_OK");});
        });}
        if(started&&!verified&&System.currentTimeMillis()>ready+12000){verified=true;query("snapshot").whenComplete((reply,error)->{
            if(error!=null){error.printStackTrace();return;}
            System.out.println("RIVET_SPARK_CONTROL_STATE "+reply.get("profiler")+" owner="+reply.get("owner")+" notice="+reply.get("notice"));
            if(reply.get("owned").getAsBoolean()&&reply.get("profiler").getAsString().equals("running"))query("cancel").whenComplete((result,failure)->{if(failure!=null)failure.printStackTrace();else System.out.println("RIVET_SPARK_CANCEL_REQUEST_OK");});
            if(mc.screen instanceof SparkDiagnosticsScreen){UiGeometryHarness.verify(mc.screen);net.minecraft.client.Screenshot.grab(new java.io.File("/private/tmp/rivet-spark-preview"),"spark-state.png",mc.getMainRenderTarget(),message->{});}
        });}
        if(verified&&stage==0&&System.currentTimeMillis()>ready+17000){stage=1;query("snapshot").thenAccept(reply->{check(reply.get("profiler").getAsString().equals("idle"),"Cancel did not stop profile");check(!reply.get("owned").getAsBoolean(),"Lease not cleared");System.out.println("RIVET_SPARK_CANCEL_VERIFIED");mc.player.connection.sendCommand("spark profiler start --force-java-sampler --timeout 60");});}
        if(stage==1&&System.currentTimeMillis()>ready+23000){stage=2;query("start").whenComplete((reply,error)->{if(error!=null)error.printStackTrace();});}
        if(stage==2&&System.currentTimeMillis()>ready+29000){stage=3;query("snapshot").thenAccept(reply->{check(reply.get("profiler").getAsString().equals("running"),"Foreign profile missing");check(!reply.get("owned").getAsBoolean(),"Foreign profile ownership taken");check(reply.get("notice").getAsString().contains("уже записывает"),"Foreign profile not refused");System.out.println("RIVET_SPARK_FOREIGN_PROFILE_PROTECTED");query("cancel");});}
        if(stage==3&&System.currentTimeMillis()>ready+34000){stage=4;query("snapshot").thenAccept(reply->{check(reply.get("profiler").getAsString().equals("running"),"Foreign profile cancelled");mc.player.connection.sendCommand("spark profiler cancel");System.out.println("RIVET_SPARK_FOREIGN_CANCEL_REFUSED");});}
        if(stage==4&&System.currentTimeMillis()>ready+39000){stage=5;query("health").whenComplete((reply,error)->{if(error!=null)error.printStackTrace();});}
        if(stage==5&&System.currentTimeMillis()>ready+56000){stage=6;query("snapshot").thenAccept(reply->{check(!reply.getAsJsonArray("reports").isEmpty(),"Health report was not saved");System.out.println("RIVET_SPARK_HEALTH_STATE reports="+reply.getAsJsonArray("reports").size()+" notice="+reply.get("notice"));if(mc.screen instanceof SparkDiagnosticsScreen){UiGeometryHarness.verify(mc.screen);net.minecraft.client.Screenshot.grab(new java.io.File("/private/tmp/rivet-spark-preview"),"spark-final.png",mc.getMainRenderTarget(),message->{});}});}
        audit(mc);
    }
    private static void audit(Minecraft mc){
        if(stage==6&&audit<19&&System.currentTimeMillis()>nextAudit&&mc.screen instanceof SparkDiagnosticsScreen screen){nextAudit=System.currentTimeMillis()+600;
            if(audit<8){tab(screen,"Состояние");screen.restoreScroll(audit);}else if(audit<17){tab(screen,"Профилирование");screen.restoreScroll(audit-8);}else if(audit==17){tab(screen,"Отчёты");screen.restoreScroll(0);}else{tab(screen,"Состояние");screen.restoreScroll(0);System.out.println("RIVET_SPARK_ALL_TABS_AND_ROWS_OK");}
            screen.rebuildWidgets();UiGeometryHarness.verify(screen);audit++;
            if(audit==19)nextAudit=System.currentTimeMillis()+1000;
        }
    }
    private static boolean captured;
    private static void capture(Minecraft mc){if(!captured&&audit==19&&System.currentTimeMillis()>nextAudit){captured=true;net.minecraft.client.Screenshot.grab(new java.io.File("/private/tmp/rivet-spark-preview"),"spark-compact.png",mc.getMainRenderTarget(),message->{});}}
    private static void tab(SparkDiagnosticsScreen screen,String text){for(var child:List.copyOf(screen.children()))if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals(text)){button.onPress();return;}throw new IllegalStateException("Missing tab "+text);}
    private static java.util.concurrent.CompletableFuture<JsonObject> query(String op){var j=new JsonObject();j.addProperty("op",op);j.addProperty("seconds",60);j.addProperty("threshold",50);return CompatibilityClient.request("spark",j);}
    private static void check(boolean valid,String text){if(!valid)throw new IllegalStateException(text);}
}
