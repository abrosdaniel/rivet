package dev.abros.rivet.server;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
/** Deterministic official-profile fixture; available only in the isolated native harness. */
@EventBusSubscriber(modid="rivet")
public final class OfficialCapeFixture {
 @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){
  if(!"true".equals(System.getenv("RIVET_TRADE_FIXTURE")))return;
  e.getDispatcher().register(net.minecraft.commands.Commands.literal("officialCapeFixture").requires(s->s.hasPermission(4)).executes(c->{
   var p=c.getSource().getPlayerOrException();try{
    var image=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
    for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,x<22?0xff39a873:0xffe2be75);
    var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);
    var settings=ServerDatabase.settings().skins();var store=new dev.abros.rivet.core.skins.SkinStore(ServerDatabase.get(),settings);
    store.fallback(p.getUUID(),"appearance-v2:"+settings.mojangFallback()+":"+p.getGameProfile().getName(),null,false,out.toByteArray(),System.currentTimeMillis());
    return 1;
   }catch(Exception ex){throw new IllegalStateException(ex);}
  }));
 }
}
