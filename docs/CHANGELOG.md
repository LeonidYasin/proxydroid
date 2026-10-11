# Изменения

## 3.8.5 — подробная диагностика

- Две независимые проверки: proxy/internet и YouTube через прокси; этапы TCP/auth/CONNECT/TLS/HTTP, коды ошибок и длительность.
- DoH binary query к native resolver IP endpoints, явно отдельный прямой HTTPS-контроль, физическая сеть/Private DNS/MTU и actual routing snapshot.
- Копируемый безопасный отчёт, рекомендации source-vs-TV/cache/reconnect, ограничения TUN/video честно указаны.
- Bounded concurrency/deadlines, cancellation socket registry, stale generation guards; без автоматических сбросов и TLS bypass.

## 3.8.4 — LocProxyTV

- Новое отображаемое имя в TV/phone launcher, шапке, уведомлениях, актуальных инструкциях и сайте.
- Репозиторий `LeonidYasin/proxydroid`, applicationId `org.proxydroid`, signing key, адреса updater/Pages, классы/JNI и технические имена APK не изменены.
- Сохранена атрибуция исходного ProxyDroid; добавлены проверки label и совместимости идентификаторов.

## 3.8.3 — шапка, докачка и подписи

- Фиксированная компактная шапка приложения; область формы ограничена ниже шапки, возвращение к первой карточке не перекрывает заголовок.
- Приватный частичный APK, HTTP Range, bounded retry, пауза/продолжение, скорость и приблизительное оставшееся время; полные файлы повторно проверяются.
- Разделены отсутствующий сертификат и действительно отличающаяся подпись; показаны реальные SHA-256 installed/downloaded, добавлен legacy OEM fallback без обхода защиты.
- Регрессионные unit-тесты докачки/подписей и instrumented проверки шапки. Название/repo/applicationId/ключ не менялись.

## 3.8.2 — фокус YouTube

- Первая успешная HTTPS-проверка активного подключения переводит фокус пульта на Open YouTube и прокручивает кнопку в видимую область.
- Повторная проверка не перехватывает выбранный пользователем фокус; после переподключения предложение фокуса повторяется.
- Добавлены регрессионные тесты connection → YouTube, повторной проверки и переподключения.

## 3.8.1 — обновление и сайт

- Установщик запускается после проверки загрузки из видимой Activity; ACTION_INSTALL_PACKAGE с APK VIEW fallback.
- Полный кликабельный путь готового APK в диалоге, повторная установка без скачки; resume после разрешения неизвестного источника.
- Повторная проверка cached size/SHA-256/package/ABI/certificate перед каждой попыткой; понятные сообщения.
- GitHub Pages: главная, актуальные Releases, все документы и интерактивная карта; генерация из единого Markdown source.
- Добавлены instrumented тесты APK path и intent grants. Реальная установка на OEM TV требует отдельной проверки.


## 3.8.0 — gateway, boot и YouTube (RC)

- HTTPS интернет через actual upstream + предложение YouTube после успеха.
- Gateway lookup startup/resume/physical network callback; Host write только auto.
- Boot foreground path, no background consent Activity, bounded network/proxy wait, boot diagnostics и duplicate guard.
- Connectivity auto-connect перестал отправлять неподдерживаемый action в boot receiver; legacy broadcast delivery всё ещё ограничен платформой.
- Native TLS validation, DoH upstream refresh, устранение credential URL log; backup opt-out.
- Unit/instrumented регрессии; README, архитектура, privacy, mind map, roadmap discovery и документация эксплуатации.

## 3.7.3 — tested RC `g5ed408c32743`

Gateway Host при переключении; endpoint сразу после gateway; DPAD initial focus; click editor FocusRequester fix; signed Stable/Test updater; separate compact ARM32 and universal. Blocking fixture/UI gate перед публикацией.

## 3.7.2 — signed TV RC

Default port 10808, Leanback launcher/banner, click-to-edit, VPN app-list/auth/FGS fixes, постоянная release signing identity, информативные имена/sidecars.

Более ранняя история — в Git history и архивной SESSION-2026-09-30. Этот changelog не утверждает, что все изменения уже merged в main.
