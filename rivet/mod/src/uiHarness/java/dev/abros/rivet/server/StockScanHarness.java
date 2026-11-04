package dev.abros.rivet.server;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Opt-in real containers in the disposable native test world. */
@EventBusSubscriber(modid="rivet")
public final class StockScanHarness {
 private static boolean done;
 private static Object field(String name)throws Exception{var f=ServerTaskStocks.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
  if(done||!"true".equals(System.getenv("RIVET_STOCK_SCAN_FIXTURE")))return;
  try{
   if(field("tasks")==null)return;done=true;var server=event.getServer();var level=server.overworld();
   var track=ServerTaskStocks.class.getDeclaredMethod("track",net.minecraft.server.MinecraftServer.class,String.class,JsonObject.class);track.setAccessible(true);
   var untrack=ServerTaskStocks.class.getDeclaredMethod("untrack",String.class);untrack.setAccessible(true);
   var keys=new ArrayList<String>();var positions=new ArrayList<BlockPos>();
   @SuppressWarnings("unchecked")var sampled=(Map<String,Long>)field("sampledAt");
   try{
    for(int n=0;n<4096;n++){
     boolean loaded=n<128;var pos=loaded?new BlockPos(1025+n%16*2,100,1024+n/16*2):new BlockPos(100000+n*16,100,100000);
     if(loaded){level.getChunkAt(pos);var chest=pos.west();level.setBlock(chest,Blocks.CHEST.defaultBlockState(),3);level.setBlock(pos,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),3);positions.add(pos);positions.add(chest);
      var sign=(SignBlockEntity)level.getBlockEntity(pos);if(sign==null)throw new IllegalStateException("Missing fixture sign");sign.setText(new SignText().setMessage(0,Component.literal("&rivet")).setMessage(1,Component.literal("Load")).setMessage(2,Component.literal("2222")),true);
     }else if(level.hasChunkAt(pos))throw new IllegalStateException("Unloaded fixture chunk was already loaded");
     String key="fixture-stock-"+n;keys.add(key);var row=new JsonObject();row.addProperty("key",key);row.addProperty("task","fixture");row.addProperty("dimension","minecraft:overworld");row.addProperty("sign",pos.asLong());row.addProperty("front",true);row.addProperty("label","Load");row.addProperty("code","2222");row.addProperty("inventory","minecraft:overworld:"+pos.west().asLong());row.add("items",new JsonObject());row.addProperty("at",System.currentTimeMillis());track.invoke(null,server,key,row);
    }
    long max=0;int passes=0;
    while(keys.subList(0,128).stream().anyMatch(k->!sampled.containsKey(k))&&passes<256){long start=System.nanoTime();ServerTaskStocks.tick(server);max=Math.max(max,System.nanoTime()-start);if((int)field("sampledSlots")>8192)throw new IllegalStateException("Slot budget exceeded");passes++;}
    if(keys.subList(0,128).stream().anyMatch(k->!sampled.containsKey(k)))throw new IllegalStateException("Loaded stock starved behind unloaded bindings");
    if(keys.subList(128,4096).stream().anyMatch(sampled::containsKey))throw new IllegalStateException("Unloaded stocks scanned");
    System.out.printf(java.util.Locale.ROOT,"RIVET_STOCK_SCAN_OK bindings=4096 loaded=128 passes=%d maxPassMs=%.3f slotsBounded=true%n",passes,max/1e6);
   }finally{for(var key:keys)untrack.invoke(null,key);for(var pos:positions)level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);}
  }catch(Throwable failure){done=true;System.out.println("RIVET_STOCK_SCAN_FAILED");failure.printStackTrace();}
 }
}
