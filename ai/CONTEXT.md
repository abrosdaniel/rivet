# Принятые решения

## Продукт

Rivet предназначен для игроков и владельцев серверов. Установка — одним JAR.
Внутренняя модульность не означает отдельные устанавливаемые дополнения.
PostgreSQL обязательна для серверных функций, даже при отключённой авторизации.

## Настройки и интеграции

- Опциональные моды подключаются автоматически через адаптеры. Выключатели
  интеграций не добавлять без прямого запроса владельца проекта.
- Настройки сервера должны быть доступны в файле с пояснениями; новые поля
  добавляются с сохранением значений владельца и резервной копией.
- Чат Rivet имеет один серверный выключатель. Каналы и формат настраиваются;
  отдельного режима compatible для чата нет. Режимы TAB независимы от чата.
- Навигация действует до достижения цели или ручной остановки; таймер не нужен.
- Упоминания @ник, вставка [item] и команды статусов исключены из продукта.

## Интерфейс

Сохранять сценарии работы: профиль, настройки и формы — диалоги.
Не добавлять главные разделы и не заменять диалоги страницами без запроса.
Основные разделы закрывают меню; вложенные возвращают к родителю.
Перестановка порядка — перетаскивание; без стрелок.
Страница задач — ориентир общей геометрии списков и подробностей.

## Проверки и выпуск

Версия — MAJOR.MINOR.PATCH по влиянию на пользователей, а не объёму кода.
Успешная локальная сборка с кэшем не подтверждает чистый CI.
Диапазоны сторонних библиотек закреплены за Maven Central, чтобы не зависеть
от ответов посторонних репозиториев.
В CI PNG отключены; UI-сценарии сохраняют проверки поведения и геометрии.
Полные сценарии используют окно 1280×1024; адаптивные меняют размер явно.

CI разделён на быстрые проверки Linux/PostgreSQL 18/инструментов и полную
матрицу для новой версии в основной ветке или ручного запуска. Полная матрица
включает TAB и packaged-server lifecycle; PNG отключены. Тег существующей версии
не запускает повторную публикацию. GitHub CI нельзя считать пройденным без наблюдения запуска.
Внутренняя модульность реализована по MODULARITY_PLAN.md; текущие границы описаны
в docs/development/modules.md. Дальнейшее разделение на устанавливаемые JAR не разрешено.

## Документация

Служебные заметки и инструкции агента хранятся только в ai/.
Публичные документы описывают текущую работу проекта, без истории разговора
и напоминаний о переходе между конкретными версиями. Технические комментарии,
лицензии, тесты и обычные release notes остаются частью проекта.

## Проверка изменений CI

Проверены actionlint, синтаксис shell-шагов, 66 тестов инструментов, сборка
и упаковка, 126 сценариев TAB/диагностики скинов без PNG. Все три режима
нового серверного CI-сценария (default, disabled, base) прошли на временной
PostgreSQL; запуск и освобождение ресурсов подтверждены маркерами.
Результат 198 адаптивных UI-сценариев также подтверждён валидатором.
Логи скинов не содержат сообщения исключения или payload; повторение ограничено.
GitHub Actions для коммита с изменённым workflow прошёл: plan, contracts,
Linux build и PostgreSQL 18. UI, modules и release были пропущены, поскольку
версия 1.3.1 уже имела тег; это не подтверждение полной матрицы выпуска.

## Будущие модули

Идея собственного модуля карты записана в ai/FUTURE_MODULES.md: карта мира,
миникарта, метки и навигация в одном внутреннем модуле. Xaero’s — ориентир
для подходящих механик. Реализация ещё не разрешена, детали требуют уточнения.

Для будущего модуля карты подтверждены личные, объединённые и серверные
метки с правами на общие метки; вместо букв — набор из 50–100 иконок.
Навигация — направление и расстояние без расчёта пути. Модель личного исследования с добровольным обменом внутри объединения
подтверждена. Подтверждены основные блоки карты, точки смерти, пещеры,
обмен и импорт меток; добавлены линейка и метки территорий. Важна автоматическая
опциональная интеграция Waystones. Игроки/существа приняты как идея,
конкретное поведение обсуждается отдельно. Детали и этапы — в FUTURE_MODULES.md.

## Подготовка выпуска 1.3.2

Версия 1.3.1 уже имеет тег; после неё исправлены TAB и диагностика скинов,
добавлена внутренняя модульность без новых пользовательских функций. Выбрана
patch-версия 1.3.2; gradle.properties и RELEASE_NOTES обновлены. Идеи карты
не входят в выпуск. Сборка с --refresh-dependencies, 66 тестов инструментов,
проверка шаблона, actionlint и локальная упаковка релиза прошли.
Полная GitHub-матрица для 1.3.2 ещё не запускалась; публикация и commit
не выполнены. Ранее подтверждённые локальные UI и серверные проверки
относятся к тому же исходному коду до изменения номера версии.

## План серверного управления модулями

Пользователь подтвердил необходимость улучшить серверный конфиг: ненужные
проекту функциональные модули должны отключаться, с явными зависимостями
и сохранением данных. Это будущая работа, пока не реализована; подробности
в ai/FUTURE_MODULES.md. Правило автоматических опциональных адаптеров
сохраняется; выключатели модулей не означают выключатели интеграций.

Для карты подтверждены многоугольные территории по блокам с допустимыми
пересечениями (конфликты решает администрация). Полученная карта сохраняется
после выхода из объединения; дальнейший обмен прекращается. Игрок фильтрует
отображение игроков/существ и выбирает передачу своей позиции: вся карта,
рядом либо скрыто. Аудитория по умолчанию — все игроки, с возможностью сменить её; режим
по умолчанию — «Только рядом», радиус задаёт сервер, в том же измерении.
Выключатели модулей применяются после перезапуска; конфликт зависимостей
выдаёт понятную ошибку конфигурации. Подтверждена телепортация с карты через Waystones с сохранением его
условий доступа, стоимости и ограничений; поддерживаемый API ещё проверить.

## Следующий этап модульности

В ai/MODULARITY_PLAN.md записано подтверждённое дальнейшее разделение
социальных функций, управления сервером и оформления. Общие службы остаются
общими. Эти работы и карта — приоритет; сейчас только собираются идеи.
Предложенные журнал мира, каталог ресурсов, логистика и торговля не приняты.
Реализацию не начинать без отдельного запроса.

## Режимы имён над головой

Реализовано display.nameplates = "rivet" | "base" | "hidden". Режим задаёт
сервер, применяет обновлённый клиент Rivet; scoreboard не меняется,
клиенты без Rivet и старые клиенты hidden не поддерживают. Старые булевы
значения сохраняют смысл: true → rivet, false → base; обычные записи конфига
обновляются с сохранением комментариев и резервной копией. Новый режим
передаётся отдельным необязательным полем nameplateMode, прежний
nameplatesEnabled сохраняется для старых клиентов.
Проверены чистая сборка, 26 тестов ServerSettings, 66 тестов инструментов
и реальные клиентские RenderNameTagEvent для всех режимов, отсутствия
метаданных, нестандартного имени, неигровой сущности и старого сервера.
Это новая функция после подготовки 1.3.2; прежняя готовность релиза
не распространяется на неё. При следующей подготовке выпуска пересмотреть
MINOR-версию и release notes; GitHub CI этих изменений ещё не запускался.

## Совместимость отображения чата

Клиент применяет шаблон Rivet только к ChatType.CHAT и только если сообщение
события совпадает с исходным оформленным сообщением Minecraft. Личные,
командные сообщения, /me, /say и изменения других обработчиков сохраняются.
Корневую подсказку автора снимает только у структурированной личности Rivet.
Повторы системных сообщений сравниваются по полному Component, включая
стили, подсказки и действия; удаляется только совпадающая последняя запись.
Проверены сборка и нативный ChatCompatibilityHarness: типы сообщений,
чужой текст/цвет/подсказки, разные ссылки и настоящие /tell самому себе и /me.
GitHub CI этих исправлений ещё не запускался.

## Цветные метаданные TAB

TAB сохраняет Component префикса/суффикса LuckPerms, вместо объединения
плоских строк; подсказка при наведении тоже сохраняет стили. Общий UiKit.fit
для Component сокращает текст по реальной ширине с сохранением оформления.
Проверены hex и legacy цвета, жирность/курсив, пределы ширины и 126 нативных
сценариев TAB (GUI 1/2/3, плотность, головы, списки и прокрутка).

## Независимые функциональные модули

Пункты 2 и 3 дополнительного плана реализованы. FeatureModules фиксирует политику
запуска: groups/tasks/storage/board/events/polls/ideas/reports/server.enabled.
Новые поля попадают в существующий конфиг автоматически с комментариями и backup;
переключение требует перезапуска. Прямые запросы, уведомления, фоновые повторы,
очистка и клиентские действия учитывают политику. Отключение сохраняет записи;
очистка объединений не удаляет данные отключённых задач/хранилищ через каскад БД.
Личные задачи не требуют объединений; storage требует tasks. Интеграции по-прежнему
автоматические. CommunityModules владеет маршрутами документных функций и задач;
ServerTaskStocks, ServerReportsModule, ServerManagement — ресурсами своих функций.
TAB и имена над головой регистрируются самостоятельно в ClientModules.

Проверены тесты PostgreSQL и настоящий жизненный цикл сервера в трёх режимах,
нативная политика клиентского меню и прав, 126 сценариев TAB GUI 1/2/3 и 66 тестов
инструментов. Локальная сборка проверяется без UI harness в поставке; GitHub CI
не запускался. Выпуск/изменение версии отложены пользователем. Следующий модуль —
карта по согласованному FUTURE_MODULES.md, реализация карты ещё не начата.

## Изоляция нативных клиентских проверок

Сборка с rivetUiHarness использует отдельный sourceSet uiHarness и собственную
задачу компиляции, не добавляя тестовые исходники в main. До исправления
обычная компиляция могла удалять тестовые классы из общего output во время загрузки
Minecraft, вызывая ClassNotFoundException при регистрации EventBusSubscriber.
Не использовать общую папку классов для тестового и обычного клиента.

## Server-owned pack replacement
GitHub seeds/catalog are removed, including template, registry, repository APIs,
lock schemas, related screens/tools/tests and seed CI. Current pack work is under
core.pack, ServerPack and ServerPackClient. Shared installation transactions and
Rivet self-update remain. See PACK_MODULE_PLAN.md for pending work; do not call the
entire replacement release-ready yet. A clean build, core and tooling tests passed;
packaged Minecraft status advertisement and TLS publication retrieval passed.

## Bundled server catalog
The title screen has one native Minecraft 20×20 icon update button, four pixels
to the right of multiplayer. Tooltip: Rivet version, plus available version
on a second line when an update exists; gold outline for updates. Server addresses ship
in mod resource rivet-servers.json and reconcile into Minecraft servers.dat.
Catalog entries cannot be deleted/edited in the native list; personal entries and
ordering remain. Core reconciliation tests (7) and native title/list checks at GUI scales 1/2/3
passed, as did bundle verification. Catalog currently includes the previously provided Ruslaanchik
address; confirm additional entries with owner. This is not the removed pack catalog.

The update dialog is centered, uses a content-sized height (up to five visible
version rows), separates installed version from selectable releases, and has
side-by-side install/close actions. Native checks at GUI 1/2/3 cover 0/3/20
releases, centered bounds, selection and return to the parent.

Title branding includes Rivet version in the native lower-left NeoForge branding
renderer, preserving white text, shadow, line spacing and title fade. Native
client verification confirms exactly one Rivet line; bundle verification passed.

Rivet update metadata is cached persistently for 15 minutes, with a 5-minute
retry pause after failure and fallback to cached metadata up to seven days old.
Concurrent requests share one fetch per URL; startup checks and the release
selector share the cache. Artifact source, compatibility and hash validation
remain in CoreUpdater. Six cache tests and core tests/bundle verification passed.
The native update button pulses gold smoothly over 2.4 seconds; disabling
accessibility animations keeps its border static.

Review fixes: update metadata also retains results/backoff in memory if disk
writes fail; eight cache tests pass. The release dialog supports retry after
network or queue errors and retries Hub initialization asynchronously. Catalog
sync validates servers.dat before native load (which otherwise swallows errors),
checks persistence before advancing tracking, and clears icons on address changes.
Native GUI 1/2/3 checks cover damaged NBT preservation and retry availability.
Core tests and bundle verification pass; full server-pack flow and source adapters
remain pending as listed in PACK_MODULE_PLAN.md.


## Pack source adapters and end-to-end installation
Exact Modrinth version and CurseForge file URLs resolve on explicit preparation.
Source checksums and sizes are verified in a private cache, then published as immutable
SHA-256 objects. Optional source mods reference component IDs. CurseForge credentials
come from pack.toml curseforgeKeyEnv; no secret enters a manifest. Distribution denial
and missing API download URLs fail without unofficial URL fallback. Native JAR metadata
checks client dependencies/version ranges and component co-availability, including
bounded JarJar inspection. Maven version parsing is provided by NeoForge at runtime.
Pack settings add the new key/comment with a private backup, preserving owner text.
Publication requests cannot accumulate in an unbounded queue. Modified owned files
are highlighted before replacement/removal. Guides distinguish player and owner flows.

Core source adapter tests include corruption, credentials, restrictions, missing and
wrong-version dependencies, component safety, nested dependencies and settings backups.
Real Modrinth download passed; live CurseForge remains unverified without a key.
Native client installed a real mod through server TLS, helper applied after process exit,
then client loaded that mod and entered the world; personal config and trust survived.
Packaged server ran source preparation/status/TLS and shut down successfully.
Full network-enabled build and bundle verification passed; tooling tests passed.
Proxy fallback is deferred by the user. Version/release remain postponed; no CI result claimed.

Verification totals: 279 core tests, 7 helper tests, 23 tooling tests. Complete
install/restart/reconnect flows passed at GUI 1, 2 and 3 in isolated client folders.
Native test harness remains excluded from the distributable JAR.


## Pack review fixes
Missing status advertisement no longer permanently blocks a previously trusted client
when the owner disables pack.enabled. Ordinary server compatibility/auth/required-hash
checks still run on connection; stored trust is retained for future re-enabling.
Pack components open before connecting from the native multiplayer list using the Pack
button next to Join Server. ServerInfo retains the publication-status dialog only.
Configure-only flow always shows saved choices without connecting, and guards pending installs.
Projected client mod identities are checked after downloads and before transaction
creation: removed owned files do not count, renamed owned updates pass, personal
duplicates stop installation without mutating game files or state. New regression tests
cover both cases. Native configure dialogs passed GUI 1/2/3, and a trusted client entered
a real packaged server with pack.enabled=false. No release/version change requested.


## Pack switching UX
Native multiplayer list adds Pack beside Join Server, with native button styling,
selection-dependent activation, LAN support and adaptive row widths. Existing catalog
edit/delete protection remains. Configure flow reviews changes before staging and returns
to the server list. No-file changes commit metadata without a helper or restart; matching
publications on different servers also commit their distinct server identity before joining.
Managed config/defaultconfigs preferences use per-server content-addressed snapshots in
rivet/pack-settings, committed with transactional state. Switching restores missing-policy
preferences or target defaults; replace-policy still enforces published content. Personal
pre-existing missing-policy configs are not acquired. Removed/deselected owned configs
are backed up and removed, while snapshots retain their preferences for re-enabling.
Tests cover A-B-A-B choices/configs, personal preservation, optional config toggling,
changed files after review and corrupted saved preferences. Core suite: 285 tests pass;
network-enabled build and distributable verification pass. Native GUI 1/2/3 covers selecting
a server, entering components before connection, review/save without restart and return.
Version and release remain postponed; no GitHub CI result claimed.


## Repository audit after pack UX
Reviewed production references, runtime entry points, resources/configuration, public docs,
network handshake, pack transfer/install, packaged output and build/tooling checks.
Removed obsolete GitHub project section from the server template; existing owner keys are
ignored without rewriting their text. Reports now persist packHash instead of obsolete
packVersion/repository fields; history remains intact. Removed the redundant Hub state
wrapper. Network-enabled build/verifyBundle and 286 core tests pass; 23 tooling tests pass;
9 report PostgreSQL tests pass on an isolated PostgreSQL 16 instance. Removed catalog
classes and temporary preview code are absent from the production JAR.

Resolved audit findings:
- PackModAudit uses NeoForge’s JarJarSelector, validates selected nested libraries rather
  than rejecting shared embedded copies, and checks mandatory/individual optional packs.
  Root duplicates, incompatible ranges, selected dependencies and extraction bounds remain checked.
- Hub performs cache housekeeping at startup. Objects/partials older than seven days are
  eligible only when unused; active ownership is protected, pending/recovery/busy installs
  skip cleanup. Pack downloads share object leases with cleanup. Saved preferences and
  transaction backups are outside the cleaned object store. Malformed protection state
  skips cleanup without blocking client startup.

Compatibility: same MAJOR, full client version >= server version, and exact pack/auth/menu wire versions; optional feature
intersection, nonce and deadline. Required-pack digest is reported by the ordinary client,
not remote attestation. Client-side direct pack flow additionally requires exact declared
Minecraft/NeoForge. Updated owner guide. Release remains postponed.

The minimum-version rule is explicitly approved by the user. Both handshake endpoints
use server/client argument order; the disconnected release picker receives the full
server requirement and excludes older releases. Guides and agent instructions updated.
298 core tests and network-enabled build/verifyBundle pass. Native Minecraft pack
configure/review/save flow passes at GUI 3 in a separate client, leaving the user’s client
running. Generated resource conflict copies were moved out of build output before
repackaging; source assets were not changed. No version bump/release/commit requested.

## Projected client mod dependencies
Reproduced server switching that removes an owned library required by a retained
personal mod. PackInstaller now audits the full projected NeoForge mod set before
preparing a transaction: removals/replacements/selected optional components and
personal mods. Same-publication no-change connect path also audits. Publisher mode
still rejects bundled Rivet; installed mode accepts the actual Rivet version and
unwraps the launcher bundle’s game module, with existing nested bounds. Personal
library JARs without NeoForge metadata are preserved; corrupt JARs fail validation.
Tests cover personal missing/wrong-version dependencies, incompatibilities, optional
deselection, safe switching, SERVER-only dependencies, Rivet runtime/bundle and shared
JarJar. No automatic deletion of personal mods or installation of new dependencies.
Verification: 305 core tests pass; network-enabled build and verifyBundle pass.
Native pack configure/review/save passes at GUI 1/2/3 in an isolated client folder.
Projected audit of the actual distributable Rivet bundle also passes, including
its embedded game module/JarJar metadata. User preview client was left running.
No GitHub CI result, version bump, release or commit claimed.


## Expanded project audit implementation (2026-10-07)
A1–A8/U1–U4/D1–D9/T1–T2 implemented per PROJECT_AUDIT_PLAN.md; U5 shortcuts
intentionally retained. Stable settings IDs, actual retention metadata, independent
home search/profile actions, accessibility-preserving presets, review before report
send, late pack-check isolation, optional publication status and real RU/EN common
actions. Existing dialogs/sidebar navigation retained. Settings event details collapse
within their original dialog. Dead news/rules presentation remnants removed.
UI demonstration classes moved to uiHarness; release JAR clean. Historical profile
fields, migrations and active integrations preserved. Full remaining Russian-literal
inventory is internal in UI_LOCALIZATION_INVENTORY.md/.csv, not a completed full
localization claim. Latest verification is recorded at the end of PROJECT_AUDIT_PLAN.md.
480 Java tests (166 PostgreSQL) and 23 tooling tests pass; network build/verifyBundle
pass. Native settings, UI Kit, adaptive, reorder, task/navigation and community-plus
flows passed. Default/disabled packaged-server lifecycle passed. No new GitHub CI
result, version bump, commit, tag or release claimed. User preview processes preserved.

Audit follow-up: hide empty admin tabs by capabilities and return to overview
when a current tab permission is revoked. Preserve the ordinary Server sidebar
page and administrator settings access when server management is disabled.
New audit harness verifies this at GUI 1/2/3. Legacy community unique cases
completed in verified segments, with actual review-before-report cancellation.

## Server page removal (2026-10-08)
User approved removing the redundant Server sidebar page and in-game pack status.
Server links now open from Help and are hidden when no links are configured.
Server management remains in administration; server.enabled controls its functional
module. Pre-connect publication checks/components remain unchanged. Removed obsolete
screens, their unconsumed menu state fields and translations; updated native fixtures.
Config template now documents apply/restart flow and where server links appear.
Verification: network-enabled production build/verifyBundle and all 314 unit
checks passed. Updated audit native flow passes GUI 1/2/3: 255 settings checks,
Help links hidden/present, live menu-link changes, parent return and scoped admin
access. All harness sources compile. Running user previews preserved; no version
bump, commit or observed GitHub CI claimed.

## Release documentation and version (2026-10-08)
User authorized updating documentation and release number. Set 2.0.0: replacement
of GitHub packs by server-owned publications requires owner setup and has no automatic
pack migration. Wire versions are unchanged; major branch checks isolate old clients.
README remains product-focused; full current config/module/download sections and
pack preparation/publication layout are documented in SERVER_GUIDE. Player controls
corrected: quick skins are unbound by default. CONTRIBUTING documents versioning and
CI publication. Release notes summarize accumulated product changes. No commit,
tag, remote CI run or publication is authorized or claimed by this change.
Validation for 2.0.0: production build/verifyBundle succeeds with network enabled;
314 Java unit tests and 23 tooling tests pass. All public documentation local links
resolve; every one of the 26 server config sections is documented. Local release
packaging (without --publish) passes: inner/outer version metadata 2.0.0, clean game
JAR, core-release schema and SHA256SUMS verified. GitHub CI remains unobserved.

## Source-first pack downloads (2026-10-08)
User requested provider downloads first, server snapshot fallback on failure.
Publication stores a validated public CDN URL for resolved Modrinth/CurseForge files;
API credentials never reach clients. Client verifies exact size and SHA-256 before
using a source download. Failure resumes the immutable server copy; cancellation
stops without fallback. Close the server connection before a source attempt and
reconnect with the pinned fingerprint for fallback to avoid the 15-second server
idle timeout. Local files remain server-only. Existing publications need explicit
republishing to acquire URLs. Old manifests without URLs retain canonical bytes
and hashes. Pack wire protocol is now 2, superseding the earlier unchanged-wire
release note above; version remains the unpublished 2.0.0.
Verification: network-enabled build/verifyBundle, 320 Java tests, 23 tooling tests,
local release packaging/schema/checksums pass. Coverage includes source success,
cache, unavailable/corrupt/short/oversized source, resume, cancellation, public URL
validation, legacy manifest hashes and fallback after a 16-second source delay.
No commit, tag, publication or observed remote CI claimed. User previews preserved.
