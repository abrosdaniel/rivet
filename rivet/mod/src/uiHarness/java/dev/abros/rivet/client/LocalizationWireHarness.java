package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.Messages;
import dev.abros.rivet.network.Protocol;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only real-wire error and command responses after switching the negotiated client language. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class LocalizationWireHarness {
 private static Consumer<JsonObject> previous;
 private static String original,request,expected;private static int phase,checked,baseline;private static long next,deadline;private static boolean replied,done;
 private static final String[] LANGUAGES={"en_us","ru_ru","en_us"};
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_LOCALIZATION_WIRE")==null||done)return;
  var mc=Minecraft.getInstance();try{
   if(mc.player==null||!ServerMenuClient.supports("compatibility-adapters"))return;
   long now=System.currentTimeMillis();
   if(previous==null){original=mc.options.languageCode;previous=Protocol.featureState;Protocol.featureState=j->{if(request!=null&&request.equals(Json.opt(j,"request",""))){String actual=Json.opt(j,"error","");if(!actual.equals(expected))throw new IllegalStateException("Wrong server response language: "+actual);replied=true;}previous.accept(j);};deadline=now+30000;}
   if(now>deadline)throw new IllegalStateException("Localization wire timeout at "+phase);
   if(now<next)return;
   if(phase%3==0){mc.options.languageCode=LANGUAGES[phase/3];mc.options.broadcastOptions();next=now+500;phase++;return;}
   if(phase%3==1){
    try(var locale=Messages.locale(LANGUAGES[phase/3])){expected=Messages.text("rivet.core.adapter_not_found_1315ccb9");}
    request=java.util.UUID.randomUUID().toString();replied=false;var j=new JsonObject();j.addProperty("action","compatibility");j.addProperty("adapter","rivet_missing_test_adapter");j.addProperty("request",request);
    PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(j)));
    baseline=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages().size();
    mc.getConnection().sendCommand("rivet compatibility");next=now+500;phase++;return;
   }
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
   if(!replied||rows.size()<=baseline)return;
   String command=rows.getFirst().content().getString();if(!command.contains("waystones (map):"))return;
   if(LANGUAGES[phase/3].equals("en_us")&&command.matches("(?s).*[А-Яа-яЁё].*"))throw new IllegalStateException("Russian command status in English: "+command);
   checked+=2;if(++phase==9){System.out.println("RIVET_LOCALIZATION_WIRE_OK checks="+checked+" EN/RU/EN without reconnect");finish(mc);}
  }catch(Throwable ex){System.out.println("RIVET_LOCALIZATION_WIRE_FAILED");ex.printStackTrace();finish(mc);}
 }
 private static void finish(Minecraft mc){done=true;if(previous!=null)Protocol.featureState=previous;if(original!=null){mc.options.languageCode=original;mc.options.broadcastOptions();}mc.stop();}
}
