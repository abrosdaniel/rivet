# Кандидат Rivet2.2.1 — 2026-10-14

## Состояние

PATCH поверх опубликованной2.2.0. Исправления карты, публикаций и очереди
черновиков сохраняют форматы данных, настройки и протоколы pack2/auth3/menu3/helper1.
Commit/push/publish не выполнялись; установленный профиль не менялся.

Локальный комплект dist/2.2.1: единый JAR, core.json, SHA256SUMS.txt,
RELEASE_NOTES.md. JAR16601097 байт, SHA256:
9ecdef0803f4ab02593e5d81dbc153d619dfbc2d0a11d04e40822e74eb13e744.
Descriptor, размер, SHA-файл, вложенная версия, JSON локализации и серверный
шаблон сверены с исходниками; verifyBundle проверяет отсутствие harness/fixtures.

## Проверки

- Полный build + PostgreSQL с --rerun-tasks и --refresh-dependencies прошёл:
  core547/helper10/bootstrap5/mod6 (568), PostgreSQL228, из них221 выполнен,
  7 нагрузочных сценариев штатно пропущены. После изменений только клиентского
  кода выполнены дополнительная сборка/check/verifyBundle и native проверки.
- Python32: успешны в временном окружении с объявленным jsonschema4.23.0.
  Первая попытка без этой зависимости не прошла; это исправлено окружением,
  тесты не отключались.
- Очередь черновиков: обе новые регрессии воспроизведены до исправления и прошли после.
- Native terrain выявил отбрасывание preview при обновлении styleEpoch.
  Детерминированная регрессия до исправления падает, после проходит:
  9216чанков,184391проверка, восстановление536ms при заблокированном colour worker
  и обновлении освещения на каждом тике. Это локальное измерение, не обещание FPS.
- Финальный упакованный JAR прошёл lifecycle default/disabled/base: START_OK/STOP_OK,
  включая отключение карты и chat history, отсутствие world/store/worker после остановки.
- Native сеть: ingress, publication (9вариантов и Close во время Submit), light,
  permissions и teleport успешны; native terrain проверен отдельно выше.
  Подтверждены actual server revoke, wildcard/explicit deny, отсутствие OP,
  смена измерений, свет без block updates, chat copy и revision moderation.
- Полная локальная UI-матрица RU/EN прошла:16 завершённых запусков,
  release corrections, approved components, HUD, task flows/navigation, adaptive
  layouts (198сценариев на язык), reorder, TAB, settings/confirmations.
  Default/light/high contrast GUI1/2/3 и representative samples остальных тем.
  Проверены completion markers и отсутствие FAILED; screenshots отключены.

## Публикация

GitHub releasev2.2.0 (published2026-10-14T00:41:18Z), commit
ec6b9a2c04983052ac329c0a3fad0cef867dc59d и run38490395393 success проверены через API.
Это не CI новых незакоммиченных изменений. Для готовности к публикации2.2.1
нужна успешная полная матрица точного релизного коммита; push в main автоматически
публикует после CI и в этой работе не выполняется.

Новая матрица Windows/Linux/PostgreSQL14–18 не подтверждена этим локальным
прогоном. Реальные100игровых клиентов, полная пользовательская сборка и длительный
soak не выполнялись. Согласованные ограничения платформенной безопасности
сохраняются (SECURITY_AUDIT_2026_10_13.md).
