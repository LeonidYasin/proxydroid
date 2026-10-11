package org.proxydroid.utils

import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LanProxyDiscoveryTest {
    @Test fun planPrioritisesGatewayAndNeverLeavesActualSubnet() {
        val plan = LanProxyDiscovery.plan("192.168.50.17", 24, "192.168.50.1", "8.8.8.8", 12345)
        assertEquals("192.168.50.1", plan.hosts.first())
        assertFalse(plan.hosts.contains("192.168.50.17"))
        assertFalse(plan.hosts.contains("192.168.50.0")); assertFalse(plan.hosts.contains("192.168.50.255"))
        assertTrue(plan.hosts.all { it.startsWith("192.168.50.") })
        assertEquals(253, plan.hosts.size); assertEquals(12345, plan.ports.first()); assertEquals(8, plan.ports.size)
        assertFalse(plan.partial)
    }
    @Test fun largeNetworksAreBoundedAndScopeIsMarkedPartial() {
        val plan = LanProxyDiscovery.plan("10.75.2.9", 16, "10.75.0.1", "10.75.1.22", 10808)
        assertTrue(plan.partial); assertTrue(plan.scope.contains("/24")); assertEquals(255, plan.hosts.size)
        assertEquals(listOf("10.75.0.1", "10.75.1.22"), plan.hosts.take(2))
        assertTrue(plan.hosts.all { it.startsWith("10.75.") })
    }
    @Test fun smallSubnetExcludesNetworkBroadcastAndSelf() {
        assertEquals(listOf("172.16.0.1"), LanProxyDiscovery.plan("172.16.0.2", 30, "172.16.0.1", null, 10808).hosts)
    }
    @Test fun publicLoopbackMulticastAndInvalidPrefixesRejected() {
        for ((host, prefix) in listOf("8.8.8.8" to 24, "127.0.0.1" to 24, "224.0.0.1" to 24, "192.168.1.2" to 0, "192.168.1.2" to 31, "172.32.1.2" to 24)) {
            try { LanProxyDiscovery.plan(host, prefix, null, null, 10808); fail("Rejected: $host/$prefix") }
            catch (_: IllegalArgumentException) { }
        }
    }
    private fun server(handler: (Socket) -> Unit, test: (Int) -> Unit) {
        val listener = ServerSocket(0)
        val worker = Executors.newCachedThreadPool()
        worker.execute { try { while (!listener.isClosed) {
            val client = listener.accept()
            worker.execute { client.use { try { it.soTimeout = 1500; handler(it) } catch (_: Exception) { } } }
        } } catch (_: Exception) { } }
        try { test(listener.localPort) } finally { listener.close(); worker.shutdownNow(); worker.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Test fun socksGreetingConfirmsNoAuthOrRequiredAuthWithoutSendingPassword() {
        for (method in listOf(0, 2)) server({ socket ->
            val greeting = ByteArray(4); java.io.DataInputStream(socket.getInputStream()).readFully(greeting)
            assertArrayEquals(byteArrayOf(5, 2, 0, 2), greeting)
            socket.getOutputStream().apply { write(byteArrayOf(5, method.toByte())); flush() }
            assertEquals(-1, socket.getInputStream().read())
        }) { port ->
            val sockets = ProbeSockets()
            try { assertEquals(LanProxy("127.0.0.1", port, "socks5", method == 2), LanProxyDiscovery.identify("127.0.0.1", port, ProbeRoute(), sockets)) }
            finally { sockets.close() }
        }
    }
    @Test fun httpConnectAndProxyAuthConfirmedButRegularWebServerIgnored() {
        for (status in listOf(200, 407, 404)) server({ socket ->
            val input = socket.getInputStream(); val first = input.read()
            if (first != 'C'.code) return@server
            val request = StringBuilder("C")
            while (!request.endsWith("\r\n\r\n")) request.append(input.read().takeIf { it >= 0 }?.toChar() ?: error("EOF"))
            assertFalse(request.contains("Proxy-Authorization"))
            socket.getOutputStream().apply {
                write("HTTP/1.1 $status Test\r\n${if (status == 407) "Proxy-Authenticate: Basic realm=local\r\n" else ""}\r\n".toByteArray()); flush()
            }
        }) { port ->
            val sockets = ProbeSockets()
            try { val proxy = LanProxyDiscovery.identify("127.0.0.1", port, ProbeRoute(), sockets)
                if (status == 404) assertNull(proxy) else assertEquals(LanProxy("127.0.0.1", port, "http", status == 407), proxy)
            } finally { sockets.close() }
        }
    }
    @Test fun cancellationClosesHangingHandshakeAndReturnsPromptly() = runBlocking {
        server({ socket -> while (socket.getInputStream().read() >= 0) { } }) { port ->
            runBlocking {
                val job = launch { LanProxyDiscovery.scan(LanScanPlan(listOf("127.0.0.1"), listOf(port), "test", false), ProbeRoute()) }
                delay(100)
                val started = System.nanoTime()
                job.cancelAndJoin()
                assertTrue((System.nanoTime() - started) / 1_000_000 < 1500)
            }
        }
    }
}
