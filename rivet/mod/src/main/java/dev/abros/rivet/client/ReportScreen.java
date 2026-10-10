package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.abros.rivet.core.*;

final class ReportScreen extends Screen implements CommunityScreen.Receiver {
    private final TextDraft draft;
    private final RequestSession session=new RequestSession();
    private final String initial;
    private final String identity;
    private final Screen parent;
    private JsonObject pending;
    private MultiLineEditBox message;
    private Button send;
    private boolean busy;
    private String status="",category="technical";
    ReportScreen(Screen parent){this(parent,"");}
    ReportScreen(Screen parent,String initial){this(parent,initial,initial);}
    ReportScreen(Screen parent,String initial,String identity){super(initial.startsWith(Client.text("ui.player_420c4d94"))?Client.tr("ui.report_a_player_59a20060"):Client.tr("server.report"));this.parent=parent;this.initial=initial;this.identity=identity;category=initial.startsWith(Client.text("ui.player_420c4d94"))?"player":"technical";draft=new TextDraft("report:"+identity);pending=draft.pending();if(!draft.pendingReadable)status=Client.text("ui.could_not_restore_the_pending_submission_3b3a7f68");if(pending!=null)category=Json.opt(pending,"category",category);}
    static ReportScreen player(Screen parent,String name){return new ReportScreen(parent,Client.text("ui.player_420c4d94")+name+"\n",dev.abros.rivet.core.DraftIdentity.playerReport(name));}
    private int panelHeight(){return Math.min(270,height-20);}
    private int top(){return (height-panelHeight())/2;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(460,width-32),top(),top()+panelHeight());}
 @Override protected void init(){
        String text=message==null?(pending==null?draft.load(""):Json.opt(pending,"message","")):message.getValue();
        // Older drafts included the target in the message itself.
        if(!initial.isEmpty()&&text.startsWith(initial))text=text.substring(initial.length());
        else if(!identity.isEmpty()&&text.startsWith(identity))text=text.substring(identity.length());
        int w=Math.min(460,width-32),x=(width-w)/2,y=top();
        if(initial.isEmpty()){var categoryButton=addRenderableWidget(UiActions.button(Component.literal(switch(category){case "appeal"->Client.text("ui.appeal_a_punishment_8e63dac1");case "other"->Client.text("ui.other_8f96cac0");default->Client.text("ui.technical_issue_6320eb16");}+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.report_category_55477ff1"),java.util.List.of(Client.text("ui.technical_issue_6320eb16"),Client.text("ui.appeal_a_punishment_8e63dac1"),Client.text("ui.other_8f96cac0")),n->{category=java.util.List.of("technical","appeal","other").get(n);rebuildWidgets();},b))).bounds(x,y+25,w,20).build());categoryButton.active=!busy&&pending==null;}
        message=addRenderableWidget(UiFields.multiline(font,x,y+48,w,panelHeight()-110,Client.tr("ui.describe_what_happened_a302276d"),Client.tr("server.message")));
        message.setCharacterLimit(Math.max(1,1500-initial.length()));message.setValue(text);
        message.setValueListener(draft::save);
        send=addRenderableWidget(UiActions.button(Client.tr("server.send"),UiActions.Tone.NORMAL,"",b->sendReport()).bounds(x,y+panelHeight()-26,w/2-3,20).build());
        UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x+w/2+3,y+panelHeight()-26,w/2-3,20),this::addRenderableWidget,this::onClose);
        updateInputs();setInitialFocus(message);
    }
    private void updateInputs(){if(send!=null){send.active=!busy&&draft.pendingReadable;send.setMessage(Client.tr(busy?"server.sending":pending!=null?"server.retryReport":"server.reviewReport"));}if(message!=null)message.active=!busy&&pending==null;}
    private void sendReport(){
        if(busy||!draft.pendingReadable)return;
        if(pending!=null){dispatchPending();return;}
        String text=message.getValue().strip();if(text.isBlank()){status=Client.tr("server.reportempty").getString();return;}
        draft.save(message.getValue());busy=true;status="";updateInputs();
        Client.IO.submit(()->{
            var j=new JsonObject();j.addProperty("action","report");j.addProperty("message",initial+text);j.addProperty("category",category);j.addProperty("coreVersion",dev.abros.rivet.Rivet.VERSION);
            j.addProperty("packHash",Client.hub==null?"":Client.hub.activeHash());
            try{String audit=Client.hub==null?"Unavailable":String.join("\n",Client.hub.audit());j.addProperty("audit",audit.substring(0,Math.min(2000,audit.length())));}catch(Exception ex){j.addProperty("audit","Verification failed");}
            minecraft.execute(()->{if(minecraft.screen!=this){busy=false;return;}busy=false;updateInputs();String preview=Client.tr("server.report.reviewText").getString()+"\n\n"+Client.tr("server.message").getString()+":\n"+Json.str(j,"message")+"\n\n"+Client.tr("server.report.details").getString()+"\ncategory: "+Json.str(j,"category")+"\nRivet: "+Json.str(j,"coreVersion")+"\npackHash: "+Json.str(j,"packHash")+"\naudit:\n"+Json.str(j,"audit");minecraft.setScreen(new ReviewScreen(this,Client.tr("server.preview"),preview,Client.tr("server.send"),()->{pending=j.deepCopy();minecraft.setScreen(this);dispatchPending();},true));});
        });
    }
    private void dispatchPending(){
        busy=true;status=Client.text("server.sending");updateInputs();var packet=session.begin(pending,true,System.currentTimeMillis());pending=session.command();
        draft.pending(pending,()->{if(minecraft.screen==this&&ServerMenuClient.available())ServerMenuClient.request(packet);else{session.cancel();busy=false;}},error->{session.cancel();busy=false;status=Client.text("ui.could_not_save_the_pending_submission_bd6b5453");updateInputs();});
    }
    @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int w=Math.min(460,width-32),left=(width-w)/2;UiHeading.dialog(g,font,title,left,top(),w);if(!initial.isBlank())Ui.text(g,font,font.plainSubstrByWidth(initial.strip(),w),left,top()+28,UiPalette.color(0xEEEEEE));Ui.status(g,font,status,left,top()+panelHeight()-49,w,top()+panelHeight()-28);});}
    public void receiveCommunity(JsonObject response){if(!session.receive(response))return;busy=false;status=Json.opt(response,"text","");if(!response.has("error")){pending=null;draft.pending(null,()->{},Client::failure);draft.clear();ServerMenuClient.result=Client.text("ui.sent_to_staff_5396c07f");minecraft.setScreen(parent);}else{if(Set.of("INVALID","FORBIDDEN","EXPIRED").contains(Json.opt(response,"code",""))){pending=null;draft.pending(null,()->{},Client::failure);}updateInputs();}}
    @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;status=Client.text("ui.no_response_try_sending_again_669313b3");updateInputs();}if(!ServerMenuClient.available()){session.cancel();busy=false;minecraft.setScreen(parent);}}
    @Override public void onClose(){if(busy){status=Client.text("ui.wait_for_the_server_to_respond_43e44ea8");return;}if(message!=null)draft.save(message.getValue());session.cancel();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
