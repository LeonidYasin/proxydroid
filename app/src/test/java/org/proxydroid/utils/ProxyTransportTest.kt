package org.proxydroid.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ProxyTransportTest {
    private fun server(type: String, user: String? = null, password: String? = null,
                       handler: (DataInputStream, DataOutputStream) -> Unit) {
        ServerSocket(0).use { listener ->
            val task = FutureTask<Unit> {
                listener.accept().use { socket ->
                    socket.soTimeout = 4000
                    handler(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream()))
                }
            }
            Thread(task).apply { isDaemon = true; start() }
            try {
                ProxyTransport.connect(ProxyEndpoint("127.0.0.1", listener.localPort, type, user, password), "example.com", 443).close()
            } finally { task.get(5, TimeUnit.SECONDS) }
        }
    }
    private fun success(input: DataInputStream, output: DataOutputStream) {
        assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte())
        assertEquals(0, input.readUnsignedByte()); assertEquals(3, input.readUnsignedByte())
        val domain = ByteArray(input.readUnsignedByte()); input.readFully(domain)
        assertEquals("example.com", domain.toString(Charsets.US_ASCII)); assertEquals(443, input.readUnsignedShort())
        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80)); output.flush()
    }
    @Test fun socks5SupportsRemoteDnsNoAuth() = server("socks5") { input, output ->
        assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte())
        output.write(byteArrayOf(5, 0)); output.flush(); success(input, output)
    }
    @Test fun socks5AuthenticatesBeforeConnect() = server("socks5", "alice", "testpass") { input, output ->
        assertEquals(5, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte()); assertEquals(2, input.readUnsignedByte())
        output.write(byteArrayOf(5, 2)); output.flush()
        assertEquals(1, input.readUnsignedByte())
        val user = ByteArray(input.readUnsignedByte()); input.readFully(user)
        val pass = ByteArray(input.readUnsignedByte()); input.readFully(pass)
        assertEquals("alice", user.toString(Charsets.UTF_8)); assertEquals("testpass", pass.toString(Charsets.UTF_8))
        output.write(byteArrayOf(1, 0)); output.flush(); success(input, output)
    }
    @Test fun socks5RejectsAuthenticationFailure() {
        assertThrows(IllegalArgumentException::class.java) {
            server("socks5", "alice", "wrong") { input, output ->
                input.readFully(ByteArray(3)); output.write(byteArrayOf(5, 2)); output.flush()
                input.readUnsignedByte(); input.readFully(ByteArray(input.readUnsignedByte())); input.readFully(ByteArray(input.readUnsignedByte()))
                output.write(byteArrayOf(1, 1)); output.flush()
            }
        }
    }
    @Test fun socks4aUsesRemoteDomain() = server("socks4") { input, output ->
        assertEquals(4, input.readUnsignedByte()); assertEquals(1, input.readUnsignedByte()); assertEquals(443, input.readUnsignedShort())
        val address = ByteArray(4); input.readFully(address); assertArrayEquals(byteArrayOf(0, 0, 0, 1), address)
        assertEquals(0, input.readUnsignedByte())
        val domain = StringBuilder(); while (true) { val b = input.readUnsignedByte(); if (b == 0) break; domain.append(b.toChar()) }
        assertEquals("example.com", domain.toString()); output.write(byteArrayOf(0, 90, 0, 80, 127, 0, 0, 1)); output.flush()
    }
    @Test fun httpConnectConsumesHeadersButNoTunnelBytes() = server("http") { input, output ->
        assertEquals("CONNECT example.com:443 HTTP/1.1", ProxyTransport.readLine(input))
        while (ProxyTransport.readLine(input).isNotEmpty()) { }
        output.writeBytes("HTTP/1.1 200 Connection established\r\nServer: fixture\r\n\r\n"); output.flush()
    }
    @Test fun refusesHeaderInjection() {
        assertThrows(IllegalArgumentException::class.java) { ProxyTransport.connect(ProxyEndpoint("localhost", 10808, "http"), "bad\r\nhost", 443) }
    }
}
