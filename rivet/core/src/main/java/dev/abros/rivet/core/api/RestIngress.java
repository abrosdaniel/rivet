package dev.abros.rivet.core.api;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded pre-authentication budget. Only the actual socket address is used, never forwarded headers. */
public final class RestIngress {
    private final int totalLimit,addressLimit;
    private final LongSupplier nanoTime;
    private final Map<String,Integer> addresses=new HashMap<>();
    private long since;
    private int total;
    public RestIngress(int totalLimit,int addressLimit,LongSupplier nanoTime){this.totalLimit=totalLimit;this.addressLimit=addressLimit;this.nanoTime=nanoTime;since=nanoTime.getAsLong();}
    public synchronized boolean take(String address){
        long now=nanoTime.getAsLong();
        if(now-since>=60_000_000_000L){since=now;total=0;addresses.clear();}
        int count=addresses.getOrDefault(address,0);
        if(total>=totalLimit||count>=addressLimit||count==0&&addresses.size()>=256)return false;
        total++;addresses.put(address,count+1);return true;
    }
}
