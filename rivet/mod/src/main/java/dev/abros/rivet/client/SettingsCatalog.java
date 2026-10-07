package dev.abros.rivet.client;
import java.util.*;
/** Search descriptors shared by settings screens; keys survive ordering and grouping. */
final class SettingsCatalog {
 record Entry(int section,String id,String name,String aliases){}
 static final List<String> EVENT_KEYS=List.of("invite","apply","application","response","workAssign","workDue","reminder","cancel","reschedule","comment","plusComment","workComment","role","plusRemoveMember","plusEventInvite","plusTaskStatus","spark");
 static final List<String> EVENT_NAMES=List.of("Приглашения","Заявки","Решения по заявкам","Отклики","Назначение задачи","Изменение срока","Напоминания","Отмена события","Перенос события","Комментарии","Комментарии к записи","Комментарии задачи","Изменение роли","Исключение из объединения","Приглашение на событие","Статус задачи","Диагностика spark");
 static final List<String> CATEGORIES=List.of("server","home","groups","events","board","polls","ideas","help");
 static final List<String> CATEGORY_NAMES=List.of("Сервер","Личные задачи","Объединения","События","Доска объявлений","Голосования","Предложения","Обращения");
 private static final List<Entry> ENTRIES=create();
 private static void add(List<Entry> out,int section,String keys,String names,String aliases){var ids=keys.split("\\|");var labels=names.split("\\|");if(ids.length!=labels.length)throw new IllegalStateException("Settings descriptors differ");for(int n=0;n<ids.length;n++)out.add(new Entry(section,ids[n],labels[n],aliases));}
 private static List<Entry> create(){var out=new ArrayList<Entry>();
  add(out,0,"theme|opacity|density|guiScale|timezone|contrast|motion|preset","Тема интерфейса|Прозрачные панели|Плотность интерфейса|Масштаб GUI Minecraft|Часовой пояс|Контрастность|Анимации|Профиль оформления","");
  add(out,1,"enabled|scale|opacity|chat|inventory|pause|debug|holograms|dimMoving|key|head|name|prefix|suffix|session|ping|order","Показывать виджет|Масштаб виджета|Плотность фона|В чате|В инвентаре|В меню паузы|При открытом F3|Голограммы привязанных складов|Приглушать фон при движении|Клавиша взаимодействия|Голова игрока|Никнейм|Префикс LuckPerms|Суффикс LuckPerms|Время текущей сессии|Задержка соединения|Порядок блоков","hud виджет");
  for(String block:HudSettings.BLOCKS)out.add(new Entry(1,"block:"+block,HudSettings.label(block),"hud виджет"));
  add(out,2,"digest|toasts|sound|quiet|quietStart|quietEnd|restart|count","Сводка пропущенных при входе|Показывать плашки|Звук|Тихие часы обычных уведомлений|Начало тихих часов|Конец тихих часов|Предупреждения о перезапуске|Одновременно на экране","уведомления");
  for(int n=0;n<CATEGORIES.size();n++)out.add(new Entry(2,"category:"+CATEGORIES.get(n),"Плашки: "+CATEGORY_NAMES.get(n),"уведомления"));
  out.add(new Entry(2,"eventCategory","Область настройки событий","уведомления фильтр событий"));
  for(int n=0;n<EVENT_KEYS.size();n++)out.add(new Entry(2,"event:"+EVENT_KEYS.get(n),EVENT_NAMES.get(n),"уведомления события"));
  add(out,3,"policy|grouping|heads|ping|footer|opacity|density|width","Режим сервера|Группировка игроков|Головы игроков|Точный пинг|Нижний контекст TAB|Прозрачность фона TAB|Плотность строк TAB|Ширина колонок TAB","таб tab ping пинг");
  add(out,4,"policy|dedupe|scale|spacing|opacity|background|hints|hintOpacity","Режим сервера|Объединять системные повторы|Размер текста чата|Расстояние между строками чата|Прозрачность текста чата|Прозрачность фона чата|Подсказки ввода чата|Прозрачность подсказок","chat чат");
  add(out,5,"enabled|opacity|coordinates|xaero","Указатель точки|Прозрачность указателя|Координаты в указателе|Метка маршрута в Xaero’s","маршрут навигация xaero");
  out.add(new Entry(6,"editor","Расположение элементов","hud виджет чат подсказки указатель навигация редактор"));return List.copyOf(out);
 }
 static List<Entry> entries(){return ENTRIES;}
 static List<Entry> section(int section){return ENTRIES.stream().filter(e->e.section()==section).toList();}
 static String id(int section,String label){if(section==1&&label.equals("Масштаб"))label="Масштаб виджета";final String name=label;return section(section).stream().filter(e->e.name().equals(name)).map(Entry::id).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown setting: "+name));}
 static int row(int section,String id){var entries=section(section);for(int n=0;n<entries.size();n++)if(entries.get(n).id().equals(id))return n;throw new IllegalArgumentException("Unknown setting key: "+id);}
 private SettingsCatalog(){}
}
