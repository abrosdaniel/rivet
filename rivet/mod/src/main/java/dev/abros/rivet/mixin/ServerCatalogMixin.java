package dev.abros.rivet.mixin;
import dev.abros.rivet.client.BundledServers;
import dev.abros.rivet.client.ServerPackClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
abstract class ServerCatalogMixin extends Screen {
 protected ServerCatalogMixin(Component title){super(title);}
 @Shadow protected ServerSelectionList serverSelectionList;
 @Shadow private Button selectButton;
 @Unique private Button rivet$packButton;
 @Inject(method="init",at=@At("TAIL"))
 private void rivet$packAction(CallbackInfo ci){
  Screen screen=(Screen)(Object)this;
  var top=children().stream().filter(w->w instanceof Button b&&b.getMessage().getContents() instanceof TranslatableContents t&&java.util.Set.of("selectServer.select","selectServer.direct","selectServer.add").contains(t.getKey())).map(w->(Button)w).toList();
  int total=Math.min(408,screen.width-16),buttonWidth=(total-12)/4,x=(screen.width-(buttonWidth*4+12))/2,y=selectButton.getY();
  selectButton.setX(x);selectButton.setWidth(buttonWidth);
  rivet$packButton=addRenderableWidget(Button.builder(Component.translatable("rivet.pack.manage"),b->{
   var selected=serverSelectionList.getSelected();
   if(selected instanceof ServerSelectionList.OnlineServerEntry row)ServerPackClient.configure(screen,row.getServerData());
   else if(selected instanceof ServerSelectionList.NetworkServerEntry row){var lan=row.getServerData();ServerPackClient.configure(screen,new net.minecraft.client.multiplayer.ServerData(lan.getMotd(),lan.getAddress(),net.minecraft.client.multiplayer.ServerData.Type.LAN));}
  }).bounds(x+buttonWidth+4,y,buttonWidth,20).build());
  int slot=2;for(var button:top)if(button!=selectButton){button.setX(x+slot++*(buttonWidth+4));button.setWidth(buttonWidth);}
  rivet$packSelection();
 }
 @Unique private void rivet$packSelection(){if(rivet$packButton!=null){var selected=serverSelectionList.getSelected();rivet$packButton.active=selected instanceof ServerSelectionList.OnlineServerEntry||selected instanceof ServerSelectionList.NetworkServerEntry;rivet$packButton.setTooltip(Tooltip.create(Component.translatable(rivet$packButton.active?"rivet.pack.manageHint":"rivet.pack.selectHint")));}}
 @Shadow private Button editButton;
 @Shadow private Button deleteButton;
 @Inject(method="onSelectedChange",at=@At("TAIL"))
 private void rivet$catalogSelection(CallbackInfo ci){
  rivet$packSelection();
  var selected=serverSelectionList.getSelected();
  boolean bundled=selected instanceof ServerSelectionList.OnlineServerEntry row&&BundledServers.contains(row.getServerData().ip);
  if(bundled){editButton.active=false;deleteButton.active=false;}
  var hint=bundled?Tooltip.create(Component.translatable("rivet.catalog.required")):null;
  editButton.setTooltip(hint);deleteButton.setTooltip(hint);
 }
}
