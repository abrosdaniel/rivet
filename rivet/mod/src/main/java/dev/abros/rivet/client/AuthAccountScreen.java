package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

final class AuthAccountScreen extends ScrollScreen {
    private final Screen parent;private JsonArray devices=new JsonArray();private String status="",type="",currentDevice="";private JsonObject invite;private EditBox oldPassword,password,repeat;private boolean changing,linked;private String mode="",linkAction="";private long refreshAfter;
    AuthAccountScreen(Screen parent){super(Client.tr("ui.account_security_18ede0e5"));this.parent=parent;}
    static Screen invitation(Screen parent,JsonObject invitation){var screen=new AuthAccountScreen(parent);screen.invite=invitation;return screen;}
    static void open(Screen parent){Minecraft.getInstance().setScreen(new AuthAccountScreen(parent));requestDevices();}
    private static void requestDevices(){var j=new JsonObject();j.addProperty("action","devices");AuthClient.send(j);}
    static void invite(Screen parent,String target){Minecraft.getInstance().setScreen(new UiConfirmDialog(yes->{Minecraft.getInstance().setScreen(parent);if(yes){var j=new JsonObject();j.addProperty("action","invite");j.addProperty("target",target);AuthClient.send(j);}},Component.literal(Client.text("ui.create_invitation_for_58c9134c")+target+"?"),Client.tr("ui.share_the_invitation_only_after_verifying_0467889f")));}
    private PasswordBox secret(int x,int y,int w,String label){return UiFields.password(font,x,y,w,Component.literal(label),this::addRenderableWidget);}
    private int panelTop(){return UiDialog.top(height,300);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(460,width-40),panelTop(),panelBottom());}
 @Override protected void init(){int w=Math.min(460,width-40),x=(width-w)/2;
        if(invite!=null){var code=addRenderableWidget(UiFields.text(font,x,panelTop()+90,w,20,Client.tr("ui.recovery_code_6b91900d")));code.setMaxLength(128);code.setValue(Json.str(invite,"token"));code.setEditable(false);addRenderableWidget(UiActions.button(Client.tr("ui.copy_code_bc56422b"),UiActions.Tone.NORMAL,"",b->{if(System.currentTimeMillis()<invite.get("expires").getAsLong()){minecraft.keyboardHandler.setClipboard(Json.str(invite,"token"));status=Client.text("ui.copied_share_it_privately_with_the_892c95cb");}else status=Client.text("ui.invitation_expired_78527b14");}).bounds(x,panelTop()+118,w,20).build());}
        else if(!linkAction.isEmpty()){
            password=secret(x,panelTop()+70,w,Client.text("ui.current_server_password_91ac0570"));password.setHint(Client.tr("ui.current_server_password_91ac0570"));
            addRenderableWidget(UiActions.button(Component.literal(linkAction.equals("link")?Client.text("ui.confirm_linking_dc49320c"):Client.text("ui.confirm_unlinking_89db9434")),UiActions.Tone.PRIMARY,UiIcons.CHECK,b->{
                if(password.getValue().isEmpty()){status=Client.text("ui.enter_your_current_server_password_ca14179e");return;}String value=password.getValue();password.setValue("");
                if(linkAction.equals("link"))AuthClient.link(value);else{var request=new JsonObject();request.addProperty("action","unlink");request.addProperty("password",value);AuthClient.send(request);}status=Client.text("ui.checking_cbf41dbe");
            }).bounds(x,panelTop()+100,w,20).build());setInitialFocus(password);
        }
        else if(changing){oldPassword=secret(x,panelTop()+40,w,Client.text("ui.current_password_6cadf497"));oldPassword.setHint(Client.tr("ui.current_password_6cadf497"));password=secret(x,panelTop()+74,w,Client.text("ui.new_password_5e611d70"));password.setHint(Component.literal(Client.text("ui.new_password_at_least_b34904a9")+AuthClient.minimumPasswordLength()+Client.text("ui.characters_62f5c625")));repeat=secret(x,panelTop()+108,w,Client.text("ui.repeat_new_password_9635320f"));repeat.setHint(Client.tr("ui.repeat_new_password_9635320f"));addRenderableWidget(UiActions.button(Client.tr("ui.change_password_and_revoke_all_sessions_3f5c4b0f"),UiActions.Tone.NORMAL,"",b->{if(password.getValue().length()<AuthClient.minimumPasswordLength()){status=Client.text("ui.at_least_acb85219")+AuthClient.minimumPasswordLength()+Client.text("ui.characters_62f5c625");return;}if(!password.getValue().equals(repeat.getValue())){status=Client.text("ui.passwords_do_not_match_a73dc9b1");return;}var j=new JsonObject();j.addProperty("action","change");j.addProperty("oldPassword",oldPassword.getValue());j.addProperty("password",password.getValue());oldPassword.setValue("");password.setValue("");repeat.setValue("");AuthClient.send(j);status=Client.text("ui.checking_cbf41dbe");}).bounds(x,panelTop()+145,w,20).build());setInitialFocus(oldPassword);}
        else {scrollArea(devices.size(),new dev.abros.rivet.core.NativeLayout.Box(x,panelTop()+50,Math.max(0,w),Math.max(0,(panelBottom()-140)-(panelTop()+50))),36);for(int i=firstRow;i<Math.min(devices.size(),firstRow+visibleRows);i++){var device=devices.get(i).getAsJsonObject();addRenderableWidget(UiActions.button(Component.literal(Json.str(device,"label")+(Json.str(device,"id").equals(currentDevice)?Client.text("ui.this_device_033f89ae"):"")+Client.text("ui.revoke_26f0cd4e")),UiActions.Tone.NORMAL,"",b->revoke(Json.str(device,"id"))).bounds(x,panelTop()+50+(i-firstRow)*36,w,20).build());}
            if(mode.equals("hybrid")){var link=addRenderableWidget(UiActions.button(Component.literal(linked?Client.text("ui.unlink_minecraft_account_0a5fcb16"):Client.text("ui.link_minecraft_account_af99422a")),UiActions.Tone.NORMAL,"",b->{linkAction=linked?"unlink":"link";status=Client.text("ui.confirm_this_action_with_your_server_a745819a");rebuildWidgets();}).bounds(x,panelBottom()-128,w,20).build());link.active=linked||AuthClient.officialLauncher();}
            var change=addRenderableWidget(UiActions.button(Client.tr("ui.change_password_3fda03bb"),UiActions.Tone.NORMAL,"",b->{changing=true;rebuildWidgets();}).bounds(x,panelBottom()-100,w,20).build());change.active=type.equals("local");addRenderableWidget(UiActions.button(Client.tr("ui.revoke_all_devices_and_sessions_50d8361f"),UiActions.Tone.NORMAL,"",b->revoke("all")).bounds(x,panelBottom()-76,w,20).build());}
        UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,panelBottom()-26,w,20),this::addRenderableWidget,this::onClose);
    }
    private void revoke(String id){minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var j=new JsonObject();j.addProperty("action","revoke");j.addProperty("device",id);AuthClient.send(j);}},Client.tr("ui.revoke_access_43400829"),Client.tr("ui.sessions_on_the_selected_device_will_3dedb086")));}
    void receive(JsonObject j){status=Json.opt(j,"text","");if(Json.opt(j,"kind","").equals("devices")){devices=j.getAsJsonArray("devices");linked=j.has("linked")&&j.get("linked").getAsBoolean();mode=Json.opt(j,"mode","");type=Json.str(j,"type");currentDevice=Json.opt(j,"current","");rebuildWidgets();}else if(Json.opt(j,"kind","").equals("updated")){linkAction="";refreshAfter=System.currentTimeMillis()+800;rebuildWidgets();}}
    @Override public void tick(){if(!AuthClient.available()){minecraft.setScreen(null);return;}if(refreshAfter>0&&System.currentTimeMillis()>=refreshAfter){refreshAfter=0;requestDevices();}}
    @Override public void onClose(){if(!linkAction.isEmpty()){if(password!=null)password.setValue("");linkAction="";status="";rebuildWidgets();}else if(changing){changing=false;rebuildWidgets();}else {if(invite!=null)invite.remove("token");minecraft.setScreen(parent);}}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int x,int y,float delta){UiDialog.render(parent,this,g,delta,()->{super.render(g,x,y,delta);UiHeading.dialog(g,font,title,(width-(Math.min(460,width-40)))/2,panelTop(),Math.min(460,width-40));if(invite!=null){Ui.centered(g,font,Client.text("ui.reset_access_32c85a11")+Json.str(invite,"target"),width/2,panelTop()+50,0xFFFFFF);Ui.centered(g,font,Client.text("ui.seconds_remaining_531dd249")+Math.max(0,(invite.get("expires").getAsLong()-System.currentTimeMillis())/1000),width/2,panelTop()+70,UiPalette.color(0xAAAAAA));}Ui.status(g,font,status,(width-Math.min(460,width-40))/2,panelBottom()-52,Math.min(460,width-40),panelBottom()-28);});}
}
