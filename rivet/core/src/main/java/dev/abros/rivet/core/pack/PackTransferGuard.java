package dev.abros.rivet.core.pack;

import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.*;

/** Deadlines surround blocking I/O only, never intentional bandwidth throttling. */
final class PackTransferGuard implements AutoCloseable {
 private final Socket socket;private final ScheduledExecutorService timer;private final long timeoutMillis;
 private ScheduledFuture<?> alarm;private long generation;private boolean expired;
 PackTransferGuard(Socket socket,ScheduledExecutorService timer,Duration timeout){this.socket=socket;this.timer=timer;timeoutMillis=timeout.toMillis();}
 synchronized void begin(){long ticket=++generation;alarm=timer.schedule(()->{synchronized(this){if(ticket!=generation)return;expired=true;try{socket.close();}catch(IOException ignored){}}},timeoutMillis,TimeUnit.MILLISECONDS);}
 synchronized void end()throws IOException{generation++;if(alarm!=null)alarm.cancel(false);alarm=null;if(expired)throw new java.net.SocketTimeoutException("Pack transfer made no progress");}
 OutputStream output(OutputStream target){return new FilterOutputStream(target){
  public void write(int b)throws IOException{begin();try{out.write(b);}finally{end();}}
  public void write(byte[] b,int offset,int length)throws IOException{begin();try{out.write(b,offset,length);}finally{end();}}
  public void flush()throws IOException{begin();try{out.flush();}finally{end();}}
 };}
 public synchronized void close(){generation++;if(alarm!=null)alarm.cancel(false);}
}
