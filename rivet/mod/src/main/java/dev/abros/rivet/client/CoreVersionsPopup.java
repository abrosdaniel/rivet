package dev.abros.rivet.client;

import dev.abros.rivet.Rivet;
import dev.abros.rivet.core.CoreUpdater;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Version selection is read-only. Only Install starts a download and transaction. */
final class CoreVersionsPopup extends ScrollScreen {
    private final Screen parent;
    private final String requirement;
    private List<CoreUpdater.Update> versions=List.of();
    private CoreUpdater.Update selected;
    private boolean started,loading,installing,failed;
    private String status="";
    CoreVersionsPopup(Screen parent){this(parent,"");}
    CoreVersionsPopup(Screen parent,String requirement){super(Client.tr("ui.rivet_version_11750c78"));this.parent=parent;this.requirement=requirement;if(Client.offeredUpdate!=null&&!Client.offeredUpdate.version().equals(Rivet.VERSION)&&(requirement.isEmpty()||dev.abros.rivet.core.Versions.supportsRequirement(Client.offeredUpdate.version(),requirement))){versions=List.of(Client.offeredUpdate);}}
    private int panelWidth(){return Math.min(300,width-24);}
    private int rowCount(){return Math.max(1,Math.min(Math.max(1,versions.size()),Math.min(5,Math.max(1,(height-24-124)/24))));}
    private int panelHeight(){return Math.min(height-16,124+rowCount()*24);}
    private int left(){return (width-panelWidth())/2;}
    private int top(){return (height-panelHeight())/2;}
    private int bottom(){return top()+panelHeight();}
    @Override protected void init(){
        ModalLayer.prepare(parent,this);
        int x=left(),w=panelWidth(),listTop=top()+52;
        var layout=scrollArea(versions.size(),new dev.abros.rivet.core.NativeLayout.Box(x+12,listTop,w-24,rowCount()*24),24);
        for(int i=firstRow;i<Math.min(versions.size(),firstRow+visibleRows);i++){
            var update=versions.get(i);
            var choice=addRenderableWidget(new UiChoiceRow(x+12,listTop+(i-firstRow)*24,layout.content().width(),update.version(),update.equals(selected),-1,0xFFE2BE75,()->{selected=update;rebuildWidgets();}));
            choice.active=!installing;
        }
        int actionWidth=(w-30)/2;
        var install=addRenderableWidget(UiActions.button(Component.literal(installing?Client.text("ui.preparing_bd54feee"):failed?Client.text("retry"):Client.text("install")),UiActions.Tone.PRIMARY,"",button->{if(failed)load();else install();}).bounds(x+12,bottom()-32,actionWidth,20).build());
        install.active=!loading&&!installing&&(failed||selected!=null)&&Client.pending.isEmpty();
        var close=UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x+18+actionWidth,bottom()-32,w-30-actionWidth,20),this::addRenderableWidget,this::onClose);close.active=!installing;
        if(!started){started=true;load();}
    }
    private void load(){
        if(loading||installing)return;
        loading=true;failed=false;status="";rebuildWidgets();
        try{Client.NETWORK.submit(()->{try{
            var hub=Client.ensureHub();
            var updates=hub.availableCoreUpdates().stream().filter(u->!u.version().equals(Rivet.VERSION)).filter(u->requirement.isEmpty()||dev.abros.rivet.core.Versions.supportsRequirement(u.version(),requirement)).toList();
            minecraft.execute(()->{loading=false;versions=updates;if(selected==null||!updates.contains(selected))selected=null;status=updates.isEmpty()?Client.text("ui.no_other_versions_81bda77b"):"";if(minecraft.screen==this)rebuildWidgets();});
        }catch(Exception failure){minecraft.execute(()->{loading=false;failed=true;status=Client.text("ui.could_not_check_versions_you_can_2b98d80f");if(minecraft.screen==this)rebuildWidgets();});}});
        }catch(java.util.concurrent.RejectedExecutionException busy){loading=false;failed=true;status=Client.text("ui.the_check_is_busy_try_again_03cafbbc");rebuildWidgets();}
    }
    private void install(){
        if(selected==null||installing)return;installing=true;status="";var update=selected;rebuildWidgets();
        Client.IO.submit(()->{try{String transaction=Client.hub.prepareCoreUpdate(Client.loadedJar,update);Client.pending=transaction;minecraft.execute(()->minecraft.setScreen(new RestartScreen(parent,transaction)));}catch(Exception failure){minecraft.execute(()->{installing=false;status=Errors.message(failure);rebuildWidgets();});}});
    }
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float delta){UiDialog.surface(graphics,left(),top(),panelWidth(),panelHeight());}
    @Override public void render(GuiGraphics graphics,int x,int y,float delta){
        UiDialog.render(parent,this,graphics,delta,()->{super.render(graphics,x,y,delta);
            Branding.icon(graphics,left()+12,top()+12,14);
            graphics.drawString(font,Client.text("ui.rivet_update_5591c964"),left()+32,top()+15,UiKit.text(),false);
            graphics.drawString(font,Client.text("ui.installed_version_81f7e135")+Rivet.VERSION,left()+12,top()+34,UiKit.muted(),false);
            if(versions.isEmpty())graphics.drawString(font,loading?Client.text("ui.fetching_versions_ab017709"):Client.text("ui.no_versions_available_50a8c186"),left()+12,top()+58,UiKit.muted(),false);
            String message=loading?Client.text("ui.checking_available_versions_8195b233"):!status.isBlank()?status:selected==null?Client.text("ui.select_a_version_to_install_7949aa4b"):Client.text("ui.install_02059d44")+selected.version()+Client.text("ui.a_game_restart_will_be_required_825c7633");
            var lines=font.split(Component.literal(message),panelWidth()-24);
            for(int i=0;i<Math.min(2,lines.size());i++)graphics.drawString(font,lines.get(i),left()+12,bottom()-60+i*11,UiKit.muted(),false);
            graphics.flush();
        });
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&(x<left()||x>left()+panelWidth()||y<top()||y>bottom())){onClose();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return x>=left()&&x<=left()+panelWidth()&&super.mouseScrolled(x,y,dx,dy);}
    @Override public void onClose(){if(!installing)minecraft.setScreen(parent);}
    @Override public boolean shouldCloseOnEsc(){return !installing;}
}
