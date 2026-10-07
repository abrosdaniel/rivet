package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class CatalogUiHarness {
 private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e)throws Exception {
  var mc=Minecraft.getInstance();if(System.getenv("RIVET_CATALOG_UI_CHECK")==null||done||!(mc.screen instanceof TitleScreen))return;done=true;
  try{
   var serverFile=mc.gameDirectory.toPath().resolve("servers.dat");byte[] original=java.nio.file.Files.readAllBytes(serverFile);
   try{java.nio.file.Files.writeString(serverFile,"invalid nbt");BundledServers.sync(mc);if(!java.nio.file.Files.readString(serverFile).equals("invalid nbt"))throw new IllegalStateException("Damaged server list overwritten");}finally{java.nio.file.Files.write(serverFile,original);}
   var brands=new java.util.ArrayList<String>();net.neoforged.neoforge.internal.BrandingControl.forEachLine(true,true,(index,value)->brands.add(value));
   if(brands.stream().filter(value->value.equals("Rivet "+dev.abros.rivet.Rivet.VERSION)).count()!=1)throw new IllegalStateException("Rivet branding missing or duplicated");
   for(int scale:new int[]{1,2,3}){
    mc.options.guiScale().set(scale);mc.resizeDisplay();var title=new TitleScreen();mc.setScreen(title);
    if(title.children().stream().filter(w->w instanceof CoreVersionButton).count()!=1)throw new IllegalStateException("Title button missing");
    var icon=(CoreVersionButton)title.children().stream().filter(w->w instanceof CoreVersionButton).findFirst().orElseThrow();
    var multiplayer=(Button)title.children().stream().filter(w->w instanceof Button b&&b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text&&text.getKey().equals("menu.multiplayer")).findFirst().orElseThrow();
    if(icon.getX()!=multiplayer.getX()+multiplayer.getWidth()+4||icon.getY()!=multiplayer.getY()||icon.getWidth()!=20||icon.getHeight()!=20)throw new IllegalStateException("Icon alignment");
    if(UiTheme.stylesButtons(title,icon))throw new IllegalStateException("Title icon lost native style");
    var update=Client.offeredUpdate;
    try{
     Client.offeredUpdate=null;if(!icon.description().getString().equals("Rivet "+dev.abros.rivet.Rivet.VERSION))throw new IllegalStateException("Installed version tooltip");
     Client.offeredUpdate=new dev.abros.rivet.core.CoreUpdater.Update("9.9.9",null,true);
     if(!icon.description().getString().equals("Rivet "+dev.abros.rivet.Rivet.VERSION+"\n"+Client.tr("update.availableVersion","9.9.9").getString()))throw new IllegalStateException("Available version tooltip");
    }finally{Client.offeredUpdate=update;}
    for(int count:new int[]{0,3,20}){
     var popup=new CoreVersionsPopup(title);
     var started=CoreVersionsPopup.class.getDeclaredField("started");started.setAccessible(true);started.setBoolean(popup,true);
     var versions=CoreVersionsPopup.class.getDeclaredField("versions");versions.setAccessible(true);
     var updates=new java.util.ArrayList<dev.abros.rivet.core.CoreUpdater.Update>();for(int i=0;i<count;i++)updates.add(new dev.abros.rivet.core.CoreUpdater.Update("9."+i+".0",null,true));versions.set(popup,updates);
     mc.setScreen(popup);
     int[] bounds=new int[4];int k=0;for(String name:new String[]{"left","top","panelWidth","panelHeight"}){var method=CoreVersionsPopup.class.getDeclaredMethod(name);method.setAccessible(true);bounds[k++]=(int)method.invoke(popup);}
     if(Math.abs(bounds[0]*2+bounds[2]-popup.width)>1||Math.abs(bounds[1]*2+bounds[3]-popup.height)>1)throw new IllegalStateException("Update dialog not centered");
     for(var child:popup.children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&(widget.getX()<bounds[0]||widget.getY()<bounds[1]||widget.getX()+widget.getWidth()>bounds[0]+bounds[2]||widget.getY()+widget.getHeight()>bounds[1]+bounds[3]))throw new IllegalStateException("Update action outside panel");
     if(count>0){var choice=(UiChoiceRow)popup.children().stream().filter(w->w instanceof UiChoiceRow).findFirst().orElseThrow();choice.onPress();var selected=CoreVersionsPopup.class.getDeclaredField("selected");selected.setAccessible(true);if(!updates.getFirst().equals(selected.get(popup)))throw new IllegalStateException("Version selection lost");}
     var failed=CoreVersionsPopup.class.getDeclaredField("failed");failed.setAccessible(true);failed.setBoolean(popup,true);popup.rebuildWidgets();
     if(popup.children().stream().noneMatch(w->w instanceof Button b&&b.active&&b.getMessage().getString().equals("Повторить")))throw new IllegalStateException("Update retry unavailable");
     popup.onClose();if(mc.screen!=title)throw new IllegalStateException("Update close lost parent");
    }
    var screen=new JoinMultiplayerScreen(title);mc.setScreen(screen);
    var field=JoinMultiplayerScreen.class.getDeclaredField("serverSelectionList");field.setAccessible(true);var list=(ServerSelectionList)field.get(screen);
    var entry=list.children().stream().filter(row->row instanceof ServerSelectionList.OnlineServerEntry online&&BundledServers.contains(online.getServerData().ip)).findFirst().orElseThrow();screen.setSelected(entry);
    for(String name:new String[]{"editButton","deleteButton"}){var buttonField=JoinMultiplayerScreen.class.getDeclaredField(name);buttonField.setAccessible(true);if(((Button)buttonField.get(screen)).active)throw new IllegalStateException("Catalog action available: "+name);}
   }
   System.out.println("RIVET_CATALOG_UI_OK scales=1,2,3");
  }catch(Throwable failure){System.out.println("RIVET_CATALOG_UI_FAILED");failure.printStackTrace();}finally{mc.stop();}
 }
}
