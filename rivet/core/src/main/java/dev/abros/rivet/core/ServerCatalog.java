package dev.abros.rivet.core;

import java.util.*;

/** Reconciles bundled entries while keeping personal entries and their order. */
public final class ServerCatalog {
 public record Entry(String id,String name,String address) {
  public Entry {if(id==null||!id.matches("[a-z0-9][a-z0-9-]{0,63}")||name==null||name.isBlank()||name.length()>128||address==null||address.isBlank()||address.length()>255||address.chars().anyMatch(Character::isWhitespace))throw new IllegalArgumentException("Invalid catalog server");}
 }
 public record Row(String name,String address) {}
 public record Tracked(Entry entry,boolean managed) {}
 public record Change(int index,Entry entry) {} // null entry removes; negative index adds.
 public record Plan(List<Change> changes,List<Tracked> state) {}
 public static Plan reconcile(List<Row> rows,List<Tracked> previous,List<Entry> catalog) {
  var byId=new LinkedHashMap<String,Entry>();for(var e:catalog)if(byId.putIfAbsent(e.id(),e)!=null)throw new IllegalArgumentException("Duplicate catalog id");
  var old=new HashMap<String,Tracked>();for(var t:previous)if(old.putIfAbsent(t.entry().id(),t)!=null)throw new IllegalArgumentException("Duplicate tracked id");
  var addresses=new HashSet<String>();for(var e:catalog)if(!addresses.add(key(e.address())))throw new IllegalArgumentException("Duplicate catalog address");
  var changes=new ArrayList<Change>();var state=new ArrayList<Tracked>();var claimed=new HashSet<Integer>();
  for(var e:catalog){
   var t=old.get(e.id());boolean managed=false;
   if(t==null){if(rows.stream().noneMatch(row->key(row.address()).equals(key(e.address())))){changes.add(new Change(-1,e));managed=true;}}
   else if(t.managed()){
    int index=find(rows,t.entry());
    if(index>=0&&!claimed.contains(index)){
     boolean collision=false;for(int i=0;i<rows.size();i++)if(i!=index&&key(rows.get(i).address()).equals(key(e.address())))collision=true;
     if(!collision){claimed.add(index);managed=true;if(!t.entry().equals(e))changes.add(new Change(index,e));}
    }
   }
   if(!managed && rows.stream().noneMatch(row->key(row.address()).equals(key(e.address()))) && changes.stream().noneMatch(c->e.equals(c.entry()))) {changes.add(new Change(-1,e));managed=true;}
   state.add(new Tracked(e,managed));
  }
  for(var t:previous)if(t.managed()&&!byId.containsKey(t.entry().id())){int index=find(rows,t.entry());if(index>=0&&!claimed.contains(index))changes.add(new Change(index,null));}
  return new Plan(List.copyOf(changes),List.copyOf(state));
 }
 private static int find(List<Row> rows,Entry e){for(int i=0;i<rows.size();i++){var r=rows.get(i);if(r.name().equals(e.name())&&key(r.address()).equals(key(e.address())))return i;}return -1;}
 private static String key(String address){String key=address.toLowerCase(Locale.ROOT);return key.endsWith(":25565")?key.substring(0,key.length()-6):key;}
}
