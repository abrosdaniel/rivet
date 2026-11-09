package dev.abros.rivet.server;
import dev.abros.rivet.core.TradeBook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
/** Preserve all components, including nested container contents; aggregate count separately. */
public final class TradeItems {
 public static TradeBook.Item encode(ItemStack stack,HolderLookup.Provider registries){return stack.isEmpty()?null:new TradeBook.Item(stack.copyWithCount(1).save(registries).toString(),stack.getCount());}
 public static ItemStack decode(TradeBook.Item item,HolderLookup.Provider registries){
  if(item==null)return ItemStack.EMPTY;
  try{var stack=ItemStack.parse(registries,TagParser.parseTag(item.data())).orElseThrow();stack.setCount(item.count());return stack;}
  catch(Exception error){throw new IllegalStateException("Cannot decode escrow item; items retained",error);}
 }
 private TradeItems(){}
}
