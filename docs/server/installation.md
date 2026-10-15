# Установка

Для сервера нужны Minecraft 1.21.1, стабильный NeoForge ветки 21.1 от 21.1.250,
Java 21 и PostgreSQL. Клиентам нужен Rivet для меню и входа при включённом Auth.

1. Поместите JAR Rivet в папку `mods/` сервера.
2. Запустите сервер, чтобы создать `config/rivet-server.toml`. Без подключения к базе Rivet остановит запуск.
3. Подготовьте PostgreSQL и заполните раздел `[database]` по инструкции ниже.
4. Полностью перезапустите сервер, чтобы применить подключение.

## PostgreSQL

Создайте базу `rivet` (или используйте своё имя базы и пользователя). Схема Rivet всегда называется `rivet`. Под администратором PostgreSQL подключитесь к ней через `psql` и выполните **один раз**:

```sql
CREATE ROLE rivet LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
\password rivet
GRANT CONNECT ON DATABASE rivet TO rivet;
CREATE SCHEMA rivet AUTHORIZATION rivet;
```

`\password` запросит новый пароль дважды. Отдельный пользователь ограничивает доступ мода его схемой.

В полном серверном конфиге заполните раздел:

```toml
[database]
host = "db.example.org"
port = 5432
database = "rivet"
username = "rivet"
password = 'ПАРОЛЬ_ПОЛЬЗОВАТЕЛЯ'
passwordEnv = ""
pool = 8
sslMode = "disable"
sslCert = ""
```

Пароль в TOML — **в кавычках**. Вместо `password` можно указать `passwordEnv = "RIVET_DB_PASSWORD"` и задать эту переменную процессу Minecraft. Если имя переменной заполнено, пароль берётся только из неё.

**Docker:** используйте Internal Host и порт Internal Port; контейнер Minecraft должен иметь доступ к сети базы. Публиковать порт БД в интернет не требуется. `sslMode = "disable"` подходит для доверенной внутренней сети; для внешнего подключения используйте `"verify-full"` и сертификат в `sslCert`.

> [!IMPORTANT]
> PostgreSQL нужна и при выключенном Auth. При ошибке подключения сервер остановит запуск с объяснением в консоли. Данные Rivet хранятся в БД отдельно от мира.
