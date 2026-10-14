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
 private final PackPublisher publisher;private final ServerSocket listener;private final SSLSocketFactory tlsFactory;private final ExecutorService workers;private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();private final Semaphore slots;
 public record Protection(int connectionsPerAddress,int requestsPerSecond,int idleSeconds,int queueSeconds){
  public Protection{if(connectionsPerAddress<1||connectionsPerAddress>64||requestsPerSecond<1||requestsPerSecond>1000||idleSeconds<1||idleSeconds>300||queueSeconds<1||queueSeconds>600)throw new IllegalArgumentException("Invalid pack protection limits");}
  public static Protection defaults(){return new Protection(16,8,30,120);}
 }
 private static final class Peer {int connections;double tokens;long at=System.nanoTime();Peer(int rate){tokens=rate*2;}synchronized boolean request(int rate){long now=System.nanoTime();tokens=Math.min(rate*2,tokens+(now-at)/1_000_000_000d*rate);at=now;if(tokens<1)return false;tokens--;return true;}}
 private final Map<InetAddress,Peer> peers=new HashMap<>();private final Protection protection;private final PackObjectVerifier verifier=new PackObjectVerifier();
 private final ScheduledThreadPoolExecutor watchdog=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"Rivet pack deadlines");t.setDaemon(true);return t;});
 private final Thread acceptor;private final AtomicBoolean closed=new AtomicBoolean();private final Budget total;private final int clientMiB;private final boolean required;private final String fingerprint;
 private static final class Budget {private final long rate;private long at;Budget(int mib){rate=(long)mib*1024*1024;}void take(int bytes)throws InterruptedException{if(rate==0)return;long until;synchronized(this){at=Math.max(at,System.nanoTime())+bytes*1_000_000_000L/rate;until=at;}while(until>System.nanoTime()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();TimeUnit.NANOSECONDS.sleep(Math.min(until-System.nanoTime(),50_000_000));}}}
 public PackServer(PackPublisher publisher,Path identity,String bind,int port,boolean required,int concurrent,int totalMiB,int clientMiB)throws Exception{
  this(publisher,identity,bind,port,required,concurrent,totalMiB,clientMiB,Protection.defaults());
 }
 public PackServer(PackPublisher publisher,Path identity,String bind,int port,boolean required,int concurrent,int totalMiB,int clientMiB,Protection protection)throws Exception{
  this.protection=protection;watchdog.setRemoveOnCancelPolicy(true);
  this.publisher=publisher;this.required=required;this.clientMiB=clientMiB;total=new Budget(totalMiB);slots=concurrent==0?null:new Semaphore(concurrent,true);
  var tls=AuthTls.identity(identity);fingerprint=tls.fingerprint();tlsFactory=tls.context().getSocketFactory();listener=new ServerSocket();
  workers=new ThreadPoolExecutor(0,64,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{var t=new Thread(r,"Rivet pack transfer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  try{listener.bind(new InetSocketAddress(bind,port));}catch(Exception ex){workers.shutdownNow();listener.close();throw ex;}
  acceptor=new Thread(this::accept,"Rivet pack listener");acceptor.setDaemon(true);acceptor.start();
 }
 public String fingerprint(){return fingerprint;}public int port(){return listener.getLocalPort();}public boolean required(){return required;}
 private synchronized Peer acquire(Socket socket){var address=socket.getInetAddress();var peer=peers.get(address);if(peer==null){if(peers.size()>=4096)return null;peer=new Peer(protection.requestsPerSecond);peers.put(address,peer);}if(peer.connections>=protection.connectionsPerAddress)return null;peer.connections++;return peer;}
 private synchronized void release(Socket socket,Peer peer){peer.connections--;if(peer.connections==0&&peers.size()>2048)peers.remove(socket.getInetAddress());}
 private void accept(){while(!closed.get())try{Socket socket=listener.accept();Peer peer=acquire(socket);if(peer==null){socket.close();continue;}sockets.add(socket);try{workers.execute(()->serve(socket,peer));}catch(RejectedExecutionException busy){sockets.remove(socket);release(socket,peer);socket.close();}}catch(IOException ex){if(!closed.get())System.getLogger(PackServer.class.getName()).log(System.Logger.Level.WARNING,"Modpack listener error",ex);}}
 private void serve(Socket socket,Peer peer){try(socket;var secure=(SSLSocket)tlsFactory.createSocket(socket,socket.getInetAddress().getHostAddress(),socket.getPort(),true);var guard=new PackTransferGuard(socket,watchdog,java.time.Duration.ofSeconds(protection.idleSeconds))){secure.setUseClientMode(false);secure.setEnabledProtocols(new String[]{"TLSv1.3"});secure.setSoTimeout(protection.idleSeconds*1000);guard.begin();try{secure.startHandshake();}finally{guard.end();}var in=new DataInputStream(secure.getInputStream());var out=new DataOutputStream(guard.output(secure.getOutputStream()));var budget=new Budget(clientMiB);
  for(int requests=0;requests<10001&&!closed.get();requests++){
   JsonObject request;try{request=PackWire.read(in,16384);}catch(EOFException done){return;}
   while(!peer.request(protection.requestsPerSecond)){if(closed.get()||Thread.currentThread().isInterrupted())return;Thread.sleep(25);}
   String action=Json.str(request,"action");
   if(action.equals("manifest")){var current=publisher.current();if(current!=null&&(!request.has("manifestProtocol")||!request.get("manifestProtocol").isJsonPrimitive()||!request.get("manifestProtocol").getAsJsonPrimitive().isNumber()||request.get("manifestProtocol").getAsInt()<2))current=current.legacy();var response=new JsonObject();response.addProperty("required",required);response.addProperty("ready",current!=null);if(current!=null){response.addProperty("hash",current.hash());response.add("manifest",Json.parse(new String(current.bytes(),java.nio.charset.StandardCharsets.UTF_8)));}PackWire.write(out,response);continue;}
   if(!action.equals("file"))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_modpack_request_6cec51c2"));var publication=publisher.load(Json.str(request,"publication"));String hash=Json.str(request,"hash");Hashes.check(hash);var file=publication.files().stream().filter(f->f.hash().equals(hash)).findFirst().orElseThrow(()->new IOException(dev.abros.rivet.core.Messages.text("rivet.core.file_missing_from_publication_34ae98c2")));long offset=request.get("offset").getAsBigDecimal().longValueExact();if(offset<0||offset>file.size())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_file_position_4958ba8a"));
   boolean acquired=false;try{if(slots!=null){var wait=new JsonObject();wait.addProperty("queued",true);PackWire.write(out,wait);if(!slots.tryAcquire(protection.queueSeconds,TimeUnit.SECONDS))throw new SocketTimeoutException("Pack queue timed out");if(closed.get()){slots.release();return;}acquired=true;}
    Path object=publisher.object(hash);verifier.check(object,hash,file.size());var response=new JsonObject();response.addProperty("bytes",file.size()-offset);PackWire.write(out,response);
    try(var stream=Files.newInputStream(object)){stream.skipNBytes(offset);byte[] buffer=new byte[65536];long left=file.size()-offset;while(left>0){int n=stream.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException();budget.take(n);total.take(n);out.write(buffer,0,n);left-=n;}out.flush();}
   }finally{if(acquired)slots.release();}
  }
 }catch(Exception ex){if(!closed.get()&&!(ex instanceof EOFException))System.getLogger(PackServer.class.getName()).log(System.Logger.Level.DEBUG,dev.abros.rivet.core.Messages.text("rivet.core.modpack_transfer_completed_42119156")+ex.getClass().getSimpleName());}finally{sockets.remove(socket);release(socket,peer);}}
 public void close()throws IOException{if(!closed.compareAndSet(false,true))return;listener.close();for(var socket:sockets)try{socket.close();}catch(IOException ignored){}workers.shutdownNow();watchdog.shutdownNow();acceptor.interrupt();try{acceptor.join(2000);workers.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
}
