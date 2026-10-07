package dev.abros.rivet.client;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
/** Bounded live samples use the same colours, profile and preferences as gameplay. */
final class UiSettingsPreview {
 static int space(NativeLayout.Box body){return body.height()>=180?72:0;}
 static void social(GuiGraphics g,NativeLayout.Box body,int tab){if(space(body)==0)return;var mc=Minecraft.getInstance();int x=body.x(),y=body.bottom()-64,w=body.width()-8;var s=SocialSettings.INSTANCE;if(tab==0)w=Math.min(w,s.columnWidth);if(w<40)return;
  g.enableScissor(x,y,x+w,y+60);for(int yy=y;yy<y+60;yy+=10)for(int xx=x;xx<x+w;xx+=10)g.fill(xx,yy,Math.min(x+w,xx+10),Math.min(y+60,yy+10),((xx-x)/10+(yy-y)/10)%2==0?0xFF394651:0xFF485661);UiKit.surface(g,x,y,w,60,(UiKit.surface()&0xFFFFFF)|((int)Math.round(255*(tab==0?s.tabOpacity:mc.options.textBackgroundOpacity().get()))<<24));
  Ui.text(g,mc.font,"Предпросмотр · "+(tab==0?"TAB":"Чат"),x+8,y+7,UiKit.accent(),false);
  int tx=x+8;if(s.heads&&mc.player!=null){net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(mc.player.getUUID()),tx,y+24,16);tx+=22;}
  String name=mc.player==null?"Игрок":mc.player.getGameProfile().getName();Ui.text(g,mc.font,UiKit.fit(mc.font,tab==0?name:name+": Привет!",w-(tx-x)-8),tx,y+new int[]{23,25,27}[s.density],UiKit.text(),false);
  if(tab==0)Ui.text(g,mc.font,"Роль · В сети",tx,y+39,UiKit.muted(),false);else Ui.text(g,mc.font,"!общий · #объединение",x+8,y+42,UiKit.muted(),false);g.disableScissor();
 }
 static void widget(GuiGraphics g,NativeLayout.Box body){if(space(body)==0)return;int x=body.x(),y=body.bottom()-64,w=body.width()-8;if(w<40)return;g.enableScissor(x,y,x+w,y+60);g.pose().pushPose();g.pose().translate(x,y,0);int h=26+HudProfile.height(true);float scale=Math.min(HudSettings.INSTANCE.scale*.6f,Math.min(w/214f,60f/h));g.pose().scale(scale,scale,1);UiHudSurface.panel(g,214,h,HudSettings.INSTANCE.opacity);Ui.text(g,Minecraft.getInstance().font,"Предпросмотр виджета",10,6,UiKit.accent(),false);HudProfile.draw(g,0,20,214,true);g.pose().popPose();g.disableScissor();}
 private UiSettingsPreview(){}
}
