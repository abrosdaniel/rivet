package dev.abros.rivet.core.skins;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Connection-scoped, bounded public identity lookups. Owned by the client thread. */
public final class SkinProfileLookup {
 public record Identity(UUID id,String name){}
 private record Pending(String key,String request,long expires,CompletableFuture<Optional<Identity>> future){}
 private record Cached(Optional<Identity> identity,long expires){}
 private final Map<String,Pending> pending=new HashMap<>();
 private final Map<String,Cached> cache=new LinkedHashMap<>(256,.75f,true);
 public CompletableFuture<Optional<Identity>> resolve(String key,long now,BiConsumer<String,String> sender){
  tick(now);var cached=cache.get(key);if(cached!=null&&cached.expires>now)return CompletableFuture.completedFuture(cached.identity);
  // Keep a confirmed identity visible until its refresh completes; a pending request is not a deletion.
  Optional<Identity> previous=cached==null?Optional.empty():cached.identity;if(previous.isEmpty())cache.remove(key);
  var existing=pending.get(key);if(existing!=null)return previous.isPresent()?CompletableFuture.completedFuture(previous):existing.future;
  if(pending.size()>=64)return CompletableFuture.completedFuture(previous);
  var item=new Pending(key,UUID.randomUUID().toString(),now+5000,new CompletableFuture<>());pending.put(key,item);
  try{sender.accept(key,item.request);}catch(RuntimeException failure){pending.remove(key);item.future.complete(Optional.empty());}return previous.isPresent()?CompletableFuture.completedFuture(previous):item.future;
 }
 public boolean receive(String request,Optional<Identity> identity,long now){
  var item=pending.values().stream().filter(p->p.request.equals(request)).findFirst().orElse(null);if(item==null)return false;
  if(now>=item.expires){pending.remove(item.key);item.future.complete(Optional.empty());return false;}
  pending.remove(item.key);cache.put(item.key,new Cached(identity,now+(identity.isPresent()?60000:2000)));
  while(cache.size()>256)cache.remove(cache.keySet().iterator().next());item.future.complete(identity);return true;
 }
 public void tick(long now){var expired=new ArrayList<Pending>();pending.values().removeIf(item->{if(now<item.expires)return false;expired.add(item);return true;});for(var item:expired)item.future.complete(Optional.empty());}
 public void clear(){var old=new ArrayList<>(pending.values());pending.clear();cache.clear();for(var item:old)item.future.complete(Optional.empty());}
}
