# Android TV / качество / QA

Актуальный обзор исходников **3.8.0**, не историческая оценка первоначального PR. Проект пригоден для практического тестирования TCP/VPN на TV, но не для утверждения «весь UDP/IPv6 и все OEM гарантированно поддерживаются». Репозиторий имеет регрессионные тесты; полнота device/TUN coverage остаётся ограниченной.

## Исправлено

- Leanback/обычный launcher, TV banner, optional touchscreen, default port 10808.
- Connection → gateway → Host/Port; начальный DPAD focus, explicit click editor, dialog FocusRequester внутри subcomposition.
- Gateway при старте/resume/network changes; сохранение Host в auto-режиме, request/profile guards.
- Foreground boot start без background consent Activity; waiting network/proxy до 120 секунд, статус и duplicate-start guard.
- Раздельные TCP и HTTPS upstream checks; YouTube button только после internet success, явное открытие.
- App-list API не смешиваются; disabled auth не передаётся native; FGS type API34+.
- Signed release, compact ARM32, actual APK metadata/name/checksum/cert validation, Stable/Test updater.
- Blocking emulator tests; fixture proxies/local HTTP target; release publication exact-commit gate.
- Native DoH refresh при смене upstream; TLS trust-all удалён; credential-bearing proxy URL убран из init log.

## Автоматические проверки

| Проверка | Покрытие | Не доказывает |
|---|---|---|
| check_tv_contract.py | manifest/order/startup/YouTube/TLS static guards | Runtime/реальный TV |
| build_metadata Python tests | version/name/ABI/manifest packaging contracts | Подпись устройства |
| JVM tests | Profile defaults, validation, NetworkUtils helpers, ReleaseCatalog, BootPolicy, ProxyTransport | Полный TUN или OEM boot |
| Compose/instrumented | DPAD/edit, initial focus, YouTube success/failure/disconnect gating, gateway startup/manual and boot receiver opt-in | Реальный cold reboot/YouTube app |
| Local proxy fixtures | SOCKS5 / HTTP CONNECT no-auth/auth handshakes и HTTP request | Production servers / UDP / VPN route |
| Signed workflow | assembleRelease, signer pin, zipalign, non-debuggable, version/package/SDK/JNI, two exported ABIs | OEM installer или runtime |

CI результаты смотрите в Actions для **того же коммита**, который указан в release sidecars. Этот документ описывает tests/contracts, а не подменяет отчёт фактического запуска.

## Device checklist (не отмечать выполненным автоматически)

- [ ] TV launcher entry и banner; DPAD initial connection; наведение не вызывает IME; OK/Cancel edits.
- [ ] Auto gateway startup ON — свежий Host; OFF — manual Host; Wi-Fi/Ethernet changes и отсутствие route.
- [ ] Proxy + VPN + интернет: SOCKS/HTTP auth success/failure, internet probe negative, YouTube button, app allow-list и реальный видео playback.
- [ ] Cold boot с заранее выданным consent и opt-in; delayed network; timeout; revoked consent; Force stop; OEM restrictions; suspend/wake.
- [ ] Stop во время ожидания; повторный boot/update event; новый профиль/upstream после disconnect.
- [ ] Signed update поверх прежней версии, ARM32/universal compatibility, unknown-source approval, actual installer confirmation, settings retained.
- [ ] Invalid HTTPS/DoH cert отказ; никаких credentials в диагностическом экспорте.

## Ограничения / оставшийся долг

General UDP/QUIC и полный IPv6 не реализованы; TLS tightening может перестать принимать self-signed HTTPS proxies. Проверка Google HTTPS endpoints может дать false negative при их блокировке. Current probe идёт через upstream, не весь TUN. Несколько физических сетей, Always-on/lockdown, Wi-Fi SSID automation, legacy NTLM/PAC и профильный bookkeeping требуют отдельных работ. Не обещать OEM autostart, если прошивка блокирует приложение.

Рекомендация: проверять каждый RC на целевой приставке; не мержить PR автоматически только из-за зелёного CI.

## Updater 3.8.1 device checklist

- Проверить окончание скачки → installer; deny/allow unknown sources → возврат; отмена установки → повтор по полному пути; удалённый cache → ошибка; длинный путь + DPAD; отсутствие OEM installer → сообщение. APK остаётся приватным, FileProvider grant ограничен.
- Instrumented: DownloadedApkLinkTest / ApkInstallIntentsTest проверяют UI/action/MIME/URI/grants, не фактическую OEM установку.
