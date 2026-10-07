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
  void fail(Exception ex){if(cancel.get())return;mc.execute(()->{if(!cancel.get())mc.setScreen(new TextScreen(parent,Component.literal("Сборка сервера"),Errors.message(ex)));});}
  void background(Runnable task){try{Client.CONNECT.execute(task);}catch(Exception ex){fail(ex);}}
  void check(){mc.setScreen(loading);background(()->{try{
   Client.ensureHub();
   installer=new PackInstaller(Client.hub.game,Client.hub.cache,dev.abros.rivet.Rivet.VERSION);trust=new PackTrust(Client.hub.game);
   var resolved=ServerNameResolver.DEFAULT.resolveAddress(address).orElseThrow(()->new java.io.IOException("Не удалось определить адрес сервера"));
   endpoint=PackDiscovery.query(resolved.asInetSocketAddress(),address.getHost(),address.getPort(),address.getHost());
   if(cancel.get())return;
   if(endpoint==null){if(configure)throw new java.io.IOException("Сервер не использует раздачу сборки Rivet.");resume();return;}
   String known=trust.fingerprint(server);if(!known.equals(endpoint.fingerprint()))mc.execute(()->{if(cancel.get())return;mc.setScreen(new UiConfirmDialog(yes->{if(!yes){cancel();return;}mc.setScreen(loading);fetch(true);},Component.literal(known.isEmpty()?"Сборка сервера":"Ключ сервера изменился"),Component.literal("Сервер: "+server+"\nКлюч: "+endpoint.fingerprint()+"\n\n"+(known.isEmpty()?"Разрешить получение файлов этого сервера?":"Перед подтверждением проверьте новый ключ у владельца сервера."))));});else fetch(false);
  }catch(Exception ex){fail(ex);}});}
  void fetch(boolean accepted){background(()->{try(var client=new PackClient(endpoint.host(),endpoint.port(),endpoint.fingerprint())){active=client;if(cancel.get())return;if(accepted)trust.accept(server,endpoint.fingerprint());offer=client.manifest();if(offer==null){if(configure)throw new java.io.IOException("Владелец ещё не опубликовал сборку сервера.");resume();return;}
   if(!offer.manifest().minecraft().equals("1.21.1")||!offer.manifest().loader().equals(Client.hub.neoVersion()))throw new java.io.IOException("Установите Minecraft "+offer.manifest().minecraft()+" и NeoForge "+offer.manifest().loader()+" перед подключением");
   var selected=installer.choices(server,offer.manifest());var review=installer.review(server,offer.manifest(),selected);
   if(!configure&&review.plan().changes().isEmpty()&&installer.installedHash().equals(offer.hash())){installer.validateMods(review);if(!Json.opt(review.state(),"packServer","").equals(server)){String id=installer.stage(review,client,cancel,s->{});new Transactions(Client.hub.game).apply(id);}resume();return;}
   mc.execute(()->{if(!cancel.get())mc.setScreen(new ServerPackScreen(parent,offer.manifest(),selected,choice->review(choice)));});
  }catch(Exception ex){fail(ex);}finally{active=null;}});}
  void review(Set<String> choice){mc.setScreen(loading);background(()->{try{var review=installer.review(server,offer.manifest(),choice);var text=new StringBuilder("Файлы: "+review.plan().changes().size()+"\nЗагрузка: "+review.plan().downloadBytes()+" байт\n\n");for(var change:review.plan().changes())text.append(change.after()==null?"Удалить: ":change.before()==null?"Добавить: ":"Заменить: ").append(change.path()).append('\n');if(!review.plan().conflicts().isEmpty())text.append("\nСуществующие или изменённые файлы затронуты:\n").append(String.join("\n",review.plan().conflicts()));text.append(review.plan().changes().isEmpty()?"\nФайлы уже соответствуют сборке. Перезапуск не требуется.":"\nСуществующие изменяемые файлы сохраняются в резервной копии. Для применения потребуется закрыть Minecraft.");mc.execute(()->{if(!cancel.get())mc.setScreen(new ReviewScreen(parent,Component.literal("Изменения сборки"),text.toString(),Component.literal(review.plan().changes().isEmpty()?"Сохранить":"Установить"),()->stage(review),true));});}catch(Exception ex){fail(ex);}});}
  void stage(PackInstaller.Review review){mc.setScreen(loading);background(()->{String id="";try(var client=new PackClient(endpoint.host(),endpoint.port(),endpoint.fingerprint())){active=client;id=installer.stage(review,client,cancel,text->loading.message=text);if(cancel.get()){new Transactions(Client.hub.game).abortReady(id);return;}if(review.plan().changes().isEmpty()){new Transactions(Client.hub.game).apply(id);mc.execute(()->{if(!cancel.get())mc.setScreen(new TextScreen(parent,Component.literal("Сборка сервера"),"Выбор компонентов сохранён. Файлы сборки готовы к подключению."));});return;}Client.hub.startHelper(id);Client.pending=id;String ready=id;mc.execute(()->{if(!cancel.get())mc.setScreen(new RestartScreen(parent,ready));});}catch(Exception ex){if(!id.isEmpty())try{new Transactions(Client.hub.game).abortReady(id);}catch(Exception ignored){}fail(ex);}finally{active=null;}});}
  void resume(){mc.execute(()->{if(cancel.get())return;resuming=true;try{ConnectScreen.startConnecting(parent,mc,address,data,quick,transfer);}finally{resuming=false;}});}
 }
 private static final class PackLoadingScreen extends Screen {
  final Screen parent;final Runnable cancel;volatile String message="Проверка сборки сервера…";UiDialog dialog;
  PackLoadingScreen(Screen parent,Runnable cancel){super(Component.literal("Сборка сервера"));this.parent=parent;this.cancel=cancel;}
  protected void init(){dialog=UiDialog.fit(width,height,360,180);var f=dialog.footer();addRenderableWidget(UiActions.button(Component.literal("Отмена"),UiActions.Tone.NORMAL,"",b->cancel.run()).bounds(f.x(),f.bottom()-20,f.width(),20).build());}
  public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
  public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());var b=dialog.body();g.drawWordWrap(font,Component.literal(message),b.x(),b.y(),b.width(),UiPalette.color(0xEEEEEE));});}
  public void onClose(){cancel.run();}public boolean isPauseScreen(){return false;}
 }
}
