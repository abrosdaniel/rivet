package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;
/** A single position editor uses actual widget/hint renderers and resolution-independent anchors. */
final class HudInteractionScreen extends Screen {
 private final boolean editing,showTab;private final Screen parent;private String dragging="";private double grabX,grabY;private final Map<String,NativeLayout.Box> targets=new LinkedHashMap<>();
 HudInteractionScreen(boolean editing,Screen parent){this(editing,parent,false);}
 HudInteractionScreen(boolean editing,Screen parent,boolean showTab){super(Component.literal(editing?"Редактор HUD":"Режим курсора"));this.editing=editing;this.parent=parent;this.showTab=showTab;}
 boolean tabVisible(){return showTab;}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){if(minecraft.level==null)g.fill(0,0,width,height,UiKit.surface());}
 @Override public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g,x,y,delta);if(showTab)RivetTab.draw(g,x,y);if(!editing){DirectionCue.draw(g);DirectionCue.tooltip(g,x,y);}HudRenderer.render(g,editing||RivetHud.widgetVisible(),editing);if(editing){ChatHints.draw(g,"",true);DirectionCue.draw(g,true);targets.clear();targets.put("widget",new NativeLayout.Box(HudRenderer.lastX,HudRenderer.lastY,HudRenderer.lastW,HudRenderer.lastH));targets.put("chat",ChatHints.bounds);targets.put("direction",DirectionCue.bounds);int guide=UiTheme.mix(UiKit.surface(),UiKit.accent(),.35f);if(!dragging.isEmpty()){g.fill(width/2,4,width/2+1,height-32,guide);g.fill(4,height/2,width-4,height/2+1,guide);}for(var entry:targets.entrySet()){var b=entry.getValue();g.renderOutline(b.x(),b.y(),b.width(),b.height(),entry.getKey().equals(dragging)?UiKit.accent():UiPalette.outline());String label=switch(entry.getKey()){case "chat"->"Подсказки чата";case "direction"->"Указатель маршрута";default->"Виджет";};int ly=Math.max(4,b.y()-12);g.fill(b.x(),ly,b.x()+Math.min(b.width(),font.width(label)+8),ly+11,UiKit.surface());Ui.text(g,font,UiKit.fit(font,label,b.width()-8),b.x()+4,ly+1,UiKit.accent(),false);}}
  if(!editing&&x>=HudRenderer.lastX&&x<HudRenderer.lastX+HudRenderer.lastW&&y>=HudRenderer.lastY&&y<HudRenderer.lastY+HudRenderer.lastH){String profile=HudProfile.fullText();if(!profile.isBlank())g.renderComponentTooltip(font,Arrays.stream(profile.split("\n")).map(Component::literal).map(v->(Component)v).toList(),x,y);}
  String help=editing?"Предпросмотр · перетащите блок · R — сброс размещения · Esc — готово":"Режим курсора · нажмите на блок или плашку · Esc — закрыть";int hintWidth=Math.min(width-16,font.width(help)+16);g.fill((width-hintWidth)/2,height-25,(width+hintWidth)/2,height-7,UiKit.surface());Ui.text(g,font,UiKit.fit(font,help,hintWidth-12),(width-hintWidth)/2+6,height-20,UiKit.text(),false);}
 @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return super.mouseClicked(x,y,button);if(editing){var entries=new ArrayList<>(targets.entrySet());Collections.reverse(entries);for(var entry:entries){var b=entry.getValue();if(x>=b.x()&&x<b.right()&&y>=b.y()&&y<b.bottom()){dragging=entry.getKey();grabX=x-b.x();grabY=y-b.y();return true;}}}else if(DirectionCue.click(x,y)||HudRenderer.click(x,y,true))return true;if(showTab)for(var hit:RivetTab.hits)if(x>=hit.x()&&x<hit.x()+hit.w()&&y>=hit.y()&&y<hit.y()+hit.h()){var player=minecraft.getConnection()==null?null:minecraft.getConnection().getPlayerInfo(hit.player());if(player!=null)FeatureListScreen.searchPlayer(this,player.getProfile().getName());return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button!=0||dragging.isEmpty())return false;var b=targets.get(dragging);HudPlacement.move(dragging,(int)Math.max(4,Math.min(width-b.width()-4,x-grabX)),(int)Math.max(4,Math.min(height-b.height()-4,y-grabY)),b.width(),b.height(),width,height);return true;}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&!dragging.isEmpty()){dragging="";HudSettings.INSTANCE.save();return true;}return super.mouseReleased(x,y,button);}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(showTab){RivetTab.scroll(dy);return true;}return super.mouseScrolled(x,y,dx,dy);}
 @Override public boolean keyPressed(int key,int scan,int mods){if(!editing&&RivetHud.INTERACT.matches(key,scan)){onClose();return true;}if(editing&&key==GLFW.GLFW_KEY_R){dragging="";HudPlacement.reset();return true;}return super.keyPressed(key,scan,mods);}
 @Override public void tick(){if(!editing&&(minecraft.player==null||!ServerMenuClient.available()))minecraft.setScreen(null);}
 @Override public void removed(){dragging="";RivetHud.INTERACT.setDown(false);}
 @Override public void onClose(){dragging="";HudSettings.INSTANCE.save();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
