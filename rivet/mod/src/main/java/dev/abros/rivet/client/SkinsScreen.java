package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.skins.SkinImage;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.lwjgl.system.MemoryStack;
import java.nio.file.*;

final class SkinsScreen extends ScrollScreen {
 private static final java.util.concurrent.ExecutorService FILES=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet skin file picker");t.setDaemon(true);return t;});
 private static final java.util.concurrent.atomic.AtomicBoolean PICKER=new java.util.concurrent.atomic.AtomicBoolean();
 private boolean secondLayer=true;private boolean choosing,uploadPending;private EditBox nameField;
 private final Screen parent;private RemotePlayer previewPlayer;private byte[] draft;private ResourceLocation draftTexture;private String draftName="",message="";private boolean slim;private float rotation;private int left,listWidth,right,previewBottom;private String selection="";private boolean selectionReady;private String dragging="",moving="";private int moveTarget=-1;private double pointerY;private long nextScroll;
 SkinsScreen(Screen parent){super(Component.literal("Скины"));this.parent=parent;}
 static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new SkinsScreen(parent));SkinClient.command("list","",false);}
 boolean isPreview(net.minecraft.world.entity.Entity entity){return entity==previewPlayer;}
 private String active(){return SkinClient.library.has("profile")?Json.opt(SkinClient.library.getAsJsonObject("profile"),"active",""):"";}
 private JsonObject selectedEntry(){for(var e:entries())if(Json.str(e.getAsJsonObject(),"id").equals(selection))return e.getAsJsonObject();return null;}
 void updated(){if(uploadPending&&!SkinClient.busy&&!SkinClient.retryable){uploadPending=false;if(SkinClient.status.isEmpty()){draft=null;draftTexture=null;selectionReady=false;}}if(!selectionReady){selection=active();selectionReady=true;}if(!moving.isEmpty()&&!SkinClient.busy){if(!SkinClient.status.isEmpty()||SkinClient.retryable){moving="";moveTarget=-1;}else continueMove();}rebuildWidgets();}
 private void continueMove(){int index=-1;for(int n=0;n<entries().size();n++)if(Json.str(entries().get(n).getAsJsonObject(),"id").equals(moving))index=n;if(index<0||index==moveTarget){moving="";moveTarget=-1;return;}SkinClient.command(index>moveTarget?"moveUp":"moveDown",moving,false);}
 private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(UiActions.button(Component.literal(text),UiActions.Tone.NORMAL,"",b->action.run()).bounds(x,y,w,20).build());}
 private JsonArray entries(){return SkinClient.library.has("entries")?SkinClient.library.getAsJsonArray("entries"):new JsonArray();}
 private int compactTab;private boolean compact(){return width<640||height<340;}
 private int stride(){return AccessibilityScreen.skinStride();}
 @Override protected void init(){left=Math.max(UiPage.body(width,height).x(),(width-600)/2);int total=width-2*left;listWidth=Math.max(110,total*2/5-12);right=left+listWidth+18;int bottom=height-90;if(compact()){listWidth=total;right=left;UiTabs.build(this,font,new dev.abros.rivet.core.NativeLayout.Box(left,34,total,20),java.util.List.of("Библиотека","Предпросмотр"),compactTab,this::addRenderableWidget,n->{cancelInteraction();compactTab=n;rebuildWidgets();},true);}
  if(!selectionReady&&SkinClient.library.has("profile")){selection=active();selectionReady=true;}
  if(!compact()||compactTab==0&&draft==null)scrollArea(entries().size()+1,new dev.abros.rivet.core.NativeLayout.Box(left,56,Math.max(0,listWidth),Math.max(0,(bottom)-(56))),stride());
  if((!compact()||compactTab==0)&&draft==null){
   for(int row=firstRow;row<Math.min(entries().size()+1,firstRow+visibleRows);row++){
    var entry=row==0?null:entries().get(row-1).getAsJsonObject();String id=entry==null?"":Json.str(entry,"id");int y=56+(row-firstRow)*stride();
    var card=addRenderableWidget(new UiSkinCard(left+2,y,UiSearchToolbar.contentWidth(listWidth)-28,stride()-6,entry,id.equals(selection),id.equals(active()),false,()->{selection=id;if(compact())compactTab=1;rebuildWidgets();}));card.active=!SkinClient.busy&&!choosing;
   }
   var add=button(choosing?"Выбор файла…":"Добавить PNG"+(SkinClient.library.has("limit")?" · "+entries().size()+" / "+SkinClient.library.get("limit").getAsInt():""),left,height-56,listWidth,this::chooseFile);
   add.setTooltip(Tooltip.create(Component.literal("PNG 64×64 или 64×32. После выбора можно проверить скин.")));
   add.active=!choosing&&!PICKER.get()&&SkinClient.available()&&!SkinClient.busy&&SkinClient.library.has("limit")&&entries().size()<SkinClient.library.get("limit").getAsInt();
  }else if((!compact()||compactTab==0)&&draft!=null){
   nameField=addRenderableWidget(UiFields.text(font,left+6,72,listWidth-12,20,Component.literal("Название скина")));nameField.setMaxLength(40);nameField.setValue(draftName);nameField.setResponder(v->draftName=v);nameField.active=!SkinClient.busy;
   button(slim?"Тонкие руки":"Обычные руки",left+6,102,listWidth-12,()->{slim=!slim;rebuildWidgets();}).active=!SkinClient.busy;
   button("Отмена",left+6,132,listWidth-12,()->{draft=null;draftTexture=null;message="";rebuildWidgets();}).active=!SkinClient.busy;
  }
  int pw=width-left-right;
  if(!compact()||compactTab==1){boolean rowControls=pw>=240;int controlsTop=height-(rowControls?118:142);previewBottom=controlsTop-8;
  button("Второй слой: "+(secondLayer?"Вкл":"Выкл"),right,controlsTop,rowControls?(pw-4)/2:pw,()->{secondLayer=!secondLayer;rebuildWidgets();});
  button("Вернуть вид",rowControls?right+(pw+4)/2:right,rowControls?controlsTop:controlsTop+24,rowControls?(pw-4)/2:pw,()->rotation=0);
  var apply=button(SkinClient.busy?"Применяется…":draft!=null?"Загрузить и применить":selection.equals(active())?"Активный скин":"Применить",right,height-82,pw,()->{
   if(draft!=null){if(draftName.strip().isEmpty()){message="Введите название скина";return;}uploadPending=true;SkinClient.upload(draftName.strip(),slim,draft);}else SkinClient.command("select",selection,false);rebuildWidgets();
  });apply.active=!SkinClient.busy&&!choosing&&!uploadPending&&(draft!=null||!selection.equals(active()));
  if(draft==null&&selectedEntry()!=null){var menu=button("Действия ▾",right,compact()?58:48,Math.min(120,pw),this::actions);menu.active=!SkinClient.busy&&!choosing;}
  if(SkinClient.retryable)button("Повторить запрос",right,height-56,pw,SkinClient::retry);
  }else if(draft!=null)button("Загрузить и применить",left,height-56,listWidth,()->{if(draftName.strip().isEmpty()){message="Введите название скина";return;}uploadPending=true;SkinClient.upload(draftName.strip(),slim,draft);rebuildWidgets();}).active=!SkinClient.busy&&!choosing&&!uploadPending;
  UiPageFooter.workspace(width,height).end(UiActions.Command.BACK,this::addRenderableWidget,this::onClose).active=moving.isEmpty();
  if(minecraft.level!=null&&minecraft.player!=null&&previewPlayer==null)previewPlayer=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){@Override public PlayerSkin getSkin(){return previewSkin();}@Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return secondLayer;}};
 }
 private void actions(){var entry=selectedEntry();if(entry==null)return;String id=selection;var labels=new java.util.ArrayList<String>();var actions=new java.util.ArrayList<Runnable>();
  if(dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-names")){labels.add("Переименовать");actions.add(()->minecraft.setScreen(new NameScreen(this,id,Json.str(entry,"name"))));}
  labels.add(entry.get("slim").getAsBoolean()?"Сделать обычные руки":"Сделать тонкие руки");actions.add(()->SkinClient.command("model",id,!entry.get("slim").getAsBoolean()));

  labels.add("Удалить…");actions.add(()->minecraft.setScreen(new UiConfirmDialog(ok->{minecraft.setScreen(this);if(ok){selection="";SkinClient.command("delete",id,false);}},Component.literal("Удалить скин?"),Component.literal(Json.str(entry,"name"))).dangerous()));
  minecraft.setScreen(new ChoicePopup(this,"Действия",labels,n->actions.get(n).run()).danger(labels.size()-1).anchorLabel("Действия ▾"));
 }
 private void chooseFile(){
  if(!PICKER.compareAndSet(false,true))return;
  choosing=true;message="";var client=minecraft;var connection=client.getConnection();var level=client.level;int limit=SkinClient.library.get("maxBytes").getAsInt();rebuildWidgets();
  FILES.execute(()->{
   byte[] bytes=null;String label="",failure="";
   try(var stack=MemoryStack.stackPush()){
    var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.png")).flip();
    String chosen=TinyFileDialogs.tinyfd_openFileDialog("Выберите скин PNG",null,patterns,"Minecraft PNG (64×64, 64×32)",false);
    if(chosen!=null){var path=Path.of(chosen);if(!Files.isRegularFile(path)||Files.size(path)>limit)throw new java.io.IOException();try(var in=Files.newInputStream(path)){bytes=SkinImage.normalize(in.readNBytes(limit+1),limit);}label=path.getFileName().toString().replaceFirst("(?i)\\.png$","").replaceAll("[\\p{Cntrl}]","");if(label.length()>40)label=label.substring(0,40);if(label.isBlank())label="Скин";}
   }catch(Exception error){failure="Нужен PNG 64×64 или 64×32 в пределах лимита.";}finally{PICKER.set(false);}
   final byte[] result=bytes;final String name=label,error=failure;
   client.execute(()->{choosing=false;if(client.screen!=this||client.getConnection()!=connection||client.level!=level)return;message=error;if(result!=null)try{draftTexture=SkinClient.preview(result);draft=result;draftName=name;slim=false;}catch(Exception invalid){message="Не удалось подготовить скин";}rebuildWidgets();});
  });
 }
 private static final class NameScreen extends Screen {
  private final SkinsScreen parent;private final String id;private String value;private EditBox field;
  NameScreen(SkinsScreen parent,String id,String value){super(Component.literal("Название скина"));this.parent=parent;this.id=id;this.value=value;}
  @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(300,width-32),height/2-54,height/2+44);}
 @Override protected void init(){int w=Math.min(300,width-32),x=(width-w)/2,y=height/2-20;field=addRenderableWidget(UiFields.text(font,x,y,w,20,title));field.setMaxLength(40);field.setValue(value);var save=addRenderableWidget(UiActions.button(Component.literal("Сохранить"),UiActions.Tone.PRIMARY,UiIcons.SAVE,b->{minecraft.setScreen(parent);SkinClient.command("rename",id,false,value.strip());}).bounds(x,y+30,(w-4)/2,20).build());save.active=!value.isBlank();field.setResponder(v->{value=v;save.active=!v.isBlank();});addRenderableWidget(UiActions.button(Component.literal("Отмена"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x+(w+4)/2,y+30,(w-4)/2,20).build());setInitialFocus(field);}
  @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,(width-Math.min(300,width-32))/2,height/2-54,Math.min(300,width-32));});}
  @Override public void onClose(){minecraft.setScreen(parent);}
  @Override public boolean isPauseScreen(){return false;}
 }
 private PlayerSkin previewSkin(){if(draftTexture!=null)return new PlayerSkin(draftTexture,null,null,null,slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);return UiSkinCard.appearance(selectedEntry());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){super.renderBackground(g,mx,my,delta);UiPage.draw(g,width,height);if(!compact()||compactTab==0)UiKit.material(g,left-6,compact()?56:38,listWidth+13,height-(compact()?120:102));if(!compact()||compactTab==1)UiKit.material(g,right-4,compact()?56:38,width-left-right+10,height-(compact()?120:102));}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);UiHeading.page(g,font,title,width);
  if(draft!=null&&(!compact()||compactTab==0))Ui.text(g,font,"Название",left+6,56,UiKit.text(),false);
  else if(!compact())Ui.text(g,font,"Библиотека",left+6,42,UiKit.muted(),false);
  if(draft==null&&(!compact()||compactTab==0)&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-order")){var b=scrollLayout().content();for(int row=Math.max(1,firstRow);row<Math.min(entries().size()+1,firstRow+visibleRows);row++)UiDragHandle.draw(g,b.right()-UiDragHandle.WIDTH,56+(row-firstRow)*stride(),Json.str(entries().get(row-1).getAsJsonObject(),"id").equals(dragging));}
  if(!dragging.isEmpty()&&dropRow(mx,my)>=0){var b=scrollLayout().content();int yy=b.y()+((my-b.y())/stride())*stride();g.fill(b.x(),yy,b.right(),yy+2,UiKit.accent());}
  if((!compact()||compactTab==1)&&previewPlayer!=null&&previewBottom>98){previewPlayer.yBodyRot=180;previewPlayer.setYRot(180);previewPlayer.yHeadRot=180;previewPlayer.yHeadRotO=180;g.enableScissor(right,90,width-left,previewBottom);InventoryScreen.renderEntityInInventory(g,(right+width-left)/2f,(90+previewBottom)/2f,Math.max(18,Math.min(70,(previewBottom-100)/2.2f)),new org.joml.Vector3f(0,previewPlayer.getBbHeight()/2,0),new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(rotation),null,previewPlayer);g.disableScissor();}
  String text=message.isEmpty()?SkinClient.status:message;if(!text.isEmpty())Ui.text(g,font,UiKit.fit(font,text,width-left-right),right,74,UiKit.muted(),false);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if((!compact()||compactTab==0)&&button==0&&draft==null&&!SkinClient.busy&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-order")&&scrollLayout()!=null&&x>=scrollLayout().content().right()-UiDragHandle.WIDTH&&x<scrollLayout().content().right()&&y>=56&&y<height-90){int row=firstRow+(int)((y-56)/stride());if(dropRow(x,y)>=0){dragging=Json.str(entries().get(row-1).getAsJsonObject(),"id");pointerY=y;return true;}}return super.mouseClicked(x,y,button);}
 private int dropRow(double x,double y){if(scrollLayout()==null)return -1;var b=scrollLayout().content();int row=firstRow+(int)((y-b.y())/stride());return b.contains(x,y)&&y<b.y()+visibleRows*stride()&&row>0&&row<=entries().size()?row-1:-1;}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){String id=dragging;dragging="";int row=dropRow(x,y);if(row>=0){moving=id;moveTarget=row;continueMove();rebuildWidgets();}return true;}return super.mouseReleased(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&!dragging.isEmpty()){pointerY=y;return true;}if((!compact()||compactTab==1)&&button==0&&x>=right&&y>=78&&y<previewBottom){rotation+=(float)dx*0.02f;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public void tick(){if(dragging.isEmpty()||scrollLayout()==null||net.minecraft.Util.getMillis()<nextScroll)return;var b=scrollLayout().content();int delta=pointerY<b.y()+8?-1:pointerY>b.bottom()-8?1:0;if(delta!=0){restoreScroll(Math.max(0,Math.min(entries().size()+1-visibleRows,firstRow+delta)));rebuildWidgets();}nextScroll=net.minecraft.Util.getMillis()+180;}
 @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){dragging="";super.resize(mc,width,height);}
 @Override boolean cancelInteraction(){if(!dragging.isEmpty()){dragging="";return true;}return super.cancelInteraction();}
 @Override public void onClose(){if(!moving.isEmpty())return;minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
