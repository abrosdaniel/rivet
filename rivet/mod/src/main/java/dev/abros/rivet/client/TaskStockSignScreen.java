package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Task-specific sign template, displayed literally so formatting cannot modify its contents. */
final class TaskStockSignScreen extends Screen {
 private final Screen parent;private final String taskName,owner,code;private final boolean personal;private String status="";
 TaskStockSignScreen(Screen parent,JsonObject task){super(Client.tr("ui.link_storage_a799a2aa"));this.parent=parent;taskName=Json.str(task,"title");owner=Json.opt(task,"stockLabel","");code=Json.str(task,"code");personal=Json.opt(task,"group","").isEmpty();}
 private int w(){return Math.min(380,width-40);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,250);}private int bottom(){return height-top();}
 String template(){return "&rivet\n"+owner+"\n"+code;}
 @Override protected void init(){addRenderableWidget(UiActions.button(Client.tr("ui.copy_template_fb61fbc8"),UiActions.Tone.NORMAL,"",b->{minecraft.keyboardHandler.setClipboard(template());status=Client.text("ui.three_lines_copied_f09edfc3");}).bounds(x(),bottom()-28,150,20).build()).active=!owner.isBlank()&&!code.isBlank();addRenderableWidget(UiActions.button(Client.tr("done"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x()+w()-80,bottom()-28,80,20).build());}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){ModalLayer.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.text(g,font,font.plainSubstrByWidth(Client.text("ui.task_86524924")+taskName,w()),x(),top()+34,UiPalette.color(0xE0E9EE),false);Ui.text(g,font,Client.text("ui.attach_a_sign_to_the_container_12ee51b6"),x(),top()+52,UiPalette.color(0xBAC7D2),false);String[] values={"&rivet",owner,code};String[] labels={Client.text("ui.marker_c3e3e32f"),personal?Client.text("ui.owner_name_35f57622"):Client.text("ui.group_4fb407c1"),Client.text("ui.task_code_566db492")};for(int n=0;n<3;n++){int yy=top()+72+n*26;g.fill(x(),yy,x()+w(),yy+22,UiPalette.color(0xFF132630));Ui.text(g,font,labels[n],x()+8,yy+7,UiPalette.color(0x8FA6B5),false);Ui.text(g,font,font.plainSubstrByWidth(values[n],w()-136),x()+128,yy+7,UiPalette.color(0xD8E9F2),false);}Ui.wrap(g,font,Client.tr("ui.case_insensitive_the_server_counts_resources_a9322518"),x(),top()+156,w(),UiPalette.color(0xBAC7D2));if(!status.isEmpty())Ui.text(g,font,status,x(),bottom()-43,UiPalette.color(0x79CBA6),false);});}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
