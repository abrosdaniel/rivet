package dev.abros.rivet.core;

import com.google.gson.JsonObject;
import java.util.*;

/** Connection-scoped scheduling and read cache, independent of Minecraft and screen rendering. */
public final class MenuTransport<O> {
    private static final int CAPACITY = 64, BACKGROUND_CAPACITY = 48;
    private static final long READ_TIMEOUT = 15000, ACTION_INTERVAL = 550;
    private record Pending<O>(JsonObject packet, O owner, boolean read, boolean background, long at) {}
    private record Read<O>(JsonObject packet, O owner, long at) {}
    private final ArrayDeque<Pending<O>> outbound = new ArrayDeque<>();
    private final Map<String, Long> dispatched = new LinkedHashMap<>();
    private final Map<String, Read<O>> reads = new LinkedHashMap<>();
    private final MenuReadCache cache = new MenuReadCache();
    private record DeltaRead<O>(Read<O> read,JsonObject baseline,boolean retried){}
    private final Map<String,DeltaRead<O>> deltaReads=new LinkedHashMap<>();
    private final MenuReadCache baselines=new MenuReadCache();
    private boolean pageDeltas;
    public void pageDeltas(boolean enabled){pageDeltas=enabled;if(!enabled)baselines.clear();}

    public boolean enqueue(JsonObject packet, O owner, boolean background, long now) {
        var prepared = MenuCommands.prepare(packet, now);
        boolean read = MenuRequests.read(prepared);
        if (background && MenuRequests.mutation(prepared))
            throw new IllegalArgumentException("Background requests must not mutate data");
        if (read) outbound.removeIf(queued -> {
            boolean same = queued.read && queued.owner == owner && MenuRequests.sameRead(queued.packet, prepared);
            if (same) forget(queued.packet);
            return same;
        });
        if (Json.opt(prepared, "action", "").equals("subscribe"))
            outbound.removeIf(queued -> Json.opt(queued.packet, "action", "").equals("subscribe"));
        if (outbound.size() >= (background ? BACKGROUND_CAPACITY : CAPACITY)) return false;
        if (MenuRequests.mutation(prepared)) clearCache();
        // Cache is connection-wide: background and screen owners share the same snapshot.
        // Keep their requests independent, but never cache an older equivalent reply.
        if (read) reads.values().removeIf(previous -> MenuRequests.sameRead(previous.packet, prepared));
        outbound.add(new Pending<>(prepared, owner, read, background, now));
        if (read && prepared.has("request")) {
            reads.put(Json.str(prepared, "request"), new Read<>(prepared.deepCopy(), owner, now));
            while (reads.size() > CAPACITY) reads.remove(reads.keySet().iterator().next());
        }
        return true;
    }
    public List<JsonObject> dispatch(long now, O currentOwner) {
        reads.values().removeIf(read -> now - read.at >= READ_TIMEOUT);
        deltaReads.values().removeIf(read -> now-read.read.at>=READ_TIMEOUT);
        if (outbound.isEmpty()) return List.of();
        var ready = new ArrayList<JsonObject>();
        // Prefer the visible interaction over periodic background reads, preserving write order.
        var ordered = new ArrayList<>(outbound);
        ordered.sort(Comparator.comparingInt((Pending<O> item) -> item.read && item.background ? 1 : 0));
        outbound.clear();outbound.addAll(ordered);
        var iterator = outbound.iterator();
        while (iterator.hasNext()) {
            var queued = iterator.next();
            if (queued.read && (queued.owner != null && queued.owner != currentOwner || now - queued.at >= READ_TIMEOUT)) {
                iterator.remove(); forget(queued.packet); continue;
            }
            String action = MenuRequestLane.key(queued.packet,pageDeltas);
            if (now - dispatched.getOrDefault(action, -ACTION_INTERVAL) < ACTION_INTERVAL) continue;
            iterator.remove(); dispatched.put(action, now);
            while (dispatched.size() > CAPACITY) dispatched.remove(dispatched.keySet().iterator().next());
            var packet=queued.packet.deepCopy();
            if(pageDeltas&&queued.read&&MenuPageDelta.eligible(packet)&&packet.has("request")){
                String request=Json.str(packet,"request");var prior=deltaReads.get(request);
                var baseline=prior!=null&&prior.retried?null:baselines.get(packet,now);var known=MenuPageDelta.known(baseline);
                if(known!=null)packet.add("knownPage",known);
                deltaReads.put(request,new DeltaRead<>(prior!=null&&prior.retried?prior.read:new Read<>(queued.packet,queued.owner,now),baseline,prior!=null&&prior.retried));
                while(deltaReads.size()>CAPACITY)deltaReads.remove(deltaReads.keySet().iterator().next());
            }
            ready.add(packet);
            PerformanceMetrics.record("client.menu.queue",Math.max(0,now-queued.at)*1000000L,false);
        }
        return List.copyOf(ready);
    }
    public JsonObject receive(JsonObject reply, long now) {
        String request=Json.opt(reply,"request","");var delta=deltaReads.remove(request);
        if(reply.has("pageDelta")){
            if(delta==null||now-delta.read.at>=READ_TIMEOUT)return null; // Closed/expired connection or an unsolicited response.
            try{reply=MenuPageDelta.apply(delta.baseline,reply);}
            catch(RuntimeException invalid){
                if(!delta.retried&&now-delta.read.at<READ_TIMEOUT&&outbound.size()<CAPACITY){
                    var retry=delta.read.packet.deepCopy();retry.remove("knownPage");
                    deltaReads.put(request,new DeltaRead<>(delta.read,null,true));
                    outbound.addFirst(new Pending<>(retry,delta.read.owner,true,false,delta.read.at));return null;
                }
                reply=new JsonObject();reply.addProperty("request",request);reply.addProperty("kind",Json.opt(delta.read.packet,"action","").equals("community")?"community":"result");reply.addProperty("error",true);reply.addProperty("code","UNAVAILABLE");reply.addProperty("text",Messages.text("rivet.core.no_response_click_retry_c724443a"));
            }
        }
        var original = reads.remove(Json.opt(reply, "request", ""));
        if (original != null && now - original.at < READ_TIMEOUT){cache.put(original.packet, reply, now);if(pageDeltas&&MenuPageDelta.eligible(original.packet))baselines.put(original.packet,reply,now);}
        return reply;
    }
    public JsonObject cached(JsonObject packet, long now) { return cache.get(packet, now); }
    public void cancel(O owner) {
        outbound.removeIf(queued -> {
            if (queued.read && queued.owner == owner) { forget(queued.packet); return true; }
            return false;
        });
        reads.values().removeIf(read -> read.owner == owner);
        deltaReads.values().removeIf(read -> read.read.owner == owner);
    }
    public void invalidate(String section, String id) { reads.clear(); cache.invalidate(section, id); }
    public void clearCache() { cache.clear(); baselines.clear(); reads.clear(); }
    public void clear() { outbound.clear(); dispatched.clear(); deltaReads.clear(); clearCache(); }
    public int queued() { return outbound.size(); }
    public int trackedReads() { return reads.size(); }
    private void forget(JsonObject packet) { reads.remove(Json.opt(packet, "request", "")); }
}
