package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
/** Exhaustive vanilla mob coverage, actual renderer output and render-state preservation. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapMobIconsHarness {
 private static final String ADDRESS=System.getenv("RIVET_MOB_ICONS_ADDRESS");private static boolean connected,done;private static long deadline;private static Sheet sheet;private static int ticks,page;private static final List<Mob> MOBS=new ArrayList<>();private static final Set<UUID> rendered=new HashSet<>();
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(!connected&&mc.screen instanceof TitleScreen){connected=true;deadline=System.currentTimeMillis()+120000;mc.options.guiScale().set(2);mc.resizeDisplay();ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Mob icons",ADDRESS,ServerData.Type.OTHER),false,null);}
  if(connected&&System.currentTimeMillis()>deadline)throw new IllegalStateException("Icon timeout");if(!WorldMapClient.ready())return;
  if(sheet==null){for(var type:BuiltInRegistries.ENTITY_TYPE){var entity=type.create(mc.level);if(entity instanceof Mob mob){mob.setPos(mc.player.position());MOBS.add(mob);}}MOBS.sort(Comparator.comparing(m->BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString()));sheet=new Sheet();mc.setScreen(sheet);return;}
  if(++ticks<80)return;var batch=MOBS.subList(page*20,Math.min(MOBS.size(),(page+1)*20));var missing=batch.stream().filter(m->!rendered.contains(m.getUUID())).map(m->BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).toString()).toList();if(!missing.isEmpty())throw new IllegalStateException("Missing model portraits: "+missing);
  String folder=System.getenv("RIVET_MOB_ICONS_SCREENSHOTS");if(folder!=null){var out=new java.io.File(folder);out.mkdirs();UiCaptureHarness.grab(out,"mobs-"+page+".png",mc.getMainRenderTarget(),m->{});}ticks=0;if(++page*20>=MOBS.size()){done=true;MapMobIcons.clear();System.out.println("RIVET_MOB_ICONS_OK mobs="+MOBS.size()+" pages="+page+" state=true");mc.stop();}
 }catch(Throwable failure){done=true;System.out.println("RIVET_MOB_ICONS_FAILED page="+page);failure.printStackTrace();mc.stop();}}
 private static final class Sheet extends Screen {
  Sheet(){super(Component.literal("Иконки всех мобов"));}
  @Override public void render(GuiGraphics g,int mx,int my,float d){g.fill(0,0,width,height,0xff24292e);g.drawString(font,title,16,12,0xffffffff);for(int i=page*20;i<Math.min(MOBS.size(),(page+1)*20);i++){var mob=MOBS.get(i);int n=i%20,x=65+n%5*125,y=70+n/5*115;float yaw=mob.getYRot(),pitch=mob.getXRot();int target=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(3,3,1);if(MapRadar.portrait(g,mob,0,0))rendered.add(mob.getUUID());g.pose().popPose();if(mob.getYRot()!=yaw||mob.getXRot()!=pitch||target!=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING))throw new IllegalStateException("Renderer state leaked");g.drawCenteredString(font,UiKit.fit(font,mob.getName().getString(),118),x,y+28,0xffffffff);}}
 }
}
