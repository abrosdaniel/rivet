package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import java.util.*;

/** One server incarnation. Leases cannot remove a later owner's registration. */
public final class IntegrationRegistry implements AutoCloseable {
 private record Key(IntegrationApi.Service<?> service,String id){}
 private static final class Entry {
  private final Object lease,provider;
  Entry(Object lease,Object provider){this.lease=lease;this.provider=provider;}
  Object lease(){return lease;}Object provider(){return provider;}
 }
 private record Cached(Entry entry,Object proxy){}
 private final Map<Key,Entry> entries=new HashMap<>();
 private boolean closed;
 public synchronized IntegrationApi owner(String owner){
  check();if(owner==null||!owner.matches("[a-z][a-z0-9_]{1,63}"))throw new IllegalArgumentException("Invalid owner");
  return new IntegrationApi(){
   private final Object lease=new Object();private boolean released;
   private final Map<Key,Cached> handles=new HashMap<>();
   private void active(){check();if(released)throw new ApiException(ApiException.Code.UNAVAILABLE,"Integration lease closed");}
   public <T> Optional<T> find(Service<T> service,String provider){synchronized(IntegrationRegistry.this){active();var entry=entries.get(new Key(service,provider));if(entry==null){handles.remove(new Key(service,provider));return Optional.empty();}
    var key=new Key(service,provider);var cached=handles.get(key);if(cached!=null&&cached.entry()==entry)return Optional.of(service.type().cast(cached.proxy()));
    var handle=service.type().cast(java.lang.reflect.Proxy.newProxyInstance(service.type().getClassLoader(),new Class<?>[]{service.type()},(proxy,method,args)->{
     synchronized(IntegrationRegistry.this){active();if(entries.get(new Key(service,provider))!=entry)throw new ApiException(ApiException.Code.UNAVAILABLE,"Provider unregistered");}
     try{return method.invoke(entry.provider(),args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
    }));handles.put(key,new Cached(entry,handle));return Optional.of(handle);}}
   public <T> AutoCloseable register(Service<T> service,String provider,T implementation){synchronized(IntegrationRegistry.this){active();Objects.requireNonNull(implementation);service.type().cast(implementation);if(!provider.equals(owner))throw new IllegalArgumentException("Provider must use its owner namespace");var key=new Key(service,provider);var entry=new Entry(lease,implementation);if(entries.putIfAbsent(key,entry)!=null)throw new IllegalStateException("Provider already registered");return ()->{synchronized(IntegrationRegistry.this){entries.remove(key,entry);}};}}
   public void close(){synchronized(IntegrationRegistry.this){if(released)return;released=true;handles.clear();entries.values().removeIf(e->e.lease()==lease);}}
  };
 }
 private void check(){if(closed)throw new ApiException(ApiException.Code.UNAVAILABLE,"Integration registry stopped");}
 public synchronized void close(){closed=true;entries.clear();}
}
