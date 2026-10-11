package org.proxydroid.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

enum class ProbeStage(val label: String) {
    DNS("DNS / адрес узла"), TCP("TCP-соединение"), PROXY_TLS("TLS до HTTPS-прокси"),
    AUTH("Протокол / авторизация прокси"), CONNECT("CONNECT / удалённый адрес"),
    TARGET_TLS("TLS до сайта"), HTTP("Ответ HTTPS"), DOH("DNS-over-Ответ HTTPS"), BUDGET("Общий лимит / незавершённая проверка")
}
data class ProbeStep(val stage: ProbeStage, val ok: Boolean, val detail: String, val millis: Long)
class ProbeProblem(val stage: ProbeStage, val code: String, val safeDetail: String) : IllegalArgumentException(safeDetail)
class ProbeRoute(val resolve: (String) -> InetAddress = InetAddress::getByName,
                 val bind: (Socket) -> Unit = {}, val register: (Socket) -> Unit = {})

/** Bounded upstream transport, NOT the VPN data plane. Never include remote text or credentials in errors. */
object ProxyTransport {
    fun classify(stage: ProbeStage, failure: Exception): ProbeProblem {
        if (failure is ProbeProblem) return failure
        val causes = generateSequence<Throwable>(failure) { it.cause }.take(8).toList()
        val certificateCode = when {
            causes.any { it is java.net.SocketTimeoutException } -> "TIMEOUT"
            causes.any { it is java.security.cert.CertificateExpiredException } -> "TLS_CERTIFICATE_EXPIRED"
            causes.any { it is java.security.cert.CertificateNotYetValidException } -> "TLS_CERTIFICATE_NOT_YET_VALID_CHECK_CLOCK"
            causes.any { it is java.security.cert.CertPathValidatorException } -> "TLS_CERTIFICATE_PATH_VALIDATION_FAILED"
            failure is javax.net.ssl.SSLException && causes.any { it.message?.contains("No subject alternative", ignoreCase = true) == true || it.message?.contains("hostname", ignoreCase = true) == true } -> "TLS_HOSTNAME_VERIFICATION_FAILED"
            else -> null
        }
        val code = certificateCode ?: when (failure) {
            is java.net.UnknownHostException -> "DNS_LOOKUP_FAILED"
            is java.net.NoRouteToHostException -> "NO_ROUTE"
            is java.net.ConnectException -> "CONNECTION_REFUSED_OR_UNREACHABLE"
            is java.net.SocketTimeoutException -> "TIMEOUT"
            is javax.net.ssl.SSLHandshakeException -> "TLS_HANDSHAKE_OR_CERTIFICATE"
            is javax.net.ssl.SSLPeerUnverifiedException -> "TLS_PEER_NOT_VERIFIED"
            is javax.net.ssl.SSLException -> "TLS_PROTOCOL_ERROR"
            is java.io.EOFException -> "UNEXPECTED_EOF"
            is java.net.SocketException -> "SOCKET_CLOSED_OR_RESET"
            is IllegalArgumentException -> "BAD_INPUT_OR_PROTOCOL"
            else -> "IO_OR_PROTOCOL_ERROR"
        }
        return ProbeProblem(stage, code, "$code (${causes.joinToString(" → ") { it.javaClass.simpleName }}); no raw server/error text retained")
    }
    fun <T> step(stage: ProbeStage, detail: String, observe: (ProbeStep) -> Unit, block: () -> T): T {
        val started = System.nanoTime()
        try {
            val result = block()
            observe(ProbeStep(stage, true, detail, (System.nanoTime() - started) / 1_000_000))
            return result
        } catch (failure: Exception) {
            val problem = classify(stage, failure)
            observe(ProbeStep(problem.stage, false, problem.safeDetail, (System.nanoTime() - started) / 1_000_000))
            throw problem
        }
    }
    fun tls(socket: Socket, host: String, port: Int, register: (Socket) -> Unit = {}): SSLSocket {
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(socket, host, port, true) as SSLSocket
        try {
            register(ssl)
            ssl.soTimeout = 4000
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.startHandshake()
            return ssl
        } catch (failure: Exception) { runCatching { ssl.close() }; throw failure }
    }
    private fun expect(condition: Boolean, stage: ProbeStage, code: String, detail: String) {
        if (!condition) throw ProbeProblem(stage, code, detail)
    }
    private fun ipv4(target: String): ByteArray? {
        val parts = target.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
        return numbers.map { it.toByte() }.toByteArray()
    }
    fun connect(endpoint: ProxyEndpoint, target: String, targetPort: Int,
                observe: (ProbeStep) -> Unit = {}, route: ProbeRoute = ProbeRoute()): Socket {
        require(endpoint.host.isNotBlank() && endpoint.port in 1..65535 && targetPort in 1..65535)
        require(target.matches(Regex("[A-Za-z0-9.-]+")) && target.length <= 255)
        val raw = Socket()
        var socket: Socket = raw
        try {
            route.register(raw); route.bind(raw)
            val address = step(ProbeStage.DNS, "Proxy host resolved / numeric address accepted", observe) { route.resolve(endpoint.host) }
            observe(ProbeStep(ProbeStage.DNS, true, "Selected proxy address: ${address.hostAddress ?: "unknown"}", 0))
            step(ProbeStage.TCP, "Proxy port accepted TCP; this alone does NOT verify forwarding", observe) {
                raw.connect(InetSocketAddress(address, endpoint.port), 4000); raw.soTimeout = 4000
            }
            if (endpoint.type == "https") socket = step(ProbeStage.PROXY_TLS, "Trusted certificate and hostname verified", observe) {
                tls(raw, endpoint.host, endpoint.port, route.register)
            }
            val input = input(socket)
            val output = DataOutputStream(socket.getOutputStream())
            val targetIp = ipv4(target)
            when (endpoint.type) {
                "socks5" -> {
                    step(ProbeStage.AUTH, if (endpoint.user == null) "SOCKS5 no-auth accepted" else "SOCKS5 username/password accepted (values hidden)", observe) {
                        output.write(byteArrayOf(5, 1, if (endpoint.user == null) 0 else 2)); output.flush()
                        expect(input.readUnsignedByte() == 5, ProbeStage.AUTH, "WRONG_PROXY_PROTOCOL", "Expected SOCKS5; wrong port/type or non-proxy service")
                        val method = input.readUnsignedByte()
                        if (method == 2 && endpoint.user != null) {
                            val u = endpoint.user.toByteArray(Charsets.UTF_8); val p = (endpoint.password ?: "").toByteArray(Charsets.UTF_8)
                            expect(u.size in 1..255 && p.size in 1..255, ProbeStage.AUTH, "BAD_CREDENTIAL_LENGTH", "SOCKS5 credential length must be 1..255 bytes; values hidden")
                            output.writeByte(1); output.writeByte(u.size); output.write(u); output.writeByte(p.size); output.write(p); output.flush()
                            expect(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0, ProbeStage.AUTH, "AUTH_REJECTED", "SOCKS5 authentication rejected; check proxy credentials/policy")
                        } else expect(method == 0 && endpoint.user == null, ProbeStage.AUTH, "AUTH_METHOD_REJECTED", "SOCKS5 method rejected or authentication settings do not match")
                    }
                    step(ProbeStage.CONNECT, if (targetIp == null) "SOCKS5 CONNECT accepted; hostname resolution delegated to proxy" else "SOCKS5 CONNECT to numeric IPv4 accepted", observe) {
                        output.write(byteArrayOf(5, 1, 0, if (targetIp == null) 3 else 1))
                        if (targetIp == null) { output.writeByte(target.length); output.write(target.toByteArray(Charsets.US_ASCII)) } else output.write(targetIp)
                        output.writeShort(targetPort); output.flush()
                        val version = input.readUnsignedByte(); val reply = input.readUnsignedByte(); val reserved = input.readUnsignedByte()
                        expect(version == 5 && reserved == 0, ProbeStage.CONNECT, "WRONG_PROXY_PROTOCOL", "Malformed SOCKS5 CONNECT reply")
                        val reason = when (reply) { 1 -> "general server failure"; 2 -> "ruleset denies connection"; 3 -> "remote network unreachable"; 4 -> "remote host unreachable (DNS or route possible)"; 5 -> "destination refused connection"; 6 -> "TTL expired"; 7 -> "CONNECT command unsupported"; 8 -> "address type unsupported"; else -> "unknown reply" }
                        expect(reply == 0, ProbeStage.CONNECT, "SOCKS5_REP_$reply", "SOCKS5 REP=$reply: $reason")
                        val length = when (input.readUnsignedByte()) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> throw ProbeProblem(ProbeStage.CONNECT, "BAD_REPLY_ADDRESS", "Invalid SOCKS5 bound address type") }
                        input.readFully(ByteArray(length + 2))
                    }
                }
                "socks4" -> {
                    step(ProbeStage.CONNECT, "SOCKS4/4a CONNECT accepted; SOCKS4 has no password-auth confirmation", observe) {
                        val user = (endpoint.user ?: "").toByteArray(Charsets.UTF_8)
                        require(user.size <= 255 && user.none { it == 0.toByte() })
                        output.writeByte(4); output.writeByte(1); output.writeShort(targetPort)
                        output.write(targetIp ?: byteArrayOf(0, 0, 0, 1)); output.write(user); output.writeByte(0)
                        if (targetIp == null) { output.write(target.toByteArray(Charsets.US_ASCII)); output.writeByte(0) }; output.flush()
                        val version = input.readUnsignedByte(); val reply = input.readUnsignedByte()
                        expect(version == 0 && reply == 90, ProbeStage.CONNECT, "SOCKS4_REP_$reply", "SOCKS4 reply=$reply (90=accepted, 91=rejected, 92/93=identity policy); verify type/policy")
                        input.readFully(ByteArray(6))
                    }
                }
                "http", "https" -> {
                    step(ProbeStage.CONNECT, "HTTP CONNECT 200; configured Basic authorization accepted if supplied", observe) {
                        val auth = endpoint.user?.let { "Proxy-Authorization: Basic " + Base64.encodeToString("$it:${endpoint.password ?: ""}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\r\n" } ?: ""
                        output.writeBytes("CONNECT $target:$targetPort HTTP/1.1\r\nHost: $target:$targetPort\r\n$auth\r\n"); output.flush()
                        val code = statusCode(readLine(input, ProbeStage.CONNECT), ProbeStage.CONNECT)
                        expect(code == 200, if (code == 407) ProbeStage.AUTH else ProbeStage.CONNECT, "CONNECT_HTTP_$code", "HTTP CONNECT status=$code; 407=proxy auth, 403=proxy policy, 502/503/504=proxy upstream/DNS/timeout (not uniquely identified)")
                        readHeaders(input, ProbeStage.CONNECT)
                    }
                }
                else -> throw ProbeProblem(ProbeStage.AUTH, "UNSUPPORTED_PROXY", "Unsupported proxy type; use SOCKS5, SOCKS4, HTTP or HTTPS CONNECT")
            }
            return socket
        } catch (failure: Exception) { runCatching { socket.close() }; runCatching { raw.close() }; throw failure }
    }
    fun statusCode(line: String, stage: ProbeStage = ProbeStage.HTTP): Int = Regex("HTTP/1\\.[01] ([0-9]{3})(?: .*|$)").matchEntire(line)?.groupValues?.get(1)?.toInt()
        ?: throw ProbeProblem(stage, if (stage == ProbeStage.CONNECT) "WRONG_PROXY_PROTOCOL" else "INVALID_HTTP_STATUS", "Malformed/non-HTTP response; verify proxy type/port; raw line omitted")
    fun readHeaders(input: DataInputStream, stage: ProbeStage = ProbeStage.HTTP): Map<String, String> {
        val headers = mutableMapOf<String, String>(); var bytes = 0
        while (true) {
            val line = readLine(input, stage, 8192); bytes += line.length + 2
            expect(bytes <= 65536, stage, "HTTP_HEADERS_LIMIT", "HTTP headers exceeded 65536-byte diagnostic limit; this is a parser limit, not proof of a dead proxy")
            if (line.isEmpty()) return headers
            val pos = line.indexOf(':'); expect(pos > 0, stage, "INVALID_HTTP_HEADER", "Malformed HTTP header; raw header omitted")
            val name = line.substring(0, pos).lowercase(); val value = line.substring(pos + 1).trim()
            // Never retain cookies, auth challenges, locations or arbitrary server metadata.
            if (name in setOf("content-length", "transfer-encoding", "content-type", "content-encoding")) {
                expect(!headers.containsKey(name), stage, "DUPLICATE_HTTP_FRAMING", "Duplicate HTTP framing/content header; raw values omitted"); headers[name] = value
            }
        }
    }
    /** Read deadline also bounds trickling replies. */
    fun input(socket: Socket): DataInputStream {
        val deadline = System.nanoTime() + 12_000_000_000L
        return DataInputStream(object : FilterInputStream(socket.getInputStream()) {
            private fun prepare() {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) throw java.net.SocketTimeoutException("Probe read deadline exceeded")
                socket.soTimeout = left.coerceIn(1, 4000).toInt()
            }
            override fun read(): Int { prepare(); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { prepare(); return `in`.read(b, off, len) }
        })
    }
    fun readLine(input: DataInputStream, stage: ProbeStage = ProbeStage.HTTP, limit: Int = 1024): String {
        val line = StringBuilder()
        while (line.length < limit) {
            val byte = input.readUnsignedByte()
            if (byte == 10) return line.toString().removeSuffix("\r")
            line.append(byte.toChar())
        }
        throw ProbeProblem(stage, "HTTP_LINE_LIMIT", "HTTP line exceeded $limit-byte diagnostic limit; parser limit, not proof of a dead proxy; raw text omitted")
    }
}
