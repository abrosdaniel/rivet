package dev.abros.rivet.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Less frequent account and moderation dialogs use the same geometry contracts. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class UiNestedAuditHarness {
 private static final java.util.List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.samples(8,0,2,4);
 private static int step=-1;private static long at;
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
  String output=System.getenv("RIVET_NESTED_UI");if(output==null||step>FRAMES.size())return;
  var mc=Minecraft.getInstance();if(step<0){if(!(mc.screen instanceof TitleScreen))return;CommunityUiHarness.open("normal");step=0;}
  if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+350;
  try{
   if(step>0){verify(mc.screen instanceof Preview p?p.content:mc.screen);UiCaptureHarness.grab(new java.io.File(output),"nested-"+step+".png",mc.getMainRenderTarget(),m->{});}
   if(step==FRAMES.size()){step++;System.out.println("RIVET_NESTED_UI_OK frames="+FRAMES.size());mc.stop();return;}
   var frame=FRAMES.get(step++);frame.apply();Screen next;
   switch(frame.scene()){
    case 0->{var screen=new AuthApplicationsScreen(null);var sessions=new JsonArray();for(int n=0;n<20;n++){var row=new JsonObject();row.addProperty("id","session-"+n);row.addProperty("application","Example application "+n);row.addProperty("created",1791000000000L);row.addProperty("expires",1791100000000L);sessions.add(row);}set(screen,"sessions",sessions);next=screen;}
    case 1->next=new AuthAccountScreen(null);
    case 2->{var report=new JsonObject();report.addProperty("id","test-report");report.addProperty("revision",1);var screen=new ReportManagementScreen(null,report);set(screen,"loaded",true);next=screen;}
    case 3->{var screen=new ReportBulkScreen(null,"resolved",new JsonArray());set(screen,"loaded",true);var data=new JsonObject();data.addProperty("affected",0);data.addProperty("skipped",0);set(screen,"data",data);next=screen;}
    case 5,6,7->{var screen=new AuthAccountScreen(null);set(screen,"type","local");set(screen,"mode","hybrid");set(screen,"secondFactor",true);set(screen,"factorSupported",true);set(screen,"applicationsSupported",true);if(frame.scene()==5)set(screen,"changing",true);if(frame.scene()==6)set(screen,"linkAction","unlink");if(frame.scene()==7){var invite=new JsonObject();invite.addProperty("target","ExamplePlayer");invite.addProperty("token","TEST-CODE-NOT-A-CREDENTIAL");invite.addProperty("expires",Long.MAX_VALUE);set(screen,"invite",invite);}next=screen;}
    default->{var screen=new UiKitShowcaseScreen(null);mc.setScreen(screen);screen.revealRow(10);screen.rebuildWidgets();return;}
   }
   mc.setScreen(next instanceof AuthApplicationsScreen||next instanceof AuthAccountScreen?new Preview(next):next);
  }catch(Throwable failure){step=FRAMES.size()+1;System.out.println("RIVET_NESTED_UI_FAILED");failure.printStackTrace();mc.stop();}
 }
 /** Render account layouts without a connection; never bypass production authentication. */
 private static final class Preview extends Screen {
  final Screen content;
  Preview(Screen content){super(content.getTitle());this.content=content;}
  @Override protected void init(){content.init(minecraft,width,height);}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float delta){UiTheme.rendering(content,()->content.render(g,x,y,delta));}
 }
 private static void verify(Screen screen){
  UiGeometryHarness.verify(screen);
  var widgets=screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible).map(c->(net.minecraft.client.gui.components.AbstractWidget)c).toList();
  for(var w:widgets)if(w.getX()<0||w.getY()<0||w.getRight()>screen.width||w.getBottom()>screen.height)throw new IllegalStateException("Dialog control escaped: "+w.getMessage().getString());
  for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++){var a=widgets.get(i);var b=widgets.get(j);if(a.getX()<b.getRight()&&b.getX()<a.getRight()&&a.getY()<b.getBottom()&&b.getY()<a.getBottom())throw new IllegalStateException("Dialog overlap: "+a.getMessage().getString()+" / "+b.getMessage().getString());}
 }
 private static void set(Object object,String name,Object value)throws Exception{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
}
