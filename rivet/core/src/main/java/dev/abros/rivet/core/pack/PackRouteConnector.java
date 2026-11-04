package dev.abros.rivet.core.pack;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded staggered TLS race. Untrusted status addresses never bypass certificate pinning. */
final class PackRouteConnector implements AutoCloseable {
 record Connected(SSLSocket socket,PackDiscovery.Endpoint endpoint){}
 private final Set<SSLSocket> sockets=ConcurrentHashMap.newKeySet();
 private final AtomicBoolean closed=new AtomicBoolean();
 Connected connect(List<PackDiscovery.Endpoint> routes,SSLSocketFactory factory,AtomicBoolean cancel)throws IOException{
  var result=new LinkedBlockingQueue<Object>();var done=new AtomicBoolean();var tasks=new ArrayList<Future<?>>();
  var executor=Executors.newVirtualThreadPerTaskExecutor();Connected winner=null;
  try{
   for(int i=0;i<routes.size();i++){var route=routes.get(i);long delay=i*250L;tasks.add(executor.submit(()->{
    SSLSocket socket=null;
    try{Thread.sleep(delay);if(done.get()||closed.get()||cancel.get())return;
     socket=(SSLSocket)factory.createSocket();sockets.add(socket);if(done.get()||closed.get()||cancel.get())return;
     socket.connect(new InetSocketAddress(route.host(),route.port()),5000);socket.setSoTimeout(15000);socket.setEnabledProtocols(new String[]{"TLSv1.3"});socket.startHandshake();
     if(done.compareAndSet(false,true)){result.offer(new Connected(socket,route));socket=null;}
    }catch(Exception ex){result.offer(new IOException(route.host()+":"+route.port()+" — "+ex.getClass().getSimpleName()+": "+ex.getMessage(),ex));}
    finally{if(socket!=null){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}}
   }));}
   var failure=new IOException(dev.abros.rivet.core.Messages.text("rivet.pack.routesUnavailable"));int failures=0;
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(23);
   while(failures<routes.size()&&System.nanoTime()<deadline){
    if(closed.get()||cancel.get())throw new InterruptedIOException("Cancelled");
    Object value=result.poll(100,TimeUnit.MILLISECONDS);
    if(value instanceof Connected c){winner=c;return c;}
    if(value instanceof IOException e){failure.addSuppressed(e);failures++;}
   }
   String details=Arrays.stream(failure.getSuppressed()).map(Throwable::getMessage).collect(java.util.stream.Collectors.joining("\n"));throw new IOException(failure.getMessage()+"\n"+details,failure);
  }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new InterruptedIOException("Cancelled");}
  finally{done.set(true);for(var task:tasks)task.cancel(true);executor.shutdownNow();for(var socket:sockets)if(winner==null||socket!=winner.socket()){try{socket.close();}catch(IOException ignored){}sockets.remove(socket);}}
 }
 void release(SSLSocket socket){sockets.remove(socket);}
 public void close(){closed.set(true);for(var socket:sockets)try{socket.close();}catch(IOException ignored){}}
}
