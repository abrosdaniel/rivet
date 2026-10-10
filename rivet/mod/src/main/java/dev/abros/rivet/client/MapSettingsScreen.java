package dev.abros.rivet.client;

import java.util.*;
import java.util.stream.IntStream;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One module owns world map, minimap, markers, navigation and radar preferences. */
class MapSettingsScreen extends ScrollScreen implements SettingsTarget, CommunityScreen.Receiver {
 static final List<String> CATEGORIES=Client.labels(()->List.of(Client.text("ui.rendering_b735d754"),Client.text("map.minimap"),Client.text("map.tool.markers"),Client.text("map.tool.navigate"),Client.text("ui.radar_0451ab68"),Client.text("map.caves")));
 static List<String> categories(){var result=new ArrayList<>(CATEGORIES);if(MapPositions.available())result.add(Client.text("ui.visibility_bd3fe393"));return List.copyOf(result);}
 private final Screen parent;private UiDialog dialog;private int category;
 private final MapPositionSettings privacy=new MapPositionSettings(this,this::rebuildWidgets);private NativeLayout.Box panelArea;
 private final String dimension;private String draft,error="";
 MapSettingsScreen(Screen parent){this(parent,0);}
 MapSettingsScreen(Screen parent,int category){this(parent,category,parent instanceof WorldMapScreen map?map.selectedDimension():net.minecraft.client.Minecraft.getInstance().level==null?"minecraft:overworld":net.minecraft.client.Minecraft.getInstance().level.dimension().location().toString());}
 MapSettingsScreen(Screen parent,int category,String dimension){super(Client.tr("map.settings"));this.parent=parent;this.category=category;this.dimension=dimension;refreshCaveDraft();}
 private void refreshCaveDraft(){var v=MapCaves.view(dimension);draft=v.mode()==2?Integer.toString(v.top()):"Auto";error="";}
 Screen parentScreen(){return parent;}
 void selectCategory(int value){category=value;if(value==5)refreshCaveDraft();restoreScroll(0);}
 private void change(Runnable action){action.run();MapRenderSettings.INSTANCE.changed();rebuildWidgets();}
 private void toggle(String label,int x,int y,int w,boolean value,Runnable action){addRenderableWidget(new UiToggle(label,x,y,w,value,()->change(action)));}
 private void choice(String label,int x,int y,int w,List<String> values,int selected,java.util.function.IntConsumer apply){addRenderableWidget(UiActions.button(Component.literal(label+": "+values.get(selected)+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,label,values,n->change(()->apply.accept(n)),b).current(selected))).bounds(x,y,w,24).build());}
 @Override protected void init(){
  if(category>=categories().size())category=0;int count=category<6?new int[]{12,9,3,3,6,11}[category]:6;
  dialog=UiSettingsShell.module(width,height,category,this,this::addRenderableWidget,categories(),n->{selectCategory(n);rebuildWidgets();},()->minecraft.setScreen(new SettingsSearchScreen(this,true)),()->UiSettingsShell.confirm(this,this::reset),this::onClose);
  var rows=dialog.body();var area=scrollForm(count,rows,28);
  if(category>=6){panelArea=area.content();privacy.build(panelArea,firstRow,visibleRows,this::addRenderableWidget);for(var child:children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text("ui.reset_section_3a46364b")))b.visible=b.active=false;return;}
  if(category==5){caveRows(rows,area.content().width());return;}
  var s=MapSettings.INSTANCE;var r=MapRenderSettings.INSTANCE;var h=MapSettings.INSTANCE.hud;
  for(int n=firstRow;n<Math.min(count,firstRow+visibleRows);n++){int x=rows.x(),y=rows.y()+(n-firstRow)*28,w=area.content().width();
   if(category==0)switch(n){
    case 0->choice(Client.text("ui.block_colors_1cae71e1"),x,y,w,List.of(Client.text("ui.accurate_a839c237"),"Minecraft"),r.blockColors,i->{r.blockColors=i;r.samplingChanged();});
    case 1->{toggle(Client.text("ui.lighting_22240aa5"),x,y,w,r.lighting,()->r.lighting=!r.lighting);((net.minecraft.client.gui.components.AbstractWidget)children().getLast()).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Client.tr("ui.automatically_uses_time_of_day_weather_31ad9f37")));}
    case 2->toggle(Client.text("ui.height_shading_89016c01"),x,y,w,r.depth,()->r.depth=!r.depth);
    case 3->choice(Client.text("ui.slope_shading_4adacc50"),x,y,w,List.of(Client.text("map.caveOff"),Client.text("ui.simple_e16892c5"),Client.text("ui.directional_b5add2d8"),Client.text("ui.vertical_b072cefd")),r.slopes,i->r.slopes=i);
    case 4->toggle(Client.text("ui.biome_color_blending_4228e24e"),x,y,w,r.biomeBlend,()->{r.biomeBlend=!r.biomeBlend;r.samplingChanged();});
    case 5->toggle(Client.text("ui.minecraft_biome_colors_b7010c7b"),x,y,w,r.biomesVanilla,()->{r.biomesVanilla=!r.biomesVanilla;r.samplingChanged();});
    case 6->toggle(Client.text("ui.flowers_aaaa320c"),x,y,w,r.flowers,()->{r.flowers=!r.flowers;r.samplingChanged();});
    case 7->toggle(Client.text("ui.redstone_4218d5ab"),x,y,w,r.redstone,()->{r.redstone=!r.redstone;r.samplingChanged();});
    case 8->toggle(Client.text("ui.stained_glass_a570c3ac"),x,y,w,r.stainedGlass,()->{r.stainedGlass=!r.stainedGlass;r.samplingChanged();});
    case 9->toggle(Client.text("ui.block_transparency_ef7e07ea"),x,y,w,r.transparency,()->{r.transparency=!r.transparency;r.samplingChanged();});
    case 10->toggle(Client.text("ui.use_thin_block_height_db2e58ce"),x,y,w,r.shortBlocks,()->{r.shortBlocks=!r.shortBlocks;r.samplingChanged();});
    case 11->toggle(Client.text("ui.biome_under_the_world_map_cursor_762b9868"),x,y,w,r.hoverBiome,()->r.hoverBiome=!r.hoverBiome);
   }
   else if(category==1)switch(n){
    case 0->toggle(Client.text("ui.show_minimap_907debf3"),x,y,w,s.enabled,()->s.enabled=!s.enabled);
    case 1->toggle(Client.text("ui.round_shape_7ded0b9c"),x,y,w,s.round,()->s.round=!s.round);
    case 2->toggle(Client.text("ui.rotate_with_view_fca119f6"),x,y,w,s.rotate,()->s.rotate=!s.rotate);
    case 3->{float[] z={.25f,.5f,1,2,4};choice(Client.text("ui.minimap_zoom_a91410b9"),x,y,w,List.of("0.25×","0.5×","1×","2×","4×"),nearest(z,s.zoom),i->s.zoom=z[i]);}
    case 4->{float[] a={1,.9f,.75f,.5f,.25f};choice(Client.text("ui.minimap_opacity_7f05dd50"),x,y,w,List.of("0%","10%","25%","50%","75%"),nearest(a,s.opacity),i->s.opacity=a[i]);}
    case 5->toggle(Client.text("ui.coordinates_bd036452"),x,y,w,s.coordinates,()->s.coordinates=!s.coordinates);
    case 6->toggle(Client.text("ui.biome_885e508c"),x,y,w,s.biome,()->s.biome=!s.biome);
    case 7->toggle(Client.text("ui.world_time_db7dca2d"),x,y,w,s.time,()->s.time=!s.time);
    case 8->toggle(Client.text("ui.weather_622351a2"),x,y,w,s.weather,()->s.weather=!s.weather);
   }
   else if(category==2)switch(n){
    case 0->{float[] v={.5f,.75f,1,1.25f,1.5f,2};choice(Client.text("ui.waypoint_size_56095874"),x,y,w,List.of("50%","75%","100%","125%","150%","200%"),nearest(v,r.markerScale),i->r.markerScale=v[i]);}
    case 1->{float[] v={0,.25f,.5f,1,2,3};choice(Client.text("ui.minimum_zoom_for_waypoints_26d52a24"),x,y,w,List.of(Client.text("ui.always_deb9e5d9"),"0.25×","0.5×","1×","2×","3×"),nearest(v,r.markerMinZoom),i->r.markerMinZoom=v[i]);}
    case 2->addRenderableWidget(UiActions.button(Client.tr("ui.import_waypoints_c183ee7f"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new MapImportScreen(this))).bounds(x,y,w,24).build());
   }
   else if(category==3)switch(n){
    case 0->toggle(Client.text("ui.destination_pointer_9b183da9"),x,y,w,h.directionEnabled,()->h.directionEnabled=!h.directionEnabled);
    case 1->{float[] a={1,.85f,.65f,.5f,.25f,0};choice(Client.text("ui.pointer_opacity_60f089a2"),x,y,w,List.of("0%","15%","35%","50%","75%","100%"),nearest(a,h.directionOpacity),i->h.directionOpacity=a[i]);}
    case 2->toggle(Client.text("ui.pointer_coordinates_377c71ff"),x,y,w,h.directionCoordinates,()->h.directionCoordinates=!h.directionCoordinates);
   }
   else switch(n){
    case 0->addRenderableWidget(UiActions.button(Client.tr("map.layers"),UiActions.Tone.NORMAL,MapGlyphs.icon("layers"),button->minecraft.setScreen(new MapLayersScreen(this))).bounds(x,y,w,24).build());
    case 1->toggle(Client.text("ui.hostile_mobs_74caa520"),x,y,w,r.hostile,()->r.hostile=!r.hostile);
    case 2->toggle(Client.text("ui.friendly_mobs_47122ccf"),x,y,w,r.friendly,()->r.friendly=!r.friendly);
    case 3->toggle(Client.text("ui.icons_instead_of_dots_c5868a43"),x,y,w,r.radarIcons,()->r.radarIcons=!r.radarIcons);
    case 4->toggle(Client.text("ui.radar_names_894661a1"),x,y,w,r.radarNames,()->r.radarNames=!r.radarNames);
    case 5->{int[] heights={4,8,16,32,64,128};choice(Client.text("ui.height_difference_c9c13ea5"),x,y,w,List.of("4","8","16","32","64","128"),nearest(new float[]{4,8,16,32,64,128},r.radarHeight),i->r.radarHeight=heights[i]);}
   }
  }

 }
 private void apply(MapCaves.View value){error="";MapCaves.set(dimension,value);rebuildWidgets();}
 private void configure(int field,int value){var o=MapCaves.options();MapCaves.configure(new MapCaves.Options(field==0?value:o.worldAuto(),field==1?value:o.miniAuto(),field==2?value:o.worldDelay(),field==3?value:o.miniDelay(),field==4?value:o.zoom(),field==5?value==1:o.showTop(),field==6?value:o.defaultType()));rebuildWidgets();}
 private net.minecraft.client.gui.components.Button choice(int x,int y,int w,String key,List<String> values,int selected,java.util.function.IntConsumer action){return addRenderableWidget(UiActions.button(Client.tr(key).copy().append(": "+values.get(selected)+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.tr(key).getString(),values,action::accept,b).current(selected))).bounds(x,y,w,24).build());}
 private List<String> labels(String... keys){return java.util.Arrays.stream(keys).map(k->Client.tr(k).getString()).toList();}
 private void height(String text){draft=text;error="";var v=MapCaves.view(dimension);try{boolean auto=text.isBlank()||text.equalsIgnoreCase("auto");int y=auto?v.top():Integer.parseInt(text);int min=-64,max=319;if(minecraft.level!=null&&minecraft.level.dimension().location().toString().equals(dimension)){min=minecraft.level.getMinBuildHeight();max=minecraft.level.getMaxBuildHeight()-1;}if(!auto&&(y<min||y>max))throw new IllegalArgumentException();MapCaves.set(dimension,new MapCaves.View(v.mode()==0?0:auto?1:2,v.full(),y,v.depth(),v.legible()));}catch(IllegalArgumentException ex){error=Client.tr("map.caveInvalid").getString();}}
 private void caveRows(NativeLayout.Box b,int contentWidth){var v=MapCaves.view(dimension);var o=MapCaves.options();
  var types=labels("map.caveOff","map.caveLayered","map.caveFull");var roofs=List.of(Client.tr("map.caveOff").getString(),"1×1","3×3","5×5");
  var delays=IntStream.rangeClosed(0,100).mapToObj(i->String.format(java.util.Locale.ROOT,"%.1f s",i/10.0)).toList();
  for(int n=firstRow;n<Math.min(11,firstRow+visibleRows);n++){int x=b.x(),y=b.y()+(n-firstRow)*28,w=contentWidth;switch(n){
   case 0->choice(x,y,w,"map.caveType",types,v.mode()==0?0:v.full()?2:1,i->apply(new MapCaves.View(i==0?0:draft.isBlank()||draft.equalsIgnoreCase("auto")?1:2,i==2,v.top(),v.depth(),v.legible())));
   case 1->{var row=NativeLayout.row(new NativeLayout.Box(x,y,w,24),8,NativeLayout.Track.flex(1),NativeLayout.Track.flex(1));var a=row.getFirst();addRenderableWidget(UiActions.button(Client.tr("map.caveTop"),UiActions.Tone.NORMAL,"",ignored->{}).bounds(a.x(),a.y(),a.width(),24).build()).active=false;var c=row.getLast();var field=addRenderableWidget(UiFields.text(font,c.x(),c.y(),c.width(),24,Client.tr("map.caveTop").getString(),"Auto",6,draft,this::height));field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Client.tr("map.caveTopHint")));field.active=!v.full()&&v.mode()!=0;}
   case 2->{var all=new java.util.ArrayList<String>();all.add(Client.tr("map.caveFollowMini").getString());all.addAll(roofs);choice(x,y,w,"map.caveAuto",all,o.worldAuto()+1,i->configure(0,i-1));}
   case 3->choice(x,y,w,"map.caveDepth",IntStream.rangeClosed(1,64).mapToObj(Integer::toString).toList(),v.depth()-1,i->apply(new MapCaves.View(v.mode(),v.full(),v.top(),i+1,v.legible()))).active=!v.full();
   case 4->addRenderableWidget(new UiToggle(Client.tr("map.caveLegible").getString(),x,y,w,v.legible(),()->apply(new MapCaves.View(v.mode(),v.full(),v.top(),v.depth(),!v.legible()))));
   case 5->choice(x,y,w,"map.caveDelay",delays,o.worldDelay()/100,i->configure(2,i*100));
   case 6->choice(x,y,w,"map.caveMiniAuto",roofs,o.miniAuto(),i->configure(1,i));
   case 7->choice(x,y,w,"map.caveMiniDelay",delays,o.miniDelay()/100,i->configure(3,i*100));
   case 8->choice(x,y,w,"map.caveZoom",List.of("1×","2×","3×","4×"),o.zoom()-1,i->configure(4,i+1));
   case 9->addRenderableWidget(new UiToggle(Client.tr("map.caveShowTop").getString(),x,y,w,o.showTop(),()->configure(5,o.showTop()?0:1)));
   case 10->choice(x,y,w,"map.caveDefaultType",types,o.defaultType(),i->configure(6,i));
  }}
  if(!WorldMapClient.cavesAllowed())for(var child:children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget&&widget.getX()>=b.x()&&widget.getY()>=b.y()&&widget.getY()<b.bottom())widget.active=false;
 }
 private static int nearest(float[] values,float value){int n=0;for(int i=1;i<values.length;i++)if(Math.abs(values[i]-value)<Math.abs(values[n]-value))n=i;return n;}
 private void reset(){var r=MapRenderSettings.INSTANCE;var s=MapSettings.INSTANCE;var h=MapSettings.INSTANCE.hud;switch(category){case 0->{r.blockColors=0;r.biomeBlend=r.flowers=r.redstone=r.stainedGlass=r.transparency=r.shortBlocks=true;r.biomesVanilla=false;r.lighting=r.depth=r.hoverBiome=true;r.slopes=2;r.samplingChanged();}case 1->{s.enabled=s.rotate=s.coordinates=true;s.round=false;s.biome=s.time=s.weather=false;s.zoom=1;s.opacity=1;}case 2->{r.markerScale=1;r.markerMinZoom=0;}case 3->{h.directionEnabled=h.directionCoordinates=true;h.directionOpacity=.85f;}case 5->{MapCaves.configure(new MapCaves.Options(-1,2,1000,1000,2,true,1));MapCaves.set(dimension,new MapCaves.View(1,false,64,30,false));refreshCaveDraft();}case 4->{r.hostile=r.friendly=r.radarIcons=true;r.items=r.other=r.radarNames=false;r.radarHeight=16;}}change(()->{});}
 private static final List<List<String>> IDS=List.of(List.of("blockColors","lighting","terrainDepth","slopes","biomeBlend","biomesVanilla","flowers","redstone","stainedGlass","transparency","shortBlocks","hoverBiome"),List.of("enabled","round","rotate","zoom","opacity","coordinates","biome","time","weather"),List.of("markerScale","markerMinZoom","markerImport"),List.of(),List.of("layers","radarHostile","radarFriendly","radarIcons","radarNames","radarHeight"),List.of("caves","caveTop","caveAuto","caveDepth","caveLegible","caveDelay","caveMiniAuto","caveMiniDelay","caveZoom","caveShowTop","caveDefaultType"));
 int settingRow(String id){if(this instanceof NavigationSettingsScreen)return SettingsCatalog.row(5,id);for(var ids:IDS)if(ids.contains(id))return ids.indexOf(id);return -1;}
 public void revealSetting(String id){if(id.equals("positionPrivacy")){int index=categories().indexOf(Client.text("ui.visibility_bd3fe393"));if(index>=0){selectCategory(index);revealRow(0);rebuildWidgets();}return;}for(int i=0;i<IDS.size();i++)if(IDS.get(i).contains(id)){selectCategory(i);revealRow(IDS.get(i).indexOf(id));rebuildWidgets();return;}}

 public void receiveCommunity(com.google.gson.JsonObject j){privacy.receive(j);}
 @Override public void tick(){if(category>=categories().size()){selectCategory(0);rebuildWidgets();return;}if(category>=6){privacy.tick();}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);if(category>=6&&panelArea!=null){privacy.render(g,panelArea,firstRow,visibleRows);}UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(category==5){String status=!WorldMapClient.cavesAllowed()?Client.tr("map.caveForbidden").getString():!error.isEmpty()?error:MapCaves.label(dimension);Ui.text(g,font,UiKit.fit(font,status,dialog.body().width()),dialog.body().x(),dialog.footer().y(),UiKit.muted(),false);}});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
