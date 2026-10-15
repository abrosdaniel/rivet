# API и серверная карта — состояние кандидата 2.3.0

Обновлено 2026-10-15. Реализация и локальная проверка завершены в описанном ниже
объёме. Публикация и полный удалённый CI точного релизного коммита не выполнены.
Этот файл хранит принятые ограничения и доказательства, а не руководство пользователя.

## Согласованные границы

- Один устанавливаемый JAR; PostgreSQL — серверное хранилище.
- Java API для установленных модов, REST для внешних backend, WebSocket для подписок.
  Никакого встроенного сайта, кабинета, Swagger UI или редактора.
- API-ключи управляются только из локальной консоли, не из игрового меню или RCON.
- Доверенные приложения могут использовать логин/пароль Rivet с отдельным scope;
  при выключенном Auth игрок подтверждает привязку в игре. Ключ не заменяет игрока.
- Административные операции доступны офлайн с актуальными проверками LuckPerms.
- Waystones и режим позиции «рядом» требуют связанного игрока онлайн и авторизации.
- Приватные профили карты разрешаются конкретному API-ключу; отдельное право игрока
  для каждого профиля не требуется. Маски применяются до выдачи и формирования кэша.
- Браузеру передаётся одноразовый WSS ticket, постоянные ключи/сессии хранятся backend.
- Серверная карта не использует файлы исследованной карты клиентов. Общий обмен
  исследованием не возвращать. Нет зависимости от Dynmap, Xaero или JourneyMap.
- Полный рендер читает сохранённый мир без загрузки и генерации чанков.
- Assets установленных модов + локальные ресурсы в rivet/terrain-resources;
  автоматическое скачивание ванильных assets не делать.
- Возможности отключённых модулей недоступны; права, приватность, ревизии,
  идемпотентность операций и закрытие подписок проверяются общими сервисами.

## Реализованный объём

| Область | Где находится актуальный контракт |
| --- | --- |
| Java: домены, команды, подписки, слои сторонних модов | [Java API](../docs/api/java.md) |
| REST: чтение, изменения, сессии, управление сервером | [REST](../docs/api/rest.md), [OpenAPI](../rivet/core/src/main/resources/rivet/api/openapi.json) |
| WebSocket: подписки, resync, tickets, отзыв доступа | [WebSocket](../docs/api/websocket.md) |
| Ключи, лимиты, origins и отключение HTTP/WS | [Настройка API](../docs/server/api.md) |
| Поверхность, изометрия, пещеры, профили и очередь заданий | [Рендер](../docs/server/terrain.md) |
| Текстуры, жидкости, статические модели и поставщики | [Материалы](../docs/api/terrain-materials.md) |
| TLS/WSS и ограничения proxy | [Reverse proxy](../docs/server/reverse-proxy.md) |

## Ограничения и оставшиеся действия

- Неизвестные клиентские загрузчики, динамические block entities и custom fluids
  требуют серверного поставщика. Полного паритета клиентских шейдеров/света нет.
- Нагрузка100 — синтетические идентичности; это не100 Minecraft JVM и не TPS-бенчмарк.
- WSS проверен Java-клиентом через Nginx; Chrome отдельно проверял браузерный протокол
  по loopback HTTP. Системное хранилище сертификатов не менялось.
- Перед публикацией нужен коммит-кандидат и зелёный полный CI этого коммита:
  Linux/Windows/macOS, PostgreSQL17/18, UI, lifecycle, map-network, api-proxy.
- Push новой версии в main автоматически публикует после CI. Подготовка кандидата
  не является разрешением на публикацию; установленный профиль не заменять.
- Безопасность платформенных библиотек остаётся в [аудите](SECURITY_AUDIT_2026_10_13.md).

## Итоговые проверки

Промежуточный журнал этапов1–36 удалён после сверки с действующими контрактами.
Ниже оставлены итоговые проверки и их фактические ограничения.

## 37. Cross-transport contract and security review (2026-10-15)

Reviewed JavaDomains/JavaWatches, REST dispatcher and domain command/resource
services, backend/browser WebSocket paths, sessions/tickets/key revalidation,
private terrain profiles, cursor delivery and map-layer ownership. Java consumers
remain trusted installed code; scopes are for external applications. Native player
permissions, module flags and domain visibility remain shared service checks.
No bypass of the disabled server module was confirmed: ApiServerControl checks it.
Java's documented TimeoutException remains distinct from ApiException failures.

Confirmed fixes:
- PlayerSessions could return a session revoked/expired/stopped while key/account
  storage validation was in progress. Recheck the same stored session at completion.
  Browser ticket admission also rechecks ticket-service lifecycle after validation.
- Synthetic backend WS requests bypass HTTP framing. Shared REST dispatch now
  rejects read/delete/preflight bodies, transfer encoding and duplicate singleton
  headers before both base and extension routes.
- Private profile discovery revalidated the general tiles scope but not individual
  profile scopes after reading metadata. Recheck every returned private profile.
- REST/WS documentation accidentally contained the Java guide as its opening
  sections. Replace with transport-specific authorization, messages, errors and
  terrain routes; retain domain/private-profile details. OpenAPI validation now
  covers every method, including DELETE/OPTIONS, and unique operation IDs.

Regression coverage includes deterministic revocation during key-storage validation
(token revoke, player revoke, shutdown, expiry), private-profile permission loss
while preparing metadata and malformed synthetic requests before extension dispatch.
Initial regression fixture used a Grant belonging to the replaced key-service
instance; corrected by authorizing through the replacement instance, preserving
production issuer binding.

Forced validation passed:121 core API/settings tests,75 PostgreSQL tests,4 public
API tests; mod compile and module composition. OpenAPI schema/reference/method
validation, generated-resource equality and whitespace checks passed. Native
NeoForge+LuckPerms HTTP on/off and saved-world chain passed (RIVET_EXTENDED_NATIVE_CHAIN_OK).
The first native run timed out during the WS phase: its HttpClient cleanup could
wait on an unclosed socket after an assertion. Added unconditional socket abort
in the harness; rerun passed all scenarios. The initial timeout did not reproduce,
so this is a diagnostic/lifecycle improvement, not proof of its underlying cause.
This review is not a claim of security certification or arbitrary third-party
renderer compatibility. Real browser end-to-end, load benchmarks and exact-release
remote CI remain separate roadmap checks; no release/profile replacement here.

## 38. 100-identity load and actual Chromium browser (2026-10-15)

Implemented an opt-in, reproducible isolated PostgreSQL18 + core HTTP/WS/Java
harness and local browser fixture; see docs/development/api-testing.md. No web
product, runtime limit changes, installed profile replacement or release packaging.
Production HTTPS origin validation remains unchanged: only the fixture injects a
loopback HTTP Origin into BrowserTickets. TLS/WSS/reverse-proxy deployment remains
outside this local protocol verification.

Passed:
- Existing mixed-module load with100 synthetic identities,8 workers,24,000 operations
  (task read/write, board, local chat append/replay, notices),16.471s. Task-write
  p95=24.860ms; read/replay p95=2.706–3.723ms; ownership/history bounds checked.
- API burst2,000 real HTTP requests at100 concurrent clients: p95=124.735ms,
  max213.618ms,2.250s, no HTTP errors or crossed private task ownership.
- Java1,000 operations in ten100-call bursts:660 completed,340 expected BUSY,
  p95 of accepted=37.393ms. Two workers/64 queue remain bounded; BUSY is deliberate
  admission control, not reported as successful execution of those340 operations.
-100 independent user subscriptions multiplexed over13 backend sockets, initial
  snapshots500ms. Alongside them,6,000 HTTP requests paced100/s for60seconds:
  p95=129.793ms,max149.729ms, no unexpected connection loss or private data leakage.
- All100 subscriptions close after session revocation in1.854s; new sessions can
  read immediately afterwards. Previous short run also passed (4.444s revocation).
- Harness JVM512MiB: sampled steady-phase peak209.23MiB, end80.28MiB; process CPU
 9.65s during60s. These numbers include BOTH server-side core and synthetic Java/HTTP
  clients, not a production-server CPU/heap benchmark or a long-duration leak proof.
- Actual Chrome153 via browser UI:8/8 assertions, ticket/subprotocol/private snapshot,
  single use, real foreign Origin, expiry, mutation denied, CORS denial, session and
  key revocation. Browser saw no persistent key/player token. Report captures UA.

Fixture corrections: Gradle init root-project lookup and direct ApiException
handling in CompletableFuture.handle (expected BUSY). No production defect was
confirmed by these scenarios; do not claim a runtime performance fix was needed.
Forced compile/testClasses, opt-in PostgreSQL mixed test and JavaExec passed;
Python syntax and whitespace checks passed. Disposable database removed on exit.

Boundaries: not100 Minecraft clients, no TPS/world-generation/client-render benchmark;
not100 browser sockets (runtime1–32 limit unchanged). HTTP stress explicitly raises
workers/connections/rate budgets in the fixture, never in server defaults. No Internet
latency, TLS/proxy deployment or remote release CI claim. Remaining release readiness
requires exact-commit CI and packaging/documentation/version review.

## 39. TLS/WSS reverse proxy and release gate (2026-10-15)

Added opt-in ApiProxyHarness + tooling/api_proxy_smoke.py. Reuses isolated seeded
PostgreSQL fixture with a production-valid HTTPS origin; no HTTP-origin bypass in
this suite. Real Nginx image pinned by manifest digest, temporary one-day CA trusted
only by the Java client, no system/browser certificate changes. Docker containers
and signing keys removed in finally. Linux uses host networking with loopback-only
Nginx listeners; Docker Desktop uses loopback-published ports and host.docker.internal.

Final local macOS/Docker Desktop run passed 15 transport/security assertions:
untrusted CA, hostname mismatch, TLS1.2/1.3, HTTP redirect, authenticated HTTPS private
read, no-store, rejected Origin/browser Bearer, WSS snapshot/subprotocol, single-use
and expired tickets, wrong ticket Origin, session and key revocation. These are
real proxy sockets with a Java client; actual Chrome was separately checked in §38,
not claimed here as a browser TLS trust test. A new fixture assumption of 401/403
for rejected handshakes was corrected to the actual Java-WebSocket1.6.0 contract:
404 Upgrade Failure; inspected library bytecode distinguishes internal500 errors.
No production transport defect was confirmed and no runtime security rule weakened.

Fixed verification gap: full CI now includes mandatory api-proxy job before release;
artifacts retain only run.log/nginx.log/proxy.json, not certificates or DB credentials.
Added public proxy deployment guide, including forwarded-IP quota aggregation and
credential-free logging/cache guidance. actionlint1.7.7 passed all workflows.
Forced online Gradle build --rerun-tasks --refresh-dependencies passed34tasks and
666Java tests (api4/bootstrap5/core641/helper10/mod6; zero skipped/failing).
Tooling33tests passed. Final proxy fixture forced compilation and scenario passed.

Observed GitHub run38497562819 on fe1ff839223d0978a2356c7690baf0039934090d:
all required jobs including release/publish succeeded. This is the OLD tracked
commit and does NOT validate current uncommitted API work. New Linux proxy CI job,
remote platform matrix and release readiness for the current candidate remain
unconfirmed until that candidate is committed and checked remotely. No push,
publication, version bump or installed-profile replacement performed.

## 40. Release candidate 2.3.0 (2026-10-15)

User requested release preparation and only necessary documentation updates.
MINOR2.3.0 for additive API/render features; installation still one JAR.
Updated existing README, SERVER_GUIDE, CONTRIBUTING, RELEASE_NOTES; removed stale
status-only REST and incomplete scope-list claims. No new documentation pages.
Updated ai index:2.2.1 preparation is historical. GitHub latest verified as v2.2.1.

Forced build + full PostgreSQL18 suite with refreshed dependencies passed35tasks:
666Java tests,303PG cases (296passed,7opt-in load skips),33Python tooling tests.
NeoForge native API enabled/disabled with LuckPerms and saved-world rendering passed.
Local dist/2.3.0 contains bundle17286934bytes, descriptor, SHA256SUMS and notes.
Verified source/template/localization/OpenAPI parity, Java-WebSocket license and
absence of harness classes; protocol map equals released2.2.1 (pack2/auth3/menu3/helper1).
Public documentation relative file links and git whitespace check passed.

No commit/push/publish or installed-profile replacement. Exact2.3.0 remote full CI
(platform/UI/PostgreSQL17/18/modules/map-network/api-proxy) remains mandatory before
publication. Local artifact is a candidate, not evidence of remote release readiness.

## Очистка документации и повторная подготовка — 2026-10-15

Удалён старый SERVER_GUIDE; ссылки в README, UI Kit, шаблоне и диагностике
ServerSettings ведут к единой документации. CONTEXT сокращён до решений и ссылок.
Два небольших отчёта карты/уведомлений объединены с VALIDATION_2026_10_14 без
удаления свидетельств. Исторические аудиты и открытые риски сохранены.

Строгая сборка сайта и проверка 80 страниц/якорей/ресурсов прошли. Проверка ссылок
включена в workflow Documentation. Локальные ссылки README/CONTRIBUTING/ai проверены.
Полный build с --rerun-tasks и --refresh-dependencies прошёл; после замены ссылки
в ServerSettings/шаблоне повторный forced build: 666 Java-тестов, 0 failures/errors.
Python tooling: 33 passed в изолированной среде с requirements.txt. PostgreSQL,
нативные сценарии и удалённая матрица в этой проверке заново не запускались.
Комплект dist/2.3.0 обновлён штатным release.py: hash, шаблон из исходников и отсутствие
Harness проверены. Базовые выходы тестов изолированы в /tmp; bootstrap JAR по
существующему build.gradle сформирован в rivet/mod/build/libs. Публикации не было.
