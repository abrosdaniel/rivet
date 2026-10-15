# Каталог операций

Сервисы задач, профилей, сообщества и администрации.

Доступны методы `reports`, `report`, `staffReports`, `staffReport`, `statistics`,
`skin`, `pack`, `notificationPreferences` и `moderationPublications`.
Соответствующие команды находятся в `DomainCommand`, подписки — в `DomainWatch`.
Они используют те же правила приватности и владения, что игровые операции.

- `taskDetails` возвращает исполнителя, срок, страницы комментариев и подзадач,
  требования ресурсов. Для изменения используются `AddSubtask`, `EditSubtask`,
  `ToggleSubtask`, `RemoveSubtask`, `TaskResources` и остальные команды задачи.
- `serverControl()` и `DomainWatch.ServerControl` дают состояние управления сервером.
  Остановка, обслуживание и объявления требуют текущих `rivet.restart`,
  `rivet.maintenance` и `rivet.announce` соответственно. Офлайн-доступ проверяется
  через статический серверный контекст LuckPerms или OP; UUID сам по себе прав не даёт.
- `groupMarkers(group, cursor)` и `groupPeople(group, kind, cursor)` возвращают
  `GroupPage` с ревизией объединения и продолжением. Внутренние метки доступны
  участникам; заявки и приглашения — управляющим либо самому заявителю/получателю.
  `SaveGroupMarker` и `DeleteGroupMarker` требуют ревизии объединения и метки.
- `CreateScheduledEvent` задаёт аудиторию, приглашённых, длительность, повторения
  и часовой пояс IANA. `eventSchedule(id)` возвращает доступные события серии;
  список приглашённых видит только владелец.
- `scheduledAnnouncements()`, `ScheduleAnnouncement` и `CancelAnnouncement`
  используют серверное расписание и проверяют `rivet.announce`, в том числе офлайн.
- `onlinePlayers(cursor)`, `positions(dimension, cursor)` и `waystones(dimension, cursor)`
  работают только в контексте онлайн-игрока, прошедшего авторизацию. Пустое измерение
  выбирает все разрешённые. Присутствие и позиции не включают самого читателя.

Подписки `GroupMarkers`, `GroupMembers`, `EventSchedule`, `ScheduledAnnouncements`,
`OnlinePlayers`, `Positions` и `Waystones` следуют общим правилам `DomainWatch`.
Списки наблюдают первую страницу; остальные страницы читайте обычными методами.
При утрате доступа удалите сохранённый снимок. Обращения к миру выполняются
на игровом потоке, чтения БД — в рабочих потоках API.
