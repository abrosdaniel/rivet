# Перенос UI на Skia

2026-11-09: пользователь одобрил перенос всего UI с сохранением функциональности,
стиля и переиспользуемых компонентов. Установленный клиент не менять.

Этапы:
1. Общий клиентский backend: зависимости и native resources всех платформ в одном
   JAR, распространяемый шрифт, управление GPU lifecycle, границы native/Skia.
2. UiKit/UiIcons и общий текстовый путь: темы, pose/scissor, форматирование,
   native fallback для неподдерживаемых glyph/provider, сохранение hitboxes.
3. Все экраны и HUD используют общий путь; native item/entity/terrain остаются
   игровыми ресурсами. Нативное управление контейнером и ввод не переписывать.
4. Проверка упаковки/серверного запуска, unit/contracts и живой полный UI suite
   RU/EN, масштабы/темы, поля/прокрутка/предметы/гардероб/Trade; исправления.
5. Документация UI Kit, отдельный клиент для проверки пользователя.

Не объявлять поддержку платформ проверенной без фактического прогона. Не публиковать
релиз автоматически. Производительность измерять на реальных экранах, отдельно от
простого прототипа. Переиспользуемые primitives/components остаются в UI Kit.

## Выполнено в рабочей ветке

- Общий UiCanvas/SkiaUiBackend: GPU, ordered batches, scissor/pose, shader tint,
  нативные границы, fallback специальных шрифтов, ограниченные кэши glyph metrics.
- UiKit: общие радиусы, мягкая elevation, hover с учётом времени и reduced motion,
  поля/фокус, Noto Sans, общие подсказки. Контейнеры и их input не переписаны.
- Natives 6 платформ внутри одного JAR; точное согласование Java/native version,
  Windows ICU data, проверяемый локальный кэш без runtime-downloads.
- Forced mod:test/verifyBundle, tooling unit tests (62, один skip), docs strict build;
  packaged server startup/shutdown без dev-библиотек прошли.
- RU/EN UI suites: pack, approved, HUD, native flows, adaptive, reorder, TAB,
  audit. После исправления двойного shader tint approved повторён успешно RU/EN;
  TAB EN повторён после исключения одновременной пересборки/старта клиента.
- Live: HD skin/cape/head/elytra 1024; Trade native/Rivet scales, cursor, draft undo,
  theme/contrast; terrain checks=110705 including reopen/disk/LOD/borders.
- Скриншоты UI Kit/Trade/HD просмотрены. CPU submission общего backend на реальных
  экранах обычно p50 0.2–0.6 мс; это не FPS/GPU timing, холодные пики встречаются.

Живые проверки выполнены только на macOS ARM64 / Java 21 / NeoForge 21.1.250.
Windows/Linux, сторонние shader packs и слабые GPU здесь не проверены. Exact-commit
CI для этих изменений не запускался. Версию/релиз не публиковать автоматически.
Подсказки подключены к UiCanvas отдельно от нативных item/entity компонентов;
живой гардероб с подсказкой проверен, снимок просмотрен. Дополнительная матрица
каталога на окончательных подсказках прошла. Установленный Prism-клиент не менять.

Packaged client также прошёл approved/Skia framebuffer checks: production JAR
с UI-fixtures, добавленными только в изолированную копию game.jar; MOD_CLASSES
и внешние bundled dependencies исключены. Основной артефакт не изменялся.

Финальный изолированный клиент запущен для пользователя; Trade review READY
на окончательном UI, основной вход в меню F8. Установленный клиент не менялся.
Артефакт около 94 MiB из-за включённых native payloads; перед выпуском нужна
новая версия (2.11.0 уже опубликована) и exact-commit CI.

## Повторный аудит экранов

- Проверены общие пути отрисовки экранов/диалогов: нативные текстовые вызовы
  перехватываются UiCanvas; игровые предметы, модели и растровые ресурсы остаются
  на игровом renderer. Выбранный пользователем Minecraft-стиль Trade сохраняется.
- Сектора колеса перенесены в общий Skia path; native recovery остаётся только
  внутри UI Kit. Добавлены framebuffer-проверки полукольца и прозрачного центра.
- Исправлены высота/центрирование вкладок, общий фон раскрывающихся списков,
  округлённые цветовые ячейки и кнопка обновления, выделение текста без синей
  инверсии. Полное название пункта доступно через подсказку.
- Убрана принудительная белая окраска инструментов карты в светлой теме.
- Повторная RU/EN матрица прошла: pack, approved, HUD, native, adaptive, reorder, TAB и audit.
  Дополнительные account/moderation layouts: 126 сценариев на язык.
  Живые Trade seed/offer/review и сборка/verifyBundle прошли. Remote CI не запускался.
- В UI CI добавлен отдельный прогон окон аккаунта и модерации: приложения,
  безопасность, смена пароля с TOTP, отвязка, приглашение, управление обращением,
  массовое действие и каталог секторов. Тестовые account layouts рисуются без
  сетевых мутаций; это проверка UI, а не успешной авторизации.

## Выявленный отдельный пробел официальных плащей

Пользователь сообщает, что выбранный официальный плащ в Rivet показан как «—».
Проверка кода: MojangSkins.find читает только SKIN; ServerSkins сохраняет только
fallback skin. ordinarySkin/withCape полагаются на capeTexture стандартного
GameProfile. При Rivet Auth локальный профиль может не содержать CAPE, хотя
серверный skin fallback работает. Получение/хранение/синхронизация официального
CAPE добавлены: отдельный cape-official cache, общий appearance/blob transport,
ordinarySkin для карточки и модели гардероба. Источник кэша обновлён до appearance-v2,
чтобы старый skin-only cache не задерживал получение плаща.
Parser tests и PostgreSQL cape/outfit tests прошли. Живой isolated client подтвердил
получение серверного CAPE для локального UUID, модель гардероба, hide/default и элитры.
Это детерминированная текстура профиля, не проверка production-аккаунта пользователя.

Повторный официальный cape native-прогон после перезапуска сервера и клиента прошёл:
сохранённый CAPE, custom → official, hide → official, vanilla/custom elytra selection.
Strict docs и diff check прошли. Установленный клиент не заменён.

## Обратная связь по HUD и наведению

- На дробном масштабе HUD старый UiHudSurface рисовал каждую строку отдельно;
  сглаживание Skia делало стыки полупрозрачных строк заметными. Заменён на
  общие цельные rounded/ring из UI Kit, включая тени и контрастный контур.
- Quiet-кнопки затухали к непрозрачному цвету panel, отличающемуся от подложки;
  теперь hover плавно уходит в прозрачность. Selected и keyboard focus сохранены.
- Добавлены framebuffer-проверки цельного HUD на масштабе 0.73 и проверки
  прозрачности hover. Повторные native проверки прошли: approved 126, HUD 216, audit 321 settings
  при GUI 1/2/3; framebuffer regression проверена. Тестовый клиент перезапущен.

## Финальный проход 2.12.0

- SidebarButton также переведён на прозрачный hover: предыдущая правка затрагивала
  общие quiet buttons, но не этот самостоятельный компонент.
- UiCanvas больше не возвращает всю строку Minecraft из-за одного отсутствующего
  символа; нативные glyph fallback рисуются после Skia-текста. Немедленный native
  вызов внутри обхода текста вызывал исчезновение хвоста; исправлено, добавлен
  framebuffer ink check. HUD screenshot подтвердил хвост навигационной строки.
- Из ChatHints удалены vanilla command aliases и неиспользуемый ключ перевода.
- Version 2.12.0, README/guide/UI Kit/release notes обновлены. Полный forced build
  прошёл: 875 Java tests, 0 failures/errors, 1 opt-in skip; tooling 62/0/1 skip.
  Packaged server lifecycle прошёл. Кандидат собирается в dist/2.12.0-candidate.
  Exact-commit CI ещё не запускался, публикация не выполнена.

Финальный packaged client 2.12.0: 126 approved frames и framebuffer mixed-text
ink check прошли. Повторный HUD216 после порядка fallback прошёл; screenshots
проверены, хвост навигации присутствует, chat hints без vanilla aliases.
Candidate hashes/resources проверены, test harness classes в production JAR отсутствуют.
Strict docs/diff check прошли. Изолированный review client перезапущен; Prism не изменён.


## Оптимизация кандидата 2.12.0 — 2026-11-09

- Универсальный JAR: 84 534 824 → 79 988 474 байта (80,62 → 76,28 МиБ;
  −5,38%). Windows ICU хранится в shared: сборка сравнивает обе исходные копии,
  загрузчик извлекает её рядом с DLL нужной архитектуры. Все шесть платформ сохранены.
- Skia применяет матрицу один раз на последовательность команд с одинаковой позой.
  Порядок, scissor/native boundaries и восстановление GL сохранены.
- Один и тот же approved UI-прогон, 126 сценариев, 1280×1024, RU, macOS ARM64:
  после 120 прогревочных кадров восемь окон по 240 кадров. Медиана окон p50 CPU:
  0,518041 → 0,4954785 мс; медиана окон p95: 1,4437085 → 1,271854 мс.
  Это время Skia draw, не полный frame time/FPS; локальный замер, без гарантии
  аналогичного выигрыша на других GPU. Максимумы не использовать как устойчивую метрику.
- CI: native jobs зависят от java-linux; java gate всё ещё требует Windows/macOS
  в полной матрице. UI: три независимых группы на каждом языке, все девять
  сценариев и маркеров сохранены, изоляция процессов сохранена. Добавлены guards
  полноты и соответствия launch/verify одной группе.
- Экспорт CI оставляет только native resources платформы runtime. На ARM64 macOS
  resources/main: 146 957 418 → 28 794 378 байт (−112,69 МиБ несжатых данных).
  Это размер ресурсов, не всего архива и не замер длительности GitHub Actions.
- Forced build: 37 задач выполнены, 875 Java tests, 0 failures/errors, 1 opt-in skip.
  Tooling: 65 tests, 0 failures, 1 opt-in skip. Strict docs прошли.
  Сетевое разрешение dependencies с refresh прошло. Packaged client (Skia contracts
  + approved126), packaged dedicated server lifecycle и exported/restored CI runtime
  (approved126) прошли. Полная UI матрица: все 18 suites RU/EN прошли, включая adaptive198,
  reorder135, HUD216, approved126 и nested126 на каждом языке.
  Локальная сумма длительностей RU Java-процессов — 568 с; те же сценарии
  распределены на группы 139/200/229 с. Расчёт критического пути: 568 → 229 с
  без checkout/download/setup/очереди GitHub. Это модель расписания по локальным
  замерам, не фактическое время remote CI.
- Кандидат перепакован; exact-commit GitHub CI ещё не запускался. Нет commit/push/
  publish и замены установленного клиента.
