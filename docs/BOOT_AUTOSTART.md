# Start on boot — поведение и диагностика

## Причины исправления

Ранее `ProxyDroidReceiver` вызывал путь с обычным `Context.startService`, который может быть запрещён из фона Android 8+. При отсутствии VPN consent путь пытался открыть Activity из background. Если сеть ещё не получила route/proxy недоступен, сервис мог сразу завершиться. Wi-Fi auto-connect ошибочно пересылал CONNECTIVITY_CHANGE в boot receiver, который игнорировал этот action.

## Новый поток

`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / поддерживаемый OEM quickboot → `Profile.autoStartOnBoot` → проверка разблокировки и существующего VPN consent → `ContextCompat.startForegroundService` → немедленный `startForeground` → IO ожидание physical network, свежего gateway при auto и доступного proxy TCP port → TUN/native start. Максимум ожидания — 120 секунд. Повторное событие не пересоздаёт активный/ожидающий VPN; Stop отменяет ожидающую coroutine.

Foreground promotion выполняется до ожидания сети, чтобы не нарушить системный deadline. Special-use type применяется только API 34+; соответствующие permissions заданы. Null/system service restart восстанавливает сохранённый профиль вместо безусловного stopSelf; системный Always-on всё равно требует отдельного device QA.

Согласие не выдаётся автоматически. Если его нет, boot-попытка сохраняет объяснение и не открывает экран из фона. Настройки/credentials остаются credential-encrypted app storage: LOCKED_BOOT_COMPLETED не обрабатывается. Android boot после unlock — основной контракт.

## Подготовка владельцем

1. Открыть приложение после установки; успешно подключиться с системным VPN consent.
2. В выбранном профиле включить **Start on boot**. Проверить Host/10808/авторизацию или Auto gateway.
3. Если прошивка имеет список разрешённого автозапуска/ограничение батареи — разрешить работу LocProxyTV, не отключая глобальные механизмы безопасности.
4. Перезагрузить приставку, дождаться сети. В Startup посмотреть сохранённый статус последней попытки.

Force stop блокирует последующие boot broadcasts до ручного запуска приложения. Просто уход из UI не равен Force stop. Если согласие отозвано, другой VPN занял слот или OEM блокирует service — приложение не может обойти это ограничение.

## Проверки

Unit: `BootPolicyTest` — action, opt-in, unlock, отказ unrelated intents. Instrumented: `GatewayStartupTest` — начальная Host/ручной Host, вызов boot starter только при enabled. Это **не** тест реального reboot или полного boot→VPN→internet пути.

На устройстве: холодная загрузка; сеть через 30/90 секунд; сеть отсутствует >120 секунд; auto on/off; revoked consent; Force stop; duplicate BOOT/update; Stop во время ожидания; standby/quickboot производителя; сохранение Host и корректный upstream после restart. Отдельно проверить Android Always-on и не включать lockdown без проверки исключённого UID.

Диагностика: статус в Startup, `adb logcat` с тегами ProxyDroidReceiver / ProxyDroidVpn / ProxyDroid. Не публикуйте необработанные логи: они могут содержать адреса/домены и данные устройства.

Android platform references: [background FGS restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [VPN guide](https://developer.android.com/develop/connectivity/vpn).
