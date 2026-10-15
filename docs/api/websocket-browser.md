# WebSocket в браузере

Получите одноразовый ticket через backend сайта и откройте соединение.

Backend сайта получает `POST /api/v1/websocket/tickets` с `Authorization: Bearer …`,
`X-Rivet-Session` и JSON `{"origin":"https://site.example"}`. Требуются scopes
`websocket.ticket`, `websocket.connect` и scopes читаемых ресурсов. Игрок должен
иметь действующую сессию этого приложения. Заголовок Origin при выдаче запрещён:
это серверный запрос. Origin будущего браузера должен точно входить в `api.http.origins`.
Ответ содержит `ticket`, `expiresAtEpochMillis` и `protocol: "rivet.v1"`;
`Cache-Control: no-store`. Backend передаёт браузеру только этот ответ, сохраняя
постоянный ключ и токен сессии у себя. Защита своего endpoint от CSRF, привязка
пользователя сайта к его сессии Rivet и ограничение частоты — ответственность сайта.

```js
// Собственный endpoint сайта; это НЕ адрес REST Rivet.
const response = await fetch('/backend/rivet-ws-ticket', {method: 'POST'});
if (!response.ok) throw new Error('Не удалось получить ticket');
const {ticket} = await response.json();
const socket = new WebSocket('wss://api.example/api/v1/ws',
  ['rivet.v1', `rivet.ticket.${ticket}`]);
socket.onopen = () => socket.send(JSON.stringify({
  type: 'watch', id: 'notifications', path: 'notifications',
  query: {}, session: null, cursor: null
}));
```

Ticket передаётся в предложенных subprotocols, не в URL. Сервер выбирает только
`rivet.v1`, не возвращая ticket в ответе. Reverse proxy должен сохранять Origin и
Sec-WebSocket-Protocol и обеспечивать WSS. Исключите из журналов Authorization,
X-Rivet-Session и Sec-WebSocket-Protocol: последний содержит временный секрет.
Предложение браузером compression допускается, но расширение не согласовывается.

Ticket одноразовый: повторное подключение, в том числе конкурентное, отклоняется.
Неверный Origin также расходует предъявленный ticket. По умолчанию предъявить его
нужно за 30 секунд; это не время жизни уже открытого соединения. Для reconnect
получайте новый ticket через backend, с задержкой между попытками. При перезапуске
сервера все tickets исчезают. Отзыв ключа/сессии или истечение сессии закрывают
соединение при очередной проверке (обычно в течение секунды).

Браузеру разрешены `watch`/`unwatch` и `subscribe`/`unsubscribe` в пределах scopes
ключа и прав игрока. В `watch` поле `session` обязательно равно `null`: сервер
подставляет привязанную сессию, сменить игрока нельзя. Туннель `request` запрещён;
чтение дополнительных страниц и изменения выполняются через backend REST.
Постоянные ключи и токены сессий не передаются в JavaScript, URL или localStorage.
Существующие backend-подключения с Bearer без Origin остаются доступны.
