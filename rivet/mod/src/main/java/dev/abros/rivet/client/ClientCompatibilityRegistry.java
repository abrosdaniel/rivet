package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.OptionalIntegration;
/** Optional client capabilities, independent of the built-in map. */
final class ClientCompatibilityRegistry {
 private static OptionalIntegration capability(String mod,String feature,OptionalIntegration.Operation<String> probe){return dev.abros.rivet.compat.IntegrationSupport.capability(mod,feature,probe);}
 private static final OptionalIntegration VOICE=capability("plasmovoice","diagnostics",PlasmoVoiceClientAdapter::probe);
 private static final OptionalIntegration SIMPLE_VOICE=capability("voicechat","diagnostics",SimpleVoiceChatClientAdapter::probe);
 private static final java.util.List<OptionalIntegration> ALL=java.util.List.of(VOICE,SIMPLE_VOICE);
 static void reset(){ALL.forEach(OptionalIntegration::reset);}
 static boolean voiceAvailable(){return VOICE.available()||SIMPLE_VOICE.available();}
 static java.util.List<String> voice(){var lines=new java.util.ArrayList<String>();
  if(net.neoforged.fml.ModList.get().isLoaded("plasmovoice"))lines.addAll(VOICE.call(PlasmoVoiceClientAdapter::inspect,()->java.util.List.of("Plasmo Voice: "+VOICE.status(),Client.text("ui.udp_unknown_18aca873"),Client.text("ui.microphone_unknown_92df460a"))));
  if(net.neoforged.fml.ModList.get().isLoaded("voicechat"))lines.addAll(SIMPLE_VOICE.call(SimpleVoiceChatClientAdapter::inspect,()->java.util.List.of("Simple Voice Chat: "+SIMPLE_VOICE.status(),Client.text("ui.udp_unknown_18aca873"),Client.text("ui.microphone_unknown_92df460a"))));
  if(lines.isEmpty())lines.add(Client.text("voice.unavailable"));return java.util.List.copyOf(lines);
 }
 static JsonArray diagnostics(){var rows=new JsonArray();ALL.forEach(a->{var row=a.diagnostics();var caps=new JsonArray();caps.add(row.get("id").getAsString().split(":",2)[1]);row.add("capabilities",caps);rows.add(row);});return rows;}
 static long revision(){ALL.forEach(OptionalIntegration::available);return ALL.stream().mapToLong(OptionalIntegration::revision).max().orElse(0);}
 static String status(){return diagnostics().asList().stream().map(e->{var j=e.getAsJsonObject();return j.get("id").getAsString()+" · "+j.get("version").getAsString()+" · "+j.get("status").getAsString()+"\n  API: "+j.get("api").getAsString()+(j.get("reason").getAsString().isEmpty()?"":Client.text("ui.reason_437c4e07")+j.get("reason").getAsString());}).collect(java.util.stream.Collectors.joining("\n"));}
 private ClientCompatibilityRegistry(){}
}
