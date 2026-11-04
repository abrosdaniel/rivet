package dev.abros.rivet.core;

import java.util.*;
import java.util.function.BiConsumer;

/** Bounded latest-value writeback. Values must be immutable snapshots; drain has one worker. */
public final class RetryingWrites<K,V> {
 @FunctionalInterface public interface Writer<V>{void write(V value)throws Exception;}
 private record Entry<V>(V value,Writer<V> writer){}
 private final int capacity;
 private final Map<K,Entry<V>> entries=new LinkedHashMap<>();
 private final Map<K,Long> retryAt=new HashMap<>(),reportedAt=new HashMap<>();
 private final Map<K,Integer> failures=new HashMap<>();
 public RetryingWrites(int capacity){if(capacity<1)throw new IllegalArgumentException();this.capacity=capacity;}
 public synchronized boolean put(K key,V value,Writer<V> writer){
  if(!entries.containsKey(key)&&entries.size()>=capacity)return false;
  entries.put(key,new Entry<>(value,writer));return true;
 }
 public synchronized V pending(K key){var entry=entries.get(key);return entry==null?null:entry.value();}
 public synchronized int size(){return entries.size();}
 public void drain(long now,int limit,boolean force,BiConsumer<K,Exception> failure){
  List<Map.Entry<K,Entry<V>>> batch;
  synchronized(this){batch=entries.entrySet().stream().filter(e->force||now>=retryAt.getOrDefault(e.getKey(),0L)).limit(limit).map(e->Map.entry(e.getKey(),e.getValue())).toList();}
  for(var item:batch){Exception error=null;try{item.getValue().writer().write(item.getValue().value());}catch(Exception ex){error=ex;}
   boolean report=false;
   synchronized(this){if(entries.get(item.getKey())==item.getValue()){
    K key=item.getKey();
    if(error==null){entries.remove(key);retryAt.remove(key);failures.remove(key);reportedAt.remove(key);}
    else {int count=failures.merge(key,1,Integer::sum);retryAt.put(key,now+Math.min(300000L,5000L<<Math.min(6,count-1)));
     if(!reportedAt.containsKey(key)||now-reportedAt.get(key)>=60000){reportedAt.put(key,now);report=true;}}
   }}
   if(report)failure.accept(item.getKey(),error);
  }
 }
}
