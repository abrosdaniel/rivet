package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.BooleanSupplier;

/** A held-key wheel. A selection remains pending until the server acknowledges it. */
final class QuickSkinsScreen extends Screen {
 private final Screen parent;
 private final BooleanSupplier held;
 private final List<String> ids=new ArrayList<>();
 private final List<UiRadialSector> cards=new ArrayList<>();
 private Object connection; private boolean loaded,released,keyboard;
 private String expected; private int selected=-1,radius; private long opened;
 QuickSkinsScreen(Screen parent){this(parent,null);}
 QuickSkinsScreen(Screen parent,BooleanSupplier held){super(Client.tr("ui.select_a_skin_8e54dd75"));this.parent=parent;this.held=held;}
 private String active(){return SkinClient.library.has("profile")?Json.opt(SkinClient.library.getAsJsonObject("profile"),"active",""):"";}
 void updated(){if(expected!=null&&!SkinClient.busy&&expected.equals(active())){onClose();return;}rebuildWidgets();}
 @Override protected void init(){
  if(!loaded){loaded=true;connection=minecraft.getConnection();opened=net.minecraft.Util.getMillis();if(!SkinClient.library.has("entries")&&!SkinClient.busy&&ServerMenuClient.previewTransport==null)SkinClient.command("list","",false);
   if(held!=null)GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),minecraft.getWindow().getScreenWidth()/2d,minecraft.getWindow().getScreenHeight()/2d);
  }
  ids.clear();cards.clear();selected=-1;
  radius=Math.max(44,Math.min(140,Math.min((width-24)/2,(height-128)/2)));
  int cw=Math.max(20,Math.min(36,(int)(radius*.3)));
  var entries=SkinWheelFavorites.entries();ids.addAll(entries.keySet());
  for(int i=0;i<ids.size();i++){int index=i;String id=ids.get(i);double angle=2*Math.PI*i/ids.size()-Math.PI/2;
   var card=addRenderableWidget(new UiRadialSector(width/2+(int)Math.round(Math.cos(angle)*radius*.74)-cw/2,height/2+(int)Math.round(Math.sin(angle)*radius*.74)-cw/2,cw,entries.get(id),id.equals(active()),()->choose(index)));
   card.active=!SkinClient.busy;cards.add(card);
  }
  addRenderableWidget(UiActions.button(Client.tr("ui.skin_library_3ae5dd7c"),UiActions.Tone.NORMAL,"",b->{released=true;SkinsScreen.open(parent);}).bounds(width/2-75,height-30,150,20).build()).active=!SkinClient.busy;
  if(SkinClient.retryable)addRenderableWidget(UiActions.button(Client.tr("ui.retry_request_fcb97cf7"),UiActions.Tone.NORMAL,"",b->SkinClient.retry()).bounds(width/2-75,height-54,150,20).build()).active=!SkinClient.busy;
 }
 static int sector(double dx,double dy,int count,double outer){if(count==0||Math.hypot(dx,dy)<outer*.48)return -1;double angle=Math.atan2(dy,dx)+Math.PI/2+Math.PI/count;return Math.floorMod((int)Math.floor(angle/(Math.PI*2)*count),count);}
 private void point(double x,double y){selected=sector(x-width/2d,y-height/2d,ids.size(),radius);}
 private void choose(int index){if(SkinClient.busy||index<0||index>=ids.size())return;released=true;String id=ids.get(index);if(id.equals(active())){onClose();return;}expected=id;SkinClient.command("select",id,false);rebuildWidgets();}
 @Override public void mouseMoved(double x,double y){keyboard=false;point(x,y);super.mouseMoved(x,y);}
 @Override public boolean mouseClicked(double x,double y,int button){if(y>=height-58)return super.mouseClicked(x,y,button);if(button==0){point(x,y);choose(selected);return true;}return super.mouseClicked(x,y,button);}
 @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}if(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_RIGHT||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){keyboard=true;if(!ids.isEmpty())selected=Math.floorMod(selected+(key==GLFW.GLFW_KEY_LEFT||key==GLFW.GLFW_KEY_UP?-1:1),ids.size());if(selected>=0)setFocused(cards.get(selected));return true;}if((key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)&&selected>=0&&(getFocused()==null||cards.contains(getFocused()))){choose(selected);return true;}return super.keyPressed(key,scan,modifiers);}
 @Override public void tick(){
  if(ServerMenuClient.previewTransport==null&&(!SkinClient.available()||connection!=minecraft.getConnection())){onClose();return;}
  if(held!=null&&!released){if(!minecraft.isWindowActive()){onClose();return;}if(!held.getAsBoolean()){released=true;if(SkinClient.busy||selected<0)onClose();else choose(selected);}}
 }
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){g.fill(0,0,width,height,AccessibilityScreen.background(0x80000000));}
 @Override public void render(GuiGraphics g,int mx,int my,float d){
  if(!keyboard)point(mx,my);
  float t=AccessibilityScreen.animations()?Math.min(1,(net.minecraft.Util.getMillis()-opened)/(float)AccessibilityScreen.motionMillis()):1;
  float scale=1-.035f*(1-t)*(1-t);
  g.pose().pushPose();g.pose().translate(width/2f,height/2f,0);g.pose().scale(scale,scale,1);g.pose().translate(-width/2f,-height/2f,0);
  UiRadialSector.draw(g,width/2,height/2,radius,ids.size(),SkinClient.busy?-1:selected);
  super.render(g,mx,my,d);
  String name=selected>=0&&selected<cards.size()?cards.get(selected).getMessage().getString():Client.text("skins.wheel.title");
  Ui.centered(g,font,UiKit.fit(font,name,(int)(radius*.86)),width/2,height/2-14,UiKit.text());
  Ui.centered(g,font,Client.text(SkinClient.busy?"ui.applying_190c3756":"skins.wheel.cancel"),width/2,height/2+4,UiKit.muted());
  g.pose().popPose();
  String hint=SkinClient.status.isEmpty()?Client.text(ids.isEmpty()?"skins.wheel.empty":held!=null&&!released?"skins.wheel.hold":"skins.wheel.click"):SkinClient.status;
  Ui.centered(g,font,UiKit.fit(font,hint,width-24),width/2,14,UiKit.muted());
 }
 @Override public void onClose(){released=true;minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
