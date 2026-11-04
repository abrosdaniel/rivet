package dev.abros.rivet.core;

import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Presentation snapshot only. Connection and installation checks still verify files directly. */
public final class AsyncPackAudit {
 @FunctionalInterface public interface Reader { String read() throws Exception; }
 private final Executor worker;private final Reader reader;private final LongSupplier clock;
 private String value="checking";private long next,generation;private boolean pending;
 public AsyncPackAudit(Executor worker,Reader reader,LongSupplier clock){this.worker=worker;this.reader=reader;this.clock=clock;}
 public synchronized String snapshot(){
  if(!pending&&clock.getAsLong()>=next){pending=true;long token=generation;
   try{worker.execute(()->{String result;try{result=reader.read();}catch(Exception failure){result="invalid";}
    synchronized(this){if(token!=generation)return;value=result;next=clock.getAsLong()+30000;pending=false;}
   });}catch(java.util.concurrent.RejectedExecutionException full){pending=false;next=clock.getAsLong()+1000;}
  }
  return value;
 }
 public synchronized void confirmed(String result){generation++;pending=false;value=result;next=clock.getAsLong()+30000;}
 public synchronized void invalidate(){generation++;pending=false;next=0;value="checking";}
}
