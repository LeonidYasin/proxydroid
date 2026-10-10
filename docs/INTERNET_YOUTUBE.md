# Проверка интернета и предложение YouTube

## Три разных уровня

1. `Utils.working`: TUN создан и native runtime запущен — не доказательство интернета.
2. `isAlive`: upstream TCP port отвечает — не доказательство proxy handshake/выхода в интернет.
3. `internetAvailable`: HTTPS HTTP 204 получен через **снимок фактического upstream активного сервиса** — основание показать кнопку YouTube.

Наш UID исключён из VPN против routing loop. Поэтому прямой `HttpsURLConnection` из UI мог бы проверить обходной интернет, а не прокси. `ProxyTransport` явно выполняет SOCKS5/SOCKS4a/HTTP CONNECT/HTTPS CONNECT с настройками работающего сервиса, затем TLS до check host и GET `/generate_204`. TLS проверяет certificate chain и hostname; HTML/redirect/HTTP 200 не считаются успехом.

Endpoints: `www.google.com`, fallback `connectivitycheck.gstatic.com`. Connect/read timeouts 4 секунды, ограниченные HTTP lines/headers, read deadline; максимум два прохода с короткой паузой. Нет credentials в логах. Snapshot/working guards и отмена UI job не позволяют старому результату показать кнопку после отключения/смены активного upstream. Socket read может завершаться по timeout после coroutine cancellation.

## UX

После подключении: «Checking internet through proxy…» → при успехе «Internet verified through proxy» и **Open YouTube**; при неуспехе сообщение и Retry. Кнопка не возникает только от зелёной TCP-точки. Нет автоматического Activity launch, autoplay или YouTube account access.

Нажатие: установленный `com.google.android.youtube.tv` → `com.google.android.youtube` → HTTPS YouTube URL через доступный handler. Пакетная visibility объявлена в manifest. Если ничего не открывается, показывается ошибка; приложение не устанавливает YouTube само.

## Что проверка не доказывает

Это upstream HTTPS probe, а не весь TUN path конкретного приложения. Если YouTube не входит в allow-list/находится в bypass list, probe всё равно может пройти. Generic UDP/QUIC не поддерживаются. Блокировка Google connectivity endpoints может дать false negative. Политики прокси/сервиса, YouTube версия, авторизация и доступность видео проверяются на приставке отдельно.

Tests: `ProxyTransportTest` (handshakes/auth/remote DNS/header injection), `InternetActionsTest` (gating/click/disconnected), device checklist. TLS-to-public-host probe и запуск реального YouTube не выполняются офлайн fixture tests.
