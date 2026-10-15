# Java API

Контракт находится в пакете `dev.abros.rivet.api` и отдельном артефакте
`rivet-api`. Он не зависит от Minecraft, Gson, PostgreSQL или HTTP.
Единый устанавливаемый JAR Rivet уже содержит эти классы; сторонний мод
использует артефакт только как `compileOnly`, без встраивания его в свой JAR.

```groovy
dependencies {
    compileOnly files('libs/rivet-api.jar')
}
```

Артефакт собирается командой `./gradlew :api:jar`; исходники —
`:api:sourcesJar`. Используйте артефакт из той же поставки Rivet.
Версия контракта — `RivetApi.MAJOR_VERSION`, независимо от версии мода.

## Подключение

После события запуска сервера передайте ID своего установленного мода:

```java
var connection = RivetApi.connect("example_mod");
if (connection.isPresent()) {
    var api = connection.get();
    var snapshot = api.serverStatus();
    int online = snapshot.status().onlinePlayers();
    var cursor = snapshot.cursor();
    // Позднее, в потоке своего потребителя:
    var changes = api.serverStatusEvents(cursor, 64);
    cursor = changes.nextCursor();
}
```

Если Rivet является необязательной зависимостью, загружайте класс интеграции
только после проверки наличия мода Rivet средствами загрузчика. Нельзя ссылаться
на отсутствующие классы API из безусловно загружаемого класса интеграции.
При наличии контракта, но отсутствии запущенного runtime `connect` возвращает
`Optional.empty()`. Повторно подключайтесь после каждого запуска сервера.

Java API предназначен для доверенных установленных модов. ID потребителя
не является сетевой авторизацией; код в той же JVM нельзя изолировать scopes.
Не передавайте этот интерфейс напрямую недоверенным внешним запросам.

## Провайдеры интеграций

`api.integrations()` возвращает привязанный к потребителю реестр `IntegrationApi`.
Он предоставляет контракты `WAYSTONES`, `VOICE`, `PERMISSIONS`, `IDENTITY`,
`DIAGNOSTICS` и `ACCESS`. Встроенные адаптеры используют те же контракты.

```java
try (var integrations = api.integrations()) {
    var stones = integrations.find(IntegrationApi.WAYSTONES, "waystones");
    if (stones.isPresent() && stones.get().available()) {
        // Выполняется в серверном потоке; playerId — онлайн авторизованный игрок.
        var targets = stones.get().points(playerId);
    }
}
```

Провайдер регистрируется через `register(service, modId, implementation)` только
в пространстве имён своего владельца. Повторная регистрация не заменяет чужую.
Закрытие регистрации удаляет именно её; закрытие `IntegrationApi` снимает все
созданные через него регистрации. Ранее полученные ссылки перестают работать
после удаления провайдера, закрытия своего handle или остановки сервера.

Игровые действия и чтение онлайн-контекста выполняются в серверном потоке.
`PERMISSIONS.offlineAllowed` предназначен для ограниченного пула фоновых задач:
он может загрузить пользователя и не должен блокировать игровой tick.
Проверка прав не вызывает доменный API и не зависит от его авторизации.
`Data` содержит копию структурированных значений без зависимости от Gson;
изменение исходных коллекций не изменяет запрос или результат.

Waystones возвращает только доступные конкретному игроку точки при включённой
карте. Специальный слой камней сохраняет штатные текстуры и сетевой формат.
Голосовые провайдеры сохраняют проверку существующего мута перед применением;
при двух установленных голосовых модах Rivet проверяет оба.
Действия `DIAGNOSTICS` и `ACCESS` дополнительно проверяют права онлайн-автора
в соответствующем адаптере. Эти сервисы не открывают новые REST-маршруты.

`IDENTITY` предоставляет разрешение имён и UUID аккаунтов Rivet для интеграций.
Его используют Create: Access Denied и Create: Numismatics. Поиск отображаемого
имени не переносит владение: сохранённые UUID счетов, балансы и пользовательские
названия Numismatics остаются под управлением самого мода.

## В этом разделе

<div class="rivet-topic-grid" markdown>

<div class="rivet-topic-card" markdown>

[Статус и курсоры](java-status.md)

Чтение снимков и восстановление после пропущенных событий.

</div>

<div class="rivet-topic-card" markdown>

[Работа с данными](java-domains.md)

Асинхронные доменные операции и контекст игрока.

</div>

<div class="rivet-topic-card" markdown>

[Подписки Java](java-subscriptions.md)

Получение изменений без периодического опроса.

</div>

<div class="rivet-topic-card" markdown>

[Каталог операций](java-operations.md)

Сервисы задач, профилей, сообщества и администрации.

</div>

<div class="rivet-topic-card" markdown>

[Собственные слои карты](java-map-layers.md)

Добавьте метки и территории своего мода.

</div>

<div class="rivet-topic-card" markdown>

[Управление рендером](java-terrain.md)

Профили, тайлы и задания через Java API.

</div>

</div>
