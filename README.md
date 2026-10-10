# ProxyDroid — Android TV / Phone

VPN-клиент, направляющий IPv4/TCP-трафик выбранных приложений через ваш SOCKS/HTTP-прокси. Root не требуется. Интерфейс Jetpack Compose поддерживает пульт/DPAD и телефоны.

> Разрабатываемая версия ветки: **3.8.0**. Это не означает, что изменения уже в `main` или стабильном релизе. Готовые APK и точный коммит смотрите в [Releases](https://github.com/LeonidYasin/proxydroid/releases); изменения проходят [PR #12](https://github.com/LeonidYasin/proxydroid/pull/12). Статус CI: [Actions](https://github.com/LeonidYasin/proxydroid/actions).

## Быстрый старт

1. Установите подписанный APK: `armeabi-v7a` — компактный ARM32, `universal` — ARM32/ARM64/x86/x86_64. Минимум Android 7.0 / API 24. Если поддержка ARM32 неизвестна — выбирайте universal. Обе версии отображаются в обычном TV launcher.
2. Откройте приложение. Пульт изначально сфокусирован на подключении. Для изменения Host/Port нажмите кнопку поля / DPAD OK: наведение не открывает клавиатуру.
3. Укажите прокси, тип, порт (по умолчанию **10808**) и при необходимости авторизацию. Прокси должен существовать и быть доступен; приложение само не создаёт сервер на шлюзе.
4. Или включите **Auto gateway**: физический IPv4 default gateway определяется при запуске, возврате в приложение, смене сети и включении опции. Если опция включена, адрес записывается в Host; иначе ручной Host сохраняется.
5. Подключитесь и подтвердите системное согласие VPN. Не включайте блокировку всего трафика без VPN: собственный UID клиента исключён из туннеля для соединения с upstream.
6. После установки туннеля запускается HTTPS-проверка интернета **через фактический upstream-прокси**. При успехе появляется **Open YouTube**. Нажатие открывает TV YouTube, мобильный YouTube или веб-страницу. Автоматического открытия/автовоспроизведения нет.

Порядок верхних строк: **подключение → авто-гейтвей → Host/Port → профиль**. Статусы «туннель создан», «TCP-порт доступен» и «интернет через прокси проверен» — разные проверки, не одно и то же.

## Возможности и границы

| Возможность | Реализация |
|---|---|
| Прокси | SOCKS5 + user/password, SOCKS4/SOCKS4a, HTTP CONNECT + Basic, HTTPS CONNECT |
| Туннель | Android VpnService → JNI → Rust/Tokio → smoltcp TCP stack |
| DNS | Перехват UDP/53, DoH через тот же прокси; IPv4 mapping для TUN |
| Приложения | Все приложения либо allow-list / bypass-list, без смешивания двух Android API |
| ТВ | Leanback launcher/banner, optional touchscreen, DPAD, явное редактирование |
| Профили | Локальные SharedPreferences, выбор/создание/переименование/удаление |
| Живость | TCP-проверка примерно каждые 15 секунд во время работы |
| Интернет | TLS + ответ HTTP 204 от Google connectivity endpoints через upstream, fallback и ограниченные таймауты |
| Загрузка приставки | Opt-in Start on boot, foreground start после разблокировки, ожидание сети/прокси до 120 секунд |
| Обновление | GitHub Stable/Test, проверка размера/SHA-256/пакета/ABI/версии/подписи, системный установщик |
| Дистрибутивы | Отдельные подписанные universal и armeabi-v7a, информативные имена и sidecars |
| LAN discovery | **Только roadmap**, не реализовано и не запускается автоматически |

**Ограничения:** общий UDP/QUIC и полноценный IPv6-туннель не реализованы. YouTube может использовать TCP fallback, но гарантии для всех версий/прошивок нет. Legacy-поля NTLM/PAC/SSID — не гарантия полноценной поддержки этих режимов современным Rust-транспортом. Проверка upstream не доказывает весь путь конкретного приложения через TUN и не гарантирует доступность YouTube; для allow-list добавьте YouTube в список. Если Google check endpoints блокируются, возможен отрицательный результат даже при доступности других сайтов.

## Автостарт после включения приставки

Сначала один раз успешно подключитесь и выдайте согласие VPN, затем включите **Start on boot** для выбранного профиля. При BOOT_COMPLETED / MY_PACKAGE_REPLACED обработчик не запускает Activity и не запрашивает согласие из фона: он стартует foreground VPN, ждёт физическую сеть/прокси и обновляет gateway Host. Состояние попытки отображается в **Startup**.

Автостарт не работает до первого открытия после установки или после системного Force stop, при отозванном VPN-согласии и может блокироваться энергосбережением/автозапуском производителя. После Force stop откройте приложение вручную. Секреты не переносятся в Direct Boot storage; до разблокировки подключение не обещается. Если сеть появляется позже двух минут — подключитесь вручную. [Подробная диагностика](docs/BOOT_AUTOSTART.md).

## Обновления

**⋮ → Update app → Stable / Test → Check latest → Download APK → Install**. Stable выбирает опубликованные non-prerelease signed releases; Test — prerelease. Если подходящий signed stable не опубликован, канал честно сообщает отсутствие кандидата. Старые debug/unsigned релизы не предлагаются. На ARM32-совместимом устройстве предпочтителен меньший armeabi-v7a APK.

Android подтверждает установку. Если запросит разрешение установки из ProxyDroid — разрешите, вернитесь и снова нажмите Install. Тот же ключ позволяет обновляться поверх release-signed APK; миграция с старой debug-подписи требует сохранения настроек и переустановки. [Подпись и релизы](docs/RELEASE_SIGNING.md).

## Сборка и тестирование

JDK **17**, Gradle wrapper **8.4**, Android SDK/compile/target **36**, min **24**, NDK **25.1.8937393**, CMake **3.22.1**, Rust stable с Android targets. AGP **8.1.2**, Kotlin **1.9.10**, Compose compiler **1.5.3** — совместимая зафиксированная связка. SDK/NDK пути задаются локально, не коммитятся.

```bash
python3 scripts/check_tv_contract.py
python3 -m unittest discover -s scripts -p test_build_metadata.py
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest
# Уже запущенный API 30+ emulator:
bash scripts/run_emulator_tests.sh
```

Скрипт эмулятора сам запускает локальные SOCKS5/HTTP CONNECT тестовые прокси и HTTP target; внешняя сеть для этих тестов не нужна. Он проверяет протоколы и UI, но не является полным тестом TUN, реальной перезагрузки/OEM или установки обновления на вашей приставке. [Матрица QA](docs/ANDROID_TV_REVIEW.md).

Release требует постоянного keystore и секретов окружения; отсутствие ключа приводит к ошибке, а не к debug/unsigned fallback. APK появляются в `app/build/outputs/apk/release/app-{universal|armeabi-v7a}-release.apk`, CI переименовывает их по фактическим metadata. Подробности — [release guide](docs/RELEASE_SIGNING.md).

## Документация

- [Полная карта проекта / mind map](docs/MINDMAP.md) · [интерактивная HTML-карта](docs/MINDMAP.html)
- [Архитектура и потоки](docs/ARCHITECTURE.md)
- [Roadmap и LAN discovery](ROADMAP.md)
- [Auto gateway](AUTO_GATEWAY.md)
- [Boot autostart](docs/BOOT_AUTOSTART.md)
- [Проверка интернета / YouTube](docs/INTERNET_YOUTUBE.md)
- [TV review и тестирование](docs/ANDROID_TV_REVIEW.md)
- [Подпись / release workflow](docs/RELEASE_SIGNING.md)
- [Изменения](docs/CHANGELOG.md) · [Privacy policy](privacy_policy.md)
- [Историческая сессия 2026-09-30](docs/SESSION-2026-09-30.md) — архив, не актуальный release guide.

## Безопасность и лицензия

VPN-согласие и установка APK остаются под контролем Android и пользователя. Интернет-проверки проверяют цепочку/имя TLS; Rust HTTPS/DoH больше не отключают проверку сертификатов. DNS-клиент пересоздаётся для текущего upstream; URL с учётными данными не выводится в init log. Самоподписанные HTTPS-прокси без доверенного сертификата не поддерживаются; импорт custom CA — отдельный план.

Настройки/пароли хранятся в app-private SharedPreferences (не дополнительное шифрование через Keystore). Manifest запрашивает отключение backup; поведение переноса данных у OEM может отличаться. Нет analytics/ads SDK; сетевые обращения описаны в privacy policy. **Никогда не коммитьте keystore, пароли, Base64 ключа или private backup.** Экспорт signing backup — отдельное действие с явным разрешением владельца, не часть выпуска APK.

Унаследованные файлы проекта имеют лицензионные заголовки **GPL v3 or later**; при распространении соблюдайте их условия. Исходный ProxyDroid — Max Lv, текущий Android TV/VPN-first форк — этот репозиторий.
