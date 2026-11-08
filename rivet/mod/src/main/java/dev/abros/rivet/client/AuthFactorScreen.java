package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Secrets stay in this modal and are never written to client preferences or chat. */
final class AuthFactorScreen extends ScrollScreen {
    private final Screen parent;
    private final boolean required;
    private boolean enabled,busy;
    private String secret="",codes="",status="",passwordValue="",codeValue="",requestId="";
    private EditBox password,code;
    private long sent;
    AuthFactorScreen(Screen parent,boolean enabled){super(Client.tr("auth.factor.title"));this.parent=parent;this.enabled=enabled;this.required=AuthClient.offer.has("factorRequired")&&AuthClient.offer.get("factorRequired").getAsBoolean();if(required)status=Client.text("auth.factor.policy");}
    private int top(){return UiDialog.top(height,280);}
    private int bottom(){return height-top();}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(UiActions.button(Client.tr(key),UiActions.Tone.NORMAL,"",b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        int w=Math.min(400,width-40),x=(width-w)/2;
        int count=!codes.isEmpty()?2:enabled?(required?3:4):secret.isEmpty()?2:5;
        var layout=scrollForm(count,new dev.abros.rivet.core.NativeLayout.Box(x,top()+38,w,Math.max(0,bottom()-76-(top()+38))),28);
        w=layout.content().width();password=null;code=null;
        for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++){
            int y=top()+38+(row-firstRow)*28;
            if(!codes.isEmpty()){
                if(row==0){var value=addRenderableWidget(UiFields.text(font,x,y,w,20,Client.tr("auth.factor.backup")));value.setMaxLength(512);value.setValue(codes);value.setEditable(false);}
                else button("auth.factor.copyCodes",x,y,w,()->minecraft.keyboardHandler.setClipboard(codes));
            }else if(row==0){
                password=UiFields.password(font,x,y,w,Client.tr("ui.current_server_password_91ac0570"),this::addRenderableWidget);password.setHint(Client.tr("ui.current_server_password_91ac0570"));password.setValue(passwordValue);password.setResponder(value->passwordValue=value);
            }else if(!enabled&&secret.isEmpty())button("auth.factor.setup",x,y,w,()->send("factorBegin"));
            else if(!enabled&&row==1){var value=addRenderableWidget(UiFields.text(font,x,y,w,20,Client.tr("auth.factor.secret")));value.setMaxLength(32);value.setValue(secret);value.setEditable(false);}
            else if(!enabled&&row==2)button("auth.factor.copySecret",x,y,w,()->minecraft.keyboardHandler.setClipboard(secret));
            else if(row==(enabled?1:3)){code=UiFields.password(font,x,y,w,Client.tr("auth.factor.code"),this::addRenderableWidget);code.setHint(Client.tr("auth.factor.code"));code.setMaxLength(22);code.setValue(codeValue);code.setResponder(value->codeValue=value);}
            else if(!enabled)button("auth.factor.confirm",x,y,w,()->send("factorConfirm"));
            else {String action=row==2?"factorCodes":"factorRemove";button(row==2?"auth.factor.renew":"auth.factor.disable",x,y,w,()->send(action));}
        }
        UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,bottom()-26,Math.min(400,width-40),20),this::addRenderableWidget,this::onClose);
        if(password!=null)setInitialFocus(password);lock();
    }
    private void lock(){for(var child:children())if(child instanceof AbstractWidget widget)widget.active=!busy||widget instanceof Button button&&UiActions.commandOf(button)==UiActions.Command.CLOSE;}
    private void send(String action){
        if(busy||passwordValue.isEmpty())return;
        var request=new JsonObject();request.addProperty("action",action);request.addProperty("password",passwordValue);request.addProperty("code",codeValue);requestId=java.util.UUID.randomUUID().toString();request.addProperty("request",requestId);
        passwordValue="";codeValue="";if(password!=null)password.setValue("");if(code!=null)code.setValue("");busy=true;sent=System.currentTimeMillis();status=Client.text("ui.checking_cbf41dbe");lock();AuthClient.send(request);
    }
    void receive(JsonObject value){
        if(requestId.isEmpty()||!requestId.equals(Json.opt(value,"request","")))return;
        busy=false;sent=0;status=Json.opt(value,"text","");String kind=Json.opt(value,"kind","");
        if(kind.equals("factorSetup")){secret=Json.str(value,"secret");status=Client.text("auth.factor.setupHelp");value.remove("secret");}
        if(kind.equals("factorUpdated")){
            enabled=value.get("secondFactor").getAsBoolean();AuthClient.offer.addProperty("secondFactor",enabled);AuthClient.forget();secret="";
            var entries=new java.util.ArrayList<String>();for(var item:value.getAsJsonArray("codes"))entries.add(item.getAsString());codes=String.join(" ",entries);value.remove("codes");
            status=Client.text(codes.isEmpty()?"auth.factor.removed":"auth.factor.saveCodes");
        }resetScroll();rebuildWidgets();
    }
    @Override public void tick(){if(!AuthClient.connected()){clear();minecraft.setScreen(null);}else if(busy&&System.currentTimeMillis()-sent>15000){busy=false;status=Client.text("ui.the_response_is_delayed_try_again_373808e3");lock();}}
    private void clear(){secret="";codes="";passwordValue="";codeValue="";requestId="";if(password!=null)password.setValue("");if(code!=null)code.setValue("");}
    @Override public void onClose(){clear();minecraft.setScreen(parent);if(parent instanceof AuthAccountScreen account)account.refreshFactors();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(400,width-40),top(),bottom());}
    @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int w=Math.min(400,width-40),left=(width-w)/2;UiHeading.dialog(g,font,title,left,top(),w);Ui.status(g,font,status,left,bottom()-72,w,bottom()-30);});}
}
