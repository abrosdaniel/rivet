# Каталог операций

Операции профилей, объединений, уведомлений и администрации.

Доступны методы `reports`, `report`, `staffReports`, `staffReport`, `statistics`,
`skin`, `pack`, `notificationPreferences` и `moderationPublications`.
Соответствующие команды находятся в `DomainCommand`, подписки — в `DomainWatch`.
Они используют те же правила приватности и владения, что игровые операции.

- `serverControl()` и `DomainWatch.ServerControl` дают состояние управления сервером.
  Остановка, обслуживание и объявления требуют текущих `rivet.restart`,
  `rivet.maintenance` и `rivet.announce` соответственно. Офлайн-доступ проверяется
  через статический серверный контекст LuckPerms или OP; UUID сам по себе прав не даёт.
- `groupMarkers(group, cursor)` и `groupPeople(group, kind, cursor)` возвращают
  `GroupPage` с ревизией объединения и продолжением. Внутренние метки доступны
  участникам; заявки и приглашения — управляющим либо самому заявителю/получателю.
  `SaveGroupMarker` и `DeleteGroupMarker` требуют ревизии объединения и метки.
- `scheduledAnnouncements()`, `ScheduleAnnouncement` и `CancelAnnouncement`
  используют серверное расписание и проверяют `rivet.announce`, в том числе офлайн.
- `onlinePlayers(cursor)`, `positions(dimension, cursor)` и `waystones(dimension, cursor)`
  работают только в контексте онлайн-игрока, прошедшего авторизацию. Пустое измерение
  выбирает все разрешённые. Присутствие и позиции не включают самого читателя.

Подписки `GroupMarkers`, `GroupMembers`, `ScheduledAnnouncements`,
`OnlinePlayers`, `Positions` и `Waystones` следуют общим правилам `DomainWatch`.
Списки наблюдают первую страницу; остальные страницы читайте обычными методами.
При утрате доступа удалите сохранённый снимок. Обращения к миру выполняются
на игровом потоке, чтения БД — в рабочих потоках API.

Методы и команды удалённых механик сохранены только для совместимости Java API
и возвращают `UNAVAILABLE`. Они не читают и не изменяют архивные данные.
