package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Scrollable review with one explicit action and a cancel button. */
final class ReviewScreen extends TextScreen {
 private final Component action;private final Runnable confirm;
 ReviewScreen(Screen parent,Component title,String text,Component action,Runnable confirm){super(parent,title,text);this.action=action;this.confirm=confirm;}
 ReviewScreen(Screen parent,Component title,String text,Component action,Runnable confirm,boolean literal){super(parent,title,text,40,literal);this.action=action;this.confirm=confirm;}
 @Override protected void actions(dev.abros.rivet.core.NativeLayout.Box area){
  var slots=dev.abros.rivet.core.NativeLayout.row(area,6,dev.abros.rivet.core.NativeLayout.Track.flex(1),dev.abros.rivet.core.NativeLayout.Track.flex(2));
  var cancel=slots.get(0);var apply=slots.get(1);
  addRenderableWidget(UiActions.button(Client.tr("cancel"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(cancel.x(),cancel.y(),cancel.width(),20).build());
  addRenderableWidget(UiActions.button(action,UiActions.Tone.PRIMARY,"",b->confirm.run()).bounds(apply.x(),apply.y(),apply.width(),20).build());
 }
}
