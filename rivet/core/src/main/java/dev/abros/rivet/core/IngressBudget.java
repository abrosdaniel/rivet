package dev.abros.rivet.core;

/** Per-connection bound on queued work, packet count and decoded string storage. */
public final class IngressBudget {
 private double packets=256,bytes=2*1024*1024;private long at=System.nanoTime();private int pending;
 public synchronized boolean acquire(int characters){return acquire(characters,System.nanoTime());}
 synchronized boolean acquire(int characters,long now){double seconds=Math.max(0,now-at)/1_000_000_000d;at=now;packets=Math.min(256,packets+seconds*128);bytes=Math.min(2*1024*1024,bytes+seconds*1024*1024);long cost=2L*characters;if(characters<0||pending>=64||packets<1||bytes<cost)return false;packets--;bytes-=cost;pending++;return true;}
 public synchronized void release(){if(pending<=0)throw new IllegalStateException("Unbalanced ingress reservation");pending--;}
}
