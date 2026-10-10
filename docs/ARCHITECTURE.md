# Архитектура ProxyDroid

## UI/control plane

`ProxyDroid` (ComponentActivity) → `MainViewModel` / `MainScreen` / `EditableSettingField`. StateFlow отражает `Utils.working`, `connecting`, ошибки. `Profile` читает app-private SharedPreferences: flat текущие значения плюс JSON по ID. Профильные UI writes сериализованы на Main, `apply()` пишет диск асинхронно; startup/resume не публикуют устаревший IO snapshot.

Gateway: ConnectivityManager callbacks + LinkProperties → `refreshGateway` → отображение IPv4 и Host только при auto. Учитываются request generation/profile ID; callbacks снимаются в onCleared.

## VPN/data plane

`ProxyController` / VPN consent → foreground `ProxyDroidVpnService` → VpnService.Builder (MTU 1500, TUN 10.0.0.1/24, IPv4 default route, DNS 10.0.0.2) → `Tun2SocksHelper` JNI → `lib.rs` → `tun2socks.rs` / Tokio / netstack-smoltcp → SOCKS5/SOCKS4/HTTP(S) CONNECT upstream.

Builder выбирает allow-list ИЛИ disallow-list; текущий app UID исключается. Rust `protect.rs` поддерживает защиту upstream sockets. IPv4 TCP frames идут через proxy; UDP/53 перехватывается и `doh_client.rs` отправляет DoH через тот же proxy. `dns_table.rs` поддерживает сопоставление имён/виртуальных адресов. General UDP и полноценный IPv6 не включены.

`Utils.activeUpstream` — неизменяемый снимок конфигурации реально запущенного сервиса; credentials не выводятся в toString. UI может редактировать следующий профиль, не меняя текущий runtime. `InternetProbe` использует active snapshot, не случайный текущий Host из формы.

## Lifecycle

Boot receiver читает opt-in только после unlock и не открывает background Activity. Foreground сервис ждёт physical network/gateway/proxy до 120 секунд; duplicate starts idempotent; explicit Stop отменяет wait/native/TUN. BootStatus хранит объяснение последней попытки без паролей. Legacy ConnectivityBroadcastReceiver остаётся для Wi-Fi автоподключения, но manifest CONNECTIVITY_CHANGE ограничен Android 7+; полного гарантированного Wi-Fi automation нет.

## Updates/release

`ReleaseCatalog` фильтрует published channel/ABI/metadata; `GitHubUpdater` скачивает в cache/updates и проверяет actual APK, size/hash/version/package/minSDK/JNI/certificate against installed app; `UpdateFileProvider` выдаёт узкий content URI системному установщику. Подтверждение/unknown sources — Android UI.

`build_metadata.py` связывает имена APK/artifacts/assets и version/ABI/run/attempt/commit. Release workflow использует постоянный signer, проверяет сертификат/zipalign/non-debuggable/manifest/ABI, ждёт successful exact-commit Android run и только затем публикует draft с обоими APK. Keystore отсутствует в app/release assets.

## Вспомогательные модули

AppManager / BypassListActivity — выбор приложений/исключений; widget — старт/стоп с consent path; `Constraints` и `ListPreferenceMultiSelect` — legacy настройки; DrawablePainter/Theme — оформление; Utils — runtime state / app icons. Старые root/iptables команды удалены.

## Security boundaries

Ни LAN discovery, ни remote management API сейчас не реализованы. TLS native HTTPS использует WebPKI roots, DoH reqwest defaults, Java probe platform trust + endpoint verification. Invalid/self-signed cert без доверия отклоняется. DoH client пересоздаётся на новый upstream вместо first-config OnceLock. Android storage приватно, но credentials пока не дополнительно зашифрованы; backup opt-out в manifest. Любой будущий export обязан редактировать secrets.

## Known debt / test scope

Legacy NTLM/PAC/DNS toggles не соответствуют полноценной реализации всех старых режимов; profile rename/delete bookkeeping требует дополнительных тестов. TCP check ≠ internet check ≠ TUN end-to-end. Android always-on, OEM boot и actual installer требуют device QA. Нельзя трактовать зелёные unit/fixture tests как отсутствие всех дефектов.

## Updater / сайт 3.8.1

UpdateViewModel pendingInstall → resumed Activity → IO verifyReady → APK content URI → ACTION_INSTALL_PACKAGE / VIEW fallback. Permission return возобновляет установку; full path action повторяет её без скачки. Подробнее [обновления](APP_UPDATES.md).

`scripts/build_site.py` строит GitHub Pages из canonical Markdown, а mind map копируется в интерактивную страницу. Публикация не имеет доступа к signing secrets. Подробнее [сайт](GITHUB_PAGES.md).
