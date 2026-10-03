# ProxyDroid

Глобальный и по-приложениям прокси для Android **без root**. Форк [madeye/proxydroid](https://github.com/madeye/proxydroid), переписанный на VPN-first архитектуру: `VpnService` + tun2socks на Rust, интерфейс на Jetpack Compose.

*Global / per-app proxy for Android, no root required. A fork of madeye/proxydroid rebuilt around `VpnService` and a Rust tun2socks core.*

## Возможности

- Типы прокси: `socks5`, `socks4`, `http`, `https`; аутентификация логин/пароль.
- Несколько профилей; прокси для выбранных приложений или для всех, кроме выбранных; список обхода адресов.
- **Use gateway as host** — шлюз текущей сети используется как адрес прокси (удобно, когда один телефон раздаёт прокси по Wi-Fi-хотспоту; см. [AUTO_GATEWAY.md](AUTO_GATEWAY.md)).
- Индикатор живости прокси с автоопросом каждые 15 секунд.
- **Start on boot** — автозапуск после перезагрузки (см. ниже).

## Автозапуск после перезагрузки

1. Один раз запустите прокси вручную и подтвердите системный диалог VPN.
2. В профиле включите **Start on boot**.
3. После загрузки приложение дождётся, пока прокси станет доступен (до 3 минут: Wi-Fi и шлюз поднимаются не сразу), и поднимет туннель. Если прокси так и не ответил, причина будет видна в приложении.
4. Если системное согласие на VPN было отозвано, придёт уведомление «Tap to allow…» (на Android 13+ нужно разрешение на уведомления).

Надёжнее всего дополнительно включить системный **Always-on VPN** (Настройки → Сеть → VPN → ⚙ рядом с ProxyDroid): Android сам перезапускает сервис, профиль берётся из сохранённых настроек.

На части прошивок (MIUI, EMUI, OneUI и др.) нужно вручную разрешить автозапуск и отключить оптимизацию батареи для приложения.

Диагностика:

```
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p org.proxydroid
adb logcat | grep -E "ProxyDroid|ProxyController"
```

## Сборка

```
./gradlew assembleDebug
```

Нужны Android SDK и Rust-тулчейн с Android-таргетами (см. `app/src/main/rust` и workflow'ы в `.github/workflows`).

## Релизы и планы

Процесс релизов и план работ — в [ROADMAP.md](ROADMAP.md). Лицензия — GPLv3 (как у оригинального проекта).
