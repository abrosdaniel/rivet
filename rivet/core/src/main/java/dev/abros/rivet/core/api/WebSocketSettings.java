package dev.abros.rivet.core.api;
import dev.abros.rivet.core.ServerSettings;
import java.util.Set;
public record WebSocketSettings(boolean enabled,String bind,int port,int connections,int requestsPerMinute){
 public WebSocketSettings{if(!Set.of("127.0.0.1","::1").contains(bind)||port<1||port>65535||connections<1||connections>32||requestsPerMinute<1||requestsPerMinute>6000)throw new IllegalArgumentException("Invalid API WebSocket settings");}
 public static WebSocketSettings from(ServerSettings s){return new WebSocketSettings(s.flag("api.websocket.enabled"),s.text("api.websocket.bind"),s.number("api.websocket.port"),s.number("api.websocket.connections"),s.number("api.websocket.requestsPerMinute"));}
}
