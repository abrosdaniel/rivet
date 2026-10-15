# Изменения тайлов

Подпишитесь на профиль карты и обновляйте изменённые тайлы через REST.

```json
{"type":"subscribe","topic":"map.tiles","profile":"staff","cursor":null}
```

Необязательное поле `profile` выбирает настроенный приватный профиль. Без него
`map.tiles` выбирает `surface`, а `map.tiles.isometric` — `isometric` как прежде.
Поле допустимо только у terrain-подписки. `terrain.snapshot.data.profile` содержит
выбранный ID, `projection.profile` — вид проекции. Одновременно у соединения одна
terrain-подписка. Требуются `map.tiles.read` и `map.tiles.profile.staff`;
права перепроверяются перед выдачей изменений. Cursor другого профиля вызывает
`RESYNC_REQUIRED`. Отзыв ключа закрывает соединение. Эти правила одинаковы для
backend и браузерных tickets; билет не расширяет scopes выдавшего его ключа.
Сами PNG через WebSocket не передаются: их получает backend через REST.

Пещерный профиль использует ту же подписку `map.tiles` с `profile: "deep_caves"`.
В `terrain.snapshot.data` возвращаются `projection.profile: "cave"` и
`slice: {minY, maxY}`. Требуется точный scope `map.tiles.profile.deep_caves`;
браузерный ticket не предоставляет доступ автоматически. Изменения и отзыв прав
обрабатываются как у остальных приватных профилей, с отдельным cursor.

Проверка под нагрузкой и в браузере: [изолированный стенд API](../development/api-testing.md).

Настройка TLS: [HTTPS/WSS через reverse proxy](../server/reverse-proxy.md).
