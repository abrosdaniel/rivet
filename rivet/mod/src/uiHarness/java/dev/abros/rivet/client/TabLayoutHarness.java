package dev.abros.rivet.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Exercises the actual TAB renderer without requiring multiple connected clients. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class TabLayoutHarness {
 private static final int[] COUNTS={0,1,2,3,4,17,96};
 private static int frame;private static long next;private static boolean started,finished;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
  if(System.getenv("RIVET_TAB_LAYOUT_CHECK")==null||finished)return;
  var mc=Minecraft.getInstance();
  if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;verifyMetadata();}
  if(System.currentTimeMillis()<next)return;
  try{
   if(mc.screen instanceof Preview preview){
    var viewport=RivetTab.rowsViewport;
    int cols=Math.max(1,Math.min(3,(mc.screen.width-32)/SocialSettings.INSTANCE.columnWidth));
    int rowHeight=SocialSettings.INSTANCE.heads?new int[]{26,30,34}[SocialSettings.INSTANCE.density]:new int[]{24,26,30}[SocialSettings.INSTANCE.density];
    int capacity=cols*Math.max(1,Math.min(16,(mc.screen.height-100)/rowHeight));
    check(RivetTab.hits.size()==Math.min(capacity,preview.players.size()),"Missing players");
    var ids=new HashSet<UUID>();
    for(var hit:RivetTab.hits){
     check(ids.add(hit.player()),"Duplicate player");
     check(hit.x()>=viewport.x()&&hit.y()>=viewport.y()&&hit.x()+hit.w()<=viewport.right()&&hit.y()+hit.h()<=viewport.bottom(),"Player clipped by TAB panel: "+hit.player());
     check(hit.x()+hit.w()<=mc.screen.width&&hit.y()+hit.h()<=mc.screen.height,"Player outside screen");
    }
    int expectedOffset=preview.scrolled?Math.min(3,Math.max(0,preview.players.size()-capacity)):0;
    var expected=preview.players.stream().sorted(Comparator.comparing(p->p.getProfile().getName().toLowerCase(Locale.ROOT))).skip(expectedOffset).limit(capacity).map(p->p.getProfile().getId()).collect(java.util.stream.Collectors.toSet());
    check(ids.equals(expected),"Scrolling skipped or repeated players");
    for(int a=0;a<RivetTab.hits.size();a++)for(int b=a+1;b<RivetTab.hits.size();b++){
     var x=RivetTab.hits.get(a);var y=RivetTab.hits.get(b);
     check(!(x.x()<y.x()+y.w()&&y.x()<x.x()+x.w()&&x.y()<y.y()+y.h()&&y.y()<x.y()+x.h()),"Overlapping player rows");
    }
   }
   if(frame==COUNTS.length*3*2*3){
    check(RivetTab.pingText(-1).equals("—"),"Unknown latency displayed as a negative number");
    check(RivetTab.pingText(10000).equals("10000 мс"),"Exact latency lost digits");
    check(!RivetTab.scrollTab(-1,true),"TAB intercepted scrolling inside another screen");
    var previous=ServerMenuClient.state.deepCopy();
    try{
     ServerMenuClient.state.addProperty("tabMode","compatible");
     check(!RivetTab.canScrollTab(true,false),"Compatible TAB intercepted the hotbar wheel");
     ServerMenuClient.state.addProperty("tabMode","rivet");
     check(!RivetTab.canScrollTab(false,false),"Closed TAB intercepted the hotbar wheel");
     check(RivetTab.canScrollTab(true,false),"Rivet TAB did not scroll");
    }finally{ServerMenuClient.state=previous;}
    RivetTab.reset();check(RivetTab.hits.isEmpty()&&RivetTab.rowsViewport==null,"Connection reset kept stale TAB targets");
    SkinWireDiagnosticsHarness.verify();finished=true;System.out.println("RIVET_TAB_LAYOUT_OK: frames="+frame+" sparse/full lists, GUI 1/2/3, heads, density, scrolling");mc.stop();return;}
   SocialSettings.INSTANCE.grouping=0;int n=frame++;mc.options.guiScale().set(n%3+1);mc.resizeDisplay();
   SocialSettings.INSTANCE.ping=n%2==0;SocialSettings.INSTANCE.heads=(n/3)%2==0;SocialSettings.INSTANCE.density=(n/6)%3;
   RivetTab.reset();if(n%2==1)RivetTab.scroll(-1);
   mc.setScreen(new Preview(COUNTS[n/18],n%2==1));next=System.currentTimeMillis()+100;
  }catch(Throwable ex){finished=true;System.out.println("RIVET_TAB_LAYOUT_FAILED frame="+frame);ex.printStackTrace();mc.stop();}
 }
 private static void verifyMetadata(){
  var mc=Minecraft.getInstance();var player=new PlayerInfo(new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ColourCheck"),false);
  var data=new com.google.gson.JsonObject();data.addProperty("prefix","<#9147FF>&l[Стример]&r");data.addProperty("suffix","&#1E90FF&o@ruslaanchik&r");SocialClient.players.put(player.getProfile().getId(),data);
  try{
   var meta=RivetTab.metadata(player);check(meta.getString().equals("[Стример] · @ruslaanchik"),"Metadata labels/separator changed");
   check(styled(meta,"[Стример]",0x9147FF,true,false),"Prefix colour or bold lost");
   check(styled(meta,"@ruslaanchik",0x1E90FF,false,true),"Suffix colour or italic lost");
   for(int width:new int[]{0,2,30,60,100,500}){
    var fitted=UiKit.fit(mc.font,meta,width);check(mc.font.width(fitted)<=width,"Rich metadata exceeds available width");
    if(!fitted.getString().isEmpty()&&!fitted.getString().equals("…"))check(styled(fitted,"[",0x9147FF,true,false),"Clipping removed prefix styling");
   }
   check(UiKit.fit(mc.font,meta,500).equals(meta),"Unclipped metadata changed");
   data.addProperty("prefix","&cRed&r");data.addProperty("suffix","§bBlue§r");meta=RivetTab.metadata(player);
   check(styled(meta,"Red",0xFF5555,false,false)&&styled(meta,"Blue",0x55FFFF,false,false),"Legacy colour lost");
   System.out.println("RIVET_TAB_METADATA_OK hex + legacy + styles + bounded rich clipping");
  }finally{SocialClient.players.remove(player.getProfile().getId());}
 }
 private static boolean styled(Component component,String text,int colour,boolean bold,boolean italic){
  var found=new boolean[1];component.visit((style,part)->{if(part.contains(text)&&style.getColor()!=null&&style.getColor().getValue()==colour&&style.isBold()==bold&&style.isItalic()==italic)found[0]=true;return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);return found[0];
 }
 private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
 private static final class Preview extends Screen{
  final List<PlayerInfo> players=new ArrayList<>();final boolean scrolled;
  Preview(int count,boolean scrolled){super(Component.literal("TAB layout regression"));this.scrolled=scrolled;for(int i=0;i<count;i++)players.add(new PlayerInfo(new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("tab-player-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),"LongPlayerName"+i),false){@Override public int getLatency(){return 10000;}});for(var player:players){var data=new com.google.gson.JsonObject();data.addProperty("prefix","<#9147FF>[Стример]&r");data.addProperty("suffix","<#1E90FF>@ruslaanchik&r");SocialClient.players.put(player.getProfile().getId(),data);}}
  @Override public void render(GuiGraphics graphics,int x,int y,float delta){RivetTab.draw(graphics,x,y,players);}
 }
}
