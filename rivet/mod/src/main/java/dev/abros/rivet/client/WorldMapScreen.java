package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import dev.abros.rivet.core.CommunityLocation;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The map owns the whole viewport. Controls are floating layers, never canvas columns. */
final class WorldMapScreen extends Screen {
    private final Screen parent;
    final MapViewport view=new MapViewport();
    private String dimension="",query="";
    private MapMarkerPanel markerPanel;
    private List<MapMarker> markerSnapshot=List.of();
    private List<MapCategory> categorySnapshot=List.of();
    private boolean centered,markerDrawer,dragged,canvasPressed;
    private int markerFilter;
    private String categoryFilter="*",appliedQuery="";
    private UiEditBox searchField;
    private int markerScroll,lastBlockX,lastBlockZ;
    private double downX,downY,markerOffsetX,markerOffsetZ;
    private MapMarker pressedMarker,dragOrigin;
    private boolean ruler;private double[] rulerStart,rulerEnd;
    private long lastClick;private boolean navigationActive;
    private MapTerritories.Draft territoryDraft;private boolean groupsAvailable;
    private int markerSize(){return Math.round(24*MapRenderSettings.INSTANCE.markerScale);}
    WorldMapScreen(Screen parent){super(Client.tr("map.title"));this.parent=parent;view.zoomAt(3,0,0,0,0);}
    NativeLayout.Box canvas(){return new NativeLayout.Box(0,0,width,height);}
    private int toolPitch(){return height<196?24:28;}
    private int toolCount(){return (navigationActive?9:8)-(MapGroupClient.available()?0:1);}
    private int toolRows(){return Math.clamp((height-56)/toolPitch(),1,toolCount());}
    private NativeLayout.Box toolbar(){return new NativeLayout.Box(8,8,32+28*((toolCount()-1)/toolRows()),8+toolPitch()*toolRows());}
    double measuredDistance(){return rulerStart==null||rulerEnd==null?Double.NaN:Math.hypot(rulerEnd[0]-rulerStart[0],rulerEnd[1]-rulerStart[1]);}
    boolean measuring(){return ruler;}
    private NativeLayout.Box drawerBox(){return new NativeLayout.Box(toolbar().right()+8,8,Math.min(240,Math.max(120,width-toolbar().right()-100)),height-16);}
    NativeLayout.Box markerBox(){int top=Math.min(40,Math.max(8,height-260));return new NativeLayout.Box(width-Math.clamp(width/3,216,248)-8,top,Math.clamp(width/3,216,248),Math.min(420,height-top-8));}
    private NativeLayout.Box titleBox(){int left=markerDrawer?drawerBox().right()+8:toolbar().right()+8,right=markerPanel!=null&&markerBox().y()<40?markerBox().x()-8:width-44,w=Math.min(180,Math.max(24,right-left));return new NativeLayout.Box(Math.max(left,(left+right-w)/2),8,w,24);}
    void revealIcon(String icon){if(markerPanel!=null)markerPanel.reveal(this,markerBox(),icon);}
    boolean markerOpen(){return markerPanel!=null;}
    void focusLocation(dev.abros.rivet.core.CommunityLocation place){dimension=place.dimension();rulerStart=rulerEnd=null;view.center(place.x()+.5,place.z()+.5);centered=true;rebuildWidgets();}
    String selectedDimension(){return dimension;}
    void refreshPanels(){rebuildWidgets();}
    void closeMarker(){cancelMarkerDrag();markerPanel=null;rebuildWidgets();}
    void openMarker(MapMarker marker,boolean create){if(marker.death()){markerPanel=null;rebuildWidgets();deathActions(marker,(int)view.screenX(marker.x()+.5,midX()),(int)view.screenZ(marker.z()+.5,midZ()));return;}markerPanel=new MapMarkerPanel(marker,create);if(markerDrawer&&drawerBox().right()+8>markerBox().x())markerDrawer=false;rebuildWidgets();ensureMarkerVisible(marker);}
    MapMarker draftMarker(){return markerPanel==null?null:markerPanel.draft();}
    List<MapMarker> visibleMarkers(){
        var visibility=WorldMapClient.markerVisibility(false);
        var rows=new ArrayList<>(WorldMapClient.markers().stream().filter(m->m.dimension().equals(dimension)&&visibility.test(m)&&view.zoom()>=MapRenderSettings.INSTANCE.markerMinZoom&&(!m.death()||MapSettings.INSTANCE.deathMap)&&(markerPanel==null||!m.id().equals(markerPanel.marker.id()))).toList());
        var draft=draftMarker();if(draft!=null&&draft.dimension().equals(dimension))rows.add(draft);
        return rows;
    }
    private void ensureMarkerVisible(MapMarker marker){
        double edge=markerDrawer?drawerBox().right():toolbar().right(),gap=markerBox().x()-edge;
        // Compact windows can leave less room than the preferred marker padding.
        double padding=Math.min(markerSize()/2d+6,Math.max(0,gap/2-1));
        double left=edge+padding,right=markerBox().x()-padding,top=50,bottom=height-46;
        if(gap<=0||bottom<top)return;
        double x=view.screenX(marker.x()+.5,midX()),y=view.screenZ(marker.z()+.5,midZ());
        if(x<left||x>right||y<top||y>bottom)view.pan((left+right)/2-x,(top+bottom)/2-y);
    }
    private void cancelMarkerDrag(){
        if(dragOrigin!=null&&markerPanel!=null&&dragOrigin.id().equals(markerPanel.marker.id()))markerPanel.move(dragOrigin.x(),dragOrigin.y(),dragOrigin.z());
        pressedMarker=null;dragOrigin=null;canvasPressed=false;dragged=false;
    }
    private List<MapMarker> markerRows(){return WorldMapClient.markers().stream().filter(m->(markerFilter==0||markerFilter==1&&!m.death()||markerFilter==2&&m.death())&&(categoryFilter.equals("*")||categoryFilter.equals(m.category())||categoryFilter.isEmpty()&&WorldMapClient.categories().stream().noneMatch(c->c.id().toString().equals(m.category())))&&(m.name()+" "+WorldMapClient.categoryName(m.category())).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(markerFilter==2?Comparator.comparingLong(MapMarker::deathAt).reversed():(a,b)->0).toList();}
    private static boolean contains(NativeLayout.Box b,double x,double y){return x>=b.x()&&x<b.right()&&y>=b.y()&&y<b.bottom();}
    private boolean inside(double x,double y){return x>=0&&y>=0&&x<width&&y<height&&!contains(toolbar(),x,y)&&!(x>=8&&x<40&&y>=height-40)&&!(markerDrawer&&contains(drawerBox(),x,y))&&!(markerPanel!=null&&contains(markerBox(),x,y))&&!(titleVisible()&&contains(titleBox(),x,y))&&!(x>=width-36&&y<=36)&&y<height-28;}
    private boolean titleVisible(){return true;}
    private double midX(){return width/2d;}
    private double midZ(){return height/2d;}
    private void center(){if(minecraft.player!=null){String current=minecraft.level.dimension().location().toString();if(!current.equals(dimension)){territoryDraft=null;rulerStart=null;rulerEnd=null;}dimension=current;view.center(minecraft.player.getX(),minecraft.player.getZ());centered=true;}}
    private void zoom(double steps){view.zoomAt(steps,midX(),midZ(),midX(),midZ());}
    private void tool(int row,String label,String icon,Runnable action){var b=addRenderableWidget(UiActions.tool(Client.tr(label),MapGlyphs.icon(icon),12+28*(row/toolRows()),12+toolPitch()*(row%toolRows()),action));b.setWidth(24);b.setHeight(toolPitch()-4);if(label.equals("map.stop"))b.active=DirectionCue.hasTarget();if(label.equals("map.add"))b.active=WorldMapClient.markersReady();if(label.equals("map.ruler")&&ruler)UiActions.iconColor(b,UiKit.accent());}
    @Override protected void init(){
        if(!centered)center();
        navigationActive=DirectionCue.hasTarget();int row=0;
        if(navigationActive)tool(row++,"map.stop","stop",()->{DirectionCue.clear();rebuildWidgets();});
        tool(row++,"map.center",UiIcons.TARGET,this::center);
        tool(row++,"map.add",UiIcons.PLUS,()->create((int)Math.floor(view.x()),(int)Math.floor(view.z())));
        tool(row++,"map.markers","markers",()->{markerDrawer=!markerDrawer;if(markerDrawer&&markerPanel!=null&&drawerBox().right()+8>markerBox().x())markerPanel=null;rebuildWidgets();});
        tool(row++,"map.zoomIn","zoom_in",()->zoom(1));
        tool(row++,"map.zoomOut","zoom_out",()->zoom(-1));
        tool(row++,"map.layers","layers",()->minecraft.setScreen(new MapLayersScreen(this)));
        if(MapGroupClient.available())tool(row++,"map.territories","territory",()->{if(territoryDraft==null)minecraft.setScreen(new MapTerritoriesScreen(this));});
        tool(row,"map.ruler","ruler",()->{ruler=!ruler;rulerStart=rulerEnd=null;rebuildWidgets();});
        var settings=addRenderableWidget(UiActions.tool(Client.tr("map.settings"),MapGlyphs.icon("settings"),12,height-36,()->minecraft.setScreen(new MapSettingsScreen(this))));settings.setWidth(24);settings.setHeight(24);
        var top=titleBox();addRenderableWidget(UiActions.button(dev.abros.rivet.network.DimensionLabels.name(dimension).copy().append(" ▾"),UiActions.Tone.NORMAL,MapGlyphs.icon("dimension"),this::dimensions).bounds(top.x(),top.y(),top.width(),top.height()).build());
        if(markerPanel==null||markerBox().y()>=40){var close=addRenderableWidget(UiActions.tool(Client.tr("close"),MapGlyphs.icon("clear"),width-32,8,this::onClose));close.setWidth(24);close.setHeight(24);}
        if(markerDrawer)drawer();
        if(markerPanel!=null)markerPanel.build(this,markerBox(),this::addRenderableWidget);
    }
    void focusTerritory(MapTerritory t){dimension=t.dimension();view.center(t.points().stream().mapToInt(MapTerritory.Point::x).average().orElse(0),t.points().stream().mapToInt(MapTerritory.Point::z).average().orElse(0));centered=true;}
    void drawTerritory(MapTerritories.Draft draft){var mc=net.minecraft.client.Minecraft.getInstance();if(!centered&&mc.player!=null){view.center(mc.player.getX(),mc.player.getZ());centered=true;}territoryDraft=draft;dimension=draft.dimension;markerPanel=null;markerDrawer=false;ruler=false;cancelMarkerDrag();if(mc.screen==this)rebuildWidgets();else mc.setScreen(this);}
    void cancelTerritory(){territoryDraft=null;rebuildWidgets();}
    private void finishTerritory(){if(territoryDraft!=null&&territoryDraft.points.size()>=3)minecraft.setScreen(new MapTerritoryScreen(this,territoryDraft));}
    private void dimensions(net.minecraft.client.gui.components.Button button){var list=minecraft.getConnection().levels().stream().map(d->d.location().toString()).sorted().toList();minecraft.setScreen(new ChoicePopup(this,Client.tr("map.dimension").getString(),list.stream().map(d->dev.abros.rivet.network.DimensionLabels.name(d).getString()).toList(),i->{territoryDraft=null;dimension=list.get(i);rulerStart=null;rulerEnd=null;markerPanel=null;rebuildWidgets();},button).current(list.indexOf(dimension)));}
    private void drawer(){
        var b=drawerBox();int x=b.x()+8,w=b.width()-16,top=b.y()+32;
        searchField=addRenderableWidget(UiFields.text(font,x,top,w-24,20,Client.tr("map.search").getString(),Client.tr("map.search").getString(),80,query,v->query=v));
        addRenderableWidget(UiActions.tool(Client.tr("map.markerFilter"),MapGlyphs.icon("filter"),x+w-20,top,button->minecraft.setScreen(new ChoicePopup(this,Client.tr("map.markerFilter").getString(),List.of(Client.tr("map.allMarkers").getString(),Client.tr("map.personalMarkers").getString(),Client.tr("map.deaths").getString()),i->{markerFilter=i;markerScroll=0;minecraft.setScreen(this);},button).current(markerFilter))));
        addRenderableWidget(UiActions.button(Component.literal((categoryFilter.equals("*")?Client.tr("map.allCategories").getString():WorldMapClient.categoryName(categoryFilter))+" ▾"),UiActions.Tone.NORMAL,"",button->{var ids=new ArrayList<String>();var labels=new ArrayList<String>();ids.add("*");labels.add(Client.tr("map.allCategories").getString());ids.add("");labels.add(Client.tr("map.uncategorized").getString());for(var c:WorldMapClient.categories()){ids.add(c.id().toString());labels.add(c.name());}minecraft.setScreen(new ChoicePopup(this,Client.tr("map.category").getString(),labels,i->{categoryFilter=ids.get(i);markerScroll=0;minecraft.setScreen(this);},button).current(ids.indexOf(categoryFilter)));}).bounds(x,top+28,w-24,20).build());
        addRenderableWidget(UiActions.tool(Client.tr("map.categories"),MapGlyphs.icon("settings"),x+w-20,top+28,()->minecraft.setScreen(new MapCategoriesScreen(this))));
        var rows=markerRows();int count=Math.max(0,(b.height()-96)/24);markerScroll=Math.clamp(markerScroll,0,Math.max(0,rows.size()-count));
        for(int n=markerScroll;n<Math.min(rows.size(),markerScroll+count);n++){var marker=rows.get(n);addRenderableWidget(UiActions.button(Component.literal(marker.name()),UiActions.Tone.NORMAL,MapGlyphs.icon(marker.icon()),ignored->{if(!dimension.equals(marker.dimension())){rulerStart=null;rulerEnd=null;}dimension=marker.dimension();view.center(marker.x(),marker.z());openMarker(marker,false);}).bounds(x,top+56+(n-markerScroll)*24,w,20).build());}
    }
    private String biomeAt(int x,int z){var tile=WorldMapClient.exploredTile(new WorldMapClient.TileKey(MapCaves.layer(dimension),Math.floorDiv(x,16),Math.floorDiv(z,16)));if(tile==null)return "";String biome=tile.biome(Math.floorMod(x,16),Math.floorMod(z,16));return biome.isEmpty()?"":Component.translatable("biome."+biome.replace(':','.').replace('/','.')).getString();}
    private boolean knownHeight(int x,int z){var tile=WorldMapClient.tile(MapCaves.layer(dimension),Math.floorDiv(x,16),Math.floorDiv(z,16));return tile!=null&&tile.color(Math.floorMod(x,16),Math.floorMod(z,16))!=0;}
    MapMarker point(int x,int z){var tile=WorldMapClient.tile(MapCaves.layer(dimension),Math.floorDiv(x,16),Math.floorDiv(z,16));int y=tile!=null&&tile.color(Math.floorMod(x,16),Math.floorMod(z,16))!=0?tile.height(Math.floorMod(x,16),Math.floorMod(z,16))+1:minecraft.player.getBlockY();return new MapMarker(UUID.randomUUID(),dimension,Client.tr("map.newMarker").getString(),x,Math.clamp(y,-2048,2048),z,MapMarkerPanel.randomColour(),"none");}
    private void create(int x,int z){if(!WorldMapClient.markersReady()||Math.abs((long)x)>30000000||Math.abs((long)z)>30000000)return;openMarker(point(x,z),true);}
    private double distance(MapMarker m,double x,double y){return Math.hypot(view.screenX(m.x()+.5,midX())-x,view.screenZ(m.z()+.5,midZ())-y);}
    private MapMarker hit(double x,double y){var draft=draftMarker();if(draft!=null&&draft.dimension().equals(dimension)&&distance(draft,x,y)<=markerSize()/2+3)return draft;return visibleMarkers().stream().filter(m->distance(m,x,y)<=markerSize()/2+3).min(Comparator.comparingDouble(m->distance(m,x,y))).orElse(null);}
    boolean canTeleport(String targetDimension){var root=minecraft.getConnection().getCommands().getRoot();return (root.getChild("teleport")!=null||root.getChild("tp")!=null)&&(targetDimension.equals(minecraft.level.dimension().location().toString())||(root.getChild("execute")!=null&&root.getChild("execute").getChild("in")!=null));}
    void teleport(MapMarker m){if(!canTeleport(m.dimension()))return;String cmd=dev.abros.rivet.core.map.MapTeleport.command(m,minecraft.level.dimension().location().toString(),minecraft.getConnection().getCommands().getRoot().getChild("tp")!=null);minecraft.player.connection.sendCommand(cmd);onClose();}
    void navigate(MapMarker m){DirectionCue.start(m);WorldMapClient.flush();minecraft.setScreen(null);}
    void delete(MapMarker m){minecraft.setScreen(new UiConfirmDialog(yes->{if(yes){try{WorldMapClient.delete(m.id());markerPanel=null;minecraft.setScreen(this);}catch(Exception ex){openMarker(m,false);minecraft.setScreen(this);}}else minecraft.setScreen(this);},Client.tr("map.deleteConfirm"),Component.literal(m.name())).dangerous().compact());}
    private void deathActions(MapMarker marker,int x,int y){
        var rows=new ArrayList<UiContextPopup.Item>();
        rows.add(new UiContextPopup.Item(Client.tr("map.navigate"),()->navigate(marker),false));
        if(DirectionCue.hasTarget())rows.add(new UiContextPopup.Item(Client.tr("map.stop"),DirectionCue::clear,false));
        if(canTeleport(marker.dimension()))rows.add(new UiContextPopup.Item(Client.tr("map.teleportMarker"),()->teleport(marker),false));
        rows.add(new UiContextPopup.Item(Client.tr("map.copy"),()->minecraft.keyboardHandler.setClipboard(marker.x()+" "+marker.y()+" "+marker.z()),false));
        rows.add(new UiContextPopup.Item(Client.tr("map.delete"),()->delete(marker),true));
        minecraft.setScreen(new UiContextPopup(this,Math.clamp(x,8,width-8),Math.clamp(y,8,height-8),rows).validWhile(()->WorldMapClient.markers().stream().anyMatch(m->m.id().equals(marker.id()))));
    }
    private void context(double x,double y){
        canvasPressed=false;dragged=false;lastClick=0;
        var marker=hit(x,y);int bx=(int)Math.floor(view.worldX(x,midX())),bz=(int)Math.floor(view.worldZ(y,midZ()));if(Math.abs((long)bx)>30000000||Math.abs((long)bz)>30000000)return;
        var place=marker==null?point(bx,bz):marker;var rows=new ArrayList<UiContextPopup.Item>();
        if(WorldMapClient.markersReady())rows.add(new UiContextPopup.Item(Client.tr("map.add"),()->create(bx,bz),false));
        if(marker!=null&&!marker.death())rows.add(new UiContextPopup.Item(Client.tr("map.edit"),()->openMarker(marker,false),false));
        if(MapLayers.visible(MapLayers.Layer.TERRITORIES,false))for(var t:MapGroupClient.territories())if(t.dimension().equals(dimension)&&t.contains(view.worldX(x,midX()),view.worldZ(y,midZ())))rows.add(new UiContextPopup.Item(Client.tr("map.territory").copy().append(": "+t.name()),()->MapGroupClient.open(this,t.id().toString()),false));
        rows.add(new UiContextPopup.Item(Client.tr("map.navigate"),()->navigate(place),false));
        if(DirectionCue.hasTarget())rows.add(new UiContextPopup.Item(Client.tr("map.stop"),DirectionCue::clear,false));
        if(canTeleport(place.dimension())&&(marker!=null||!minecraft.gameMode.canHurtPlayer()||knownHeight(bx,bz)))rows.add(new UiContextPopup.Item(Client.tr(marker==null?"map.teleportHere":"map.teleportMarker"),()->teleport(place),false));
        rows.add(new UiContextPopup.Item(Client.tr("map.copy"),()->minecraft.keyboardHandler.setClipboard(place.x()+" "+place.y()+" "+place.z()),false));
        if(marker!=null)rows.add(new UiContextPopup.Item(Client.tr("map.delete"),()->delete(marker),true));
        rows.add(new UiContextPopup.Item(Client.tr("map.export"),()->minecraft.setScreen(new MapExportScreen(this)),false));
        minecraft.setScreen(new UiContextPopup(this,(int)x,(int)y,rows));
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;if(!inside(x,y))return false;
        if(territoryDraft==null&&!ruler&&MapSharedOverlay.clickWorld(this,dimension,view,x,y,button))return true;
        if(button==1&&territoryDraft!=null){if(!territoryDraft.points.isEmpty())territoryDraft.points.removeLast();return true;}
        if(button==1){cancelMarkerDrag();if(ruler){rulerStart=null;rulerEnd=null;}else context(x,y);return true;}
        if(button==0){downX=x;downY=y;dragged=false;canvasPressed=true;pressedMarker=ruler||territoryDraft!=null?null:hit(x,y);dragOrigin=null;return true;}
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(button==0&&canvasPressed){
            if(Math.hypot(x-downX,y-downY)>3)dragged=true;
            if(dragged&&pressedMarker!=null&&!pressedMarker.death()){
                if(dragOrigin==null){
                    if(markerPanel==null||!markerPanel.marker.id().equals(pressedMarker.id()))openMarker(pressedMarker,false);
                    dragOrigin=markerPanel.draft();markerOffsetX=dragOrigin.x()+.5-view.worldX(downX,midX());markerOffsetZ=dragOrigin.z()+.5-view.worldZ(downY,midZ());
                }
                if(inside(x,y)){
                    int bx=(int)Math.floor(view.worldX(x,midX())+markerOffsetX),bz=(int)Math.floor(view.worldZ(y,midZ())+markerOffsetZ);
                    if(Math.abs((long)bx)<=30000000&&Math.abs((long)bz)<=30000000){
                        var tile=WorldMapClient.tile(MapCaves.layer(dimension),Math.floorDiv(bx,16),Math.floorDiv(bz,16));
                        int by=tile!=null&&tile.color(Math.floorMod(bx,16),Math.floorMod(bz,16))!=0?Math.clamp(tile.height(Math.floorMod(bx,16),Math.floorMod(bz,16))+1,-2048,2048):dragOrigin.y();
                        markerPanel.move(bx,by,bz);
                    }
                }
            }else if(dragged)view.pan(dx,dy);
            return true;
        }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        boolean pressed=canvasPressed;
        if(button==0&&pressed){
            if(dragOrigin!=null){if(!inside(x,y))cancelMarkerDrag();else{pressedMarker=null;dragOrigin=null;canvasPressed=false;}lastClick=0;return true;}
            canvasPressed=false;pressedMarker=null;
            if(inside(x,y)&&!dragged){
                if(territoryDraft!=null){if(territoryDraft.points.size()>=3){var first=territoryDraft.points.getFirst();if(Math.hypot(x-view.screenX(first.x(),midX()),y-view.screenZ(first.z(),midZ()))<=6){finishTerritory();return true;}}if(territoryDraft.points.size()<128){int bx=(int)Math.round(view.worldX(x,midX())),bz=(int)Math.round(view.worldZ(y,midZ()));try{var point=new MapTerritory.Point(bx,bz);if(!territoryDraft.points.contains(point))territoryDraft.points.add(point);}catch(IllegalArgumentException ignored){}}return true;}
                if(ruler){double[] p={view.worldX(x,midX()),view.worldZ(y,midZ())};if(rulerStart==null||rulerEnd!=null){rulerStart=p;rulerEnd=null;}else rulerEnd=p;return true;}
                var marker=hit(x,y);if(marker!=null){if(markerPanel==null||!markerPanel.marker.id().equals(marker.id()))openMarker(marker,false);return true;}
                int bx=(int)Math.floor(view.worldX(x,midX())),bz=(int)Math.floor(view.worldZ(y,midZ()));long now=System.currentTimeMillis();
                if(now-lastClick<300&&Math.abs(bx-lastBlockX)+Math.abs(bz-lastBlockZ)<4){create(bx,bz);lastClick=0;}else{lastClick=now;lastBlockX=bx;lastBlockZ=bz;}return true;
            }
            lastClick=0;return true;
        }
        return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(dragOrigin!=null)return true;if(markerPanel!=null&&contains(markerBox(),x,y))return markerPanel.scroll(this,markerBox(),vertical,y);if(markerDrawer&&contains(drawerBox(),x,y)){markerScroll+=vertical<0?1:-1;rebuildWidgets();return true;}if(!inside(x,y))return super.mouseScrolled(x,y,horizontal,vertical);view.zoomAt(Math.clamp(vertical,-4,4),x,y,midX(),midZ());return true;}
    private void terrain(GuiGraphics g){
        g.enableScissor(0,0,width,height);
        long terrainTiming=dev.abros.rivet.core.PerformanceMetrics.start();
        int detail=MapDetail.level(view.zoom(),minecraft.getWindow().getGuiScale()),chunks=4<<detail;
        var regions=new LinkedHashSet<MapRegionTextures.Key>();
        for(var key:WorldMapClient.visibleTiles(MapCaves.layer(dimension),view,width,height))regions.add(new MapRegionTextures.Key(key.layer(),Math.floorDiv(key.x(),chunks),Math.floorDiv(key.z(),chunks),detail));
        for(var key:regions){
            var texture=MapRegionTextures.texture(key,detail);
            float left=(float)view.screenX(key.x()*key.blocks(),midX()),top=(float)view.screenZ(key.z()*key.blocks(),midZ()),right=(float)view.screenX((key.x()+1)*key.blocks(),midX()),bottom=(float)view.screenZ((key.z()+1)*key.blocks(),midZ());
            if(texture==null||!texture.complete())MapRegionTextures.fallback(g,key,left,top,right,bottom);
            if(texture!=null)MapRegionTextures.draw(g,texture,left,top,right,bottom);
        }
        dev.abros.rivet.core.PerformanceMetrics.end("client.worldmap.terrain",terrainTiming);
        MapTerritories.world(g,dimension,view,width,height,territoryDraft);
        MapRadar.world(g,dimension,view,width,height);
        long prepareTiming=dev.abros.rivet.core.PerformanceMetrics.start();
        var markers=visibleMarkers();var occupied=new dev.abros.rivet.core.BoxIntersectionIndex(new NativeLayout.Box(-64,-64,width+128,height+128));
        for(var m:markers)occupied.add(new NativeLayout.Box((int)view.screenX(m.x()+.5,midX())-markerSize()/2-1,(int)view.screenZ(m.z()+.5,midZ())-markerSize()/2-1,markerSize()+2,markerSize()+2));
        var deathLabels=markers.stream().filter(MapMarker::death).map(m->{int x=(int)view.screenX(m.x()+.5,midX()),y=(int)view.screenZ(m.z()+.5,midZ()),w=font.width(dev.abros.rivet.core.map.MapDeathLifecycle.countdown(m,System.currentTimeMillis()))+10;return new NativeLayout.Box(x-w/2,y+markerSize()/2+2,w,14);}).toList();
        dev.abros.rivet.core.PerformanceMetrics.end("client.worldmap.prepare",prepareTiming);
        long markersTiming=dev.abros.rivet.core.PerformanceMetrics.start();
        MapSharedOverlay.world(g,dimension,view,width,height,occupied,deathLabels);
        var draft=draftMarker();for(var m:markers)if(draft==null||!m.id().equals(draft.id()))drawMarker(g,m,occupied,deathLabels);
        dev.abros.rivet.core.PerformanceMetrics.end("client.worldmap.markers",markersTiming);
        var target=DirectionCue.target();if(MapLayers.visible(MapLayers.Layer.ROUTE,false)&&MapSettings.INSTANCE.hud.directionMap&&target!=null&&target.dimension().equals(dimension))markerGlyph(g,"navigate",(int)view.screenX(target.x()+.5,midX()),(int)view.screenZ(target.z()+.5,midZ()),UiKit.accent());
        if(minecraft.player!=null&&minecraft.level.dimension().location().toString().equals(dimension)){int px=(int)view.screenX(minecraft.player.getX(),midX()),py=(int)view.screenZ(minecraft.player.getZ(),midZ());MapPlayerArrow.draw(g,px,py,minecraft.player.getYRot());}
        if(draft!=null&&draft.dimension().equals(dimension))drawMarker(g,draft,occupied,deathLabels);
        drawRuler(g,minecraft.mouseHandler.xpos()*width/minecraft.getWindow().getScreenWidth(),minecraft.mouseHandler.ypos()*height/minecraft.getWindow().getScreenHeight());
        g.disableScissor();
    }
    private void drawRuler(GuiGraphics g,double mouseX,double mouseY){
        if(!ruler||rulerStart==null)return;
        double[] end=rulerEnd==null?new double[]{view.worldX(mouseX,midX()),view.worldZ(mouseY,midZ())}:rulerEnd;
        double ax=view.screenX(rulerStart[0],midX()),ay=view.screenZ(rulerStart[1],midZ()),bx=view.screenX(end[0],midX()),by=view.screenZ(end[1],midZ());
        // Clip before stepping pixels, so off-screen world points cannot make an unbounded loop.
        double dx=bx-ax,dy=by-ay,t0=0,t1=1;double[] ps={-dx,dx,-dy,dy},qs={ax,width-ax,ay,height-ay};
        for(int i=0;i<4;i++){if(ps[i]==0){if(qs[i]<0)return;}else{double t=qs[i]/ps[i];if(ps[i]<0)t0=Math.max(t0,t);else t1=Math.min(t1,t);}}if(t0>t1)return;
        int x=(int)(ax+t0*dx),y=(int)(ay+t0*dy),ex=(int)(ax+t1*dx),ey=(int)(ay+t1*dy),sx=x<ex?1:-1,sy=y<ey?1:-1,adx=Math.abs(ex-x),ady=-Math.abs(ey-y),error=adx+ady;
        while(true){g.fill(x,y,x+2,y+2,UiKit.accent());if(x==ex&&y==ey)break;int twice=2*error;if(twice>=ady){error+=ady;x+=sx;}if(twice<=adx){error+=adx;y+=sy;}}
        for(double[] p:new double[][]{rulerStart,end}){int px=(int)view.screenX(p[0],midX()),py=(int)view.screenZ(p[1],midZ());g.fill(px-3,py-3,px+4,py+4,0xff10151a);g.renderOutline(px-3,py-3,7,7,UiKit.accent());}
        String label=String.format(Locale.ROOT,"%.1f",Math.hypot(end[0]-rulerStart[0],end[1]-rulerStart[1]))+" "+Client.tr("map.blocks").getString();
        double angle=Math.atan2(dy,dx);if(angle>Math.PI/2)angle-=Math.PI;else if(angle< -Math.PI/2)angle+=Math.PI;
        double mid=(t0+t1)/2;g.pose().pushPose();g.pose().translate(ax+mid*dx,ay+mid*dy,0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)angle));
        int lw=font.width(label);UiKit.material(g,-lw/2-5,-18,lw+10,14);g.drawString(font,label,-lw/2,-15,UiKit.text(),false);g.pose().popPose();

    }
    private void markerGlyph(GuiGraphics g,String icon,int x,int y,int color){int size=markerSize(),half=size/2;g.fill(x-half,y-half,x-half+size,y-half+size,0xee10151a);g.renderOutline(x-half,y-half,size,size,color|0xff000000);if(MapGlyphs.MARKERS.contains(icon))MapGlyphs.marker(g,icon,x-half+2,y-half+2,Math.max(1,size-4));else{g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(size/24f,size/24f,1);UiIcons.draw(g,MapGlyphs.icon(icon),-6,-6,color);g.pose().popPose();}}

    private void drawMarker(GuiGraphics g,MapMarker m,dev.abros.rivet.core.BoxIntersectionIndex occupied,List<NativeLayout.Box> deathLabels){
        int x=(int)view.screenX(m.x()+.5,midX()),y=(int)view.screenZ(m.z()+.5,midZ());if(x<-32||y<-32||x>width+32||y>height+32)return;
        String labelName=m.death()?dev.abros.rivet.core.map.MapDeathLifecycle.countdown(m,System.currentTimeMillis()):markerPanel!=null&&m.id().equals(markerPanel.marker.id())?markerPanel.draftName():m.name();markerGlyph(g,m.icon(),x,y,m.color());if(m.icon().equals("none"))MapGlyphs.initial(g,labelName,x,y,markerSize()-4,m.color());if(labelName.isBlank())return;String name=UiKit.fit(font,labelName,140);int w=font.width(name)+10;var label=new NativeLayout.Box(x-w/2,y+markerSize()/2+2,w,14);
        if(label.x()<0||label.right()>width||label.bottom()>height)return;
        if(!m.death()&&deathLabels.stream().anyMatch(b->b.x()<label.right()&&b.right()>label.x()&&b.y()<label.bottom()&&b.bottom()>label.y()))return;
        if(!labelBlocked(occupied,label)){g.fill(label.x(),label.y(),label.right(),label.bottom(),0xee10151a);g.drawString(font,name,label.x()+5,label.y()+3,0xffffffff,false);occupied.add(label);}
    }
    private static boolean labelBlocked(dev.abros.rivet.core.BoxIntersectionIndex occupied,NativeLayout.Box label){long began=dev.abros.rivet.core.PerformanceMetrics.start();try{return occupied.intersects(label);}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.worldmap.collision",began);}}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1,1,1,1);UiKit.surface(g,0,0,width,height,UiKit.surface(UiKit.Surface.CANVAS));g.flush();terrain(g);var b=toolbar();UiKit.material(g,b.x(),b.y(),b.width(),b.height());g.renderOutline(b.x(),b.y(),b.width(),b.height(),UiKit.border());if(markerDrawer){b=drawerBox();UiKit.material(g,b.x(),b.y(),b.width(),b.height());g.renderOutline(b.x(),b.y(),b.width(),b.height(),UiKit.border());}if(markerPanel!=null)markerPanel.render(g,markerBox()); }
    @Override public void render(GuiGraphics g,int x,int y,float delta){long timing=dev.abros.rivet.core.PerformanceMetrics.start();try{renderContents(g,x,y,delta);}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.worldmap.draw",timing);}}
    private void renderContents(GuiGraphics g,int x,int y,float delta){
        super.render(g,x,y,delta);if(markerPanel!=null)markerPanel.labels(this,g,markerBox());
        if(markerDrawer)Ui.text(g,font,Client.tr("map.markers").getString(),drawerBox().x()+8,drawerBox().y()+10,UiKit.text(),false);
        String status=WorldMapClient.error().isEmpty()?(int)Math.floor(view.worldX(x,midX()))+", "+(int)Math.floor(view.worldZ(y,midZ()))+" · "+Client.tr("map.scale").getString()+" "+String.format(Locale.ROOT,"%.2f",view.zoom()):WorldMapClient.error();if(ruler){double[] end=rulerEnd==null?new double[]{view.worldX(x,midX()),view.worldZ(y,midZ())}:rulerEnd;status=rulerStart==null?Client.tr("map.rulerStart").getString():String.format(Locale.ROOT,"%.1f",Math.hypot(end[0]-rulerStart[0],end[1]-rulerStart[1]))+" "+Client.tr("map.blocks").getString()+" · "+Client.tr(rulerEnd==null?"map.rulerEnd":"map.rulerAgain").getString();}if(MapRenderSettings.INSTANCE.hoverBiome&&inside(x,y)){String biome=biomeAt((int)Math.floor(view.worldX(x,midX())),(int)Math.floor(view.worldZ(y,midZ())));if(!biome.isEmpty())status+=" · "+biome;}if(territoryDraft!=null)status=Client.tr("map.territoryHint").getString()+" · "+territoryDraft.points.size()+"/128";if(MapCaves.options().showTop()&&MapCaves.layer(dimension).cave())status=MapCaves.label(dimension)+" · "+status;int footerX=markerDrawer?drawerBox().right()+8:toolbar().right()+8,footerRight=markerPanel!=null?markerBox().x()-8:width-8;int available=footerRight-footerX-16;if(available>=40){status=UiKit.fit(font,status,available);int sw=font.width(status)+16;UiKit.material(g,footerX,height-28,sw,20);Ui.text(g,font,status,footerX+8,height-22,UiKit.muted(),false);}
        if(inside(x,y)){var marker=hit(x,y);if(marker!=null){g.flush();g.renderComponentTooltip(font,marker.death()?List.of(Client.tr("map.death"),Component.literal(CommunityScreen.local(marker.deathAt())),Component.literal(marker.x()+", "+marker.y()+", "+marker.z()+" · "+dev.abros.rivet.network.DimensionLabels.name(marker.dimension()).getString())):List.of(Component.literal(marker.name())),x,y);g.flush();}}
    }
    @Override public void tick(){if(groupsAvailable!=MapGroupClient.available()){groupsAvailable=MapGroupClient.available();if(!groupsAvailable)territoryDraft=null;rebuildWidgets();}super.tick();if(navigationActive!=DirectionCue.hasTarget())rebuildWidgets();if(categorySnapshot!=WorldMapClient.categories()){categorySnapshot=WorldMapClient.categories();if(!categoryFilter.equals("*")&&!categoryFilter.isEmpty()&&categorySnapshot.stream().noneMatch(c->c.id().toString().equals(categoryFilter)))categoryFilter="";if(markerDrawer)rebuildWidgets();}if(markerDrawer&&!query.equals(appliedQuery)){appliedQuery=query;boolean focus=searchField!=null&&searchField.isFocused();int cursor=searchField==null?0:searchField.getCursorPosition();markerScroll=0;rebuildWidgets();if(focus){setFocused(searchField);searchField.setCursorPosition(cursor);}}if(markerSnapshot!=WorldMapClient.markers()){markerSnapshot=WorldMapClient.markers();if(markerDrawer)rebuildWidgets();}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(territoryDraft!=null){if(key==256){cancelTerritory();return true;}if(key==257||key==335)return true;if(key==259){if(!territoryDraft.points.isEmpty())territoryDraft.points.removeLast();return true;}}if(key==256&&dragOrigin!=null){cancelMarkerDrag();return true;}if(key==256&&ruler){if(rulerStart!=null){rulerStart=null;rulerEnd=null;}else{ruler=false;rebuildWidgets();}return true;}if(key==256&&markerPanel!=null){closeMarker();return true;}if(key==256&&markerDrawer){markerDrawer=false;rebuildWidgets();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){WorldMapClient.flush();minecraft.setScreen(parent);}
    static void releaseTextures(){MapRegionTextures.reset();}
}
