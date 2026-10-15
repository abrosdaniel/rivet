package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.MapTerritoryJson;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;
import static dev.abros.rivet.api.MapLayersApi.*;

/** Bounded transient objects; no consumer callbacks, persisted private data or foreign write lookup. */
public final class MapLayerRegistry implements AutoCloseable {
    private final Map<String,Registration> layers=new TreeMap<>();
    private long revision,epoch;private int objects;private boolean enabled,closed;
    public MapLayerRegistry(boolean enabled){this.enabled=enabled;}
    public synchronized long revision(){return revision;}
    public synchronized boolean available(){return enabled&&!closed;}
    public synchronized void enabled(boolean value){
        if(closed)return;
        if(enabled!=value){enabled=value;clear();}
    }
    private void clear(){for(var layer:layers.values()){layer.entries.clear();layer.released=true;}layers.clear();objects=0;epoch++;revision++;}
    @Override public synchronized void close(){if(!closed){closed=true;clear();}}
    private void active(){if(closed)throw fail(UNAVAILABLE,"Map layers stopped");if(!enabled)throw fail(MODULE_DISABLED,"Map disabled");}
    public synchronized MapLayersApi owner(String mod){
        active();if(mod==null||!mod.matches("[a-z][a-z0-9_]{1,63}"))throw fail(INVALID_ARGUMENT,"Invalid owner");
        return new Owner(mod,epoch);
    }
    private final class Owner implements MapLayersApi {
        final String mod;final long generation;boolean released;
        Owner(String mod,long generation){this.mod=mod;this.generation=generation;}
        void valid(){active();if(released||generation!=epoch)throw fail(UNAVAILABLE,"Owner handle expired");}
        public Layer register(LayerSpec spec){synchronized(MapLayerRegistry.this){
            valid();Objects.requireNonNull(spec);localId(spec.id());if(spec.id().contains("/"))throw fail(INVALID_ARGUMENT,"Invalid layer ID");text(spec.title(),100);audience(spec.audience());String id=mod+":"+spec.id();
            if(layers.containsKey(id))throw fail(CONFLICT,"Layer already registered");
            if(layers.size()>=256||layers.values().stream().filter(l->l.owner.mod.equals(mod)).count()>=16)throw fail(BUSY,"Layer capacity exceeded");
            var layer=new Registration(this,id,spec);layers.put(id,layer);layer.version=++revision;return layer;
        }}
        public void close(){synchronized(MapLayerRegistry.this){if(released)return;released=true;var owned=layers.values().stream().filter(l->l.owner==this).toList();owned.forEach(Registration::close);}}
    }
    private record Entry(long revision,ObjectSpec value){}
    private final class Registration implements Layer {
        final Owner owner;final String id;final LayerSpec spec;final Map<String,Entry> entries=new TreeMap<>();long version;boolean released;
        Registration(Owner owner,String id,LayerSpec spec){this.owner=owner;this.id=id;this.spec=spec;}
        void valid(){owner.valid();if(released||layers.get(id)!=this)throw fail(UNAVAILABLE,"Layer handle expired");}
        public String id(){return id;}
        public long put(ObjectSpec object,long expectedRevision){synchronized(MapLayerRegistry.this){
            valid();Objects.requireNonNull(object);localId(object.id());audience(object.audience());shape(object.shape());
            var previous=entries.get(object.id());if(expectedRevision<0||expectedRevision!=(previous==null?0:previous.revision()))throw fail(CONFLICT,"Object changed");
            if(previous==null&&(entries.size()>=1024||objects>=8192))throw fail(BUSY,"Object capacity exceeded");
            long next=++revision;entries.put(object.id(),new Entry(next,object));if(previous==null)objects++;version=next;return next;
        }}
        public void remove(String objectId,long expectedRevision){synchronized(MapLayerRegistry.this){
            valid();localId(objectId);var item=entries.get(objectId);if(item==null)throw fail(NOT_FOUND,"Object unavailable");if(item.revision()!=expectedRevision)throw fail(CONFLICT,"Object changed");
            entries.remove(objectId);objects--;version=++revision;
        }}
        public void close(){synchronized(MapLayerRegistry.this){if(released)return;released=true;if(layers.get(id)==this){layers.remove(id);objects-=entries.size();revision++;}entries.clear();}}
    }
    public synchronized Page<LayerView> layers(UUID viewer,String cursor,OptionalLong snapshot){
        active();check(snapshot,cursor);var result=new ArrayList<LayerView>();boolean more=false;
        for(var layer:layers.values())if(layer.id.compareTo(cursor)>0&&allowed(layer.spec.audience(),viewer)){
            if(result.size()==64){more=true;break;}result.add(new LayerView(layer.id,layer.owner.mod,layer.spec.title(),layer.version));
        }
        return new Page<>(result,more?result.getLast().id():"",revision);
    }
    public synchronized Page<ObjectView> objects(UUID viewer,String layerId,String cursor,OptionalLong snapshot){
        active();check(snapshot,cursor);var layer=layers.get(layerId);if(layer==null||!allowed(layer.spec.audience(),viewer))throw fail(NOT_FOUND,"Layer unavailable");
        var result=new ArrayList<ObjectView>();boolean more=false;
        for(var entry:layer.entries.entrySet())if(entry.getKey().compareTo(cursor)>0&&allowed(entry.getValue().value().audience(),viewer)){
            if(result.size()==8){more=true;break;}var value=entry.getValue();result.add(new ObjectView(entry.getKey(),value.revision(),value.value().shape()));
        }
        return new Page<>(result,more?result.getLast().id():"",revision);
    }
    /** One coherent, already filtered projection for the game transport. */
    public record Visible(long revision,List<VisibleLayer> layers) {}
    public record VisibleLayer(LayerView layer,List<ObjectView> objects) {}
    public synchronized Visible visible(UUID viewer){
        active();var result=new ArrayList<VisibleLayer>();
        for(var layer:layers.values())if(allowed(layer.spec.audience(),viewer)){
            var items=new ArrayList<ObjectView>();
            for(var entry:layer.entries.entrySet())if(allowed(entry.getValue().value().audience(),viewer))
                items.add(new ObjectView(entry.getKey(),entry.getValue().revision(),entry.getValue().value().shape()));
            result.add(new VisibleLayer(new LayerView(layer.id,layer.owner.mod,layer.spec.title(),layer.version),List.copyOf(items)));
        }
        return new Visible(revision,List.copyOf(result));
    }
    private void check(OptionalLong snapshot,String cursor){
        if(snapshot==null||cursor==null||cursor.length()>130||!cursor.matches("[a-z0-9_:.\\-/]*"))throw fail(INVALID_ARGUMENT,"Invalid continuation");
        if(!cursor.isEmpty()&&snapshot.isEmpty())throw fail(INVALID_ARGUMENT,"Snapshot required for continuation");
        if(snapshot.isPresent()&&snapshot.getAsLong()!=revision)throw fail(CONFLICT,"Layers changed; refresh snapshot");
    }
    private static boolean allowed(Audience audience,UUID viewer){return audience.publicRead()||viewer!=null&&audience.players().contains(viewer);}
    private static void audience(Audience value){if(value==null||value.players().size()>256||value.publicRead()&&!value.players().isEmpty())throw fail(INVALID_ARGUMENT,"Invalid audience");}
    private static void localId(String id){if(id==null||!id.matches("[a-z0-9_][a-z0-9_./-]{0,63}")||id.contains(".."))throw fail(INVALID_ARGUMENT,"Invalid local ID");}
    private static void text(String value,int limit){if(value==null||value.isBlank()||value.length()>limit||value.codePoints().anyMatch(Character::isISOControl))throw fail(INVALID_ARGUMENT,"Invalid label");}
    private static void shape(DomainData.Shape shape){
        if(shape==null)throw fail(INVALID_ARGUMENT,"Shape required");
        String dimension,name;
        if(shape instanceof DomainData.Marker marker){dimension=marker.dimension();name=marker.name();if(Math.abs((long)marker.x())>30000000||Math.abs((long)marker.z())>30000000||Math.abs((long)marker.y())>2048||marker.icon()==null||!marker.icon().matches("[a-z0-9_.:-]{1,64}"))throw fail(INVALID_ARGUMENT,"Invalid marker");}
        else if(shape instanceof DomainData.Territory territory){dimension=territory.dimension();name=territory.name();if(territory.points().size()>128)throw fail(INVALID_ARGUMENT,"Too many vertices");try{MapTerritoryJson.read(Json.GSON.toJsonTree(territory).getAsJsonObject(),new UUID(0,0));}catch(RuntimeException invalid){throw fail(INVALID_ARGUMENT,"Invalid territory");}}
        else throw fail(INVALID_ARGUMENT,"Unsupported shape");
        text(name,100);if(dimension==null||dimension.length()>160||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw fail(INVALID_ARGUMENT,"Invalid dimension");
    }
    private static ApiException fail(ApiException.Code code,String message){return new ApiException(code,message);}
}
