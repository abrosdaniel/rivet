package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.*;

/** Explicit local import; no external mod or server endpoint is required. */
final class MapImportScreen extends ScrollScreen {
 private static final java.util.concurrent.ExecutorService FILES=java.util.concurrent.Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"rivet-waypoint-import");t.setDaemon(true);return t;});
 private static final java.util.concurrent.atomic.AtomicBoolean PICKER=new java.util.concurrent.atomic.AtomicBoolean();
 private final Screen parent;private final MapRepository owner=WorldMapClient.ownRepository();
 private MapWaypointImport.Preview preview=new MapWaypointImport.Preview(List.of(),List.of());
 private final Set<Integer> selected=new HashSet<>();private final Map<String,String> dimensions=new LinkedHashMap<>();
 private MapWaypointImport.Plan plan;private List<MapMarker> plannedBase;private String planError="";
 private UiDialog dialog;private NativeLayout.Box area;private Button apply;private boolean busy,finished;private String status=Client.text("ui.select_files_or_drag_them_into_ed407917");
 MapImportScreen(Screen parent){super(Client.tr("ui.import_waypoints_15dce4fd"));this.parent=parent;}
 private Button button(String label,int x,int y,int w,Runnable action){var b=addRenderableWidget(UiActions.button(Component.literal(label),UiActions.Tone.NORMAL,"",ignored->action.run()).bounds(x,y,w,24).build());if(font.width(label)>w-16)b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));return b;}

 @Override protected void init(){
  plannedBase=null;dialog=UiDialog.fit(width,height,520,390);var body=dialog.body();int half=(body.width()-8)/2;
  button(Client.text("ui.select_files_371fb87d"),body.x(),body.y(),half,()->choose(false)).active=!busy;
  button(Client.text("ui.waypoint_folder_9e2c0e73"),body.x()+half+8,body.y(),body.width()-half-8,()->choose(true)).active=!busy;
  var viewport=new NativeLayout.Box(body.x(),body.y()+48,body.width(),Math.max(0,body.height()-48));
  var keys=new ArrayList<>(dimensions.keySet());int count=keys.size()+preview.entries().size()+preview.issues().size();area=scrollArea(count,viewport,40).content();
  for(int n=firstRow;n<Math.min(count,firstRow+visibleRows);n++){int y=area.y()+(n-firstRow)*40;
   if(n<keys.size()){String source=keys.get(n),target=dimensions.get(source);button(mappingLabel(source,target),area.x(),y,area.width(),()->chooseDimension(source)).active=!busy&&!finished;}
   else if(n<keys.size()+preview.entries().size()){int index=n-keys.size();var e=preview.entries().get(index);addRenderableWidget(new UiChoiceRow(area.x(),y,area.width()-26,e.name(),selected.contains(index),-1,UiKit.accent(),()->{if(!selected.add(index))selected.remove(index);rebuildWidgets();})).active=!busy&&!finished;addRenderableWidget(new UiColorSwatch(area.right()-18,y+3,e.color(),Component.literal(String.format(java.util.Locale.ROOT,"#%06X",e.color()&0xffffff)),false,()->{})).active=false;}
  }
  var footer=dialog.footer();apply=addRenderableWidget(UiActions.button(Client.tr("ui.import_55fd5f8a"),UiActions.Tone.PRIMARY,"",b->commit()).bounds(footer.x(),footer.bottom()-24,Math.max(1,(footer.width()-8)/2),24).build());
  button(Client.text("map.tool.clear"),footer.x()+(footer.width()+8)/2,footer.bottom()-24,(footer.width()-8)/2,this::onClose);updateApply();
 }
 private List<MapWaypointImport.Entry> selection(){return selected.stream().sorted().map(preview.entries()::get).toList();}
 private void updateApply(){
  if(apply==null)return;var base=WorldMapClient.markers();
  if(plannedBase!=base){plannedBase=base;plan=null;planError="";try{plan=MapWaypointImport.plan(selection(),dimensions,base);}catch(IllegalArgumentException ex){planError=ex.getMessage();}}
  apply.active=!busy&&!finished&&owner!=null&&owner==WorldMapClient.ownRepository()&&WorldMapClient.ready()&&WorldMapClient.markersReady()&&plan!=null&&!plan.additions().isEmpty();
 }
 private static String dimensionLabel(String id){return dev.abros.rivet.network.DimensionLabels.name(id).getString();}
 private static String mappingLabel(String source,String target){return (source.startsWith("?")?Client.text("ui.no_dimension_555a3684")+MapWaypointImport.label(source.substring(1)):dimensionLabel(source))+" → "+(target.isEmpty()?Client.text("ui.select_6279ddde"):dimensionLabel(target));}
 private void chooseDimension(String source){var options=new LinkedHashSet<String>();if(minecraft.getConnection()!=null)for(var d:minecraft.getConnection().levels())options.add(d.location().toString());options.addAll(List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end"));if(source.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))options.add(source);var values=List.copyOf(options);minecraft.setScreen(new ChoicePopup(this,Client.text("ui.waypoint_dimension_48108508"),values.stream().map(MapImportScreen::dimensionLabel).toList(),i->{dimensions.put(source,values.get(i));rebuildWidgets();}).anchorLabel(mappingLabel(source,dimensions.get(source))));}
 private void choose(boolean folder){if(busy||!PICKER.compareAndSet(false,true))return;busy=true;rebuildWidgets();var client=minecraft;
  FILES.execute(()->{List<Path> paths=List.of();String error="";try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
   String chosen;if(folder)chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog(Client.text("ui.select_the_waypoint_folder_for_one_323802bd"),null);
   else{var patterns=stack.mallocPointer(2);patterns.put(stack.UTF8("*.txt")).put(stack.UTF8("*.dat")).flip();chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(Client.text("ui.xaero_s_journeymap_waypoints_0650f5da"),null,patterns,"TXT, DAT",true);}
   if(chosen!=null)paths=folder?List.of(Path.of(chosen)):Arrays.stream(chosen.split("\\|",-1)).map(Path::of).toList();
  }catch(Exception ex){error=Client.text("ui.could_not_open_the_file_picker_f2f331e1");}finally{PICKER.set(false);}var result=paths;var failure=error;client.execute(()->{busy=false;if(client.screen!=this)return;if(!result.isEmpty())load(result);else{if(!failure.isEmpty())status=failure;rebuildWidgets();}});});
 }
 static com.google.gson.JsonObject readDat(byte[] bytes)throws java.io.IOException {
  try(var input=new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes))){var tag=net.minecraft.nbt.NbtIo.read(input,net.minecraft.nbt.NbtAccounter.create(16*1024*1024));if(input.read()!=-1||!tag.contains("waypoints",10))throw new java.io.IOException(Client.text("message.invalid_waypoint_dat"));return net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,tag).getAsJsonObject();}
 }
 void load(List<Path> paths){if(busy)return;busy=true;status=Client.text("ui.reading_files_caa86c0b");rebuildWidgets();var client=minecraft;FILES.execute(()->{MapWaypointImport.Preview result=null;String error="";try{result=MapWaypointImport.read(paths,MapImportScreen::readDat);}catch(Exception ex){error=ex instanceof java.io.IOException?ex.getMessage():Client.text("ui.could_not_read_waypoints_7c2eea9b");}var data=result;var failure=error;client.execute(()->{busy=false;if(client.screen!=this)return;if(owner!=WorldMapClient.ownRepository()){status=Client.text("ui.the_world_has_changed_reopen_the_2efb827f");rebuildWidgets();return;}if(data!=null){preview=data;dimensions.clear();selected.clear();finished=false;for(int i=0;i<data.entries().size();i++){var e=data.entries().get(i);selected.add(i);dimensions.putIfAbsent(e.mappingKey(),e.dimension().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")?e.dimension():"");}status=Client.text("ui.review_dimensions_and_selected_waypoints_490936ff");resetScroll();}else status=failure;rebuildWidgets();});});}
 @Override public void onFilesDrop(List<Path> paths){load(paths);}
 private void commit(){if(!apply.active)return;try{busy=true;WorldMapClient.importMarkers(owner,selection(),Map.copyOf(dimensions),message->{busy=false;finished=message.startsWith(Client.text("ui.imported_11599910"));status=message;rebuildWidgets();});status=Client.text("ui.saving_waypoints_40329e8f");}catch(IllegalStateException|IllegalArgumentException ex){busy=false;status=ex.getMessage();}rebuildWidgets();}
 @Override public void tick(){if(owner!=WorldMapClient.ownRepository()){busy=false;finished=true;status=Client.text("ui.the_world_has_changed_reopen_the_2efb827f");}updateApply();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int mouseY,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,mouseY,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());String summary=Client.text("ui.xaero_s_txt_journeymap_dat_personal_fd1f2740");
  if(!preview.entries().isEmpty()){summary=Client.text("ui.selected_3ef8b742")+selected.size()+Client.text("ui.issues_6ec179aa")+preview.issues().size();if(plan!=null)summary+=Client.text("ui.new_be44d90e")+plan.additions().size()+Client.text("ui.duplicates_0941fe10")+plan.duplicates();else summary+=" · "+planError;}
  Ui.text(g,font,UiKit.fit(font,summary,dialog.body().width()),dialog.body().x(),dialog.body().y()+32,UiKit.muted(),false);
  int keys=dimensions.size(),count=keys+preview.entries().size()+preview.issues().size();for(int n=firstRow;n<Math.min(count,firstRow+visibleRows);n++){int y=area.y()+(n-firstRow)*40;if(n>=keys&&n<keys+preview.entries().size()){var e=preview.entries().get(n-keys);String dim=dimensions.getOrDefault(e.mappingKey(),e.dimension());Ui.text(g,font,UiKit.fit(font,e.x()+", "+e.y()+", "+e.z()+" · "+(dim.isEmpty()?Client.text("ui.select_a_dimension_01c9d531"):dimensionLabel(dim))+(e.visible()?"":Client.text("ui.hidden_5460218d")),area.width()),area.x(),y+27,UiKit.muted(),false);}else if(n>=keys+preview.entries().size())Ui.text(g,font,UiKit.fit(font,preview.issues().get(n-keys-preview.entries().size()),area.width()),area.x(),y+6,UiKit.muted(),false);}
  Ui.text(g,font,UiKit.fit(font,status,dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.muted(),false);
 });}
 @Override public void onClose(){if(!busy)minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
