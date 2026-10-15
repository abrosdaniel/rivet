package dev.abros.rivet.core.api;

import dev.abros.rivet.core.ServerSettings;
import java.net.URI;
import java.util.Set;

/** The initial listener is deliberately local-only; TLS terminates at an owner's reverse proxy. */
public record RestSettings(boolean enabled, String bind, int port, int connections, int connectionsPerAddress,
                           int workers, int queue, int timeoutSeconds, int requestsPerMinute,
                           int addressRequestsPerMinute, Set<String> origins, Set<String> publicResources) {
    public static final Set<String> RESOURCES = Set.of("capabilities", "server.status", "server.status.events", "openapi");
    public RestSettings {
        origins=Set.copyOf(origins);publicResources=Set.copyOf(publicResources);
        if(!Set.of("127.0.0.1","::1").contains(bind))throw new IllegalArgumentException("api.http.bind: loopback only (127.0.0.1 or ::1)");
        range(port,1,65535);range(connections,1,256);range(connectionsPerAddress,1,connections);
        range(workers,1,8);range(queue,1,256);range(timeoutSeconds,1,30);
        range(requestsPerMinute,1,100000);range(addressRequestsPerMinute,1,requestsPerMinute);
        if(origins.size()>32||!RESOURCES.containsAll(publicResources))throw new IllegalArgumentException("Invalid API HTTP allowlist");
        for(String origin:origins){
            URI uri;
            try{uri=URI.create(origin);}catch(Exception invalid){throw new IllegalArgumentException("Invalid API HTTP origin");}
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||!uri.getRawPath().isEmpty()||uri.getPort()==0||uri.getPort()>65535||origin.length()>256)
                throw new IllegalArgumentException("API origin must be an exact HTTPS origin without path");
        }
    }
    private static void range(int n,int min,int max){if(n<min||n>max)throw new IllegalArgumentException("Invalid API HTTP limit");}
    public static RestSettings from(ServerSettings s){
        return new RestSettings(s.flag("api.http.enabled"),s.text("api.http.bind"),s.number("api.http.port"),s.number("api.http.connections"),s.number("api.http.connectionsPerAddress"),s.number("api.http.workers"),s.number("api.http.queue"),s.number("api.http.timeoutSeconds"),s.number("api.http.requestsPerMinute"),s.number("api.http.addressRequestsPerMinute"),Set.copyOf(s.strings("api.http.origins")),Set.copyOf(s.strings("api.http.publicResources")));
    }
}
