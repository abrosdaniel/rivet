package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import dev.abros.rivet.core.map.MapMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;

/** Fixed form and actions; only the icon grid scrolls. Drafts require explicit save. */
final class MapMarkerPanel {
 final MapMarker marker;final boolean create;
 private String name,icon,error="",category;private boolean mapVisible,minimapVisible;private final String[] coordinates=new String[3];private final UiEditBox[] coordinateFields=new UiEditBox[3];private int colour,firstRow,x,y,z;
 static final int[] COLOURS={0xffe2be75,0xff83c6c4,0xffb9a3f2,0xffef7777,0xff87d59c,0xff7bb5f2,0xffffffff,0xfff5aa72,0xffdc83b5,0xff606876,0xff947253,0xffecda71};
 static int randomColour(){return COLOURS[java.util.concurrent.ThreadLocalRandom.current().nextInt(COLOURS.length)];}
 static final String[] COLOUR_NAMES={"gold","cyan","purple","red","green","blue","white","orange","pink","gray","brown","yellow"};
 MapMarkerPanel(MapMarker marker,boolean create){if(marker.death())throw new IllegalArgumentException(Client.text("message.death_not_editable"));this.marker=marker;this.create=create;name=create?"":marker.name();colour=marker.color();icon=marker.icon();x=marker.x();y=marker.y();z=marker.z();category=marker.category();mapVisible=marker.mapVisible();minimapVisible=marker.minimapVisible();coordinates[0]=""+x;coordinates[1]=""+y;coordinates[2]=""+z;}
 MapMarker draft(){return new MapMarker(marker.id(),marker.dimension(),name.isBlank()?marker.name():name.strip(),x,y,z,colour,icon,marker.deathAt(),category,mapVisible,minimapVisible);}
 String draftName(){return name.strip();}
 void move(int x,int y,int z){this.x=x;this.y=y;this.z=z;coordinates[0]=""+x;coordinates[1]=""+y;coordinates[2]=""+z;for(int i=0;i<3;i++)if(coordinateFields[i]!=null)coordinateFields[i].setValue(coordinates[i]);error="";}
 private boolean compact(NativeLayout.Box b){return b.height()<340;}
 private int columns(NativeLayout.Box b){return Math.max(1,(b.width()-24)/24);}
 private int colourColumns(NativeLayout.Box b){return Math.max(1,(b.width()-24)/20);}
 private int colourRows(NativeLayout.Box b){return (COLOURS.length+colourColumns(b)-1)/colourColumns(b);}
 private int totalRows(NativeLayout.Box b){return (MapGlyphs.CHOICES.size()+columns(b)-1)/columns(b);}
 private int inputY(NativeLayout.Box b){return b.y()+(compact(b)?28:44);}
 private int colourY(NativeLayout.Box b){return inputY(b)+(compact(b)?48:120);}
 private int iconLabelY(NativeLayout.Box b){return colourY(b)+colourRows(b)*20+(compact(b)?4:8);}
 private int footerHeight(WorldMapScreen host,NativeLayout.Box b){return (create?28:host.canTeleport(marker.dimension())?72:48)-(compact(b)?4:0);}
 private int footerY(WorldMapScreen host,NativeLayout.Box b){return b.bottom()-footerHeight(host,b);}
 private NativeLayout.Box grid(WorldMapScreen host,NativeLayout.Box b){int top=iconLabelY(b)+12;return new NativeLayout.Box(b.x()+8,top,b.width()-16,Math.max(0,footerY(host,b)-top));}
 private int visible(WorldMapScreen host,NativeLayout.Box b){return grid(host,b).height()/24;}
 void build(WorldMapScreen host,NativeLayout.Box b,Consumer<AbstractWidget> add){var mc=Minecraft.getInstance();int x=b.x()+8,w=b.width()-16;
  add.accept(UiActions.tool(Client.tr("close"),MapGlyphs.icon("clear"),b.right()-28,b.y()+8,host::closeMarker));
  var nameField=UiFields.text(mc.font,x,inputY(b),w,20,Client.tr("map.name").getString(),Client.tr("map.name").getString(),80,name,v->name=v);UiFields.issue(nameField,error);add.accept(nameField);
  if(compact(b))add.accept(UiActions.button(Client.tr("map.details"),UiActions.Tone.NORMAL,MapGlyphs.icon("edit"),button->mc.setScreen(new MapMarkerDetailsScreen(host,this))).bounds(x,inputY(b)+24,w,20).build());
  else extras(host,x,inputY(b)+24,w,add,host::refreshPanels);
  int cols=colourColumns(b);for(int i=0;i<COLOURS.length;i++){int value=COLOURS[i];var swatch=new UiColorSwatch(x+(i%cols)*20,colourY(b)+(i/cols)*20,value,Client.tr("map.color."+COLOUR_NAMES[i]),value==colour,()->{colour=value;host.refreshPanels();});add.accept(swatch);}
  int visible=visible(host,b);firstRow=Math.clamp(firstRow,0,Math.max(0,totalRows(b)-visible));
  for(int row=firstRow;row<Math.min(totalRows(b),firstRow+visible);row++){int y=grid(host,b).y()+(row-firstRow)*24;cols=columns(b);int start=row*cols;
   for(int i=start;i<Math.min(MapGlyphs.CHOICES.size(),start+cols);i++){String value=MapGlyphs.CHOICES.get(i);add.accept(UiActions.tool(Client.tr("map.icon."+value),MapGlyphs.icon(value),x+(i-start)*24,y,()->{icon=value;host.refreshPanels();}));}
  }
  int footer=footerY(host,b),half=(w-6)/2;
  add.accept(UiActions.button(Client.tr("map.save"),UiActions.Tone.PRIMARY,MapGlyphs.icon("save"),ignored->save(host)).bounds(x,footer,half,20).build());
  add.accept(UiActions.button(Client.tr("cancel"),UiActions.Tone.NORMAL,"",ignored->{if(create)host.closeMarker();else host.openMarker(marker,false);}).bounds(x+half+6,footer,w-half-6,20).build());
  if(!create){
   add.accept(UiActions.button(Client.tr("map.navigate"),UiActions.Tone.NORMAL,MapGlyphs.icon("navigate"),ignored->{if(validCoordinates())host.navigate(draft());else{error=Client.tr("map.invalidCoordinates").getString();host.refreshPanels();}}).bounds(x,footer+24,half,20).build());
   add.accept(UiActions.button(Client.tr("map.delete"),UiActions.Tone.DANGER,MapGlyphs.icon("delete"),ignored->host.delete(marker)).bounds(x+half+6,footer+24,w-half-6,20).build());
   if(host.canTeleport(marker.dimension()))add.accept(UiActions.button(Client.tr("map.teleportMarker"),UiActions.Tone.NORMAL,MapGlyphs.icon("teleport"),ignored->{if(validCoordinates())host.teleport(draft());else{error=Client.tr("map.invalidCoordinates").getString();host.refreshPanels();}}).bounds(x,footer+48,w,20).build());
  }
 }
 void extras(WorldMapScreen host,int x,int top,int w,Consumer<AbstractWidget> add,Runnable refresh){var mc=Minecraft.getInstance();
  var fields=NativeLayout.row(new NativeLayout.Box(x,top,w,20),4,NativeLayout.Track.flex(1),NativeLayout.Track.flex(1),NativeLayout.Track.flex(1));String[] axis={"X","Y","Z"};for(int i=0;i<3;i++){int index=i;var box=fields.get(i);var field=UiFields.text(mc.font,box.x(),box.y(),box.width(),box.height(),axis[i],axis[i],9,coordinates[i],v->{coordinates[index]=v;try{int value=Integer.parseInt(v);if(index==1?(value < -2048||value>2048):Math.abs((long)value)>30000000)throw new NumberFormatException();if(index==0)this.x=value;else if(index==1)this.y=value;else this.z=value;UiFields.issue(coordinateFields[index],"");}catch(NumberFormatException ex){UiFields.issue(coordinateFields[index],Client.tr("map.invalidCoordinates").getString());}});coordinateFields[i]=field;field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(axis[i])));try{int value=Integer.parseInt(coordinates[i]);if(i==1?(value < -2048||value>2048):Math.abs((long)value)>30000000)throw new NumberFormatException();}catch(NumberFormatException ex){UiFields.issue(field,Client.tr("map.invalidCoordinates").getString());}add.accept(field);}
  var categoryButton=UiActions.button(net.minecraft.network.chat.Component.literal(WorldMapClient.categoryName(category)+" ▾"),UiActions.Tone.NORMAL,"",button->{var groups=WorldMapClient.categories();var labels=new java.util.ArrayList<String>();labels.add(Client.tr("map.uncategorized").getString());groups.forEach(c->labels.add(c.name()));mc.setScreen(new ChoicePopup(mc.screen,Client.tr("map.category").getString(),labels,i->{category=i==0?"":groups.get(i-1).id().toString();refresh.run();},button));}).bounds(x,top+24,w-28,20).build();add.accept(categoryButton);
  add.accept(UiActions.tool(Client.tr("map.categories"),MapGlyphs.icon("settings"),x+w-20,top+24,()->mc.setScreen(new MapCategoriesScreen(mc.screen))));
  int halfVisible=(w-6)/2;add.accept(new UiChoiceRow(x,top+48,halfVisible,Client.tr("map.visibleMapShort").getString(),mapVisible,-1,UiKit.accent(),()->{mapVisible=!mapVisible;refresh.run();}));add.accept(new UiChoiceRow(x+halfVisible+6,top+48,w-halfVisible-6,Client.tr("map.visibleMinimapShort").getString(),minimapVisible,-1,UiKit.accent(),()->{minimapVisible=!minimapVisible;refresh.run();}));
 }
 private boolean validCoordinates(){try{for(int i=0;i<3;i++){int value=Integer.parseInt(coordinates[i]);if(i==1?(value < -2048||value>2048):Math.abs((long)value)>30000000)return false;}return true;}catch(NumberFormatException ex){return false;}}
 private void save(WorldMapScreen host){try{for(int i=0;i<3;i++){int value=Integer.parseInt(coordinates[i]);if(i==1?(value < -2048||value>2048):Math.abs((long)value)>30000000)throw new IllegalArgumentException();}var saved=new MapMarker(marker.id(),marker.dimension(),name.strip(),x,y,z,colour,icon,marker.deathAt(),category,mapVisible,minimapVisible);WorldMapClient.put(saved);host.openMarker(saved,false);}catch(Exception ex){error=Client.tr("map.saveError").getString();host.refreshPanels();}}
 boolean scroll(WorldMapScreen host,NativeLayout.Box b,double delta,double y){var area=grid(host,b);if(y<area.y()||y>=area.bottom())return true;firstRow+=delta<0?1:-1;host.refreshPanels();return true;}
 void reveal(WorldMapScreen host,NativeLayout.Box b,String value){int i=MapGlyphs.CHOICES.indexOf(value);if(i<0)return;firstRow=Math.max(0,i/columns(b)-visible(host,b)+1);host.refreshPanels();}
 void render(GuiGraphics g,NativeLayout.Box b){var mc=Minecraft.getInstance();int x=b.x()+8;UiKit.material(g,b.x(),b.y(),b.width(),b.height());g.renderOutline(b.x(),b.y(),b.width(),b.height(),UiKit.border());if(icon.equals("none"))MapGlyphs.initial(g,name,x+10,b.y()+16,12,colour);else MapGlyphs.marker(g,icon,x+4,b.y()+10,12);Ui.text(g,mc.font,UiKit.fit(mc.font,create?Client.tr("map.add").getString():name,b.width()-68),x+26,b.y()+14,UiKit.text(),false);}
 void labels(WorldMapScreen host,GuiGraphics g,NativeLayout.Box b){var mc=Minecraft.getInstance();int x=b.x()+8,w=b.width()-24;
  if(!compact(b)){Ui.text(g,mc.font,Client.tr("map.name").getString(),x,b.y()+28,UiKit.text(),false);Ui.text(g,mc.font,Client.tr("map.color").getString(),x,colourY(b)-12,UiKit.text(),false);}
  Ui.text(g,mc.font,Client.tr("map.icons").getString(),x,iconLabelY(b),UiKit.text(),false);
  int i=MapGlyphs.CHOICES.indexOf(icon),row=i/columns(b);if(i>=0&&row>=firstRow&&row<firstRow+visible(host,b))UiKit.focus(g,x+(i%columns(b))*24,grid(host,b).y()+(row-firstRow)*24,20,20);
  var grid=grid(host,b);UiScrollbar.draw(g,new NativeLayout.Box(b.right()-8,grid.y(),4,visible(host,b)*24),visible(host,b),totalRows(b),firstRow);
 }
}
