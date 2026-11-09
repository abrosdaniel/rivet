package dev.abros.rivet.server;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.TradeBook;
import dev.abros.rivet.network.TradeWire;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Native container slots, with server-only escrow and a durable carried stack.
 * No vanilla drop, creative clone or item-defined click hook may bypass escrow. */
public final class TradeMenu extends AbstractContainerMenu {
 private static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,"rivet");
 public static final DeferredHolder<MenuType<?>,MenuType<TradeMenu>> TYPE=TYPES.register("trade",()->IMenuTypeExtension.create((id,inv,data)->new TradeMenu(id,inv,data.readUtf())));
 public static void register(IEventBus bus){TYPES.register(bus);}
 public static final int PAGE=18, STRIDE=24, INVENTORY=48, HOTBAR=75, END=84;
 public final String dealId;
 private final Player player;
 private final SimpleContainer trays=new SimpleContainer(INVENTORY){@Override public int getMaxStackSize(){return 1_000_000_000;}@Override public int getMaxStackSize(ItemStack stack){return getMaxStackSize();}};
 private final Container shadow;
 private final List<ItemStack> own=new ArrayList<>();
 private final Set<Integer> drag=new LinkedHashSet<>();
 private boolean resultLayout;
 private int dragMode;public int ownPage,otherPage;
 public long revision;
 public String status="draft";
 public boolean accepted,otherAccepted,gift,frozen;
 public int ownPages=1,otherPages=1;
 public TradeMenu(int id,Inventory inventory,String dealId){super(TYPE.get(),id);this.player=inventory.player;this.dealId=dealId;this.shadow=player.level().isClientSide?inventory:new SimpleContainer(36);
  for(int i=0;i<INVENTORY;i++)addSlot(traySlot(i,false));
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(shadow,col+row*9+9,47+col*18,152+row*18));
  for(int col=0;col<9;col++)addSlot(new Slot(shadow,col,47+col*18,210));
  if(player instanceof ServerPlayer)reload();
 }
 public int inventoryY(){return 152;}
 public int frameHeight(){return 234;}
 private Slot traySlot(int i,boolean result){int side=i/STRIDE,n=i%STRIDE;return new Slot(trays,i,(side==0?(result?74:8):140)+(n%6)*18,29+(n/6)*18){
  public boolean mayPlace(ItemStack item){return side==0&&(editable()||!getCarried().isEmpty());}public boolean mayPickup(Player p){return side==0;}public boolean isActive(){return n<PAGE&&(side==0||editable())&&(!result||!fullyCollected());}
  public int getMaxStackSize(){return 1_000_000_000;}public int getMaxStackSize(ItemStack stack){return getMaxStackSize();}
 };}
 public void resultLayout(boolean result){
  if(resultLayout==result)return;resultLayout=result;
  for(int i=0;i<INVENTORY;i++){var slot=traySlot(i,result);slot.index=i;slots.set(i,slot);}
 }

 public String otherName="",otherId="";public boolean otherOnline,ownEmpty=true,otherEmpty=true,canCollect,dirty,otherDirty;
 public record Change(ItemStack before,ItemStack after) {}
 public final Map<Integer,Change> changes=new HashMap<>();
 private boolean editable(){return status.equals("draft")||status.equals("open");}
 public void reload(){if(!(player instanceof ServerPlayer p))return;try{
  var s=ServerTrades.require(p);var d=s.book.require(dealId,p.getStringUUID());revision=d.revision;status=d.status;frozen=d.frozenAt>0;accepted=d.accepted(p.getStringUUID());otherAccepted=d.accepted(d.a.equals(p.getStringUUID())?d.b:d.a);gift=d.gift;dirty=d.dirty(p.getStringUUID());otherDirty=d.dirty(d.a.equals(p.getStringUUID())?d.b:d.a);
  own.clear();for(var item:d.items(p.getStringUUID()))own.add(TradeItems.decode(item,p.registryAccess()));
  otherId=d.a.equals(p.getStringUUID())?d.b:d.a;otherName=d.a.equals(p.getStringUUID())?d.nameB:d.nameA;otherOnline=p.server.getPlayerList().getPlayer(UUID.fromString(otherId))!=null;ownEmpty=own.isEmpty();canCollect=java.util.stream.Stream.concat(own.stream(),java.util.stream.Stream.of(TradeItems.decode(s.book.cursors.get(p.getStringUUID()),p.registryAccess()))).filter(item->!item.isEmpty()).anyMatch(item->p.getInventory().items.stream().anyMatch(target->target.isEmpty()||ItemStack.isSameItemSameComponents(item,target)&&target.getCount()<target.getMaxStackSize()));
  var other=d.shownToOther(otherId);ownPages=Math.max(1,(own.size()+(editable()?PAGE:PAGE-1))/PAGE);otherPages=Math.max(1,(other.size()+PAGE-1)/PAGE);otherEmpty=other.isEmpty();if(editable()&&!status.equals("draft")){int ownDisplay=own.size()+removed(baseline(d,p.getStringUUID()),d.items(p.getStringUUID())).size();int otherDisplay=other.size()+removed(d.previous(otherId),other).size();ownPages=Math.max(ownPages,(ownDisplay+PAGE-1)/PAGE);otherPages=Math.max(otherPages,(otherDisplay+PAGE-1)/PAGE);}ownPage=Math.min(ownPage,ownPages-1);otherPage=Math.min(otherPage,otherPages-1);
  for(int i=0;i<STRIDE;i++){trays.setItem(i,i<PAGE&&ownPage*PAGE+i<own.size()?own.get(ownPage*PAGE+i).copy():ItemStack.EMPTY);trays.setItem(i+STRIDE,i<PAGE&&otherPage*PAGE+i<other.size()?TradeItems.decode(other.get(otherPage*PAGE+i),p.registryAccess()):ItemStack.EMPTY);}
  for(int i=0;i<36;i++)shadow.setItem(i,p.getInventory().getItem(i).copy());setCarried(TradeItems.decode(s.book.cursors.get(p.getStringUUID()),p.registryAccess()));
  var state=new JsonObject();state.addProperty("kind","menu");state.addProperty("frozen",frozen);state.addProperty("dirty",dirty);state.addProperty("otherDirty",otherDirty);state.addProperty("otherName",otherName);state.addProperty("otherId",otherId);state.addProperty("otherOnline",otherOnline);state.addProperty("ownEmpty",ownEmpty);state.addProperty("canCollect",canCollect);state.addProperty("otherEmpty",otherEmpty);state.addProperty("container",containerId);state.addProperty("revision",revision);state.addProperty("status",status);state.addProperty("accepted",accepted);state.addProperty("otherAccepted",otherAccepted);state.addProperty("gift",gift);state.addProperty("ownPage",ownPage);state.addProperty("otherPage",otherPage);state.addProperty("ownPages",ownPages);state.addProperty("otherPages",otherPages);PacketDistributor.sendToPlayer(p,new TradeWire.State(state.toString(),changes(d,p)));broadcastFullState();
 }catch(Exception error){ServerTrades.error(p,error);}}
 private List<TradeBook.Item> baseline(TradeBook.Deal d,String who){return d.dirty(who)?d.published(who):d.previous(who);}
 private List<TradeBook.Item> removed(List<TradeBook.Item> before,List<TradeBook.Item> after){var keys=TradeBook.totals(after);return before.stream().filter(i->!keys.containsKey(i.data())).toList();}
 private List<TradeWire.Change> changes(TradeBook.Deal d,ServerPlayer p){
  var rows=new ArrayList<TradeWire.Change>();if(!editable()||status.equals("draft"))return rows;
  for(int side=0;side<2;side++){
   String who=side==0?p.getStringUUID():otherId;
   var after=side==0?d.items(who):d.published(who);
   // Never expose the other participant's unpublished draft.
   var before=side==0?baseline(d,who):d.previous(who);
   var totals=TradeBook.totals(before);var display=new ArrayList<>(after);display.addAll(removed(before,after));
   int page=side==0?ownPage:otherPage;
   for(int n=0;n<PAGE&&page*PAGE+n<display.size();n++){
    int index=page*PAGE+n;var item=display.get(index);long old=totals.getOrDefault(item.data(),0L);int count=index<after.size()?item.count():0;
    if(old==count)continue;rows.add(new TradeWire.Change(side*STRIDE+n,TradeItems.decode(new TradeBook.Item(item.data(),1),p.registryAccess()),Math.toIntExact(old),count));
   }
  }return rows;
 }
 @Override public void broadcastChanges(){
  if(player instanceof ServerPlayer p){try{if(ServerTrades.require(p).available()){boolean changed=!otherId.isEmpty()&&otherOnline!=(p.server.getPlayerList().getPlayer(UUID.fromString(otherId))!=null);for(int i=0;i<36;i++)if(!ItemStack.matches(shadow.getItem(i),p.getInventory().getItem(i))){changed=true;break;}if(changed){reload();return;}}}catch(Exception ignored){}}
  super.broadcastChanges();
 }
 @Override public boolean stillValid(Player p){return player==p;}
 @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
 @Override public void removed(Player p){setCarried(ItemStack.EMPTY);if(p instanceof ServerPlayer sp)ServerTrades.closed(sp,dealId);}
 public boolean fullyCollected(){return ownEmpty&&getCarried().isEmpty();}
 @Override public boolean clickMenuButton(Player p,int button){if(!(p instanceof ServerPlayer sp))return false;try{var s=ServerTrades.require(sp);if(!s.available())return false;
  if(button>=10&&button<=13){if(button==10)ownPage=Math.max(0,ownPage-1);if(button==11)ownPage=Math.min(ownPages-1,ownPage+1);if(button==12)otherPage=Math.max(0,otherPage-1);if(button==13)otherPage=Math.min(otherPages-1,otherPage+1);drag.clear();reload();return true;}
  return false;
 }catch(Exception e){ServerTrades.error(sp,e);return false;}}
 public void confirm(ServerPlayer p,long expected,int button){try{var s=ServerTrades.require(p);if(!s.available())return;if(expected!=revision)throw new IllegalArgumentException("trade.changed");if(button==2&&!editable()){collect(p,s);return;}if(button==0&&!ownEmpty&&otherEmpty&&editable())throw new IllegalArgumentException("trade.free.warning");if(button==0||button==1||button==3)s.action(p,this,button==1?"cancel":status.equals("draft")?"send":dirty?"publish":"accept");}catch(Exception error){ServerTrades.error(p,error);reload();}}
 private void collect(ServerPlayer p,ServerTrades service){
  var before=ServerTrades.inventory(p);var after=new ArrayList<ItemStack>();for(var item:before)after.add(item.copy());var remaining=new ArrayList<TradeBook.Item>();var available=new ArrayList<>(own);if(!getCarried().isEmpty())available.add(getCarried().copy());
  for(var original:available){var stack=original.copy();for(int pass=0;pass<2&&!stack.isEmpty();pass++)for(int i=0;i<after.size()&&!stack.isEmpty();i++){var target=after.get(i);if(pass==0&&!target.isEmpty()&&ItemStack.isSameItemSameComponents(stack,target)||pass==1&&target.isEmpty()){int count=Math.min(stack.getCount(),stack.getMaxStackSize()-target.getCount());if(count>0){after.set(i,stack.copyWithCount(target.getCount()+count));stack.shrink(count);}}}if(!stack.isEmpty())remaining.add(TradeItems.encode(stack,p.registryAccess()));}
  service.transfer(p,this,before,after,remaining,ItemStack.EMPTY);
 }
 private boolean writable(int index){return index>=0&&index<slots.size()&&(index<PAGE||index>=INVENTORY);}
 private int capacity(int index,ItemStack stack){return index<STRIDE?1_000_000_000:stack.getMaxStackSize();}
 private boolean mayPut(int index){return index>=INVENTORY||editable()||!editable()&&!getCarried().isEmpty();}
 @Override public void clicked(int index,int button,ClickType type,Player p){
  if(!(p instanceof ServerPlayer sp))return; // Native client predicts nothing before durable acknowledgement.
  try{var service=ServerTrades.require(sp);if(!service.available()){broadcastFullState();return;}
   if(type!=ClickType.QUICK_CRAFT)drag.clear();
   if(type==ClickType.QUICK_CRAFT){int phase=getQuickcraftHeader(button),mode=getQuickcraftType(button);if(phase==0){drag.clear();dragMode=mode;return;}if(phase==1){if(writable(index)&&mayPut(index))drag.add(index);return;}if(phase!=2||dragMode>1||drag.isEmpty())return;}
   else if(!writable(index)&&!(index==-999&&type==ClickType.PICKUP&&(button==0||button==1)))return;
   var before=ServerTrades.inventory(sp);for(int i=0;i<36;i++)shadow.setItem(i,before.get(i).copy());
   var cursor=getCarried().copy();var dropped=ItemStack.EMPTY;
   if(type==ClickType.PICKUP&&index==-999&&(button==0||button==1)){if(cursor.isEmpty())return;dropped=cursor.split(button==0?cursor.getCount():1);}
   else if(type==ClickType.THROW&&cursor.isEmpty()&&(button==0||button==1)){var source=slots.get(index).getItem();if(source.isEmpty()||!source.getItem().onDroppedByPlayer(source,sp))return;dropped=source.split(button==0?1:Math.min(source.getCount(),source.getMaxStackSize()));}
   else if(type==ClickType.PICKUP&&(button==0||button==1))cursor=pickup(index,button,cursor);
   else if(type==ClickType.QUICK_MOVE)shift(index);
   else if(type==ClickType.SWAP&&button>=0&&button<9){int hotbar=HOTBAR+button;if(index!=hotbar){var a=slots.get(index).getItem();var b=slots.get(hotbar).getItem();if(mayPut(index)&&a.getCount()<=a.getMaxStackSize()){slots.get(index).set(b.copy());slots.get(hotbar).set(a.copy());}}}
   else if(type==ClickType.PICKUP_ALL&&!cursor.isEmpty()){
    for(var stack:own)if(ItemStack.isSameItemSameComponents(stack,cursor)){int n=Math.min(stack.getCount(),cursor.getMaxStackSize()-cursor.getCount());cursor.grow(n);stack.shrink(n);}
    syncOwnPage();
    for(int i=INVENTORY;i<slots.size()&&cursor.getCount()<cursor.getMaxStackSize();i++){var stack=slots.get(i).getItem();if(ItemStack.isSameItemSameComponents(stack,cursor)){int n=Math.min(stack.getCount(),cursor.getMaxStackSize()-cursor.getCount());cursor.grow(n);stack.shrink(n);}}
   }
   else if(type==ClickType.QUICK_CRAFT){int each=dragMode==1?1:cursor.getCount()/drag.size();for(int slot:drag)if(!cursor.isEmpty()&&mayPut(slot)){var existing=slots.get(slot).getItem();if(existing.isEmpty()||ItemStack.isSameItemSameComponents(existing,cursor)){int n=Math.min(Math.min(each,cursor.getCount()),capacity(slot,cursor)-existing.getCount());if(n>0){slots.get(slot).set(cursor.copyWithCount(existing.getCount()+n));cursor.shrink(n);}}}drag.clear();}
   else return;
   // Merge identical entries across pages, retaining full item components.
   while(own.size()<ownPage*PAGE+PAGE)own.add(ItemStack.EMPTY);for(int i=0;i<PAGE;i++)own.set(ownPage*PAGE+i,trays.getItem(i).copy());
   var items=new ArrayList<TradeBook.Item>();for(var stack:own)if(!stack.isEmpty()){var item=TradeItems.encode(stack,sp.registryAccess());int found=-1;for(int i=0;i<items.size();i++)if(items.get(i).data().equals(item.data())){found=i;break;}if(found<0)items.add(item);else items.set(found,new TradeBook.Item(item.data(),Math.addExact(items.get(found).count(),item.count())));}
   var after=new ArrayList<ItemStack>();for(int i=0;i<36;i++)after.add(shadow.getItem(i).copy());service.transfer(sp,this,before,after,items,cursor,dropped);reload();
  }catch(Exception error){ServerTrades.error(sp,error);reload();}
 }
 private ItemStack pickup(int index,int button,ItemStack cursor){var slot=slots.get(index);var item=slot.getItem();
  if(cursor.isEmpty()){if(item.isEmpty())return cursor;int n=Math.min(item.getMaxStackSize(),button==1?(item.getCount()+1)/2:item.getCount());cursor=item.copyWithCount(n);item.shrink(n);return cursor;}
  if(!mayPut(index))return cursor;
  if(item.isEmpty()||ItemStack.isSameItemSameComponents(item,cursor)){int n=Math.min(button==1?1:cursor.getCount(),capacity(index,cursor)-item.getCount());if(n>0){slot.set(cursor.copyWithCount(item.getCount()+n));cursor.shrink(n);}}
  else if(item.getCount()<=item.getMaxStackSize()&&cursor.getCount()<=capacity(index,cursor)){slot.set(cursor);cursor=item;}
  return cursor;
 }
 private void syncOwnPage(){for(int i=0;i<PAGE;i++)trays.setItem(i,ownPage*PAGE+i<own.size()?own.get(ownPage*PAGE+i).copy():ItemStack.EMPTY);}
 private void shift(int index){var stack=slots.get(index).getItem();if(stack.isEmpty())return;int start=index<STRIDE?INVENTORY:0,end=index<STRIDE?END:PAGE;
  if(start==0){
   if(!mayPut(0))return;
   // A tray is one escrow inventory; pages are only a viewport.
   boolean merged=false;for(var item:own)if(ItemStack.isSameItemSameComponents(item,stack)){item.setCount(Math.addExact(item.getCount(),stack.getCount()));merged=true;break;}
   if(!merged)own.add(stack.copy());stack.setCount(0);syncOwnPage();return;
  }
  for(int pass=0;pass<2&&!stack.isEmpty();pass++)for(int i=start;i<end&&!stack.isEmpty();i++){var slot=slots.get(i);var other=slot.getItem();if(pass==0&&!other.isEmpty()&&ItemStack.isSameItemSameComponents(stack,other)||pass==1&&other.isEmpty()){int n=Math.min(stack.getCount(),capacity(i,stack)-other.getCount());if(n>0){slot.set(stack.copyWithCount(other.getCount()+n));stack.shrink(n);}}}
 }
}
