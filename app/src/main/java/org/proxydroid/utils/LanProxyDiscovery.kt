package org.proxydroid.utils

import java.io.DataInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Discovery never sends saved credentials or scans outside the attached RFC1918 subnet. */
data class LanProxy(val host: String, val port: Int, val type: String, val requiresAuth: Boolean) {
    val key get() = "$host:$port/$type"
}
data class LanScanPlan(val hosts: List<String>, val ports: List<Int>, val scope: String, val partial: Boolean)
data class LanScanResult(val proxies: List<LanProxy>, val checked: Int, val total: Int, val openPorts: Int, val timedOut: Boolean)

object LanProxyDiscovery {
    val commonPorts = listOf(10808, 1080, 7890, 8080, 3128, 8888, 8118)
    private fun numeric(host: String): Long? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            if (!part.matches(Regex("[0-9]{1,3}"))) return null
            val n = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or n.toLong()
        }
        return value
    }
    private fun text(n: Long) = listOf(24, 16, 8, 0).joinToString(".") { ((n shr it) and 255).toString() }
    private fun privateIp(n: Long): Boolean = (n shr 24) == 10L || (n shr 20) == 0xac1L || (n shr 16) == 0xc0a8L
    fun plan(address: String, prefix: Int, gateway: String?, currentHost: String?, currentPort: Int): LanScanPlan {
        val own = numeric(address) ?: error("Нужен IPv4-адрес локальной сети")
        require(privateIp(own) && prefix in 1..30) { "Поиск доступен только в частной IPv4-сети Wi-Fi/Ethernet" }
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val base = own and mask
        val last = base or (0xffffffffL xor mask)
        val scanPrefix = maxOf(prefix, 24)
        val scanMask = (0xffffffffL shl (32 - scanPrefix)) and 0xffffffffL
        val low = own and scanMask
        val high = low or (0xffffffffL xor scanMask)
        val hosts = linkedSetOf<Long>()
        fun add(host: String?) { val n = host?.let(::numeric) ?: return
            if (privateIp(n) && (n and mask) == base && n != own && n > base && n < last) hosts += n }
        add(gateway); add(currentHost)
        for (n in low + 1 until high) if (n != own && privateIp(n)) hosts += n
        return LanScanPlan(hosts.take(256).map(::text), (listOf(currentPort) + commonPorts).filter { it in 1..65535 }.distinct(),
            "${text(low)}/$scanPrefix" + if (prefix < 24) " + шлюз/текущий Host в /$prefix" else "", prefix < 24)
    }
    /** Numeric addresses only; open ports without a recognised handshake are NOT listed as proxies. */
    internal fun identify(host: String, port: Int, route: ProbeRoute, sockets: ProbeSockets,
                          onOpen: () -> Unit = {}): LanProxy? {
        require(numeric(host) != null && port in 1..65535)
        val address = InetAddress.getByAddress(numeric(host)!!.let { n -> byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte()) })
        fun connect(): Socket = Socket().also { socket ->
            try { sockets.register(socket); route.register(socket); route.bind(socket)
                socket.connect(InetSocketAddress(address, port), 350); socket.soTimeout = 700 }
            catch (e: Exception) { socket.close(); throw e }
        }
        try {
            connect().use { socket ->
                onOpen()
                // Offer both no-auth and username/password; do not perform password authentication.
                socket.getOutputStream().apply { write(byteArrayOf(5, 2, 0, 2)); flush() }
                val input = DataInputStream(socket.getInputStream())
                val version = input.readUnsignedByte(); val method = input.readUnsignedByte()
                if (version == 5 && method in listOf(0, 2)) return LanProxy(host, port, "socks5", method == 2)
                if (version == 5) return null // Unsupported SOCKS authentication, not HTTP.
            }
        } catch (_: Exception) { /* A different protocol may not answer a SOCKS greeting. */ }
        try {
            connect().use { socket ->
                onOpen()
                socket.getOutputStream().apply {
                    write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII)); flush()
                }
                val input = socket.getInputStream()
                fun line(): String {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (bytes.size() < 8192) { val b = input.read(); if (b < 0) error("EOF")
                        if (b == 10) return bytes.toString("US-ASCII").removeSuffix("\r")
                        bytes.write(b) }
                    error("Header limit")
                }
                val match = Regex("HTTP/1\.[01] ([0-9]{3})(?: .*|)").matchEntire(line()) ?: return null
                val status = match.groupValues[1].toInt()
                if (status == 200) return LanProxy(host, port, "http", false)
                if (status != 407) return null
                var budget = 0; var proxyAuth = false
                while (budget < 16384) { val l = line(); budget += l.length + 2
                    if (l.isEmpty()) return if (proxyAuth) LanProxy(host, port, "http", true) else null
                    if (l.startsWith("Proxy-Authenticate:", ignoreCase = true)) proxyAuth = true }
            }
        } catch (_: Exception) { /* Neither handshake confirmed. */ }
        return null
    }
    suspend fun scan(plan: LanScanPlan, route: ProbeRoute,
                     progress: (Int, Int, List<LanProxy>) -> Unit = { _, _, _ -> }): LanScanResult = coroutineScope {
        require(plan.hosts.size <= 256 && plan.ports.size <= 8)
        val sockets = ProbeSockets()
        val closer = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { sockets.close() } }
        val checked = AtomicInteger(); val open = AtomicInteger()
        val found = java.util.concurrent.ConcurrentHashMap<String, LanProxy>()
        val total = plan.hosts.size * plan.ports.size
        val semaphore = Semaphore(16)
        var timedOut = false
        try {
            try { withTimeout(90_000L) {
                val timeoutCloser = launch(start = CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { sockets.close() } }
                try { plan.hosts.flatMap { host -> plan.ports.map { port -> async(Dispatchers.IO) {
                    semaphore.withPermit {
                        ensureActive()
                        var countedOpen = false
                        val proxy = identify(host, port, route, sockets) { if (!countedOpen) { open.incrementAndGet(); countedOpen = true } }
                        ensureActive()
                        proxy?.let { found[it.key] = it }
                        val done = checked.incrementAndGet()
                        if (proxy != null || done % 16 == 0 || done == total) progress(done, total, found.values.sortedBy { it.key })
                    }
                } } }.awaitAll() } finally { timeoutCloser.cancel() }
            } } catch (_: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); timedOut = true }
            LanScanResult(found.values.sortedBy { it.key }, checked.get(), total, open.get(), timedOut)
        } finally { closer.cancel(); sockets.close() }
    }
}
