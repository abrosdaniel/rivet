# Действующий контекст Rivet

Обновлено 2026-11-08. Правила работы — [AGENTS.md](AGENTS.md).
Описание продукта, параметров и API поддерживается только в `docs/`.

## Где искать актуальные сведения

- [Игроки](../docs/player-guide.md), [сервер](../docs/server/index.md).
- [Архитектура](../docs/development/modules.md), [потоки данных](../docs/development/data-flow.md).
- [UI Kit](../docs/development/ui-kit.md), [компоновка](../docs/development/ui-layout.md).
- [API](../docs/api/index.md), [OpenAPI](../rivet/core/src/main/resources/rivet/api/openapi.json).
- [Материалы карты](../docs/api/terrain-materials.md), [серверный рендер](../docs/server/terrain.md).
- [Сборка и CI](../CONTRIBUTING.md), [проверки API](../docs/development/api-testing.md).

## Текущая работа

2026-11-08: Windows CI упал на двух ServerSettingsTest из-за CRLF шаблона.
Воспроизведены оба исходных падения с CRLF-копией packaged resource, затем
фикстуры переведены на canonical LF перед явным выбором LF/CRLF. Добавлены
assert фактической мутации невалидной фикстуры и проверка загрузки неизменённого
raw packaged template. Полный core прогон с CRLF: 799 tests, 0 failures/errors.
Настоящий Windows runner ещё не перепроверен; runtime parser/validator не ослаблены.

2026-11-08: пользователь подтвердил, что 2.8.0 уже выпущена. Следующий кандидат
2.9.0: кроме исправления плаща включает новые настройки chat.globalTrigger,
chat.groupTrigger, chat.localTrigger, chat.personalTrigger и chat.localEmptyMessage. Любой один триггер
может быть пустым и задаёт канал без префикса; два пустых запрещены. Пересекающиеся префиксы, пробелы (включая NBSP), двоеточие,
начальный slash и неверная длина останавливают загрузку настроек.
Предупреждение пустого локального чата отправляется каждый раз; удалён старый
emptyLocalWarned и его tick scan. Live chat с !! / @@ / ~ прошёл, включая два
предупреждения с пользовательским текстом, picker/completion/hints и tell/me.
Конфиг очищает retired switches/limits/categories и community.sections с backup;
активные значения и многострочные строки сохранены. Шаблон не возвращает categories.
Личный канал добавлен через ChatPersonalInputMixin: shortcut преобразуется в
native sendCommand(msg), сервер собственного пути доставки не имеет. Raw shortcut
на сервере отменяется, чтобы никогда не попасть в общий чат. ClientChat оформляет
MSG_COMMAND incoming/outgoing зелёным, не меняя bound type и signed message;
чужие замены компонентов сохраняются. /msg, /w, /tell не перехватываются.
Три изолированных клиента подтвердили адресную доставку и отсутствие утечки
постороннему; отдельный прогон подтвердил два local-empty предупреждения.
Установленный клиент не заменён. GitHub CI точного коммита ещё не выполнялся.

2026-11-08: исправлен наклон плаща гардероба: detached preview синхронизирует
cloak/current/previous position и предыдущий поворот тела для CapeLayer.
Новых режимов просмотра нет. Native wardrobe с 81 состоянием и проверкой
отсутствия ложного движения после рендера прошёл; кадр проверен визуально.
Полный build/verifyBundle: 820/0. Кандидат 2.8.0 и release notes обновлены.
Установленный клиент не заменён; exact-commit CI/публикация не выполнялись.

2026-11-08: после ручной проверки гардероба входы на главной, в профиле,
поиске действий и колесе переименованы в «Внешний вид». Вкладка «Скины» сохранена.
Кнопки профиля имеют равную ширину. Гардероб использует общую ширину страницы
без лишних боковых полей, одинаковые панели с внутренним отступом 8 и зазором 8.
Проверено, что даже при большой плотности в низком окне остаётся видимая строка.
Найден воспроизводимый путь timeout главной: её queued read отбрасывался при
переходе в гардероб, но CommunityWorkspace продолжал ждать. При открытии теперь
cancelRead отменяет read и помечает страницу для обновления после возврата.
SkinUiHarness проходит через настоящие кнопки главной/профиля, проверяет возврат
и успешную загрузку home (RIVET_WARDROBE_HOME_RETURN_OK), затем все skin/cape/outfit
сценарии и 81 кадр GUI/themes. Финальный live повтор после удаления широких
боковых полей прошёл. Adaptive RU/EN по 198 состояний и reorder 135 прошли.
Полный build 820/0; после последней правки ширины повтор mod:test/verifyBundle
успешен. Tooling 52/0/1 skip; strict docs 80 страниц/0 битых ссылок. Кандидат
2.8.0 перепакован после финальной компиляции, bytecode и SHA256 сверены.
Установленный клиент и ручной стенд не изменены. Exact-commit GitHub CI и
публикация не запускались; готовность удалённого CI не подтверждена.


2026-11-08: по запросу пользователя запущен отдельный клиент для ручной проверки
нового гардероба (профиль wardrobe-review, актуальный packaged JAR кандидата).
Подключение к уже работающему тестовому серверу 127.0.0.1:25621, RivetReview.
При запуске на сервере не было игроков; старый тестовый профиль и установленный
Prism не изменялись. Auth прежнего стенда сохранён; автоматического ввода пароля
нет. Новый клиент не останавливать до завершения проверки пользователя.


2026-11-08: по выбору пользователя Skin UI переделан в «Гардероб».
Один SkinsScreen: персонаж слева, коллекция справа, категории Skin/Cape/Outfit
через UiTabs; на узком окне общий селектор, без вложенной библиотеки плащей.
Карточки только примеряют, подтверждение в футере. Поиск с сохранением фокуса;
при фильтрации reorder скрыт. UiSkinCard поддерживает плащи, добавлен в UI Kit.
Примерка скина сохраняет текущий плащ; смена категории не меняет внешность;
удалённый из обновлённой библиотеки выбор сбрасывается к активному.
Сохранены rename/model/delete/undo/equipment/favorites и направленное колесо.
Native SkinUiHarness: загрузка, cape/outfit/undo/wheel + 81 кадров GUI1–3,
обычная/светлая/контрастная темы и остальные sampled; поиск/фокус/preview
прошли на отдельном сервере. Mod tests 9/0; strict docs 80 страниц/0 битых ссылок;
verifyBundle и проверка локализаций упакованного JAR прошли. Кандидат обновлён.
Полные adaptive RU/EN прошли: по 198 состояний на шести размерах окна.
Reorder прошёл: 135 состояний, отмена/ошибка/сохранение порядка, GUI1–3 и темы.
Дополнительно исправлена индикация одинаковых образов: applied определяется
по составу внешности, а не ID первой записи; проверено реальным UI для каждой
темы/масштаба. Финальный повтор native и mod:test/verifyBundle прошёл; кандидат
перепакован после последнего изменения, SHA256SUMS проверены.
Установленный клиент и работающий ручной стенд не изменены; GitHub CI не запускался.


2026-11-08: собственная история мира OP отменена пользователем. Используем
отдельный Indexor (CurseForge indexor-ledger, NeoForge 1.21.1). Начатый код и migration 013 удалены;
миграция запускалась только в удалённой временной тестовой БД.
Установленный клиент и запущенный профиль не изменять.

Indexor 1.0.3: реализован отдельный адаптер через Java API identity/permissions.
Чтение по UUID — публичный ILogStorage; проверки команд и отложенная запись
отменяемых событий требуют mixin-перехватов. Они ограничены SHA-256 проверенных
классов; диагностика проверяет фактическую установку mixin-маркеров. Новые версии
Indexor не активировать без проверки. Право rivet.indexor.admin отдельно от
rivet.admin, через LuckPerms и каталог ролей. Обычный OP 2 сохранён, Auth обязателен.

Исправлена ошибка предыдущего теста Copycats+: задавался Material без Item,
что не соответствует реальному заполнению. Indexor сохранял Material в журнале,
а Copycats отбрасывал неполный набор данных. Новая fixture заполняет блок реальным
useItemOn, сравнивает полный NBT после отката; исправление Indexor NBT не нужно.
Отменённые BreakEvent и EntityPlaceEvent исключаются адаптером после обработки
всех обработчиков, без отдельной БД. Проверки — IndexorHarness/IndexorFixture;
клиент использует копию установленных модов, Indexor только на тестовом сервере.
Финальный живой прогон прошёл: Auth → действия → выдача/отзыв LuckPerms без OP →
rollback → rename из консоли → штатная остановка/запуск сервера → повторный вход.
После перезапуска новый ник вернул все 9 записей UUID (4 из них со старым именем).
Отменённые break/place дали 0 записей; полный NBT Copycats совпал до/после отката.
Дополнительно проверено, что старое имя из записи не используется фильтром отката
после rename (сопоставление через UUID, с пустым кэшем имён).
Тесты используют SQLite Indexor; JSON backend и все возможные модовые блоки
вживую не проверены. Core tests, PermissionCatalogTest, verifyBundle и строгая сборка MkDocs прошли.
Установленный клиент не заменён; GitHub CI и публикация не запускались.

2026-11-08: финальная очистка кандидата: удалены неиспользуемые RowViewport,
UiTextForm и UiFormDraft, две строки локализации старой обёртки черновиков и
повторный вызов IndexorBindings.clear при остановке сервера. Рабочий DraftStore,
TextDraft, обработка старых данных и регрессионные тесты сохранены.
Полный build: 820 tests / 0 failures/errors. Вложенные JAR проверены: удалённых
классов, OP history и uiHarness нет; рабочая строка ошибки сохранения черновика
присутствует в обеих локалях. Кандидат и контрольные суммы пересозданы.
Установленный клиент и работающий ручной стенд не изменялись.

2026-11-08: релизный кандидат обновлён после добавления Indexor. Полный build:
820 Java tests без ошибок; tooling 52 tests, 1 штатный skip; strict MkDocs,
80 страниц / 0 битых ссылок. dist/2.8.0-candidate содержит актуальный JAR,
core.json, SHA256SUMS.txt и RELEASE_NOTES.md. Предыдущий SHA256SUMS удалён,
чтобы не оставлять две разные контрольные суммы. GitHub CI точного коммита
и публикация не выполнялись.

Запущен отдельный ручной стенд на 127.0.0.1:25621, игрок RivetReview (OP),
Auth base / самостоятельная регистрация. PostgreSQL контейнер
rivet-280-indexor-review на 55481 оставлен для проверки пользователя.
Сервер и клиент используют упакованный JAR кандидата и копии модов; Indexor
только на сервере. Площадка 0/100/0, Copycats 3/100/0, сундук 5/100/0.
Indexor в диагностике доступен. Установленный Prism и прежний ручной стенд
не изменены. Новый ручной стенд не останавливать до завершения проверки.

Реализация и локальная приёмка 2.8.0 завершены; итоговая матрица — в конце
ECOSYSTEM_PLAN.md. Кандидат находится в dist/2.8.0-candidate. Непроверенный
релизный gate: GitHub CI точного коммита; commit/push/publish не выполнялись.
Установленный клиент не заменять.

Актуальное решение: тестовые каналы Builder и коды доступа исключены из плана.
Остаётся одна стабильная публикация. Не реализовывать rollout и не спрашивать о нём снова.
Это не отменяет TOTP и резервные коды Auth. Живой прогон — после всех изменений;
установленный клиент не заменять.

2026-11-07 — продолжение W05/W08/W09, без живого запуска:
- HomeLayout хранит избранные действия в существующем home-layout.json; старые
  настройки сохранены, новые действия выключены по умолчанию. HomeWidgetsScreen
  использует прежние переключатели и drag handles. Порядок учитывает скрытые по
  правам действия; временная потеря доступа не удаляет выбор. Активация повторно
  проверяет MenuActions. В UiFlowHarness добавлены случаи отзыва/возврата прав.
- Кнопка Notifications в MenuSidebar теперь учитывает module availability,
  совпадая с поиском действий, а не только объявленные capabilities.
- MenuPlaces добавляет в общий поиск загруженные личные/публичные метки и доступные
  публичные места. Личные координаты не включаются в запрос globalSearch; результаты
  содержат только ID/название/источник. Открытие заново разрешает ID по актуальному
  snapshot и центрирует карту. Локальная выдача доступна сразу, до серверного debounce.
  Полная библиотека всех мест и адаптеров пока НЕ завершена.
- Chat: выбор канала кликом по индикатору или Ctrl+Tab через общий ChoicePopup.
  Esc сохраняет черновик, выбор не отправляет сообщение. Недоступные каналы и группы
  исключаются; перед применением проверяются повторно. ChatChannels.draft не меняет
  команды, не обрезает текст и не превращает префикс тела в другой канал/команду.
  Ui-сценарий добавлен в RivetChatIdentityHarness, но живой запуск отложен.
- Исправлены выявленные при компиляции ошибки тестового импорта и недоступного
  getter EditBox; используются ограничения ванильного чат-пакета. Новые ключи
  локализации сверены с сгенерированными ресурсами RU/EN.
- Strict docs, targeted ChatChannelsTest и окончательная compile main/UI прошли.
  Полный core:test: 756/756; mod:test: 5/5, без failures/errors/skips, принудительное
  выполнение. RU/EN сгенерированные ресурсы совпадают с исходниками. Живые сценарии
  не запускались; они только скомпилированы. PostgreSQL не повторялся: схемы/SQL в
  этом пакете не менялись. Удалённый CI не проверен.
- Общий план не завершён: плащи/образы, Builder rollback, оставшиеся
  Menu/Map/Chat/OP и архитектурная очистка, затем общий live/CI/release. Клиент не заменён.

2026-11-07 — W02 PlayerDirectory / W10 отмена внешности и примерка:
- Общий PlayerDirectory вынесен из server.compat; принадлежит ServerProviders,
  используется Identity Java API адаптеров и ServerSkins. Auth on разрешает только
  зарегистрированную identity; Auth off использует онлайн-профиль/локальный кэш.
  Исправлен lookup по вычисленному offline UUID: при лицензионном UUID он терял
  офлайн-игрока. CachedProfiles + CachedProfileAccessor читают уже загруженный
  name-cache без сети/создания аккаунта. ApiServerHarness дополнен Auth-off случаем
  с произвольным UUID, поиском без учёта регистра и отсутствующим игроком.
- Миграция 012 добавляет undo_active/undo_slim/undo_available. SkinStore сохраняет
  предыдущий выбор/модель рук; undo один раз, по владельцу, с RequestJournal.
  No-op выбора не затирает отмену. Удаление текущего/предыдущего элемента снимает
  отмену; rename/reorder сохраняют её. Повтор upload того же PNG сохраняет прежнюю
  модель. Новая capability skin-undo; кнопка в существующих действиях библиотеки.
- Скин Rivet больше не теряет официальный capeTexture/elytraTexture при построении
  PlayerSkin; сохранение добавлено и в UiSkinCard/предпросмотр PNG. Загрузка своих
  плащей и образы пока НЕ реализованы — это не закрытие W10.
- Примерка экипировки: без вещей / копия текущих / броня / броня и элитры. Меняется
  только RemotePlayer предпросмотра. Доступно до отправки PNG, не выполняет серверных
  команд. SkinUiHarness дополнен реальным UI выбором, неизменностью инвентаря,
  undo через кнопку, путями отрисовки и сохранением официальных текстур.
- PostgreSQL SkinStoreTest + DatabaseReliabilityTest: 21/21. Первое падение миграции
  было в downgrade fixtures: удаляли запись версии, но оставляли новые столбцы;
  исправлен откат тестовой схемы, production migration не ослаблена.
  Полные core:test 755/755 и mod:test 5/5 прошли; tooling 52, один штатный skip;
  strict docs и compile main/UI после добавления примерки прошли. Native сценарии только скомпилированы, не запускались.
- W05: MenuActions использует тот же набор доступных разделов, что MenuSidebar,
  добавляет уведомления/скины по согласованным возможностям. Поиск объединяет
  локальные переходы с серверными результатами; локальные действия остаются при
  ошибке/тайм-ауте. При нажатии доступ проверяется снова. UiFlowHarness включает
  обычного игрока/администратора/отзыв права. Избранное и единый поиск всех видов
  мест остаются в плане.
- После расширения примерки/поиска compile main/UI, strict docs, diff --check и
  tooling (52, 1 штатный skip) прошли. Полный PostgreSQL выявил ещё два downgrade
  fixture с оставленными undo-столбцами (CommunityPlusTest/NotificationExpiryTest);
  исправлена подготовка старой схемы. Финальный полный повтор прошёл: 304 total, 0 failures/errors, 5 штатных skips.
- Клиент не заменён. Общий план, live-проверки, релиз и удалённый CI не завершены.

2026-11-07 — W02/W03 доставка, W04 очистка, W05 поиск, W06 пауза, W10 HUD:
- ServerClientDelivery владеет подписками окон и транспортом/локализацией; DataRuntime
  открывает/закрывает сессии и очищает подписки. ServerChangeDelivery больше не
  вызывает ServerFeatures напрямую. Новая capability notification-state доставляет
  только unread/popupSequence; старым клиентам сохранён full-state fallback.
  Меню обновляет счётчик без пересоздания окна; realtime harness проверяет получение
  с закрытым GUI. Модуль changes зависит от data/notifications.
- Убраны legacy HUD task/event, TAB event footer, stockView/tasksDelta на сервере,
  retired wire capabilities. Generic CommunityUiHarness очищен от данных задач,
  событий и опросов; отдельные архивные regression fixtures сохранены.
- CommunitySearch ограничивает выдачу и добавляет названия внутренних меток групп:
  только текущим участникам/администраторам, без координат, только открытые группы.
  Выбор открывает вкладку меток. PG проверяет outsider/join/leave/delete/admin.
  GlobalSearchScreen сразу отменяет запрос при редактировании, не принимает старый
  ответ во время debounce/после очистки; UiFlowHarness расширен соответствующим случаем.
- Builder: PackPausedScreen ждёт освобождения worker перед продолжением; новый Flow
  заново проверяет публикацию/обзор. Сохраняются проверенные файлы и прерванные
  source.part с сильным ETag; hash/size проверяются перед использованием. Устаревшие
  source.partial/etag удаляются через 7 дней. Перед startHelper атомарно закрывается
  UI cancel gate; Hub overload поддерживает этот переход и для core-only обновления.
  Отмена на handoff удаляет ready transaction, сохраняя загрузку и установленный JAR.
- HUD: минимальный/обычный/подробный пресеты меняют состав и поля профиля, сохраняя
  размещение/масштаб/порядок/настройки уведомлений. Старые preferences при чтении
  сохраняют явный hidden. Пресеты добавлены в настройки/поиск/SettingsReworkHarness.
- Проверки: полный core:test и mod:test прошли до последних изменений Builder;
  PostgreSQL CommunityStoreTest 34/34; затем PackDownloadFallbackTest 6/6,
  CacheCleanupTest 5/5, CoreUpdaterTest с новой отменой прошли принудительно.
  Tooling 52 (0 failures, 1 штатный skip), strict docs успешно.
  Compile main/UI после очистки fixtures прошла; diff --check прошёл. Первое добавление
  UI теста имело конфликт локального имени field; исправлено на clearInput.
- Продолжение Builder сохраняет выбранные компоненты для той же публикации (hash);
  при изменении публикации используется новый состав и обязательный обзор.
- Minecraft не запускался, клиент не заменён. W02/03 остаются незавершёнными
  (PlayerDirectory/границы владения); W05 поиск мест/действий/избранное не завершён;
  W06 rollback и live matrix впереди (rollout исключён); W10 плащи/образы ещё не реализованы.
  Общий план и релиз НЕ завершены, удалённый CI не проверен.

2026-11-07 — W04 удаление экранов / W02 владелец данных (продолжение):
- Удалены TaskScreen, TaskResourcesScreen, TaskArchiveScreen, TaskMemberPicker,
  TaskPlanning, TaskStockSignScreen, UiTaskLayout, StockHologramRenderer,
  FollowingScreen и MemberPickerScreen. Общая форма TaskEditScreen стала
  UiRecordForm; её используют объявления и отчёты. CommunityForm очищена от
  исполнителей/приглашений, повторений, торговли и полей удалённых механик.
- Native/Adaptive/Next/Community/CommunityPlus/Dialog/Pilot/RoleFlow/RoleNetwork/
  MapActivities/Remaining/SettingsRework и geometry harness перенесены на Groups,
  внутренние метки, уведомления, общий редактор и FeatureListScreen.pick.
  Сохранены проверки фокуса, resize, scroll, возврата, неизменного ответа,
  pending mutation, выбора UUID, отключения модуля и старых уведомлений.
  Удалены исключительно визуальные stages удалённых складских голограмм.
- Убраны переключатель складских голограмм, task pin и параметры запроса HUD,
  TASKS/EVENTS layers, фильтры удалённых категорий в настройках уведомлений.
  Старые неизвестные значения HUD JSON сохраняются при записи. Sidebar больше
  не содержит пустого configure и исключений tasks. NotificationPopup защищён
  от перехода в retired section; архивные уведомления остаются читаемыми.
- ServerDataRuntime владеет CommunityStore, ServerExtras, ServerWorkQueues и
  PlayerSessions. Login HIGHEST открывает сессию, logout LOWEST закрывает только
  соответствующее соединение после очистки Features. Модуль data зависит от
  notifications: очереди завершаются до остановки publisher и до ServerExtras.
  ServerFeatures сохраняет фасады; ServerChangeDelivery читает сессии и очередь
  непосредственно у нового владельца. Полная независимость delivery от Menu
  (subscription/sendState) ещё не завершена, W02/W03 не закрывать.
- Проверки: core:test 751/751, mod:test 5/5; tooling 52, 0 failures, 1 штатный skip;
  strict docs успешно. Последняя compile main/UI после очистки форм успешна.
  Tooling сообщения о publish/native screenshots — fixtures unittest, не реальный
  publish/live. Minecraft не запускался, установленный клиент не заменён.
- Остаются: финальная ревизия stale harness fixtures (старые
  поля generic CommunityUiHarness; groupItems task уже заменён меткой), конфликты/черновики и captions после миграции,
  продолжение W02/W03, W05–W11. Общий план и релиз НЕ завершены.

2026-11-07 — W04 очистка интерфейса и прав (продолжение):
- Удалены BoardSection, EventsSection, PollsSection, IdeasSection. Карточка
  объединения оставляет обзор, участников, заявки и внутренние метки; формы
  меток не содержат исполнителей, сроков и статусов задач.
- CommunityScreen очищен от создания/редактирования удалённых механик,
  профиля задач/организаторов, карточек событий на главной и состояния голосования.
  Старые названия источников оставлены для читаемости сохранённых уведомлений.
- Старый список уведомлений открывает NotificationPopup по ID: единый серверный
  noticeOpen/noticeNavigate вместо перехода по сохранённому target.
- rivet.events исключён из PermissionCatalog и preview ролей; Actor больше
  не читает удалённую capability. Записи LuckPerms не изменены. Документация
  прав и REST объединений очищена, объявления сохранены.
- CommunityPolicy предлагает действия только Groups, включая отказ владельцу/
  администратору для архивных механик. Добавлены regression tests для отказа,
  полномочий помощника и отзыва заявки без повторной подачи.
- Проверки после изменения CommunityPolicy: core:test 751/751 passed;
  PostgreSQL CommunityStoreTest 33/33 passed; main/UI harness компилируется;
  strict docs и diff check прошли. Полный PostgreSQL до этой очистки —
  300 total / 0 failures / 5 штатных skips; после неё повторён затронутый класс.
- Native harness ещё содержит удалённые сценарии и требует миграции; его
  компиляция НЕ означает успешный live-прогон. W04 и общий план не завершены.
  Minecraft не запускался, установленный клиент не изменён.

2026-11-07 — итог текущей проверки W04:
- Полный core:postgresTest: 300 total / 0 failures / 0 errors / 5 штатных skips.
  Все прежние 38 legacy failures устранены с переносом generic guarantees на
  Groups и отдельной проверкой неизменности retired task/event данных.
- core:test ранее этого же этапа: 747 passed. mod:test: 5 passed;
  compileJava и compileUiHarnessJava успешны. Native client НЕ запускался.
- Tooling: 52 tests, 0 failures/errors, 1 штатный skip. Системный Python не имел
  jsonschema и sandbox запрещал local TCP; итоговый прогон выполнен штатным
  .venv-docs с jsonschema и разрешением local sockets. Реальные localization gaps
  исправлены: core TOTP messages синхронизированы в обоих Minecraft catalogs,
  добавлен menu.home.empty. OpenAPI retired 503 имеет Error schema; contract test
  требует deprecated + отсутствие 2xx только для явных retired domain prefixes.
- Strict docs и git diff --check успешны. Ресурсы принудительно пересобраны
  processResources; OpenAPI и оба каталога переводов совпадают source/build.
- Остаются: полное удаление dead UI и миграция native harness W04; независимый
  lifecycle Notifications и оставшиеся пакеты W05–W11. Это не завершённый план,
  не готовый релиз и не подтверждённый CI. Установленный клиент не заменён.

2026-11-07 — W04 API regression migration завершена по классам:
- CommunityStoreTest: 33/33 passed. Generic retry/CAS/ownership/rollback/ordering/
  paging/trash contracts перенесены на Groups; private locations — group markers;
  archived non-group row явно проверяется как запрещённый chat channel.
- DomainRestTest: все 62 сценария прошли внутри полного PG. Generic contracts
  перенесены на active Groups/profiles/notifications/markers; retired task/event
  сценарии теперь проверяют 503 UNAVAILABLE и неизменность persisted bodies/
  revisions, включая subtask/resources/assignment/due/comment и privileged recurrence.
- Найден и исправлен hasInvitation pagination bug; backend flag computed before
  slicing, client fallback поддерживает предыдущие server payloads. Targeted 2
  pagination/trash tests и compileJava/compileUiHarnessJava прошли.
- Полный PG: 300 total, 2 failed, 5 skipped. Оба оставшихся failure —
  CommunityQueryRelationsTest legacy event/participation fixtures. Исправлены:
  supported group relations проверяются, retired relations явно UNAVAILABLE;
  equal creation times stable cursor проверяется с правильным ASC/DESC порядком.
  Полный повтор PG + mod:test/compileJava/UI compilation сейчас запущен;
  итог обязательно проверить, не считать завершённым по этой записи.
- Client openCommunity/HUD/map stale routes теперь не открывают retired screens;
  MapActivities запрашивает только groupmarkers. Полное удаление dead UI классов
  и перенос native harness ещё остаются в W04. Live не запускался.
- Strict docs и diff check прошли. Общий план W05–W11 не завершён; release/CI нет.

2026-11-07 — W04 продолжение: новый шаблон конфигурации больше не содержит
[board/events/polls/ideas/storage/tasks]; опубликованные старые секции читаются,
сохраняются с комментариями, но не включают retired-функции. Config-upgrade tests
проверяют вставку реального нового totp-поля, backup и повторное применение.
ServerSettingsTest/FeatureModulesTest/EcosystemUpgradeTest проходят.
CommunityApiCommands очищен от недостижимых task/event/board/poll/idea обработчиков;
ранний RetiredFeatures guard и scope совместимости сохранены.
Старые generic regression fixtures переносятся по одному на Groups, без удаления
проверок retry/CAS/ownership/rollback/cursors/queue cancellation. Последний полный
запуск двух классов CommunityStoreTest+DomainRestTest: 95 tests / 25 failures
(до последних пяти миграций и двух command-tests). Это НЕ зелёный общий PG.
После очистки API: core:test 747 passed, targeted PG 13 passed; strict docs passed.
Дополнительно targeted cursor/expired/queue 5 passed и REST command security 2 passed.
Найден UX-баг: приглашение пропадало на второй странице группы. Добавлен
hasInvitation до slice, GroupsSection использует его с fallback для старого сервера.
Проверки pagination/restore и compileUiHarness идут отдельно; итог записать после.
Живой Minecraft не запускать до всего плана; установленный клиент не заменён.
W04 и W05–W11 остаются незавершёнными. CI/publish не запускались.


Последняя проверка W04: targeted PostgreSQL 16 tests, 0 failures, 0 errors, 0 skipped — успешна. Включает квоту 200 архивных задач + 200 разрешённых меток и отказ 201-й, group places/read/visibility и сохранность архива. README, навигация руководства, Java/WS примеры и архитектурная таблица обновлены; strict docs успешен. Общий PG всё ещё требует устранения 38 старых сценариев с сохранением контрактного покрытия.

2026-11-07 — продолжение W04, TOTP/API перепроверены; план НЕ завершён:
- CommunityQueries читает только поддерживаемые объединения; home больше не читает
  старые upcoming events даже из прежней конфигурации. Сохранены keyset cursor,
  snapshot ceiling, private trash, mine/member/participating filters. Добавлены
  PostgreSQL consumer-регрессии на архив событий и фильтры групп.
- CommunityPlus убирает неиспользуемые запросы workload/tasks/nextEvent. Лимит
  group markers теперь считает только kind=place: архивные задачи не блокируют
  добавление меток. Регрессия 200 old tasks + 199 markers → 200th marker allowed,
  201st denied, архив не меняется; результат проверки ниже/в следующей записи.
- Клиентские module/navigation guards отвергают retired IDs даже при старом
  server state. Главная оставляет объявления/группы и moderation votes.
- Обновлены player menu, REST и Java domain docs; 67 старых OpenAPI операций
  marked deprecated/503 UNAVAILABLE без успешных ответов. Java task/event methods
  сохранены для binary compatibility, помечены deprecated. Новый config template
  и оставшиеся dead UI/API branches пока ещё требуют очистки.
- Полный PG завершился: 297 total / 38 failures / 5 opt-in skips. Причины в
  CommunityStoreTest и DomainRestTest — старые fixtures удаляемых механик; это
  НЕ зелёный прогон. BrowserTickets теперь не зависает. Не удалять/ослаблять
  общие контракты, переносить только с подтверждённым сохранением покрытия.
- Авто-проверка ранее отклонила массовую перепись старых тестов. Та команда НЕ
  выполнена. Вместо неё добавлен отдельный GroupCommandContractTest; новые
  проверки retry/CAS/privacy/rollback/cursor/delete-restore прошли. Старые
  generic tests пока сохранены и требуют отдельного review/migration.
- Targeted PG 20 passed (AuthFactorStore, NotificationRest, GroupCommandContract,
  RetiredFeatures, CommunityReadPerformance). После API/client/docs изменений:
  core:test 747 passed; mod:test, compileUiHarnessJava, checkModuleComposition passed;
  strict docs и diff check прошли. Последняя CommunityPlus правка проверяется отдельно.
- Minecraft не запускался, установленный клиент не заменялся, CI/publish не запускались.
  Живой прогон только после всего плана; W04/W05–W11 ещё не завершены.

2026-11-07 — продолжение W07/W03 и начало W04 (НЕ релизное состояние):
- Application sessions: AuthApplicationsScreen + own-session listing/cursor/revoke by
  management UUID; no token exposure; revoked keys/generation filtered. DomainRestTest
  adds foreign isolation, 50+10 pagination and revocation. Auth account actions scroll.
- AuthFactorScreen now scrolls fields on small GUI, preserves entered values between
  scroll/resize, clears on close/send. Correlates request UUID including errors so stale
  replies cannot populate another modal. AuthUiHarness layout-only fixtures compiled,
  wired into AdaptiveUiHarness for six sizes; NOT run live per user instruction.
- ChangeOutbox extracted from CommunityOutbox; notification summary owned by
  ServerNotifications. Lifecycle epoch rejects old completions. Delivery still uses
  social/control transport, not a finished fully independent Notifications module.
- W04 RetiredFeatures now rejects tasks/storage/board/events/polls/ideas even with old
  enabled=true config. Old notices stay readable without navigation. No data migration
  drops/deletes those rows. RetiredFeaturesPostgresTest verifies saved documents survive
  reminders/purge; FeatureModulesPostgresTest verifies old group tasks cannot be accessed
  through plusItemRead/Save/Delete and recurrence remains untouched.
- Removed CommunityTasks, TaskArchive, StockHolograms, ServerTaskStocks, TaskStockWork,
  TaskSignMixin and retired stock scan/completion fixtures; removed background task/event/
  poll work, removed board/event/poll/idea mutation branches from CommunityStore and
  creation/repetition helpers. Remaining UI, queries, config, docs and API contracts
  cleanup is unfinished. No claim that W04 is complete.
- Checks: core:test 747 passed before latest physical removals; targeted PostgreSQL
  retired/navigation tests passed; next retired/group-item suite 4 passed; harness compile
  passed. Full PostgreSQL after retirement stopped at 224 tests (53 expected legacy
  behavior failures, 6 skipped) when BrowserTicketsTest hung closing HttpClient before
  server after a now-retired events subscription assertion. Stopped only that test JVM.
  Fixed resource close order and ported browser subscription to groups (not yet rerun).
  Ported CommunityReadPerformanceTest to groups and PostgresConcurrencyTest to 300
  group applications + idempotent retries (not yet rerun). Other 53 old tests still need
  careful migration/removal of retired behavior while preserving shared contract tests.
- Current remaining important work: W04 legacy tests/API/docs/UI, all later W05–W11
  items, full live pass only AFTER all changes. Installed client untouched. No push/release.


2026-11-07 — W03 external publishing + W07 TOTP (реализовано, live ещё отложен):
- NotificationRest: PUT/DELETE /api/v1/notifications/{type}/{recipient}/{key}, scopes
  notifications.publish/revoke, immutable application ID ownership (rotation survives),
  no player session/read acknowledgement; rejects browser Origin, foreign namespace,
  arbitrary actions. Revalidation under api_keys row lock inside publication transaction.
  Bounded worker/owner type quotas retained. Last update retry persists previous revision
  to return same receipt after lost reply/restart; actual endpoint tests cover this.
- Auth: Totp RFC6238 SHA1/6/30 with vectors, replay step; AuthFactorVault AES-GCM
  bound to UUID/purpose, local private auth-factors.key; migration 011 stores encrypted
  pending/active secret and hashed 128-bit one-time backup codes (10). Enrollment
  expires in 10m. Confirm, rotate/remove/reset bump generation/revoke sessions/devices.
  Existing password rate limits apply; reset password keeps factor; rename keeps UUID.
- AuthStore password/API/device/official login all require configured factor. ServerRest
  reuses AuthServer store/vault (does not accidentally construct a vault-less verifier).
  auth.totp optional/staff/all; staff=OP or permanent LP admin/manage/reset/restart/
  maintenance/pack. Existing validSessions batches factor state; policy rechecked for
  browser sessions and game validation. Console-only rivet auth reset-totp records audit.
- AuthScreen accepts factor; AuthFactorScreen modal setup/confirm/copy codes/rotate/remove,
  available before joining for required enrollment and under account security. Existing
  link/unlink/password change accept code. No arbitrary secret logging/client persistence.
  Native UI/TLS/privileged role scenarios MUST still be exercised at final live pass.
- Fixed old validation snapshot disconnecting freshly refreshed own session (generation
  and device recheck); config adds auth.totp while preserving published settings/comments.
- Realtime tick moved out of ServerNotifications into own changes runtime lifecycle;
  late old callbacks reject stopped epoch. Delivery still uses social store/control lane:
  full Notifications independence and W04+ general plan remain unfinished.
- Checks: full core:test 747 passed after fixing old-config fixture failure; full PostgreSQL
  347 total/7 opt-in skipped (340 executed) passed, then expanded AuthFactorStore tests
  passed separately; compileUiHarnessJava passed. Notification retry/lifecycle final
  targeted run pending at time of note. Strict docs/diff passed earlier; rerun after edits.
- No Minecraft launch, installed client replacement, remote CI, commit/push/release.


2026-11-07 — продолжение W03/W09/W08 (не завершение общего плана):
- Notifications: категории источников mod:type, единое mute/filter/HUD; legacy
  notifications-wide mute сохранён. Каталог ограничен собственными записями и
  сохранёнными предпочтениями. OpenOwnPublication проверяет владельца и состояние;
  native delivery повторно проверяет map.enabled/capability. Объявления с пустым
  routeId открывают публичную помощь сервера, не администрирование.
- Изменения отдельных настроек в игре используют preferencePatch под той же
  блокировкой preferences:<UUID>; не затирают настройки сайта/другого окна.
  notificationPreferencePatch в state согласует поддержку; старому серверу
  отправляется прежний полный снимок. Проверки REST/Java revision сохранены.
- ServerNotifications owns publisher, bounded maintenance expiry and delivery tick.
  Доставка ещё зависит от social store/control queue: полная независимость не готова.
- B&C: новый ChatEffectEnvelope только на теле local/group/API/marker-share;
  BrewinChannelEffects вызывает проверенный API 4.5.0 и восстанавливает временный
  tipsyMessageLevel. Не потребляет nextTipsyMessage/randomSeed; сохраняет стиль
  пустых контейнеров, клики/имя/канал. История снимает metadata без эффекта.
  ApiChat.Envelope отделяет body/author/application, читает старые pending записи.
  Global signed chat не переделан. Async group errors проверяют текущее подключение.
- Map: MapConnectionPolicy/ClientMapPolicy — immutable snapshot UUID и разрешений
  вне ServerMenuClient.state. Смена подключения сбрасывает политику; malformed
  policy fail-closed. Protocol отбрасывает featureState от старого соединения.
  WorldMapClient/radar/positions/settings/direction читают нового владельца.
  Native fixtures явно подают изменения политики; MapModuleHarness проверяет
  сохранение repository при замене меню. Живые проверки пока отложены пользователем.
- Checks: полный core:test 744 passed; full PostgreSQL 336 total, 7 штатных opt-in
  skips (329 executed), затем адресные navigation/category PostgreSQL успешны;
  compileUiHarnessJava успешен, strict docs и diff check успешны. Последнее
  уточнение radius/malformed policy + direction recompilation успешно проверено.
  Важно: compileJava -PrivetUiHarness сам по себе НЕ компилирует uiHarness;
  для проверки использован именно compileUiHarnessJava. Первые два пропущенных
  конструктора CommunityPreferences в harness найдены и исправлены.
- Дополнительно: legacy routeSection=server/spark классифицируется одинаково в
  SQL/list/HUD/mute без переписывания истории; старый home/notifications mute
  сохранён. external event допускает обычные всплытия; локальный source mute
  проверяется вместе с legacy category mute. PG категории успешны, HudUiHarness
  дополнен реальным consumer receive/queue сценарием (финальный live отложен).
- Весь план остаётся незавершённым, W04 removal ещё не начат. Пользователь подтвердил
  оба решения: Auth TOTP + одноразовые резервные коды; внешние Notifications —
  собственные типы/записи, получатели UUID, отдельные права API-ключа, прочтение
  только игроком. Эти расширения ещё не реализованы. Установленный клиент не
  заменён, Minecraft/CI не запущены.

2026-11-07 — W03 локализованные подписки и владелец publisher:
- DomainWatch.Notifications(locale), сохранён no-arg constructor с fallback;
  DomainWatch.NotificationPreferences читает настройки через существующий reader.
- PostgreSQL consumer-тест: две локали, update/revoke, повторная подписка с новым
  stream, изменения настроек через REST; WS cursors разных локалей не смешиваются.
- JavaWatches ловит сбой revision supplier на уровне конкретной подписки: Closed
  UNAVAILABLE вместо прекращения scheduled timer для всех подписок. Recovery
  и старый constructor проверены unit-тестами.
- ServerNotifications владеет NotificationPublisher как runtime notifications
  после base. social/api зависят от notifications; API только выдаёт owner handles.
  Обратная остановка: API, social, затем publisher. Это ещё не полное выделение
  доставки/настроек/expiry из social и не новый выключатель модуля.
- Проверки: 741 unit passed, 3 целевых PostgreSQL consumer tests passed, mod compile,
  docs strict/diff check. После runtime extraction повторён mod compile.
  Native/remote CI не запускались; клиент не заменялся.
- Далее W03: действия публикаций; категории/настройки по источникам; полный
  независимый lifecycle доставки и очистки; затем W04 и остальные пакеты плана.

2026-11-07 — W03 единое открытие истории и HUD:
- Все noticeOpen читают owned/unexpired запись, включая legacy. Popup больше
  не открывает target из старого client cache. HUD открывает тот же popup по ID;
  отметка read происходит после успешного серверного чтения, не до него.
- Legacy история показывается текстом; отдельный переход проверяется сервером.
  groups/board/events/polls/ideas используют sharedReference, tasks — task access
  и актуальную группу; help — собственное обращение; spark — server/admin.
  Неизвестные маршруты не исполняются, текст остаётся доступным.
- OpenReport(UUID) добавлен к sealed Java Actions, проверяет принадлежность
  обращения и включённый reports. Не открывает staff queue. OpenGroup сохранён.
- Старые quick actions используют NotificationStore.owned вместо отдельного SQL;
  существующие приглашения и проверки конфликтов не изменены.
- Проверки: 739 unit passed, 22 PostgreSQL passed (navigation, quick actions,
  notification store), mod compile/docs strict/diff check успешны. Новые fixtures
  сначала вызывали append вне транзакции; исправлены и повторно проверены.
- Native/remote CI не запускались, установленный клиент не менялся.
  Живой прогон — после всех изменений. W03 ещё не завершён: действия публикаций,
  локализованные watch, независимый runtime и категории/настройки остаются;
  REST/Java invocation действий пока нет. Не добавлять новые API для удаляемых
  events/tasks: текущие legacy-ссылки поддерживаются до W04.

2026-11-07 — W03 typed navigation: Action/OpenGroup(UUID), owner message revision
и дедупликация действия. noticeOpen возвращает свежую запись вместо client cache;
noticeNavigate проверяет получателя/TTL/revision и доступ к stored group через
apiRead. Без команд/URL/автовступления. UI использует TextScreen+UiActions;
при ошибке retry обновляет список. Нет отдельного REST/Java invocation действия.
Остальные typed actions, legacy bridge, localized watch, самостоятельный runtime
и UX настроек ещё в плане. Живые проверки только после всех изменений.
Проверки: 739 unit passed; полный PostgreSQL 330 total — 322 passed, 7 opt-in
skipped, один новый тест сначала ошибался, читая members из JSON вместо relations.
После исправления fixture-assertion оба NotificationNavigationTest прошли;
финальная mod compile, docs strict, diff check успешны. Native/CI не запускались.

2026-11-07 — W03 TTL: Message.expiresAt, миграция 010 (expires_at + partial index),
общая фильтрация всех читателей уведомлений до пагинации/счётчиков, cleanup256
через pendingChanges с outbox в той же транзакции. Старые записи не истекают.
HUD deadline действует при паузе и defer; serverTime защищает от разных часов.
Конструкторы API сохранены; изменённый TTL требует ревизию. План/Java docs обновлены.
Исправлен HUD cursor при просроченном хвосте (нет пустого more-loop).
Проверки: 739 unit passed; полный PostgreSQL 321 passed / 7 opt-in skipped;
после последней правки курсора повторно прошли NotificationExpiry/CommunityHud.
mod compile, docs strict, diff check успешны. Игру/CI не запускали, клиент не меняли.

2026-11-07 — W03 локализация: NotificationsApi.Message получает immutable
translations (до 8), прежний конструктор сохранён. Варианты сохраняются, участвуют
в дедупликации, локализуются в копии игрового ответа и не пересылаются целиком.
REST locale и Java notifications(cursor, locale) используют общий reader;
без locale возвращается прежний fallback. OpenAPI/docs обновлены.
TTL, typed actions, локализованные Java watch и standalone lifecycle остаются.
Проверки: 738 unit и 318 PostgreSQL прошли, 7 opt-in load пропущены;
mod compile, docs strict, diff check успешны. Minecraft/remote CI не запускались.
Установленный клиент не менялся.

2026-11-07 — W03 Java Notifications: NotificationsApi/NotificationPublisher,
owner/type/key/UUID scope, bounded async queue, регистрация типов, put с ревизией,
идемпотентный create пока запись сохранена, revoke. История общая с меню/DomainApi;
read сохраняется при update. Ревизии из persistent notices sequence защищают
удаление/повторное создание от старых операций. Нет новой миграции/изменений данных
production. ServerApi владеет lifecycle. TextScreen показывает буквальный текст
внешнего уведомления после клика. Типы пока без TTL, локализованных вариантов
и действий: эти части W03 и самостоятельный runtime Notifications ещё предстоят.
Проверки: 737 core-тестов без пропусков; PostgreSQL 317 прошли, 7 opt-in load
пропущены; mod compile, docs strict и diff check успешны. Игру не запускали,
установленный клиент не заменяли; CI и живой UX этого пакета не проверены.

2026-11-07: пользователь перенёс ВСЕ живые игровые прогоны на этап после всех
изменений, перед подготовкой релиза. До этого выполнять автоматические проверки,
не запускать тестовый или установленный Minecraft.

W02/W03 продолжение: MenuSubscriptions владеет negotiation и leases конкретной
сессии; закрытие окна не сбрасывает negotiation, reconnect не наследует подписку.
ServerChangeDelivery выделен из ServerFeatures: outbox, счётчики, пакетная
доставка, retry/ack и HUD вне открытого меню. NotificationRefresh защищает запрос
пересверки после reconnect от позднего завершения предыдущего чтения.
Lifecycle пока вызывается из social; owner-scoped Notifications API описан ниже.
Проверка Auth: same-connection logout/relogin отсутствует; revoke синхронно сбрасывает
joined и отключает игрока, поэтому отдельный Auth epoch сейчас не требуется. Данные и установленный клиент не менялись.
Проверки этого пакета: 735 core-тестов прошли, пропусков нет; mod compile и
docs strict успешны. Игру не запускали, удалённый CI не проверяли.

2026-11-07: W02 PlayerSessions подключён к login/logout/currentPlayer и
realtime delivery ServerFeatures. Снимок сессий отбрасывает результат по старому
подключению после reconnect, условный logout не удаляет новое. Время входа
сохранено вместо joined. Auth/UUID не изменены, проверки прав остаются отдельно.
Добавлены 4 теста сессий. Живой прогон realtime/notifications заблокирован
GLFW без монитора до загрузки клиента Rivet; сервер дошёл до Done, затем
остановлен. Teardown также выдал H2 classloading error; см. ECOSYSTEM_PLAN.
Клиент Prism не заменялся. Не объявлять игровой reconnect проверенным.
Полный core-прогон: 730 тестов прошли без пропусков; native prepare скомпилировал
mod, docs strict успешен. CI не запускался. Следующее: Auth epoch и подписки,
затем перенос runtime-доставки Notifications; игровые проверки повторить с дисплеем.

2026-11-07, W02: очереди ServerFeatures выделены в ServerWorkQueues, лимиты и
локализация сохранены. Владение ExecutorService скрыто за execute/submit/queued/stop.
При остановке поздняя отправка отклоняется вместо NPE. WorkerShutdown теперь
отменяет ожидающие Future из shutdownNow — без этого результат не завершался.
Тесты покрывают перегрузку WRITE с доступными READ/CONTROL, forced cancellation,
новый lifetime и rollback частичного старта. Сессии и отдельный runtime-модуль
очередей пока не выделены. Рабочие данные и установленный клиент не менялись.
Полный core-прогон: 726 тестов успешно, без пропусков; компиляция mod и docs
strict успешны. Живой игровой lifecycle и удалённый CI этого пакета не запускались.

2026-11-07, продолжение: начато выделение NotificationStore из CommunityStore
(append/лимит200/read/preferences/пагинация/сводка). Существующий фасад и
таблицы сохранены, внешние контракты не менялись. Повторный read не создаёт
outbox; quick actions используют общий путь прочтения. Архив дополнен
уведомлениями групповых задач через routeSection=tasks (14 fixture-записей).
Notifications ещё не отдельный runtime-модуль: типы, owner API и доставка
предстоят. План W00–W04 не объявлять завершённым; клиент не заменялся.
Проверки: 723 unit + 314 PostgreSQL успешны, 7 opt-in load пропущены;
mod compile и docs strict успешны. Архив: 5 тестов успешны с реальной БД.
Итоговая базовая сводка 100 UUID/20000 notices: p95 10.289 мс, адресная
сводка одного UUID p95 0.828 мс. Это не игровой онлайн и не before/after.
Удалённый CI не запускался; см. ECOSYSTEM_PLAN для следующих шагов.

2026-11-07: пользователь поручил реализацию ECOSYSTEM_PLAN. Начаты W00/W01:
fixture конфига опубликованной 2.7.0 + regression обновления, консольный
read-only export-features в существующем database_backup.py. Данные не удаляются;
экспорт JSONL не заменяет полный backup и не содержит Auth/API secrets.
39 целевых Java-тестов и 51 tooling unit-тест прошли. Отдельный живой тест
PostgreSQL проверил 13 записей, сохранность исходных данных и исключение
объединений/меток/объявлений. Добавлен в CI PostgreSQL 17/18, удалённый CI
не проверен. Оставшиеся условия W00/W01 перечислены в плане; разделение
Notifications и удаление функций ещё не выполнены. Клиент не заменялся.

2026-11-07: выполнен статический архитектурный аудит состава a211445; в
ECOSYSTEM_PLAN.md добавлены A01–A18 с привязкой к коду и пакеты W00–W11,
зависимости, критерии приёмки, миграции и матрица проверок. Auth устройства/отзыв,
транзакции Builder, realtime и API уведомлений уже существуют: переиспользовать.
Критичные границы удаления — общие documents/records/notices/outbox, строгий
ServerSettings, public API и task UI fixtures. 47 tooling unit-тестов прошли;
Java/БД/игра/нагрузка в этом аудите не запускались. Код продукта не изменён.
Начало реализации после поручения: W00/W01, сохранность данных и контракты.

2026-11-07: в ECOSYSTEM_PLAN.md добавлен согласованный модуль Notifications
с Java/REST/WebSocket API. Существующие API уведомлений сохраняются и развиваются;
Menu/HUD — представления. Дополнительные архитектурные рекомендации записаны
отдельно как кандидаты, ещё не согласованы. Продуктовый код не менялся.

2026-11-07: согласован [план упрощения экосистемы](ECOSYSTEM_PLAN.md).
Основные модули: Builder/Auth/Map/Chat/Skin/Menu/OP/HUD/API/Adapter; объединения
сохраняем и упрощаем. Убираем задачи/склады, события со всеми очередями и
повторениями, голосования сообщества, предложения и торговую доску. Объявления
администрации остаются. Серверный рендер принадлежит Map, доступ — API.
Этот план приоритетнее старых планов удаляемых механик. Только планирование,
код и данные не изменены; перед реализацией определить сохранение данных и миграции.

2026-11-05: в USERFLOW_AND_RENAME_PLAN.md добавлена согласованная поддержка
статичных PNG-плащей в модуле скинов: загрузка/выбор/скрытие, UUID, realtime,
официальный fallback и серверные лимиты. Только план; реализация не начата.

2026-11-04: добавлен согласованный пункт в USERFLOW_AND_RENAME_PLAN.md: эффект
Brewin’ and Chewin’ для живых сообщений local/group, а не только global.
Причина подтверждена по коду: local/group доставляются как system messages.
Реализация пока не начата; нужны проверки всех каналов, истории и отсутствия B&C.

2026-11-04: CI41815736727, commit20252fb: все проверки успешны, включая
map-network/combined routes; упала только release/publish на GitHub HTTP502
при gh release create v2.7.0. Публичный2.7.0 не наблюдался. В release.py
добавлены4 ограниченные попытки с5/10/20с: reads повторяются, writes сначала
сверяют удалённое состояние/commit/draft. Потерянный ответ create продолжает
совпавший draft; потерянный ответ edit распознаёт успешную публикацию; upload
повторяется только для прежнего draft. Ошибки401/403/404 не повторяются,
чужой commit и опубликованные assets не перезаписываются. Verify-download
использует --clobber только во временном каталоге, чтобы частичный локальный
файл не ломал retry. Python47 успешны; реальный выпуск не запускался агентом.
Изменены только tooling/tests/docs, JAR и установленный клиент не менялись.
Удалённый CI этих новых изменений ещё не запускался.

2026-11-04: исправлена единственная упавшая job CI41812171502 (commit440d995):
combined-routes/direct/alias. ReviewScreen был экраном сетевой ошибки, harness
скрывал её как Repeated update review. Тестовый TCP relay слушал только IPv4;
Minecraft VM args preferIPv6Addresses=system на Linux выбирают ::1 для localhost.
Linux Java21 подтвердил порядок ::1,127.0.0.1. Старый relay в нативном клиенте
с принудительным IPv6 воспроизвёл Connection refused на alias; исправленный
слушает обе loopback-семьи. Диагностика теперь включает текст экрана.
После исправления: прямой/proxy/fallback + alias,3 цикла/9 процессов, все OK;
одна транзакция и точные файлы сохранены. Linux Docker:2 relay-теста OK;
Python36, strict docs, actionlint и diff-check OK. CI route-команда явно включает
--prefer-ipv6, чтобы регрессия не зависела от системного порядка адресов.
Изменены только fixtures/tooling/CI/документация; release JAR и установленный
клиент не менялись. Все остальные jobs указанного CI успешны, Envelope-шаг
после сбоя пропущен; удалённый CI самого исправления пока не выполнен.


2026-11-04: кандидат2.7.0 проверен и упакован. Полный build с
refresh-dependencies и принудительными Test:750 Java тестов без ошибок/пропусков;
затем11 route-тестов, включая отказ подмены публикации; Python35 и strict docs OK.
Packaged combined UI default/light/contrast × GUI1/2/3:9 циклов/18 процессов.
Дополнительно direct/proxy/fallback + alias reconnect:3 цикла/9 процессов,
одна COMMITTED транзакция в каждом профиле, точные файлы и личный конфиг сохранены.
TCP relay пропускает реальные TLS bytes; при отключении relay работает резерв.
Сценарии маршрутов добавлены в CI; тестовый relay проверен на half-close.
Внешние пользовательские прокси: DNS/SRV найдены, игровой и pack TCP сброшены
удалённой стороной до status/TLS; основной адрес из servers.dat дал connect timeout.
Это не подтверждает причину недоступности; внешний e2e остаётся непроверенным.
Установленный Prism только прочитан, не изменён. Dist2.7.0:17472732 байт,
SHA256 8b99a54bb18a0c92ba8257681d1b6c111dd27746605bc4f91f025ce867e934ff.
Bundle/ресурсы сверены, тестовых классов нет. Опубликованная версия пока2.6.0;
новый кандидат не опубликован, удалённый CI текущего дерева не подтверждён.

2026-11-04: реализованы несколько pack-маршрутов с gameHost, ограниченным
TLS-race, одним pinned key, возобновлением передачи и общей подтверждённой
идентичностью сборки. Сохранены legacy host/publicPort; новая настройка routes
мигрируется штатным расширением конфигурации. Версия кандидата 2.7.0.
Получен клиентский лог: Brewin’ and Chewin’ 4.5.0 приводит PlainTextContents
к TranslatableContents после оформления Rivet. По явному выбору пользователя
добавлен отдельный автоматический адаптер; приоритет общего чата не менялся.
Нативный прогон с настоящими B&C/Farmer’s Delight прошёл: sober/tipsy,
получение server chat, /tell, /me. TLS read timeout закрывается ниже SSL handler,
без повторных SSL flush: реальный timeout и авторизованный reconnect прошли.
Core/mod tests, 10 route tests (включая обрыв посреди передачи), Python34 успешны.
Без B&C отдельный нативный чат-прогон также прошёл; optional mixin изолирован.
Установленный Prism не изменён. Удалённый CI и публикация не выполнены.

2026-11-04: исправлен реальный Linux-баг LaunchGate после combined update.
ProcessHandle.startInstant отставал от wall clock примерно на0.4с:20/20
ложных отказов немедленного запуска в Linux-контейнере. Сравнение заменено
на RuntimeMXBean.getStartTime до mod discovery:0/20 ложных отказов; реальная
мутация после начала JVM по-прежнему отклоняется, lock освобождается.
Bootstrap7/helper10 (включая20 отдельных JVM) и Python34 прошли. Полный
combined UI default/light/contrast × GUI1/2/3 прошёл:9 циклов/18 запусков,
одна транзакция и успешный reconnect. Кандидат dist2.6.0 перепакован,
проверены bootstrap-класс/descriptor/SHA256; тестов внутри нет.
Release notes обновлены. Установленный клиент не заменён, удалённый CI
нового коммита и публикация не выполнены.


2026-11-04: исправлен CI MapTerrainHarness phase12. Воспроизведён тот же
тайм-аут при поздней инвалидизации исходного тайла: harness держал worker,
который требовался для подготовки этого же тайла. Теперь источники и готовые
uploads завершаются до барьера; barrier+composition ставятся в одном tick,
PauseScreen не позволяет миникарте добавлять работу. В тесте сохранены холодный
HUD и принудительная поздняя инвалидизация. Пиксельные проверки и deadlines
не ослаблены. Отдельный terrain/light и полный map-network (11 сценариев в порядке CI)
прошли; финальный RIVET_MAP_NETWORK_OK. Python34 и diff-check прошли.
Изменён только тестовый код; продукт/версия/JAR/Prism не менялись.
Удалённый CI после исправления ещё не проверен.


2026-11-04: полный packaged UI Rivet+pack → helper → запуск игры и вход проверен:
default/light/contrast × GUI1/2/3, 9 циклов/18 процессов. Одна транзакция,
точные файлы, сохранённый personal config, новый addon загружен, повторной
установки нет. Добавлен combined_update.py и compatibility harness, включены
в CI. Core-кандидат поступает из проверяемого кэша (релиз ещё не опубликован),
исходный тестовый JAR — текущий код с номером2.5.99, не прежний релиз.
Повторные43 updater/helper/bootstrap теста, Python34, strict docs/80links OK.
Dist2.6.0 совпадает с verifyBundle, без fixture; пользовательский Prism не менялся.
Документация обновлена. Удалённый CI точного коммита/публикация ещё не выполнены.


2026-11-04: общий план реализуется в рабочем дереве, кандидат 2.6.0 (новый
preflight и объединённое обновление). A1–A5, региональный индекс, общий план
Rivet+pack, CAS состояния и дисковый бюджет реализованы. Изолированные
realtime/skins/persistence/terrain/notifications прошли; blocked-worker скинов
и 4096 складских привязок тоже. Миллион файлов проверен при heap 128 МиБ;
копия реального слоя 134130 файлов — индекс 8.9 с, повторное открытие без обхода.
Подробный статус и остающиеся проверки — в конце USERFLOW_AND_RENAME_PLAN.md.
Финальный build: 736 Java + 310 PostgreSQL проверок прошли (6 opt-in SQL
сценариев пропущены), нагрузка100: 24000 операций/14.86 с. Catalog UI1/2/3
и изоляция legacy-кэша прошли. Кандидат в dist/2.6.0, bundle/хеши проверены;
удалённый CI не проверен.
Установленный клиент и исходная карта не менялись. Не считать выпуск опубликованным.


2026-11-04: аудит остальных модулей добавлен в USERFLOW_AND_RENAME_PLAN.md:
A1 синхронное хеширование сборки при открытии меню (изолированный Java-прогон
128 МиБ: 54–63 мс каждый вызов), A2 повтор ERROR каждый тик при ошибке terrain
journal, A3 до 512 с обхода складов при 4096 привязках, A4 HUD ждёт опроса после
изменений без notice, A5 неограниченная DISK очередь скинов при медленном диске.
A2–A5 — подтверждение по путям кода, без нативного воспроизведения. В плане
описаны регрессии и охват; подозрения без подтверждения не названы багами.
683 обычных core-теста принудительно выполнены, все прошли; PostgreSQL/native/
browser/удалённый CI не запускались. Только аудит и план, продукт не исправлялся.

2026-11-03: в USERFLOW_AND_RENAME_PLAN.md добавлен согласованный этап больших
клиентских карт. В Prism/Rivet 2.5.2 подтверждён отказ MapRepository.chunks
на >100000 файлов слоя; у пользователя 134130. План: региональная выборка,
дисковый восстанавливаемый индекс, ограниченные память/очереди/работа за кадр,
изоляция ошибок и разумные повторы, сохранение данных. Проверки: реальная копия,
100000/100001/1000000 чанков, reconnect/restart, аварийные сценарии и замеры.
Только планирование; код, установленный клиент и карты не изменялись.


2026-10-31: выпуск 2.5.1 уже существует (проверен remote tag), дополнительные
исправления идут в 2.5.2. Живой сценарий расширен настоящими SkinWire upload/select
на тех же ItemStack и SkullBlockEntity: новый скин, старый, обычный и обратно.
Воспроизведены две ошибки: стандартная текстура между appearance и PNG;
обычный скин предмета выбирался по старому UUID даже после разрешения владельца.
SkinClient хранит ограниченный набор последних загруженных текстур на время
загрузки новых, сбрасывает его при обычном скине/отключении/новом соединении;
vanilla fallback строится по восстановленному UUID. Новый тест специально
задерживает обработку PNG, проверяет сохранность предмета и блока, оба режима Auth,
а также существующую регрессию фонового refresh и reconnect.
Окончательный код прошёл native Auth base/false: upload, select, ordinary, возврат,
те же предмет/блок без повторной выдачи, reconnect и 960 проверок refresh суммарно.
Полный build с принудительными тестами: 708/708 Java; Python 34/34.
Зависимости проверены с --refresh-dependencies. MkDocs strict и 80 страниц ссылок
прошли. dist/2.5.2 перепакован после последнего изменения, проверены классы,
SHA256/размер/протоколы; тестовых fixture в JAR нет. Установленный клиент не менялся.
Публикации 2.5.2 и push не выполнялись; CI точного нового коммита не подтверждён.


2026-10-31: дополнительно исправлено мерцание голов при обновлении поиска владельца.
Нативно воспроизведено переключение SkullBlockRenderer на стандартную текстуру,
когда повторный UUID-запрос ещё pending, а имя уже сопоставлено с аккаунтом.
SkinClient.headSkin больше не обрывает поиск до проверки известного имени.
SkinProfileLookup сохраняет подтверждённый положительный результат на время
фонового refresh; подтверждённое удаление/смена владельца заменяет результат,
при reconnect кэш очищается. Явные декоративные текстуры не менялись.
Регрессия до исправления падала в игровом рендере и тесте истечения TTL.
После исправления: 683 теста ядра, 0 ошибок; classes и verifyBundle успешны.
Native Auth false и Auth base прошли, по 240 последовательных обращений к
рендеру при pending UUID/name на каждом из двух подключений (960 суммарно).
Проверены online/offline, wrong UUID/NIL, установленная голова, reconnect,
автоматические и явные текстуры. Auth base повторён в изолированном мире:
старые fixture-данные другого режима имели несовместимые тестовые UUID.
Установленный клиент и dist/2.5.1 не заменялись, версия не менялась;
перед публикацией нужно заново подготовить dist с этим исправлением.


2026-10-31: подготовлено исправление голов 2.5.1. На коде 2.5.0
воспроизведён обход поиска Rivet для уже resolved профиля с NIL_UUID.
SkullTextureMixin теперь проверяет владельца через ограниченный SkinProfileLookup
на каждом обращении к рендеру (UUID, затем имя для неизвестного UUID).
HeadProfileOriginMixin помечает автоматически разрешённые профили, чтобы
текстуры Mojang не ошибочно считались явно заданными декоративными текстурами.
Декоративные профили не переписываются. Native проверяет SkullBlockRenderer,
а не только SkinManager: `_ToT_KeKs_`, lowercase, NIL/unknown UUID, онлайн,
офлайн, установленная голова, reconnect, автоматические и явные текстуры.
Auth base и false прошли; повторный запуск сервера/клиента использовал сохранённую
голову из мира (RIVET_HEAD_SAVED_BLOCK_REUSED). Полная сборка: 706 Java тестов,
0 ошибок/пропусков; Python 34/34. dist/2.5.1 собран, установленный клиент не тронут.
Публикации нет; удалённый CI этого изменения ещё не подтверждён.
Финальная подготовка: verifyBundle с --refresh-dependencies прошёл; JAR,
core.json и SHA256SUMS согласованы, артефакт после повторной упаковки неизменен.
MkDocs --strict и проверка 80 страниц прошли без битых ссылок. Release notes
указывают обновление обеих сторон и повторную выдачу старых текстурных голов.
Изменения остаются локальными, поэтому CI точного релизного коммита ещё нет.
Старую голову с уже встроенной текстурой без признака её происхождения нельзя
надёжно отличить от декоративной: её текстура сохраняется, такую голову нужно
выдать заново после обновления. Точное состояние головы на сервере пользователя
не получено; воспроизведён и закрыт конкретный дефект обработки resolved профилей.


Исправление CI: test_map_network ожидал одного OP после добавления отдельного
RivetDataTest для realtime. Ошибка воспроизведена локально. Тест теперь требует
ровно двух fixture-операторов, корректных offline UUID/уровней, отдельного
пользователя realtime и допустимых имён; RivetMapGuest по-прежнему без OP.
После исправления весь Python suite: 34/34, exit 0. Предыдущее утверждение
о Python suite в финальной сводке 2.5.0 было ошибочным: несколько проверок
запускались одной shell-командой, успешный последний шаг скрывал код ошибки
unittest. Для составных проверок применять fail-fast или проверять каждый
exit code и лог отдельно. Удалённый CI исправления ещё не подтверждён.

Подготовка 2.5.0 (MINOR: новый адаптер Envelope) по поручению владельца.
Найдено лишнее ожидание: фоновые groupMap/mapActivities/mapObjects/toolsPlaces
делили один интервал с видимыми Community-запросами. Для согласованного
menu-page-deltas введён фиксированный mapData lane на клиенте и сервере;
старый режим сохранён. Позиции игроков уже имели отдельный прямой транспорт.
Добавлен UiTitleLayout: перенос заголовка хранится на виджете до изменения
ширины/текста/шрифта/ресурсов. Full build с refresh-dependencies, принудительными
Test/ProcessResources и verifyBundle прошёл: финальные 706 обычных тестов,
PostgreSQL 309 выполненных + 7 opt-in пропусков; предыдущий прогон также
включал MixedModuleLoadTest 100/24000 (310 выполненных + 6 пропусков).
Собран локальный кандидат dist/2.5.0; публикации и замены установленного клиента нет.
Полная финальная map-network матрица (10 сценариев) прошла на окончательном
коде, включая конкурентные чтения меню/карты и переподключения. UI: 144
ролевых варианта RU/EN, каталог GUI 1/2/3 и проверка кэша текста успешны.
Remote CI этих незакоммиченных изменений не подтверждён. Точные результаты
и ограничения — в конце USERFLOW_AND_RENAME_PLAN.md.

2026-10-30: владелец поручил реализовать весь общий план. Начат realtime,
прошли core/PostgreSQL, смешанная нагрузка 100 идентичностей и живой тест
уведомлений. Начаты офлайн-головы: native /give прошёл с Auth base и false.
Envelope реализован через Java API: UUID сохраняется в адресе, старые имена
защищены журналом и резервной копией перед миграцией. Проверены реальный мод
0.8.1, Auth base/false и перезапуск. Точные результаты и оставшиеся ограничения —
в конце USERFLOW_AND_RENAME_PLAN.md. Весь план завершённым не считать.

Продолжение 2026-10-30: добавлен согласуемый `menu-page-deltas` для остальных
чтений меню (Community, игроки, обращения, история, сводка). Полные ответы
авторизуются и локализуются до кодирования; клиент проверяет базу/результат,
при несовпадении один раз запрашивает полный снимок. После reconnect кэш очищается.
Пачки событий группируются по разделу: серия уведомлений больше не превращается
в глобальную инвалидизацию карты. Проверены 100 клиентских состояний, 2000
обновлений/400 reconnect, PostgreSQL-нагрузка 100 идентичностей и восстановление
outbox с новым пулом БД. Живые realtime/роли и точные результаты — в конце плана.

## Следующая работа

2026-10-30: в общий USERFLOW_AND_RENAME_PLAN.md добавлен согласованный этап
realtime уведомлений и данных через игровое соединение: снимки и изменения
после commit, права доступа, восстановление после переподключения, ограниченные
очереди и нагрузочная проверка до 100 игроков. В этот же этап добавлены
оптимизация SQL/очередей/объёма ответов и клиентской отрисовки с одинаковыми
сценариями и замерами до/после, включая редкие длинные задержки и кадры.
Направления проверки не считать доказанными дефектами. Реализация начата; результат выше.

2026-10-30: в USERFLOW_AND_RENAME_PLAN.md добавлен общий этап скинов, профилей
и голов офлайн-игроков: /give по имени, UUID Rivet, офлайн-загрузка, retry,
ограничение кэшей и вытеснение текстур, декоративные головы, однократные
запросы сторонних модов и rename. Подтверждённые свойства кода отделены от
рисков для воспроизведения. Реализация начата; результат выше.

2026-10-28: по поручению пользователя в USERFLOW_AND_RENAME_PLAN.md добавлен
адаптер Envelope через Java API: UUID/имена Auth, rename и перенос существующей
почты при поздней установке адаптера. Реализация и результаты описаны в текущей работе.

По поручению владельца составлен [план смены ника и userflow](USERFLOW_AND_RENAME_PLAN.md).
Команда `/rivet auth rename` реализована: консоль и авторизованный администратор,
целевой игрок офлайн; UUID сохраняется. Проверки и границы — в конце плана. Проведена статическая проверка сценариев разных ролей;
подтверждены проблемы обнаружения публикаций карты, остальные проверки и
предложения явно отделены от доказанных дефектов. Начат приоритетный этап данных/карты; UI-прогон 8 потоков ролей
прошёл 144 варианта RU/EN, GUI 1/2/3, default/light/contrast. Это fixture-транспорт,
не полный многопользовательский прогон всех ролей; границы описаны в плане.
Последующий живой RoleNetworkHarness прошёл 54 проверки на настоящих Auth base,
PostgreSQL и LuckPerms: восемь ролевых сценариев, оба входа публикаций, отзыв
прав при диалоге и rename из игры без OP. Один клиент последовательно меняет роли. Для rename владелец подтвердил серверную консоль
и администратора из игры, переименовываемый игрок офлайн. Отдельное право
модерации остаётся предложением; владелец согласовал понятные входы в публикации:
личная очередь из списка меток, административная — из обзора администрирования.

В план 2026-10-24 добавлены задержки/устаревание данных, фризы и UX карты,
цвета Copycats+. Подтверждён статический кэш по BlockState с ModelData.EMPTY;
исправление проверено на настоящих Create 6.0.10 / Copycats+ 3.0.9.
Результаты приоритетного этапа и оставшаяся работа — в конце плана.
Следующий этап данных: защита общего кэша от поздних ответов разных владельцев,
перерисовка задач при изменении прав/ограничений и однократное восстановление
полного списка при несовпадении базы дельты. Доказательства и границы — в плане.
Живые проверки уведомлений, прав карты и восстановления чтений меню прошли.
Удалена неиспользуемая сортировка видимых чанков большой карты; замер движения
и зума добавлен в MapTerrainHarness. Подробности в плане: p95 terrain улучшился,
но единичный пик полного кадра остаётся необъяснённым; все фризы устранёнными
не считать. Полная матрица ролей и сравнение с Xaero ещё не выполнены.
JFR длинного прогона локализовал воспроизведённый пик 37.9 мс в glfwSwapBuffers
(render 3.35 мс, без GC); исходный пик 66 мс не имел записи JFR. Отдельно
исправлены интенсивные regex-аллокации MapLayer: эквивалентная проверка грамматики
без Matcher, 140 тестов карты и минутный native-прогон прошли. После исправления
terrain p95 3.03 мс, max 5.53 мс; максимальный интервал кадра 34.34 мс.
Изменения частоты дисплея ограничивают прямое сравнение FPS. Подробности в плане.

Проверка смены доступности модулей: исправлен выход из недоступных Community/Task
окон и форм с сохранением черновика и очисткой Back-history. Native-матрица —
171 проверка, 54 модульных варианта. Это инъекция политики в fixture; в продукте
модули по-прежнему переключаются после перезапуска. Конкурентная нагрузка на
100 идентичностей/API-сессий прошла (24 000 смешанных SQL-операций, REST/WS,
100 запросов/с минуту). Это не 100 Minecraft-клиентов; детали и границы в плане.

## Решения владельца, которые нельзя потерять

- Установка одним JAR. Отдельный установщик платформенных библиотек не согласован.
- Не заменять установленный Prism/JAR, профиль, сборку или production без поручения.
  Не удалять pending/locks для обхода восстановления обновления.
- Не менять состав пользовательского pack.toml по предположению. Самостоятельные
  библиотеки не приписывать случайным модам; дополнения группировать с основным модом.
- Карта самостоятельная: Xaero/JourneyMap только для импорта актуальных меток.
  Общий обмен исследованной местностью отменён. Клиентское исследование не поступает
  в серверный рендер. Личные территории разрешены последним решением владельца.
- Xaero — ориентир поведения, не обещание пиксельного совпадения.
- Не добавлять торговлю, логистику, журнал мира или другие несогласованные модули.
- API принят для внешних модов и сайтов. Встроенный сайт/кабинет не нужен.
  Ключи — только локальная консоль, не игровое меню и не RCON.
- Доверенный backend может входить по логину/паролю Rivet с отдельным scope;
  при выключенном Auth — привязка в игре. Административные действия нужны офлайн.
- Waystones и позиции — только связанный онлайн авторизованный игрок.
  Приватный профиль рендера разрешается конкретному API-ключу, без отдельного LP-права игрока.
- Ресурсы рендера локальные; не скачивать ванильные assets автоматически.
- Документация имеет один источник `docs/`, публикуемый GitHub Pages; Wiki не нужна.
  Служебные инструкции и доказательства проверок остаются в `ai/`.

## Текущий релиз

- Опубликована 2.3.0 (проверен GitHub Release v2.3.0). Кандидат 2.3.1 исправляет загрузку и кэш клиентской карты.
  Версия в `gradle.properties`, пользовательские изменения в `rivet/RELEASE_NOTES.md`.
- [Состояние API и итоговые проверки](API_AND_SERVER_MAP_PLAN.md) содержит доказательства
  локального build/PostgreSQL/native/browser/proxy и ограничения этих проверок.
- Кандидат зафиксирован в 4cd1fb3; последующие исправления CI остаются в рабочем дереве.
- Push новой версии в main может автоматически опубликовать JAR после CI.
  Подготовка кандидата не является разрешением на публикацию.
- Полная удалённая матрица точного нового коммита ещё не подтверждена.
  Успех старого fe1ff839 / run38497562819 не проверяет текущее рабочее дерево.
- Для первой публикации сайта требуется GitHub Pages Source = GitHub Actions.
  Настройки GitHub и установленный клиент не менялись.

## Границы доказательств

- Синтетические 100 аккаунтов не равны 100 Minecraft-клиентам или TPS-бенчмарку.
  Многочасовой soak, полный пользовательский modpack и новая удалённая матрица не подтверждены.
- TLS/WSS проверялся Java-клиентом через Nginx; Chrome отдельно проверял протокол
  через loopback HTTP. Системные сертификаты не менялись.
- [Аудит безопасности](SECURITY_AUDIT_2026_10_13.md) содержит открытые риски библиотек
  Minecraft/NeoForge. Не объявлять их устранёнными изменением classpath Rivet.
- Исторические проверки: [валидация](VALIDATION_2026_10_14.md),
  [аудит 2.2.1](AUDIT_2.2.1.md), [общий аудит](AUDIT_2026_10_13.md).
- Изолировать build directories и тестовую БД; не перекомпилировать выходы работающей JVM.

## Последняя локальная проверка

Очистка документации: 80 страниц, ссылки и ресурсы без ошибок; проверка включена
в Documentation CI. Кандидат dist/2.3.0 обновлён после forced build (666 Java-тестов)
и 33 Python-тестов. Подробности и ограничения — в конце API_AND_SERVER_MAP_PLAN.md.

Интерфейс документации следует Elysia с палитрой Rivet. Боковая панель прокручивается
целиком на 100dvh, без вычисляемой Material высоты. Старые дубли CSS удалены.
79 страниц навигации проверены на переполнение при 320, 390 и 1440 px; отдельно
проверены 1024×600 и 1920×1080, поиск, меню, копирование (подтверждение UI) и обе темы.
Строгая сборка и проверка ссылок/ресурсов 80 страниц проходят. Публикация не выполнялась.

Прозрачность и motion сверены с живым Elysia: шапка без общей заливки, локальные
подложки кнопок blur 8–16 px; мобильная панель 75% / blur 8 px, выезд 250 мс.
Поиск анимирует прозрачность при неизменной ширине; разделы — высоту за 250 мс.
Системное уменьшение движения учитывается; непрозрачную шапку не возвращать.

Адаптеры: добавлен IntegrationApi и реестр с владельцами регистраций. Серверные
вызовы Waystones, голоса, прав и действий spark/Create идут через провайдеры;
идентичность Create — через Identity. Специальный сетевой слой Waystones сохранён
ради текстур и приватной выборки. Проверки: 650 API/core тестов (5 новых),
33 Python-теста, компиляция мода и compatibility fixtures, архитектурная проверка,
сборка docs и ссылки. Optional harness обновлён для проверки вызовов через Java API. Установленный
клиент и production не менялись.

Numismatics: добавлен автоматический адаптер разрешения имён через IntegrationApi.IDENTITY
и серверный провайдер ACCESS/numismatics (только lookup). Клиент использует ограниченные
асинхронные запросы CompatibilityClient с очисткой при смене соединения; подписи счетов
Numismatics имеют приоритет. Общий и клиентский optional mixin обходят Mojang lookup для
обрабатываемых UUID. Балансы, UUID, карты доступа и владение не мигрируются; verified alias
не подменяет владельца банковского счёта. Контракт проверяется по сигнатуре UsernameUtils.
Проверки 2026-10-15: forced API/core — 652 теста без ошибок; mod/fixtures compilation,
архитектурная проверка. Изолированный native сервер: Numismatics 1.1.0 + Create 6.0.10,
Java API, зарегистрированная офлайн-идентичность, null/unknown/official-alias fallback,
баланс, авторизация владельца и имя счёта банкира. Native клиент подключён к тестовому
серверу: офлайн/онлайн имена, пользовательская подпись, отсутствие Mojang lookup для
обработанного UUID. Отдельный сервер без Numismatics/Create успешно запущен и остановлен.
Сценарии NumismaticsHarness и NumismaticsClientHarness — только compatibilityFixtures;
не входят в релизный JAR. Документация и ссылки 80 страниц проходят. Production, профиль
игрока и опубликованные артефакты не менялись; CI текущего коммита не запускался.

Совместный native-прогон семи адаптеров выполнен 2026-10-15: LuckPerms 5.4.150,
Plasmo Voice 2.1.17, Simple Voice Chat 2.6.22, Waystones 21.1.46/Balm 21.0.66,
spark 1.10.124, Create: Access Denied 0.0.4+c6.0.10.mc1.21.1 и Numismatics 1.1.0
с Create 6.0.10. Во всех сценариях моды установлены совместно; LP только на сервере.
Успешны core (права/контекст LP, оба голосовых провайдера и общий mute, срок/дубликат,
реальный SVC dispatchEvent с пустыми тестовыми proximity/whisper/group пакетами,
Numismatics, spark start/cancel и защита чужой записи), access (клиентские offline-add,
preview/apply), access-server (чужой актор, устаревшее подтверждение, rename, сохранение),
waystones (скрытые/активированные/глобальные/другое измерение, rename/remove, смена
публичности, атомарность клиентского снимка). Оба голосовых UDP-подключения установились;
качество настоящей речи с микрофона вручную не оценивалось.
Регрессий адаптеров в этих сценариях не найдено. Исправлены устаревшая рефлексивная
сигнатура теста Create и прямые вызовы Waystones в тесте; они используют Identity/Waystones
Java API. Фоновая запись spark по умолчанию — чужая для Rivet; для теста управления
она выключается только в тестовом профиле, чужая запись проверяется отдельно.
Запускатель rivet/tooling/optional_adapters_smoke.py описан в CONTRIBUTING: отдельные
профили, Docker PostgreSQL с временным паролем, штатный stop, проверка маркеров,
удаление контейнера. Проверен также core на другом порту. Принудительный SIGTERM
вызывал гонку shutdown-hook H2 LuckPerms; повторный core со штатным stop завершился
с кодом 0, Goodbye LuckPerms, сохранением всех измерений и закрытием Hikari.
Запускатель не засчитывает таймаут/ненулевой код остановки; при задержке снимает jstack.
Компиляция fixtures/UI harness, 33 Python-теста и docs/ссылки проходят. Установленные
профили, production и опубликованный JAR не менялись; новая удалённая CI не запускалась.

Подготовка кандидата 2.3.0 после совместного прогона адаптеров: обновлены руководства
игрока/сервера, Java API, CONTRIBUTING и RELEASE_NOTES. Forced build с
--refresh-dependencies и PostgreSQL 18 завершился успешно: 35 задач выполнены,
673 обычных Java-теста; PostgreSQL — 296 выполнено, 7 отдельных opt-in нагрузочных
сценариев пропущены. 33 Python-теста, strict docs и ссылки/ресурсы 80 страниц проходят.
Локальный dist/2.3.0 обновлён: единый JAR, core.json, SHA256SUMS и RELEASE_NOTES.
В упакованном game.jar проверены IntegrationApi, Numismatics mixins/адаптер,
актуальные языковые ресурсы и отсутствие harness/fixtures. Хэши и размер совпадают
с манифестом. Удалённая CI точного кандидата остаётся неподтверждённой; публикация,
push и замена установленного клиента не выполнялись.

Повторная сверка публичной документации перед релизом: README дополнен Numismatics
и точным правилом проверки NeoForge; описание модулей отражает IntegrationApi /
ServerProviders. Убрана неверная рекомендация отключать HTTP после выдачи сессий
WebSocket: перезапуск их завершает. Резервное копирование включает весь мир,
идентификатор карты и очередь серверного рендера; откат требует согласованной БД.
CONTRIBUTING содержит первый запуск GitHub Pages через существующий Documentation.
Strict docs и ссылки/ресурсы 80 страниц, ссылки корневых документов и 17 явно
указанных абсолютных REST-путей против OpenAPI проверены. Контрольные суммы и
заметки локального dist/2.3.0 совпадают. Код/JAR в этом проходе не менялись,
используется предыдущая свежая проверенная сборка. Публикация и CI не запускались.


Исправления CI 2026-10-15 для run38772404011 / 4cd1fb3:
из артефактов установлены две первопричины. При синхронизации команд до добавления
игрока в PlayerList и после удаления при logout провайдер прав выбрасывал Player offline.
Внутренний вызов теперь передаёт точного ServerPlayer через ограниченный синхронный
контекст с finally; публичный UUID API продолжает отклонять офлайн-игрока.
WebSocket harness принимал фоновый terrain.changed за снимок нового профиля.
Ожидание различает типы сообщений, ограничено 5 секундами, ошибки не подавляет.
Добавлены детерминированное чередование push/ответа и проверка очистки контекста
прав при исключении; маркер последней обязателен в api_smoke.

Локально прошли реальный ingress с обычным игроком (3 цикла повторного входа),
полный api_smoke с LuckPerms и PostgreSQL 18, включая профили карты/WebSocket,
forced build (34 задачи, 673 Java-теста) и 33 Python-теста.
Для Python-проверок в локальное venv установлена отсутствовавшая jsonschema.
Обновлены RELEASE_NOTES и локальный dist/2.3.0. Проверка Linux/Xvfb и полная
удалённая матрица нового исправления ещё не выполнены; релиз заблокирован до
успешной CI точного коммита. Установленный клиент и production не менялись.


Карта после жалобы на 2.3.0 (2026-10-15): убран общий барьер WorldMapScreen,
скрывавший готовые текстуры до восстановления всех страниц и родительских LOD.
Превью читаются/пишутся отдельной ограниченной очередью, независимо от индекса
и tile IO; первыми загружаются обзорные уровни. Видимые страницы защищены при
вытеснении, лимит GPU адаптируется к окну в пределах 2048–8192 страниц.
Отклонённая очередью запись превью повторяется, ошибка IO журналируется с паузой
5 секунд. Превью остаются производными, исходные данные карты не изменены.
Native MapTerrainHarness усилен блокировкой terrain IO вместе с colour worker.
Два локальных прогона успешны: 9216 чанков, пиксели/границы, повторное открытие
с теми же GPU-текстурами, смена LOD и обновление границы. Последний прогон:
холодная композиция 18064 мс; восстановление сохранённых превью 124 мс при
заблокированных чтении чанков и пересчёте. Это синтетическая карта, не замер
пользовательского профиля. Полная Linux CI и пользовательский клиент не проверены.
Правки пока только в исходниках, установленный клиент и dist не заменялись.


Подготовка патча 2.3.1 (2026-10-15): версия повышена после подтверждения
опубликованной v2.3.0. RELEASE_NOTES описывает только исправления карты.
Forced build с --refresh-dependencies: 35 задач выполнены; 673 обычных Java-теста,
296 PostgreSQL-тестов без ошибок (7 отдельных opt-in сценариев пропущены).
33 Python-теста и strict MkDocs/проверка 80 страниц прошли. Сайт проверен во
временном каталоге: iCloud-каталог старой сборки возвращал Stale NFS file handle.
Полный map_network завершился RIVET_MAP_NETWORK_OK: integrations, notifications,
terrain, ingress, publication, light, permissions, teleport. Публикации — 9 вариантов
масштабов/профилей. Последнее восстановление превью — 137 мс, 9216 чанков.
Локальный dist/2.3.1 содержит JAR, core.json, SHA256SUMS.txt и RELEASE_NOTES.
Проверены размер/хэш, версия вложенного game.jar, наличие исправлений и отсутствие
harness, неизменные протоколы относительно 2.3.0. Удалённая матрица точного нового
коммита не запущена; готовность публикации зависит от её успеха. Push, публикация
и замена установленного клиента не выполнялись.

## Меню «Воздух и глубина» — 2026-10-24

Владелец выбрал этот вариант из интерактивного сравнения и поручил перенести
стиль на всё серверное меню, сохранив функциональность. Общий UiKit теперь
рисует округлые контролы, карточки, рамки фокуса и приподнятые диалоги;
UiWorkspace отделяет поверхность контента от тихой боковой навигации.
Обновлены поля (включая многострочные), вкладки, переключатели, уведомления,
детали записей, скины, выбор темы, выпадающие меню и полосы прокрутки.
Кнопки/права/обработчики и размеры областей взаимодействия сохранены.
Документация UI Kit и служебный каталог актуализированы. В каталоге прогресс
перенесён в отдельную строку, чтобы не перекрывать показатели.

Проверки нового меню: компиляция production и uiHarness; ApprovedUiHarness —
126 состояний, основная/светлая/контрастная темы на GUI 1/2/3 и образцы остальных
тем; AdaptiveUiHarness — 198 состояний в шести размерах; RoleFlowHarness —
144 сценария RU/EN. Все завершились обязательными маркерами. Просмотрены PNG
основной и светлой темы, исправлена коллизия каталога и повторён Approved.
Это проверки настоящих native-виджетов с fixture-транспортом, не новые тесты
бизнес-логики сервера. Strict MkDocs и git diff --check прошли. Для ручного
просмотра подготовлены отдельные клиент/сервер с PostgreSQL, русский язык,
GUI 2, администратор RivetMapTest, Auth выключен только в тестовом профиле.
Установленные профили, dist и релиз не заменялись.

Дополнительный проход по вложенным окнам «Воздуха и глубины»: заменены отдельные
подложки статистики игрока, диагностики/spark, текстовых панелей, пустых состояний,
порядка HUD и шаблона таблички задачи. Убраны старые цвета фона кнопки пароля,
панели задач и предпросмотра прозрачности; контуры выдвижных панелей карты
согласованы с их округлой поверхностью. Геометрия маркеров, графиков, drag-индикаторов
и самих данных карты сохранена. Вкладки оформлены как утопленные сегменты.
Расширенный старый CommunityUiHarness ожидал редактор места у объединения;
обновлён под действующий контракт: форма объединения без legacy-места, редактор
существующего места проверяется через объявление. Проверки не отключались.

Финальная проверка дополнительного прохода: Approved — 126 состояний, Community —
94 состояния (включая возврат из модальных окон и сохранение существующих полей
места), Next — 138 состояний GUI 1/2/3, клавиатура, стабильность при обновлении,
этапы задач и шаблоны табличек. Все обязательные маркеры успешны; просмотрены PNG
профиля, объединения, вложенного редактора места, диагностики и таблички задачи.
Strict docs и diff --check успешны. Проверки используют fixture-транспорт native UI.
Подготовлен новый отдельный игровой просмотр, прежние профили не заменены.

Колесо быстрой смены скина: выбран вариант «Сектора», цельное кольцо вместо
карточек. Выбор по углу от центра, в том числе за внешней границей; нейтральный
центр и Esc отменяют. Удержание назначенной клавиши, отпускание применяет,
потеря фокуса отменяет. Смена закрывается по подтверждению SkinWire. В библиотеке
добавлено управление составом колеса, до 8 вариантов, локально по серверу/аккаунту.
Число секторов динамическое; обычный скин участвует как вариант. Серверный потолок
skins.maxPerPlayer поднят с 5 до 8, существующие значения и default 2 сохранены.
Обычный скин не занимает серверный слот: при 8 загруженных доступны 9 вариантов,
из которых в колесо выбираются 8. UI-компонент UiRadialSector доступен из каталога.
Проверки: SkinWheelHarness — 45 native-состояний GUI 1/2/3, темы, геометрия
1–8 секторов, выбор далеко за кольцом, избранное, Esc, отказ и подтверждение.
SkinImageTest + ServerSettingsTest — 42 теста успешны. Отдельный живой сервер
с лимитом 8: загружены ванильные тестовые скины, проверены отмена в центре,
отпускание вне кольца и настоящее подтверждение сервера; RIVET_SKIN_WHEEL_LIVE_OK.
Клиент оставлен для проверки, F7 назначена только в тестовом профиле. Установленные
профили не менялись. Два ранних дефекта тестовой оснастки (возврат на TitleScreen
и слишком частые uploads) исправлены; ограничения сервера не ослаблялись.

Карта приведена к общему стилю «Воздух и глубина»: UiKit.floating для инструментов,
списка меток, редактора и строки состояния; лишние постоянные контуры оставлены
только для высокой контрастности. UiActions.quiet выделяет выбранный инструмент
и наведение, UiActions.leading выравнивает строки меток слева; полное имя и
координаты доступны в подсказке. Настройки отдельно снизу слева, порядок инструментов
и поведение всех действий сохранены. Подписи и подложки меток/линейки используют
скругления и семантические цвета, пользовательские цвета меток не менялись.
Вложенные окна карты уже используют общий UiDialog/UI Kit. Рендер terrain,
геометрия территорий и кэш не изменены. Миникарта сохраняет существующий UiHudSurface.
Сборка успешна. WorldMapHarness на настоящем сервере: 785 проверок при начальном
1280×800, контрольные 776 при 1280×1024; GUI 1/2/3, default/light/contrast,
компактные 960×540 и 640×480, создание/сохранение/удаление/перетаскивание меток,
категории, поиск, измерение, масштабирование, навигация, подтверждения и Esc.
Оба прогона RIVET_MAP_UI_OK; просмотрены снимки основной, светлой и компактной
компоновки. diff --check успешен. Изолированный клиент оставлен для просмотра,
русский язык, GUI 2; существующие пользовательские установки не менялись.

Общий проход по стилю карты, миникарты и меню: UiTheme разделяет наведение мыши
и клавиатурный фокус. После щелчка кнопка/строка не остаётся подсвеченной при
уходе указателя; фокус фонового окна не просвечивает через диалог. Поля ввода
сохраняют фокус редактирования. Переключатели и строки выбора используют общую
анимацию; HOVER рассчитывается от CONTROL. Карточки, боковая навигация, уведомления,
палитры и меню используют общие состояния. Подложки меток миникарты, подписей
общих/внешних слоёв и HUD согласованы с UI Kit, пользовательские цвета сохранены.
Проверки: UiHoverHarness — 24 проверки, Approved — 126, Community — 94, Next — 138,
Localization — 324 состояния RU/EN; GUI 1/2/3, default/light/contrast и образцы тем.
WorldMapHarness — 785 проверок actualTerrain, compact, RIVET_MAP_UI_OK.
Strict docs и diff --check успешны. В MinimapHarness обнаружена старая зависимость
от высоты поверхности: y=83 оказался в пещере, а координаты клика считались без
пещерного zoom. Фикстура переведена в spectator над миром. Повторные прогоны выявили оставшиеся
после сбоя совпадающие тестовые метки и устаревший клик за пределами значка
при markerScale=50%. Оснастка очищает только свои именованные фикстуры и нажимает
на видимую часть метки за краем миникарты; исходные проверки сохранены.
Финальный MinimapHarness: 781 проверка, default/light/contrast GUI 1/2/3, compact,
serverPolicy; RIVET_MINIMAP_UI_OK. Обновлённый изолированный клиент подключён
к тестовому серверу и оставлен для просмотра.

HUD: выбран стиль «Парящие острова». UiHudSurface — общий радиус 6, мягкая тень,
обе зависят от прозрачности; opacity=0 не оставляет подложку. Блоки виджета
разделены промежутками 6 GUI px с обновлённым измерением и hit-регионами;
порядок, скрытые блоки, масштабы, якоря, движение и настройки сохранены.
Навигация, подсказки чата, уведомления/отсчёт рестарта, TAB, подписи миникарты
и подсказка редактора используют общую поверхность. Предпросмотр виджета
обновлён; образец HUD добавлен в живой UI Kit. Ванильные показатели не заменены.
Проверки: HudUiHarness 90 состояний GUI 1/2/3, default/light/contrast и образцы
тем; повторный прогон после обновления предпросмотра также успешен, включая
verifyGaps (промежутки не перекрываются и не перехватывают клики). Approved 126
состояний + 24 hover-проверки. Живой MinimapHarness 781 проверка; Notifications
58 проверок/27 кадров, command/menuCancel/preferences/seconds. Все маркеры OK.
Просмотрены снимки HUD и редактора. Strict docs и diff --check успешны.

Права: по выбору пользователя редактируются в Rivet и LuckPerms, единый источник
LP. В существующем окне «Права ролей» каталог с описаниями, allow/deny/inherit,
подтверждение, CAS, асинхронное сохранение через Java API, внешний refresh 5с.
Управление: admin + rivet.permissions.manage либо OP2; только глобальные
постоянные права Rivet в загруженных группах. Создание/состав групп и контекстные
права — LP. rivet.map.seeHiddenPlayers позволяет видеть скрытых игроков на
игровой карте; входит в административный доступ. Сохранённая приватность и
обычный API-доступ не меняются. Перед отправкой координат доступ перепроверяется.
Проверки и ограничения зафиксированы в USERFLOW_AND_RENAME_PLAN.md.

Подготовка 2.4.0 (новые возможности, minor): собран локальный bundle и descriptor
с SHA256 в dist/2.4.0, установленный клиент не заменялся. Убраны ошибочные
неиспользуемые skins.wheel.* из core-локализации (клиентские rivet.skins.wheel.*
сохранены). Каталог узлов больше не создаёт список на каждый вызов; LuckPerms
обходит назначения группы один раз для всего редактора вместо прохода на каждое
право. Доверенный просмотр скрытых позиций не читает SQL-аудитории/членство,
при сохранённых проверках входных UUID, лимитов и доступа перед отправкой.
Добавлена обязательная CI job permissions: живые права/rename; прежний
map-network сохранён. Release зависит от обеих проверок.

Финальная локальная проверка: build с сетевым refresh зависимостей успешен;
после исправления ресурсов повторный build и принудительный запуск Test успешны.
685 обычных тестов; PostgreSQL 306 успешных, 7 отдельных нагрузочных отключены
их штатными флагами. Python tooling 33 успешных; strict docs и diff успешны.
Native LuckPerms/Auth 59 проверок и 27 состояний UI, все маркеры завершения OK.
verifyBundle успешен; ресурсы обоих языков и mixins внутри JAR сверены с исходниками,
Harness/Fixture отсутствуют. Новые изменения не опубликованы и не проверены
удалённым CI; статус готовности CI не подтверждён.


CI 2.4.0, запуск 40113593169: по скачанным артефактам установлены две причины.
MapTerrainHarness завершал холодную загрузку за 165 с и упирался в общий
180-секундный срок уже на движении. MapRegionTextures повторно сканировал
пустые страницы без изменений; добавлен LRU до 8192 записей с инвалидированием
по ревизии исследованных чанков и очисткой при reset. Тайм-аут не увеличен.
Нативный тест теперь проверяет и повторное чтение пустой страницы, и появление
в ней нового чанка. Финальный локальный terrain: 110670 проверок, включая
пиксели, LOD, повторное открытие и disk preview; light также OK.

RoleNetworkHarness падал с Admin publication entry missing: карточка была
за пределами прокрутки при первом запуске GUI 3. Сценарий прокручивает список
до карточки, падает при её отсутствии в конце, проверяет прежний маршрут.
Логическая ширина зафиксирована как 1280 / 3 с учётом Retina. Финальный живой
прогон 427×342 прошёл 61 проверку и 27 состояний, включая прокрутку, rename,
allow/deny/inherit/CAS и внешнее обновление LuckPerms. Обёртки map/role
теперь выводят исходный FAILED и стек, либо хвост startup-лога, включая ненулевой
exit code. Python tooling: 34 теста; build с принудительными Test/ProcessResources:
685 тестов без пропусков, verifyBundle успешен. Bundle 2.4.0 и SHA256 обновлены.
Удалённый CI для этих изменений ещё не запускался; готовность релиза не подтверждена.


2.4.1: исправлена воспроизведённая ошибка обновлений 2.3.1 — HTTP parser
отклонял длинные заголовки GitHub (Maximum line length limit exceeded).
PublicHttp задаёт конечные лимиты 32 KiB на строку и 100 заголовков;
проверки публичных DNS/IP не изменены. Ручной retry проходит через Hub,
CoreUpdater и UpdateMetadataCache с refresh, без отрицательного кэша и без
маскировки ошибки устаревшими данными. Автоматический backoff сохранён.
Окно не показывает пустой результат при ошибке; причина пишется в журнал.
Регрессии проверяют длинный CSP/Location, превышение обоих лимитов и retry.
Чистая сборка с refresh-dependencies успешна; затем все Test принудительно
исполнены: 690 тестов, 0 ошибок/пропусков. Python 34 теста и strict docs OK.
Живой CatalogUiHarness: GUI 1/2/3 OK. Реальный GitHub: поиск 2.4.0,
скачивание 17372273 байт, SHA256 и Transactions.apply в отдельном временном
каталоге успешны, исходный файл сохранён до apply, pending удалён после.
Bundle/descriptor/SHA256 подготовлены в dist/2.4.1; изменённые классы сверены
с компиляцией. Установленный PrismLauncher клиент не изменялся. При сломанном
обновляторе старого клиента необходим ручной переход на исправленный JAR.
Удалённый CI текущих изменений ещё не подтверждён; релиз не опубликован.


CI 2.4.1 terrain phase=9 (Persisted preview pixel changed): обнаружена гонка
страниц региона. Prepared/worker callbacks проверяли styleEpoch/generation,
но не изменение исходных блоков и границ после захвата images. Добавлены
инвалидизация затронутых страниц всех LOD и отбрасывание запоздавшей композиции.
Последний GPU-кадр и дисковые fallback-превью сохраняются до свежего результата.
MapTerrainHarness принудительно удерживает worker, ставит старую композицию,
изменяет блок, проверяет инвалидизацию и затем проверяет точные пиксели с диска.
Условия точного сравнения и тайм-ауты не ослаблены; ошибка теперь содержит
регион, пиксель, ожидаемый и фактический цвет.
Живые terrain/light успешны; принудительная гонка: 110675 проверок.
Отдельный запуск процесса с сохранённой fixture: 147494 проверки, preview 233 мс,
исходный terrain IO и композиция при проверке восстановления заблокированы.
Полная сборка/verifyBundle и принудительные 690 тестов без ошибок/пропусков
успешны во внешнем buildDirectory. Рабочий iCloud-каталог снова содержал
конфликтные .class с суффиксами; его неуспешная сборка остановлена, релизный
JAR сверён с классами чистой внешней сборки. dist/2.4.1 обновлён.
Установленный клиент не заменялся; удалённый CI этих изменений ещё не проверен.


CI 2.4.1 повторно упал в queueOldPage: тест ошибочно требовал постановку
композиции в пределах одного кадра. prepareView ограничивает бюджет и может
сначала загрузить ранее подготовленную страницу, заменяя тестовый sentinel.
Исправлен только MapTerrainHarness: отдельная фаза ожидания, worker-барьер
подтверждается на игровом потоке после предыдущих callbacks; окно временно
закрыто при подготовке гонки. Готовый upload потребляется до новой композиции.
Три попытки с исчерпанным бюджетом обязательны в каждом прогоне, затем тест
ждёт фактического pending и только после этого меняет блок. Исходное точное
сравнение пикселей, инвалидизация и общий deadline сохранены.
Окончательный отдельный terrain/light: DEFERRED_PAGE_OK ticks=4, 110679 проверок.
Полный map-network выполнен в порядке CI: integrations, notifications, terrain,
ingress, publication, light, permissions, teleport — все маркеры OK, финальный
RIVET_MAP_NETWORK_OK. Terrain внутри полного прогона: 82000 проверок, deferred
на 4-м кадре; число повторных сравнений зависит от завершения загрузки страниц.
Публикации GUI 1/2/3 и default/light/contrast, права и teleport успешны.
Это локальный прогон на macOS, не подтверждение удалённого Linux CI.
Код продукта и установленный клиент не менялись; harness не входит в JAR.


2026-11-07 — W06/W09/W10: плащи, образы, откат публикаций и скрытие чата:
- CapeStore хранит библиотеку и выбранный плащ по UUID в records, общий PNG по hash;
  транзакционные квоты, проверка владельца, cleanup только без оставшихся ссылок.
  Статичные 64×32 PNG с alpha; настройки capes/maxCapesPerPlayer/maxCapeSizeKiB
  автоматически дополняют старый конфиг. Capability skin-capes. Клиент сохраняет
  официальные элитры, удерживает предыдущий плащ до загрузки нового, поддерживает
  официальный вариант/явное скрытие, предпросмотр/загрузку/удаление в SkinsScreen.
- OutfitStore сохраняет текущую пару скин+плащ, атомарно применяет оба компонента
  после проверки доступности и владельца. Capability skin-outfits, лимит
  maxOutfitsPerPlayer 1–8. Удаление образа не удаляет компоненты; удалённый компонент
  делает образ недоступным. Образы включаются в существующее колесо (максимум 8).
  Отмена пока только смены скина, это явно указано на кнопке; полный W10 не закрыт.
- PNG перед декодированием проходит CRC/структуру/порядок критических chunks;
  второй IHDR и APNG отвергаются, compressed metadata не передаются ImageIO.
  NativeImage получает нормализованные пиксели. Добавлены regression cases.
- Builder хранит previous snapshot; /rivet pack rollback [hash] готовит активацию
  на следующий запуск. До смены state проверяются size/hash всех blob; повреждение
  не меняет current/pending. Не откатывает мир/БД/серверные моды. Тестовые каналы и коды доступа отменены владельцем.
- ClientChat учитывает vanilla PlayerSocialManager для живых структурированных
  сообщений и истории. Системные alerts не скрываются. Новая история сохраняет
  автора независимо от шаблона/наличия head; для старой используется head marker.
  Скрытие не стирает уже показанные строки. Harness проверяет system/history/unhide.
- SkinUiHarness дополнен загрузкой/примеркой плаща, скрытием, образом через колесо,
  удалением. Пока только compile; Minecraft не запускался, Prism не заменялся.
- Полные core:test, core:postgresTest, mod:test прошли до последнего PNG/Chat патча.
  PostgreSQL 308 tests, 0 failures/errors, 5 штатных skips; mod 5/5. После PNG
  отдельно 8 image tests прошли. Main/UI compile после Chat прошла; первое падение
  harness использовало отсутствующий toJsonTree — исправлено на toJson + JsonParser.
- Общий план НЕ завершён: остальные Menu/Map/OP/Chat (ответы/упоминания),
  архитектурная очистка, API сверка, общий живой прогон, CI и релиз остаются.

- Финальная strict-сборка docs прошла в существующем venv (системный Python не содержит mkdocs); diff --check прошёл.


2026-11-07 — продолжение W05/W08/W09 (без живого запуска):
- MenuPlaces теперь ищет Waystones, приватные разрешённые метки групп,
  личные/публичные/групповые территории, маркеры и территории включённых
  слоёв адаптеров. ID содержат namespace/layer/source, активация заново читает
  разрешённый snapshot; территории открываются focusTerritory. Личные координаты
  в серверный поиск не передаются. MapApiLayersHarness проверяет поиск, скрытие,
  отсутствие private layer и недоступность старого результата после owner cleanup.
- Подтверждён и исправлен лишний сброс MapLayerClient.snapshot каждый тик:
  старый peersAvailable=false путь очищал snapshot даже без изменений. Удалены
  неработающий peer polling/session и ветки UI переключения. Legacy PEERS enum
  остаётся для чтения прежних настроек, недоступен; позиции обслуживает MapPositions.
- MapPlaceSnapshot применяет до 1000 мест атомарно, проверяет страницы по 20,
  UUID/дубликаты/курсор. Неизменённые данные сохраняют ссылку для кэша геометрии.
  Ошибка/тайм-аут не продлевает старый snapshot; он истекает через 60 секунд.
  Четыре unit regression: стабильность/атомарность, отказ/expiry, cursor/size, limit/reset.
- ChatMentions: точный @ник без prefix/email/Unicode-word совпадений. Клиентские
  transient HUD уведомления только по живым сообщениям, глобальный cooldown 5 секунд,
  deadline 15 секунд; собственные/скрытые игроки и история не уведомляют. Настройка
  mentions в существующем SocialSettings, поиск настроек, reset, RU/EN, docs.
  Local/group/API тело читается из существующего ChatEffectEnvelope до адаптера;
  Player event обрабатывается LOWEST с проверкой отмены, подписи не меняются.
  Клик открывает ChatScreen. Snooze и общие HUD preferences учитываются.
  RivetChatIdentityHarness дополнен живым consumer-path, пока только compile.
- Полные core:test/mod:test прошли после Map изменений; после Chat новые 7 unit tests
  (MapPlaceSnapshot/ChatMentions) прошли, main/UI compile и strict docs прошли.
  PostgreSQL не повторялся: SQL/схема не менялись. diff --check прошёл.
- Тестовые публикации Builder и коды доступа отменены владельцем; вопрос закрыт.
- Весь план НЕ завершён: ответы/цитаты чата, оставшиеся OP/HUD/
  архитектурная очистка/API и финальная проверка. Minecraft не запускался,
  установленный клиент не заменялся, remote CI/релиз не объявлялись готовыми.


2026-11-07 — W05/OP и HUD, без запуска Minecraft:
- Builder tester channels/access codes отменены владельцем. Решение внесено в
  актуальный план и CONTEXT; старые pending rollout формулировки исправлены.
- adminDashboard больше не выдаёт обращения/диагностику/Builder/таймеры целиком
  любому rivet.admin: отдельный allowlist AdminDashboardView, права проверяются
  до запросов и повторно перед отправкой; current player/Auth/store проверяются
  в callback. Клиент очищает отозванные поля и фильтрует ответы до rebuild.
- Проверка databaseHealthy выполняет реальный SELECT 1 и без модуля Reports.
- CommunityStore.mapReviewSummary выдаёт администратору count и до 6 pending
  metadata без координат. Существующий OP Overview открывает конкретную заявку;
  экран повторно загружает её и проверяет актуальную revision. Это не общая
  пагинация/фильтрация всех очередей; весь пункт OP пока не закрыт.
- Builder status различает disabled/unpublished/invalid/published; отключение
  не выдаётся за ошибку обязательной сборки. Проблемы показаны в OP Overview.
- HudDropLayout ищет ближайшее свободное место при drop с gap 4; остальные блоки
  не сдвигаются. При отсутствии места возврат; snap к центру/краям, Esc отменяет
  текущий drag, закрытие/removed также возвращает его, wheel во время drag игнорируется.
  Это не полный collision layout при изменении размера/разрешения. Unit tests
  геометрии и native interaction сценарий добавлены; native пока только compile.
- Проверки: полный core:test/mod:test прошёл после OP; PostgreSQL
  CommunityMapObjectsTest и AdminDashboardViewTest прошли. Main/UI compile прошёл.
  После HUD — targeted geometry tests + main/UI compile; финальная проверка ниже.
- Общий план не завершён: Chat replies/quotes, оставшиеся OP/HUD/Map/архитектура,
  API сверка и общий живой прогон/CI/релиз остаются. Prism не заменялся.

- Открытая OP-сводка теперь подписывается на home hints; изменения обращений
  поступают только администраторам с rivet.reports. Обновления coalesce 250 мс,
  hint во время запроса сохраняется для следующего чтения; resync восстанавливает
  сводку. Данные не передаются в hints. Исправлена ошибка компиляции после
  изменения tick: обработчик должен быть только у сводки, не во вложенной форме.
- Финальные targeted tests AdminDashboardView/HudDropLayout/MenuChangeHint,
  main/UI compile и strict docs проверены; Minecraft/native scenarios ещё не запускались.


2026-11-07 — продолжение полного плана, без живого запуска:
- AppearanceUndo хранит до/после выбора скина, модели рук и плаща; outer mutation
  под skins→capes locks сохраняет одну отмену для образа/скина/плаща. Новая
  capability appearance-undo, прежний skin-undo сохранён для старого клиента.
  Отмена атомарна, одноразовая, RequestJournal replay не выполняет её повторно;
  missing references/изменённый текущий выбор/выключенные функции запрещают
  устаревшую отмену. UI показывает действие и в библиотеке плащей. SkinUiHarness
  обновлён на новый action и проверку скрытого плаща после отмены образа.
- PostgreSQL OutfitStoreTest/SkinStoreTest/CapeStoreTest прошли; main/UI compile
  прошёл. Full appearance live пока не выполнялся. Документация обновлена.
- Исправлена гонка группового живого чата: deliverGroupChat удерживает FOR SHARE
  на документе группы до фактической отправки через BoundedDelivery (deadline
  3 секунды), заново проверяются epoch/подключение/Auth/modules. Убран второй
  запрос playerGroups только ради имени. PostgreSQL проверка NOWAIT показывает
  блокировку смены состава до callback и снятие после; прошла, compile прошёл.
- MapRepository.readRecovering отделяет повреждение формата от IO/permissions и
  новой неизвестной версии. Повреждённый tile сохраняется .corrupt-UUID, участок
  можно переснять; JSON меток/категорий/территорий не меняются. Чтение bounded
  196609 bytes, каталоги/symlinks отвергаются без перемещения. WorldMapClient
  использует восстановление для own/legacy tiles. MapRepositoryTest прошёл.
  MapPersistenceHarness расширен на реальный consumer path, пока только подготовлен.
- Общий план всё ещё в работе; не объявлять готовность/релиз и не запускать
  Minecraft до всех изменений. Не заменять установленный клиент. Пользователь
  просит не останавливать работу промежуточной итоговой сводкой.

2026-11-07 — владелец отменил ответы/цитаты чата. Удалены ChatReferences, кнопка ↩, reply/original команды, настройки и связанные тесты/docs. Сохранены прежние !/#/обычный текст и исправление гонки доставки группового чата. Не возвращать эту функцию в план.

2026-11-07 — Map/HUD и актуальные проверки данных, без Minecraft:
- HUD: wheel resize проверяет фактические границы и ближайшее свободное место;
  при отсутствии места размер возвращается, сохранение только после проверки.
  Закрытие/смена разрешения отменяют незавершённую правку. Native-сценарий подготовлен.
- Map: Source держит WeakReference на исходный tile, не удерживая вытесненные
  выборки. Проверки same/revision/collected source прошли. Валидатор biome
  скомпилирован один раз, неизменившийся biome не требует повторной проверки.
- MapDataScreen показывает локальные миры/профили, полный размер и terrain.
  Явная очистка только вне мира после завершения IO; метки, категории, территории,
  неизвестные файлы и другие миры сохраняются. Учтены maps-received и region index.
  Четыре storage-теста прошли; native просмотр подготовлен, не выполнялся.
- Community: убраны недоступные ветки derived/task mutation/card presentation;
  архивные данные остаются. Restore использует retention.trashDays вместо 30 дней;
  policy и реальная PostgreSQL mutation проверены на 7/60-дневных границах.
- Полный PostgreSQL прогон после удаления веток: 314 тестов, 5 opt-in пропущены,
  ошибок нет. Mod tests 7/7; tooling ранее 52, один PG skip; strict docs прошёл.
- Обнаружены устаревшие opt-in load fixtures удалённых задач. MixedModuleLoadTest
  переведён на группы/приватные места/чат/уведомления; TaskDetailLoadTest заменён
  GroupDetailLoadTest (101 участник, 200 мест, 200 архивных задач). LargeRead
  проверяет только доступные группы и отказ архивных разделов. LargeRead прошёл;
  GroupDetail и Mixed(1 игрок) после исправления fixture type прошли (0 failures).
- Документация Map, API, конфигурации и performance-data приведена к текущим
  сценариям. Живые проверки, нагрузка 100 игроков и окончательный CI ещё впереди.
  План не завершён, установленный клиент не заменялся.

2026-11-07 — продолжение Map/OP/нагрузки:
- MapCoverage повторно использует неизменяемое покрытие текущего viewport;
  убраны повторные коллекции/sort при каждом warm/restored/prepareView. Два
  изолированных теста проверяют слои, отрицательные координаты и смену viewport.
  Первоначальный тест напрямую outer renderer не работал без Minecraft classes;
  расчёт выделен, после этого mod tests (9) и UI compile прошли.
- Поздний legacy tile callback больше не заполняет receivedTiles после eviction.
- Частые настройки отрисовки видимы сразу, расширенные раскрываются в том же
  разделе. Поиск раскрывает нужное поле. UI harness обновлён, strict docs прошёл.
- ReportQueue подписывается на reports, coalesce refresh 250мс и resync;
  изменение revision снимает устаревший выбор для массового действия.
- Mixed 100 accounts/8 workers/24000 ops: до оптимизации elapsed 46.530s;
  p95 group-write 68.647ms, private-place-read 66.868ms, group-list 8.994ms,
  chat-write 5.412ms, chat-replay 2.967ms, notice-read 8.593ms. Это SQL workload,
  не 100 подключённых Minecraft клиентов. Проверки изоляции прошли.
- Найден global community:groups lock на plusItemRead. Чтение перенесено на
  один SQL snapshot документа/членства/метки, без полного relation enrichment.
  Read обходит RequestJournal, чтобы operationId не воспроизводил старый
  приватный ответ после отзыва членства. Регрессия проверяет read при удержанном
  global lock и повтор того же operationId после удаления участника.
- Последние изменения OP/private read в проверке; Minecraft ещё не запускался.

- После оптимизации private-place-read: p95 7.279ms вместо 66.868ms;
  Mixed100 elapsed 39.744s вместо 46.530s. Другие метрики колебались/часть выросла,
  это последовательные локальные SQL прогоны, не универсальная гарантия FPS/сети.
  CommunityPlusTest включая блокировку/replay после удаления участника прошёл.
- Правило read без mutation receipts обобщено через MenuCommands.communityRead,
  чтобы повтор operationId не возвращал приватные ответы других read-операций.
- MapCacheBudget уменьшает потолки подробных samples/images/overview/edges на
  небольшом heap (1024–8192 подробных entries); disk previews уже ограничены,
  оригинальное исследование автоматически не удаляется. Это потолки entries,
  не точная квота памяти процесса. Добавлена проверка границ/overflow.
- Из HUD/map hints исключены удалённые topics, удалены неиспользуемые taskNote.
  ReportQueue принимает ответы и с открытым ChoicePopup. Обновлённые core/full PG
  проверки продолжаются; после последних правок потребуется повтор compile/test.

2026-11-07 — проверки и библиотека мест:
- Full core:test/core:postgresTest/mod:test + UI compilation прошли после
  унификации read без receipts. После удаления taskNote исправлена legacy fixture:
  старое уведомление теперь создаётся как сохранённая запись NotificationStore,
  без production helper удалённых задач. Все core/mod tests и targeted
  NotificationStorePostgresTest прошли. Tooling: 52 tests, 1 штатный skip.
- MenuPlaces.browse даёт bounded страницы (50+lookahead) с фильтрами personal,
  public, groups, adapters. Новый MapPlacesScreen открыт из существующего drawer
  меток, использует общий UI Kit и локальный поиск. Показываются только загруженные
  разрешённые объекты, выбор повторно получает актуальный snapshot. Старые
  personal/category/publications entrypoints сохранены. Документация markers
  обновлена; native geometry/return/source filter сценарий подготовлен.
- Main/mod tests/localization прошли для библиотеки; native harness сначала не
  скомпилировался из-за отсутствующего import Json, исправлено; повтор compile
  выполняется. Strict docs прошёл. Никаких запусков Minecraft/замены Prism.
- Для финальной приёмки всё ещё нужны: закрыть оставшиеся продуктовые пункты
  (в частности preview разных viewport/GUI в HUD, итоговая ревизия OP/API и
  архграниц), затем общая изолированная игровая матрица и exact-commit CI.
  Весь план готовым не объявлять. Тестовые каналы и цитаты чата отменены.

- Библиотека мест: исправлено сохранение фокуса при фоновом обновлении и обновление
  кнопки следующей страницы при изменении lookahead. Main/UI compile завершился
  успешно, generated RU/EN соответствуют исходникам. Strict docs/diff check прошли.
- На следующем этапе не повторять уже успешные проверки без изменений. Продолжить
  HUD viewport/GUI preview, сверку общего плана OP/API/архитектуры; затем общий
  живой прогон. Текущая версия в gradle.properties всё ещё 2.7.0; новый релиз
  не упакован/не опубликован, remote CI не проверен. Никаких фоновых тестов не осталось.

2026-11-07 — HUD viewport preview и изоляция уведомлений:
- HudPreviewScreen использует реальные рендереры внутри HudViewport для четырёх
  разрешений и GUI 1–3. Размер окна и сохранённые настройки не меняются.
- Исправлено расходование времени настоящих уведомлений при показе образцов:
  реальная очередь приостанавливает presentation lifetime; deadline сохраняется.
  Клики по образцам отключены, normal HUD не рисуется поверх preview/popups.
- Scissor в Minecraft 1.21.1 не учитывает pose: ChatHints применяет координаты
  preview явно; вне scope остаются прежние координаты. Удалён dead overdue-task HUD.
- Native HUD harness покрывает 12 viewport/scale combinations, неизменность окна
  и сохранение настоящих уведомлений. Это подготовленные сценарии, live не запущен.
- mod:test и compileUiHarnessJava прошли. Документация HUD/UI Kit обновлена;
  таблица владельцев данных исправлена на ServerDataRuntime/ServerClientDelivery.

2026-11-07 — кандидат 2.8.0 и начало общей приёмки:
- rivetVersion поднята до 2.8.0: новые возможности, автоматическая конфигурационная
  миграция и сохранённые архивные данные; RELEASE_NOTES переписан по текущему составу.
- Полный build успешен: api 4, bootstrap 7, core 790, helper 10, mod 9 tests,
  без failures/skips. VerifyBundle и архитектурные guards прошли.
- Tooling 52 tests / 1 штатный skip; strict docs успешен; generated RU/EN совпадают.
- После реализации начат изолированный живой прогон. HUD 216 сценариев прошли
  (включая новые preview, три масштаба, светлую/контрастную и образцы всех тем).
- Сетевой прогон использует отдельную новую БД rivet_280. Первый запуск остановился
  до сервера из-за отсутствующего CA-файла локального Python; повтор использует
  системное доверенное хранилище с включённой проверкой TLS. Это не баг Rivet.
- Все остальные live сценарии, exact-commit CI и публикация ещё не подтверждены.
  Установленный клиент не заменён; артефакт пока только локальный кандидат.

2026-11-07 — общий живой прогон (продолжается):
- Исходные 12 map_network scenarios прошли: chat, realtime (3 reconnect), heads,
  integrations, notifications (59 checks, 27 frames), persistence, terrain, ingress,
  publications, light, permissions, teleport. Terrain p95 render 4.435ms; сохранённое
  preview 330ms, холодный fixture 9216 chunks 2661ms. Локальный стенд, не гарантия FPS.
- Добавлены постоянные сценарии wardrobe и module в map_network, чтобы новые
  плащи/образы и окна карты не оставались только ручными проверками. Проверка идёт.
- AuthFactorNetworkFlow через реальный TLS/modal успешно проверил настройку TOTP,
  ротацию backup codes, отказ старого кода и отключение свежим. Драйвер учитывает
  700ms action throttle и 1s cooldown после неверного кода; защита не ослаблена.
  role_network теперь требует отдельный маркер второго фактора.
- В UI fixtures исправлены переход в удалённые events, отсутствующие type/recruiting
  группы и выбор Members через настоящий UiTabs dropdown на GUI3. Продуктовые
  проверки сохранены. Полная матрица RU/EN повторяется; готовой её не считать.
- Role fixture требует пустую БД: повтор в использованной БД закономерно отказал
  регистрации RenameBefore. Последний прогон — отдельная rivet_280_roles_final.

2026-11-07 — продолжение общей приёмки:
- Role network завершён: 255 проверок, шесть ролей, 36 frames отключения модулей;
  TOTP/backup rotation/replay denial/remove прошли через настоящую Auth TLS/UI цепочку.
- Wardrobe и Map module прошли. Найден и исправлен production UI drift:
  MapDataScreen теперь использует общий UiActions.Command.REFRESH.
- RU native/adaptive/reorder/settings/pack/approved/tab прошли; EN ещё выполняется.
  AuditFixes fixture больше не требует удалённое действие событий профиля.
- API нагрузочный и proxy/browser fixtures переведены с удалённых tasks на
  приватные уведомления; проверки изоляции и отзыва сохранены. 2000 REST requests,
  100 WS users/13 connections, sustained 6000 requests/60s без ошибок. p95 267ms
  в совместном локальном прогоне; Java 660 accepted/340 штатный BUSY.
  Browser восемь проверок прошёл, Nginx TLS/WSS прошёл. Не тест 100 Minecraft клиентов.
- Все четыре optional adapter scenarios прошли; Envelope Auth on/off и restart
  прошёл. B&C с реальным модом и combined-update матрица ещё выполняются.
- Tooling 52 tests, 1 штатный skip прошли в окружении docs с loopback sockets.
  Первый локальный запуск без jsonschema/разрешения сокетов был ошибкой окружения.
- Docs api-sessions исправлен: удалённые scopes убраны, поле code для TOTP описано.
  Exact-commit CI и публикация по-прежнему не подтверждены. Клиент не заменён.

- Дополнительная приёмка: PostgreSQL 17 — 315 тестов, 0 failures/errors,
  5 opt-in skips. Смена ника из игры подтверждена серверным маркером: UUID,
  пароль, LuckPerms, whitelist и scoreboard сохранены у администратора без OP.
- B&C 4.5.0 с настоящими модами на клиенте и сервере: sober/tipsy, local/group/API
  envelopes и кликабельные действия прошли. Первый стенд имел мод только у клиента
  и корректно получил отказ NeoForge; повтор с обеими сторонами успешен.
- Повтор API browser идёт с notifications.write: mutation denial должен зависеть
  от браузерного транспорта, не от отсутствующего scope. Первый load/browser и
  Nginx TLS/WSS прошли; новый повтор не завершён.

- Полная UI matrix RU/EN завершена: native/adaptive/reorder/settings/pack/approved/tab,
  GUI1–3, default/light/contrast, остальные темы sampled. HUD отдельно 216 frames.
- Финальный API load/browser с правом notifications.write прошёл: 8000 REST reads,
  100 WS subscriptions и 8 browser checks. Nginx TLS/WSS проверен ранее.
- Saved world renderer прошёл: surface/isometric/caves/materials/masks/noChunkLoads.
- В ApiServerHarness ещё найдены ожидания удалённых tasks/events: HTTP сценарий
  перенесён на groups/notifications, Java создание события теперь проверяет
  UNAVAILABLE. MapActivitiesWireHarness ждёт только groupmarkers/territories и
  проверяет отсутствие retired layers. Native API + scheduler повторяются.
- Combined update: direct полная GUI/profile matrix прошла; proxy в работе,
  fallback впереди. Не считать общий live прогон завершённым до итоговых маркеров.

- Native API (LuckPerms) после переноса fixtures прошёл: console keys, REST/WS,
  browser tickets, offline staff, reports, group markers, retired event denial,
  server control, map privacy и render geometry. Scheduler crash/recovery два этапа
  прошли с сохранением cursor/queue/dirty/cancelled и запретом загрузки чанков.
- Packaged module lifecycle прошёл default/disabled/Auth base. Harness обновлён
  на ServerDataRuntime вместо удалённых STORAGE/CONTROL/READS полей фасада:
  проверяет все три реальные очереди, termination и обнуление owner/store.
- Combined direct и proxy: все GUI1–3 × default/light/contrast прошли, один
  transaction, exact hashes, reconnect/alias без переустановки. Fallback продолжается.

## Итог локальной приёмки 2.8.0 — 2026-11-07

- W00–W10: реализация и локальные регрессии завершены. W11: контракты,
  документация, локальная упаковка завершены; exact-commit CI/публикация не выполнены.
- 820 обычных Java tests (api 4, bootstrap 7, core 790, helper 10, mod 9),
  без ошибок. PostgreSQL 17: 315/0 failures/5 opt-in skips; PostgreSQL 18
  проверен ранее полным прогоном и текущими native/load scenarios.
- Tooling 52/0 failures/1 штатный skip; strict docs и diff check успешны.
- Native: 14 map-network сценариев, роли (255 проверок / 6 ролей / 36 module frames),
  TOTP/backup rotation/replay, rename из игры без OP, RU/EN UI matrix и HUD216 прошли.
- Адаптеры: четыре optional scenarios, B&C all-channel с модом 4.5.0, Envelope
  Auth on/off и restart прошли. Native API enabled/disabled, offline LuckPerms,
  saved terrain и восстановление scheduler прошли. Module lifecycle default/disabled/base
  прошёл на packaged JAR, проверены termination и очистка владельцев.
- API: 100 identities, 100 WS subscriptions, 8000 REST reads, Java queue saturation,
  отзыв/повторная привязка и восемь browser checks прошли; Nginx TLS/WSS тоже.
  Это синтетический нагрузочный стенд, не сто одновременно запущенных игр.
- Builder: 27 direct/proxy/fallback × GUI1–3 × default/light/contrast сочетаний
  прошли. Дополнительно direct/proxy/fallback с preferIPv6Addresses, GUI3/default
  прошли. Каждый профиль: одна транзакция, exact JAR/pack hashes, личный конфиг
  сохранён, повторный и alias reconnect без переустановки. Проверялся изолированный
  настоящий TCP relay; внешний production playit не проверялся.
- Кандидат: dist/2.8.0-candidate, JAR + SHA256SUMS + RELEASE_NOTES.
  Никакого commit/push/tag/publish не выполнялось, установленный Prism клиент
  не изменён. Старый fixture обновления использует текущий updater со старой
  metadata: это не доказательство обновляемости всех исторических выпущенных JAR.

Финальная локальная проверка кандидата 2.9.0 (2026-11-08): build/verifyBundle,
828 Java tests / 0 failures; tooling 52 / 1 штатный skip; strict docs 80 страниц,
0 битых ссылок. Native chat: custom triggers, empty/default routing UI,
повторные local warnings, личный shortcut и /msg /w /tell; 3-client audience check
подтвердил получателя и изоляцию observer. dist/2.9.0-candidate перепакован,
SHA256 и ClientChat/mixin внутри game.jar сверены. Exact-commit GitHub CI,
commit/push/tag/publish не выполнялись. Установленный клиент не заменён.

После CRLF-прогона полный build/verifyBundle с LF и --rerun-tasks: 829 tests,
0 failures/errors, все 34 задачи выполнены. Кандидат 2.9.0 перепакован и SHA256
проверены. Remote exact-commit CI не запускался; установленный клиент не изменён.
