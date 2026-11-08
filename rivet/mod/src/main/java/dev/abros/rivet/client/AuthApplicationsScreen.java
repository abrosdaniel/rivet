package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AuthApplicationsScreen extends ScrollScreen {
    private final Screen parent;
    private JsonArray sessions=new JsonArray();
    private String next="",status="",requestId="";
    private long sent;
    AuthApplicationsScreen(Screen parent){super(Client.tr("auth.applications.title"));this.parent=parent;}
    static void open(Screen parent){var screen=new AuthApplicationsScreen(parent);Minecraft.getInstance().setScreen(screen);screen.request("");}
    private int top(){return UiDialog.top(height,320);}
    private int bottom(){return height-top();}
    private void request(String cursor){if(sent>0)return;var q=new JsonObject();q.addProperty("action","applications");q.addProperty("cursor",cursor);send(q);}
    @Override protected void init(){int w=Math.min(460,width-40),x=(width-w)/2;
        scrollArea(sessions.size(),new dev.abros.rivet.core.NativeLayout.Box(x,top()+36,w,Math.max(0,bottom()-110-top()-36)),30);
        for(int i=firstRow;i<Math.min(sessions.size(),firstRow+visibleRows);i++){var row=sessions.get(i).getAsJsonObject();String app=Json.str(row,"application"),created=date(row.get("created").getAsLong()),expires=date(row.get("expires").getAsLong());var button=addRenderableWidget(UiActions.button(Component.literal(app+" · "+created+Client.text("ui.revoke_26f0cd4e")),UiActions.Tone.NORMAL,"",b->revoke(Json.str(row,"id"))).bounds(x,top()+36+(i-firstRow)*30,scrollLayout().content().width(),20).build());button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(app+"\n"+Client.text("auth.applications.created")+created+"\n"+Client.text("auth.applications.expires")+expires)));}
        addRenderableWidget(UiActions.button(Client.tr("auth.applications.refresh"),UiActions.Tone.NORMAL,"",b->{firstRow=0;request("");}).bounds(x,bottom()-106,(w-8)/2,20).build());
        var more=addRenderableWidget(UiActions.button(Client.tr("auth.applications.next"),UiActions.Tone.NORMAL,"",b->{firstRow=0;request(next);}).bounds(x+(w+8)/2,bottom()-106,(w-8)/2,20).build());more.active=!next.isEmpty();
        addRenderableWidget(UiActions.button(Client.tr("auth.applications.revokeAll"),UiActions.Tone.NORMAL,"",b->revoke("all")).bounds(x,bottom()-80,w,20).build());
        UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,bottom()-26,w,20),this::addRenderableWidget,this::onClose);
        if(sent>0)for(var child:children())if(child instanceof net.minecraft.client.gui.components.Button button&&UiActions.commandOf(button)!=UiActions.Command.CLOSE)button.active=false;
    }
    private String date(long value){return java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(java.util.Locale.forLanguageTag(minecraft.getLanguageManager().getSelected().replace('_','-'))).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(value));}
    private void send(JsonObject request){requestId=java.util.UUID.randomUUID().toString();request.addProperty("request",requestId);sent=System.currentTimeMillis();status=Client.text("ui.checking_cbf41dbe");rebuildWidgets();AuthClient.send(request);}
    private void revoke(String id){minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var q=new JsonObject();q.addProperty("action","revokeApplication");q.addProperty("id",id);send(q);}},Client.tr("ui.revoke_access_43400829"),Client.tr("auth.applications.confirm")));}
    void receive(JsonObject value){if(requestId.isEmpty()||!requestId.equals(Json.opt(value,"request","")))return;sent=0;if(Json.opt(value,"kind","").equals("applications")){sessions=value.getAsJsonArray("sessions");next=Json.opt(value,"next","");status=sessions.isEmpty()?Client.text("auth.applications.empty"):"";rebuildWidgets();}else {status=Json.opt(value,"text","");rebuildWidgets();}}
    @Override public void tick(){if(!AuthClient.available()){minecraft.setScreen(null);return;}if(sent>0&&System.currentTimeMillis()-sent>15000){sent=0;status=Client.text("ui.the_response_is_delayed_try_again_373808e3");rebuildWidgets();}}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(460,width-40),top(),bottom());}
    @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int w=Math.min(460,width-40),left=(width-w)/2;UiHeading.dialog(g,font,title,left,top(),w);Ui.status(g,font,status,left,bottom()-54,w,bottom()-30);});}
}
