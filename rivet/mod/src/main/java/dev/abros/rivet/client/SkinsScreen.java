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
 private boolean capes;private int category;private int previewWidth;private int previewEquipment;private boolean secondLayer=true;private boolean choosing,uploadPending;private EditBox nameField;
 private final Screen parent;private RemotePlayer previewPlayer;private byte[] draft;private ResourceLocation draftTexture;private String draftName="",message="";private boolean slim;private float rotation;private int left,listWidth,previewBottom;private String query="";private String selection="";private boolean selectionReady;private String dragging="",moving="";private int moveTarget=-1;private double pointerY;private long nextScroll;
 SkinsScreen(Screen parent){this(parent,false);}
 SkinsScreen(Screen parent,boolean capes){super(Client.tr("wardrobe.title"));this.parent=parent;if(parent instanceof CommunityScreen menu)menu.cancelRead();this.capes=capes;this.category=capes?1:0;this.rotation=capes?(float)Math.PI:0;}
 private JsonObject library(){return capes?(SkinClient.library.has("capes")?SkinClient.library.getAsJsonObject("capes"):new JsonObject()):SkinClient.library;}
 private void select(String id){SkinClient.command(category==2?"outfitSelect":capes?(id.equals("hidden")?"capeHide":"capeSelect"):"select",id.equals("hidden")?"":id,false);}
 static void open(Screen parent){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new SkinsScreen(parent));SkinClient.command("list","",false);}
 boolean isPreview(net.minecraft.world.entity.Entity entity){return entity==previewPlayer;}
 private String active(){if(category==2){for(var e:entries())if(e.getAsJsonObject().get("selected").getAsBoolean())return Json.str(e.getAsJsonObject(),"id");return "";}if(capes)return library().has("hidden")&&library().get("hidden").getAsBoolean()?"hidden":Json.opt(library(),"active","");return SkinClient.library.has("profile")?Json.opt(SkinClient.library.getAsJsonObject("profile"),"active",""):"";}
 private boolean isApplied(String id){if(category==2){for(var e:entries()){var row=e.getAsJsonObject();if(id.equals(Json.str(row,"id")))return row.get("selected").getAsBoolean();}return false;}return id.equals(active());}
 private JsonObject selectedEntry(){for(var e:entries())if(Json.str(e.getAsJsonObject(),"id").equals(selection))return e.getAsJsonObject();return null;}
 void updated(){if(uploadPending&&!SkinClient.busy&&!SkinClient.retryable){uploadPending=false;if(SkinClient.status.isEmpty()){draft=null;draftTexture=null;selectionReady=false;}}if(selectionReady&&!selection.isEmpty()&&!selection.equals("hidden")&&selectedEntry()==null)selectionReady=false;if(!selectionReady){selection=active();selectionReady=true;}if(!moving.isEmpty()&&!SkinClient.busy){if(!SkinClient.status.isEmpty()||SkinClient.retryable){moving="";moveTarget=-1;}else continueMove();}rebuildWidgets();}
 private void continueMove(){int index=-1;for(int n=0;n<entries().size();n++)if(Json.str(entries().get(n).getAsJsonObject(),"id").equals(moving))index=n;if(index<0||index==moveTarget){moving="";moveTarget=-1;return;}SkinClient.command(index>moveTarget?"moveUp":"moveDown",moving,false);}
 private Button button(String text,int x,int y,int w,Runnable action){return addRenderableWidget(UiActions.button(Component.literal(text),UiActions.Tone.NORMAL,"",b->action.run()).bounds(x,y,w,20).build());}
 private JsonArray entries(){if(category==2)return SkinClient.library.has("outfits")?SkinClient.library.getAsJsonArray("outfits"):new JsonArray();return library().has("entries")?library().getAsJsonArray("entries"):new JsonArray();}
 private int stride(){return Math.min(AccessibilityScreen.skinStride(),Math.max(36,height-192));}
 private boolean editable(){return !SkinClient.busy&&!choosing&&moving.isEmpty()&&!uploadPending;}
 private JsonArray visibleEntries(){var rows=new JsonArray();for(var e:entries())if(Json.str(e.getAsJsonObject(),"name").toLowerCase(java.util.Locale.ROOT).contains(query.strip().toLowerCase(java.util.Locale.ROOT)))rows.add(e);return rows;}
 private int defaults(){if(!query.isBlank())return 0;return category==2?0:capes?2:1;}
 private String rowId(int row){return row<defaults()?(row==1?"hidden":""):Json.str(visibleEntries().get(row-defaults()).getAsJsonObject(),"id");}
 private void category(int next){if(!editable()||draft!=null)return;cancelInteraction();category=next;capes=next==1;selection=active();selectionReady=true;message="";query="";resetScroll();rotation=capes?(float)Math.PI:0;rebuildWidgets();}
 @Override protected void init(){
  left=UiPage.body(width,height).x()+8;int total=width-left*2;previewWidth=Math.max(88,Math.min(260,total/3));int collection=left+previewWidth+24;listWidth=width-left-collection;
  var tabs=new java.util.ArrayList<String>();var categories=new java.util.ArrayList<Integer>();tabs.add(Client.text("ui.skins_bef1a24f"));categories.add(0);
  if(dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-capes")&&SkinClient.library.has("capes")&&SkinClient.library.getAsJsonObject("capes").get("enabled").getAsBoolean()){tabs.add(Client.text("cape.title"));categories.add(1);}
  if(dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-outfits")){tabs.add(Client.text("outfit.title"));categories.add(2);}
  if(!categories.contains(category)){category=0;capes=false;selectionReady=false;draft=null;draftTexture=null;}
  UiTabs.build(this,font,new dev.abros.rivet.core.NativeLayout.Box(collection,42,listWidth,20),tabs,categories.indexOf(category),this::addRenderableWidget,n->category(categories.get(n)),editable()&&draft==null);
  if(!selectionReady&&SkinClient.library.has("profile")){selection=active();selectionReady=true;}
  int bottom=height-96;
  if(draft==null){
   var rows=visibleEntries();
   scrollArea(rows.size()+defaults(),new dev.abros.rivet.core.NativeLayout.Box(collection,96,listWidth,Math.max(0,bottom-96)),stride());
   for(int row=firstRow;row<Math.min(rows.size()+defaults(),firstRow+visibleRows);row++){
    var entry=row<defaults()?null:rows.get(row-defaults()).getAsJsonObject();String id=rowId(row);int y=96+(row-firstRow)*stride();
    int cardWidth=scrollLayout().content().width()-(category==0&&query.isBlank()?22:0);
    var card=!capes?addRenderableWidget(new UiSkinCard(collection,y,cardWidth,stride()-6,entry,id.equals(selection),isApplied(id),false,()->{selection=id;message="";rebuildWidgets();})):addRenderableWidget(UiSkinCard.cape(collection,y,cardWidth,stride()-6,entry,id.equals("hidden"),id.equals(selection),isApplied(id),()->{selection=id;message="";rebuildWidgets();}));
    card.active=editable();
    if(category==2&&!entry.get("available").getAsBoolean())card.setTooltip(Tooltip.create(Client.tr("outfit.unavailable")));
   }
   String addLabel=Client.text(category==2?"outfit.save":"ui.add_png_d4a93ac3");
   int limit=category==2?(SkinClient.library.has("outfitLimit")?SkinClient.library.get("outfitLimit").getAsInt():0):library().has("limit")?library().get("limit").getAsInt():0;
   int addWidth=Math.min(180,listWidth/2);var search=addRenderableWidget(UiFields.text(font,collection,68,listWidth-addWidth-6,20,Client.tr("wardrobe.search")));search.setHint(Client.tr("wardrobe.search"));search.setMaxLength(40);search.setValue(query);search.setResponder(v->{query=v;resetScroll();rebuildWidgets();});search.active=editable();
   var add=button(choosing?Client.text("ui.selecting_file_247b7c5f"):addLabel+" · "+entries().size()+" / "+limit,collection+listWidth-addWidth,68,addWidth,()->{if(category==2)minecraft.setScreen(new NameScreen(this,"","","outfitSave"));else chooseFile();});
   add.active=editable()&&!PICKER.get()&&SkinClient.available()&&entries().size()<limit;
   int maxResolution=library().has("maxResolution")?library().get("maxResolution").getAsInt():64;add.setTooltip(Tooltip.create(category==2?Client.tr("wardrobe.saveHint"):Client.tr("skin.uploadDimensions",maxResolution,capes?maxResolution/2:maxResolution)));
  }else{
   nameField=addRenderableWidget(UiFields.text(font,collection,96,listWidth,20,Client.tr(capes?"cape.name":"ui.skin_name_3f916450")));nameField.setMaxLength(40);nameField.setValue(draftName);nameField.setResponder(v->draftName=v);nameField.active=!SkinClient.busy;
   if(!capes)button(slim?Client.text("ui.slim_arms_894a2812"):Client.text("ui.classic_arms_87d0a86d"),collection,120,listWidth,()->{slim=!slim;rebuildWidgets();}).active=!SkinClient.busy;
   button(Client.text("cancel"),collection,capes?120:144,listWidth,()->{draft=null;draftTexture=null;message="";rebuildWidgets();}).active=!SkinClient.busy;
  }
  previewBottom=height-122;
  button(Client.text("ui.restore_view_8cace5b2"),left,height-118,previewWidth,()->rotation=0);
  button(Client.text("ui.second_layer_a465cbee")+(secondLayer?Client.text("ui.on_a60b4b83"):Client.text("ui.off_622574d5")),left,height-94,previewWidth,()->{secondLayer=!secondLayer;rebuildWidgets();});
  if(draft==null){var actions=button(Client.text("ui.actions_928503bd"),collection,height-90,listWidth,this::actions);actions.active=editable();}
  button(Client.text("wardrobe.turn"),left,height-70,previewWidth,()->rotation+=(float)Math.PI);
  var entry=selectedEntry();boolean unavailable=category==2&&(entry==null||!entry.get("available").getAsBoolean());
  var apply=addRenderableWidget(UiActions.button(Client.tr(SkinClient.busy?"ui.applying_190c3756":draft!=null?"ui.upload_and_apply_eb06bef3":isApplied(selection)?"wardrobe.applied":"map.caveApply"),UiActions.Tone.PRIMARY,"",b->{
   if(draft!=null){if(draftName.strip().isEmpty()){message=Client.text("ui.enter_a_skin_name_299b5e8e");return;}uploadPending=true;SkinClient.upload(draftName.strip(),slim,draft,capes);}else select(selection);rebuildWidgets();
  }).bounds(collection,height-34,listWidth,20).build());apply.active=editable()&&!unavailable&&(draft!=null||!isApplied(selection));
  if(SkinClient.retryable)button(Client.text("ui.retry_request_fcb97cf7"),collection,height-66,listWidth,SkinClient::retry);
  button(Client.text("back"),left,height-34,previewWidth,this::onClose).active=moving.isEmpty()&&!uploadPending;
  if(minecraft.level!=null&&minecraft.player!=null&&previewPlayer==null)previewPlayer=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){@Override public PlayerSkin getSkin(){return previewSkin();}@Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return part==net.minecraft.world.entity.player.PlayerModelPart.CAPE||secondLayer;}};
  // This detached preview never ticks: initialise the history consumed by vanilla CapeLayer.
  if(previewPlayer!=null){
   previewPlayer.xo=previewPlayer.xCloakO=previewPlayer.xCloak=previewPlayer.getX();
   previewPlayer.yo=previewPlayer.yCloakO=previewPlayer.yCloak=previewPlayer.getY();
   previewPlayer.zo=previewPlayer.zCloakO=previewPlayer.zCloak=previewPlayer.getZ();
  }
  updateEquipment();
 }
 private void actions(){var entry=selectedEntry();String id=selection;var labels=new java.util.ArrayList<String>();var actions=new java.util.ArrayList<Runnable>();
  labels.add(Client.text("skin.preview.equipment"));actions.add(()->minecraft.setScreen(new ChoicePopup(this,Client.text("skin.preview.equipment"),java.util.List.of(Client.text("skin.preview.none"),Client.text("skin.preview.current"),Client.text("skin.preview.armor"),Client.text("skin.preview.elytra")),n->{previewEquipment=n;updateEquipment();}).current(previewEquipment)));
  if(category==0&&draft==null){labels.add(Client.text(SkinWheelFavorites.contains(id)?"skins.wheel.remove":"skins.wheel.add"));actions.add(()->{String error=SkinWheelFavorites.toggle(id);message=error.isEmpty()?"":Client.text(error);rebuildWidgets();});}
  if(draft==null&&SkinClient.library.has("canUndoAppearance")&&SkinClient.library.get("canUndoAppearance").getAsBoolean()&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("appearance-undo")){labels.add(Client.text("skin.undoAppearance"));actions.add(()->{selectionReady=false;SkinClient.command("appearanceUndo","",false);});}
  else if(!dev.abros.rivet.network.Protocol.supportedFeatures.contains("appearance-undo")&&!capes&&draft==null&&SkinClient.library.has("canUndo")&&SkinClient.library.get("canUndo").getAsBoolean()&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-undo")){labels.add(Client.text("skin.undo"));actions.add(()->{selectionReady=false;SkinClient.command("undo","",false);});}
  if(category==0&&entry!=null&&draft==null){
  if(dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-names")){labels.add(Client.text("ui.rename_715e8f0c"));actions.add(()->minecraft.setScreen(new NameScreen(this,id,Json.str(entry,"name"))));}
  labels.add(entry.get("slim").getAsBoolean()?Client.text("ui.use_classic_arms_45286ef5"):Client.text("ui.use_slim_arms_40a8a1d9"));actions.add(()->SkinClient.command("model",id,!entry.get("slim").getAsBoolean()));

  labels.add(Client.text("ui.delete_f43320b0"));actions.add(()->minecraft.setScreen(new UiConfirmDialog(ok->{minecraft.setScreen(this);if(ok){selection="";SkinClient.command("delete",id,false);}},Client.tr("ui.delete_skin_67f3302d"),Component.literal(Json.str(entry,"name"))).dangerous()));
  }
  if(category==2&&entry!=null&&draft==null){String outfitId="outfit:"+id;
   if(entry.get("available").getAsBoolean()){labels.add(Client.text(SkinWheelFavorites.contains(outfitId)?"skins.wheel.remove":"skins.wheel.add"));actions.add(()->{String error=SkinWheelFavorites.toggle(outfitId);message=error.isEmpty()?"":Client.text(error);rebuildWidgets();});}
   labels.add(Client.text("ui.delete_f43320b0"));actions.add(()->minecraft.setScreen(new UiConfirmDialog(ok->{minecraft.setScreen(this);if(ok){selectionReady=false;SkinClient.command("outfitDelete",id,false);}},Client.tr("outfit.delete"),Component.literal(Json.str(entry,"name"))).dangerous()));
  }
  if(capes&&entry!=null&&draft==null){labels.add(Client.text("ui.delete_f43320b0"));actions.add(()->minecraft.setScreen(new UiConfirmDialog(ok->{minecraft.setScreen(this);if(ok){selectionReady=false;SkinClient.command("capeDelete",id,false);}},Client.tr("cape.delete"),Component.literal(Json.str(entry,"name"))).dangerous()));}
  var popup=new ChoicePopup(this,Client.text("ui.actions_9978ac34"),labels,n->actions.get(n).run()).anchorLabel(Client.text("ui.actions_928503bd"));if(entry!=null&&draft==null){int delete=labels.indexOf(Client.text("ui.delete_f43320b0"));if(delete>=0)popup.danger(delete);}minecraft.setScreen(popup);
 }
 private void updateEquipment(){if(previewPlayer==null||minecraft.player==null)return;
  var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET};
  var armor=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.DIAMOND_HELMET,previewEquipment==3?net.minecraft.world.item.Items.ELYTRA:net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,net.minecraft.world.item.Items.DIAMOND_LEGGINGS,net.minecraft.world.item.Items.DIAMOND_BOOTS};
  for(int n=0;n<slots.length;n++)previewPlayer.setItemSlot(slots[n],previewEquipment==0?net.minecraft.world.item.ItemStack.EMPTY:previewEquipment==1?minecraft.player.getItemBySlot(slots[n]).copy():new net.minecraft.world.item.ItemStack(armor[n]));
 }
 private void chooseFile(){
  if(!PICKER.compareAndSet(false,true))return;
  choosing=true;message="";var client=minecraft;var connection=client.getConnection();var level=client.level;int limit=library().get("maxBytes").getAsInt();int resolution=library().has("maxResolution")?library().get("maxResolution").getAsInt():64;rebuildWidgets();
  FILES.execute(()->{
   byte[] bytes=null;String label="",failure="";
   try(var stack=MemoryStack.stackPush()){
    var patterns=stack.mallocPointer(1);patterns.put(stack.UTF8("*.png")).flip();
    String chosen=TinyFileDialogs.tinyfd_openFileDialog(Client.text(capes?"cape.selectFile":"ui.select_a_png_skin_ebe006f7"),null,patterns,capes?"Minecraft cape PNG":"Minecraft skin PNG",false);
    if(chosen!=null){var path=Path.of(chosen);if(!Files.isRegularFile(path)||Files.size(path)>limit)throw new java.io.IOException();try(var in=Files.newInputStream(path)){bytes=capes?dev.abros.rivet.core.skins.CapeImage.normalize(in.readNBytes(limit+1),limit,resolution):SkinImage.normalize(in.readNBytes(limit+1),limit,resolution);}label=path.getFileName().toString().replaceFirst("(?i)\\.png$","").replaceAll("[\\p{Cntrl}]","");if(label.length()>40)label=label.substring(0,40);if(label.isBlank())label=Client.text("ui.skin_e5519889");}
   }catch(Exception error){failure=Client.text(capes?"cape.invalid":"ui.a_png_of_64_64_or_5c9963b5");}finally{PICKER.set(false);}
   final byte[] result=bytes;final String name=label,error=failure;
   client.execute(()->{choosing=false;if(client.screen!=this||client.getConnection()!=connection||client.level!=level)return;message=error;if(result!=null)try{draftTexture=capes?SkinClient.capePreview(result):SkinClient.preview(result);draft=result;draftName=name;slim=false;}catch(Exception invalid){message=Client.text("ui.could_not_prepare_the_skin_5b00a31c");}rebuildWidgets();});
  });
 }
 private static final class NameScreen extends Screen {
  private final SkinsScreen parent;private final String id,op;private String value;private EditBox field;
  NameScreen(SkinsScreen parent,String id,String value){this(parent,id,value,"rename");}
  NameScreen(SkinsScreen parent,String id,String value,String op){super(Client.tr(op.equals("outfitSave")?"outfit.name":"ui.skin_name_3f916450"));this.parent=parent;this.id=id;this.value=value;this.op=op;}
  @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(300,width-32),height/2-54,height/2+44);}
  @Override protected void init(){int w=Math.min(300,width-32),x=(width-w)/2,y=height/2-20;field=addRenderableWidget(UiFields.text(font,x,y,w,20,title));field.setMaxLength(40);field.setValue(value);var save=addRenderableWidget(UiActions.button(Client.tr("map.tool.save"),UiActions.Tone.PRIMARY,UiIcons.SAVE,b->{minecraft.setScreen(parent);SkinClient.command(op,id,false,value.strip());}).bounds(x,y+30,(w-4)/2,20).build());save.active=!value.isBlank();field.setResponder(v->{value=v;save.active=!v.isBlank();});addRenderableWidget(UiActions.button(Client.tr("cancel"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x+(w-4)/2+4,y+30,w-(w-4)/2-4,20).build());setInitialFocus(field);}
  @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,(width-Math.min(300,width-32))/2,height/2-54,Math.min(300,width-32));});}
  @Override public void onClose(){minecraft.setScreen(parent);}
  @Override public boolean isPauseScreen(){return false;}
 }
 private PlayerSkin previewSkin(){
  if(category==2){var outfit=selectedEntry();if(outfit==null)return SkinClient.skin(minecraft.player.getUUID());var skin=UiSkinCard.appearance(outfit);ResourceLocation cape=null;if(!outfit.get("hidden").getAsBoolean()){String id=Json.str(outfit,"cape");cape=SkinClient.ordinarySkin().capeTexture();if(!id.isEmpty()&&SkinClient.library.has("capes"))for(var e:SkinClient.library.getAsJsonObject("capes").getAsJsonArray("entries"))if(id.equals(Json.str(e.getAsJsonObject(),"id")))cape=SkinClient.capeTexture(Json.str(e.getAsJsonObject(),"hash"));}return new PlayerSkin(skin.texture(),skin.textureUrl(),cape,SkinClient.elytraTexture(cape,skin.elytraTexture()),skin.model(),skin.secure());}
  if(capes){var skin=SkinClient.skin(minecraft.player.getUUID());ResourceLocation cape=draftTexture;if(draft==null){var entry=selectedEntry();cape=selection.equals("hidden")?null:entry==null?SkinClient.ordinarySkin().capeTexture():SkinClient.capeTexture(Json.str(entry,"hash"));}return new PlayerSkin(skin.texture(),skin.textureUrl(),cape,SkinClient.elytraTexture(cape,skin.elytraTexture()),skin.model(),skin.secure());}
  var current=SkinClient.skin(minecraft.player.getUUID());if(draftTexture!=null)return new PlayerSkin(draftTexture,null,current.capeTexture(),current.elytraTexture(),slim?PlayerSkin.Model.SLIM:PlayerSkin.Model.WIDE,false);
  var skin=UiSkinCard.appearance(selectedEntry());return new PlayerSkin(skin.texture(),skin.textureUrl(),current.capeTexture(),current.elytraTexture(),skin.model(),skin.secure());
 }
  @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){super.renderBackground(g,mx,my,delta);UiPage.draw(g,width,height);UiKit.material(g,left-8,34,previewWidth+16,height-76);UiKit.material(g,left+previewWidth+16,34,listWidth+16,height-76);}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);UiHeading.page(g,font,title,width);
  if(draft==null&&visibleEntries().isEmpty()&&defaults()==0)Ui.text(g,font,UiKit.fit(font,Client.text(query.isBlank()?"outfit.empty":"ui.nothing_found_1e1b70b1"),listWidth-12),left+previewWidth+24,100,UiKit.muted(),false);
  if(draft!=null)Ui.text(g,font,Client.text("map.name"),left+previewWidth+24,66,UiKit.text(),false);
  if(draft==null&&category==0&&query.isBlank()&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-order")&&scrollLayout()!=null){var b=scrollLayout().content();for(int row=Math.max(1,firstRow);row<Math.min(entries().size()+1,firstRow+visibleRows);row++)UiDragHandle.draw(g,b.right()-UiDragHandle.WIDTH,96+(row-firstRow)*stride(),rowId(row).equals(dragging));}
  if(!dragging.isEmpty()&&dropRow(mx,my)>=0){var b=scrollLayout().content();int yy=b.y()+((my-b.y())/stride())*stride();g.fill(b.x(),yy,b.right(),yy+2,UiKit.accent());}
  if(previewPlayer!=null&&previewBottom>60){previewPlayer.yBodyRotO=previewPlayer.yBodyRot=180;previewPlayer.yRotO=180;previewPlayer.setYRot(180);previewPlayer.yHeadRot=180;previewPlayer.yHeadRotO=180;g.enableScissor(left,44,left+previewWidth,previewBottom);InventoryScreen.renderEntityInInventory(g,left+previewWidth/2f,(44+previewBottom)/2f,Math.max(14,Math.min(previewWidth*.7f,(previewBottom-54)/2.2f)),new org.joml.Vector3f(0,previewPlayer.getBbHeight()/2,0),new org.joml.Quaternionf().rotateZ((float)Math.PI).rotateY(rotation),null,previewPlayer);g.disableScissor();}
  String text=message.isEmpty()?SkinClient.status:message;
  if(text.isEmpty()){var entry=selectedEntry();text=draft!=null?draftName:entry!=null?Json.str(entry,"name"):category==2?Client.text("outfit.empty"):Client.text(capes?(selection.equals("hidden")?"cape.hide":"cape.official"):"ui.default_skin_b097c1e1");}
  if(!SkinClient.retryable)Ui.text(g,font,UiKit.fit(font,text,listWidth),left+previewWidth+24,height-58,UiKit.muted(),false);
 }
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&draft==null&&!SkinClient.busy&&category==0&&query.isBlank()&&dev.abros.rivet.network.Protocol.supportedFeatures.contains("skin-order")&&scrollLayout()!=null&&x>=scrollLayout().content().right()-UiDragHandle.WIDTH&&x<scrollLayout().content().right()&&y>=96&&y<height-96){int row=firstRow+(int)((y-96)/stride());if(dropRow(x,y)>=0){dragging=Json.str(entries().get(row-1).getAsJsonObject(),"id");pointerY=y;return true;}}return super.mouseClicked(x,y,button);}
 private int dropRow(double x,double y){if(scrollLayout()==null)return -1;var b=scrollLayout().content();int row=firstRow+(int)((y-b.y())/stride());return b.contains(x,y)&&y<b.y()+visibleRows*stride()&&row>0&&row<=entries().size()?row-1:-1;}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){String id=dragging;dragging="";int row=dropRow(x,y);if(row>=0){moving=id;moveTarget=row;continueMove();rebuildWidgets();}return true;}return super.mouseReleased(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&!dragging.isEmpty()){pointerY=y;return true;}if(button==0&&x>=left&&x<left+previewWidth&&y>=44&&y<previewBottom){rotation+=(float)dx*0.02f;return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public void tick(){if(dragging.isEmpty()||scrollLayout()==null||net.minecraft.Util.getMillis()<nextScroll)return;var b=scrollLayout().content();int delta=pointerY<b.y()+8?-1:pointerY>b.bottom()-8?1:0;if(delta!=0){restoreScroll(Math.max(0,Math.min(entries().size()+1-visibleRows,firstRow+delta)));rebuildWidgets();}nextScroll=net.minecraft.Util.getMillis()+180;}
 @Override public void resize(net.minecraft.client.Minecraft mc,int width,int height){dragging="";super.resize(mc,width,height);}
 @Override boolean cancelInteraction(){if(!dragging.isEmpty()){dragging="";return true;}return super.cancelInteraction();}
 @Override public void onClose(){if(!moving.isEmpty())return;minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
