package dev.abros.rivet.server;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.world.item.*;
@EventBusSubscriber(modid="rivet")
public final class TradeFixture {
 private static boolean cancelToss,cancelJoin;
 @SubscribeEvent public static void toss(net.neoforged.neoforge.event.entity.item.ItemTossEvent e){if("true".equals(System.getenv("RIVET_TRADE_FIXTURE"))&&cancelToss){e.getPlayer().getInventory().add(e.getEntity().getItem().copy());e.setCanceled(true);}}
 @SubscribeEvent public static void drop(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if("true".equals(System.getenv("RIVET_TRADE_FIXTURE"))&&!e.getLevel().isClientSide&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item){if(cancelJoin)e.setCanceled(true);else item.setPickUpDelay(6000);}}

 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){if(!"true".equals(System.getenv("RIVET_TRADE_FIXTURE")))return;
  e.getDispatcher().register(Commands.literal("tradeFixture").then(Commands.argument("operation",StringArgumentType.word()).executes(c->{var p=c.getSource().getPlayerOrException();String op=StringArgumentType.getString(c,"operation");
   if(op.equals("seed")){p.getInventory().clearContent();p.getInventory().add(new ItemStack(p.getGameProfile().getName().equals("RivetTradeA")?Items.DIAMOND:Items.EMERALD,256));}
   else if(op.equals("open")||op.equals("completed")||op.equals("active")){var s=ServerTrades.require(p);var d=s.book.deals.values().stream().filter(v->v.visible(p.getStringUUID())&&(!op.equals("completed")||v.status.equals("completed")&&!v.items(p.getStringUUID()).isEmpty())&&(!op.equals("active")||v.status.equals("open"))).reduce((a,b)->b).orElseThrow();ServerTrades.open(p,d.id);}
   else if(op.equals("adminDenied")){if(ServerCommands.allowed(p,"rivet.trade.view"))throw new IllegalStateException("Guest unexpectedly has staff access");try{var check=TradeAdministration.class.getDeclaredMethod("allowed",net.minecraft.server.level.ServerPlayer.class,String.class);check.setAccessible(true);for(String right:java.util.List.of("rivet.trade.view","rivet.trade.freeze","rivet.trade.cancel")){try{check.invoke(null,p,right);throw new IllegalStateException("Unauthorized trade access");}catch(java.lang.reflect.InvocationTargetException denied){if(!(denied.getCause() instanceof IllegalArgumentException))throw denied;}}System.out.println("RIVET_TRADE_ADMIN_DENIED_OK");}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}}
   else if((op.equals("cards")||op.equals("review"))){var service=ServerTrades.require(p);var next=service.copy();String who=p.getStringUUID();var known=next.deals.values().stream().filter(d->d.a.equals(who)).findFirst().orElseThrow();String other=known.b;
    for(int i=0;i<(op.equals("review")?1:14);i++){var d=next.create(who,p.getGameProfile().getName(),other,known.nameB,false,System.currentTimeMillis(),86400000L,1000);next.edit(d.id,who,d.revision,java.util.List.of(TradeItems.encode(new ItemStack(Items.DIAMOND,op.equals("review")||i==13?128:i+1),p.registryAccess())),100);next.action(d.id,who,d.revision,"send",System.currentTimeMillis());next.edit(d.id,other,d.revision,java.util.List.of(TradeItems.encode(new ItemStack(Items.EMERALD,64),p.registryAccess())),100);if(op.equals("review")||i%2==1)next.action(d.id,other,d.revision,"publish",System.currentTimeMillis());}
    String latest=new java.util.ArrayList<>(next.deals.values()).getLast().id;service.commit(null,next,null,null,()->{if(op.equals("review"))ServerTrades.open(p,latest);else ServerTrades.list(p,1,0);});}
   else if(op.equals("packed")){for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));}
   else if(op.equals("full")){for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));p.getInventory().setItem(8,ItemStack.EMPTY);}
   else if(op.equals("components")){p.getInventory().clearContent();var box=new ItemStack(Items.SHULKER_BOX);box.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Trade preservation"));box.set(net.minecraft.core.component.DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(new ItemStack(Items.DIAMOND,37))));p.getInventory().setItem(0,box);}
   else if((op.equals("pages")||op.equals("pageControls"))){p.getInventory().clearContent();for(int i=0;i<19;i++){var item=new ItemStack(Items.DIAMOND);item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Entry "+i));p.getInventory().setItem(i,item);}if(op.equals("pageControls")){p.getInventory().setItem(19,p.getInventory().getItem(0).copyWithCount(32));p.getInventory().setItem(20,p.getInventory().getItem(18).copyWithCount(2));p.getInventory().setItem(21,p.getInventory().getItem(0).copyWithCount(2));}}
   else if(op.equals("large")){for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.DIAMOND,64));}
   else if(op.equals("tossDeny")){cancelToss=true;}
   else if(op.equals("joinDeny")){cancelToss=false;cancelJoin=true;}
   else if(op.equals("dropAllow")){cancelToss=false;cancelJoin=false;}
   else if(op.equals("dropClear")){for(var item:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(64)))item.discard();}
   else if(op.equals("space")){p.getInventory().setItem(8,ItemStack.EMPTY);}
   return 1;
  })));
 }
}
