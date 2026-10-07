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
 private final PackDownloads.Source source;private final SSLSocketFactory factory;private final String host;private final int port;private SSLSocket socket;private DataInputStream in;private DataOutputStream out;private boolean closed;
 public PackClient(String host,int port,String fingerprint)throws Exception{this(host,port,fingerprint,new Remote()::download);}
 PackClient(String host,int port,String fingerprint,PackDownloads.Source source)throws Exception{this.source=source;this.host=host;this.port=port;factory=AuthTls.client(fingerprint).getSocketFactory();connect();}
 private synchronized SSLSocket connect()throws IOException{if(closed)throw new IOException("Соединение закрыто");if(socket!=null)return socket;var next=(SSLSocket)factory.createSocket();try{next.connect(new InetSocketAddress(host,port),5000);next.setSoTimeout(15000);next.setEnabledProtocols(new String[]{"TLSv1.3"});next.startHandshake();in=new DataInputStream(next.getInputStream());out=new DataOutputStream(next.getOutputStream());socket=next;return next;}catch(IOException|RuntimeException ex){try{next.close();}catch(IOException close){ex.addSuppressed(close);}throw ex;}}
 private synchronized void disconnect()throws IOException{var previous=socket;socket=null;if(previous!=null)previous.close();}
 public Offer manifest()throws IOException{connect();var q=new JsonObject();q.addProperty("action","manifest");PackWire.write(out,q);var response=PackWire.read(in,8*1024*1024);boolean required=response.get("required").getAsBoolean();if(!response.get("ready").getAsBoolean()){if(required)throw new IOException("Владелец ещё не опубликовал сборку сервера");return null;}var manifest=PackManifest.parse(Json.GSON.toJson(response.get("manifest")).getBytes(java.nio.charset.StandardCharsets.UTF_8));String hash=Json.str(response,"hash");if(!manifest.hash().equals(hash))throw new IOException("Описание сборки повреждено");return new Offer(manifest,hash,required);}
 public void download(String publication,PackManifest.Entry file,Path object,AtomicBoolean cancel,Consumer<String> progress)throws IOException{
  PackPublisher.safe(object);Files.createDirectories(object.getParent());if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Загрузка отменена");if(Files.exists(object)&&Files.size(object)==file.size()&&Hashes.sha256(object).equals(file.hash()))return;
  if(!file.url().isEmpty())disconnect();
  if(PackDownloads.source(file,object,cancel,progress,source)){Path serverPart=object.resolveSibling(object.getFileName()+".pack.part");PackPublisher.safe(serverPart);Files.deleteIfExists(serverPart);return;}
  Path part=object.resolveSibling(object.getFileName()+".pack.part");PackPublisher.safe(part);long offset=Files.exists(part)?Files.size(part):0;if(offset>file.size()){Files.delete(part);offset=0;}
  var connection=connect();if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Загрузка отменена");var q=new JsonObject();q.addProperty("action","file");q.addProperty("publication",publication);q.addProperty("hash",file.hash());q.addProperty("offset",offset);PackWire.write(out,q);
  var reply=PackWire.read(in,16384);if(reply.has("queued")){progress.accept("Ожидание свободного места для загрузки…");connection.setSoTimeout(0);try{reply=PackWire.read(in,16384);}finally{connection.setSoTimeout(15000);}}
  long left=reply.get("bytes").getAsBigDecimal().longValueExact();if(left!=file.size()-offset)throw new IOException("Некорректный размер файла");progress.accept("Загрузка: "+file.path());
  try(var stream=Files.newOutputStream(part,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){byte[] buffer=new byte[65536];while(left>0){if(cancel.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Загрузка отменена");int n=in.read(buffer,0,(int)Math.min(left,buffer.length));if(n<0)throw new EOFException();stream.write(buffer,0,n);left-=n;}}
  if(!Hashes.sha256(part).equals(file.hash())){Files.delete(part);throw new IOException("Контрольная сумма не совпала: "+file.path());}Json.move(part,object);
 }
 public synchronized void close()throws IOException{closed=true;disconnect();}
}
