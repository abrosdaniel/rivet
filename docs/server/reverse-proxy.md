# HTTPS и WSS через reverse proxy

Rivet слушает HTTP и WebSocket на loopback. TLS завершает reverse proxy на той же
машине. Не публикуйте внутренние порты Rivet. Сайт и его backend разворачиваются
отдельно: постоянный API-ключ хранится только на backend, браузер получает
одноразовый ticket. Разрешённый HTTPS origin сайта укажите в `api.http.origins`.

Пример Nginx внутри секции `http`. Замените домен, сертификаты и порты на свои;
сертификат должен быть доверенным для клиентов и соответствовать имени сервера.
Порты 24481/24482 ниже — пример, они должны совпадать с конфигурацией Rivet.

```nginx
server {
    listen 443 ssl;
    server_name api.example.org;
    ssl_certificate /etc/letsencrypt/live/api.example.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.org/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    client_max_body_size 16k;
    access_log off;

    location = /api/v1/ws {
        proxy_pass http://127.0.0.1:24482;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 75s;
        proxy_send_timeout 15s;
        proxy_buffering off;
        proxy_cache off;
    }
    location /api/v1/ {
        proxy_pass http://127.0.0.1:24481;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
    }
    location / { return 404; }
}
```

Nginx передаёт `Authorization`, `X-Rivet-Session`, `Origin` и
`Sec-WebSocket-Protocol` без подмены. Не добавляйте `Access-Control-Allow-Origin: *`,
не переписывайте Origin и не кэшируйте приватные ответы. Rivet самостоятельно
проверяет Origin, ключ, scopes и сессию; выбранный WebSocket-подпротокол — только
`rivet.v1`, без ticket. Таймаут прокси должен пропускать heartbeat Rivet.

Не отправляйте ключи по HTTP даже при настроенном перенаправлении на HTTPS.
Не журналируйте заголовки авторизации, сессии, WebSocket-протоколы и тела запросов.
Проверьте это также в CDN, WAF, трассировщике и журналах ошибок; не включайте debug
для рабочего трафика. Для публичных тайлов отдельная политика кэша допустима только
после проверки профиля и прав: общий кэш приватных тайлов недопустим.

Rivet видит IP прокси; заголовки `X-Forwarded-For` не заменяют адрес соединения.
Поэтому лимиты по адресу на стороне Rivet общие для этого прокси. Настройте внешний
лимит по IP и согласуйте внутренние квоты с ожидаемой нагрузкой, сохраняя лимиты
API-ключей. Прокси в контейнере требует отдельной настройки доступа к loopback хоста;
приведённый пример рассчитан на процессы на одной машине.

Проверка конфигурации на изолированном стенде: [HTTPS и WSS](../development/api-testing.md#проверка-https-и-wss).
