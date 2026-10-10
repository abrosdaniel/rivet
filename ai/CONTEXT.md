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
выдаёт понятную ошибку конфигурации. Интеграция Waystones уточнена: только иконки общедоступных
и открытых игроком камней на карте; при разрушении метка исчезает.
Телепортация, открытие точек и остальные механики остаются в Waystones.

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
A1–A8/U1–U4/D1–D9/T1–T2 implemented per PROJECT_AUDIT.md; U5 shortcuts
intentionally retained. Stable settings IDs, actual retention metadata, independent
home search/profile actions, accessibility-preserving presets, review before report
send, late pack-check isolation, optional publication status and real RU/EN common
actions. Existing dialogs/sidebar navigation retained. Settings event details collapse
within their original dialog. Dead news/rules presentation remnants removed.
UI demonstration classes moved to uiHarness; release JAR clean. Historical profile
fields, migrations and active integrations preserved. Full remaining Russian-literal
inventory is internal in каталогах RU/EN (промежуточная инвентаризация удалена), not a completed full
localization claim. Latest verification is recorded at the end of PROJECT_AUDIT.md.
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

## Windows configuration test repair (2026-10-08)
User supplied Windows Actions build with two failures. Reproduced both by making
the built configuration template CRLF and rerunning FeatureModulesTest and
ServerSettingsTest. Fixture removal assumed LF; boolean migration fixture doubled
the CR in CRLF. Production loading was not at fault. Normalize fixture input
before transformations; parameterize both upgrade tests over LF/CRLF, verify
fixtures actually omit module keys, preserved line endings, owner choices, backup
content and repeated-load stability. Do not normalize or rewrite owner files.
All 312 core tests pass with the CRLF resource; regular build/verifyBundle then
passes with restored LF resources and 322 Java tests. No remote CI result claimed.
Ignore numbered cloud-sync duplicates when counting XML reports; current Gradle
HTML summaries and canonical XML reports provide the actual results.

## General NeoForge pack metadata compatibility (2026-10-08)
User reported unsafe JarJar path and supplied the 52-provider-file Ruslaanchik
pack; asked for universal handling as mods change. Downloaded exact provider
versions to temporary audit cache, checked SHA-512, recursively inspected
metadata without executing mod code. False path rejection: LambDynamicLights
4.8.11 and Supplementaries 3.9.9 nested MixinSquared use META-INF/jars.
Allow any relative archive entry, retain path traversal/absolute/colon/backslash/
control-character rejection, size/depth/count limits, and owner/path diagnostics.
Real-file audit also exposed EMI uppercase REQUIRED, Sodium LIBRARY container
outer TOML duplication and FML's Minecraft 1.21.1 compatibility matrix. Match
loader semantics generally: dependency types case insensitive, missing type
required (legacy explicit mandatory=false optional), skip outer LIBRARY mod TOML,
1.21.1 accepts loader aliases Minecraft 1.21 and NeoForge 21.0.166 only.
No per-mod bypasses. Existing core uses JarSelector for bundled version selection.
Standalone Better Advanced Tooltips build.5 conflicts with KubeJS embedded build.1
in original pack; preserve duplicate validation. Test-only input list excluding
standalone Better Advanced Tooltips passes all 51 actual provider mods and their
optional component subsets. User's three local JARs were not supplied/tested;
do not claim entire supplied pack or actual game runtime passed.
Verification: build/verifyBundle and 327 Java tests pass (317 core plus 10
helper/bootstrap); tests cover alternate nested locations, recursion, unsafe
paths, case/default dependency enforcement, LIBRARY containers and matrix limits.
No live server modification, commit, release or remote CI claimed.

## Rivet 2.0.1 and Ruslaanchik release preparation (2026-10-08)
Remote v2.0.0 already exists; patch 2.0.1 prepared for generic pack metadata fixes.
Pack/auth/menu/helper protocols unchanged (2/3/3/1). Production build and
verifyBundle pass; 327 Java and 23 tooling tests pass. Release descriptor schema,
embedded production bundle and SHA-256 checksums verified. Local release files
are in workspace outputs/releases/2.0.1; no commit, tag, push or publication made.
Updated outputs/Ruslaanchik/pack.toml removes standalone Better Advanced Tooltips
which conflicts with the KubeJS embedded copy. Provider versions preserved.
All three original local JARs and macOS input config obtained from original
sources and included beside pack.toml. Full 54-mod metadata/dependency/component
audit passes. Actual PackPublisher.prepare passes with 55 entries, 9 components
and 51 external download sources using genuine downloaded files/API metadata.
No live server activation, full 54-mod game runtime or remote CI success claimed.

## Required CI failure after 2.0.1 preparation (2026-10-08)
User supplied three Actions logs (macOS, Windows, Linux) and explicitly demanded
a persistent CI reliability rule. All three fail the same test:
ServerSettingsTest.configurationErrorsPointToKeysWithoutExposingValues, line 71,
expected true but got false (317 core tests, one failure). This is a test failure,
not the Gradle deprecation warning or a dependency download error. Root cause
not yet reproduced or fixed in this request, which only asks to record the rule.
Previous local build success does not establish release readiness; required CI
is currently failing and must be resolved before calling 2.0.1 ready.

## Reproduced and fixed CI config diagnostics test (2026-10-08)
Forced test execution reproduced the supplied failure. Config template now ends
in display, whereas the test appended unknown keys at EOF and expected
pack.downloads. Local generated resources still contained the older template,
which hid the mismatch in the prior release check. Fix injects unknown/unsafe
keys explicitly into pack.downloads, preserving key diagnostics and secret
redaction assertions, and parameterizes LF/CRLF. Forced full build/verifyBundle
passes after regenerating resources; tooling tests pass. Regenerated local 2.0.1
release bundle. Exact remote commit CI remains unverified; no push/publication.

## Server pack checkbox list (2026-10-08)
User requested component selection matching the updater checkbox UI, including
required files checked and disabled. ServerPackScreen uses shared UiChoiceRow
at 24px stride; optional rows represent manifest components, required rows are
manifest files with empty component ID (file basename, full path/reason tooltip).
Required rows are always checked, inactive and never enter the selected component
set; no manifest or wire protocol change. Shared disabled choices use muted colour.
ServerPackFlowHarness components mode checks optional toggling, locked required
rows, submitted IDs and geometry at GUI scales 1/2/3 without a live server.
Native checkbox scenario passed GUI 1/2/3. Forced production build and
verifyBundle passed (25 tasks executed); refreshed workspace release JAR 2.0.1.
Remote CI and publication not performed.

## Rivet 2.0.2 release preparation (2026-10-08)
Remote v2.0.1 points to 7ec75f9; its Build and test run 37707280278 completed
successfully. New checkbox UI is a patch; version set to 2.0.2, player guide and
release notes updated. Native components checks already passed GUI 1/2/3.
Forced build/verifyBundle and 328 Java + 23 tooling tests passed. Inspection
found 669 numbered cloud-sync class copies in local generated output; clean
build removed them. Final bundled JAR has no numbered copies or harness classes.
Release descriptor schema, SHA-256 checksums, embedded version and unchanged
pack/auth/menu/helper protocols verified. Workspace outputs/releases/2.0.2 has
JAR, core.json, SHA256SUMS.txt and release notes. New release changes are uncommitted;
no push, tag or publication. CI for the new release commit still pending.
When packaging from a cloud-synced checkout, use clean generated outputs and
inspect final archive entries for sync duplicates, not just passing tests.


## Карта — первый рабочий этап (2026-10-08)

Пользователь разрешил начать реализацию. Добавлены MapRepository/MapTile/MapMarker/
MapViewport, ServerMap и WorldMapClient/WorldMapScreen/MapMarkerScreen. Клавиша M,
сохранение лично исследованной поверхности, личные метки, панорамирование/масштаб,
выбор измерения, навигация и остановка в карте, `[map].enabled` с автодополнением
конфига. Формы остаются диалогами; инструменты используют общий UiActions.tool.
Листва не блокирует надземное исследование; пещеры и измерения с потолком пока
не исследуются. Лимиты первого этапа: радиус 32, кэш 512 чанков, восемь значков.
Не называть этот этап полной реализацией согласованной карты. Остаток — в
FUTURE_MODULES.md; старую навигацию/Xaero не удалять до полного переноса.

Проверено локально:
- `clean build :bootstrap:verifyBundle` — успешно, 339 тестов без ошибок;
  ранее тесты принудительно запускались без использования кэша задач.
- WorldMapHarness с настоящим сервером/клиентом и PostgreSQL: 100 проверок,
  default/light/high contrast × GUI 1/2/3 и компактное окно 960×540.
- Повторный вход/сохранённая карта; отдельный сценарий с пустым кэшем и
  наземной позицией в лесу — 20 проверок, 20 чанков/3209 исследованных ячеек.
- `map.enabled=false` — RIVET_MAP_DISABLED_OK.
- Визуально проверены диалоги и компактное окно. Исправлена прозрачность
  RGB-значков при высокой контрастности в общем UiIcons.draw.
- Сценарии опциональные, только uiHarness, не включаются в выпускаемый JAR.
  В CI новых заданий и скриншотов не добавлено. Удалён собственный временный
  контейнер БД; тестовый сервер и клиент остановлены.

Удалённый CI этой работой не проверен. Версия не изменена; выпуск не готовился.


## Карта — полноэкранная компоновка и читаемость (2026-10-08)

По замечаниям пользователя карта занимает весь viewport, инструменты и список меток
накладываются поверх без изменения центра. ПКМ открывает общий UiContextPopup;
формы остаются модальными. Есть создание/редактирование/удаление с подтверждением,
навигация/остановка, копирование координат. Телепортация выполняется штатной командой
сервера и предлагается только при наличии команды в полученном дереве прав;
между измерениями также требуется execute in. Waystones не затрагивается.
MapGlyphs содержит оригинальные заполненные 16×16 значки. Метки имеют постоянный
размер, подложку, контур и подписи с проверкой пересечений.

MapBlockColors читает верхние текстуры моделей, учитывает биомный tint и прозрачные
слои; MapColors добавляет освещение по соседним высотам, в том числе на границах
чанков. Принцип подтверждён журналом автора Xaero: текстурные цвета, прозрачность,
освещение склонов (https://chocolateminecraft.com/update.php?mod_id=2). Исходники
Xaero закрыты; код и значки реализованы самостоятельно. Старые сохранённые цвета
обновляются при повторном исследовании; исходные блоки в дисковом кэше не хранятся.

Проверки: 342 Java-теста, clean build и verifyBundle с принудительным выполнением
всех 30 задач; ноль ошибок. Настоящий сервер/клиент: 110 проверок default/light/contrast
на GUI 1/2/3 плюс компактное окно; 22 проверки гостя без teleport; ещё 24 проверки
с наземной позицией и ПКМ у края окна, визуальный просмотр меню. Новые компоненты
добавлены в каталог UI Kit и документацию. CI не проверялся, версия не менялась.
Это продолжение первого этапа, а не готовый полный модуль согласованной карты.

## Карта — цветные спрайты и боковая карточка (2026-10-08)

Пользователь явно разрешил заменить диалог метки правой панелью поверх карты.
MapMarkerPanel показывает карточку и редактирует черновик в том же месте. Сохранение
возвращает карточку, отмена сохраняет исходную метку; удаление подтверждается.
Старый MapMarkerScreen удалён. В узком окне список и карточка взаимно сворачиваются,
если не помещаются рядом; центр и размер карты сохраняются. Строка координат
ограничена свободным пространством и не перекрывает боковые панели.
Название измерения сверху — якорный dropdown, отдельная кнопка убрана. Новая карта
открывается в измерении игрока, выбор в текущем окне сохраняется до переключения.

Одноцветные маски заменены оригинальным цветным PNG-атласом: 24 значка меток и
11 инструментов. Спрайты не тонируются цветом метки; цвет используется для рамки.
Рецепт пиксельных рисунков — tooling/generate_map_icons.py, Pillow нужен только
для повторного создания атласа, не для сборки или запуска мода. Все названия
значков переведены, редактор имеет общий фокус и прокрутку при нехватке места.
Это стартовый набор, согласованные 50–100 значков ещё остаются в плане карты.

Нативные проверки панели: 160 проверок default/light/contrast, GUI 1/2/3 и
компактного окна; затем 36 проверок гостя, включая сохранение значка/цвета,
отмену черновика и отсутствие teleport. Скриншоты редактора просмотрены вручную.

Окончательная компоновка: ещё 36 нативных проверок наземного и компактного окна;
снимок редактора просмотрен. Clean build и verifyBundle: 30 задач выполнены
принудительно, 342 Java-теста без ошибок. Старого экрана и harness-классов в JAR
нет; цветной атлас включён. Версия и удалённый CI не менялись.

### Карта: прямое редактирование и набор значков

Карточка справа теперь сразу содержит название, координаты, палитру цветных
квадратов и прокручиваемую сетку из 64 значков. Сохранение явное; отмена
восстанавливает исходные данные. Навигация, удаление и доступная по правам
телепортация подписаны, а не спрятаны за отдельными маленькими инструментами.
UiColorSwatch общий для UI Kit: фокус, подсказка, выбранный цвет с галочкой.
Игрок отображается контрастной двухцветной стрелкой навигатора с вырезом сзади.

Часть значков основана на Kenney Tiny Dungeon / Tiny Town (CC0), остальные —
рисунки Rivet. Источники и лицензия находятся в licenses и включены в JAR.
Именованные 16×16 PNG — источник атласа; tooling/pack_map_icons.py собирает
его через Pillow только при изменении графики. Старый генератор удалён.
Стартовый набор из 24 значков расширен до 64; прошлое ограничение снято.

Нативный сценарий: 180 проверок трёх тем и GUI 1/2/3 с компактным окном;
гость: 36 проверок, включая отсутствие телепортации без прав. Снимки панелей,
палитры и стрелки просмотрены. Эти проверки относятся к текущему этапу карты,
а не ко всему будущему модулю. Версия не меняется; удалённый CI не подтверждён.
Итоговая чистая сборка после исправления соответствия значков названиям:
30 задач выполнены принудительно, 342 Java-теста без ошибок; verifyBundle
пройден. Во вложенном game.jar проверены атлас и лицензия, старый экран и
тестовые классы отсутствуют.

### Инструменты карты: единая пиксельная графика

Пользователь отклонил гладкую геометрию стрелки и разнородные инструменты.
MapPlayerArrow теперь рисует собственный спрайт 24×24 в исходном разрешении,
со ступенчатым контуром, двумя половинами и задним вырезом. Треугольный GPU
рисунок удалён. Инструменты карты перерисованы на единой сетке 16×16:
прицел, увеличение/уменьшение, список меток, поиск, карандаш, сохранение,
корзина, закрытие, остановка, направление и портал. Общие кнопки UI Kit
сохраняются; новые рисунки используются только как glyph, без изменения потока.
Альтернативы Kenney: Shikashi и Raven Fantasy Icons; пока не устанавливаем
их автоматически и не заменяем выбранные значки существующих меток.
Проверки текущей правки: принудительная сборка и verifyBundle пройдены,
342 Java-теста без ошибок. Нативный полный сценарий — 180 проверок на трёх
масштабах и трёх темах с компактным окном; снимки просмотрены. Байты обоих
PNG в вложенном game.jar совпадают с исходными ресурсами. Версия неизменна.

### Карта: набор 50 инструментов Nikoichu

По запросу пользователя выбран конкретный сторонний набор: 1-bit Pixel Icons
v1.2 от Nikoichu (CC0). Взяты 50 именованных 16×16 спрайтов. Copy составлен
из двух страниц; redo отражён из undo; grid добавлен в исходную квадратную
рамку. Манифест происхождения и лицензия находятся в licenses. Не переносить
в репозиторий весь архив 1476 значков, страницы загрузки или временные токены.

MapToolIcons — отдельный атлас 128×112, MapGlyphs содержит отдельный атлас
64 меток 128×128. Прежние рисованные инструменты удалены. Цвет инструмента
передаётся из UI Kit с восстановлением shader color после рисования; текстуры
не масштабируются. Набор охватывает навигацию, масштаб, метки, слои, измерения,
видимость, линейку, территории, выделение, игроков, смерть, Waystones,
настройки и общие файловые действия. Наличие значков будущих инструментов
не означает, что все будущие функции карты уже реализованы.

Все 50 значков с переведёнными названиями добавлены в живой каталог UI Kit.
Сетка каталога рассчитывает число столбцов по доступной ширине. В основной
карте отображаются только реально работающие действия, поток не изменён.
Проверка набора Nikoichu: 342 Java-теста без ошибок, принудительная сборка и
verifyBundle пройдены. Все 50 спрайтов непустые и совпадают с ячейками атласа;
атлас и лицензия проверены во вложенном game.jar. Нативный сценарий — 180
проверок default/light/contrast, GUI 1/2/3 и компактного окна. Снимки обычной
и светлой темы просмотрены, tint не меняет соседние элементы интерфейса.
Версия неизменна, удалённый CI не заявляется как пройденный.

### Карта: безопасная телепортация, линейка и разделение графики

Пользователь закрепил Nikoichu 1-bit Pixel Icons (CC0) как основу значков
инструментов, кнопок и аналогичных действий проекта. Использовать существующие
50 именованных glyph и общий UI Kit. Обозначения меток должны быть цветными;
для них выбран Raven Fantasy Icons Free + собственные символы Rivet в общей
палитре. Прежние Kenney-ресурсы и лицензия удалены; 64 ключа меток сохранены.
Raven не CC0: сохранять условия автора и таблицу происхождения из licenses.

Линейка реально работает: два щелчка, расстояние XZ в блоках, мировые координаты
при панорамировании и масштабе, ПКМ очищает, Esc очищает/выключает режим.
Измерение сбрасывается при изменении измерения. Панель инструментов переносит
кнопки в столбцы при нехватке высоты.

Карта вызывает серверную команду rivet map teleport вместо непосредственного tp.
Сервер сохраняет проверки Auth, включения карты и права native teleport/tp;
для смены измерения требуется также execute. SafeMapLanding сначала ищет
безопасную высоту в выбранной колонке, затем в радиусе двух блоков. Проверяются
опора, полный объём игрока, жидкости, граница мира и опасные блоки; учитываются
полублоки. Без безопасного места переход отклоняется, права не повышаются.

Проверки: чистая сборка, 347 Java-тестов без ошибок, verifyBundle. Настоящий
сервер/клиент: закрытая позиция, полублок, граница мира, магма и переход в Край;
гость не получает команду, принудительная попытка отклоняется. UI: 230 проверок
GUI 1/2/3, default/light/contrast, компактное окно. Снимки линейки и палитры
просмотрены. Итоговые атласы и лицензии проверены в game.jar, Harness отсутствует.
Версия не менялась; это текущий этап карты, полный план и удалённый CI не заявляются
готовыми.

### Карта: уточнения панели и фактическое поведение Xaero

Пользователь отверг собственный серверный поиск безопасного места и потребовал
поведение Xaero. Изучена официальная сборка World Map 1.47.0 для NeoForge 1.21.1:
MapTeleporter отправляет стандартную команду, использует верхний слой (включая
прозрачные блоки и воду), а PARTIAL_Y_TELEPORT по умолчанию добавляет 0.5 к высоте.
Rivet теперь делает так же: сохранённая высота ног + 0.5, центр колонки XZ,
обычный tp @s (или teleport, если alias отсутствует), execute in при смене измерения.
Права обрабатывает Minecraft. SafeMapLanding, серверный map teleport и его сообщения
удалены. Не возвращать предыдущий алгоритм поиска соседних колонок. Неизвестная
высота скрывает teleport-here в survival. Сохранённая метка использует свой Y,
как Xaero; не обещать безопасность после изменения местности.
Источник сравнения: https://modrinth.com/mod/xaeros-world-map
Описание верхнего слоя: https://chocolateminecraft.com/update.php?mod_id=2

Инструменты Nikoichu должны сохранять светлую заливку и тёмную обводку в светлой
теме. Цветные метки расширены до 96: 32 новых спрайта, таблица происхождения и
переводы обновлены. Названия отвечают рисунку (church, lantern, jug, windmill,
helmet и т.д.). Старые ключи castle/portal/tower сохранены ради существующих меток;
это исторические идентификаторы, не ошибка перевода. Отдельная крепость добавлена.

Редактор закрепляет название, координаты, цвета и действия. Только сетка значков
имеет прокрутку. Список меток выровнен с верхним краем (8 GUI-пикселей). В компактном
окне карточка поднимается, если иначе не остаётся видимой строки значков; элементы
не перекрываются. Число блоков подписано вдоль линии линейки с читаемой ориентацией.

Проверки: 346 тестов, чистая сборка и verifyBundle; 297 нативных UI-проверок трёх
тем, GUI 1/2/3 и окон 960x540/640x480. Проверяются доступность последнего значка,
неподвижность и сохранение поля названия, наличие палитры при прокрутке. Снимки
обычной/светлой темы и минимального окна просмотрены. Телепортация реальным действием
карты проверена на верхней поверхности, полублоке, глубокой воде и между измерениями;
гость не получает кнопку, сервер отклоняет принудительный tp. Старой команды нет.
Атлас 128x192, 96 переводов/источников и отсутствие старых/test классов проверены
в итоговом game.jar. Версия не менялась; удалённый CI не заявляется пройденным.

### Карта: цветные булавки для новых меток

Старый ключ pin сохранён, рисунок заменён оригинальной диагональной булавкой.
Добавлены семь цветных вариантов: всего восемь булавок, 103 значка в атласе.
Новая метка получает случайную булавку и независимый случайный цвет из существующей
палитры 12 цветов. Выбор происходит при создании объекта, не при перерисовке панели.
Пользователь меняет оба значения обычными элементами редактора. Существующие метки
не меняют сохранённые ключи и цвета; старый pin отображается красной булавкой.

Проверки: чистая сборка, 346 тестов, verifyBundle; 297 нативных UI-проверок
GUI 1/2/3, default/light/contrast и компактных окон. Дополнительно проверены
допустимые случайные значения новых меток. Просмотрены спрайты и сетка в редакторе.
Атлас 128x208 совпадает с исходным в game.jar; 103 ключа и перевода согласованы,
тестовые классы не упакованы. Версия не менялась.

### Карта: пустое поле названия новой метки

Редактор новой метки начинает с пустого значения названия. Подсказка поля остаётся,
редактирование существующих меток сохраняет их название. Перерисовка и выбор
цвета/значка не сбрасывают введённый текст. Проверено 297 нативными UI-проверками
GUI 1/2/3, default/light/contrast и компактных окон, включая новую проверку пустого
значения при открытии создания. Проверка непустого имени при сохранении сохранена.

### Карта: живой черновик и перенос метки

Карта отображает черновик сразу при открытии создания. Его значок, цвет, название
и позиция обновляются без записи в хранилище. Пустое название не рисует подпись;
валидный MapMarker для предпросмотра использует внутреннее временное название.
Непустое имя по-прежнему требуется для сохранения. Редактируемый значок рисуется
поверх стрелки игрока, чтобы новая метка на позиции игрока была видна. Панорамирование
при открытии карточки выводит метку из-под панелей, включая компактное окно.

ЛКМ переносит как новую, так и сохранённую метку; панорама при этом не меняется.
Карточка сохраняет текст, цвет и значок, показывает новые координаты. Высота
исследованной колонки берётся над верхним слоем; неизвестная высота сохраняет Y.
Сохранить применяет позицию. Отмена восстанавливает исходную метку или удаляет
новый черновик. Esc во время жеста и отпускание над панелью/вне карты отменяют
только текущий перенос. Изменение измерения не переносит метку между измерениями.

Проверки: 451 нативная UI-проверка GUI 1/2/3, default/light/contrast, компактные
окна; дополнительно 123 проверки на поверхности после изменения порядка слоёв.
Проверены предпросмотр, отсутствие ранней записи, координаты и высота, сохранение
переноса, отмена жеста/редактирования и сохранение полей. Снимки GUI3 и минимального
окна просмотрены. Сборка и verifyBundle прошли. Версия не менялась.

### Карта: второй этап — миникарта

Minimap отображает те же исследованные тайлы и личные метки WorldMapClient.
Текстура ограничена размером HUD, обновляется раз в 100 мс и при изменении
поворота; только известные тайлы подгружаются существующей очередью карты.
Нет отдельного исследования или сканирования мира. MinimapProjection даёт
общие прямое/обратное преобразования для поверхности, значков и курсора.
Круглая/квадратная форма, север сверху/поворот, размер, масштаб, прозрачность,
координаты, биом, время мира и погода сохраняются в локальных настройках карты.
Исходный размер 128 GUI-пикселей, масштаб 2; не менять существующие значения
пользователя. Позиция — отдельные якоря существующего HudSettings/HudPlacement.

Настройки «Карта» добавлены в общий UiSettingsShell и поиск; старые индексы
сохранены. Редактор HUD переносит миникарту, колесо меняет её размер. Курсорный
режим открывает карточку метки большой карты, колесо меняет масштаб. Удержание Z
увеличивает миникарту; отпускание восстанавливает размер. Клавиша переназначается
средствами Minecraft, события нажатия/отпускания обрабатываются также в курсорном
режиме. Чат не скрывает карту, F1 и серверный map.enabled запрещают обычный показ.
Серверное отключение не затрагивает сохранённые данные. GPU-текстура и нажатия
сбрасываются при смене соединения/мира и перезагрузке ресурсов.

Проверки: чистая сборка с принудительным выполнением задач, 351 Java-тест,
verifyBundle; 341 проверка настоящего клиента/сервера (default/light/contrast,
GUI 1/2/3, компактные окна). После уточнения масштаба и обработчиков клавиш ещё
93 проверки GUI3 и двух компактных окон. Проверка серверного запрета использует
изолированный снимок полученного состояния, чтобы обновления реального сервера
не делали тест недетерминированным; это проверка клиентского потребителя политики.
Снимки круглой/квадратной карты и редактора просмотрены. Радар, общие метки,
точки смерти и дополнительные слои остаются последующими этапами. Версия не менялась.

### Миникарта: край, подписи и размеры HUD

Метки вне миникарты проецируются на её внутренний край с сохранением направления,
для кругов и квадратов; эти значки остаются кликабельными в режиме курсора.
Рамка значка уменьшена с 18 до 14 GUI-пикселей. Стандартные измерения называются
«Мир», «Ад», «Энд». Под миникартой одна центрированная строка «Мир · 0, 0, 0»,
без букв X/Y/Z и без тени; длинная строка уменьшается, а не обрезается.
Стороны света используют светлый текст с тёмной тенью независимо от темы.

Редактор HUD меняет размер колесом отдельно для миникарты, виджета, подсказок
чата, указателя маршрута и уведомлений. Настройки физического размера миникарты
и масштаба виджета удалены из диалогов/поиска. Масштаб местности и общая настройка
GUI сохранены; прежние размеры пользователя читаются. Для новых масштабов
используется HudSettings, для миникарты MapSettings. R сбрасывает размеры и
размещение. Уведомления показывают изолированный образец; их позиция по-прежнему
фиксирована сверху справа. Исправлена область обрезки текста подсказок при
масштабировании. В компактном редакторе образец уведомления занимает до трети
ширины, чтобы не перекрывать миникарту.

429 нативных проверок прошли: default/light/contrast, GUI1/2/3 и два компактных
окна. Проверены край и переход к дальней метке, независимое сохранение размеров,
границы элементов, отсутствие перекрытий настроек, увеличенная миникарта и
клавиша, клиентская политика сервера. Снимки светлой темы и дальней метки просмотрены.

### Миникарта: плавное движение, скорость и выступающие метки

Проверены MinimapRenderer/MinimapProcessor установленного Xaero’s Minimap 26.5.0:
координаты камеры интерполируются между тиками, zoom сглаживается по времени
кадра (остаток 0.8 за кадр 60 FPS). Его getTargetZoom использует выбранный масштаб,
масштаб увеличенной карты и пещерный масштаб; привязки к скорости в этом пути нет.
Не утверждать, что новый эффект скорости является точной копией Xaero’s.

Minimap теперь рисует мировую текстуру треугольным веером круга/квадрата.
Координаты и yaw берутся из интерполированных позиций каждый кадр, UV учитывают
поворот и текущий масштаб. Обновление цветов раз в 100 мс не останавливает
движение поверхности. Кэш 64/128/256/512 пикселей, максимум 512×512, разрешение
выбирается по видимому охвату; мировая сетка выровнена по размеру пикселя.
Значки двигаются на дробных координатах. Метки закрепляются на радиусе R−2
и выступают на пять GUI-пикселей; их внешняя часть сохраняет область нажатия.

MinimapMotion добавляет запрошенное мягкое отдаление при горизонтальной скорости
свыше обычной ходьбы; отдаление ограничено 1.3× и не меняет сохранённый масштаб.
Темп сглаживания независим от FPS и согласован с наблюдённой анимацией zoom Xaero’s.
Остановка возвращает исходный масштаб, отключение анимаций применяет его сразу.

Проверки: 429 полных нативных сценариев default/light/contrast, GUI1/2/3 и два
компактных окна, включая клик снаружи рамки; 117 предварительных сценариев.
MinimapMotionHarness отдельно проверил настоящие промежуточные кадры движения,
отдаление и возврат масштаба. Три новых Java-теста проверяют ограничение масштаба,
плавный возврат и независимость от частоты кадров. Сборка и verifyBundle выполнены
с принудительным запуском задач. Снимки светлой темы и выступающей метки просмотрены.


## Карта: точки смерти (2026-10-08)

MapMarker имеет необязательный deathAt (миллисекунды); прежний конструктор и
JSON version=1 сохранены. Нулевой timestamp означает обычную метку. Цвет смерти
нормализуется на уровне модели в #606876, поэтому загрузка и редактирование
не позволяют заменить его. Название остаётся датой; подпись полной карты считает
остаток времени каждый кадр через MapDeathLifecycle.countdown.

WorldMapClient отмечает переход живого игрока в состояние смерти, сохраняет
координаты ног и измерение. Повторное подключение уже умершего игрока не создаёт
новую точку. Очередь ожидает загрузки markers.json. Очистка выполняется в клиентском
тике одним асинхронным снимком: 10 минут с deathAt или прибытие живого игрока
в том же измерении с допусками 3 блока по горизонтали и 4 по высоте. Просроченное
время вне игры учитывается при следующем входе. Настройки deathMap/deathMinimap
только скрывают отображение. Закрывается карточка удалённой точки, а DirectionCue
хранит UUID цели смерти и снимает маршрут после её исчезновения.

MapDeathHarness выполняет реальные kill/respawn, повторную смерть, прибытие,
моделирует старый timestamp для проверки истечения срока без ожидания 10 минут,
проверяет фильтр списка, карточку и настройки в default/light/contrast GUI1/2/3
и компактных окнах. Обычные метки и их идентичность сохраняются.

Локальная синхронизация iCloud может создавать конфликтные копии скомпилированных
классов и ресурсов с числовыми суффиксами. Это не ошибка исходников/CI. Для достоверной
проверки перенаправлять buildDirectory во временную несинхронизируемую папку;
проверять итоговый JAR на конфликтные копии и отсутствие тестовых классов.

Итоговые проверки этапа: чистый build и verifyBundle с принудительным выполнением,
363 Java-теста без ошибок. Нативный MapDeathHarness завершил 206 проверок,
две реальные смерти, прибытие и истечение срока с закрытием карточки и маршрута,
default/light/contrast GUI1/2/3 и компактные окна. Снимок подтвердил, что таймер
смерти остаётся видимым при совпадении с обычной меткой. Итоговый JAR не содержит
конфликтных копий и тестовых классов; обе локализации совпадают с исходниками.
Версия не менялась; удалённый CI этим этапом не подтверждён.


## Уточнения интерфейса карты (2026-10-08)

По последнему решению пользователя смерть не имеет редактора: только контекстное
меню действий. Череп зарезервирован в MapMarker (смерть нормализуется в skull,
обычный skull заменяется pin), в MapGlyphs.CHOICES его нет; порядок атласа не менять.
WorldMapClient.put запрещает замену существующей смерти изменённой записью.
ЛКМ/список/миникарта открывают меню, ПКМ не предлагает редактирование, перетаскивание
не изменяет смерть. UiContextPopup.validWhile закрывает меню после исчезновения цели;
WorldMapScreen обновляет строки списка при изменении снимка меток.

Подсказка смерти передаётся renderComponentTooltip отдельными строками: название,
одна дата с часовым поясом, координаты и измерение. Не вставлять LF в один Component:
это не создаёт строку в обычном renderTooltip. На самой карте остаётся countdown.
Рамка метки большой карты уменьшена до 24 GUI px, спрайт увеличен до 20 px.
Поворот миникарты по взгляду — default/reset=true, явно сохранённый rotate сохраняется.

Проверены локальные MapPixel и BlockTextureColorUtils Xaero’s World Map 1.46.0 /
XaeroLib 1.7.3: основой цвета служит крупнейшая верхняя грань или particle texture,
прозрачные пиксели отбрасываются при усреднении, биомный tint применяется отдельно;
рельеф имеет базовый/рассеянный свет. В Rivet использованы эти подходы, без копирования
кода и без обещания идентичности всех режимов Xaero’s. MapBlockColors выбирает
крупнейшую верхнюю грань и не усредняет её с декоративными; alpha<=10 исключается
из среднего, полупрозрачность стекла сохраняется. MapColors задаёт мягкий ambient
floor 0.8, чтобы листья и крутые перепады не превращались в тёмные пятна.

Проверки этих уточнений: чистая принудительная сборка и verifyBundle, 365 Java-тестов
без ошибок; MapDeathHarness 206, WorldMapHarness 451 и MinimapHarness 451 нативных
проверок. Каждый прогон завершился собственным OK-маркером, охватил default/light/
contrast GUI1/2/3 и компактные окна. Проверены отсутствие редактора и изменения
записи смерти, недоступность skull в выборе, закрытие меню по истечении срока,
сохранение обычных меток, выбор и перетаскивание, поворот после сброса, HUD и запрет
мини-карты сервером. Просмотрены снимки обычного редактора и меню смерти.
JAR без конфликтных копий/тестовых классов, локализации совпадают с исходниками.


## Рендер поверхности и компактные метки (2026-10-08)

Последнее решение заменяет случайную булавку по умолчанию: новая метка имеет
icon=none и случайный цвет рамки. В CHOICES значение none первое; не добавлять его
в MARKERS и не менять порядок спрайтов атласа. На обеих картах MapGlyphs.initial
показывает крупный первый графемный символ названия, в верхнем регистре. Пустое
имя черновика оставляет пустую рамку. Существующие выбранные значки сохраняются.
Правая карточка ограничена 420 GUI px, сетка значков прокручивается отдельно.
Удаление использует UiConfirmDialog.compact(), высоту по тексту и ContentPane
без вложенного фона. Компактный вариант доступен через «Удалить» в UI-каталоге.

Проверка фактических MapPixel и XaeroLib BlockTextureColorUtils подтвердила
2646 совпадений формулы освещения и цвета текстур всех 957 проверенных состояний
стандартных блоков в одном биоме. Текущая MapColors использует северный и
северо-западный уклоны, освещение высоты и отдельные прозрачные слои; прежняя
заметка об ambient floor 0.8 больше не описывает реализацию. Проверка одного
биома не доказывает совпадение всех границ биомов, режимов и сторонних моделей.
MapTile сохраняет верхнюю и эффективную высоту, прозрачные слои и свечение.
MapRepository читает старый v1 и записывает v2; старое исследование не удаляется.

Загрузка поверхности перенесена из одного чанка за тик в RenderFrameEvent.Pre:
ограничение времени кадра, возобновление по 16 колонок, ближайшие чанки первыми,
ожидающее чтение диска не блокирует остальные. Дальность учитывает фактическую
клиентскую настройку до 32; чтение только уже загруженных чанков. Крыша над игроком
не останавливает обновления поверхности; измерения с потолком ожидают пещерного
этапа. Список видимых чанков кэшируется и не обрезается до 512.
MapTerrainCache рассчитывает освещение и уровни MapDetail в фоне по неизменяемым
снимкам, сохраняет версию при одинаковых пикселях. MapRegionTextures собирает
страницы 64×64, сохраняет готовое изображение до обновления и текстуры между
открытиями. Смена детализации пересоздаёт GPU-текстуру нужного размера.
Ограничены фоновые очереди, количество загрузок за кадр и размеры кэшей.
Миникарта собирает изображение в фоне, повторно загружает его только при изменении
содержимого/палитры/границ и сохраняет мировую привязку с запасом для движения.

Итоговые локальные проверки: принудительные build и verifyBundle, 373 Java-теста
без ошибок. WorldMapHarness и MinimapHarness по 451 проверке каждый, default/light/
contrast GUI1/2/3 и компактные окна, завершились своими OK-маркерами. Дополнительный
реальный сценарий проверил 6400 исследованных колонок, детализацию при отдалении,
сохранение текстур при повторном открытии, неизменность неподвижной миникарты,
обновление блока над игроком, компактную карточку и подтверждение удаления;
завершился RIVET_PIPELINE_OK. Просмотрены карта, миникарта, карточка с первой буквой
и удаление. Производственный JAR содержит новые классы рендера, не содержит
тестовых классов/конфликтных копий; локализации совпадают с исходниками.
Версия не менялась; удалённый CI и полная идентичность всех режимов Xaero не заявлены.

### Центрирование меток — 2026-10-08

Буквы меток центрируются по видимым пикселям глифа, а не по ширине строки или размеру ячейки шрифта. Прозрачные поля Bitmap/Unihex учитываются через клиентские accessor mixins. Цветные значки обрезаются по альфа-границам и равномерно вписываются в рамку. Кэш границ сбрасывается при перезагрузке ресурсов. Проверено на снимках работающей игры: центр буквы «Д» на полной карте совпадает с центром рамки; на миникарте разница 0,5 физического пикселя из-за округления.

Заголовок карточки метки использует отдельный размер значка 12 GUI-пикселей и центр на высоте 16 от верхней границы. Он отделён от подписи названия и компактного поля; крупный размер букв на самой карте не переносится в заголовок формы.

### Категории и видимость меток — 2026-10-08

Реализован четвёртый этап карты. MapCategory хранит постоянный UUID, имя и независимую видимость карты/миникарты; категории сохраняются в categories.json по миру/игроку. MapMarker получил category/mapVisible/minimapVisible с совместимыми старыми конструкторами и значениями по умолчанию при чтении старых файлов. Удалённая категория считается отсутствующей, сами метки сохраняются; активный фильтр переключается на «Без категории». Точки смерти остаются вне категорий.

Панель меток ищет при вводе по имени метки/категории, сохраняет фокус и позицию курсора, фильтрует категории. Скрытые записи доступны в списке. Карточка редактирует координаты, категорию и видимость; компактные окна используют MapMarkerDetailsScreen, общие UiDialog/UiFields/UiChoiceRow. Категории управляются через MapCategoriesScreen на общих компонентах. Только значки прокручиваются в карточке. Ввод координат проверяется при редактировании и сохранении; перенос обновляет поля.

Локальная проверка: полный build/verifyBundle с принудительным выполнением всех 25 задач; 377 тестов, 0 ошибок. Финальный WorldMapHarness завершился RIVET_MAP_UI_OK: 785 проверок, default/light/contrast, GUI1/2/3 и компактные окна. Включены создание и переименование категории, выбор в карточке, раздельная видимость, неверные координаты, поиск при вводе по категории и фильтр. Производственный bundle содержит новые экраны и актуальные локализации, тестовые классы исключены. Версия не менялась, удалённый CI не проверялся.

### Карта: скорость появления чанков — 2026-10-08

Пользователь уточнил, что главное отличие от Xaero’s — медленное появление чанков.
Причина найдена в бюджете WorldMapClient.sample: Math.clamp(long,int,int) возвращает
int; умножение на 86960 переполнялось до присваивания long. Вместо ~1–4 мс получался
почти нулевой/отрицательный бюджет и выполнялась одна порция из 16 колонок за вызов.
MapWorkBudget использует long на всех стадиях, тестирует FPS 20–1000 и границы времени.
Целевая доля времени осталась прежней; исправлен её расчёт. Фактическая работа за
кадр выросла с одной порции до предусмотренного ограниченного бюджета.

Завершённые чанки запоминаются по слабой ссылке на экземпляр чанка. Движение не
сбрасывает текущую порцию и не начинает повторное исследование готовой области.
Новые чанки и изменения блоков чередуются; загрузка с диска не блокирует другие
участки. ChunkEvent.Load сбрасывает отметку готовности повторно полученного чанка,
перезагрузка ресурсов запускает повторное исследование. Фоновая сверка неизменных
чанков выполняется через 30 секунд; когда работы нет, поиск ограничен одним за тик.

Локальный исходный замер: 165 непустых чанков обработаны примерно за 21 секунду
(исходный диагностический сценарий включал ещё четыре пустых чанка и затем ошибся
при обращении к закрытому экрану; это не успешный полный прогон). Новый MapLoadingHarness
проверяет реальные непустые чанки и каждый пиксель готовой текстуры региона.
Повторные прогоны: 165 чанков за 242–450 мс, первые текстуры за 45–253 мс.
Проверены движение через границы чанков, отсутствие голодания дальних участков,
добавление и удаление блока: RIVET_MAP_LOADING_OK. Снимок готовой карты просмотрен.
Это сравнение Rivet до/после в одном локальном тестовом мире, не сравнительный
бенчмарк производительности Xaero’s и не подтверждение полной визуальной идентичности.

Принудительные build/verifyBundle и компиляция UI-проверок: 26 задач выполнены,
379 Java-тестов без ошибок. Итоговый JAR содержит исправленный бюджет; тестовых
классов и конфликтных копий нет. Версия не изменена; удалённый CI не проверялся.
Полные нативные сценарии после исправления: WorldMapHarness — 785 проверок,
MinimapHarness — 451; оба завершились собственными OK-маркерами, охватили
обычную/светлую/контрастную темы, GUI 1/2/3 и компактные окна. Тестовые клиенты
завершены; существующий локальный сервер оставлен работающим.

### Рабочий процесс и продолжение карты — 2026-10-08

Продолжается работа из чата «MC Rivet»: ориентир Xaero’s относится ко всей загрузке
и отрисовке карты, не только формуле цвета. Новые режимы карты отложены до устранения
проблем текущего рендера. Пользователь отдельно потребовал не упаковывать установочный
JAR без прямого запроса. Для текущих исправлений использовать компиляцию, нужные тесты
и запуск клиента; не вызывать build/verifyBundle/jar, создающие поставляемый мод.

### Миникарта: мерцание при движении и повороте — 2026-10-08

Пользователь подтвердил мерцание именно при движении/повороте. Найдены два дефекта:
координаты/размеры для UV менялись до завершения фоновой композиции, а при смене
размера готовую текстуру немедленно заменяла пустая. Minimap теперь хранит отдельно
запрошенную область и опубликованную Area. Новые пиксели и их мировая привязка
публикуются вместе на render thread; готовое изображение остаётся до замены.
Законченная композиция предыдущей области не отбрасывается только из-за продолжающегося
движения; следующая область определяется с точной проверкой геометрии. Смена измерения,
ресурсов или палитры не позволяет публиковать чужой/устаревший результат.

Для поверхности миникарты включена линейная фильтрация при движении/повороте и
CLAMP_TO_EDGE; параметры восстанавливаются после каждого upload, который сбрасывает
фильтрацию. Иконки и текст используют прежнюю отрисовку.
MinimapRefreshHarness задерживает фоновый композитор и проверяет сохранение старой
текстуры/геометрии, замену, изменение размера и фактические GL-параметры: 22 проверки.
MinimapMotionHarness завершился OK: 863 кадра, 275 межтиковых изменений позиции,
проверены ускорение и восстановление масштаба. Установочный JAR не упаковывался.
Полный MinimapHarness после исправления прошёл 451 проверку: default/light/contrast,
GUI 1/2/3, компактные окна, настройки и ограничения сервера. Удалённый CI не запускался.


### Карта: обзорные уровни и границы областей — 2026-10-09

Продолжение текущего рендера по ориентиру Xaero’s: новые ночной/биомный/рельефный/
пещерный режимы остаются отдельным этапом. Изучена локальная реализация организации
текстур Xaero: фиксированные страницы и более широкая область мира на старших уровнях.
Rivet теперь хранит отдельные страницы 64×64 для каждого уровня, ключ включает уровень.
При смене масштаба используются готовые родительские/дочерние страницы, пока новый
уровень отсутствует или заполнен частично. Готовые текстуры остаются между открытиями.

MapTerrainCache разделяет подробные изображения (8192) и компактные обзорные уровни
(до 100000 чанков); высоты южной/восточной границы хранятся отдельно. Соседние известные
чанки читаются с диска для корректного освещения, неизвестные не исследуются рендером.
Изменение границы инвалидирует зависимые соседние изображения. Дисковое чтение и запись
тайлов буферизованы; формат и совместимость v1/v2 сохранены. GPU-кэш ограничен 2048
страницами, фоновые очереди и бюджет загрузок ограничены. Пустые placeholder-чанки
не стирают сохранённую поверхность; исчезнувшие колонки реального чанка очищаются.

Нативный MapTerrainHarness создаёт 9216 файлов чанков (больше подробного кэша), сверяет
36864 пикселя девяти обзорных страниц с независимым расчётом, проверяет отрицательные
координаты, границы, повторное открытие, смену детализации и изменение высоты на стыке.
Первый полный прогон: 25 проверок, загрузка с диска около 1465 мс. Это локальный замер,
не сравнительный бенчмарк Xaero. MapLoadingHarness: 165 реальных чанков за 302 мс,
первые текстуры за 55 мс; движение и изменения блоков прошли. Принудительно выполнены
369 core-тестов без ошибок. Полный WorldMapHarness: 785 проверок, GUI1/2/3,
default/light/contrast и компактные окна. Установочный JAR не упаковывался,
версия не менялась, удалённый CI не запускался.

Полный MinimapHarness после изменений общего кэша: 451 проверка,
default/light/contrast, GUI1/2/3, компактные окна и серверные ограничения — OK.
Финальная проверка MapTerrainHarness с признаком полной готовности страниц:
25 проверок, 9216 чанков за 1409 мс; все 36864 пикселя, повторное открытие,
детализация и обновление границ прошли.
При финальном визуальном просмотре обнаружено просвечивание мира через фон карты:
фон зависел от оставшегося глобального shader color. WorldMapScreen явно восстанавливает
белый непрозрачный цвет и завершает рисование фона перед текстурами. После перекомпиляции
проверен снимок клиента: неоткрытая область имеет ровный непрозрачный фон.
Клиент разработки оставлен открытым с картой для просмотра пользователем.

### Выравнивание полей форм — 2026-10-09

По снимку пользователя исправлено расхождение правого края названия и координат
в MapMarkerPanel. Название занимает общую ширину содержимого, X/Y/Z распределяются
через NativeLayout.row с одинаковыми промежутками 4 GUI px и учётом остатка деления.
Тот же расчёт применён в LocationEditor и автоматически в компактных подробностях
метки, использующих MapMarkerPanel.extras. Сценарии редактирования не менялись.
Проверены снимки карточки метки и редактора места. WorldMapHarness: 785 проверок,
GUI1/2/3, default/light/contrast и компактные окна. DialogUiHarness: 288 кадров,
полные диалоги в трёх профилях/масштабах, выборочные остальные темы, геометрия
и возврат/фокус/валидация — OK. Компиляция прошла; установочный JAR не упаковывался.


### Общий аудит выравнивания окон — 2026-10-09

Запрос уточнён: проверить весь интерфейс, а не две формы карты. Обход исходников
охватил 64 файла экранов; причины и состав правок — в истории этого файла (отдельный промежуточный аудит удалён).
Заголовки списков и управляющие строки используют ширину содержимого с учётом
полосы прокрутки. Исправлены поиск участников/предметов/настроек, подписки,
объявления, роли, профиль, уведомления. Многострочное поле владеет всей внешней
шириной вместе с внутренней полосой; локальные компенсации ширины удалены,
кнопки форм и счётчики выровнены. Сетки тем/скинов не теряют остаток деления.
Категории и подробности метки, короткие подтверждения уменьшены по содержимому;
фиксированная форма категорий резервирует полосу только при переполнении.
В компактных размерах устранены пересечение навигации настроек с редактором HUD
и неиспользуемая область прокрутки редактора задачи. Подсказка карточки появляется
только для обрезанного заголовка с задержкой, не закрывает соседей всем описанием.

Локально прошли DialogUiHarness: 306 состояний (default/light/high contrast,
GUI 1/2/3), AdaptiveUiHarness: 198 состояний на шести размерах окна, NextUiHarness:
138 состояний с переходами/отменой/темами и масштабами 1–3. Проверены ввод, фокус,
возврат, валидация и перетаскивание внутренней полосы многострочного поля.
Принудительно выполнены сфокусированные ScrollLayoutTest. Визуально просмотрены
30 состояний диалогов и 40 дополнительных состояний GUI 2. NextUiHarness учитывает
фактическую геометрию UiWorkspace и компактный выбор вкладок; обязательные проверки
доступности действий и отмены сохранены. Установочный JAR не упаковывался,
версия не менялась, удалённый CI не запускался.

Финальный WorldMapHarness после общих правок: 785 проверок, GUI 1/2/3,
default/light/contrast и компактные окна — OK. Проверены реальные категории,
сохранение/отмена меток и вложенные диалоги.
Финальный снимок категорий поверх редактора метки просмотрен: общий правый край
полей, переключателей, кнопки сохранения и верхней строки совпадает; внешние
отступы симметричны. Клиент разработки оставлен открытым на этом окне.


### Пещерная карта по поведению Xaero’s — 2026-10-09

Пользователь явно поручил реализацию пещерного режима по Xaero’s. Сверены строки
настроек и поведение локальной версии World Map: потолок 3×3, Top Y, Layered
(16-блочные диапазоны), Full, задержка переключения и освещение по глубине.
Реализация самостоятельная. Это согласование модели исследования: читаются
уже полученные клиентом чанки, отдельный обход по видимости тоннелей не вводится.
Ночной/биомный/рельефный режимы к этому этапу не относятся.

MapLayer разделяет поверхность, пещерные диапазоны и Full во всех ключах:
диск, индекс, очередь выборки, CPU-изображения, обзорные страницы GPU и композиция
миникарты. Старый формат и путь поверхности сохранены; пещеры находятся в отдельной
подпапке измерения. Кэш не подставляет поверхность или другой слой при ожидании.
MapCaves хранит настройки по UUID мира/измерению, общий выбор для двух карт;
автоматика требует непрозрачный потолок 3×3, игнорирует листву и жидкости.
MapCaveTransition задерживает вход/выход на 500 мс, сбрасывается при смене мира.
Выборка срезает сплошной потолок и ищет открытый пол в заданной глубине;
Full снимает первый потолок без ограничения глубины. Метки используют пол текущего
слоя. Свет блоков сохраняется в исходных данных; освещение по глубине вычисляется
при отрисовке и меняет также уже сохранённые участки.

Кнопка пещеры добавлена в панель карты, тот же диалог — в настройки карты и поиск.
Общий UiDialog/scrollForm: режим, тип, ввод верхнего Y с проверкой границ измерения,
глубина, освещение, высота игрока. Неактуальные поля Full отключены. Серверный
map.caves добавляется через существующий механизм обновления TOML; запрет
переключает обе карты на поверхность, сохранённые слои остаются.

Локальные core-проверки принудительно выполнены: 44 теста MapLayer,
MapCaveTransition, MapCaveLighting, MapRepository и ServerSettings — без ошибок.
WorldMapHarness после добавления слоёв и кнопки: 785 проверок, GUI 1/2/3,
default/light/contrast и компактные окна — OK.

MapCaveHarness использует временное хранилище и изменяет блоки только в клиентском
чанке с восстановлением. Два этажа, удаление потолка, ограничение глубины,
отрицательные слои в core, индексы/кэши/GPU, высота новой метки, миникарта,
серверный запрет и смена освещения сохранённых данных проверены. Автоматика:
распознавание реального потолка/листвы/неба проверяется на клиентских блоках;
задержка — с детерминированным временем MapCaveTransition, поскольку сервер
корректирует искусственную локальную позицию игрока. Удалённый CI не запускался;
установочный JAR не упаковывался и версия не менялась.

Финальный MapCaveHarness: 67 проверок и 36 кадров, default/light/high contrast,
GUI 1/2/3, выборочные остальные темы и компактное окно. Ввод неверной/верной
высоты, выбор Full, возвращение к родителю и сохранение настроек прошли;
общий правый край футера в компактном окне проверяется отдельно.
Полный MinimapHarness после изменений: 462 проверки, три профиля и масштаба,
компактные окна, настройки, редактор HUD, метки и серверный запрет — OK.
Снимки нового диалога просмотрены в обычной/светлой теме и компактном окне.
Клиент разработки открыт на ручном подземном срезе Y=24. Финальный снимок
реального мира просмотрен: видны пещеры, шахты, вода и лава; поверхность не
подмешивается. Кнопка пещер находится внизу левой панели инструментов.


### Исправление расхождений пещерной карты — 2026-10-09

Пользователь справедливо указал, что настройки и поведение не совпадают с Xaero.
Повторная сверка выполнена по фактической паре World Map 1.46.0 / Minimap 26.5.0.
Матрица значений, принятые решения и оставшиеся ограничения — XAERO_CAVE_PARITY.md.
Предыдущую запись о соответствии нельзя трактовать как доказательство паритета.

Добавлены потолки 1×1/3×3/5×5, следование миникарте, отдельные интервалы 0–10 с,
пещерный масштаб 1–4×, показ Y, тип новых измерений и клавиша ручной миникарты.
Начальные глубина/свет: 30/выкл.; существующие явные значения сохраняются.
Top Y применяется сразу; Auto/пустая строка возвращают автоматику.
Исправлены включительная нижняя граница, распознавание воздуха под потолком,
вход Full под грунт, небесный свет, пустые обследованные столбцы и освещение
от реального Top Y. Пересчёт цветов сохраняет видимые текстуры; ChoicePopup
не переключает активный слой. Публичное руководство обновлено.

Принудительно выполнены 16 core-тестов переходов, освещения и хранилища.
MapCaveHarness: 92 проверки, 36 кадров, GUI 1/2/3, default/light/high contrast,
выборочные остальные темы и компактное окно; пройден. Кадры просмотрены.
Компиляция mod и uiHarness прошла. Установочный JAR не собирался; версия
не менялась; удалённый CI не запускался. Полный пиксельный паритет с Xaero
не заявляется: ограничения сохранённых прозрачных слоёв указаны в матрице.


### Чёткость карты при масштабировании — 2026-10-09

У большой карты уровень детализации выбирался по GUI-пикселям, поэтому при
GUI 2–4 блоки усреднялись раньше, чем становились меньше физического пикселя.
MapDetail теперь учитывает GUI scale; WorldMapScreen использует этот расчёт.
Миникарта использовала линейное увеличение и слишком рано уменьшала источник
из-за фиксированного лимита 512. Увеличение переключено на nearest; линейная
фильтрация уменьшения сохранена для поворота. Детализация выбирается по
физическим пикселям, источник ограничен 2048; композиция остаётся вне
рендер-потока, готовая текстура сохраняется до замены.

MapDetailTest принудительно выполнен: 4 теста. MinimapRefreshHarness: 22 проверки
движения, замены/изменения размера, clamp и реальных GL-фильтров после upload — OK.
Компиляция клиента прошла. Установочный JAR не собирался; CI не запускался.

Дополнительно сняты 12 состояний большой карты и миникарты при разных масштабах,
включая GUI 1/2/3. Просмотрены общий план и увеличение: границы пикселей чёткие.
Исправленный клиент оставлен открытым на большой карте.


### Единые настройки карты и расширение отображения — 2026-10-09

Пользователь разрешил объединить карту, миникарту и навигацию в один модуль.
Шестерёнка большой карты открывает модальный MapSettingsScreen с пятью категориями.
Отдельные разделы карты и навигации убраны из меню общих настроек; глобальный
поиск сохраняет доступ и раскрывает нужную категорию. Существующие параметры
не сбрасываются. Рабочая матрица и проверки — истории этого файла (отдельная промежуточная заметка удалена).

Добавлены пересчёт исходных прозрачных слоёв, свет/высота/склоны, биом под курсором,
размер и порог масштаба меток, радар с фильтрами и серверным map.radar, PNG
видимой области текущего слоя. Формат v3 совместимо читает v1/v2; старые составные
RGB постепенно заменяются исходными слоями при выборке клиентских чанков.
Радар читает только полученные клиентом сущности. Экспорт не генерирует чанки,
оставляет неизвестное прозрачным и ограничивает изображение 4096 пикселями.

86 core-тестов выполнены принудительно, компиляция клиента и uiHarness прошла.
MapModuleHarness: 534 проверки/126 кадров, MapCaveHarness: 92/36;
AuditFixesUiHarness: 339 настроек/12 кадров. Снимки новых окон просмотрены.
Полный визуальный паритет Xaero не заявляется: покрытие иконок существ ограничено,
а пиксельное сравнение шейдеров не проводилось. Установочный JAR не упаковывался,
версия не менялась, удалённый CI не запускался.

Полный WorldMapHarness обнаружил перекрытие редактируемой метки в компактном
окне после расширения панели инструментов. Отступ центрирования теперь учитывает
доступный промежуток и размер метки. Повторный полный прогон: 785 проверок,
GUI 1/2/3, default/light/contrast и компактные окна — OK.

Финальный полный MinimapHarness: 638 проверок, GUI 1/2/3, default/light/contrast,
компактные окна, настройки, HUD, метки и серверный запрет — OK. Установочный JAR
не упаковывался; версия не менялась; GitHub CI не запускался.


### Отдельная кнопка настроек карты — 2026-10-09

По запросу пользователя шестерёнка вынесена из панели инструментов и закреплена
в левом нижнем углу большой карты. Панель резервирует место снизу; в низких окнах
уменьшается вертикальный шаг инструментов, чтобы не перекрывать кнопку и метки.
Карта, миникарта, пещеры, метки, радар и навигация объединены по функциям и UI;
внутренняя изоляция не полная: ClientNavigation регистрируется отдельно, параметры
указателя хранятся в HudSettings. Не утверждать, что весь код и конфигурация
перенесены в самостоятельный изолированный компонент.

После восстановления остановившегося тестового сервера полный WorldMapHarness
прошёл: 785 проверок, GUI 1/2/3, default/light/contrast и компактные окна.
Просмотрены снимки обычного и компактного окна. Установочный JAR не собирался.


### Радар и стиль настроек карты — 2026-10-09

По шести замечаниям пользователя исправлены UV голов (овца/курица), добавлены
выступающие части морд/уши/рога и кролик, увеличены читаемые портреты. Вместо яиц
призыва неизвестные мобы используют точки. ItemEntity и прочие объекты исключены
из радара и его настроек; видимы только игроки и Mob. Игроки используют лицо и
верхний слой своего PlayerSkin; в нативной проверке добавлены клиентские Steve/Alex.
Стороны света миникарты локализованы, RU: С/В/Ю/З.

MapSettingsScreen использует UiSettingsShell.module: тот же размер модального окна,
левые разделы/поиск/редактор HUD, правая область параметров, компактный выбор
разделов. Поиск внутри модуля ограничен картой и навигацией. Кнопка пещер удалена
из инструментов; путь: шестерёнка → Большая карта → Пещерная карта. Контекст
пещер сохраняется также при поиске. Функциональная структура модулей описана
в docs/development/modules.md; изоляция кода в этой задаче не переделывалась.

Первый MapModuleHarness: 536 проверок/126 кадров — OK. Лист портретов игроков и
мобов и снимок миникарты с русскими буквами просмотрены. Полный паритет Xaero
по всем портретам не заявляется: неподдерживаемые существа остаются точками.

Финальный MapModuleHarness: 542 проверки/126 кадров — OK, включая поиск только
по модулю, возврат к настройкам, вход в пещеры и отсутствие их кнопки в инструментах.
Локальные фикстуры игроков и мобов удаляются после теста; сервер не меняется.

AuditFixesUiHarness после изменения оболочки: 333 настройки, GUI 1/2/3, 12 кадров
— OK. Клиент разработки запускается с двумя клиентскими фигурами TestSteve/TestAlex
для проверки радара, без серверных игроков. Установочный JAR не упаковывался,
версия не менялась, удалённый CI не запускался.

### Изоляция модуля карты и головы мобов — 2026-10-09

По запросу пользователя карта приведена к принятой композиции внутренних модулей.
ClientModules устанавливает ClientMap; он владеет регистрацией событий, клавишами,
отрисовкой миникарты/указателя, обработкой навигации и остановкой карты. Отдельная
регистрация ClientNavigation удалена. MapHudPreferences перенесены в MapSettings:
положение миникарты, параметры и положение указателя. Старые значения из hud.json
импортируются один раз в map-settings.json.hud; исходные ключи не уничтожаются.
Общий редактор HUD остаётся потребителем настроек карты, как общий UI Kit.
Серверный map с зависимостью base и политикой map.enabled сохранён.

Пользователь уточнил, что иконки должны показывать головы. Для отсутствовавших
ванильных портретов MapMobHeads выбирает части головы модели текущего рендера;
MapMobIcons создаёт обрезанную по альфе текстуру. Туловище и конечности не рисуются;
для существ без отдельной головы берётся лицевая часть. Отдельно учтены лама,
шалкер, дракон, насекомые и рыбы. Кэш ограничен 256 текстурами, работа — двумя
генерациями на кадр; неподдерживаемые сторонние модели повторяются с паузой.
Модель и графическое состояние восстанавливаются. Mixin accessors используются
вместо нестабильной рефлексии. Ресурсы кэша освобождаются при смене мира/ресурсов.

Четыре core-теста миграции MapHudPreferences выполнены без ошибок.
checkModuleComposition пройден. MapModuleHarness: 542 проверки/126 кадров — OK.
MapMobIconsHarness проверяет все 82 ванильных типа Mob и сохранность состояния
рендера. Набор из пяти листов голов просмотрен; сторонние модели не проверены
исчерпывающе. Установочный JAR не упаковывался, версия не менялась, CI не запускался.

Полный MinimapHarness после изоляции: 616 проверок, GUI 1/2/3,
default/light/high contrast, компактные окна, редактор HUD, сохранение масштаба
указателя в map-settings.json.hud и серверный запрет — OK. Удалённые настройки
предметов/прочих сущностей сократили число строк относительно старого прогона.

Финальный прогон голов: RIVET_MOB_ICONS_OK для 82 типов и пяти листов.
У ламы/верблюда отдельно убрана длинная шея; финальные листы просмотрены.
Клиент разработки оставлен открытым на карте с клиентскими тестовыми мобами
и двумя тестовыми игроками Steve/Alex; серверные сущности этим показом не добавляются.

### Проверка скинов, перезахода и пещер — 2026-10-09

Пользователь подтвердил, что движение/отрисовка карты работают хорошо, и поручил
проверить интеграцию с модулем скинов, переходы поверхность–пещера и сохранение.
Добавлен opt-in MapIntegrationHarness: upload через настоящий SkinWire на локальном
тестовом сервере, RemotePlayer с профилем тестового аккаунта без переопределения
getSkin, настоящие MapRadar.world/minimap и проверка пикселей лица/верхнего слоя.
Две последовательные смены (wide/slim) прошли; исходный выбранный скин восстановлен,
созданные тестом записи удалены. Перед пиксельной проверкой ожидается исчезновение
загрузочного overlay; между upload соблюдается серверная пауза. Ожидаемый хэш
рассчитывается после штатной нормализации PNG, как на сервере.

MapIntegrationHarness write проверил сохранение после настоящего disconnect/connect;
отдельный запуск verify проверил загрузку с диска после завершения процесса клиента.
Проверены размер/масштаб/вращение/прозрачность миникарты, размещение и навигация,
свет/склоны, общие настройки пещер и ручной срез конкретного мира/измерения.
Оба запуска завершены успешно; исходный файл восстановлен из резервной копии.
Режим recover использует сохранённые исходный выбор и ID только тестовых скинов.

MapCaveHarness расширен реальными часовыми задержками входа/выхода и проверкой
автоматического слоя миникарты, а также возврата GPU-карты на поверхность.
Первое расхождение оказалось артефактом теста: сервер исправлял локально заданный Y,
поэтому миникарта успевала выбрать другой диапазон. Фикстура согласовывает телепорт
тестового игрока с сервером и временно включает полёт в creative; исходные положение
и полёт восстанавливаются. Блоки пещеры изменяются только на клиенте и восстанавливаются.

Финальный MapCaveHarness: 101 проверка/36 кадров — OK; автоматические переходы,
ручные этажи/Full, глубина/свет, кэш/GPU, обе карты, модальные настройки, компактное
окно и серверный запрет. Проверки выполнены локально; production-код в этой задаче
не менялся, установочный JAR не упаковывался и удалённый CI не запускался.


### Хранилище карты, большой обзор и занятая очередь — 2026-10-09

По разрешению пользователя проверены исследованная территория после перезахода/
перезапуска, изоляция миров и измерений, старые форматы и большой локальный кэш.
69 core-тестов карты выполнены принудительно и прошли, включая форматы v1/v2 → v3,
идентичность мира и разделение данных по миру/игроку/измерению.

Добавлен opt-in MapStorageHarness (write/reuse/verify): изолированные UUID миров,
16384 сохранённых чанка поверхности, отдельные Nether/End и пещерный слой.
Смена идентичности проверяется через поле серверных метаданных в тестовой фикстуре,
а не подключением к двум физическим серверам. Фикстура закрепляет это поле перед
штатным tick: периодические пакеты настоящего тестового сервера иначе сбрасывали
искусственный мир и давали ложную картину постоянной перезагрузки кэша.
Настоящие переход в Nether/обратно и disconnect/connect прошли (19 проверок);
отдельный процесс verify подтвердил чтение после перезапуска (2 проверки).
Локальный замер 16384 чанков: первые GPU-страницы 584 мс, весь обзор 9314 мс;
прогретый кадр median 9.54/p95 10.28 мс, pan/zoom median 9.34/p95 17.45 мс.
Лимиты исходных плиток, цветных изображений, GPU-страниц и подготовленных текстур
соблюдены. Наблюдавшиеся 674/812 MiB — занятая heap всей JVM, не память модуля карты.

MapSaveQueueHarness воспроизвёл реальную потерю последнего dirty-чанка при reset
с полной очередью: flush отклонялся, затем reset очищал dirty и tiles.
В WorldMapClient обычные задачи ограничены 256 местами; ещё одно место зарезервировано
для финального снимка reset/shutdown. Запись остаётся в той же FIFO-очереди, без
записи на render thread. Следующий мир получает данные через чтения после снимка,
поэтому не может накопить ещё один финальный снимок, пока резерв занят.
Загрузка меток повторяет попытку постановки в очередь после отказа из-за занятости.
Финальный native-тест подтвердил сохранение на reset и shutdown, порядок старой/
новой записи, сохранение dirty при отклонённом периодическом flush и повторную
загрузку меток: RIVET_MAP_SAVE_QUEUE_OK reset=true fifo=true markersRetry=true shutdown=true.
Установочный JAR и версия не менялись; удалённый CI не запускался.
Дополнительный MapLoadingHarness после правки очереди прошёл: 165 чанков холодной
загрузки за 644 мс (первые 149 мс), движение через границу 49 чанков и обновление
после установки/удаления блока — OK. Предел измерения движения включает намеренные
четыре секунды перемещений фикстуры, а не только ожидание загрузки.


### Разделы настроек карты без повторных входов — 2026-10-09

По запросу пользователя пещерные параметры перенесены из кнопки внутри «Большой
карты» в самостоятельный раздел общего окна настроек. MapCaveScreen сохранён
как совместимая точка входа в этот раздел, отдельного вложенного диалога больше нет.
Используются общие sidebar/scroll/footer, поиск ведёт к каждой пещерной строке;
сброс раздела восстанавливает общие пещерные опции и вид текущего измерения,
не удаляя сохранённые виды остальных миров/измерений. При открытии из большой
карты используется выбранное на ней измерение.

Убран повтор «Расположение и размер…» из миникарты: остаётся общий редактор HUD.
Два входа в тот же экран клавиш (увеличение миникарты и ручные пещеры) объединены
в общую кнопку «Клавиши…». В компактном окне редактор HUD и клавиши доступны
через меню разделов. Отдельные переключатели радара/смертей, задержки пещер и
масштабы сохранены: они относятся к разным картам или режимам и не дублируют
один и тот же параметр. Поиск пещер больше не возвращает все строки миникарты
из-за чрезмерно широких aliases; переход из поиска сохраняет исходного родителя.

MapModuleHarness расширен на шестой раздел и отсутствие повторных входов:
584 проверки / 144 кадра — OK (три GUI масштаба, светлая/контрастная темы,
остальные темы на представительных экранах, компактное окно).
MapCaveHarness после переноса: 101 проверка / 36 кадров — OK; высота, режимы,
автопереходы, политика сервера и компактная геометрия. Снимки просмотрены,
длинная подпись общей кнопки клавиш сокращена, чтобы не обрезалась.
Установочный JAR не собирался; клиент разработки открывается на пещерном разделе.


### Общие настройки Rivet и вход из нижней части меню — 2026-10-09

Пользователь разрешил такую же ревизию общих настроек и перенос входа из профиля
в самый низ меню. MenuSidebar закрепляет «Настройки» внизу, отдельно от прокрутки
страниц; в компактном списке это последний пункт. Клиентские настройки доступны
независимо от включённых серверных разделов и роли. UiNavigation открывает их
модально с исходной страницей как родителем. Из карточки профиля, меню «⋯» и
пункта «Интерфейс и голос…» удалены повторные входы; диагностика голоса сохранена.

Новый раздел «Общие»: часовой пояс (ранее в оформлении), голограммы складов
(ранее в виджете), редактор виджетов главной (ранее в профиле). Форматы хранения
этих предпочтений сохранены. Остальные разделы: оформление, виджет, уведомления,
TAB и чат. Общие кнопки Minecraft/клавиши/редактор HUD доступны и в компактном меню.
Убраны повторные контролы масштаба Minecraft и текста/фона его чата: они доступны
в штатных настройках через общую кнопку. Прежние значения не меняются.

Сброс оформления сохраняет часовой пояс и GUI-scale; сброс виджета сохраняет
голограммы и положение/размер из HUD-редактора; сброс чата меняет только параметры
Rivet. Профили оформления также перестали сбрасывать размер HUD. Поиск обновлён
по новым владельцам параметров и возвращается к исходной странице без накопления
окон настроек. Руководство игрока обновлено по актуальным путям.

SettingsReworkHarness: 259 проверок / 162 кадра — OK; перенос кнопки, единственный
вход, поиск, редактор главной, Minecraft/клавиши, границы сброса, компактное меню,
все разделы на GUI 1/2/3, светлая/контрастная темы и остальные темы на примерах.
Снимки главной и общих настроек просмотрены. AuditFixesUiHarness проверяет все
345 попаданий каталога на GUI 1/2/3, профильные действия, старые сценарии меню и
сохранение нестандартного размера HUD при применении профилей.
Тесты используют локальную фикстуру интерфейса, не отправляют обращения на сервер.
Настройки тестового клиента сохранены до прогона и восстанавливаются после него.
Установочный JAR, версия и Git-коммиты не создаются.

### Следующий этап после ревизии настроек — 2026-10-09

Пользователь отклонил общую кнопку «Minecraft…»: она не нужна. Удалить её
из обычной и компактной компоновки при следующем этапе, обновить соответствующие
проверки и руководство. Удалённые дубли штатных настроек обратно не возвращать.
Текущий запрос — назвать следующий этап; новые функции в этом ходе не реализуются.
По сохранённому плану остаётся блок видов карты (ночной, биомы, рельеф), затем
территории, общие данные/права/приватность, импорт меток и интеграция Waystones.
Пещеры, категории, радар, сохранение и настройки уже прошли отдельные этапы;
старые списки «следующих этапов» в FUTURE_MODULES читать с учётом этих результатов.

### Виды карты и удаление кнопки Minecraft — 2026-10-09

Пользователь подтвердил следующий этап. Убран переход «Minecraft…» из обычного
и компактного окна общих настроек. Общие клавиши и редактор HUD сохранены.
Раздел карты «Большая карта» переименован в «Отрисовка», поскольку его параметры
общие для большой карты и миникарты. Добавлен выбор: обычный (по умолчанию),
ночной, биомы, рельеф. PNG использует тот же стиль. Выбор сохраняется в display.view;
старые конфигурации получают обычный вид. Сброс отрисовки также сбрасывает вид.

Ночной вид использует записанный свет блоков и свечение слоёв. Формат тайла v4
добавляет отдельный байт света на колонку; чтение v1/v2/v3 сохранено, неизвестный
свет обозначается -1 и обновляется при повторном сканировании. Для биомов без
данных применяется нейтральный серый, неизвестные модовые ID получают стабильный
цвет. Рельеф окрашивает высоту грунта (включая дно воды), подсвечивает склоны
и линии через 16 блоков. Неисследованные пиксели остаются прозрачными.
Это собственная реализация видов; точное совпадение палитры Xaero не проверялось.

Core map tests: 76, без ошибок. MapViewsHarness на локальном сервере: 26 проверок,
все четыре вида, CPU/GPU пиксели, обновление миникарты, PNG, сохранение настройки
и старые значения. Фикстура принудительно использует поверхность и восстанавливает
пещерные/визуальные настройки; обычный вид не требует пересоздания готовой миникарты.

SettingsReworkHarness после удаления Minecraft: 258 проверок / 162 кадра — OK.
MapModuleHarness с новым выбором вида: 584 проверки / 144 кадра — OK, три масштаба,
светлая/контрастная темы и прочие темы на примерах, компактное окно. Просмотрен
снимок нового раздела. Настройки тестового клиента восстановлены после прогонов.
Установочный JAR, изменение версии, коммит и запуск GitHub CI не выполнялись.

### Коррекция автоматики и настроек отрисовки по Xaero — 2026-10-09

Пользователь справедливо указал: ручной ночной вид не соответствует автоматическому
освещению Xaero. Сверены официальные описания и фактические профили World Map 1.46.0 /
Minimap 26.5.0. Матрица и ограничения — XAERO_RENDER_PARITY.md. Предыдущую запись
про четыре ручных вида считать отменённой для пользовательского интерфейса.

В UI вместо ручных видов — «Цвета блоков: Точные / Minecraft». Освещение автоматически
учитывает getSkyDarken (время и погода), ambient измерения, отдельные небесный/блочный
свет грунта и прозрачных материалов. Выключение освещения оставляет полную яркость.
display.view больше не загружается/сохраняется и не влияет на карту. Формат v5
добавляет skyLight, читает v1–v4. Кеши получают изменения окружения без пересканирования
блоков; PNG фиксирует окружение в момент экспорта.

Добавлены параметры смешивания биомов, биомных цветов стандартной палитры, цветов,
редстоуна, стекла, прозрачности, поправки тонких блоков. Стандарт: точные цвета,
lighting/depth=true, slopes=2, указанные детали включены, biomesVanilla=false.
Новая миникарта: square, opacity=1, zoom=1; явные старые значения сохраняются.
Настройки выборки пересканируют доступные чанки, удалённые записи — при посещении.

Core: 82 проверки — OK. MapViewsHarness переориентирован на автоматическое окружение:
24 проверки — OK (день, ночь, гроза, свет выключен, CPU/GPU/PNG, миникарта, палитра,
исходные настройки). Снимки дня и ночи просмотрены. MapModuleHarness: 584 проверки /
144 кадра — OK после расширения отрисовки. Полное пиксельное совпадение с Xaero
не доказано; не повторять прошлое обещание полной идентичности.
MapCaveHarness после разделения света: 101 проверка / 36 кадров — OK (переходы,
ручной слой, глубина, CPU/GPU/мини, политика сервера, компактный диалог).
Дополнительно выключенное освещение отключает специальное legible-затемнение;
добавлен отдельный core-тест этого приоритета. Установочный JAR и версия не меняются.

### Живое ночное освещение и аудит обновлений — 2026-10-09

Пользователь обнаружил, что серверная ночь не меняет карту. Причина подтверждена:
Level.getSkyDarken() без аргументов возвращает cached skyDarken, ClientLevel обновляет
его при создании. Прежний native-тест вызывал updateSkyBrightness() вручную и скрывал
ошибку. Production использует ClientLevel.getSkyDarken(1f), который вычисляет текущее
небо по времени/осадкам. MapViewsHarness больше не вызывает setDayTime,
updateSkyBrightness или tickLight: время/погода меняются серверными командами.

По дополнительному запросу проверена цепочка настроек, источника света, sampler,
пещерного выбора, CPU/GPU кеша, миникарты и экспорта. Исправлены две дополнительные
гонки состояния: старый асинхронный пересчёт не публикуется после смены настроек /
освещения; MapExportScreen выбирает текущий слой в момент нажатия, а не при открытии.
CPU проверяет stamp, region/minimap проверяют styleEpoch; сохраняется последняя
полная картинка, устаревшие подготовленные NativeImage освобождаются. Экспорт чужого
пещерного band использует ceiling этого band, не текущий выбранный слой.

Live lighting harness: 26 проверок — OK, реальные серверные день/ночь/гроза,
освещение выключено, PNG, миникарта и отклонение устаревшего worker результата.
При смене погоды PNG сравнивается со снимком цвета на момент запуска экспорта,
а не с уже изменившейся картой в момент завершения. Прежний второй вариант давал
ложный провал, поскольку экспорт намеренно фиксирует окружение до запуска потока.
Cave harness дополнен реальным нажатием Export после переключения с верхнего
на нижний этаж при уже открытом диалоге; проверяются пиксели PNG нижнего этажа.
Итог: 104 проверки / 36 кадров — OK. Клиент разработки открыт на большой карте.
Установочный JAR, версия и коммиты не создавались.


### Удаление рабочей интеграции Xaero — 2026-10-09

Удалены XaeroBridge, XaeroMapBridge и ManagedXaeroLayer. Клиентская диагностика
сохраняет Plasmo Voice, не регистрирует возможности Xaero. «На карте» и
«Сохранить метку…» открывают WorldMapScreen Rivet; сохранение начинается с
черновика. MapLayerClient передаёт разрешённые сервером общие места и добровольные
позиции в собственные world/minimap overlays. Общие точки не записываются в
личное хранилище; просроченные позиции исчезают. Головы участников используют
SkinClient. Настройка directionMap управляет native-меткой цели; старое значение
directionXaero читается однократно при отсутствии нового поля.

84 core map tests — OK. Native RivetWorldMapHarness на сервере без Xaero:
открытие/центрирование/возврат, общие точки, истечение позиций, фильтрация
закрытых/выключенных мест, неизменность личных меток — OK.
MapModuleHarness: 584 проверки / 144 кадра — OK, масштабы и темы.
Документация активных интеграций обновлена. JAR, версия, коммит и CI не выполнялись.
Импорт Xaero/JourneyMap и адаптер Waystones ещё не реализованы; Waystones
остаётся подтверждённым заданием (доступные игроку точки, только отображение).
Пиксельное сравнение рендера с Xaero по-прежнему не завершено.


### Адаптер Waystones и родные иконки — 2026-10-09

Реализован автоматический read-only адаптер Waystones, проверяемый через
OptionalIntegration. Сервер использует getTargetsForPlayer из Waystones:
активированные + глобальные + командные обычные камни. Общая база скрытых
камней не перечисляется и клиенту не отправляется. Транзитные точки,
Sharestones и Warp Plates в этот слой не входят. API проверяется отражением,
обязательной зависимости сборки и загрузки классов Waystones при отсутствии
мода нет. Проверенная версия: Waystones 21.1.46, Balm 21.0.66, NeoForge 1.21.1.

WaystonesWire — отдельный optional play channel, bounded pages 128 × 32,
sequence и атомарная публикация полного snapshot. Клиент опрашивает раз в 2 с,
сервер ограничивает частоту, проверяет включённую карту и авторизацию.
Снимок истекает через 7 с, соединение/выключение карты очищает отображение.
Серверные ограничения не задаются клиентом. Массовый snapshot >4096 точек
отклоняется целиком с диагностикой, не усекается молча.

Большая карта и миникарта используют временный слой, а не личные маркеры.
Действие камня — только маршрут; нет копирования, телепортации, активации.
По уточнению пользователя иконка рендерится предметом waystones:waystone:
настоящая модель/текстуры из установленного мода и ресурспака, без копирования
ресурсов Waystones в Rivet. Используется стандартная иконка, не определение
материала каждого удалённого камня. При отсутствии клиентского предмета —
собственный условный значок. Подписи избегают пересечений друг с другом,
личными значками и зарезервированными подписями смерти.

Native server/client harness с настоящими Waystones/Balm подтвердил:
скрытые точки не приходят, открытые и глобальные приходят, переименование,
удаление и «GLOBAL → ACTIVATION → GLOBAL» обновляются на клиенте.
Открытый игроком камень сохраняет доступ при смене видимости.
Проверены codec, атомарные части, устаревшие sequence, expiry, reset и
выключенная карта. Просмотрены снимки world/minimap; сняты GUI 1/2/3 (6 кадров).
Один промежуточный прогон ошибочно провалил неизменность личных меток из-за
смерти тестового игрока от слизня: фикстура переведена в creative/peaceful.
Повторный полный прогон успешен; проверки сохранения не ослаблялись.
Компиляция и checkUiComposition успешны. Установочный JAR, версия и CI не менялись.


Дополнительно: отдельный реальный сервер и клиент без Waystones/Balm успешно
запустились; карта доступна, optional-снимок пуст, диагностика «Не установлен».
Итоговый прогон с родными иконками: серверные и клиентские сценарии завершены,
codec/atomic/stale/policy/expiry/reset — OK, 6 кадров GUI 1/2/3, подписи
перенесены над/под точкой без пересечений. Тестовый сервер остановлен,
обычный клиент возвращён для просмотра.

### Слои существующих источников и личные территории — 2026-10-09

По подтверждению следующего этапа добавлены отдельные инструменты «Слои» и
«Территории» в WorldMapScreen. Шестерёнка остаётся в левом нижнем углу.
MapLayers хранит независимую видимость world/mini: личные метки, территории,
смерти, общие места, Waystones, местный радар игроков, участники объединения,
существа, цель маршрута. Отрисовка и hit-testing общих точек используют один
фильтр. Это не переключатели интеграций и не расширение серверного доступа.
Существующее согласие на передачу позиции не меняется. Старые выключенные
настройки читаются; включение одного слоя не включает остальные/вторую карту.
Дубли показа смертей, цели и радара убраны из MapSettingsScreen/SettingsCatalog;
PlacesScreen и MapSharingScreen ведут в «Слои». Детальные фильтры радара остаются.

MapTerritory — личный простой многоугольник 3–128 целочисленных вершин по границам
блоков, максимум 256 территорий. Разрешены вогнутые фигуры и наложения разных
территорий; запрещены самопересечения, нулевая площадь, дубли вершин и разворот
ребра на себя. Хранилище territories.json v1 отдельно по миру/игроку, ограничено
размером, асинхронное чтение/запись. Ошибка загрузки не даёт перезаписать файл,
метки загружаются независимо. Общие территории/права не реализованы этим этапом.

Создание через список: ЛКМ добавляет точку, drag двигает карту, ПКМ/Backspace
убирает последнюю, Enter/инструмент завершает, Esc отменяет. Форма редактирует
черновик (имя/цвет/координаты/удаление вершин/перерисовка), Save применяет.
Контекст внутри территории и список открывают редактор; удаление с подтверждением.
Рендер обеих карт: заливка строками, ограниченная viewport, обрезанные границы;
круглая миникарта учитывает поворот и круг. Подпись малой территории над контуром.

Проверено: 90 core map tests без ошибок; MapTerritoriesHarness — 320 проверок,
180 кадров (новые окна + все разделы настроек, GUI 1/2/3, светлая/контрастная
темы отдельно, остальные темы выборочно). Создание кликами, переименование,
отмена, неверная геометрия, разные поверхности, legacy radar master и возврат
из диалогов. Пройден checkUiComposition; просмотрены окна/карта/миникарта.
Тестовые территории удалены, настройки восстановлены. JAR для установки,
версия, коммит и GitHub CI не создавались.

Оставшаяся часть полного плана слоёв: источники задач и событий ещё не подключены;
у текущей модели задачи нет собственной точки (есть привязанные склады).
Не показывать пустые переключатели и не выдавать это за выполненную интеграцию.
Следом: серверные/групповые территории и права, общие метки/исследование,
расширенная приватность позиции, импорт Xaero/JourneyMap. Полное совпадение
рендера с Xaero по-прежнему не доказано.

### Уточнение инструментов, слоёв и создания территорий — 2026-10-09

Прямое уточнение пользователя заменяет прежнее раздельное управление слоями и
завершение территории Enter. Toolbar: остановка маршрута (только при активной
навигации), к игроку, новая метка, приблизить, отдалить, слои, территории.
Появление/исчезновение stop отслеживается в tick, без пустого места. Шестерёнка
остаётся отдельно снизу. Список меток, линейка и экспорт перенесены в контекст
карты по ПКМ; тестовые helpers прежних сценариев обновлены на эту точку входа.

MapLayers теперь хранит один boolean на источник. Оба рендера и hit-testing
используют одно значение; разделитель карта/миникарта в окне удалён. При чтении
старых объектов map/mini применяется AND: если где-то было скрыто, слой скрыт
на обеих до включения. Legacy radar/death master учитываются одинаково на обеих
картах; явное включение переводит их в общее состояние, сохраняя другие слои.
Согласие на передачу своей позиции по-прежнему не затрагивается.

Создание территории: минимум три уникальные вершины; завершение только кликом
по первой в радиусе 6 GUI px. После третьей вершины первая выделена рамкой.
Enter поглощается без завершения, прежней кнопки Finish нет. ПКМ/Backspace/Esc
сохраняют прежнее поведение. Цвет выбирается UiColorSwatch из тех же 12 цветов и
локализованных подписей, что у меток; сетка адаптируется к ширине диалога.

Проверка уточнений: compileUiHarnessJava/checkUiComposition — успешно;
MapTerritoriesHarness — 352 проверки / 180 кадров, GUI 1/2/3 и профили тем.
Проверены точный порядок toolbar, динамический stop без промежутка, отклонение
замыкания двух точек, отсутствие завершения Enter, замыкание кликом первой,
12 UiColorSwatch и сохранение выбранного цвета, общее выключение обоих рендеров,
миграция split-параметров и сохранение прочих фильтров. Просмотрены снимки
слоёв и палитры на GUI 3. Тестовые данные очищены, настройки восстановлены.
Установочный JAR, версия и коммиты не создавались; CI не запускался.

### Окончательное уточнение порядка toolbar — 2026-10-09

По новому прямому запросу порядок: stop (только активная навигация), к игроку,
создать метку, список меток, приблизить, отдалить, слои, территории, линейка.
Список меток и линейка возвращены на toolbar и удалены из ПКМ. Экспорт остаётся
в ПКМ. Геометрия учитывает 8/9 инструментов; активная линейка выделяется цветом.
Прежние тестовые сценарии списка/линейки снова используют кнопки панели.
Это заменяет предыдущую запись о переносе списка и линейки в контекстное меню.

Уточнённый порядок проверен в клиенте: MapTerritoriesHarness — 352 проверки,
180 кадров, GUI 1/2/3 и профили тем; compileUiHarnessJava/checkUiComposition
успешны. Проверены появление stop первым и исчезновение без промежутка.
Установочный JAR не собирался, версия и коммиты не менялись; CI не запускался.

### Задачи и события на карте — 2026-10-09

По подтверждению пользователя подключены источники задач и событий. У задачи
место назначения НЕ обязательно; склад не подставляется автоматически. В обзоре
TaskScreen доступны добавление/изменение/удаление места через общий LocationEditor,
показ на карте и маршрут. workLocation использует текущие права управления задачей,
revision, receipt и историю; null удаляет location. Старые workSave сохраняют место.
Личные/групповые права задачи распространяются и на её координаты.

CommunityMapActivities — отдельная read-модель с SQL-фильтрацией до выдачи координат:
личные задачи только владельцу, групповые по доступу к открытому объединению;
events учитывают public/group/invited и membersOnly места. Завершённые/архивные задачи,
отменённые/удалённые и закончившиеся события исключены; идёт учёт durationMinutes,
поэтому начавшееся, но ещё не закончившееся событие остаётся на карте. Пустое место
не создаёт точку. Отключённые модули не отдают координат. Пакеты по 32 записи с
keyset cursor, без описаний/участников/комментариев. mapActivities — READ, без receipt
кэша; negotiated feature map-activities, серверная проверка map.enabled.

MapActivities собирает отдельные снимки tasks/events, обновляет их каждые 3 секунды
после полного ответа, очищает при сбое/таймауте/отключении/смене соединения; старые
снимки живут максимум 12 секунд. Сборка ограничена 4096 точками и 12 секундами;
очень большой/медленный снимок отклоняется целиком, а не публикуется частично.
MapLayers TASKS/EVENTS общие для карты и миникарты; пункты скрыты при неподдерживаемом
сервере/выключенном модуле. MapSharedOverlay использует кирку для задач и звезду
для событий, контекст открывает исходную карточку или маршрут. Копирование таких
точек в личные метки не предлагается, дискового хранения координат нет. Маршрут
следует за обновлённым местом и снимается при исчезновении точки; другой вручную
выбранный маршрут не затрагивается.

Проверено: 23 PostgreSQL теста (6 новых CommunityMapActivitiesTest + 17 задач),
33 теста запросов/архитектуры меню/совместимости, compileUiHarnessJava и
checkUiComposition. MapActivitiesHarness: 182 проверки / 144 кадра, полный путь
карта → карточка → удалить/добавить место на GUI 1/2/3, default/light/contrast и
остальные темы выборочно; просмотрены слои, карточки с местом/без, LocationEditor,
миникарта. Отдельный MapActivitiesWireHarness проверяет реальные read-endpoints
без подмены транспорта. Результат его запуска фиксируется ниже.

В тестовом сервере обнаружена старая установленная копия Rivet, перехватывавшая
серверный запуск. Для dev-запуска она отключена с сохранением файла; сервер теперь
использует классы проекта и отдельные runtime-зависимости. Установочный JAR не
собирался, версия не менялась; коммитов/публикации/CI не было.

Реальный серверный read-путь подтверждён: RIVET_ACTIVITIES_WIRE_OK для обоих
источников без previewTransport. Дополнительно добавлен hit-testing значков задач
и событий на миникарте в режиме курсора: тот же контекст карточки/маршрута,
с проверкой текущей видимости/доступности и приоритетом личной метки при наложении.
В TaskScreen название измерения локализовано, новое место получает имя задачи.

Финальный игровой проход с нажатиями на миникарте: MapActivitiesHarness —
290 проверок / 144 кадра, успешно; checkUiComposition пройден.


### Территории и метки принадлежат объединениям — 2026-10-09

Прямое уточнение пользователя заменяет личные территории: один серверный контур
на объединение вместо location. Территория публичная; внутренние метки доступны
участникам/администрации. Вопрос о видимости был задан, на момент реализации
ответ не поступил; публичный контур принят как явно сообщённое допущение.
Управление по существующим правам manager (лидер/помощник/admin), revision и receipt.
Старые личные territories.json и старый group.location сохраняются для восстановления,
но не отображаются и не публикуются автоматически.

CommunityGroupMap/groupMap: READ без replay, страницы по 2 максимальных контура
для соблюдения размера сообщения; feature group-map. plusTerritorySave проверяет
права, revision, геометрию, измерение; clearTerritory переживает Gson omission null.
MapGroupClient собирает атомарный снимок с TTL 12 с, без записи на диск.
MapTerritoriesScreen/редактор работают с группой и её ревизией, создание закрывается
кликом первой вершины. При неизвестном результате сохранения повтор использует
тот же operationId. В карточке группы вместо координаты — действия контура.
Внутренние точки используют существующие group_items kind=place, приватность
принудительная на сервере, включая чтение старых публичных place. MapActivities
получил источник groupmarkers, layer и переход в объединение.

MapLayers.available скрывает отключённые источники; PEERS прекращает запросы и
сбрасывает снимок при groups=false. Все toolsMap* закрыты на сервере и экране.
Личные задачи остаются; групповые точки/маршрут скрываются сразу при отключении
объединений. Групповые вкладки tasks/events учитывают свои модули.

Проверено: 93 PostgreSQL теста (включая 6 CommunityGroupMap), 24 unit-теста,
compileUiHarnessJava/checkUiComposition; MapTerritoriesHarness обновлён на
серверный протокол fixture — 394 проверки/180 кадров, GUI 1/2/3 и профили тем.
Просмотрены список/редактор GUI3. Прежний personal файл в тесте не меняется.
Проверка настоящего group-map обмена и финального запуска фиксируется отдельно.
Установочный JAR, версия, коммиты и CI не создавались.

Финальный обычный dev-клиент открыт на карте. Без previewTransport получены
все реальные серверные снимки: tasks/events/groupmarkers/territories, подтверждены
RIVET_AUTO_MAP_PREVIEW_READY и RIVET_ACTIVITIES_WIRE_OK. Финальная компиляция и
checkUiComposition успешны; git diff --check чистый. Видимость контура публичная,
внутренние метки закрыты; автоматической миграции/публикации legacy-данных нет.


### Исправления территорий по замечаниям — 2026-10-09

Территории исключительно функционал объединений. Полностью удалены личный
reader/writer MapRepository.territories, асинхронная загрузка и API записи
WorldMapClient, конструкторы черновика без группы, тексты про личные территории.
Существующие неиспользуемые файлы не читаются и не создаются.
Название — производное от текущего title группы, отдельное имя не хранится.
Сервер игнорирует присланное имя, read-модель и карточка всегда подставляют
текущее имя группы; поддерживается полный лимит 100 символов. Поле переименования
убрано. Переименование группы проверено отдельным PostgreSQL-тестом.
Вход из карточки открывает сразу подготовленный режим контура одним переходом;
исключены повторный setScreen на той же карте и создание черновика до выбора
измерения. Дубликат действия на вкладке участников удалён.
Native harness теперь нажимает настоящую кнопку в карточке группы перед
расстановкой точек, а не только начинает через список территорий.

Проверки исправлений: 94 PostgreSQL-теста, 5 тестов геометрии/codec,
checkUiComposition и MapTerritoriesHarness — 393 проверки / 180 кадров.
Полный вход из карточки проходит GUI 1/2/3; имя в редакторе недоступно для ввода.
Первый тестовый запуск исправлен: fixture-карточка ожидала отложенного ответа
и не имела author, теперь получает полноценный ответ до нажатия.


### Добровольный обмен исследованием объединений — 2026-10-09

По продолжению принятого плана добавлен обмен исследованной местностью.
`map-exchange` согласуется отдельно, endpoints mapExchangeSettings/Consent/Put/Pull.
Согласие выключено по умолчанию, относится к одному объединению и миру; сервер
проверяет членство на каждом запросе, без обхода для администратора. Изменение
членства/закрытие группы очищает согласие; повторное вступление не включает его.
Согласие имеет receipt; повтор старого включения после остановки не возобновляет
обмен. Порции данных идемпотентны по содержимому и не засоряют журнал receipts.
Проверки прав/записи упорядочены с изменениями групп. Идентификатор мира, квоту
и разрешение пещер сервер подставляет сам, клиентские значения удаляет.

MapShareCodec передаёт 4 ограниченные порции на чанк, исходные материалы,
слои, биом, высоты, block/sky light. Пакеты укладываются в 32767, нет распаковки
неограниченного содержимого. Неизвестные пиксели не раскрывают/не стирают карту.
Квота считает уникальные чанки по измерению/пещерному слою; одинаковые повторы
не меняют cursor. Клиент получает изменения с последовательностью, записывает
cursor только после сохранения. Получение продолжает работать при заполнении
серверного хранилища; передача приостанавливается с сообщением в диалоге.

Личные карты `rivet/maps` не смешиваются с сохранением `rivet/maps-received`.
Рендер карты/миникарты и PNG объединяют их с приоритетом личных наблюдений;
отправка читает только личное хранилище и собственные результаты сканирования.
Полученное сохраняется после остановки, выхода и перезапуска; не перепубликуется
в другое объединение как своё. Отключённые map/groups прекращают обмен, но
полученная местность остаётся. Метки и контуры сами местность не раскрывают.

Настройки карты получили условный раздел «Обмен», также доступен вход из
«Карты объединения». Раздел/поиск скрыты, если обмен недоступен. Сброс раздела
не меняет согласие. Команда остановки ставится перед следующей передачей,
в том числе при нажатии во время фонового запроса. Отправка отделена от очереди
меню и ограничена собственной серверной частотой, одна порция в полёте.
Новые map.sharing=true и map.shareTiles=16384 доступны в шаблоне с пояснениями,
валидируются и добавляются владельцу с сохранением существующих значений.

Core-проверки: 100 PostgreSQL-тестов, 136 тестов моделей карты/настроек/протокола;
все успешно. Нативный сценарий сначала проверяет реальный negotiated endpoint,
затем подменяет только тестовый обмен в отдельном UUID мира. Обнаружено, что
периодические настоящие FeatureState сбрасывали тестовый мир; fixture теперь
временно изолирует Protocol.featureState и восстанавливает его при завершении.
Синтетический участок и cursor предыдущего теста удалены из dev-карты после
проверки точного содержимого. Итог последнего изолированного прогона ниже.
Установочный JAR, версия, коммиты и GitHub CI не создавались.

Изолированный финальный MapExchangeHarness: 182 проверки / 81 кадр, успешно;
полные сценарии согласия/остановки/получения/повторного открытия на GUI 1/2/3
в default/light/contrast, остальные темы выборочно. Проверены реальный read-endpoint,
отсутствие отправки до согласия, отсутствие повторной публикации полученного,
сохранение после reset, скрытие при groups=false. Просмотрен отдельный раздел
«Обмен» GUI 3; checkUiComposition успешен. Ошибок в финальном игровом логе нет.

После проверок перезапущены обычные dev-сервер и клиент из классов проекта.
Клиент открыт на карте; подтверждены RIVET_AUTO_MAP_PREVIEW_READY и реальные
снимки tasks/events/groupmarkers/territories без fixture. git diff --check чистый.


### 2026-10-09 — видимость позиции на всей карте по умолчанию

Пользователь явно уточнил default: full/all, а не прежний nearby/all.
MapPositionPolicy и CommunityMapPositions хранят только режим и аудиторию;
координаты берутся ServerMapPlayers на игровом потоке из действительных онлайн
игроков. Новый negotiated feature map-positions и read/settings/save API.
Режимы full/nearby/hidden; аудитории all/groups/none. Радиус nearby 128 блоков,
трёхмерный, в том же измерении; серверный map.nearbyRadius 1–4096.
map.positions=true управляет передачей отдельно от радара и обмена местностью.
Новые поля шаблона автоматически дополняются с сохранением выбора владельца.

Аудитория groups требует текущего членства в открытом объединении, при groups=false
закрывается; all не зависит от groups. Нет обхода администратором. Отсутствующие,
неавторизованные, spectator и invisibleTo(viewer) исключены перед отправкой.
Сервер сам создаёт ограниченный список кандидатов (64/page), стирая клиентские
служебные поля. Revision fence отсеивает устаревшие асинхронные ответы, изменение
приватности/состава объединения сбрасывает клиентские позиции. Snapshot атомарный,
TTL 7 секунд, без записи координат на диск. Старые toolsMapShare/Peers/Settings
retired; их временное согласие не является настройкой новой приватности.

Один слой «Игроки» на обеих картах использует авторизованные сервером позиции,
головы через SkinClient. Локальный радар игроков на новом сервере подавлен,
дублирующий PEERS скрыт. Маршрут к игроку следует за позицией и отменяется при
исчезновении/истечении/отзыве. Сохранение живой позиции в личную метку убрано.
Настройки карты получили «Видимость позиции», поиск и вход из меню профиля;
MapSharingScreen заменён общими режимом/аудиторией с явным сохранением.

Проверены 109 PostgreSQL-тестов (отдельная временная БД), 38 core-тестов,
компиляция UI harness и checkUiComposition. Проверяются full/all по умолчанию,
скрытие от администратора, выход из группы, groups=false, сохранение после
повторного открытия и невозможность старым повтором откатить новый выбор.
Нативный MapPositionsHarness сначала читает реальные default сервера, затем
изолированные сценарии настроек на GUI 1/2/3 default/light/contrast + остальные
темы выборочно (27 кадров). Проверка живых позиций использует отдельный fixture
без записи в БД; реальный двухпользовательский сетевой сеанс пока не проведён.
Установочный JAR, версии, коммиты и CI в этом этапе не создавались.

Финальный MapPositionsHarness завершён: 111 проверок, 9 полных UI-сценариев,
27 кадров. Дополнительно проверены дальняя позиция, подавление локального радара,
отмена маршрута и удаление позиции при отзыве, TTL, запрет сервера и независимость
от groups. Просмотрен кадр GUI3: текст и кнопки не перекрываются. Обычный dev-клиент
открыт на новых настройках видимости; dev-сервер работает с текущими классами.


### 2026-10-09 — прокрутка разделов и плоские настройки карты

По скриншотам пользователя исправлена область навигации UiSettingsShell:
UiSettingsSidebar ограничена между поиском и закреплёнными «Клавиши»/«Редактор
HUD», со своей прокруткой колёсиком/трекпадом, перетаскиванием scrollbar и
клавиатурой. Не меняет прокрутку правой формы. Переход из поиска раскрывает
выбранный раздел. Общий компонент добавлен в UI Kit и живой каталог.

«Видимость» и «Обмен» больше не содержат промежуточных кнопок в отдельные
диалоги: настройки и действия сразу в правой форме. MapPositionSettings и
MapExchangeSettings переиспользуют состояние, сохранение/согласие остаётся
явным. Входы MapSharingScreen/MapExchangeScreen открывают соответствующий
раздел общей оболочки, сохраняют родительский экран. Ненужный сброс серверного
раздела скрыт. Ширина навигации увеличена для подписей с полосой прокрутки.

MapPositionsHarness: 186 проверок, 9 сценариев, 27 кадров; нативная прокрутка
вверх/вниз/drag, последний раздел, отсутствие пересечения с закреплёнными
действиями, сохранение/повторное открытие формы на GUI 1/2/3 и темах. Просмотрен
GUI2 кадр, параметры в разделе, пункты не перекрываются.

Финальные проверки: MapExchangeHarness — 191 проверка / 81 кадр, включая
встроенный обмен, закрытие к карте и клавиатурную прокрутку разделов;
SettingsReworkHarness — 258 проверок / 162 кадра (навигация, поиск, сброс,
компактное окно, остальные разделы настроек). Все успешно, checkUiComposition
и git diff --check прошли. Просмотрен итоговый GUI2 кадр: «Пещерная карта» целиком,
«Видимость» не пересекается с закреплёнными кнопками. Обычный dev-клиент запущен
на обновлённых настройках. Установочный JAR и версия не изменялись.


### 2026-10-09 — удалён общий обмен исследованной картой

Пользователь отменил обмен из-за лишней нагрузки. Удалены MapExchangeClient,
формы/раздел/поиск обмена, CommunityMapExchange, negotiated map-exchange,
отправка результатов сканирования и приём новых участков, служебные курсоры,
сетевой кодек и запрос очистки согласий при каждом изменении группы.
Старые mapExchange* запросы явно отвергаются до обращения к хранилищу.
map.sharing/shareTiles удалены из шаблона/проверок; старые ключи игнорируются,
чтобы существующий конфиг запускался без правки. Локальные личные и ранее
полученные карты сохранены; MapTileMerge только объединяет их для рендера/PNG.
Старые записи серверной БД не удалялись, новый обмен их не читает и не пишет.
Позиции игроков, приватность, территории и метки объединений не отключались.
Тест CommunityMapExchangeTest заменён проверкой отказа старым клиентам.

Проверки удаления: 104 PostgreSQL-теста, 33 теста настроек/локального объединения
карт, checkUiComposition; MapPositionsHarness — 105 проверок, 9 сценариев,
27 кадров. Реальное согласование подтверждает отсутствие map-exchange;
раздел и поиск обмена отсутствуют, позиции работают. Dev-сервер перезапущен
без обмена, обычный dev-клиент снова открыт. JAR/версия/коммиты не создавались.


### 2026-10-09 — настоящий сетевой прогон с двумя клиентами

Добавлены opt-in MapNetworkServerHarness/MapNetworkClientHarness. Два отдельных
JVM Minecraft, игровые каталоги и тестовые профили; настоящие Protocol запросы
и серверные позиции без previewTransport/подмены снимков. RIVET_MAP_NETWORK
задаёт каталог координации фаз, RIVET_MAP_ROLE — actor/viewer; контрольные файлы
содержат только ожидания/результаты, не поставляют данные карты клиентам.

Успешны все 15 фаз: дальняя позиция, перемещение, full в другом измерении,
nearby в другом измерении/127/129 блоках, hidden, nobody, группы до вступления,
после вступления и выхода, invisible, spectator, цель маршрута и её отзыв.
Скин создан тестовым игроком через реальный SkinWire upload; наблюдатель получил
rivet:skins текстуру через SkinClient. Локальный радар не дублирует игроков.
Временные объединения помечены удалёнными, настройки позиции тестовых профилей
очищены. Тестовый скин принадлежит отдельному профилю RivetPeerTest.

Первый запуск самого harness отправлял сохранение мимо очереди меню и попадал
под rate limit при входе. Исправлен сценарий: сохранение идёт через обычный
ServerMenuClient.request; повторный полный прогон успешен с обоих клиентов.
Продуктового дефекта в этих сценариях не выявлено.

MinimapRefreshHarness: 22 проверки, movement/resize/clamp/filtering успешно.
MapTerrainHarness: 25 проверок, 9216 синтетических чанков с диска, 36864 точных
пикселя; cold load 3162 мс на этой машине. Проверены границы кеша, LOD, стыки,
повторное открытие без пересоздания текстур, обновление границ. Это тестовый
набор, не универсальная оценка времени загрузки пользовательских карт.

MinimapMotionHarness обнаружил устаревшее ожидание базового zoom=2 при нынешнем
default=1. Исправлена тестовая база: сравнивается относительное уменьшение и
восстановление текущего исходного масштаба с прежним допустимым коэффициентом.
Повторный прогон: RIVET_MINIMAP_MOTION_OK, 835 кадров, 252 промежуточных кадра,
speedZoom=0.9455 при базе 1. Функциональный код карты не потребовал исправлений.
После прогона dev-сервер запущен без сетевого harness; обычный клиент открыт
на карте. Реальный CI/внешний сервер этим локальным прогоном не проверялись.


### 2026-10-09 — явный импорт личных меток

Пользователь поручил завершить оставшийся текущий план. Дополнительно уточнил:
поддерживать только актуальные форматы, без старого JSON JourneyMap.
Добавлены MapWaypointImport (core), MapImportScreen и вход из «Метки» настроек,
включая поиск. Импорт не зависит от наличия внешних модов и не обращается к серверу.
TXT-контракт проверен по установленному Xaero’s Minimap 26.5.0, DAT — по
официальному JourneyMap 6.0.9 для NeoForge 1.21.1. Используются только сведения
о формате хранения; внешние реализации не включались в проект.

Выбор файлов/папки одного мира и drop, ограниченные чтения в фоне, предпросмотр
с выбором строк, исходными цветами, координатами и видимостью. Для каждого TXT
отдельно назначается измерение; совпадающие имена файлов из разных папок не
объединяются в одну настройку. DAT использует pos/dimensions, их можно переназначить.
Весь перенос без масштабирования координат. Личные метки создаются со значком pin,
категории/внешние права не переносятся. Временные, death и TXT без Y пропускаются
с объяснениями. Дубли внутри партии/хранилища определяются по измерению,
координатам и имени без учёта регистра и крайних пробелов; существующие не заменяются.

Перед атомарной записью делается точная копия прежнего markers.json в backups.
Проверяется актуальность хранилища, запись блокирует конкурирующие изменения меток,
новый снимок публикуется только после успешной записи. Ошибка оставляет старые данные.
Смена мира блокирует сохранение старого предпросмотра; фоновые ответы не переносятся
в новый мир. Повторный импорт не создаёт дубли. Лимиты: 4096 меток/файлов,
2 MiB/файл, 16 MiB/операцию, NBT accounting 16 MiB. Вложенные папки/симлинки
не обходятся. Дополнительно запись MapRepository теперь проверяет тот же лимит
2 MiB, что и чтение: длинные Unicode-имена не создают нечитаемый файл.

MapWaypointImportTest: 13 сценариев (форматы, отдельные измерения TXT, лимиты,
повтор, backup, устаревший снимок/ошибка копии, слишком большой Unicode JSON).
MapImportHarness: настоящий клиент, чтение TXT/DAT с диска, игровой NBT parser,
выбор измерения, прокрутка, отмена, пакетная запись, повторное открытие/дубли,
геометрия GUI1/2/3 default/light/contrast, остальные темы выборочно. Тестовые
импорты изолированы отдельными UUID мира; рабочие метки пользователя не меняются.
Первый запуск пересёкся с принудительной перекомпиляцией core и завершился
ClassNotFoundException; проверки затем разведены последовательно. Во втором
прогоне не существовала папка PNG; harness создаёт её перед захватом. Прогоны
поведения были успешны, заключительный прогон на окончательном коде записан ниже.

Локальная подготовка: forced core/helper/bootstrap с --refresh-dependencies —
425/7/3 тестов успешно до трёх дополнительных импортных проверок; затем все
изменённые тесты импорта/репозитория повторены. Полный PostgreSQL-набор — 184
теста на отдельной временной БД, успешно. 23 Python-теста инструментов (в окружении
с requirements), actionlint ci.yml/release.yml, checkUiComposition, diff --check
успешны. Первый системный Python не имел jsonschema; повтор в отдельном окружении
с зависимостями workflow успешен. Релизные сетевые действия в тестах замоканы.

Установочный JAR, версия, коммит и публикация не выполнялись. Отдалённый CI
именно текущего дерева не запускался: изменения ещё не являются коммитом.
Не называть выпуск готовым до проверки точного релизного коммита и упаковки.


Заключительный MapImportHarness на финальном коде: RIVET_IMPORT_OK,
515 проверок, 27 кадров, без ошибок. Проверены выбор отдельного измерения TXT,
реальный NBT DAT, отмена без записи, 21 выбранная запись → 19 новых / 2 дубля,
дисковое сохранение, блокировка повторного импорта, скролл и возврат в настройки.
Просмотрены кадры GUI2/default и GUI3/light; контролы не перекрываются.
Окончательные core-проверки: 13 импортных + 9 MapRepository, checkUiComposition
и diff --check успешно. Документация игрока обновлена для текущих TXT/DAT.

Обычный dev-сервер перезапущен на текущих классах, Done подтверждён.
Обычный клиент открыт в настройках карты → «Метки», без fixture;
RIVET_AUTO_MAP_PREVIEW_READY подтверждён, ошибок запуска нет.


### 2026-10-09 — начало плана производительности

Пользователь поручил начать ai/LOCALIZATION_PERFORMANCE_PLAN.md. Добавлена отключённая
по умолчанию подробная диагностика PerformanceMetrics: не более 64 операций,
окно 2048 измерений, p50/p95/p99 nearest rank, неизменяемые снимки.
Существующие агрегаты запросов/БД сохранены. Клиентские таймеры охватывают
сканирование карты, миникарту, выбор/отрисовку её меток и большую карту;
в БД добавлено время успешного получения соединения. Вложенные времена
не складывать, GPU отдельно не измеряется.

PerformanceBaselineHarness выполняет шесть сцен трижды на настоящем клиенте:
0/100/1000/4096 личных меток, поворот с 4096 и большая карта с 4096.
Синтетические метки изолированы временными репозиториями, затем состояние
восстанавливается. Первая пара прогонов содержала ошибку стенда: null вместо
receivedRepository при чтении большой карты. Эти результаты исключены;
стенд исправлен и оба варианта повторены с пустым временным репозиторием.

Первое изменение: Minimap использует стабильный NearestSelection для 64
ближайших вместо полной сортировки. Расстояние Math.hypot вычисляется один
раз на подходящую метку. Сохранены фильтры, лимит и порядок равных расстояний.
Тесты сравнивают с прежней полной сортировкой, включая движение центра,
равные расстояния, фильтры и предел 4096. Подробности итоговых замеров и
оставшиеся ограничения фиксируются в ai/PERFORMANCE_BASELINE.md.

Проверки окончательного кода: принудительно выполнены 437 core-тестов
и 184 PostgreSQL-теста на временной изолированной БД, без ошибок/пропусков.
В core входят 5 проверок стабильного выбора и 4 проверки диагностики.
checkUiComposition и git diff --check успешны; два чистых native-прогона по
18 сцен завершены. Установочный JAR, версия, коммит и публикация не выполнялись.
Отдалённый CI текущего дерева не проверялся.


### 2026-10-09 — оптимизация подписей большой карты

После поручения продолжить отдельно измерены поверхность, подготовка,
метки и проверки пересечений большой карты. На 4096 личных метках линейные
проверки занятых областей занимали в среднем 4,90 мс на кадр. Добавлен
BoxIntersectionIndex с клетками 64 пикселя и ограниченным fallback для
огромных/переполненных прямоугольников. Индекс строится на каждый проход;
принятые подписи добавляются сразу в прежнем порядке. Общие слои используют
тот же индекс; правила и резервирование death-подписей сохранены.

Три native-повтора до/после: p95 большой карты (медиана) 17,725 → 13,156 мс,
меток 11,236 → 6,064 мс; подготовка выросла 0,917 → 1,604 мс и включена в
общий результат. Пересечения 4,899 → 0,060 мс в среднем на кадр.
Полная карта быстрее примерно на 26% в этом сценарии, не обещание общего FPS.
PerformanceBaselineHarness поддерживает WORLD_ONLY (три сцены-повтора).
Подробные метрики по-прежнему отключены по умолчанию.

4 BoxIntersectionIndexTest прошли: эквивалентность линейному предикату,
края/касания/нулевые размеры/отрицательные координаты, огромные значения,
порядок принятия подписей на плотном наборе при пяти смещениях.
MapActivitiesHarness: RIVET_ACTIVITIES_OK, 290 проверок, 144 кадра,
настоящий UI с контролируемыми протокольными данными, включая масштаб/темы,
задачи с/без места, события, слои и навигацию. Это не новый многопользовательский
нагрузочный прогон. Остальные этапы PERFORMANCE_PLAN остаются открытыми.

Заключительный WorldMapHarness: RIVET_MAP_UI_OK, 785 проверок,
GUI 1/2/3, default/light/contrast, компактные окна, настоящая поверхность,
создание/иконки/цвета/категории/перетаскивание/отмена/навигация/удаление меток.
MapActivitiesHarness: 290 проверок и 144 кадра, успешно.
Для текущего изменения выполнены 4 профильных unit-теста, компиляция harness,
checkUiComposition и diff --check. Полные core/PostgreSQL из предыдущего шага
повторно не запускались: транзакции, протоколы и БД здесь не менялись.
Установочный JAR, версия, коммит и публикация не выполнялись; CI не заявляется.


### 2026-10-09 — лишние запросы синхронизации позиций

CommunityMapPositions теперь читает членство только для кандидатов с audience=groups
и mode!=hidden. Пустой список кандидатов не выполняет ни один из двух запросов
подсистемы позиций. Настройки приватности не кешируются, проверка актуальных
прав/членства, дистанции и revision fence сохранена. Добавлены отключённые
по умолчанию метрики map.positions.policies/groups.

MapPositions не опрашивает сервер при скрытом слое PLAYERS, если нет активного
слежения за игроком. Активная навигация продолжает получать позиции, даже если
слой скрыт; смена/отмена цели прекращает ненужный опрос. Размер страницы 64,
координаты, частота активного опроса, TTL 7 секунд и протокол не менялись.

На наборах по умолчанию число запросов именно к политикам/членству на полный
снимок: 10 позиций 2→1, 50 позиций 2→1, 100 позиций 4→2. Исходное число следует
из прежних двух безусловных SQL на страницу, итоговое проверено счётчиками
фактического выполнения на PostgreSQL. Это не сокращение всех SQL мода вдвое.
3 новых PostgreSQL-теста: 10/50/100 кандидатов, пустые кандидаты, hidden/groups
без чтения членства. Весь PostgreSQL-набор выполнен: 187, без ошибок/пропусков.
MapPositionPolicyTest и компиляция harness/checkUiComposition успешны.

MapPositionsHarness: 111 проверок, 9 flows, 27 кадров, настоящий клиент,
реальное согласование функции/чтение defaults, затем контролируемый transport.
Проверены отсутствие запросов при скрытом слое, обновление активной навигации,
атомарные 100 позиций за 2 страницы, без публикации промежуточных данных;
локальный сценарий завершил снимок за 495 мс. Это не 100 подключённых клиентов
и не измерение RTT/байтов реальной многопользовательской сети.

Остаётся: нагрузка 10/50/100 настоящих/явно синтетических получателей,
профиль серверного тика, трафик по размеру пакетов, задержки/потери сети.
При десятках страниц сумма RTT и пауз 300 мс может превысить TTL 7 секунд:
клиент безопасно отбрасывает снимок, но устойчивое получение на очень большом
онлайне требует отдельной переработки пагинации. Это не исправлено данным шагом;
этап 3 целиком не завершён. Обмен исследованной картой не возвращался.
Установочный JAR, версия, коммит и публикация не выполнялись; CI не заявляется.


### 2026-10-09 — атомарные позиции одним запросом

Проблема последовательной пагинации закрыта новым опциональным соглашением
map-position-pages. Старые пары клиент/сервер сохраняют 64 кандидата на страницу.
Новый клиент делает один запрос на снимок до 4096 кандидатов; сервер читает
политики одним набором, проверяет revision fence, актуальные координаты,
расстояние, spectator/invisible/auth и отправляет разрешённые позиции несколькими
пакетами. positionCompact формируется только сервером из согласованных функций;
входной флаг удаляется. Лимит новых запросов 1/сек вместо legacy 250 мс.
Сверх 4096 кандидатов возвращается ошибка, не усечённый снимок.

MapPositionPage: до 384 строк на пакет, словарь измерений и компактный JSON
без отступов, рабочий бюджет 30000 символов и итоговая проверка 32767 символов
кодека. MapPositionBatch: тот же request, последовательные positionPage,
одинаковая revision, positionLast, проверка UUID/координат/дублей и общего лимита.
Никакие части нового снимка не публикуются до последней страницы. Запрос остаётся
pending до её приёма, диагностика отражает полный ответ, а не первый пакет.

TTL остаётся 7 секунд от начала запроса; получение страниц не продлевает его.
Нет продления старых координат ради устранения мерцания. Отзыв видимости/сброс
отменяет корреляцию и накопление, поздние пакеты игнорируются. Потеря пакета,
неверный порядок/ревизия или ответ после TTL безопасно отбрасывают снимок.
При чрезмерно медленной сети данные могут истечь — это преднамеренная граница
свежести, а не гарантия непрерывного отображения при любой задержке.

7 MapPositionBatchTest: 4096 позиций, бюджет при длинных измерениях/именах,
потеря/повтор/порядок/смена ревизии, TTL, legacy и отсутствие переговоров,
некорректные индексы/координаты/дубли, пустой/слишком большой снимок.
Добавлен тест optional feature и PostgreSQL-тест чтения 4096 политик одним
запросом с отказом на 4097. Полные forced core 449 и PostgreSQL 188 прошли
без ошибок/пропусков. checkUiComposition и diff --check успешны.

Нативный тест использует настоящее согласование с сервером и его компактный
ответ; нагрузочная часть затем использует контролируемый transport с настоящим
FeatureState.CODEC encode/decode. Legacy: 100 позиций, два запроса, около 0,5 с.
Новый режим: 4096 позиций, один запрос, 11 пакетов, 308400 байт кодека до
сетевого сжатия, около 1,25 с при задержке 500 мс + 50 мс между пакетами.
Проверены 9 секунд повторных обновлений без исчезновения, отзыв посреди снимка
и отсутствие восстановления от поздних пакетов; интерфейс flows9/frames27.
Это не 4096 сетевых клиентов и не замер общей пропускной способности сервера.

Первый тест бюджета обнаружил, что общий Json.GSON форматирует с отступами.
Исправлено выделенным компактным сериализатором позиции; ограничение пакета
не увеличивалось. Промежуточный вариант более крупных запрашиваемых страниц
отброшен: накопленная RTT могла бы снова превысить срок свежести.

Остаток этапа 3: много получателей, профиль серверного тика/CPU при рассылке,
суммарный трафик и насыщение очередей. Общий обмен исследованной картой не
возвращался. Установочный JAR, версия, коммит, публикация не выполнялись.
Отдалённый CI текущего дерева не проверялся.

Финальный native-прогон после исправления времени RequestSession:
RIVET_POSITIONS_OK checks=181 flows=9 frames=27, delayedBatch и
partialRevocation успешны; 4096 позиций за 1251 мс, 308400 байт кодека.
В журнале был отдельный timeout чтения профиля с sessionserver.mojang.com;
сценарии позиций и проверки завершились успешно. Это не ошибка передачи
снимка и не подтверждение доступности внешнего сервиса скинов.


## Очередь чтения позиций — 2026-10-09

Добавлен opt-in PositionFanoutLoadTest: реальная PostgreSQL и компактная
сериализация, 36 сценариев (10/50/100 получателей, пакетная/равномерная подача,
с ожиданием моделируемого тика 50 мс/без ожидания, три повтора).
Медиана p95 ответа по трём повторам, мс:

| Получателей | Подача | С ожиданием | Без ожидания | Отказов до/после |
|---|---|---:|---:|---:|
| 10 | одновременно | 253.321 | 5.949 | 0/0 |
| 50 | одновременно | 1202.468 | 17.486 | 0/0 |
| 100 | одновременно | 1602.459 | 25.621 | 34/34 |
| 10 | за секунду | 58.208 | 6.127 | 0/0 |
| 50 | за секунду | 272.186 | 1.302 | 0/0 |
| 100 | за секунду | 1451.524 | 1.478 | 0/0 |

При 100 равномерных получателях пик очереди 60 → 1, JSON 680300 байт
на волну. Одновременный всплеск остаётся ограничен 2 исполнителями + 64
местами ожидания; увеличение очереди не выполнялось.

ServerFeatures использует уже захваченный на игровом потоке immutable Actor
для read-only mapPositionPeers, без повторного submit/get из потока чтения.
Проверки сеанса, Actor, поколения хранилища и позиции перед ответом сохранены;
мутации и остальные операции сохраняют предшествующую проверку.

Числа характеризуют синтетическую модель, не ускорение реального сервера:
Minecraft может исполнять задачи раньше следующего тика. Не измерены игровой
CPU/тик, сетевое сжатие, реальные 100 клиентов и конкуренция с другими запросами.
Остаток: сглаживание всплесков с ограниченным временем повторов, профиль
настоящего сервера, суммарный трафик, затем дальнейшие этапы плана.

Проверки этого изменения: :mod:compileJava успешно; принудительный :core:test
449/449; opt-in PostgreSQL-нагрузка 36 сценариев успешно; native
RIVET_POSITIONS_OK checks=182 flows=9 frames=27, delayedBatch/partialRevocation
успешны, 4096 позиций / 11 пакетов / 308400 байт / 1252 мс.
Git diff --check без ошибок. Установочный JAR, версия, коммит и публикация
не выполнялись; удалённый CI не проверялся.


## Распределение опросов и повторов — 2026-10-09

MapPositionPoll распределяет первый запрос по UUID в пределах секунды,
включая повторную загрузку после mapPositionsChanged. Ошибки и timeout:
пауза 1/2/4 секунды + 0–999 мс по UUID и попытке, максимум 4999 мс.
Успех сбрасывает backoff; публичный reset сбрасывает его при смене состояния.
Очистка снимка/навигации и отзыв доступа остаются немедленными.
Нормальный опрос остаётся раз в секунду после успешного ответа; TTL отсчитывается
от отправки. Серверный протокол и предел очереди не менялись.

Нагрузочная модель теперь использует тот же MapPositionPoll.initialDelay
с округлением до 50-мс тиков вместо идеальной равномерной подачи.
36 сценариев завершились. Без повторного server handoff, три повтора:

| Получателей | Медиана p95 ответа, мс | Максимум ожидающих | Отказы |
|---|---:|---:|---:|
| 10 | 6.666 | 1 | 0 |
| 50 | 6.622 | 5 | 0 |
| 100 | 8.687 | 9 | 0 |

Предыдущий одновременный всплеск 100 запросов отклонял 34. Это синтетическая
модель с настоящей SQL/сериализацией, не реальный онлайн 100 клиентов.
Конкуренция других операций и настоящий серверный CPU/тик остаются открытыми.

Проверки: compileJava успешно; принудительный core:test 452/452;
PostgreSQL-нагрузка 36 сценариев; native RIVET_POSITIONS_OK checks=206
flows=9 frames=27, включая boundedRetries, сброс после успеха, начальную
паузу и отсутствие немедленных повторов. Отзыв и просроченные пакеты
проверены; 4096 позиций за 2003 мс с начальной фазой и тестовыми задержками.
Установочный JAR, версия, коммит и публикация не выполнялись. CI не проверялся.
Следующий этап: профиль фактического серверного CPU/тика и конкуренция
запросов разных модулей; затем общий аудит SQL и фоновых операций.


## Настоящая серверная очередь и смешанные чтения — 2026-10-09

Добавлен opt-in ServerLoadHarness: девять фаз, три повтора по 200 тиков
простоя / позиций / смешанных чтений. Пять запросов за тик через настоящие
ServerFeatures.READS, CommunityStore и PostgreSQL. Смешанная фаза: позиции,
главная, объединения, события, объявления; для меню сохранён предварительный
server.submit/get, финальная сериализация выполняется на игровом потоке.
Добавлены выключенные по умолчанию production-таймеры подготовки/заполнения
позиций. Настройки, протокол, количество потоков и ёмкость очередей не менялись.

Результаты: RIVET_SERVER_LOAD_OK phases=9; 6000 тестовых чтений завершены,
0 отказов/ошибок. Пик ожидающих запросов 5 из доступных 64.
Медиана p95 по трём повторам, мс:

| Режим | Тело серверного тика | Подготовка позиций | Заполнение позиций |
|---|---:|---:|---:|
| Простой | 0.792 | 0.036 | 0.034 |
| Позиции | 0.763 | 0.020 | 0.020 |
| Смешанный | 0.772 | 0.024 | 0.028 |

Смешанная фаза: ожидание очереди главная 0.036, объединения 0.064,
события 8.477, объявления 12.894 мс; чтение вместе с предварительным
переходом на игровой поток 12.823/8.409/7.675/7.196 мс соответственно.
Ожидание соединения БД p95 0.004 мс. Явного насыщения общей очереди
или пула в этой конфигурации не обнаружено; расширять их оснований нет.

Ограничения: один настоящий подключённый зритель, остальные запросы
синтетические и обходят сетевой rate limit. Других онлайн-игроков нет,
поэтому списки позиций пустые; эти времена не оценивают плотный радар.
Данные текущего небольшого dev-сервера, не большой тестовый набор.
Ответы сериализуются, но не отправляются; это не замер сети/сжатия.
Тело тика Pre/Post не включает всё выполнение между тиками; время ответа
замерено отдельно. Значения — wall-clock, не CPU-профиль и не доказательство
ускорения. Первая фаза включает прогрев; сравнение медиан не даёт оснований
объявлять сокращение времени тика. Обычный клиент остаётся подключённым.

Проверки: compileJava успешно; PerformanceMetricsTest принудительно выполнен,
серверный сценарий завершился; git diff --check без ошибок. Детальные таймеры
после сценария выключены. Установочный JAR, версия, коммит и публикация
не выполнялись, удалённый CI не проверялся.

Далее: этап SQL на изолированной базе с большим числом задач, событий,
объединений и участников, планы запросов и стоимость списков/поиска.
Полный CPU-профиль, плотный список онлайн-игроков, мутации, скины, сеть
и длительная память остаются отдельными незавершёнными проверками.


## SQL-списки: отбор до восстановления коллекций — 2026-10-09

CommunityQueries фильтровал community_documents: представление восстанавливает
восемь коллекций связей через jsonb_object_agg. Поля body использовались
в фильтрах, сортировке и проверках участия. Перенесён отбор на documents,
проверки нормализованного участия — EXISTS по community_relations.
Материализованный CTE ограничивает восстановление полной записи 11 строками;
внешний ORDER BY повторяет исходный порядок, включая sequence при равных
startsAt. Приватность приглашённых/групповых событий, архив, корзина, owner,
ceiling и параметры курсора сохранены. Изменений схемы/индексов нет.

Изолированная PostgreSQL 16, синтетические 6000 документов (по 2000
объединений/событий/объявлений), 96000 связей (16 на документ), фиксированное
время. Один предварительный запрос + пять измерений на сценарий.
Медианы до/после, мс; контрольные суммы результатов совпали в 8/8 случаях.

| Раздел | Поиск | До | После |
|---|---|---:|---:|
| groups | нет | 2710.612 | 4.861 |
| groups | Needle | 2581.808 | 3.466 |
| events | нет | 2919.000 | 5.062 |
| events | Needle | 2587.202 | 3.708 |
| board | нет | 2534.551 | 4.167 |
| board | Needle | 2404.486 | 3.460 |
| home | нет | 3416.195 | 5.300 |
| home | Needle | 2833.090 | 3.995 |

Это wall-clock SQL-списков на конкретном наборе, не ускорение всего мода
или сетевого интерфейса. Вклад планирования/JIT отдельно не измерялся;
EXPLAIN-планы не записаны. Обычная тестовая база сервера не изменялась.
Большие задачи, поиск по участникам/прочим разделам, записи и конкурентные
мутации остаются для следующего этапа.

Добавлены обязательные PostgreSQL-регрессии: все семь видов участия,
фильтр члена объединения и три порядка пагинации при равных временах событий.
CommunityLargeReadTest — opt-in нагрузочный тест, обычный CI его пропускает.

Итог проверки: основной набор 452/452; PostgreSQL 190 выполнено успешно,
два opt-in нагрузочных теста пропущены в общем прогоне (192 записей JUnit).
Большой SQL-набор отдельно выполнен до и после, 8/8 контрольных сумм
совпали. Общий слой скомпилирован; git diff --check без ошибок. Обычные
dev-сервер и клиент запущены снова. Установочный JAR, версия, коммит
и публикация не выполнялись; удалённый CI не проверялся.


## Чтение списков задач — 2026-10-09

CommunityTasks.workList больше не получает из PostgreSQL комментарии,
историю, склады, зависимости и резервы только ради последующего удаления
в Java. Материализованная страница ограничена 21 записью; сокращение JSON
выполняется после отбора, внешний ORDER BY id сохраняет порядок курсора.
Фильтры и условия доступа не менялись. Полная карточка и данные в БД
сохраняются. Новых индексов и миграций в окончательном изменении нет.

Opt-in TaskLargeReadTest: изолированная PostgreSQL 16, 6000 задач,
1000 участников группы, 40 комментариев + 20 записей истории на задачу.
Предварительный запрос и семь измерений. Медианы до/после, мс:

| Список | Поиск | До | После |
|---|---|---:|---:|
| Личный | нет | 107.154 | 106.063 |
| Личный | Needle | 110.028 | 101.785 |
| Личный | CODE0000000059 | 109.812 | 98.738 |
| Групповой | нет | 9.256 | 6.718 |
| Групповой | Needle | 112.907 | 103.996 |
| Групповой | CODE0000000059 | 110.504 | 101.189 |

Все 6 контрольных сумм результатов совпали с исходными. JSON первой
тестовой задачи между БД и сервером: 26908 → 921 байт (около −96.6%).
Это не размер пакета клиенту: список и раньше удалял подробные поля
перед отправкой. Обычный личный список практически не ускорился;
групповой ускорился примерно на 27%. Общий поиск остаётся около 100 мс.
Числа не являются ускорением всего мода или порогом CI.

Добавлена обязательная регрессия: список не содержит подробных коллекций,
сохраняет счётчики этапов, а последующее открытие карточки возвращает
неизменённые комментарии/историю/этапы. Нагрузочный тест opt-in.
Следующий шаг — EXPLAIN для поиска задач и оставшейся стоимости личного
списка; детали задач, зависимости, склады, записи и длительная память
ещё не завершены.

Проверки финального варианта: основной набор 452/452; PostgreSQL
191 выполнено успешно, три opt-in нагрузки пропущены (194 записей JUnit).
TaskLargeReadTest отдельно прошёл, результаты 6/6 совпали с baseline.
Компиляция успешна, git diff --check без ошибок. Обычные dev-сервер
и клиент запущены вновь. Установочный JAR, версия, коммит и публикация
не выполнялись; удалённый CI не проверялся.


## Поиск задач: компактные вычисляемые поля — 2026-10-09

Снят EXPLAIN ANALYZE BUFFERS настоящего LIST_SQL с теми же параметрами.
До изменения Bitmap Heap Scan проверял 3000 задач области, оставлял 2000,
далее Nested Loop искал коды 2000 раз; SQL Execution Time около 93–98 мс.
Статус/архив/поиск читались из body с большими комментариями и историей.
JIT в этих планах отсутствовал.

Миграция 007 добавляет STORED generated task_status/task_archived/task_search.
Значения автоматически вычисляются из body, поэтому save, архивация, прямые
UPDATE и миграция старых строк не требуют синхронизации на стороне Java.
Поиск использует компактные поля; объединение с кодом и lower/strpos
сохраняет регистр/буквальные символы/границы полей как прежде.
Права доступа, курсор и выходной JSON не менялись. LIST_SQL выделен в одну
константу, чтобы тест снимал план production-запроса без отдельной копии.

Тот же набор 6000 задач/1000 участников/40 комментариев/20 записей истории,
предварительный запрос + семь замеров. Медианы store.request, мс:

| Список | Поиск | До | После |
|---|---|---:|---:|
| Личный | нет | 107.507 | 3.920 |
| Личный | Needle | 99.158 | 13.834 |
| Личный | CODE0000000059 | 96.111 | 13.316 |
| Групповой | нет | 6.339 | 6.558 |
| Групповой | Needle | 104.105 | 17.254 |
| Групповой | CODE0000000059 | 102.169 | 15.652 |

6/6 контрольных сумм совпали. EXPLAIN Execution Time после: 0.34–0.38 мс
без поиска, 8.85–10.84 мс с поиском. EXPLAIN — отдельное выражение и может
иметь другой план, чем повторно используемый JDBC statement (особенно
групповой список без поиска); пользовательские показатели выше берутся
из полного store.request. Это wall-clock на конкретной тестовой базе.

Новые регрессии: обновление с v6 с сохранением JSON/ревизии, повторное
применение миграции, изменение generated-полей после прямого UPDATE;
поиск кириллицы, буквальных %/_, границы названия/описания и кода,
редактирование названия и приватность чужих задач. Fixture миграции v5
обновлён для последовательного прохождения шагов 6 и 7.

Компромисс: дополнительное место для короткого поискового текста/статусов
и вычисление при записи. Миграция обрабатывает таблицу на старте;
время на производственной базе ещё не измерялось. Следующие направления:
стоимость полной карточки/зависимостей/складов, запись и конкурентные
обновления; это не завершение общего плана оптимизации.

Финальные проверки: основной набор 452/452; PostgreSQL 193/193 выполненных
(196 записей JUnit, 3 opt-in нагрузки пропущены). Первые два сбоя полного
прогона были устаревшими ожиданиями версии 6 и удалением предпоследнего
шага в тесте отката; fixtures обновлены на последнюю миграцию 7, полный
прогон повторён успешно без ослабления проверок отката/целостности.
Ресурсы принудительно сгенерированы, 007.sql в build совпадает с исходником.
Компиляция и git diff --check успешны. Обычный dev-сервер запустился
с автоматической миграцией, клиент подключён и карта открыта.
Установочный JAR, версия, коммит и публикация не выполнялись; CI не проверялся.


## Карточки задач: зависимости и пустые требования ресурсов — 2026-10-09

TaskWorkflow.dependencies(t) заменяет до 20 запросов (get+code для каждой
из 10 зависимостей) одним SELECT с LEFT JOIN кодов. Читаются только title,
status, scope и code; полные JSON зависимых задач не разбираются.
Окончательная выдача идёт в исходном порядке; дубликаты сохранены,
удалённые зависимости остаются блокирующими, чужой scope пропускается.
Пустой список не читает БД. Опциональный task.dependencies.query считает
запросы и время, по умолчанию не читает часы.
TaskWorkflow.stock пропускает чтение общего пула при пустом wanted,
поскольку результат и раньше был пустым. Привязки собственной задачи
и reservedElsewhere не менялись.

Изолированный TaskDetailLoadTest: 11 задач с 40 комментариями/20 записями
истории, 704 складские привязки, 0/1/10 зависимостей, с ресурсами/без.
Предварительный запрос + девять измерений полного workGet. Медианы, мс:

| Зависимостей | Ресурсы | До | После |
|---|---|---:|---:|
| 0 | нет | 6.455 | 3.153 |
| 0 | есть | 4.777 | 5.475 |
| 1 | нет | 5.283 | 2.949 |
| 1 | есть | 4.555 | 4.534 |
| 10 | нет | 10.856 | 2.343 |
| 10 | есть | 10.230 | 4.360 |

Все 6 контрольных сумм совпали. Для 10 зависимостей улучшение заметно;
сценарий 0 зависимостей + ресурсы не оптимизирован и слегка медленнее
в этом прогоне, ускорение для него не заявляется. Время зависит от
окружения, это не оценка всего мода и не абсолютный порог CI.
Первый исходный запуск нагрузочного fixture остановился из-за отсутствия
записи пользователя; fixture исправлен через store.seen, baseline
повторён на исходной реализации до сравнения.

Две обязательные PostgreSQL-регрессии проверяют один запрос вместо
поэлементных чтений, ноль запросов при пустом списке, порядок/дубликаты,
отсутствующий код, удалённую задачу и приватность личных/групповых данных.
Следующее направление: обход цепочек при изменении зависимостей,
проверки прав/повторные чтения группы и операции со складскими резервами.
Новые схемы, настройки или протоколы на этом шаге не добавлялись.

Проверки финального варианта: основной набор 452/452; PostgreSQL
195/195 выполненных, четыре opt-in нагрузки пропущены (199 JUnit).
TaskDetailLoadTest отдельно успешно выполнен до/после, 6/6 ответов
совпали. Компиляция и git diff --check успешны. Обычные dev-сервер
и клиент запущены вновь. Установочный JAR, версия, коммит и публикация
не выполнялись; удалённый CI не проверялся.


## Изменение графа зависимостей и права групп — 2026-10-09

TaskWorkflow.dependencies(t,codes) использует локальный nodes-map только
на время операции. Каждый узел читается один раз, из БД получаются только
dependencies/group_id/owner, без полной карточки. Каждый корень сохраняет
собственный seen и предел 200; проверка цикла выполняется перед seen/cache.
При 10 корнях верхняя граница карты не более 2000 узлов; она не глобальная
и не сохраняется между операциями. Прежняя блокировка task-workflow остаётся
на вызывающем пути записи. task.graph.query даёт опциональный счётчик чтений.

CommunityTasks.access вместо store.get (все коллекции участников) читает
status/owner и одну роль LEFT JOIN. Кеширования прав нет. Сохранены отличия
read/manage: владелец без членства может manage, но не read; admin не
обходит закрытое состояние группы и приватность чужой личной задачи.

TaskGraphLoadTest на отдельной PostgreSQL 16: 10 корней + общая цепочка
100 узлов, дополнительное непрочитанное текстовое поле в JSON; группа
из 1000 участников. Предварительный запрос + девять измерений в транзакции.
Медианы: проверка графа 297.957 → 28.375 мс; пара read/manage прав
3.719 → 0.917 мс. Это конкретный синтетический набор, не скорость всей
операции сохранения с сетью/журналом и не время всего серверного тика.

Три обязательных PostgreSQL-теста: 110 чтений узлов общей цепочки вместо
1020 повторных чтений; прежний порядок корней; предел 200 допускается,
201 отклоняется; последующие изменения/циклы/удаление заново проверяются;
роли участник/assistant/owner/admin, снятие членства и закрытие группы
немедленно влияют на доступ. Новый opt-in benchmark обычный CI пропускает.

Следующий участок: складские резервы и повторное чтение подтверждённых
остатков, затем конкурирующие записи и жизненный цикл ресурсов. Новых
миграций, настроек и изменений протокола на этом шаге нет.

Проверки: основной набор 452/452, PostgreSQL 198/198 выполненных
(203 записи JUnit, пять opt-in нагрузок пропущены). Benchmark графа
и прав отдельно завершился до/после. Компиляция и git diff --check
успешны. Обычные dev-сервер и клиент запущены снова. Установочный JAR,
версия, коммит и публикация не выполнялись; удалённый CI не проверялся.


## Складские резервы — 2026-10-09

При workReserve результат чтения confirmedPool/reservedElsewhere передаётся
в enrich явным ReservationSnapshot только внутри текущей транзакции.
Блокировки task-workflow и task-stock-quota удерживаются до завершения;
снимок не хранится в сервисе и не переносится между запросами. Release
читает актуальные данные один раз обычным путём. Фильтр завершённых задач
использует уже существующее generated-поле task_status (миграция 007).
Новых миграций, настроек, протоколов и изменений прав нет.

Два обязательных PostgreSQL-теста: опциональные счётчики подтверждают
по одному чтению пула и чужих резервов вместо двух каждого на резервирование;
после изменения склада release видит новый остаток. Два параллельных
запроса разных задач одного владельца на 7 предметов каждый из остатка 10:
один успех, один отказ; суммарный резерв 7, история и revision проигравшей
операции неизменны. Это проверка атомарности, не нагрузочный профиль разных
владельцев/модулей или фактического извлечения предметов из Minecraft.

Проверки: компиляция, основной набор 452/452, PostgreSQL 200/200 выполненных
(205 записей JUnit, пять opt-in нагрузок пропущены), git diff --check.
Следующее: конкурирующие записи разных владельцев/модулей и жизненный цикл
очередей/ресурсов. Полный план оптимизации остаётся открытым.
Установочный JAR, версия, коммит и публикация не выполнялись; CI не проверялся.

Обычные dev-сервер и клиент запущены вновь; автоматическое открытие карты подтверждено.


## Завершение фоновых задач — 2026-10-10

ModuleWorkers ранее после таймаута вызывал shutdownNow без повторного
ожидания. Следующий этап остановки мог закрыть PostgreSQL, пока поток
ещё откатывал транзакцию. Новый core WorkerShutdown: shutdown всех пулов,
общий срок 5 секунд для штатного завершения, shutdownNow оставшихся,
общий срок ещё 5 секунд для очистки. Срок каждой фазы общий для пулов
одного модуля, не умножается на число пулов. Прерывание вызывающего потока
восстанавливается после ограниченного ожидания; задачи, игнорирующие
прерывание, приводят к false и предупреждению. Это не гарантия завершения
непрерываемого стороннего кода. Очереди Auth, скинов и меню используют helper.

Четыре core-теста: штатная очередь/отказ новых задач; остановка ждёт
явного освобождения cleanup-latch; уже прерванный вызывающий поток;
ограниченный выход при игнорировании прерывания. PostgreSQL-тест прерывает
реальную транзакцию записи уведомления и outbox: оба откатываются до
возврата stop, ThreadLocal очищен, соединение возвращено, дальнейшая
community-транзакция доступна. Пока запись заблокирована, независимая
транзакция также проходит. Проверка не заменяет нагрузку разных владельцев
и модулей или аудит всех callback при переподключении.

Следующее: отдельные сеансовые очереди Auth и callback старого подключения,
конкурирующие операции разных владельцев/модулей. Общий план не завершён.

Проверки: компиляция успешна; core 456/456, PostgreSQL 201/201 выполненных (206 JUnit, пять opt-in пропущены); git diff --check. Установочный JAR, версия, коммит и публикация не выполнялись. Удалённый CI не проверялся.

Обычные dev-сервер и клиент восстановлены, открытие карты подтверждено.


## Закрытие очередей Auth — 2026-10-10

SerialExecutor получил close(cleanup): атомарно отклоняет новые задания,
освобождает ссылки на ожидающие, выполняет cleanup ровно один раз после
активного задания. Если worker ещё не начал drain, cleanup выполняется
сразу: закрытие не зависит от наличия места или работающего пула.
Работающее задание не откатывается автоматически и может закончить свою
транзакцию. При fatal Error очередь закрывается и не остаётся зависшей.

AuthServer закрывает отключённый сеанс напрямую, вместо постановки close
в переполненную очередь. TLS очищается после активного задания. Проверка
актуальности включает closed, экземпляр сервера, текущий сеанс в SESSIONS
и состояние соединения. Она применяется к входящим запросам, TLS upgrade,
ответу проверки Minecraft, завершению входа и отложенному disconnect.
Замена сеанса закрывает предыдущий. close отменяет official request и его
таймер; запуск проверки и close согласованы монитором сеанса. Timeout
захватывает конкретный Future, не изменяемое поле следующей проверки.

Четыре новых core-регрессии: закрытие полной очереди до старта worker,
закрытие при активном задании/отбрасывание позднего callback, cleanup при
отказе общего пула, self-close с RuntimeException. Существующие проверки
порядка и независимых сеансов остаются. Это не полный end-to-end вход через
официальный аккаунт и не испытание всех административных Auth-команд.
Следующие участки: оставшиеся административные callback Auth, фоновые
скины и конкурирующие записи разных владельцев/модулей.

Проверки: компиляция, core 460/460, PostgreSQL 201/201 выполненных (206 JUnit, пять opt-in пропущены), git diff --check. Установочный JAR, версия, коммит и публикация не выполнялись; удалённый CI не проверялся.

Обычные dev-сервер и клиент восстановлены; открытие карты подтверждено.


## Фоновое обновление скинов — 2026-10-10

ServerSkins объединяет ожидающие/выполняющиеся fallback-проверки по UUID
через CoalescingExecutor на один запуск модуля. Повтор не занимает место
в очереди. Очередь по-прежнему ограничена 64 заданиями и одним worker.
AbortPolicy вместо DiscardPolicy позволяет освободить ключ при отказе;
отказ необязательного обновления не отменяет чтение сохранённой библиотеки.
close запрещает новые задания и пропускает ожидающие. Активное задание
может завершаться; повторная проверка store после сети отбрасывает результат
старого запуска перед записью (уже начатая запись не откатывается автоматически).

Задания захватывают store/settings/fallback/database/attempts текущего запуска,
не читают глобальную новую БД или сброшенные настройки внутри worker.
Attempts-map пересоздаётся на start. Личные ответы/ошибки/blob сверяют
исходное подключение; общая рассылка уже сохранённого изменения внешности
по-прежнему доступна всем текущим поддерживаемым клиентам.

Четыре core-теста: 101 запрос одного ключа создаёт одно задание; другой ключ
независим; после завершения можно обновить снова; отказ/ошибка освобождают
ключ; close пропускает ожидающее, новый экземпляр принимает тот же UUID;
активное задание одного ключа не блокирует другой. Это проверка количества
работ и жизненного цикла, не замер реальной сети Mojang и не UI-прогон.
Следующее: административные callback Auth и порядок параллельных изменений
скинов; конкурирующие операции разных владельцев/модулей остаются открытыми.

Проверки: компиляция, core 464/464, PostgreSQL 201/201 выполненных (206 JUnit, пять opt-in пропущены), git diff --check. Реальные обращения к Mojang не проверялись. Установочный JAR, версия, коммит и публикация не выполнялись; удалённый CI не проверялся.

Обычные dev-сервер и клиент восстановлены, открытие карты подтверждено.


## Порядок операций скинов и административные callback Auth — 2026-10-10

ServerSkins отправляет запросы через KeyedSerialExecutor<UUID> над прежним
пулом двух worker. Один владелец обрабатывается в порядке постановки,
разные владельцы могут выполняться параллельно. Общий лимит 128 включает
активные и ожидающие операции всех владельцев, не 128 на игрока. Lane
удаляется после опустошения; rejection освобождает ключ/ёмкость; close
отбрасывает ожидающее и не отменяет уже начатую транзакцию. Это исправляет
перестановку последовательных model/select/rename и чтений двух worker;
блокировка skins в PostgreSQL продолжает защищать общие ссылки/очистку.

Четыре core-теста: порядок при заблокированном первом запросе, независимый
владелец, общий предел/повторное использование, отказ/ошибка, закрытие
при активном задании. PostgreSQL-тест блокирует первый model перед записью,
ставит следующий model и rename другого владельца: независимый rename
проходит сразу, итоговая модель соответствует второму запросу, active-id
сохраняется. Это не полный профиль производительности SQL-блокировки skins.

Приглашения Auth из UI и команды игрока идут через очередь исходного сеанса.
Доставка заново проверяет актуальность подключения и mayReset на игровом
потоке; ошибки также не отправляются от старого сеанса. Консольные команды
захватывают AuthStore/индекс/server/pool одного запуска; работа и доставка
ответа проверяют их актуальность. Политика прав и сроки приглашений прежние.
Компиляция и существующие Auth-тесты не заменяют ручную проверку команды
со снятием прав во время ожидания или полный вход официального аккаунта.

Следующие участки: отмена внешних сетевых цепочек при остановке, ограничение
фонового обслуживания и конкуренция разных модулей. Полный план открыт.

Проверки: компиляция, core 468/468, PostgreSQL 202/202 выполненных (207 JUnit, пять opt-in пропущены), git diff --check. Установочный JAR, версия, коммит и публикация не выполнялись; удалённый CI не проверялся.

Обычные dev-сервер и клиент восстановлены, открытие карты подтверждено.


## Территории при открытии меню и отмена Mojang lookup — 2026-10-10

Причина пользовательского исчезновения территорий: subscribe при новом
разделе посылал общий changed без section; ServerMenuClient трактовал его
как изменение всех картографических данных и вызывал MapGroupClient.reset.
Общий список для большой карты/миникарты очищался до следующего groupMap.

Подписка теперь посылает changed с section/id и resync=true через
MenuChangeHint.subscription. Меню по-прежнему обновляется, но это сообщение
не сбрасывает территории/activities. Настоящие changed без resync остаются
инвалидацией; home/events больше не очищают территории. groups и старый
общий changed сохраняют немедленный reset, чтобы не удерживать данные при
отзыве доступа. TTL 12 секунд, фильтрация сервера, сброс при смене соединения
или отключении модуля не изменены. На старом сервере без resync новое
поведение не отличает старое общее уведомление от изменения прав.

Три core-регрессии проверяют различие resync/данных и области инвалидирования.
MapTerritoriesHarness расширен открытием home/groups/events и возвратом на
карту с сохранением того же полигона; реальный changed(groups) должен его
немедленно скрыть, последующая загрузка возвращает. Это общий источник
территорий для большой карты и миникарты.

Продолжение этапа жизненного цикла: MojangSkins проверяет флаг прерывания
перед началом lookup, каждым следующим HTTP-запросом и после чтения ответа.
Прерванная работа не продолжает цепочку profile -> texture; флаг сохраняется.
Уже заблокированное HTTP-чтение всё ещё ограничено существующим таймаутом,
это не гарантия мгновенной отмены сетевого вызова. Два core-теста без сети:
прерванные nickname/UUID lookup и прежний результат некорректного nickname.

Игровая регрессия MapTerritoriesHarness прошла: 429 проверок, 180 кадров; полный flow GUI 1/2/3, матрица тем и проверки отключённых модулей. Успешный маркер наблюдался. PNG-съёмка не включалась.

Финальная компиляция и core 473/473 прошли; git diff --check. PostgreSQL повторно не запускалась: SQL/хранилища не менялись. Реальная сеть Mojang и удалённый CI не проверялись. Установочный JAR, версия, коммит и публикация не выполнялись. Следующее — фоновое обслуживание и оставшаяся конкуренция модулей.

Обычные dev-сервер и клиент восстановлены; открытие карты подтверждено.


## Корневая причина самосброса территорий при просмотре объединения — 2026-10-10

Предыдущее исправление resync было неполным: оно убрало очистку от самой
подписки, но не от последующих чтений карты. В ServerFeatures после
community-ответа существовал отдельный старый deny-list операций чтения,
где отсутствовали groupMap и mapActivities. Поэтому успешное чтение
рассылало invalidate(section=groups), включая самого подписанного игрока.
Клиент правильно очищал защищённый снимок, запускал новое чтение, и цикл
повторялся. Чтение одного игрока могло также задеть других подписчиков.

Реальное воспроизведение до исправления: MapGroupReadWireHarness открыл
существующую карточку объединения без каких-либо записей. Первый ответ
с территориями вызвал один changed(groups), тест упал с явной причиной.
Это не previewTransport и не подставленный серверный ответ. Прошлый
MapTerritoriesHarness проверял клиентскую часть и не ловил серверную рассылку.

ServerFeatures теперь использует MenuRequests.invalidatesCommunity,
выведенный из единого MenuCommands.Effect.WRITE. Дублирующий список удалён.
Позиции сохраняют отдельный путь; groupMap/mapActivities и остальные READ
не рассылают изменение. Настоящие записи объединений/территорий по-прежнему
инвалидируют подписчиков. TTL, немедленный сброс при отзыве доступа и
серверная фильтрация не ослаблены.

Два core-теста проверяют read-операции карты/карточек, записи и отдельный
путь позиций. Сетевой harness читает реальные данные, дважды открывает
карточку и возвращается на карту, проверяет отсутствие ложных changed,
наличие свежего снимка и сохранность исходных ID территорий. Данные сервера
не создаются/не редактируются; если доступной карточки нет, используется
список объединений, что явно отмечается в маркере.

Проверки финального варианта: core 475/475, компиляция production/UI harness и git diff --check. Реальная сеть после исправления: card=true, 1 территория, 7 ответов, 0 ложных инвалидаций, 4 фазы (карточка/карта дважды); успешный маркер наблюдался. До исправления — 1 ответ/1 ложная инвалидация и отказ теста. PostgreSQL повторно не запускалась: SQL и схема не менялись. Установочный JAR, версия, коммит и публикация не выполнялись; удалённый CI не проверялся.

Обычный dev-клиент восстановлен на исправленном сервере, открытие карты подтверждено.

## Общий аудит по запросу владельца — 2026-10-10

Владелец разрешил системно проверять и исправлять весь проект; о спорных
продуктовых решениях спрашивать. План и границы проверки — PROJECT_AUDIT.md.
Первый проход исправил устаревшие callback складов, ограниченное ожидание
остановки statistics, cleanup/старые ответы ServerPack и отмену PackPublisher
перед подготовкой/активацией. Core 477/477, mod 2/2, компиляция и проверки
границ модулей прошли. Полный аудит не завершён; SQL/PG и удалённый CI в этом
проходе не проверялись, установочный JAR и выпуск не выполнялись.

## Изменение порядка работы: сначала аудит — 2026-10-10

Владелец остановил дальнейшие исправления до составления аудита. Следовать
новому порядку: сначала PROJECT_AUDIT.md с доказательствами/рисками и обсуждение,
затем реализация. Предыдущие правки сохранены. Статический проход добавил девять
находок A01–A09, матрицу покрытия всего мода и порядок проверок; новые исправления,
тесты и игровые прогоны в этом проходе не выполнялись. Не выдавать матрицу будущих
проверок за законченный аудит каждой подсистемы.

## Исправления аудита и история чата — 2026-10-10

Владелец одобрил реализацию аудита и поведение Longer Chat History / Server
Chat Sync внутри чата Rivet. Подтвердил все три канала: global всем, группы
текущим участникам (включая до вступления), local только прежним получателям.
Сервер history=100/historyDays=7, клиент 65536; миграция 008 и согласованная
chat-history. Новые настройки документированы/добавляются автоматически.
Повторный вход общего/local проверен настоящей сетью; права групп — PostgreSQL.
Реализация и ограничения подробно в PROJECT_AUDIT.md, раздел выполнения.
A01–A09 получили изменения; матрица остального проекта не объявлена закрытой.
Файловое восстановление карты проверено отказами во временном репозитории;
настройки 259 проверок/162 состояния; core 480, mod 3, PostgreSQL 206 выполненных
и 5 opt-in пропущено. UI/сетевая история чата прошли. Выпуска/JAR/коммита нет,
удалённый CI не проверен. Применённые миграции 001–007 не изменялись.
Сетевой сценарий территорий после новых пакетных снимков: 7 ответов, 0 ложных
инвалидаций, 4 фазы, реальная карточка. Архив общего/local чата также проверен
после настоящего перезапуска dev-сервера. Проверки не заменяют полный нагрузочный
профиль или удалённый CI.

## Полный проход аудита и исправлений — 2026-10-10

По поручению владельца завершён проход по всем областям матрицы
PROJECT_AUDIT.md. Дополнительные находки A10–A18 исправлены: lock/bootstrap,
владение ошибками меню, повторная проверка административных прав, повреждённые
строки replay чата, lifecycle флагов, немедленное уменьшение истории,
устаревшие ожидания UI тестов, повтор чтения видимости и границы sidebar.
704 Java/SQL теста и 23 Python прошли; 5 opt-in нагрузочных SQL пропущено.
Игровые наборы: AuditFixes 12/360, native 54, настройки 162/259, adaptive 198,
TAB 126; реальные сетевые ошибки/восстановление, чат и история прошли.
Карточка объединения: 7 ответов, 0 ложных инвалидаций, 4 фазы.
Миграции 001–008 сохранены; версия/коммит/публикация/установочный JAR не делались.
Удалённый CI, свежая загрузка Java-зависимостей и максимальный онлайн не
проверены: не объявлять этот результат релизной сертификацией. Подробные
границы и подтверждения — PROJECT_AUDIT.md.

## Дополнительные исправления A19–A22 — 2026-10-10

Владелец одобрил все четыре находки повторного просмотра и лимит истории 1000
по умолчанию. Исправлены окно отзыва доступа при replay групп (shared-lock
документа до bounded доставки), утрата идентификатора отправки обращения/ответа
(сохранение pending до сети), очередь TextDraft (объединение последних записей),
периодическая очистка истории независимо от включённого чата. Существующий
выбор лимита сохранён. 699 Java/SQL проверок успешны, 5 opt-in пропущены;
AuditFixes/native/settings/chat compatibility/history wire прошли. Детали и
границы — PROJECT_AUDIT.md. Версия, миграции, публикация и коммиты не менялись.


## Локализация, измеренная производительность и размер — 2026-10-10

Владелец поручил полную RU/EN локализацию и продолжение оптимизации всего
мода; целевой обычный онлайн уточнён: до 100 игроков. Новый подробный отчёт
и ограничения — LOCALIZATION_PERFORMANCE_PLAN.md. Клиентские каталоги
по 2682 ключа, core 747; язык сохраняется между фоновыми операциями и
при отправке получателю. Новые notice/подписи истории имеют явные I18n-
метаданные, пользовательские строки и старые записи не угадываются.
Черновики сохраняют стабильные прежние ключи; язык можно менять на ходу.

Измеренные оптимизации: выбор 4096 меток миникарты −84% p95; клиентский
replay 1000 сообщений −69% медианы суммарной работы. До 16 получателей
серверной истории за один переход на игровой поток: модель 100 получателей
с настоящей PostgreSQL, p95 первой порции 4755 → 302–305 мс, права отдельных
аудиторий/блокировки при отзыве членства сохранены. Смешанный SQL-прогон:
100 владельцев, 24000 операций, без ошибок/нарушений доступа. Позиции:
распределённые запросы 100 получателей без отказов; одновременный burst
сохраняет ограничение очереди и backpressure, а не гарантированную доставку.

Standalone helper 953643 → 302005 байт; это не размер всего устанавливаемого
мода. Криптография и PostgreSQL-зависимости не урезались. Подтверждены 500
core, 9 helper, 3 mod, 212 SQL проверок (4 opt-in пропущены), проверки
каталогов. Игровые карта 785 проверок, RU/EN 324, native 54, adaptive 198;
финальная реальная сеть истории/совместимости чата/EN-RU-EN прошла.
Многочасовой прогон, 100 живых подключений, внешняя Mojang-авторизация,
свежая загрузка зависимостей и удалённый CI не подтверждены. Установочный
JAR/версия/коммит/публикация не выполнялись. Восстановление обычного preview
выполняется после тестовых прогонов, без установки JAR.

Обычный dev-сервер и клиент восстановлены на актуальных классах; открытие
карты подтверждено маркером готовности. Клиент оставлен для проверки владельцем.


## Simple Voice Chat — 2026-10-10

По поручению владельца добавлен адаптер Simple Voice Chat, аналогичный
Plasmo Voice: диагностика клиента и голосовой мут через существующие
защищённые голосования. Автообнаружение voicechat; API 2.5.0 compileOnly,
не включается в Rivet. Официальный ForgeVoicechatPlugin обнаруживается
Simple Voice Chat; внешний API изолирован в compat/voice/SimpleVoicePlugin.
UDP/mic/disabled берутся из Client API, отсутствие данных не угадывается.

MicrophonePacketEvent блокируется по UUID до маршрутизации речи/шёпота/групп.
VoiceMutes хранит сроки атомарно в rivet/voicechat-mutes.json папки сервера;
аудиопоток читает только immutable snapshot. Истёкшие записи не действуют,
чистятся при записи; память ограничена 4096 записями. Повреждённый файл не
перезаписывается, ошибка записи не публикует неподтверждённое наказание.
Другие плагины не разблокируются. При двух установленных голосовых модах
требуется доступность обоих, проверка существующего мута до применения;
мут применяется к обоим. Ошибка второго API не считается успехом, уже
применённое ограничение первого автоматически не снимается.

Свежие проверки: core 505, mod 6, три проверки RU/EN каталогов, модульные
границы и компиляция UI harness успешны; все 17 Gradle-задач выполнены
принудительно. На реальном NeoForge-сервере два сценария: без voicechat
(его API действительно отсутствует), с официальным NeoForge 1.21.1-2.6.22
(плагин rivet зарегистрирован, UDP server started, moderation available).
Оба завершились маркерами успеха и штатной остановкой. Голос с микрофона
между двумя живыми клиентами не проверялся; обработчик пакетов проверен
через API события в автоматических тестах. Тестовый внешний JAR удалён
из временного сервера. Установочный Rivet JAR/версия/коммит/публикация не
выполнялись. PostgreSQL не менялась; удалённый CI не запускался.

Документация API: https://modrepo.de/minecraft/voicechat/api/getting_started
Исходники API: https://github.com/henkelmax/simple-voice-chat/tree/1.21.1/api


## Подготовка релиза 2.1.0 — 2026-10-10

По поручению владельца подготовлены MINOR 2.1.0, описание выпуска и локальный
релизный комплект dist/2.1.0 с SHA-256 и core.json. Полный JAR 14464046 байт.
Детали приёмки и границы — RELEASE_2.1.0.md. Build с обновлением зависимостей
и принудительными проверками прошёл; Java 525, Python 26, PostgreSQL 18 —
209 выполненных проверок (7 opt-in пропущено). Упакованные default/disabled/
base серверы и обнаружение Simple Voice Chat внутри поставки прошли.
Тег/публикация/push/новый коммит не выполнялись; CI для будущего релизного
коммита ещё не подтверждён. Не объявлять локальную проверку готовым remote CI.

Запущен полный клиентский JAR в отдельном чистом профиле: старые настройки
не переносились, прежний профиль сохранён. В список добавлен локальный
сервер 2.1.0. Подтверждены загрузка ресурсов и подключение клиента к нему.

## Последние исправления перед выпуском 2.1.0

Компоненты сборки: описание под именем, группировка файлов сохранена.
Миникарта по умолчанию 112; компас квадратной формы следует краям.
Выбор профиля оформления исправлен: актуальный пресет/ручные настройки,
первый пункт больше не игнорируется. 526 Java тестов, 63 игровых сценария
(334 проверки) и полный build/verifyBundle прошли. Комплект dist/2.1.0
обновлён без публикации. Подробности — RELEASE_2.1.0.md.

Стрелка миникарты отделена по размеру от стрелки большой карты: вместо
фиксированных 24 единиц используется размер области HUD / 9, с пределами
8–16 (12 при стандартных 112). Размер большой карты прежний. Нативный
MinimapArrowHarness отрисовал 45 состояний: размеры 80/112/192, GUI 1/2/3,
светлая/контрастная темы и остальные выборочно; PNG стандартного размера
проверен. MinimapHarness явно выбирает круг для проверки круглой маски,
поскольку сброс теперь правильно возвращает квадрат по умолчанию.

Следующая корректировка HUD: буквы компаса масштабируются по side/160
(пределы 0.5–1.25), отступ периметра учитывает размер глифа. Стрелка полной
карты уменьшена с 24 до 16. MinimapArrowHarness дополнен отрисовкой стрелки
полной карты; 45 состояний успешны, PNG стандартного HUD просмотрен.

## Очистка служебных материалов

Удалены завершённые промежуточные планы аудита/геометрии/карты, прежний план
производительности и одноразовые реестры переноса локализации. Актуальные
решения, отчёты аудита/измерений, инструкции и приёмка 2.1.0 сохранены.
Временные скрипты правок, старые логи и снимки очищены; исходники регрессионных
тестов, рабочие профили и запуск локального сервера сохранены.

## CI: локаль нативных UI-проверок — 2026-10-10

В предоставленном Linux-логе падал UiFlowHarness: он искал русское «Найти»
при en_us. Поиск виджета возвращал пустой набор, а сообщение ошибочно
сообщало о заблокированной кнопке. Селекторы Find/Save/New task/Overview
в UiFlowHarness и NativeUiHarness переведены на ключи Client.text.
Проверка Save больше не пропускает нажатие молча при отсутствии кнопки.
CI запускает нативную навигацию отдельно на ru_ru и en_us, проверяет
маркер каждого прогона и сохраняет отдельные логи. Сообщения flite/OpenAL
в исходном логе предшествуют успешной инициализации сценария и не были
причиной остановки. Production-код и JAR не менялись.
Локальные нативные прогоны завершены: en_us 54 состояния и ru_ru 54,
оба маркера проверены штатным verify_ui_snapshots.py. Actionlint и
проверка diff успешны. Удалённый повтор GitHub CI ещё не наблюдался;
успех локального macOS-клиента не заменяет Linux-проверку релизного коммита.
