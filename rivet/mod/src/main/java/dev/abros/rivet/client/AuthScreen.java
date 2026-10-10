package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AuthScreen extends Screen {
    private EditBox password,repeat,invitation;
    private int panelTop,panelBottom,statusTop;
    private PasswordBox secret(int x,int y,int w,Component label){return UiFields.password(font,x,y,w,label,this::addRenderableWidget);}
    private boolean resetting,remember=true,error;
    private String status="";
    private long sent;
    private Button submit;
    AuthScreen(){super(Client.tr("ui.rivet_sign_in_0bce7a94"));}
    private boolean registering(){return !resetting&&Json.opt(AuthClient.offer,"type","").equals("new");}
    private String heading(){return resetting?Client.text("ui.account_recovery_c52439eb"):registering()?Client.text("ui.welcome_031669e1"):Client.text("ui.welcome_back_c72db9be");}
    private String action(){return resetting?Client.text("ui.save_new_password_5a9c975c"):registering()?Client.text("ui.create_account_and_sign_in_8f859050"):Client.text("ui.join_server_e31aa98c");}
    private Button button(String text,int x,int y,int w,Runnable run){return addRenderableWidget(UiActions.button(Component.literal(text),UiActions.Tone.NORMAL,"",b->run.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        int w=Math.min(320,width-32),x=(width-w)/2;panelTop=Math.max(6,(height-254)/2);int y=panelTop+46;
        String first=password==null?"":password.getValue(),second=repeat==null?"":repeat.getValue(),code=invitation==null?"":invitation.getValue();
        repeat=null;invitation=null;
        password=secret(x,y,w,Component.literal(resetting||registering()?Client.text("ui.new_password_4934551e")+AuthClient.minimumPasswordLength()+Client.text("ui.128_characters_6bc827fb"):Client.text("ui.your_password_c6c4a6ab")));
        password.setHint(Component.literal(resetting||registering()?Client.text("ui.choose_a_password_at_least_505f6984")+AuthClient.minimumPasswordLength()+Client.text("ui.characters_62f5c625"):Client.text("ui.enter_password_9728edf3")));password.setValue(first);y+=22;
        if(resetting||registering()){repeat=secret(x,y,w,Client.tr("ui.repeat_password_c39e27d0"));repeat.setHint(Client.tr("ui.repeat_new_password_e32d8bb9"));repeat.setValue(second);y+=22;}
        if(resetting){invitation=secret(x,y,w,Client.tr("ui.code_from_administrator_c79fe328"));invitation.setHint(Client.tr("ui.invitation_code_from_administrator_da6f8e34"));invitation.setValue(code);y+=22;}
        else{addRenderableWidget(new UiChoiceRow(x,y,w,Client.text("ui.remember_me_30_days_368f0b42"),remember,-1,UiKit.ACCENT,()->{remember=!remember;rebuildWidgets();}));y+=22;}
        submit=button(action(),x,y,w,this::submit);y+=22;
        if(!registering()){button(resetting?Client.text("ui.return_to_sign_in_3663ccab"):Client.text("ui.forgot_password_74372940"),x,y,w,()->{resetting=!resetting;clearSecrets();error=false;status=resetting?Client.text("ui.ask_an_administrator_for_a_recovery_6151ebb8"):"";rebuildWidgets();});y+=22;}
        if(!resetting&&AuthClient.officialLauncher()&&Json.opt(AuthClient.offer,"mode","").equals("hybrid")&&AuthClient.offer.has("linked")&&AuthClient.offer.get("linked").getAsBoolean()){button(Client.text("ui.sign_in_with_minecraft_account_5a2a4dad"),x,y,w,()->{waiting(Client.text("ui.verifying_minecraft_account_c1ae2522"));AuthClient.official();});y+=22;}
        statusTop=y+4;panelBottom=y+68;button(Client.text("ui.disconnect_12b95765"),x,panelBottom-26,w,this::onClose);
        setInitialFocus(password);setBusy(sent>0);
    }
    private void clearSecrets(){if(password!=null)password.setValue("");if(repeat!=null)repeat.setValue("");if(invitation!=null)invitation.setValue("");}
    private void setBusy(boolean value){for(var child:children())if(child instanceof AbstractWidget widget)widget.active=!value;for(var child:children())if(child instanceof Button b&&b.getMessage().getString().equals(Client.text("ui.disconnect_12b95765")))b.active=true;if(submit!=null)submit.setMessage(Component.literal(value?Client.text("ui.checking_350473a9"):action()));}
    private void waiting(String text){sent=System.currentTimeMillis();error=false;status=text;setBusy(true);}
    private void fail(String text,EditBox field){error=true;status=text;setFocused(field);}
    private void submit(){
        if(submit==null||!submit.active)return;
        String value=password.getValue();
        if(value.isEmpty()){fail(Client.text("ui.enter_your_password_to_continue_a651d34b"),password);return;}
        if((resetting||registering())&&value.length()<AuthClient.minimumPasswordLength()){fail(Client.text("ui.at_least_acb85219")+AuthClient.minimumPasswordLength()+Client.text("ui.characters_6c0d6c91"),password);return;}
        if((resetting||registering())&&!value.equals(repeat.getValue())){fail(Client.text("ui.passwords_do_not_match_check_the_50e3ca94"),repeat);return;}
        if(resetting&&invitation.getValue().isBlank()){fail(Client.text("ui.enter_the_invitation_you_received_from_b5d27e98"),invitation);return;}
        var j=new JsonObject();j.addProperty("action",resetting?"reset":registering()?"register":"login");j.addProperty("password",value);j.addProperty("remember",remember);j.addProperty("label",System.getProperty("os.name",Client.text("ui.computer_1db1b7e7")));if(resetting)j.addProperty("invitation",invitation.getValue());
        clearSecrets();waiting(Client.text("ui.checking_data_f3aeedf6"));AuthClient.send(j);
    }
    void receive(JsonObject j){sent=0;error=Json.opt(j,"kind","").equals("error");status=Json.opt(j,"text","");if(Json.opt(j,"kind","").equals("resetDone")){resetting=false;AuthClient.offer.addProperty("type","local");AuthClient.forget();clearSecrets();status=Client.text("ui.password_changed_sign_in_with_your_a089f072");rebuildWidgets();}setBusy(false);setFocused(password);}
    @Override public void tick(){if(sent>0&&System.currentTimeMillis()-sent>15000){sent=0;setBusy(false);error=true;status=Client.text("ui.the_response_is_delayed_try_again_373808e3");}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==335)&&getFocused() instanceof EditBox){if(getFocused()==password&&repeat!=null)setFocused(repeat);else if(getFocused()==repeat&&invitation!=null)setFocused(invitation);else submit();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void onClose(){clearSecrets();AuthClient.cancel();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g,x,y,delta);UiDialog.draw(g,width,Math.min(320,width-32),panelTop,panelBottom);}
    @Override public void render(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);int w=Math.min(320,width-32),left=(width-w)/2;Ui.centered(g,font,heading(),width/2,panelTop+12,UiPalette.color(0xE2BE75));String name=Json.opt(AuthClient.offer,"name","");Ui.centered(g,font,font.plainSubstrByWidth(name+" · "+(resetting?Client.text("ui.new_password_5e611d70"):registering()?Client.text("ui.account_registration_d79428bc"):Client.text("ui.account_sign_in_c1e9a4ea")),w),width/2,panelTop+29,UiPalette.color(0xCCD4DE));if(error)g.fill(left-4,statusTop,left-2,panelBottom-30,UiPalette.color(0xFFFF8A80));Ui.status(g,font,status.isEmpty()?Client.text("ui.secure_connection_sign_in_before_loading_a915a2f9"):status,left,statusTop,w,panelBottom-30);}
}
