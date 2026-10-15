package dev.abros.rivet.core.api;

import dev.abros.rivet.core.WorkerShutdown;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Owned local HTTP/1.1 listener. Apache HttpCore parses framing; no custom HTTP parser or game-thread work. */
public final class RestHttpServer implements AutoCloseable {
    private static final Http1Config HTTP=Http1Config.custom().setMaxLineLength(8192).setMaxHeaderCount(32).setMaxEmptyLineCount(2).setBufferSize(8192).build();
    private final RestSettings settings;
    private final RestApi api;
    private final RestIngress ingress;
    private final Map<String,Integer> addresses=new HashMap<>();
    private final Set<ConnectionTask> clients=ConcurrentHashMap.newKeySet();
    private int connections;
    private volatile boolean active;
    private ServerSocket listener;
    private ExecutorService acceptor;
    private ScheduledThreadPoolExecutor deadlines;
    private ThreadPoolExecutor workers;
    public RestHttpServer(RestSettings settings,RestApi api){this.settings=settings;this.api=api;ingress=new RestIngress(settings.requestsPerMinute(),settings.addressRequestsPerMinute(),System::nanoTime);}
    public void start()throws IOException{
        if(listener!=null)throw new IllegalStateException("REST listener already started");
        active=true;
        acceptor=Executors.newSingleThreadExecutor(threads("Rivet HTTP accept"));
        deadlines=new ScheduledThreadPoolExecutor(1,threads("Rivet HTTP deadline"));deadlines.setRemoveOnCancelPolicy(true);
        workers=new ThreadPoolExecutor(settings.workers(),settings.workers(),0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(settings.queue()),threads("Rivet HTTP request"),new ThreadPoolExecutor.AbortPolicy());
        try{
            listener=new ServerSocket();listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(InetAddress.getByName(settings.bind()),settings.port()),settings.connections());
            acceptor.execute(this::accept);
        }catch(IOException|RuntimeException failure){close();throw failure;}
    }
    private static ThreadFactory threads(String name){return r->{var t=new Thread(r,name);t.setDaemon(true);return t;};}
    public int port(){if(listener==null)throw new IllegalStateException("REST not started");return listener.getLocalPort();}
    private synchronized boolean acquire(String address){int n=addresses.getOrDefault(address,0);if(!active||connections>=settings.connections()||n>=settings.connectionsPerAddress())return false;connections++;addresses.put(address,n+1);return true;}
    private synchronized void release(String address){connections--;int n=addresses.getOrDefault(address,1)-1;if(n==0)addresses.remove(address);else addresses.put(address,n);}
    private void accept(){
        while(active){
            Socket socket;
            try{socket=listener.accept();}catch(IOException stopped){if(active)closeListener();return;}
            String address=socket.getInetAddress().getHostAddress();
            if(!acquire(address)){closeSocket(socket);continue;}
            var task=new ConnectionTask(socket,address);clients.add(task);
            try{
                socket.setSoTimeout(settings.timeoutSeconds()*1000);socket.setTcpNoDelay(true);
                // Absolute deadline includes queue wait and trickled headers, not just idle time.
                task.deadline=deadlines.schedule(()->task.cancel(true),settings.timeoutSeconds(),TimeUnit.SECONDS);
                workers.execute(task);
            }catch(IOException|RejectedExecutionException rejected){task.cancel(true);}
        }
    }
    private final class ConnectionTask extends FutureTask<Void>{
        final Socket socket;final String address;
        volatile ScheduledFuture<?> deadline;
        ConnectionTask(Socket socket,String address){super(()->{serve(socket,address);return null;});this.socket=socket;this.address=address;}
        @Override protected void done(){
            closeSocket(socket);var timer=deadline;if(timer!=null)timer.cancel(false);
            clients.remove(this);workers.remove(this);release(address);
        }
    }
    private void serve(Socket socket,String address){
        if(!active||socket.isClosed())return;
        try(var connection=new DefaultBHttpServerConnection("http",HTTP)){
            connection.bind(socket);
            try{
                boolean admitted=ingress.take(address);
                var request=connection.receiveRequestHeader();
                if(request==null)return;
                // Consume the bounded header before replying: closing with unread request bytes can reset TCP and lose 429.
                if(!admitted){send(connection,RestApi.error(429,"RATE_LIMITED"));return;}
                if(!HttpVersion.HTTP_1_1.equals(request.getVersion())||request.getHeaders("Host").length!=1){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                String host=request.getFirstHeader("Host").getValue();
                if(host==null||host.isBlank()||host.length()>256||host.chars().anyMatch(c->c<=32||c>=127||"/@?#,".indexOf(c)>=0)){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                var headers=new HashMap<String,List<String>>();int size=0;
                for(var header:request.getHeaders()){
                    size+=header.getName().length()+header.getValue().length()+4;
                    headers.computeIfAbsent(header.getName().toLowerCase(Locale.ROOT),x->new ArrayList<>()).add(header.getValue());
                }
                if(size>8192){send(connection,RestApi.error(431,"INVALID_ARGUMENT"));return;}
                var lengths=headers.getOrDefault("content-length",List.of());
                boolean bodyRoute=Set.of("POST","PUT").contains(request.getMethod())&&request.getRequestUri().startsWith("/api/v1/");
                long length=0;
                try{if(lengths.size()==1)length=Long.parseLong(lengths.getFirst());}catch(NumberFormatException invalid){length=-1;}
                if(headers.containsKey("transfer-encoding")||lengths.size()>1||length<0||length>16384||!bodyRoute&&length!=0){send(connection,RestApi.error(413,"PAYLOAD_TOO_LARGE"));return;}
                String body="";
                if(bodyRoute){
                    var types=headers.getOrDefault("content-type",List.of());
                    if(length==0||types.size()!=1||!types.getFirst().equalsIgnoreCase("application/json")){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                    connection.receiveRequestEntity(request);
                    byte[] data=request.getEntity().getContent().readNBytes((int)length);
                    if(data.length!=length){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                    try{body=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(data)).toString();}catch(java.nio.charset.CharacterCodingException invalid){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                }
                if(headers.containsKey("expect")||headers.containsKey("upgrade")){send(connection,RestApi.error(400,"INVALID_ARGUMENT"));return;}
                if(!active||socket.isClosed()||Thread.currentThread().isInterrupted())return;
                var response=api.handle(new RestApi.Request(request.getMethod(),request.getRequestUri(),headers,body));
                if(active&&!socket.isClosed())send(connection,response);
            }catch(HttpException invalid){if(!socket.isClosed())send(connection,RestApi.error(400,"INVALID_ARGUMENT"));}
        }catch(Exception failure){
            // No raw parser, SQL or request exceptions: messages can contain credentials or URLs.
        }
    }
    private static void send(DefaultBHttpServerConnection connection,RestApi.Response value)throws IOException,HttpException{
        byte[] bytes=value.payload();
        try(var response=new BasicClassicHttpResponse(value.status())){
            response.setVersion(HttpVersion.HTTP_1_1);value.headers().forEach(response::setHeader);
            if(value.status()!=204&&value.status()!=304){response.setHeader("Content-Length",Integer.toString(bytes.length));response.setEntity(new ByteArrayEntity(bytes,ContentType.APPLICATION_JSON));}
            response.setHeader("Connection","close");connection.sendResponseHeader(response);
            if(response.getEntity()!=null)connection.sendResponseEntity(response);
            connection.flush();
        }
    }
    private static void closeSocket(Socket socket){try{socket.close();}catch(IOException ignored){}}
    private void closeListener(){active=false;if(listener!=null)try{listener.close();}catch(IOException ignored){}}
    @Override public void close(){
        closeListener();for(var client:clients)client.cancel(true);
        WorkerShutdown.stop(Duration.ofSeconds(1),Duration.ofSeconds(3),acceptor,workers,deadlines);
    }
}
