package org.proxydroid.utils

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class DiagnosticKind { INTERNET, YOUTUBE }
data class DiagnosticTarget(val name: String, val host: String, val path: String,
                            val expected: Int = 204, val doh: Boolean = false, val direct: Boolean = false)
data class TargetDiagnostic(val target: DiagnosticTarget, val steps: List<ProbeStep>, val success: Boolean,
                            val code: String? = null, val httpStatus: Int? = null) {
    val tcpReached get() = steps.any { it.stage == ProbeStage.TCP && it.ok }
}
data class DiagnosticReport(val kind: DiagnosticKind, val endpoint: String, val environment: List<String>,
                            val results: List<TargetDiagnostic>, val checkedAt: Long = System.currentTimeMillis(),
                            val stale: Boolean = false) {
    val internetOk get() = results.any { !it.target.direct && !it.target.doh && it.success }
    fun text(): String = buildString {
        appendLine("LocProxyTV — ${if (kind == DiagnosticKind.YOUTUBE) "YouTube" else "proxy / internet"} diagnostics")
        appendLine("Время проверки: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(checkedAt))}")
        if (stale) appendLine("УСТАРЕЛО: сеть/профиль/сервис изменились — повторите проверку.")
        appendLine("Upstream: $endpoint (credentials скрыты)")
        environment.forEach { appendLine(it) }
        appendLine()
        results.forEach { r ->
            appendLine("${if (r.success) "PASS" else "FAIL"} · ${r.target.name} · ${r.target.host}${r.target.path} · ${if (r.target.direct) "ПРЯМОЙ КОНТРОЛЬ, не прокси" else "ЧЕРЕЗ ПРОКСИ"}")
            r.steps.forEach { appendLine("  ${if (it.ok) "OK" else "FAIL"} ${it.stage.label}: ${it.detail} · ${it.millis} ms") }
            r.code?.let { appendLine("  Code: $it") }
        }
        appendLine(); appendLine("Куда смотреть и что делать:")
        recommendations(results, kind).forEach { appendLine("• $it") }
        appendLine()
        appendLine("Границы: UID LocProxyTV исключён из собственного VPN; эти запросы явно идут через upstream, но НЕ проверяют TUN/native путь другого приложения. Прямые запросы — отдельный контроль сети, не fallback для объявления прокси рабочим. Сайт/миниатюра НЕ доказывают воспроизведение видео, account/API или googlevideo. Java TLS и Rust могут использовать разные trust stores. Автоматических сбросов/перезагрузок и обхода сертификатов нет. Отчёт содержит адреса сети — передавайте только доверенной поддержке.")
    }
    companion object {
        fun recommendations(results: List<TargetDiagnostic>, kind: DiagnosticKind): List<String> {
            val proxy = results.filter { !it.target.direct }
            val web = proxy.filter { !it.target.doh }
            val dns = proxy.filter { it.target.doh }
            val direct = results.filter { it.target.direct }
            val advice = linkedSetOf<String>()
            if (proxy.any { it.code in setOf("HTTP_HEADERS_LIMIT", "HTTP_LINE_LIMIT", "DUPLICATE_HTTP_FRAMING") }) advice += "Ответ не уложился в безопасные limits/правила диагностического HTTP-парсера. Это не доказательство мёртвого порта или TLS; сравните с реальным браузером и сохраните код ошибки."
            if (results.any { it.code == "CHECK_DEADLINE" }) advice += "Общий лимит времени исчерпан для части целей: это незавершённая проверка, не доказательство мёртвого порта. Повторите на стабильной сети; завершённые цели сохранены."
            if (proxy.any { it.code == "BAD_INPUT_OR_PROTOCOL" && !it.tcpReached }) advice += "Проверьте заполнение Host, Port 1..65535 и поддерживаемый тип прокси. Некорректные входные данные не являются доказательством отказа источника."
            if (proxy.any { it.code == "DNS_LOOKUP_FAILED" }) advice += "Приставка не разрешила имя HOST прокси: проверьте Host, DNS сети и смену IP/шлюза. До соединения с прокси нельзя оценить DNS удалённого сайта."
            if (proxy.any { it.code in setOf("DNS_LOOKUP_FAILED", "NO_ROUTE", "CONNECTION_REFUSED_OR_UNREACHABLE", "TIMEOUT", "SOCKET_CLOSED_OR_RESET", "UNEXPECTED_EOF") && it.steps.any { step -> !step.ok && step.stage in setOf(ProbeStage.DNS, ProbeStage.TCP) } } && proxy.none { it.tcpReached }) advice += "TCP-порт прокси недоступен: проверьте Host/Port, Wi-Fi/Ethernet, изоляцию клиентов/firewall роутера или точки доступа; источник прокси должен слушать порт и не спать. Сравните с другим устройством в ТОЙ ЖЕ сети. Очистка кэша здесь не поможет."
            if (proxy.any { it.code?.contains("AUTH") == true || it.code == "CONNECT_HTTP_407" || it.code == "WRONG_PROXY_PROTOCOL" || it.steps.any { step -> !step.ok && step.stage == ProbeStage.AUTH } }) advice += "Сначала проверьте тип прокси и авторизацию: SOCKS vs HTTP/HTTPS, Basic vs неподдерживаемый NTLM. Открытый порт может принадлежать другому сервису. Не сбрасывайте данные приложения и сертификаты."
            if (proxy.any { it.code?.startsWith("SOCKS5_REP_") == true || it.code?.startsWith("SOCKS4_REP_") == true || it.code?.startsWith("CONNECT_HTTP_") == true && it.code != "CONNECT_HTTP_407" }) advice += "Источник прокси отказал/не выполнил CONNECT. Смотрите его логи, правила доменов/назначений, DNS и собственный выход в интернет. REP / HTTP-код сужает причину, но не всегда отличает DNS от маршрута. Перезапускайте источник, если другие независимые клиенты тоже не работают либо логи указывают на зависший сервис."
            if (proxy.any { r -> r.steps.any { !it.ok && it.stage in setOf(ProbeStage.PROXY_TLS, ProbeStage.TARGET_TLS) } }) advice += "Сбой TLS: проверьте дату/время/часовой пояс приставки, HTTPS-vs-HTTP тип прокси, доверие/имя сертификата, перехват TLS и сбросы соединения источником. Не отключайте проверку сертификата. Таймаут сам по себе не доказывает ошибку сертификата."
            if (dns.isNotEmpty() && dns.none { it.success } && web.any { it.success }) advice += "HTTPS-сайты отвечают, но оба используемых native DNS адреса DoH не прошли: возможна проблема DNS внутри туннеля. Источник должен разрешать 1.1.1.1:443 / 8.8.8.8:443 и POST DNS-message. После исправления источника/сети переподключите VPN. Кэш браузера/YouTube не исправляет этот DNS-путь."
            if (dns.any { it.success } && dns.any { !it.success }) advice += "Один DoH сервер ответил, другой нет: native использует fallback между ними. Это не полный отказ DNS; сверяйте native путь, а не только отдельный endpoint."
            if (kind == DiagnosticKind.INTERNET && web.any { it.httpStatus == 200 || (it.httpStatus ?: 0) in 300..399 }) advice += "Тест /generate_204 ожидал 204, но получил страницу/redirect: проверьте captive portal или ограничения этого test endpoint. Полученный HTTP-ответ сам по себе не означает мёртвый TCP-порт."
            if (web.any { it.success } && web.any { !it.success }) advice += "Недоступна лишь часть целей: это не означает, что весь прокси мёртв. Сравните домены, фильтрацию, перегрузку и ограничения IP источника."
            if (proxy.any { it.httpStatus == 403 || it.httpStatus == 429 }) advice += "Сайт вернул HTTP 403/429 уже после CONNECT/TLS: HTTPS-путь существует, но сайт ограничивает запросы или IP источника. Перезагрузка приставки/очистка кэша обычно не меняет серверное ограничение."
            if (direct.any { it.success } && web.none { it.success } && web.any { it.code !in setOf("CHECK_DEADLINE", "HTTP_HEADERS_LIMIT", "HTTP_LINE_LIMIT", "DUPLICATE_HTTP_FRAMING") }) advice += "Прямой контрольный HTTPS прошёл в выбранной сети приставки, а через прокси — нет: прежде перезагрузки приставки проверьте источник, его правила/выход и путь приставка→прокси. Это всё ещё не независимое доказательство исправности native туннеля."
            if (direct.isNotEmpty() && direct.none { it.success } && web.none { it.success }) advice += "Не прошли ни прямой контроль, ни сайты через прокси: сначала проверьте физическую сеть, captive portal и часы приставки; затем тот же источник с другого устройства. Прямые сайты тоже могут блокироваться — только по этим результатам нельзя назначить виновником приставку или источник."
            if (web.any { it.success }) advice += "Если upstream-проверки проходят, а реальные приложения всё равно не работают: проверьте ФАКТИЧЕСКИЙ allow/bypass список, результаты DoH, неподдерживаемые UDP/QUIC и полный IPv6. Один раз переподключите VPN, чтобы пересоздать native-состояние. Успех upstream не доказывает работу TUN и native DNS."
            if (kind == DiagnosticKind.YOUTUBE) advice += "Проверки сайта YouTube и миниатюры отдельно от кнопки Open YouTube. Убедитесь, что установленный TV/phone клиент включён в VPN. Видео использует дополнительные googlevideo/CDN/API адреса; успешная миниатюра не является проверкой воспроизведения."
            advice += "Порядок восстановления: настройки/сеть → сравнить другое приложение/устройство → выключить/включить VPN один раз → перезапустить источник только при соответствующих признаках. Если не работает ТОЛЬКО один клиент, а другой маршрутизируемый клиент работает, принудительно остановите/откройте проблемный клиент; можно очистить ТОЛЬКО его кэш. Полная перезагрузка приставки — последний шаг. Не очищайте данные и не удаляйте приложение ради диагностики."
            return advice.toList()
        }
    }
}

/** Socket registry closes blocking reads on cancellation/overall timeout; no background test survives the UI job. */
internal class ProbeSockets {
    private val closed = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    fun register(socket: Socket) {
        sockets.add(socket)
        if (closed.get()) { runCatching { socket.close() }; throw java.net.SocketException("Probe cancelled") }
    }
    fun close() {
        closed.set(true)
        // Close underlying TCP FIRST: SSLSocket.close can otherwise wait behind an in-flight handshake.
        sockets.filterNot { it is javax.net.ssl.SSLSocket }.forEach { runCatching { it.close() } }
        sockets.forEach { runCatching { it.close() } }; sockets.clear()
    }
}

object NetworkDiagnostics {
    val internetTargets = listOf(
        DiagnosticTarget("Google HTTPS / 204", "www.google.com", "/generate_204"),
        DiagnosticTarget("Cloudflare HTTPS / 204", "cp.cloudflare.com", "/generate_204"),
        DiagnosticTarget("Native DNS path: Cloudflare DoH", "1.1.1.1", "/dns-query", 200, doh = true),
        DiagnosticTarget("Native DNS path: Google DoH", "8.8.8.8", "/dns-query", 200, doh = true))
    val youtubeTargets = listOf(
        DiagnosticTarget("YouTube website", "www.youtube.com", "/", 200),
        DiagnosticTarget("YouTube thumbnail CDN (not video stream)", "i.ytimg.com", "/vi/jNQXAC9IVRw/default.jpg", 200))
    fun safeEndpoint(endpoint: ProxyEndpoint): String {
        val host = endpoint.host.takeIf { it.length <= 255 && it.matches(Regex("[A-Za-z0-9.:%_-]+")) } ?: "[invalid/redacted host]"
        val type = endpoint.type.takeIf { it in setOf("socks5", "socks4", "http", "https") } ?: "[unsupported]"
        return "$type://$host:${endpoint.port}; authentication ${if (endpoint.user == null) "not configured" else "configured (hidden)"}"
    }
    suspend fun run(kind: DiagnosticKind, endpoint: ProxyEndpoint, environment: List<String>, route: ProbeRoute = ProbeRoute(),
                    progress: (Int, Int) -> Unit = { _, _ -> }): DiagnosticReport = coroutineScope {
        val sockets = ProbeSockets()
        val tracked = ProbeRoute(route.resolve, route.bind) { sockets.register(it); route.register(it) }
        val targets = (if (kind == DiagnosticKind.INTERNET) internetTargets else youtubeTargets + internetTargets.filter { it.doh }) +
            internetTargets.filter { !it.doh }.map { it.copy(name = "Direct reference: ${it.name}", direct = true) }
        val semaphore = Semaphore(2)
        val closer = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { sockets.close() }
        }
        val completed = java.util.concurrent.atomic.AtomicInteger()
        val retained = ConcurrentHashMap<DiagnosticTarget, TargetDiagnostic>()
        try {
            // DNS lookup is Android's system resolver; cancelling closes sockets immediately,
            // but a resolver call itself may finish on its platform timeout.
            val results = try { withTimeout(65_000) {
                val budgetCloser = launch(start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { sockets.close() }
                }
                try { targets.map { target -> async(Dispatchers.IO) {
                    semaphore.withPermit {
                        ensureActive()
                        val result = probe(endpoint, target, tracked)
                        ensureActive(); retained[target] = result
                        progress(completed.incrementAndGet(), targets.size); result
                    }
                } }.awaitAll() } finally { budgetCloser.cancel() }
            } } catch (_: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                targets.map { retained[it] ?: TargetDiagnostic(it, listOf(ProbeStep(ProbeStage.BUDGET, false, "Overall 65-second budget ended before this target completed; no specific failure stage inferred", 65000)), false, "CHECK_DEADLINE") }
            }
            DiagnosticReport(kind, safeEndpoint(endpoint), environment, results)
        } finally { closer.cancel(); sockets.close() }
    }
    internal fun probe(endpoint: ProxyEndpoint, target: DiagnosticTarget, route: ProbeRoute): TargetDiagnostic {
        val steps = mutableListOf<ProbeStep>(); var status: Int? = null
        try {
            if (!target.host.matches(Regex("[A-Za-z0-9.-]+")) || target.host.length > 255 || !target.path.startsWith("/") || target.path.length > 1024 || target.path.any { it == '\r' || it == '\n' })
                throw ProbeProblem(ProbeStage.HTTP, "INVALID_TEST_TARGET", "Unsafe diagnostic test target rejected before network; raw target redacted")
            val socket = if (target.direct) {
                val raw = Socket(); route.register(raw)
                try {
                    route.bind(raw)
                    val address = ProxyTransport.step(ProbeStage.DNS, "Destination resolved on selected physical network (direct reference)", steps::add) { route.resolve(target.host) }
                    ProxyTransport.step(ProbeStage.TCP, "Direct destination TCP; NOT proxy success", steps::add) { raw.connect(java.net.InetSocketAddress(address, 443), 4000); raw.soTimeout = 4000 }
                    raw
                } catch (e: Exception) { runCatching { raw.close() }; throw e }
            } else ProxyTransport.connect(endpoint, target.host, 443, steps::add, route)
            socket.use { tunnel ->
                ProxyTransport.step(ProbeStage.TARGET_TLS, "Certificate chain and destination hostname/IP verified", steps::add) { ProxyTransport.tls(tunnel, target.host, 443, route.register) }.use { tls ->
                    val query = if (target.doh) dnsQuery() else null
                    val request = if (query != null) "POST ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nContent-Type: application/dns-message\r\nAccept: application/dns-message\r\nContent-Length: ${query.size}\r\nConnection: close\r\n\r\n"
                        else "GET ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nUser-Agent: LocProxyTV-diagnostics\r\nConnection: close\r\n\r\n"
                    val input = ProxyTransport.input(tls)
                    val headers = ProxyTransport.step(ProbeStage.HTTP, "HTTP response parsed; expected status ${target.expected}", steps::add) {
                        tls.getOutputStream().apply { write(request.toByteArray(Charsets.US_ASCII)); query?.let { write(it) }; flush() }
                        status = ProxyTransport.statusCode(ProxyTransport.readLine(input))
                        val h = ProxyTransport.readHeaders(input)
                        if (status != target.expected && !(target.host == "www.youtube.com" && (status ?: 0) in 301..308))
                            throw ProbeProblem(ProbeStage.HTTP, "DESTINATION_HTTP_$status", "Destination HTTP status=$status (TCP/CONNECT/TLS already succeeded); expected ${target.expected}")
                        h
                    }
                    // Include exact numeric status, but no cookies/body/redirect destination.
                    steps.add(ProbeStep(ProbeStage.HTTP, true, "Destination HTTP=$status${if ((status ?: 0) in 301..308) "; redirect observed, NOT followed or playback verified" else ""}", 0))
                    if (query != null) ProxyTransport.step(ProbeStage.DOH, "Binary DNS reply to example.com A verified: QR / ID / RCODE=0 / answers present", steps::add) {
                        if (headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/dns-message") throw ProbeProblem(ProbeStage.DOH, "BAD_DOH_CONTENT_TYPE", "DoH did not return application/dns-message")
                        if (headers["content-encoding"]?.lowercase()?.let { it != "identity" } == true) throw ProbeProblem(ProbeStage.DOH, "ENCODED_DOH_BODY", "Unexpected encoded DNS body")
                        validateDnsReply(readBody(input, headers), query)
                    }
                }
            }
            return TargetDiagnostic(target, steps, true, httpStatus = status)
        } catch (e: Exception) {
            val problem = ProxyTransport.classify(steps.lastOrNull()?.stage ?: ProbeStage.TCP, e)
            if (steps.none { !it.ok }) steps.add(ProbeStep(problem.stage, false, problem.safeDetail, 0))
            val safeTarget = if (problem.code == "INVALID_TEST_TARGET") target.copy(name = "[invalid test target]", host = "[redacted]", path = "/[redacted]") else target
            return TargetDiagnostic(safeTarget, steps, false, problem.code, status)
        }
    }
    internal fun dnsQuery(): ByteArray = byteArrayOf(0x4c, 0x50, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 7) + "example".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) + "com".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 1, 0, 1)
    internal fun validateDnsReply(body: ByteArray, query: ByteArray) {
        fun n(i: Int) = body[i].toInt() and 255
        if (body.size < query.size || n(0) != (query[0].toInt() and 255) || n(1) != (query[1].toInt() and 255) || n(2) and 0x80 == 0 || n(2) and 0x7a != 0 || n(4) != 0 || n(5) != 1 || !body.copyOfRange(12, query.size).contentEquals(query.copyOfRange(12, query.size)))
            throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "DNS response ID/flags/question do not match the bounded test query")
        val rcode = n(3) and 15
        if (rcode != 0) throw ProbeProblem(ProbeStage.DOH, "DNS_RCODE_$rcode", "DoH DNS RCODE=$rcode (2=SERVFAIL, 3=NXDOMAIN, 5=REFUSED)")
        val count = n(6) * 256 + n(7)
        if (count == 0 || count > 64) throw ProbeProblem(ProbeStage.DOH, "DNS_NO_ANSWERS", "DoH returned no/bounded-invalid answer records")
        var offset = query.size
        var foundA = false
        repeat(count) {
            var labels = 0
            while (true) {
                if (offset >= body.size || ++labels > 128) throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "Truncated DNS answer name")
                val length = n(offset++)
                if (length == 0) break
                if (length and 0xc0 == 0xc0) {
                    if (offset >= body.size || (length and 0x3f) * 256 + n(offset) >= body.size) throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "Invalid DNS name pointer")
                    offset++; break
                }
                if (length > 63 || offset + length > body.size) throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "Invalid DNS name label")
                offset += length
            }
            if (offset + 10 > body.size) throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "Truncated DNS answer header")
            val type = n(offset) * 256 + n(offset + 1); val clazz = n(offset + 2) * 256 + n(offset + 3)
            val length = n(offset + 8) * 256 + n(offset + 9); offset += 10
            if (offset + length > body.size) throw ProbeProblem(ProbeStage.DOH, "INVALID_DNS_REPLY", "Truncated DNS answer data")
            if (type == 1 && clazz == 1 && length == 4) foundA = true
            offset += length
        }
        if (!foundA) throw ProbeProblem(ProbeStage.DOH, "DNS_NO_A_ANSWER", "DoH returned no IPv4 A answer for the test question")
    }
    internal fun readBody(input: DataInputStream, headers: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream(); val limit = 8192
        fun bytes(n: Int) { require(n >= 0 && n <= limit - out.size()); val b = ByteArray(n); input.readFully(b); out.write(b) }
        val transfer = headers["transfer-encoding"]?.lowercase()
        require(transfer == null || transfer == "chunked")
        require(transfer == null || headers["content-length"] == null)
        if (transfer == "chunked") {
            var chunks = 0
            while (true) {
                require(++chunks <= 128)
                val size = ProxyTransport.readLine(input).substringBefore(';').trim().toIntOrNull(16) ?: error("Invalid chunk")
                require(size >= 0)
                if (size == 0) { ProxyTransport.readHeaders(input); break }
                bytes(size); require(input.readUnsignedByte() == 13 && input.readUnsignedByte() == 10)
            }
        } else if (headers["content-length"] != null) {
            bytes(headers.getValue("content-length").toInt())
        } else {
            while (true) { val b = input.read(); if (b < 0) break; require(out.size() < limit); out.write(b) }
        }
        return out.toByteArray()
    }
}
