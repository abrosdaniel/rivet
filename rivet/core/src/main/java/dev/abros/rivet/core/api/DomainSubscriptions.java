package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.Hashes;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Bounded current-state cursors. Never retains historical private payloads or session tokens. */
public final class DomainSubscriptions {
    private static final int CAPACITY=256,WINDOW=256;
    private static final long RETENTION=600_000_000_000L;
    private final DomainRest domains;private final java.util.function.LongSupplier revision;
    private final Object[] reads=java.util.stream.IntStream.range(0,32).mapToObj(i->new Object()).toArray();
    private final LinkedHashMap<String,State> states=new LinkedHashMap<>(16,.75f,true);
    private static final class State {
        final UUID stream=UUID.randomUUID();String digest="";long sequence,used=System.nanoTime();
    }
    public record Target(String path,Map<String,String> query,String session){
        public Target {
            if(path==null||!path.matches("[A-Za-z0-9_/-]{1,160}")||query==null||query.size()>1||query.containsKey("cursor")||session!=null&&!session.matches("[A-Za-z0-9_-]{43}"))throw ApiJson.invalid();
            query=Map.copyOf(query);
            for(var e:query.entrySet())if(e.getKey().length()>32||e.getValue().length()>80)throw ApiJson.invalid();
        }
    }
    public DomainSubscriptions(DomainRest domains){this(domains,()->0);}
    public DomainSubscriptions(DomainRest domains,java.util.function.LongSupplier revision){this.domains=Objects.requireNonNull(domains);this.revision=Objects.requireNonNull(revision);}
    public long revision(){return revision.getAsLong();}
    public JsonObject snapshot(ApiKeys.Grant grant,Target target,JsonObject previous)throws Exception{
        String identity=hash(grant.keyId()+"\n"+Objects.toString(target.session(),"")+"\n"+target.path()+"\n"+new TreeMap<>(target.query()));
        // Serialize identical reads, so a slower old read cannot overwrite a newer cursor.
        synchronized(reads[Math.floorMod(identity.hashCode(),reads.length)]){
            return read(grant,target,previous,identity);
        }
    }
    private JsonObject read(ApiKeys.Grant grant,Target target,JsonObject previous,String identity)throws Exception{
        // Read and authorize even when the caller's cursor is unchanged. A cursor never grants access.
        JsonObject payload=domains.subscription(grant,target.path(),target.query(),target.session());
        String digest=hash(payload.toString());
        synchronized(states){
            long now=System.nanoTime();states.values().removeIf(s->now-s.used>RETENTION);
            State state=states.get(identity);
            if(state==null){if(states.size()>=CAPACITY)states.remove(states.keySet().iterator().next());state=new State();states.put(identity,state);}
            state.used=now;
            if(!state.digest.equals(digest)){state.digest=digest;state.sequence++;}
            if(previous!=null){
                ApiJson.fields(previous,"stream","sequence");
                long sequence;
                try{sequence=previous.get("sequence").getAsBigDecimal().longValueExact();}catch(RuntimeException invalid){throw ApiJson.invalid();}
                if(!state.stream.toString().equals(ApiJson.string(previous,"stream"))||sequence<Math.max(1,state.sequence-WINDOW)||sequence>state.sequence)throw new ApiException(RESYNC_REQUIRED,"Fresh subscription snapshot required");
            }
            var cursor=new JsonObject();cursor.addProperty("stream",state.stream.toString());cursor.addProperty("sequence",state.sequence);
            var result=new JsonObject();result.add("cursor",cursor);result.add("resource",payload);return result;
        }
    }
    private static String hash(String text){return Hashes.sha256(text.getBytes(StandardCharsets.UTF_8));}
    public void clear(){synchronized(states){states.clear();}}
}
