# Браузерные tickets

Подключение браузера без постоянного ключа API.

`POST /api/v1/websocket/tickets` — только backend, без Origin; обязательны Bearer
ключ с `websocket.ticket` и `websocket.connect`, действующий `X-Rivet-Session` и
`Content-Type: application/json`. Тело: `{"origin":"https://site.example"}`,
без дополнительных полей и query; максимум 1024 символа. Origin точно соответствует
`api.http.origins`. Ответ: `ticket`, `expiresAtEpochMillis`, `protocol: "rivet.v1"`,
с `Cache-Control: no-store`. Capabilities включают `websocket.browser`, если WS включён.
При выключенном WebSocket выдача возвращает `MODULE_DISABLED`, при исчерпании
ёмкости — `BUSY`. Ticket не заменяет scopes чтения и права игрока.

Backend выдаёт браузеру только ticket. Подключение, ограничения подписок, отзыв
и reconnect описаны в [протоколе WebSocket](websocket-browser.md).
