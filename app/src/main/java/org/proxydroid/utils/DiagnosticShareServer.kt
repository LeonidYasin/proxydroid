package org.proxydroid.utils

import java.io.Closeable
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit, short-lived LAN download. No filesystem access, cloud upload or credentials. */
class DiagnosticShareServer(address: Inet4Address, report: String, private val lifetimeMs: Long = 300_000L, filename: String = "LocProxyTV-diagnostics.txt") : Closeable {
    private val downloadName = filename.also { require(it.matches(Regex("[A-Za-z0-9._-]{1,100}")) && !it.startsWith(".") && !it.contains("..")) { "Invalid download filename" } }
    private val validLifetime = lifetimeMs.also { require(it in 1..300_000L) }
    private val content = report.toByteArray(Charsets.UTF_8).also { require(it.size <= 512 * 1024) { "Report too large" } }
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
    private val path = "/$token/diagnostics.txt"
    private val listener = ServerSocket(0, 4, address).apply { soTimeout = 500 }
    private val closed = AtomicBoolean(false)
    private val timer = java.util.Timer("diagnostic-share-expiry", true)
    private val expires = System.nanoTime() + lifetimeMs * 1_000_000
    @Volatile private var client: Socket? = null
    val url = "http://${address.hostAddress}:${listener.localPort}$path"
    val isOpen: Boolean get() = !closed.get() && System.nanoTime() < expires
    init {
        timer.schedule(object : java.util.TimerTask() { override fun run() { close() } }, validLifetime)
        Thread({
            try {
                while (isOpen) {
                    val socket = try { listener.accept() } catch (_: SocketTimeoutException) { continue }
                    client = socket
                    try { socket.use { serve(it) } } catch (_: Exception) { /* malformed/abandoned client */ }
                    finally { client = null }
                }
            } catch (_: Exception) { /* close unblocks accept */ }
            finally { close() }
        }, "diagnostic-lan-share").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 1500
        val input = socket.getInputStream()
        val header = StringBuilder()
        val deadline = System.nanoTime() + 2_000_000_000L
        while (header.length < 8192 && isOpen && System.nanoTime() < deadline) {
            val b = input.read(); if (b < 0) return
            header.append(b.toChar())
            if (header.endsWith("\r\n\r\n")) break
        }
        if (!isOpen || !header.endsWith("\r\n\r\n")) return
        val request = header.toString().substringBefore("\r\n").split(' ')
        val authorized = request.size == 3 && request[0] == "GET" && request[2] in listOf("HTTP/1.0", "HTTP/1.1") &&
            MessageDigest.isEqual(request[1].toByteArray(Charsets.US_ASCII), path.toByteArray(Charsets.US_ASCII))
        val body = if (authorized) content else "Not found\n".toByteArray(Charsets.UTF_8)
        val status = if (authorized) "200 OK" else "404 Not Found"
        val response = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nContent-Disposition: attachment; filename=$downloadName\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply { write(response.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }
    override fun close() {
        closed.set(true); timer.cancel()
        runCatching { client?.close() }; runCatching { listener.close() }
    }
}
