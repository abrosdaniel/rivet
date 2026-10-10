package dev.abros.rivet.client;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.pack.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Checks the server publication before Minecraft begins mod negotiation. */
public final class ServerPackClient {
 private static boolean resuming;
 public static boolean intercept(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,TransferState transfer){
  if(resuming)return false;
  if(!Client.pending.isEmpty()){mc.setScreen(new RestartScreen(parent,Client.pending));return true;}
  var flow=new Flow(parent,mc,address,data,quick,transfer);flow.check();return true;
 }
 public static void configure(Screen parent,ServerData data){
  var mc=Minecraft.getInstance();if(!Client.pending.isEmpty()){mc.setScreen(new RestartScreen(parent,Client.pending));return;}
  if(data==null)return;
  var flow=new Flow(parent,mc,ServerAddress.parseString(data.ip),data,false,null);flow.configure=true;flow.check();
 }
 private static final class Flow {
  final Screen parent;final Minecraft mc;final ServerAddress address;final ServerData data;final boolean quick;final TransferState transfer;
  final AtomicBoolean cancel=new AtomicBoolean();volatile PackClient active;final PackLoadingScreen loading;
  boolean configure;PackDiscovery.Endpoint endpoint;PackClient.Offer offer;PackInstaller installer;PackTrust trust;String server;
  Flow(Screen parent,Minecraft mc,ServerAddress address,ServerData data,boolean quick,TransferState transfer){this.parent=parent;this.mc=mc;this.address=address;this.data=data;this.quick=quick;this.transfer=transfer;server=address.getHost().toLowerCase(Locale.ROOT)+":"+address.getPort();loading=new PackLoadingScreen(parent,this::cancel);}
  void cancel(){cancel.set(true);var client=active;if(client!=null)try{client.close();}catch(Exception ignored){}mc.setScreen(parent);}
  void fail(Exception ex){if(cancel.get())return;mc.execute(()->{if(!cancel.get())mc.setScreen(new TextScreen(parent,Client.tr("ui.server_pack_88518674"),Errors.message(ex)));});}
  void background(Runnable task){try{Client.CONNECT.execute(task);}catch(Exception ex){fail(ex);}}
  void check(){mc.setScreen(loading);background(()->{try{
   Client.ensureHub();
   installer=new PackInstaller(Client.hub.game,Client.hub.cache,dev.abros.rivet.Rivet.VERSION);trust=new PackTrust(Client.hub.game);
   var resolved=ServerNameResolver.DEFAULT.resolveAddress(address).orElseThrow(()->new java.io.IOException(Client.text("ui.could_not_resolve_the_server_address_d2e1a78a")));
   endpoint=PackDiscovery.query(resolved.asInetSocketAddress(),address.getHost(),address.getPort(),address.getHost());
   if(cancel.get())return;
   if(endpoint==null){if(configure)throw new java.io.IOException(Client.text("ui.this_server_does_not_distribute_a_7ceb10ae"));resume();return;}
   String known=trust.fingerprint(server);if(!known.equals(endpoint.fingerprint()))mc.execute(()->{if(cancel.get())return;mc.setScreen(new UiConfirmDialog(yes->{if(!yes){cancel();return;}mc.setScreen(loading);fetch(true);},Component.literal(known.isEmpty()?Client.text("ui.server_pack_88518674"):Client.text("ui.server_key_changed_3600f2c5")),Component.literal(Client.text("ui.server_ce169643")+server+Client.text("ui.key_ac804c85")+endpoint.fingerprint()+"\n\n"+(known.isEmpty()?Client.text("ui.allow_downloading_files_from_this_server_6be6c08a"):Client.text("ui.verify_the_new_key_with_the_065a5528")))));});else fetch(false);
  }catch(Exception ex){fail(ex);}});}
  void fetch(boolean accepted){background(()->{try(var client=new PackClient(endpoint.host(),endpoint.port(),endpoint.fingerprint())){active=client;if(cancel.get())return;if(accepted)trust.accept(server,endpoint.fingerprint());offer=client.manifest();if(offer==null){if(configure)throw new java.io.IOException(Client.text("ui.the_owner_has_not_published_a_92a25485"));resume();return;}
   if(!offer.manifest().minecraft().equals("1.21.1")||!offer.manifest().loader().equals(Client.hub.neoVersion()))throw new java.io.IOException(Client.text("ui.install_minecraft_50fb718a")+offer.manifest().minecraft()+Client.text("ui.and_neoforge_b399f786")+offer.manifest().loader()+Client.text("ui.before_connecting_6905d240"));
   var selected=installer.choices(server,offer.manifest());var review=installer.review(server,offer.manifest(),selected);
   if(!configure&&review.plan().changes().isEmpty()&&installer.installedHash().equals(offer.hash())){installer.validateMods(review);if(!Json.opt(review.state(),"packServer","").equals(server)){installer.saveSelection(review);}resume();return;}
   mc.execute(()->{if(!cancel.get())mc.setScreen(new ServerPackScreen(parent,offer.manifest(),selected,choice->review(choice)));});
  }catch(Exception ex){fail(ex);}finally{active=null;}});}
  void review(Set<String> choice){mc.setScreen(loading);background(()->{try{var review=installer.review(server,offer.manifest(),choice);var text=new StringBuilder(Client.text("ui.files_a8faed18")+review.plan().changes().size()+Client.text("ui.download_a39bb753")+review.plan().downloadBytes()+Client.text("ui.bytes_beea81d5"));for(var change:review.plan().changes())text.append(change.after()==null?Client.text("ui.delete_1c562302"):change.before()==null?Client.text("ui.add_8e5f90ba"):Client.text("ui.replace_90e10d85")).append(change.path()).append('\n');if(!review.plan().conflicts().isEmpty())text.append(Client.text("ui.existing_or_modified_files_affected_f592057d")).append(String.join("\n",review.plan().conflicts()));text.append(review.plan().changes().isEmpty()?Client.text("ui.the_files_already_match_the_pack_62a4af53"):Client.text("ui.existing_files_being_modified_will_be_01a9083a"));mc.execute(()->{if(!cancel.get())mc.setScreen(new ReviewScreen(parent,Client.tr("ui.pack_changes_d70cc394"),text.toString(),Component.literal(review.plan().changes().isEmpty()?Client.text("map.tool.save"):Client.text("install")),()->stage(review),true));});}catch(Exception ex){fail(ex);}});}
  void stage(PackInstaller.Review review){
   if(review.plan().changes().isEmpty()){
    mc.setScreen(loading);background(()->{try{
     if(cancel.get())return;installer.saveSelection(review);
     mc.execute(()->{if(!cancel.get())mc.setScreen(new TextScreen(parent,Client.tr("ui.server_pack_88518674"),Client.text("ui.component_selection_saved_the_pack_files_15d12937")));});
    }catch(Exception ex){fail(ex);}});return;
   }
   mc.setScreen(loading);background(()->{String id="";try(var client=new PackClient(endpoint.host(),endpoint.port(),endpoint.fingerprint())){active=client;id=installer.stage(review,client,cancel,text->loading.message=text);if(cancel.get()){new Transactions(Client.hub.game).abortReady(id);return;}Client.hub.startHelper(id);Client.pending=id;String ready=id;mc.execute(()->{if(!cancel.get())mc.setScreen(new RestartScreen(parent,ready));});}catch(Exception ex){if(!id.isEmpty())try{new Transactions(Client.hub.game).abortReady(id);}catch(Exception ignored){}fail(ex);}finally{active=null;}});}
  void resume(){mc.execute(()->{if(cancel.get())return;resuming=true;try{ConnectScreen.startConnecting(parent,mc,address,data,quick,transfer);}finally{resuming=false;}});}
 }
 private static final class PackLoadingScreen extends Screen {
  final Screen parent;final Runnable cancel;volatile String message=Client.text("ui.checking_server_pack_f3bfd987");UiDialog dialog;
  PackLoadingScreen(Screen parent,Runnable cancel){super(Client.tr("ui.server_pack_88518674"));this.parent=parent;this.cancel=cancel;}
  protected void init(){dialog=UiDialog.fit(width,height,360,180);var f=dialog.footer();addRenderableWidget(UiActions.button(Client.tr("cancel"),UiActions.Tone.NORMAL,"",b->cancel.run()).bounds(f.x(),f.bottom()-20,f.width(),20).build());}
  public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
  public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());var b=dialog.body();g.drawWordWrap(font,Component.literal(message),b.x(),b.y(),b.width(),UiPalette.color(0xEEEEEE));});}
  public void onClose(){cancel.run();}public boolean isPauseScreen(){return false;}
 }
}
