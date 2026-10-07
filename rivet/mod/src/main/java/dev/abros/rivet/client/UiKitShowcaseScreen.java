package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Live UI Kit catalogue. Uses production components and appears only in developer previews. */
final class UiKitShowcaseScreen extends ScrollScreen {
 private final Screen parent;private boolean checked=true;private UiDialog dialog;
 UiKitShowcaseScreen(Screen parent){super(Component.literal("Rivet · UI Kit"));this.parent=parent;}
 @Override protected void init(){
  dialog=UiDialog.fit(width,height,560,460);var body=dialog.body();int rowHeight=88;
  scrollArea(9,body,rowHeight);int x=body.x(),w=scrollLayout().content().width();
  for(int n=firstRow;n<Math.min(9,firstRow+visibleRows);n++){
   int y=body.y()+(n-firstRow)*rowHeight+20;
   switch(n){
    case 0->{int gap=UiKit.SPACE_MEDIUM,bw=(w-2*gap)/3;addRenderableWidget(UiActions.button(Component.literal("Обычная"),UiActions.Tone.NORMAL,"",b->{}).bounds(x,y,bw,UiKit.CONTROL_HEIGHT).build());addRenderableWidget(UiActions.button(Component.literal("Создать"),UiActions.Tone.PRIMARY,UiIcons.PLUS,b->{}).bounds(x+bw+gap,y,bw,UiKit.CONTROL_HEIGHT).build());addRenderableWidget(UiActions.button(Component.literal("Удалить"),UiActions.Tone.DANGER,UiIcons.DELETE,b->{}).bounds(x+2*(bw+gap),y,bw,UiKit.CONTROL_HEIGHT).build());var b=addRenderableWidget(UiActions.button(Component.literal("Недоступно"),UiActions.Tone.NORMAL,"",v->{}).bounds(x,y+26,bw,20).build());b.active=false;}
    case 1->{addRenderableWidget(new UiToggle("Подсказки ввода",x,y,w,checked,()->{checked=!checked;rebuildWidgets();}));addRenderableWidget(UiActions.button(Component.literal("Тема: "+UiPalette.name()+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,"Тема",UiPalette.names(),i->{UiPalette.preview(i);rebuildWidgets();},b).current(UiPalette.selected()))).bounds(x,y+28,w,24).build());}
    case 2->{int half=(w-8)/2;var input=UiFields.text(font,x,y,half,24,Component.literal("Поиск"));input.setHint(Component.literal("Поиск по названию…"));addRenderableWidget(input);var invalid=addRenderableWidget(UiFields.text(font,x+half+8,y,w-half-8,24,Component.literal("Ошибка поля")));UiFields.issue(invalid,"Проверьте значение");}
    case 3->{int half=(w-8)/2;addRenderableWidget(new SidebarButton(x,y,half,"Выбранный раздел",true,()->{}));UiTabs.build(this,font,new dev.abros.rivet.core.NativeLayout.Box(x+half+8,y,w-half-8,20),java.util.List.of("Активная вкладка"),0,this::addRenderableWidget,i->{},true);addRenderableWidget(new UiChoiceRow(x,y+28,w,"Явный выбор · этап или вариант",checked,-1,UiKit.accent(),()->{checked=!checked;rebuildWidgets();}));}
    case 5->{var player=new com.google.gson.JsonObject();player.addProperty("uuid","00000000-0000-0000-0000-000000000001");player.addProperty("name","Игрок");player.addProperty("online",true);addRenderableWidget(new PlayerRow(x,y,w,player,()->{},true));var notice=new com.google.gson.JsonObject();notice.addProperty("title","Новый ответ на задачу");notice.addProperty("section","tasks");notice.addProperty("read",false);notice.addProperty("at",1791000000000L);addRenderableWidget(new NotificationRow(x,y+38,w,notice,()->{}));}
    case 6->{int half=(w-8)/2;addRenderableWidget(UiMetricCard.compact(x,y,half,"Время игры","12 ч 30 мин"));addRenderableWidget(UiMetricCard.compact(x+half+8,y,w-half-8,"Онлайн","8 / 20"));}
    case 8->{}
    case 7->UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(x,y,Math.min(240,w),44),this::addRenderableWidget,UiActions.primary("Применить изменения",()->{},true),UiActions.action("Вернуться к настройкам",()->{},true));
    case 4->addRenderableWidget(UiSummaryCard.compact(x,y,w,"Карточка записи","Краткий статус",java.util.List.of("Подробности — вторичный текст"),UiKit.ACCENT,()->{}));
   }
  }
  UiActions.close(dialog.footer(),this::addRenderableWidget,this::onClose);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());String[] headings={"Действия и состояния","Настройки · название / значение","Поля и валидация","Навигация и выбор","Списки и карточки","Игроки и уведомления","Показатели и обратная связь","Перенос действий при нехватке ширины","Длинные имена и плотность текста"};var b=dialog.body();for(int n=firstRow;n<Math.min(9,firstRow+visibleRows);n++){UiTypography.draw(g,font,headings[n],b.x(),b.y()+(n-firstRow)*88,scrollLayout().content().width(),UiTypography.Role.CAPTION);if(n==7)UiDragHandle.draw(g,scrollLayout().content().right()-UiDragHandle.WIDTH,b.y()+(n-firstRow)*88+56,false);if(n==8){var p=new com.google.gson.JsonObject();p.addProperty("uuid","00000000-0000-0000-0000-000000000001");p.addProperty("name","ABR0Sxd");p.addProperty("prefix","&c[Root]");p.addProperty("suffix","&9@abrosdaniel");p.addProperty("online",true);int identityY=b.y()+(n-firstRow)*88+16;UiPlayerIdentity.draw(g,font,p,b.x(),identityY,scrollLayout().content().width());UiParagraph.draw(g,font,"Строки описания идут подряд.\nКнопки сохраняют свою высоту.",b.x(),identityY+46,UiKit.muted());}}});}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
