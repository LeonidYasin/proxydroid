# Roadmap / План развития

Это план текущей ветки, а не обещание наличия каждой функции в `main` или APK. Версия source: **3.8.1**. Актуальные публикации: [Releases](https://github.com/LeonidYasin/proxydroid/releases). Полная структура: [mind map](docs/MINDMAP.md).

## Уже реализовано в исходниках

- VpnService/JNI/Rust TCP proxy, DNS через DoH, профили и app allow/bypass lists.
- TV launcher/banner, DPAD, click-to-edit, default port 10808, gateway/Host layout.
- Updater 3.8.1: installer из visible Activity, повтор по полному пути APK, resume после разрешения источника.
- GitHub Pages с едиными source docs и интерактивной картой.
- Signed universal + armeabi-v7a; Stable/Test updater с проверкой реального APK и подписи.
- Определение gateway при старте/resume/смене сети, сохранение Host только в auto-режиме.
- Foreground boot start без фонового Activity, ожидание сети/прокси до 120 секунд, boot status.
- HTTPS internet check через upstream и предложение открыть YouTube по нажатию.
- Проверка TLS вместо trust-all, обновление DoH клиента при смене upstream, устранение auth URL из init logs.
- Blocking emulator UI/protocol regression gate перед публикацией tagged release.

**Не считать подтверждённым без проверки:** реальный OEM boot, suspend/wake, YouTube на конкретной прошивке, системный установщик обновлений. Тестовая матрица находится в документации; положительный TCP probe не доказывает интернет или весь TUN path.

## Приоритет 0 — довести эксплуатацию 3.8

- [ ] Device QA: холодная загрузка TV с VPN-согласием, задержанная Wi-Fi/Ethernet, сохранение Host, отрицательные boot scenarios, YouTube и обновление APK.
- [ ] Диагностика OEM ограничений без root/обхода безопасности; системный Always-on VPN как отдельный проверенный сценарий.
- [ ] Добавить end-to-end TUN/DNS/HTTPS и reboot instrumentation; текущие host-proxy тесты проверяют handshakes, а не весь VPN.
- [ ] Ограничить/редактировать сетевые логи и добавить безопасный opt-in диагностический экспорт без секретов.

## Приоритет 1 — обнаружение прокси в LAN (план, не реализовано)

### Пользовательский сценарий

**⋮ → Найти прокси в локальной сети → обзор границ/портов → Начать → результаты → карточка → Добавить профиль / Подключиться**.

Сканирование запускается только явно, в сети, которую пользователь вправе проверять. Ничего не сканируется при открытии приложения/boot. Не использовать обнаруженный сервер автоматически и не отправлять ему сохранённые credentials без отдельного подтверждения.

### MVP поиска

1. Через ConnectivityManager/LinkProperties определить физическую сеть, локальный IPv4/CIDR, gateway; VPN не сканировать. Не требовать Android `VALIDATED`: интернет может работать только через прокси.
2. Сначала gateway и явно заданные IP. Затем, с подтверждением, ограниченный private LAN диапазон (по умолчанию максимум 256 адресов); широкие /16 не перечислять автоматически. IPv6 /64 не перебирать.
3. Основной порт **10808**; дополнительно выбранные пользователем 1080/8080/3128 и ручной список. Не сканировать весь TCP port space.
4. TCP connect с коротким таймаутом, максимум 16 одновременных задач, прогресс/Stop, общий budget. Смена сети отменяет поиск и помечает результаты другой сети.
5. Проверять протокол, а не только открытый порт: SOCKS5 greeting / SOCKS4a / HTTP CONNECT. HTTPS — только с проверкой сертификата. «Порт открыт» без подтверждения протокола остаётся неподтверждённым кандидатом.
6. Фиксировать выбранный метод auth / требование credentials, но не угадывать полный набор методов из одного ответа. Никакого brute force, словарей, default passwords или попыток NTLM login.
7. HTTPS интернет-проверка — отдельный согласованный шаг, особенно если прокси требует credentials. HTML captive portal не считать успехом. Remote DNS и TCP проверяются отдельно; generic UDP/QUIC не обещать.

### Полная карточка найденного прокси

| Поле | Источник / смысл |
|---|---|
| Имя / alias | Ручная метка; advertised hostname только если доступен и подтверждён |
| Host/IP и port | Фактический endpoint |
| Протокол / TLS | Подтверждённый либо кандидат; сертификат/имя/expiry, если удалось получить |
| Auth | Не требуется / требуется / неизвестно; только наблюдаемый метод, без пароля |
| Доступность / latency | TCP RTT, handshake RTT, время и результат последней проверки |
| Интернет / DNS | Отдельные результаты, check endpoint и ограничения проверки |
| Сеть | Local IP/CIDR, gateway, transport Wi-Fi/Ethernet; SSID/BSSID лишь при доступности и разрешениях |
| История | firstSeen, lastSeen, scan ID, online/stale/error; не путать старый результат с текущим |
| Происхождение | manual / gateway / subnet scan / future mDNS; ограничения доверия |
| Связь с профилем | ID сохранённого профиля, принадлежность текущей сети, дубликаты |
| Ограничения | Неизвестно ≠ отсутствует: MAC/vendor/владелец сервера часто недоступны Android-приложению |

Нельзя обещать узнать «всё о сервере»: MAC, ОС, владелец и поддерживаемые режимы могут остаться «неизвестно». PTR / Server header — подсказки, не доказательства.

### Хранение и подключение

Модель `DiscoveredProxy`: стабильный ID по network identity + host + port + protocol; статус, источники, timestamps и typed probe results. Метаданные поиска хранить отдельно от credentials профиля. Дедупликация, TTL и фильтры «эта сеть / проверенные / auth required / stale». Для будущего каталога рассмотреть Room; не переписывать текущие SharedPreferences без миграции.

Действия: посмотреть детали, повторить проверку, переименовать, скрыть/удалить, **добавить в профили**, **выбрать и подключиться**. Подключение использует существующий Profile/ProxyController/VPN consent. Auth вводится пользователем, не берётся с другого профиля автоматически. Сохранить несколько найденных endpoint; не заменять текущий профиль без подтверждения. На ТВ вся навигация и активация — DPAD/click, без редактирования по фокусу.

### Acceptance criteria / тесты

- [ ] Найдены локальные SOCKS5 no-auth/auth-required, SOCKS4a, HTTP CONNECT, trusted HTTPS fixtures.
- [ ] Обычный HTTP сервер/открытый TCP порт/captive portal не помечаются как рабочий прокси.
- [ ] Invalid TLS cert отклоняется; пароль не присутствует в таблице, логах, crash output или discovery export.
- [ ] Таймауты, остановка, дедупликация, исчезновение узла, смена сети, denied permissions и `/16` budget покрыты.
- [ ] Список содержит несколько результатов с корректной полной карточкой; добавление профиля и DPAD-подключение проверены.
- [ ] Нет обращения за пределы подтверждённого LAN диапазона и нет сканирования без действия пользователя.

## Приоритет 2 — поддержка каталога и безопасный recovery

- [ ] Хранилище найденных прокси, миграции, фильтры, безопасный JSON export без credentials.
- [ ] Opt-in повторный поиск/переподключение при потере связи, backoff и лимит попыток.
- [ ] Автовыбор только явно доверенных/закреплённых пользователем прокси в соответствующей сети.
- [ ] Keystore-backed защита credentials и управление их удалением.
- [ ] Explicit custom CA / certificate pinning для HTTPS-прокси; никогда не возвращать trust-all.

## Приоритет 3 — транспорт, UX, развитие

- [ ] IPv6 и общий UDP/SOCKS5 UDP ASSOCIATE / QUIC: отдельный проект, не считать текущей поддержкой.
- [ ] Удалить/ясно маркировать legacy NTLM/PAC/SSID controls либо реализовать их end-to-end.
- [ ] Локализация RU/EN, оптимизация размера APK/минификации с проверкой JNI.
- [ ] Улучшить профильные имена/удаление bookkeeping, покрыть список профилей и multi-network routing.
- [ ] Автобамп по explicit PR classification (major/minor/patch) с защитой от конфликтов; пока bump version.properties вручную.

## Release flow — актуальный

Версия из `version.properties`, `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Feature → minor/reset patch; fix → patch; breaking → major. PR не означает merge в main.

1. Изменения и тесты в ветке → PR. `android-build.yml`: build, unit, lint, **blocking** emulator with local fixtures.
2. `publish-rc` label на собственном PR / manual prerelease workflow создаёт signed **artifacts**, не rolling release и не перезапись старых APK.
3. Для опубликованного тестового релиза — уникальный annotated tag `vVERSION-rc.gSHA12`; release workflow проверяет точный commit, подпись, два ABI, metadata и успешный Android run.
4. После проверки владельцем merge в main с bump version → auto-tag; workflow явно dispatch release (не полагается на рекурсивный GITHUB_TOKEN tag trigger).
5. Релиз сначала draft, публикуется после загрузки всех assets. Existing tag/release binary не перезаписывается. Keystore backup/export не является частью этого flow.

История прежних PR и rolling workflow — в архивной сессии; она не заменяет текущий код.
