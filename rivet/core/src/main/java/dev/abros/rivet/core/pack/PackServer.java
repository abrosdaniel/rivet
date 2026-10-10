package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.AuthTls;
import com.google.gson.*;
import javax.net.ssl.*;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Owns the publication listener and bounded transfer workers, independently of game ticks. */
public final class PackServer implements AutoCloseable {
 private final PackPublisher publisher;private final SSLServerSocket listener;private final ExecutorService workers;private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();private final Semaphore slots;
 private final Thread acceptor;private final AtomicBoolean closed=new AtomicBoolean();private final Budget total;private final int clientMiB;private final boolean required;private final String fingerprint;
 private static final class Budget {private final long rate;private long at;Budget(int mib){rate=(long)mib*1024*1024;}void take(int bytes)throws InterruptedException{if(rate==0)return;long until;synchronized(this){at=Math.max(at,System.nanoTime())+bytes*1_000_000_000L/rate;until=at;}while(until>System.nanoTime()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();TimeUnit.NANOSECONDS.sleep(Math.min(until-System.nanoTime(),50_000_000));}}}
 public PackServer(PackPublisher publisher,Path identity,String bind,int port,boolean required,int concurrent,int totalMiB,int clientMiB)throws Exception{
  this.publisher=publisher;this.required=required;this.clientMiB=clientMiB;total=new Budget(totalMiB);slots=concurrent==0?null:new Semaphore(concurrent,true);
  var tls=AuthTls.identity(identity);fingerprint=tls.fingerprint();listener=(SSLServerSocket)tls.context().getServerSocketFactory().createServerSocket();listener.setEnabledProtocols(new String[]{"TLSv1.3"});
  workers=new ThreadPoolExecutor(0,64,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"Rivet pack transfer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  try{listener.bind(new InetSocketAddress(bind,port));}catch(Exception ex){workers.shutdownNow();listener.close();throw ex;}
  acceptor=new Thread(this::accept,"Rivet pack listener");acceptor.setDaemon(true);acceptor.start();
 }
 public String fingerprint(){return fingerprint;}public int port(){return listener.getLocalPort();}public boolean required(){return required;}
 private void accept(){while(!closed.get())try{Socket socket=listener.accept();sockets.add(socket);try{workers.execute(()->serve(socket));}catch(RejectedExecutionException busy){sockets.remove(socket);socket.close();}}catch(IOException ex){if(!closed.get())System.getLogger(PackServer.class.getName()).log(System.Logger.Level.WARNING,dev.abros.rivet.core.Messages.text("rivet.core.modpack_listener_error_31604241"),ex);}}
 private void serve(Socket socket){try(socket){socket.setSoTimeout(15000);((SSLSocket)socket).startHandshake();var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());var budget=new Budget(clientMiB);
  for(int requests=0;requests<10001&&!closed.get();requests++){
   JsonObject request;try{request=PackWire.read(in,16384);}catch(EOFException done){return;}
   String action=Json.str(request,"action");
   if(action.equals("manifest")){var current=publisher.current();if(current!=null&&(!request.has("manifestProtocol")||!request.get("manifestProtocol").isJsonPrimitive()||!request.get("manifestProtocol").getAsJsonPrimitive().isNumber()||request.get("manifestProtocol").getAsInt()<2))current=current.legacy();var response=new JsonObject();response.addProperty("required",required);response.addProperty("ready",current!=null);if(current!=null){response.addProperty("hash",current.hash());response.add("manifest",Json.parse(new String(current.bytes(),java.nio.charset.StandardCharsets.UTF_8)));}PackWire.write(out,response);continue;}
   if(!action.equals("file"))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_modpack_request_6cec51c2"));var publication=publisher.load(Json.str(request,"publication"));String hash=Json.str(request,"hash");Hashes.check(hash);var file=publication.files().stream().filter(f->f.hash().equals(hash)).findFirst().orElseThrow(()->new IOException(dev.abros.rivet.core.Messages.text("rivet.core.file_missing_from_publication_34ae98c2")));long offset=request.get("offset").getAsBigDecimal().longValueExact();if(offset<0||offset>file.size())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_file_position_4958ba8a"));
   boolean acquired=false;try{if(slots!=null){var wait=new JsonObject();wait.addProperty("queued",true);PackWire.write(out,wait);while(!closed.get()&&!slots.tryAcquire(1,TimeUnit.SECONDS)){}if(closed.get())return;acquired=true;}
    Path object=publisher.object(hash);if(Files.size(object)!=file.size()||!Hashes.sha256(object).equals(hash))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.published_file_is_corrupt_cdfe5556"));var response=new JsonObject();response.addProperty("bytes",file.size()-offset);PackWire.write(out,response);
    try(var stream=Files.newInputStream(object)){stream.skipNBytes(offset);byte[] buffer=new byte[65536];long left=file.size()-offset;while(left>0){int n=stream.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException();budget.take(n);total.take(n);out.write(buffer,0,n);left-=n;}out.flush();}
   }finally{if(acquired)slots.release();}
  }
 }catch(Exception ex){if(!closed.get()&&!(ex instanceof EOFException))System.getLogger(PackServer.class.getName()).log(System.Logger.Level.DEBUG,dev.abros.rivet.core.Messages.text("rivet.core.modpack_transfer_completed_42119156")+ex.getClass().getSimpleName());}finally{sockets.remove(socket);}}
 public void close()throws IOException{if(!closed.compareAndSet(false,true))return;listener.close();for(var socket:sockets)try{socket.close();}catch(IOException ignored){}workers.shutdownNow();acceptor.interrupt();try{acceptor.join(2000);workers.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
}
