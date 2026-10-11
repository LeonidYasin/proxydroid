# Диагностика прокси, интернета и YouTube — LocProxyTV

## Почему «порт живой» недостаточно

1. `Utils.working` означает, что TUN создан и native запуск вернул успех. Это не мониторинг текущего forwarding.
2. TCP liveness означает, что на Host/Port кто-то принимает TCP. Это не проверка протокола, пароля, DNS или выхода.
3. CONNECT/auth подтверждают лишь прохождение proxy handshake к конкретной цели.
4. Проверенный TLS и ожидаемый HTTP-код подтверждают HTTPS этой цели через upstream.
5. DoH ответ подтверждает доступность конкретного DNS-over-HTTPS сервера/запроса через Java диагностический транспорт; это ещё не полный native DNS/TUN путь другого приложения.

## Две кнопки / 3.8.5

**Проверить прокси / интернет**: Google и Cloudflare `/generate_204`, затем бинарный `example.com A` DNS-message POST к `https://1.1.1.1/dns-query` и `https://8.8.8.8/dns-query`. Эти IP/POST/MIME соответствуют native DoH endpoints. DNS ответ проверяется по ID, QR/flags, question, RCODE и наличию целого IN A answer record. HTTP 200 с HTML не является успешным DoH.

**Проверить YouTube через прокси**: `www.youtube.com/` и фиксированная публичная миниатюра `i.ytimg.com/vi/jNQXAC9IVRw/default.jpg`, затем те же DoH endpoints. Для YouTube homepage 200 или 301–308 означает лишь полученный ответ/redirect; redirect не открывается автоматически и не доказывает конечную страницу/видео. Thumbnail ожидает 200. Контент сайта/миниатюры не сохраняется и не анализируется; может быть captcha/consent page даже при 200.

Оба теста включают **отдельные прямые HTTPS-контроли без прокси** к Google/Cloudflare через выбранную physical network. Они явно помечены DIRECT, не служат fallback для успешного результата прокси. Успех лишь прямого запроса или только DoH не включает `internetAvailable`.

Проверки доступны с выключенным VPN: используется configured profile, TUN не включается. С включённым VPN используется `Utils.activeUpstream`, а не новая отредактированная форма. Отчёт предупреждает о различии. Proxy host DNS и сокеты диагностики привязаны к выбранной non-VPN physical network, предпочитается default; используется первый IPv4 адрес, иначе первый доступный адрес. Проверка не реализует Happy Eyeballs/полный перебор DNS адресов. Native protected socket может выбрать иную physical route на multi-network устройстве, это ограничение нужно учитывать.

## Что видно в отчёте

- Версия/build/SHA, Android/model, время проверки и системный часовой пояс.
- Физическая сеть, transport/interface, адреса/шлюз/DNS/MTU, Android VALIDATED/CAPTIVE_PORTAL, Private DNS flag. VALIDATED=false не доказывает отсутствие интернета через прокси.
- Фактически применённая allow-list или exclusions и включён ли установленный YouTube TV/phone в этот snapshot. Это не чтение текущей формы и не доказательство здоровья native runtime.
- Для каждой цели: локальное разрешение proxy host, TCP, TLS прокси при HTTPS CONNECT, auth/protocol, CONNECT с SOCKS REP или HTTP status, TLS цели и HTTP status, DoH reply — с временем этапов и безопасной причиной.
- HTTP CONNECT 407 отличается от destination HTTP 403/429. SOCKS5 REP=4 означает host unreachable; DNS vs remote route по этому коду не различимы однозначно. HTTP Basic CONNECT успех не доказывает, что сервер действительно требует/проверяет предоставленные credentials. SOCKS4 не имеет password authentication.

Подробный отчёт открывается/копируется. Credentials, исходные error strings, cookies/auth headers, redirect Location и произвольное тело сайта не выводятся. В отчёте есть адреса сети: не публикуйте его без проверки/редактирования. Он не включает account data. Проверки не отключают TLS, не очищают кэш/данные и не перезапускают сервисы автоматически.

## Куда рыть

| Наблюдение | Что проверить первым | Что не делать вслепую |
| --- | --- | --- |
| Proxy host DNS / TCP не проходит | Host/Port, смена DHCP/шлюза, Wi-Fi/Ethernet, изоляция клиентов/firewall, proxy source asleep/not listening; другой клиент той же сети | Кэш YouTube/браузера не чинит сетевой порт |
| TCP есть, SOCKS/auth/CONNECT не проходит | Тип протокола, Basic/NTLM ограничения, логин/пароль, правила/логи/интернет/DNS источника | Не называть открытый порт исправным прокси, не переустанавливать приставку |
| TLS не проходит | Дата/время, HTTP vs HTTPS, hostname/trust/перехват, reset/timeout | Не отключать проверку сертификатов; timeout не равно bad certificate |
| Обычный HTTPS проходит, оба DoH нет | Источник разрешает ли 1.1.1.1/8.8.8.8:443 и binary DNS POST; после исправления reconnect VPN | Не сбрасывать кэш клиента вместо проверки native DNS |
| Прямой контроль проходит, прокси нет | TV→proxy путь и source policy/upstream, сравнить тот же источник с другим устройством | Прямой успех не доказывает исправность TUN |
| Прокси HTTPS/DoH проходит, реальные apps нет | Actual allow/bypass, UDP/QUIC/IPv6 ограничения, native/TUN state; reconnect VPN, затем другой routed app | Не объявлять источник точно исправным: diagnostic Java transport ≠ весь native путь |
| YouTube сайт/thumbnail HTTP 403/429 | IP/доменная политика или rate limit источника/сервиса | Reboot TV/кэш обычно не меняют server-side ограничения |
| Только один routed client не работает, другой работает | Force-stop/reopen клиента; затем очистить только cache при необходимости | Не очищать данные/аккаунт и не удалять LocProxyTV |

Порядок: проверить настройки/сеть → сравнить другое приложение/устройство → выключить/включить VPN один раз → перезапуск источника, если другие клиенты/его логи тоже показывают проблему. Полная перезагрузка приставки — последний шаг. Причина может быть неоднозначной; отчёт не назначает виновника без доказательств.

## Ограничения и отмена

Наш UID исключён из собственного VPN против loops. Upstream тесты явно выполняют SOCKS/CONNECT и trusted TLS, но НЕ тестируют TUN-путь стороннего browser/YouTube, native Rust trust store или просмотр видео. Полного UDP/QUIC и IPv6 сейчас нет. YouTube video/API/googlevideo/account/регион/consent/captcha требуют device QA.

Connect/read/TLS budgets 4 секунды, bounded HTTP lines/headers/body/chunks, read deadline 12 секунд, concurrency 2, общий сетевой budget 65 секунд с сохранением завершённых целей. При cancellation/timeout registry закрывает raw/TLS sockets. Системный DNS resolver может завершиться позже на платформенном timeout; hard real-time отмена DNS не обещается. Новая generation не принимает старые результаты. При смене сети/профиля/active upstream отчёт помечается stale; запуск после выключения VPN не выдаётся за TUN test.

При подключении выполняется интернет-диагностика, а первая успешная proxied web проверка по-прежнему показывает и фокусирует **Open YouTube**. Повторный тест не перехватывает фокус. Открытие клиента только явным нажатием; кнопка проверки YouTube сама ничего не открывает.

Tests: staged SOCKS/HTTP fixtures (407/502/REP/auth), DNS framing/question/answer/RCODE, no direct false positive, credential/raw error redaction, cancellation with blocked TLS, actual routing policies и instrumented independent buttons/busy/cancel. Public endpoints и реальная прошивка/видео в offline fixture tests не проверяются.
