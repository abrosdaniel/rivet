package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.auth.AuthTls;
import javax.net.ssl.*;
import com.google.gson.*;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PackClient implements AutoCloseable {
 public record Offer(PackManifest manifest,String hash,boolean required){}
 private final PackDownloads.Source source;private final SSLSocketFactory factory;private final java.util.List<PackDiscovery.Endpoint> routes;private final java.util.Set<PackDiscovery.Endpoint> failed=new java.util.HashSet<>();private PackDiscovery.Endpoint selected;private final PackRouteConnector connector=new PackRouteConnector();private final AtomicBoolean cancellation;private volatile SSLSocket socket;private DataInputStream in;private DataOutputStream out;private volatile boolean closed;
 private static PackDownloads.Source remoteSource(){return new PackDownloads.Source(){final Remote remote=new Remote();public void download(String u,Path p,long n,AtomicBoolean c)throws IOException,InterruptedException{remote.download(u,p,n,c);}public void download(String u,Path p,long n,AtomicBoolean c,Remote.Progress progress)throws IOException,InterruptedException{remote.download(u,p,n,c,progress);}};}
 public PackClient(String host,int port,String fingerprint)throws Exception{this(host,port,fingerprint,remoteSource());}
 PackClient(String host,int port,String fingerprint,PackDownloads.Source source)throws Exception{this(java.util.List.of(new PackDiscovery.Endpoint(host,port,fingerprint,true)),new AtomicBoolean(),source);}
 public PackClient(java.util.List<PackDiscovery.Endpoint> routes,AtomicBoolean cancel)throws Exception{this(routes,cancel,remoteSource());}
 PackClient(java.util.List<PackDiscovery.Endpoint> routes,AtomicBoolean cancel,PackDownloads.Source source)throws Exception{
  if(routes.isEmpty()||routes.size()>9)throw new IllegalArgumentException("Invalid pack routes");String fingerprint=routes.getFirst().fingerprint();if(routes.stream().anyMatch(r->!r.fingerprint().equals(fingerprint)))throw new IllegalArgumentException("Mixed server identities");
  this.routes=java.util.List.copyOf(routes);this.cancellation=cancel;this.source=source;factory=AuthTls.client(fingerprint).getSocketFactory();connect();
 }
 private SSLSocket connect()throws IOException{if(closed||cancellation.get())throw new InterruptedIOException("Cancelled");if(socket!=null)return socket;
  var available=routes.stream().filter(r->!failed.contains(r)).toList();if(available.isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.pack.routesUnavailable"));
  var connected=connector.connect(available,factory,cancellation);var next=connected.socket();selected=connected.endpoint();socket=next;
  if(closed||cancellation.get()){disconnect();throw new InterruptedIOException("Cancelled");}
  in=new DataInputStream(next.getInputStream());out=new DataOutputStream(next.getOutputStream());return next;
 }
 private void disconnect()throws IOException{var previous=socket;socket=null;if(previous!=null){connector.release(previous);previous.close();}}
 public java.util.Optional<CoreUpdater.Update> core(String loader)throws IOException{
  connect();var request=new JsonObject();request.addProperty("action","core");PackWire.write(out,request);var response=PackWire.read(in,1024*1024);
  return response.get("ready").getAsBoolean()?SignedUpdates.update(response.getAsJsonObject("release"),"1.21.1",loader):java.util.Optional.empty();
 }
 public void downloadCore(CoreUpdater.Update update,Cache cache,AtomicBoolean cancel,Consumer<InstallProgress> progress)throws IOException{
  SignedUpdates.validate(update);var artifact=update.artifact();
  var file=new PackManifest.Entry(artifact.path(),artifact.sha256(),artifact.size(),"","replace","");
  try{cache.write(artifact.sha256(),cancel,object->downloadRoutes("",file,object,cancel,message->{},(position,received)->progress.accept(new InstallProgress("downloading",artifact.path(),position,artifact.size(),0,1)),true));}catch(IOException failure){try{disconnect();}catch(IOException close){failure.addSuppressed(close);}throw failure;}
 }
 public Offer manifest()throws IOException{
  IOException last=null;for(int attempt=0;attempt<routes.size();attempt++)try{return manifestOnce();}catch(IOException failure){last=failure;if(closed||cancellation.get()||Thread.currentThread().isInterrupted())throw failure;if(selected!=null)failed.add(selected);try{disconnect();}catch(IOException close){failure.addSuppressed(close);}if(failed.size()>=routes.size())throw failure;}throw last;
 }
 private Offer manifestOnce()throws IOException{connect();var q=new JsonObject();q.addProperty("action","manifest");q.addProperty("manifestProtocol",2);PackWire.write(out,q);var response=PackWire.read(in,8*1024*1024);boolean required=response.get("required").getAsBoolean();if(!response.get("ready").getAsBoolean()){if(required)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.the_owner_has_not_published_a_60620978"));return null;}var manifest=PackManifest.parse(Json.GSON.toJson(response.get("manifest")).getBytes(java.nio.charset.StandardCharsets.UTF_8));String hash=Json.str(response,"hash");if(!manifest.hash().equals(hash))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.modpack_description_is_corrupt_1e82099e"));return new Offer(manifest,hash,required);}
 public void download(String publication,PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress)throws IOException{
  download(publication,file,object,cancel,progress,(position,received)->{});
 }
 public void download(String publication,PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress,Remote.Progress bytes)throws IOException{
  downloadRoutes(publication,file,object,cancel,progress,bytes,false);
 }
 private void downloadRoutes(String publication,PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress,Remote.Progress bytes,boolean core)throws IOException{
  IOException failure=null;
  for(int attempt=0;attempt<routes.size();attempt++){
   try{downloadOnce(publication,file,object,cancel,progress,bytes,core);return;}
   catch(IOException ex){if(cancel.get()||cancellation.get()||closed||Thread.currentThread().isInterrupted())throw ex;failure=ex;if(selected!=null)failed.add(selected);try{disconnect();}catch(IOException close){ex.addSuppressed(close);}if(failed.size()>=routes.size())throw ex;progress.accept(dev.abros.rivet.core.Messages.text("rivet.pack.switchingRoute"));}
  }throw failure;
 }
 private void downloadOnce(String publication,PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress,Remote.Progress bytes,boolean core)throws IOException{
  PackPublisher.safe(object);Files.createDirectories(object.getParent());if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException(dev.abros.rivet.core.Messages.text("rivet.core.upload_cancelled_13f622b6"));if(Files.exists(object)&&Files.size(object)==file.size()&&Hashes.sha256(object).equals(file.hash()))return;
  if(!file.url().isEmpty())disconnect();
  if(PackDownloads.source(file,object,cancel,progress,source,bytes)){Path serverPart=object.resolveSibling(object.getFileName()+".pack.part");PackPublisher.safe(serverPart);Files.deleteIfExists(serverPart);return;}
  Path part=object.resolveSibling(object.getFileName()+".pack.part");PackPublisher.safe(part);long offset=Files.exists(part)?Files.size(part):0;if(offset>file.size()){Files.delete(part);offset=0;}
  bytes.accept(offset,0);
  var connection=connect();if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException(dev.abros.rivet.core.Messages.text("rivet.core.upload_cancelled_13f622b6"));var q=new JsonObject();q.addProperty("action",core?"coreFile":"file");q.addProperty("publication",publication);q.addProperty("hash",file.hash());q.addProperty("offset",offset);PackWire.write(out,q);
  var reply=PackWire.read(in,16384);if(reply.has("queued")){progress.accept(dev.abros.rivet.core.Messages.text("rivet.core.waiting_for_an_available_download_slot_5ef66d77"));connection.setSoTimeout(0);try{reply=PackWire.read(in,16384);}finally{connection.setSoTimeout(15000);}}
  long left=reply.get("bytes").getAsBigDecimal().longValueExact();if(left!=file.size()-offset)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_file_size_742ae031"));progress.accept(dev.abros.rivet.core.Messages.text("rivet.core.downloading_427b0d87")+file.path());
  try(var stream=Files.newOutputStream(part,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){byte[] buffer=new byte[65536];while(left>0){if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException(dev.abros.rivet.core.Messages.text("rivet.core.upload_cancelled_13f622b6"));int n=in.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException();stream.write(buffer,0,n);left-=n;bytes.accept(file.size()-left,n);}}
  if(!Hashes.sha256(part).equals(file.hash())){Files.delete(part);throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.checksum_mismatch_29958c6d")+file.path());}Json.move(part,object);
 }
 public void close()throws IOException{closed=true;connector.close();disconnect();}
}
