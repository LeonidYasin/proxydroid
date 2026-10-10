package org.proxydroid.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Small bounded transport used by the internet probe, not the VPN data plane. */
object ProxyTransport {
    fun tls(socket: Socket, host: String, port: Int): SSLSocket {
        val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(socket, host, port, true) as SSLSocket
        ssl.soTimeout = 4000
        ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        ssl.startHandshake() // Default trust store AND hostname verification; no insecure verifier.
        return ssl
    }

    fun connect(endpoint: ProxyEndpoint, target: String, targetPort: Int): Socket {
        require(endpoint.port in 1..65535 && targetPort in 1..65535)
        require(target.matches(Regex("[A-Za-z0-9.-]+")) && target.length <= 255)
        val raw = Socket()
        try {
            raw.connect(InetSocketAddress(endpoint.host, endpoint.port), 4000)
            raw.soTimeout = 4000
            val socket = if (endpoint.type == "https") tls(raw, endpoint.host, endpoint.port) else raw
            val input = input(socket)
            val output = DataOutputStream(socket.getOutputStream())
            when (endpoint.type) {
                "socks5" -> {
                    output.write(byteArrayOf(5, 1, if (endpoint.user == null) 0 else 2)); output.flush()
                    require(input.readUnsignedByte() == 5) { "Invalid SOCKS5 reply" }
                    val method = input.readUnsignedByte()
                    if (method == 2 && endpoint.user != null) {
                        val u = endpoint.user.toByteArray(Charsets.UTF_8)
                        val p = (endpoint.password ?: "").toByteArray(Charsets.UTF_8)
                        require(u.size in 1..255 && p.size in 1..255) { "Invalid SOCKS5 credential length" }
                        output.writeByte(1); output.writeByte(u.size); output.write(u)
                        output.writeByte(p.size); output.write(p); output.flush()
                        require(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0) { "Proxy authentication rejected" }
                    } else require(method == 0 && endpoint.user == null) { "Proxy authentication method rejected" }
                    output.write(byteArrayOf(5, 1, 0, 3)); output.writeByte(target.length)
                    output.write(target.toByteArray(Charsets.US_ASCII)); output.writeShort(targetPort); output.flush()
                    require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0 && input.readUnsignedByte() == 0) { "Proxy CONNECT rejected" }
                    val length = when (input.readUnsignedByte()) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> error("Invalid SOCKS5 address") }
                    input.readFully(ByteArray(length + 2))
                }
                "socks4" -> {
                    val user = (endpoint.user ?: "").toByteArray(Charsets.UTF_8)
                    require(user.size <= 255 && user.none { it == 0.toByte() })
                    // SOCKS4a: target DNS is resolved by the proxy, not directly.
                    output.writeByte(4); output.writeByte(1); output.writeShort(targetPort)
                    output.write(byteArrayOf(0, 0, 0, 1)); output.write(user); output.writeByte(0)
                    output.write(target.toByteArray(Charsets.US_ASCII)); output.writeByte(0); output.flush()
                    require(input.readUnsignedByte() == 0 && input.readUnsignedByte() == 90) { "SOCKS4 CONNECT rejected" }
                    input.readFully(ByteArray(6))
                }
                "http", "https" -> {
                    val auth = endpoint.user?.let {
                        "Proxy-Authorization: Basic " + Base64.encodeToString("$it:${endpoint.password ?: ""}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\r\n"
                    } ?: ""
                    output.writeBytes("CONNECT $target:$targetPort HTTP/1.1\r\nHost: $target:$targetPort\r\n$auth\r\n"); output.flush()
                    val line = readLine(input)
                    require(line.matches(Regex("HTTP/1\\.[01] 200(?: .*|$)"))) { "HTTP CONNECT rejected" }
                    var bytes = line.length
                    while (true) {
                        val header = readLine(input); bytes += header.length + 2
                        require(bytes <= 8192) { "Oversized proxy headers" }
                        if (header.isEmpty()) break
                    }
                }
                else -> error("Unsupported proxy type")
            }
            return socket
        } catch (failure: Exception) { raw.close(); throw failure }
    }

    /** Total read deadline prevents a trickling proxy from holding a check forever. */
    fun input(socket: Socket): DataInputStream {
        val deadline = System.nanoTime() + 12_000_000_000L
        return DataInputStream(object : FilterInputStream(socket.getInputStream()) {
            private fun prepare() {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0) throw java.net.SocketTimeoutException("Proxy check deadline exceeded")
                socket.soTimeout = left.coerceIn(1, 4000).toInt()
            }
            override fun read(): Int { prepare(); return super.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { prepare(); return `in`.read(b, off, len) }
        })
    }
    fun readLine(input: DataInputStream): String {
        val line = StringBuilder()
        while (line.length < 1024) {
            val byte = input.readUnsignedByte()
            if (byte == 10) return line.toString().removeSuffix("\r")
            line.append(byte.toChar())
        }
        error("Oversized HTTP line")
    }
}
