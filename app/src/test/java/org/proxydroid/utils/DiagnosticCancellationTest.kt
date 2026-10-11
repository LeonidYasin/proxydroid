package org.proxydroid.utils

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DiagnosticCancellationTest {
    @Test fun cancellationClosesSocketsWhileTlsIsBlocked() = runBlocking {
        ServerSocket(0).use { listener ->
            val ready = CountDownLatch(2)
            val observed = ConcurrentHashMap.newKeySet<Socket>()
            val fixture = Thread {
                repeat(2) {
                    val client = listener.accept()
                    Thread {
                        client.use { socket ->
                            socket.soTimeout = 4000
                            val input = DataInputStream(socket.getInputStream()); val output = socket.getOutputStream()
                            input.readFully(ByteArray(3)); output.write(byteArrayOf(5, 0)); output.flush()
                            input.readFully(ByteArray(4)); input.readFully(ByteArray(input.readUnsignedByte())); input.readUnsignedShort()
                            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80)); output.flush()
                            ready.countDown()
                            runCatching { while (input.read() >= 0) { } }
                        }
                    }.apply { isDaemon = true; start() }
                }
            }.apply { isDaemon = true; start() }
            val job = launch(Dispatchers.IO) {
                NetworkDiagnostics.run(DiagnosticKind.INTERNET, ProxyEndpoint("127.0.0.1", listener.localPort, "socks5"), emptyList(),
                    ProbeRoute(resolve = { InetAddress.getLoopbackAddress() }, register = { observed.add(it) }))
            }
            assertTrue(ready.await(4, TimeUnit.SECONDS))
            withTimeout(3000) { job.cancelAndJoin() }
            assertTrue(observed.isNotEmpty()); assertTrue(observed.all { it.isClosed })
            fixture.join(1000)
        }
    }
}
