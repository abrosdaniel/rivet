package dev.abros.rivet.core;
import java.util.*;
/** Bounded aggregate timings; operation names only, never account or payload data. */
public final class PerformanceMetrics {
 public record Sample(long count,long totalNanos,long maxNanos,long failures){public double meanMillis(){return count==0?0:totalNanos/1_000_000.0/count;}}
 /** Percentiles describe only the most recent bounded window, not the lifetime aggregate. */
 public record Distribution(int samples,long p50Nanos,long p95Nanos,long p99Nanos){}
 private static final int LIMIT=64,WINDOW=2048;
 private static final class Bucket {
  long count,total,max,failures;long[] recent;int cursor,size;
  void add(long nanos,boolean failed){count++;total+=nanos;max=Math.max(max,nanos);if(failed)failures++;if(detailed){if(recent==null)recent=new long[WINDOW];recent[cursor]=nanos;cursor=(cursor+1)%WINDOW;size=Math.min(WINDOW,size+1);}}
  Sample sample(){return new Sample(count,total,max,failures);}
 }
 private static final Map<String,Bucket> values=new LinkedHashMap<>();
 private static volatile boolean detailed;
 /** Diagnostic timing is opt-in. Disabled hot paths do not call the clock or allocate. */
 public static long start(){return detailed?System.nanoTime():0;}
 public static void end(String operation,long started){if(started!=0&&detailed)record(operation,System.nanoTime()-started,false);}
 public static synchronized void detailed(boolean enabled){detailed=enabled;for(var b:values.values()){b.recent=null;b.cursor=b.size=0;}}
 public static synchronized void record(String operation,long nanos,boolean failed){var b=values.get(operation);if(b==null){if(values.size()>=LIMIT)return;b=new Bucket();values.put(operation,b);}b.add(Math.max(0,nanos),failed);}
 public static synchronized Map<String,Sample> snapshot(){var result=new LinkedHashMap<String,Sample>();values.forEach((k,b)->result.put(k,b.sample()));return Collections.unmodifiableMap(result);}
 public static synchronized Map<String,Distribution> distributions(){var result=new LinkedHashMap<String,Distribution>();values.forEach((k,b)->{if(b.size>0){long[] sorted=Arrays.copyOf(b.recent,b.size);Arrays.sort(sorted);result.put(k,new Distribution(b.size,quantile(sorted,.50),quantile(sorted,.95),quantile(sorted,.99)));}});return Collections.unmodifiableMap(result);}
 private static long quantile(long[] values,double q){return values[(int)Math.ceil(values.length*q)-1];}
 public static synchronized void clear(){values.clear();}
 private PerformanceMetrics(){}
}
