# Privacy policy / Конфиденциальность

Описание текущего открытого форка LocProxyTV (ранее ProxyDroid) (серия 3.8.x); исходники позволяют проверить перечисленные механизмы. Это не обещание безопасности любых сторонних прокси, прошивок или сайтов.

## Что хранится на устройстве

Профили: host/port/type, app lists, gateway/boot options и введённые credentials находятся в app-private SharedPreferences; пароли пока не дополнительно зашифрованы отдельным Android Keystore key. Boot status хранит результат/время попытки без credentials. APK update download — частичные байты в private cache/update-downloads для докачки; полностью проверенный APK переносится в cache/updates. Пауза/обрыв сохраняет байты; checksum failure удаляет повреждённый partial, Android может очистить кэш.

Manifest запрашивает `allowBackup=false`; перенос/backup производителя может иметь собственную политику. Uninstall удаляет локальные app data по правилам Android. Signing private key не входит в приложение; release certificate — публичный идентификатор, не секрет.

## Сетевые обращения

- Пользовательский IPv4/TCP трафик направляется через настроенный upstream. Оператор прокси может видеть destinations/метаданные и незашифрованный application traffic; HTTPS защищается конечным сервисом/клиентом.
- UDP DNS forwarding использует DoH через proxy к `https://1.1.1.1/dns-query` и `https://8.8.8.8/dns-query`; resolver/proxy получают соответствующие сетевые метаданные и DNS-запросы.
- После соединения HTTPS internet checks через actual upstream обращаются к `www.google.com` / `connectivitycheck.gstatic.com` `/generate_204`. Endpoint видит сетевые метаданные/proxy exit IP; check не передаёт профильные passwords этим hosts. Auth отправляется только настроенному proxy в его handshake.
- По действию пользователя GitHub updater обращается к GitHub API и release/CDN endpoints без встроенного GitHub token. Этот UID исключён из VPN, поэтому GitHub обращения могут идти прямо по физической сети. API может видеть device public IP/User-Agent; учитывайте privacy policy GitHub.
- YouTube открывается только по нажатию после успешного check. Дальнейшие видео/accounts/cookies обрабатывает установленный клиент/браузер по его политике, не LocProxyTV.
- LAN discovery пока **не реализовано** и не сканирует сеть. Будущий поиск требует explicit user action/разрешённого диапазона и описан в roadmap.

## Логи и сторонние SDK

Нет встроенных analytics, advertising или external crash-reporting SDK. Диагностические сообщения пишутся в Android logcat, не автоматически отправляются разработчику. Логи могут содержать endpoints, domains, network errors и сведения об устройстве; native init больше не логирует proxy URL с credentials. Перед любым ручным экспортом удаляйте секреты и личные данные.

## Permissions и безопасность

VPN consent, boot receive, internet/network-state, foreground service и APK-install permission обслуживают описанные функции. Не требуются root или covert installation. APK installer требует Android confirmation; отсутствие VPN consent/доступа не обходится. Invalid/self-signed TLS certificates без доверия не принимаются современным HTTPS/DoH/probe path.

Нет встроенного cookie storage для internet checks/updater. Браузер/YouTube могут использовать свои cookies. SharedPreferences privacy не означает криптографическое шифрование; compromised/rooted device или недоверенный proxy остаются рисками.

Для вопросов/ошибок используйте [issues репозитория](https://github.com/LeonidYasin/proxydroid/issues), но не прикладывайте keystore, passwords, tokens, private backups или необработанные конфиденциальные логи. Изменения политики отслеживаются в Git history.

## Сайт проекта

GitHub Pages размещает статическую документацию. Нет cookies/analytics/трекеров приложения на сайте. Главная делает запрос к публичному GitHub API списка Releases для ссылок APK; GitHub/Pages получает обычные HTTP данные (например IP и user-agent) согласно своей политике. APK скачиваются с GitHub Releases, а не с постороннего зеркала.
