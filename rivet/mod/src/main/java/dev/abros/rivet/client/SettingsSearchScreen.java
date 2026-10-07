package dev.abros.rivet.client;
import java.util.*;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Local settings index: navigation opens the matching row in the original workspace. */
final class SettingsSearchScreen extends ScrollScreen {
 record Entry(int section,int row,String name,String aliases){}
 private final Screen parent;private UiDialog dialog;private String query="";private long changed;private List<Entry> results=List.of();
 SettingsSearchScreen(Screen parent){super(Component.literal("Поиск настроек"));this.parent=parent;}
 static List<Entry> entries(){var out=new ArrayList<Entry>();String[][] names={
  {"Тема интерфейса","Прозрачные панели","Плотность интерфейса","Детали оформления","Масштаб GUI Minecraft","Часовой пояс","Контрастность","Анимации","Профиль оформления"},
  {"Показывать виджет","Масштаб виджета","Плотность фона","Виджет в чате","Виджет в инвентаре","Виджет в меню паузы","Виджет при F3","Голограммы привязанных складов","Приглушать фон при движении","Режим курсора · клавиша","Голова игрока","Никнейм","Префикс LuckPerms","Суффикс LuckPerms","Время текущей сессии","Задержка соединения","Порядок блоков","Сервер и онлайн","Мини-профиль","Моя задача","Ближайшее событие","Мои объединения","Непрочитанные"},
  {"Сводка пропущенных при входе","Показывать плашки","Звук уведомлений","Тихие часы","Начало тихих часов","Конец тихих часов","Предупреждения о перезапуске","Количество плашек"},
  {"Режим сервера","Группировка игроков","Головы игроков","Точный пинг","Нижний контекст TAB","Прозрачность фона TAB","Плотность строк TAB","Ширина колонок TAB"},
  {"Режим сервера","Объединять системные повторы","Размер текста чата","Расстояние между строками чата","Прозрачность текста чата","Прозрачность фона чата","Подсказки ввода чата","Прозрачность подсказок"},
  {"Указатель точки","Прозрачность указателя","Координаты в указателе","Метка маршрута в Xaero’s"}};
  for(int section=0;section<names.length;section++)for(int row=0;row<names[section].length;row++)out.add(new Entry(section,section==0?row*2:row,names[section][row],section==1?"hud виджет":section==3?"таб tab ping пинг":section==5?"маршрут навигация xaero":section==4?"chat чат":""));
  out.add(new Entry(6,0,"Расположение элементов","hud виджет чат подсказки указатель навигация редактор"));
  var categories=List.of("Сервер","Личные задачи","Объединения","События","Доска объявлений","Голосования","Предложения","Обращения");for(int n=0;n<categories.size();n++)out.add(new Entry(2,8+n,"Плашки: "+categories.get(n),"уведомления"));out.add(new Entry(2,16,"Фильтр событий","уведомления"));var events=List.of("Приглашения","Заявки","Решения по заявкам","Отклики","Назначение задачи","Изменение срока","Напоминания","Отмена события","Перенос события","Комментарии","Комментарии к записи","Комментарии задачи","Изменение роли","Исключение из объединения","Приглашение на событие","Статус задачи","Диагностика spark");for(int n=0;n<events.size();n++)out.add(new Entry(2,17+n,events.get(n),"уведомления события"));return List.copyOf(out);}
 static List<Entry> search(String query){var words=query.strip().toLowerCase(Locale.ROOT).split("\\s+");return entries().stream().filter(e->{String text=(e.name()+" "+UiSettingsShell.SECTIONS.get(e.section())+" "+e.aliases()).toLowerCase(Locale.ROOT);return Arrays.stream(words).allMatch(text::contains);}).toList();}
 private void search(){results=search(query);resetScroll();rebuildWidgets();}
 @Override protected void init(){dialog=UiSettingsShell.build(width,height,-1,parent,this,this::addRenderableWidget,()->{query="";search();},this::onClose);var body=dialog.body();UiSearchToolbar.query(font,new NativeLayout.Box(body.x(),body.y(),body.width(),20),"Тема, пинг, прозрачность…",query,this::addRenderableWidget,v->{query=v;changed=net.minecraft.Util.getMillis()+120;},this::search,true);if(results.isEmpty()&&query.isBlank())results=search("");var geometry=scrollArea(results.size(),new NativeLayout.Box(body.x(),body.y()+30,body.width(),Math.max(0,body.height()-30)),28);for(int n=firstRow;n<Math.min(results.size(),firstRow+visibleRows);n++){var e=results.get(n);var button=UiActions.button(Component.literal(UiSettingsShell.SECTIONS.get(e.section())+" · "+e.name()),UiActions.Tone.NORMAL,"",v->{var screen=UiSettingsShell.open(e.section()==6?this:parent,e.section());if(screen instanceof ScrollScreen scroll)scroll.revealRow(e.row());minecraft.setScreen(screen);}).bounds(geometry.content().x(),geometry.content().y()+(n-firstRow)*28,geometry.content().width(),24).build();button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(e.name())));addRenderableWidget(button);}}
 @Override public void tick(){if(changed>0&&net.minecraft.Util.getMillis()>=changed){changed=0;search();}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(results.isEmpty())UiEmptyState.draw(g,font,"Настройки не найдены","Попробуйте «пинг», «прозрачность» или название раздела.",dialog.body().x(),dialog.body().y()+32,dialog.body().width(),dialog.body().bottom());});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){UiNavigation.back(this,parent);}
}
