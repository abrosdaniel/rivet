package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
/** A single position editor uses actual widget/hint renderers and resolution-independent anchors. */
final class HudInteractionScreen extends Screen {
 private final boolean editing,showTab;private final Screen parent;private String dragging="";private double grabX,grabY;private NativeLayout.Box dragStart,dragPosition;private final Map<String,NativeLayout.Box> targets=new LinkedHashMap<>();
 HudInteractionScreen(boolean editing,Screen parent){this(editing,parent,false);}
 HudInteractionScreen(boolean editing,Screen parent,boolean showTab){super(Component.literal(editing?Client.text("ui.hud_editor_fe822f17"):Component.translatable("key.rivet.hudInteract").getString()));this.editing=editing;this.parent=parent;this.showTab=showTab;}
 private String resizing="";private Runnable undoResize;
 private boolean resizeElement(String item,double delta){
  var h=HudSettings.INSTANCE;var m=MapSettings.INSTANCE;
  float scale=h.scale,hints=h.hintScale,toasts=h.toastScale,direction=m.hud.directionScale;int size=m.size;
  Runnable undo=()->{h.scale=scale;h.hintScale=hints;h.toastScale=toasts;m.hud.directionScale=direction;m.size=size;Minimap.invalidate();};
  if(!HudPlacement.resize(item,delta,false))return false;
  resizing=item;undoResize=undo;return true;
 }
 // Resolve against actual renderer bounds, not a proportional size estimate.
 void finishResize(){
  if(resizing.isEmpty())return;
  var box=targets.get(resizing);var occupied=targets.entrySet().stream().filter(e->!e.getKey().equals(resizing)).map(Map.Entry::getValue).toList();
  var viewport=new NativeLayout.Box(4,4,Math.max(0,width-8),Math.max(0,height-36));
  var placement=box==null?Optional.<NativeLayout.Box>empty():dev.abros.rivet.core.HudDropLayout.place(box,viewport,occupied,4);
  if(placement.isEmpty()||resizing.equals("notices")&&!placement.get().equals(box))undoResize.run();
  else if(!resizing.equals("notices")){var b=placement.get();HudPlacement.move(resizing,b.x(),b.y(),b.width(),b.height(),width,height,false);}
  resizing="";undoResize=null;HudSettings.INSTANCE.save();MapSettings.INSTANCE.save();
 }
 private void cancelResize(){if(undoResize!=null)undoResize.run();undoResize=null;resizing="";}
 boolean layoutEditing(){return editing;}
 boolean tabVisible(){return showTab;}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){if(minecraft.level==null)g.fill(0,0,width,height,UiKit.surface());}
 @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g,x,y,delta);if(showTab)RivetTab.draw(g,x,y);Minimap.draw(g,editing);if(!editing){DirectionCue.draw(g);DirectionCue.tooltip(g,x,y);}HudRenderer.render(g,editing||RivetHud.widgetVisible(),editing);if(editing){ChatHints.draw(g,"",true);DirectionCue.draw(g,true);targets.clear();targets.put("widget",new NativeLayout.Box(HudRenderer.lastX,HudRenderer.lastY,HudRenderer.lastW,HudRenderer.lastH));targets.put("chat",ChatHints.bounds);targets.put("direction",DirectionCue.bounds);targets.put("minimap",Minimap.bounds);targets.put("notices",HudRenderer.noticeBounds);finishResize();int guide=UiTheme.mix(UiKit.surface(),UiKit.accent(),.35f);if(!dragging.isEmpty()){g.fill(width/2,4,width/2+1,height-32,guide);g.fill(4,height/2,width-4,height/2+1,guide);}for(var entry:targets.entrySet()){var b=entry.getValue();g.renderOutline(b.x(),b.y(),b.width(),b.height(),entry.getKey().equals(dragging)?UiKit.accent():UiPalette.outline());String label=switch(entry.getKey()){case "chat"->Client.text("ui.chat_hints_94b24d49");case "direction"->Client.text("ui.navigation_pointer_ca06076a");case "minimap"->Client.text("map.minimap");case "notices"->Client.text("ui.notifications_ee3c35f3");default->Client.text("ui.widget_92a5d7dc");};int ly=Math.max(4,b.y()-12);g.fill(b.x(),ly,b.x()+Math.min(b.width(),font.width(label)+8),ly+11,UiKit.surface());Ui.text(g,font,UiKit.fit(font,label,b.width()-8),b.x()+4,ly+1,UiKit.accent(),false);}}
  if(!editing&&x>=HudRenderer.lastX&&x<HudRenderer.lastX+HudRenderer.lastW&&y>=HudRenderer.lastY&&y<HudRenderer.lastY+HudRenderer.lastH){String profile=HudProfile.fullText();if(!profile.isBlank())g.renderComponentTooltip(font,Arrays.stream(profile.split("\n")).map(Component::literal).map(v->(Component)v).toList(),x,y);}
  String help=editing?Client.text("ui.drag_a_block_wheel_element_size_72230266"):Client.text("ui.cursor_mode_click_a_block_or_f4a878f0");int hintWidth=Math.min(width-16,font.width(help)+16);UiHudSurface.panel(g,(width-hintWidth)/2,height-25,hintWidth,18,.92f);Ui.text(g,font,UiKit.fit(font,help,hintWidth-12),(width-hintWidth)/2+6,height-20,UiKit.text(),false);}
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);if(editing){if(!resizing.isEmpty())return true;var entries=new ArrayList<>(targets.entrySet());Collections.reverse(entries);for(var entry:entries){var b=entry.getValue();if(x>=b.x()&&x<b.right()&&y>=b.y()&&y<b.bottom()){if(entry.getKey().equals("notices"))return true;dragging=entry.getKey();dragStart=dragPosition=b;grabX=x-b.x();grabY=y-b.y();return true;}}}else if(Minimap.click(x,y,this)||DirectionCue.click(x,y)||HudRenderer.click(x,y,true))return true;if(showTab)for(var hit:RivetTab.hits)if(x>=hit.x()&&x<hit.x()+hit.w()&&y>=hit.y()&&y<hit.y()+hit.h()){var player=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(hit.player());if(player!=null)FeatureListScreen.searchPlayer(this,player.getProfile().getName());return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button!=0||dragging.isEmpty())return false;var b=targets.get(dragging);dragPosition=new NativeLayout.Box((int)Math.max(4,Math.min(width-b.width()-4,x-grabX)),(int)Math.max(4,Math.min(height-b.height()-4,y-grabY)),b.width(),b.height());dragPosition=dev.abros.rivet.core.HudDropLayout.snap(dragPosition,new NativeLayout.Box(8,8,Math.max(b.width(),width-16),Math.max(b.height(),height-16)),8);placeDrag(dragPosition);return true;}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){var occupied=targets.entrySet().stream().filter(e->!e.getKey().equals(dragging)).map(Map.Entry::getValue).toList();var viewport=new NativeLayout.Box(4,4,Math.max(0,width-8),Math.max(0,height-36));placeDrag(dev.abros.rivet.core.HudDropLayout.place(dragPosition,viewport,occupied,4).orElse(dragStart));dragging="";HudSettings.INSTANCE.save();MapSettings.INSTANCE.save();return true;}return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(editing){if(!dragging.isEmpty()||!resizing.isEmpty())return true;var entries=new ArrayList<>(targets.entrySet());Collections.reverse(entries);for(var entry:entries){var b=entry.getValue();if(x>=b.x()&&x<b.right()&&y>=b.y()&&y<b.bottom())return resizeElement(entry.getKey(),dy);}}if(!editing&&Minimap.scroll(x,y,dy))return true;if(showTab){RivetTab.scroll(dy);return true;}return super.mouseScrolled(x,y,dx,dy);}
 private void placeDrag(NativeLayout.Box box){HudPlacement.move(dragging,box.x(),box.y(),box.width(),box.height(),width,height,false);}
 @Override public boolean keyPressed(int key,int scan,int mods){if(editing&&key==GLFW.GLFW_KEY_ESCAPE&&!dragging.isEmpty()){placeDrag(dragStart);dragging="";return true;}if(!editing&&Minimap.EXPAND.matches(key,scan)){Minimap.EXPAND.setDown(true);return true;}if(!editing&&RivetHud.INTERACT.matches(key,scan)){onClose();return true;}if(editing&&key==GLFW.GLFW_KEY_R){cancelResize();dragging="";HudPlacement.reset();return true;}return super.keyPressed(key,scan,mods);}
 @Override public boolean keyReleased(int key,int scan,int mods){if(Minimap.EXPAND.matches(key,scan)){Minimap.EXPAND.setDown(false);return true;}return super.keyReleased(key,scan,mods);}
 @Override public void tick(){if(!editing&&(minecraft.player==null||!ServerMenuClient.available()))minecraft.setScreen(null);}
 @Override public void resize(net.minecraft.client.Minecraft client,int width,int height){
  cancelResize();if(!dragging.isEmpty())placeDrag(dragStart);dragging="";targets.clear();super.resize(client,width,height);
 }
 @Override public void removed(){cancelResize();if(!dragging.isEmpty())placeDrag(dragStart);dragging="";RivetHud.INTERACT.setDown(false);Minimap.EXPAND.setDown(false);}
 @Override public void onClose(){cancelResize();if(!dragging.isEmpty())placeDrag(dragStart);dragging="";HudSettings.INSTANCE.save();MapSettings.INSTANCE.save();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
