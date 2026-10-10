package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;
import static dev.abros.rivet.core.NativeLayout.Track.*;

/** Reusable native title/description form. State and persistence belong to its controller. */
final class UiTextForm {
 record Field(String label,String hint,int limit,String value,Consumer<String> changed) {}
 static List<AbstractWidget> compose(Font font,NativeLayout.Box area,Field title,Field description,
                                    boolean editable,boolean canSave,String saveLabel,Runnable save,Runnable cancel,String titleIssue) {
  var slots=NativeLayout.column(area,4,fixed(titleIssue.isBlank()?20:34),flex(1),fixed(20));
  var titleBox=slots.get(0);
  var input=UiFields.text(font,titleBox.x(),titleBox.y(),titleBox.width(),20,title.label(),title.hint(),title.limit(),title.value(),title.changed());
  input.active=editable;UiFields.issue(input,titleIssue);
  var descBox=slots.get(1);
  var text=UiFields.multiline(font,descBox.x(),descBox.y(),descBox.width(),Math.max(1,descBox.height()-16),description.label(),description.hint(),description.limit(),description.value(),description.changed());
  text.active=editable;
  var buttons=UiActions.row(slots.get(2),ignored->{},new NativeLayout.Track[]{fixed(104),fixed(80)},
   UiActions.primary(saveLabel,save,canSave).withIcon(UiIcons.SAVE).because(editable?Client.text("ui.check_the_previous_submission_s_result_e2826cab"):Client.text("ui.wait_for_saving_to_finish_eee34f2e")),UiActions.action(Client.text("cancel"),cancel,editable).because(Client.text("ui.wait_for_saving_to_finish_eee34f2e")));
  return List.of(input,text,buttons.get(0),buttons.get(1));
 }
 private UiTextForm() {}
}
