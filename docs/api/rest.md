# REST API

HTTP API предназначен для backend доверенного приложения. Включается отдельно
в `[api.http]`; для внешнего доступа используйте HTTPS через reverse proxy.
API не предоставляет веб-интерфейс. Полный перечень методов, полей и ошибок —
[OpenAPI 3.1](../../rivet/core/src/main/resources/rivet/api/openapi.json), доступный
также через `GET /api/v1/openapi.json`. Проверяйте `GET /api/v1/capabilities`.

## Первый запрос

Попросите владельца [включить HTTP API и выдать ключ](../server/api.md).
Из backend приложения проверьте доступные возможности:

```bash
curl --fail-with-body \
  -H "Authorization: Bearer $RIVET_API_KEY" \
  https://api.example.org/api/v1/capabilities
```

`RIVET_API_KEY` — переменная окружения вашего backend. Не передавайте постоянный ключ в браузер.
Для личных данных понадобится также [сессия игрока](rest-auth.md).

## В этом разделе

<div class="rivet-topic-grid" markdown>

<div class="rivet-topic-card" markdown>

[Авторизация запросов](rest-auth.md)

Ключ приложения, сессия игрока и проверка прав.

</div>

<div class="rivet-topic-card" markdown>

[Запросы и ошибки](rest-requests.md)

Формат запросов, повтор изменений, курсоры и обработка ошибок.

</div>

<div class="rivet-topic-card" markdown>

[Тайлы и задания рендера](rest-terrain.md)

Получение PNG и управление рендером сохранённого мира.

</div>

<div class="rivet-topic-card" markdown>

[Ресурсы и действия](rest-resources.md)

Чтение и изменение данных игроков и сообщества.

</div>

<div class="rivet-topic-card" markdown>

[Управление сервером](rest-server.md)

Объявления, обслуживание и остановка через API.

</div>

<div class="rivet-topic-card" markdown>

[Объединения и расписания](rest-community.md)

Участники, заявки, внутренние метки и повторяющиеся события.

</div>

<div class="rivet-topic-card" markdown>

[Игроки на карте](rest-presence.md)

Присутствие, приватность позиций и доступные Waystones.

</div>

<div class="rivet-topic-card" markdown>

[Браузерные tickets](rest-tickets.md)

Подключение браузера без постоянного ключа API.

</div>

<div class="rivet-topic-card" markdown>

[Приватные карты](rest-private-map.md)

Доступ к профилям, кэширование и пещерные срезы.

</div>

</div>
