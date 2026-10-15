# WebSocket API

Endpoint — `/api/v1/ws`, listener настраивается отдельно в `[api.websocket]`.
Для внешнего доступа используйте WSS. Постоянный ключ допускается только для
backend-соединения без Origin: заголовок `Authorization: Bearer <key>` и scope
`websocket.connect`. Браузер использует [одноразовый ticket](websocket-browser.md).

Текстовые сообщения — JSON. Ответ имеет оболочку `{"type":"…","data":{…}}`.
Неизвестные поля отклоняются. Авторизация проверяется при каждом запросе и
периодически во время подписки; отзыв ключа или сессии прекращает соединение.

## Доменные подписки

```json
{"type":"watch","id":"events","path":"events","query":{},"session":null,"cursor":null}
```

Path — маршрут чтения относительно `/api/v1/`. Backend может передать сессию
игрока; браузер обязан передать null и использует сессию своего ticket.
На соединение допускается до восьми подписок с разными id. Ответ
`resource.snapshot` содержит id, path, cursor и resource. При слишком большом
снимке приходит `resource.invalidated`: backend должен перечитать ресурс REST.
При утрате доступа приходит `watch.closed` с id и code.

Подписки повторно проверяют доступ и получают текущий снимок. Они не являются
архивом всех промежуточных изменений. При RESYNC_REQUIRED начните с cursor=null.
`{"type":"unwatch","id":"events"}` прекращает одну доменную подписку.

## Статус и тайлы

```json
{"type":"subscribe","topic":"server.status","cursor":null}
```

Требуется `server.status.subscribe`. Первый ответ — snapshot, последующие — event.
Для продолжения передайте cursor с полями stream и sequence. При потере истории
приходит error с RESYNC_REQUIRED; получите новый снимок.

Для карты используйте topic `map.tiles` или `map.tiles.isometric`, при необходимости
profile. Требуется `map.tiles.read` и scope выбранного приватного профиля.
Ответы: terrain.snapshot, terrain.changed, terrain.closed. PNG не передаётся:
backend читает его через REST. `{"type":"unsubscribe"}` отменяет подписки
на статус и тайлы; доменные watch отменяются отдельно.

## Запросы backend

```json
{"type":"request","id":"read-status","method":"GET","path":"/api/v1/server/status","session":null,"ifMatch":null,"body":null}
```

Ответ response содержит id, status и body из REST-диспетчера. HTTP-заголовки
не передаются; для ETag и Retry-After используйте HTTP. Тела GET/DELETE должны
быть null. Scope, личная сессия, права и правила изменения данных такие же, как
в REST. Маршруты sessions, websocket и tile.png через request запрещены.
Браузерные соединения допускают только подписки, без request и изменения данных.

Сообщения и очереди ограничены; медленный получатель отключается. После разрыва
восстановите подписки с последними cursor либо запросите новые снимки. Не считайте
разрыв подтверждением отмены уже отправленной операции изменения.

## В этом разделе

<div class="rivet-topic-grid" markdown>

<div class="rivet-topic-card" markdown>

[WebSocket в браузере](websocket-browser.md)

Получите одноразовый ticket через backend сайта и откройте соединение.

</div>

<div class="rivet-topic-card" markdown>

[Изменения тайлов](websocket-map.md)

Подпишитесь на профиль карты и обновляйте изменённые тайлы через REST.

</div>

</div>
