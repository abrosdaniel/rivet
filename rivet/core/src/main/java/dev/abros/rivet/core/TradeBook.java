package dev.abros.rivet.core;

import java.util.*;

/** Item data is opaque here; only the Minecraft server creates or decodes it. */
public final class TradeBook {
 public record Item(String data,int count){public Item{if(data==null||count<1||count>1_000_000_000)throw new IllegalArgumentException("Invalid trade item");}}
 public static final class Deal {
  public String id,a,b,nameA,nameB,status="draft";public long createdAt,expires,revision=1,frozenAt;public boolean gift,acceptedA,acceptedB; // gift is legacy journal metadata; every offer has the same rules.
  public List<Item> left=new ArrayList<>(),right=new ArrayList<>();
  // Descriptions, not additional escrow: left/right alone own the actual items.
  public List<Item> publishedLeft,publishedRight,previousLeft,previousRight;
  public void initializePublished(){if(publishedLeft==null)publishedLeft=new ArrayList<>(left);if(publishedRight==null)publishedRight=new ArrayList<>(right);}
  public List<Item> published(String who){if(!participant(who))throw new IllegalArgumentException("trade.denied");var value=a.equals(who)?publishedLeft:publishedRight;return value==null?items(who):value;}
  public boolean dirty(String who){return active()&&!sameItems(items(who),published(who));}
  public List<Item> previous(String who){var value=a.equals(who)?previousLeft:previousRight;return value==null?List.of():value;}
  public List<Item> shownToOther(String who){return active()?published(who):items(who);}
  public boolean participant(String who){return a.equals(who)||b.equals(who);}
  public boolean visible(String who){return participant(who)&&(!status.equals("draft")||a.equals(who));}
  public boolean active(){return status.equals("open")||status.equals("draft");}
  public List<Item> items(String who){if(!participant(who))throw new IllegalArgumentException("trade.denied");return a.equals(who)?left:right;}
  public boolean accepted(String who){return a.equals(who)?acceptedA:acceptedB;}
 }
 public record Snapshot(List<Item> left,List<Item> right){}
 public record Audit(long at,String actor,String action,String reason,long revision,String snapshot,long remainingA,long remainingB){}
 public Map<String,Snapshot> snapshots=new LinkedHashMap<>();
 public Map<String,List<Audit>> history=new LinkedHashMap<>();
 public void record(Deal d,String actor,String action,String reason,long now){
  var snapshot=new Snapshot(List.copyOf(d.published(d.a)),List.copyOf(d.published(d.b)));String key;
  try{key=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Json.GSON.toJson(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  snapshots.putIfAbsent(key,snapshot);history.computeIfAbsent(d.id,k->new ArrayList<>()).add(new Audit(now,actor,action,reason,d.revision,key,d.left.stream().mapToLong(Item::count).sum(),d.right.stream().mapToLong(Item::count).sum()));
 }
 /** Staff may stop an active escrow, never reassign or rewind completed ownership. */
 public void moderate(String id,long revision,String actor,String action,String reason,long now){
  if(reason==null||reason.isBlank()||reason.length()>300||reason.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("trade.reason");
  var d=deals.get(id);if(d==null)throw new IllegalArgumentException("trade.denied");fresh(d,revision);if(!d.active())throw new IllegalArgumentException("trade.closed");d.initializePublished();
  switch(action){
   case "freeze"->{if(d.frozenAt>0)throw new IllegalArgumentException("trade.frozen");d.frozenAt=Math.max(1,now);d.acceptedA=d.acceptedB=false;}
   case "unfreeze"->{if(d.frozenAt==0)throw new IllegalArgumentException("trade.action");d.expires=Math.addExact(d.expires,Math.max(0,now-d.frozenAt));d.frozenAt=0;}
   case "cancel"->{d.frozenAt=0;cancel(d,"cancelled");}
   default->throw new IllegalArgumentException("trade.action");
  }
  d.revision++;record(d,actor,"admin."+action,reason,now);noticeFor(d,d.a,"admin."+action);noticeFor(d,d.b,"admin."+action);
 }
 public record Notice(String recipient,String deal,String other,String event,long sequence){}
 public Map<String,Notice> notices=new LinkedHashMap<>();
 public long noticeSequence;
 public void noticeFor(Deal d,String who,String event){if(!d.visible(who))return;notices.put(who+":"+d.id,new Notice(who,d.id,d.a.equals(who)?d.nameB:d.nameA,event,++noticeSequence));}
 public void notice(Deal d,String actor){
  if(d.status.equals("draft"))return;
  if(d.active()){noticeFor(d,d.a.equals(actor)?d.b:d.a,"offer");}
  else{noticeFor(d,d.a,d.status);noticeFor(d,d.b,d.status);}
 }
 public Map<String,Deal> deals=new LinkedHashMap<>();
 public Map<String,Item> cursors=new HashMap<>();
 public Map<String,String> cursorDeals=new HashMap<>();
 public Map<String,Set<String>> blocked=new HashMap<>();
 public Set<String> closedInbox=new HashSet<>();
 public static void conserve(List<Item> before,List<Item> after){
  if(!totals(before).equals(totals(after)))throw new IllegalArgumentException("trade.conservation");
 }
 public static boolean sameItems(List<Item> a,List<Item> b){return totals(a).equals(totals(b));}
 public static Map<String,Long> totals(List<Item> items){var result=new HashMap<String,Long>();for(var item:items)if(item!=null)result.merge(item.data(),(long)item.count(),Math::addExact);return result;}
 public Deal create(String a,String nameA,String b,String nameB,boolean gift,long now,long lifetime,int limit){
  if(a.equals(b))throw new IllegalArgumentException("trade.self");
  if(closedInbox.contains(b)||blocked.getOrDefault(b,Set.of()).contains(a))throw new IllegalArgumentException("trade.blocked");
  if(deals.values().stream().filter(d->d.a.equals(a)&&d.active()).count()>=limit)throw new IllegalArgumentException("trade.limit");
  var d=new Deal();d.id=UUID.randomUUID().toString();d.a=a;d.b=b;d.nameA=nameA;d.nameB=nameB;d.gift=gift;d.createdAt=now;d.expires=Math.addExact(now,lifetime);deals.put(d.id,d);return d;
 }
 public Deal require(String id,String who){var d=deals.get(id);if(d==null||!d.visible(who))throw new IllegalArgumentException("trade.denied");return d;}
 public void edit(String id,String who,long revision,List<Item> items,int maxEntries){
  var d=require(id,who);fresh(d,revision);if(d.frozenAt>0)throw new IllegalArgumentException("trade.frozen");d.initializePublished();if(d.active()&&items.size()>maxEntries&&items.size()>d.items(who).size())throw new IllegalArgumentException("trade.capacity");
  if(!d.active()){
   // Completed/cancelled trays are collection-only, never a fresh deposit.
   var remaining=new HashMap<String,Long>();d.items(who).forEach(i->remaining.merge(i.data(),(long)i.count(),Long::sum));
   var cursor=cursors.get(who);if(cursor!=null)remaining.merge(cursor.data(),(long)cursor.count(),Long::sum);
   for(var item:items)if(remaining.merge(item.data(),-(long)item.count(),Long::sum)<0)throw new IllegalArgumentException("trade.closed");
  }
  if(d.a.equals(who))d.left=new ArrayList<>(items);else d.right=new ArrayList<>(items);
  d.revision++;
 }
 /** Return only this player's already-owned cursor, never mint a fresh deposit. */
 public void restoreCursor(String id,String who){var item=cursors.get(who);if(item==null)return;var d=require(id,who);d.initializePublished();var items=new ArrayList<>(d.items(who));int found=-1;for(int i=0;i<items.size();i++)if(items.get(i).data().equals(item.data())){found=i;break;}if(found<0||(long)items.get(found).count()+item.count()>1_000_000_000)items.add(item);else items.set(found,new Item(item.data(),Math.addExact(items.get(found).count(),item.count())));if(d.a.equals(who))d.left=items;else d.right=items;cursors.remove(who);cursorDeals.remove(who);d.revision++;}
 public void action(String id,String who,long revision,String action,long now){
  var d=require(id,who);fresh(d,revision);
  if(!d.active())throw new IllegalArgumentException("trade.closed");
  if(d.frozenAt>0)throw new IllegalArgumentException("trade.frozen");
  if(now>=d.expires){expireDeal(d);return;}
  boolean wasDraft=d.status.equals("draft"),notify=!action.equals("publish")||d.dirty(who);
  d.initializePublished();
  switch(action){
   case "send" -> {if(!d.a.equals(who)||!d.status.equals("draft")||d.left.isEmpty())throw new IllegalArgumentException("trade.empty");d.publishedLeft=new ArrayList<>(d.left);d.publishedRight=new ArrayList<>(d.right);d.status="open";d.acceptedA=true;d.acceptedB=false;}
   case "publish" -> {
    if(!d.status.equals("open"))throw new IllegalArgumentException("trade.action");
    if(d.dirty(who)){if(d.a.equals(who)){d.previousLeft=new ArrayList<>(d.publishedLeft);d.publishedLeft=new ArrayList<>(d.left);}else{d.previousRight=new ArrayList<>(d.publishedRight);d.publishedRight=new ArrayList<>(d.right);}d.acceptedA=d.a.equals(who);d.acceptedB=d.b.equals(who);}
   }
   case "cancel" -> cancel(d,"cancelled");
   case "accept" -> {
    if(d.dirty(d.a)||d.dirty(d.b))throw new IllegalArgumentException("trade.draft.blocked");
    if(!d.status.equals("open")||d.left.isEmpty()&&d.right.isEmpty())throw new IllegalArgumentException("trade.empty");
    if(d.a.equals(who))d.acceptedA=!d.acceptedA;else d.acceptedB=!d.acceptedB;
    if(d.acceptedA&&d.acceptedB){var left=d.left;d.left=d.right;d.right=left;d.status="completed";}
   }
   default -> throw new IllegalArgumentException("trade.action");
  }
  d.revision++;
  if(notify)record(d,who,action,"",now);
  if(notify){if(wasDraft&&!d.status.equals("open"))noticeFor(d,d.a,d.status);else notice(d,who);}
 }
 public boolean expire(long now){boolean changed=false;for(var d:deals.values())if(d.active()&&d.frozenAt==0&&now>=d.expires){expireDeal(d);changed=true;}return changed;}
 private void expireDeal(Deal d){boolean draft=d.status.equals("draft");cancel(d,"expired");d.revision++;record(d,"server","expired","",System.currentTimeMillis());if(draft)noticeFor(d,d.a,"expired");else notice(d,"");}
 private static void cancel(Deal d,String state){d.status=state;d.acceptedA=d.acceptedB=false;}
 private static void fresh(Deal d,long revision){if(d.revision!=revision)throw new IllegalArgumentException("trade.changed");}
}
