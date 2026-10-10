package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
/** Calendar input used by event creation, rescheduling, polls and group tasks. */
final class DateTimeScreen extends Screen {
 private final Screen parent;private final Consumer<String> result;private LocalDate day;private YearMonth month;private int hour,minute;
 DateTimeScreen(Screen parent,String value,Consumer<String> result){super(Client.tr("ui.date_and_time_0011621b"));this.parent=parent;this.result=result;var initial=LocalDateTime.now(AccessibilityScreen.zone()).plusHours(1).withSecond(0);try{initial=LocalDateTime.parse(value,DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));}catch(Exception ignored){}day=initial.toLocalDate();month=YearMonth.from(day);hour=initial.getHour();minute=initial.getMinute()/5*5;}
 private void button(String label,int x,int y,int w,Runnable run){addRenderableWidget(UiActions.button(Component.literal(label),UiActions.Tone.NORMAL,"",b->run.run()).bounds(x,y,w,20).build());}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,dev.abros.rivet.core.UiLayout.calendar(width,height).width(),Math.max(4,dev.abros.rivet.core.UiLayout.calendar(width,height).top()-10),dev.abros.rivet.core.UiLayout.calendar(width,height).time()+54);}
 @Override protected void init(){var layout=dev.abros.rivet.core.UiLayout.calendar(width,height);int w=layout.width(),x=layout.left(),y=layout.top(),cw=w/7;
  button("‹",x,y,28,()->{month=month.minusMonths(1);rebuildWidgets();});button("›",x+w-28,y,28,()->{month=month.plusMonths(1);rebuildWidgets();});
  int offset=month.atDay(1).getDayOfWeek().getValue()-1;
  for(int n=1;n<=month.lengthOfMonth();n++){final var date=month.atDay(n);int cell=n-1+offset;button((date.equals(day)?"[":"")+n+(date.equals(day)?"]":""),x+cell%7*cw,layout.gridTop()+cell/7*layout.cellHeight(),cw-2,()->{day=date;rebuildWidgets();});((Button)children().get(children().size()-1)).setHeight(layout.cellHeight()-2);}
  button(Client.text("ui.today_5d5d2ea2"),x,layout.shortcuts(),w/2-2,()->{day=LocalDate.now(AccessibilityScreen.zone());month=YearMonth.from(day);rebuildWidgets();});button(Client.text("ui.tomorrow_f6de15c5"),x+w/2+2,layout.shortcuts(),w/2-2,()->{day=LocalDate.now(AccessibilityScreen.zone()).plusDays(1);month=YearMonth.from(day);rebuildWidgets();});
  addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.hour_9e03f2f1")+String.format("%02d",hour)+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.hour_9ce65a67"),java.util.stream.IntStream.range(0,24).mapToObj(n->String.format("%02d",n)).toList(),n->{hour=n;rebuildWidgets();},b))).bounds(x,layout.time(),w/2-2,20).build());
  addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.min_8c886fe2")+String.format("%02d",minute)+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.minutes_b571ae10"),java.util.stream.IntStream.range(0,12).mapToObj(n->String.format("%02d",n*5)).toList(),n->{minute=n*5;rebuildWidgets();},b))).bounds(x+w/2+2,layout.time(),w/2-2,20).build());
  button(Client.text("ui.select_fe4b0c80"),x,layout.time()+26,w/2-2,()->{result.accept(day.atTime(hour,minute).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")));onClose();});button(Client.text("cancel"),x+w/2+2,layout.time()+26,w/2-2,this::onClose);
 }
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var layout=dev.abros.rivet.core.UiLayout.calendar(width,height);int top=layout.top(),w=layout.width(),left=layout.left();Ui.centered(g,font,month.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE,java.util.Locale.forLanguageTag("ru"))+" "+month.getYear(),width/2,top+6,UiPalette.color(0xE2BE75));String[] days={Client.text("ui.mon_3770c0ef"),Client.text("ui.tue_93480c93"),Client.text("ui.wed_387e3922"),Client.text("ui.thu_9c8acdf3"),Client.text("ui.fri_e18bb084"),Client.text("ui.sat_e8bd00ad"),Client.text("ui.sun_c58ca969")};for(int i=0;i<7;i++)Ui.text(g,font,days[i],left+i*(w/7)+6,top+27,UiPalette.color(0xCCCCCC));});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
