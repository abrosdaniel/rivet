package dev.abros.rivet.core;
import java.util.*;
/** Loaded locations only. Change hints alternate with fallback polling to prevent starvation. */
public final class StockScanQueue {
 private final LinkedHashSet<String> active=new LinkedHashSet<>(),changed=new LinkedHashSet<>();private boolean preferChanged;
 public void active(String key,boolean value){if(value)active.add(key);else{active.remove(key);changed.remove(key);}}
 public void changed(String key){if(active.contains(key))changed.add(key);}
 public String poll(){if(active.isEmpty())return null;String key;if(!changed.isEmpty()&&(preferChanged=!preferChanged)){key=changed.iterator().next();}else key=active.iterator().next();changed.remove(key);active.remove(key);active.add(key);return key;}
 public int size(){return active.size();}
 public void clear(){active.clear();changed.clear();preferChanged=false;}
}
