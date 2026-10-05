package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.OptionalIntegration;
/** Independent client capabilities: a waypoint editor failure never disables live navigation. */
final class ClientCompatibilityRegistry {
 private static OptionalIntegration capability(String mod,String feature,OptionalIntegration.Operation<String> probe){return dev.abros.rivet.compat.IntegrationSupport.capability(mod,feature,probe);}
 private static final OptionalIntegration LAYERS=capability("xaerominimap","layers",()->{ManagedXaeroLayer.probe();return "ThirdPartyWaypoints · live layers";}),EDITOR=capability("xaerominimap","editor",()->{XaeroBridge.probe();return "GuiAddWaypoint · public editor";}),WORLD_MAP=capability("xaeroworldmap","camera",XaeroMapBridge::probe),VOICE=capability("plasmovoice","diagnostics",PlasmoVoiceClientAdapter::probe);
 private static final java.util.List<OptionalIntegration> ALL=java.util.List.of(LAYERS,EDITOR,WORLD_MAP,VOICE);
 static boolean xaeroAvailable(){return LAYERS.available();}static boolean xaeroEditorAvailable(){return EDITOR.available();}
 static void tickWorldMap(){XaeroMapBridge.tick();}
 static boolean worldMapAvailable(){return WORLD_MAP.available();}
 static void openWorldMap(net.minecraft.client.gui.screens.Screen parent,dev.abros.rivet.core.CommunityLocation place)throws Exception{WORLD_MAP.required(()->{XaeroMapBridge.open(parent,place);return true;});}
 static void editMap(net.minecraft.client.gui.screens.Screen parent,dev.abros.rivet.core.CommunityLocation place,int mode)throws Exception{EDITOR.required(()->{XaeroBridge.edit(parent,place,mode);return true;});}
 static void updateMap(JsonArray places,JsonArray players){LAYERS.call(()->{ManagedXaeroLayer.update(places,players);return true;},()->false);if(!LAYERS.available())clearMap();}
 static void clearMap(){ManagedXaeroLayer.clear();}
 static void reset(){clearMap();XaeroMapBridge.reset();ALL.forEach(OptionalIntegration::reset);}
 static java.util.List<String> voice(){return VOICE.call(PlasmoVoiceClientAdapter::inspect,()->java.util.List.of("Plasmo Voice: "+VOICE.status(),"UDP: неизвестно","Микрофон: неизвестно"));}
 static JsonArray diagnostics(){var rows=new JsonArray();ALL.forEach(a->{var row=a.diagnostics();var caps=new JsonArray();caps.add(row.get("id").getAsString().split(":",2)[1]);row.add("capabilities",caps);rows.add(row);});return rows;}
 static long revision(){ALL.forEach(OptionalIntegration::available);return ALL.stream().mapToLong(OptionalIntegration::revision).max().orElse(0);}
 static String status(){return diagnostics().asList().stream().map(e->{var j=e.getAsJsonObject();return j.get("id").getAsString()+" · "+j.get("version").getAsString()+" · "+j.get("status").getAsString()+"\n  API: "+j.get("api").getAsString()+(j.get("reason").getAsString().isEmpty()?"":"\n  Причина: "+j.get("reason").getAsString());}).collect(java.util.stream.Collectors.joining("\n"));}
 private ClientCompatibilityRegistry(){}
}
