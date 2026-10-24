package dev.abros.rivet.api;

import java.util.*;

/** Trusted in-process integration services. Game-context operations require the server thread.
 * Registrations are owner-scoped and must be closed when the integration disables.
 * Providers retain responsibility for permission checks and for isolating failures of optional APIs.
 */
public interface IntegrationApi extends AutoCloseable {
    record Service<T>(String name,Class<T> type) {
        public Service {Objects.requireNonNull(name);Objects.requireNonNull(type);if(!type.isInterface())throw new IllegalArgumentException("Service type must be an interface");}
    }
    Service<Waystones> WAYSTONES=new Service<>("waystones",Waystones.class);
    Service<Voice> VOICE=new Service<>("voice",Voice.class);
    Service<Permissions> PERMISSIONS=new Service<>("permissions",Permissions.class);
    Service<Identity> IDENTITY=new Service<>("identity",Identity.class);
    Service<Actions> DIAGNOSTICS=new Service<>("diagnostics",Actions.class);
    Service<Actions> ACCESS=new Service<>("access",Actions.class);
    <T> Optional<T> find(Service<T> service,String provider);
    <T> AutoCloseable register(Service<T> service,String provider,T implementation);
    @Override void close();

    interface Waystones {
        boolean available();
        /** Only an online authenticated player may read their currently accessible targets. */
        List<DomainData.Waystone> points(UUID player)throws Exception;
    }
    interface Voice {
        boolean available();
        /** Empty when the provider cannot expose its current mute state. */
        default Optional<Boolean> muted(UUID player){return Optional.empty();}
        void checkMute(UUID player)throws Exception;
        void mute(UUID player,int minutes,String reason)throws Exception;
    }
    interface Permissions {
        long revision();
        /** online=true requires a live player and uses their current world context. */
        Data profile(UUID player,boolean online);
        boolean allowed(UUID player,String permission);
        /** May block; only call from bounded workers, never the server tick. */
        boolean offlineAllowed(UUID player,String permission);
        List<Data> roles();
        /** Server thread only. Provider must authorize the live actor; completion means durable save.
         * Only registered Rivet nodes and global, permanent group assignments may be edited. */
        default java.util.concurrent.CompletionStage<Data> editRole(UUID actor,Data request){
            return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("Role editing unavailable"));
        }
    }
    record Player(UUID uuid,String name){}
    interface Identity {
        Optional<Player> byName(String name);
        Optional<Player> byId(UUID uuid);
        Optional<UUID> verifiedAlias(UUID uuid);
    }
    interface Actions {
        /** Actor must be online and authenticated. Provider checks its operation-specific rights. */
        Data execute(UUID actor,Data request)throws Exception;
    }
    /** Immutable transport-neutral structured data for extensible adapter diagnostics and actions.
     * Supported values: strings, booleans, finite numbers, lists and string-keyed maps and null.
     */
    record Data(Map<String,Object> fields) {
        public Data {fields=copy(fields,0);}
        private static Map<String,Object> copy(Map<?,?> input,int depth){
            if(depth>32)throw new IllegalArgumentException("Integration data is too deeply nested");
            var result=new LinkedHashMap<String,Object>();
            input.forEach((key,value)->{if(!(key instanceof String text))throw new IllegalArgumentException("String key required");result.put(text,value(value,depth+1));});
            return Collections.unmodifiableMap(result);
        }
        private static Object value(Object value,int depth){
            if(depth>32)throw new IllegalArgumentException("Integration data is too deeply nested");
            if(value==null||value instanceof String||value instanceof Boolean)return value;
            if(value instanceof Number n){if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Finite number required");return new java.math.BigDecimal(n.toString());}
            if(value instanceof Map<?,?> map)return copy(map,depth);
            if(value instanceof List<?> list)return list.stream().map(v->value(v,depth+1)).toList();
            throw new IllegalArgumentException("Unsupported integration value");
        }
    }
}
