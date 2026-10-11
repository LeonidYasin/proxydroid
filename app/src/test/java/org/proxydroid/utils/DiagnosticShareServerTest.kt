package org.proxydroid.utils
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Socket
import java.net.URI
import org.junit.Assert.*
import org.junit.Test
class DiagnosticShareServerTest {
    private fun request(url: String, path: String = URI(url).rawPath): String {
        val uri = URI(url)
        return Socket(uri.host, uri.port).use { s ->
            s.soTimeout = 3000
            s.getOutputStream().write("GET $path HTTP/1.1\r\nHost: local\r\n\r\n".toByteArray())
            s.getInputStream().bufferedReader().readText()
        }
    }
    @Test fun onlyTokenRouteServesImmutableReport() {
        DiagnosticShareServer(InetAddress.getByName("127.0.0.1") as Inet4Address, "Тестовый отчёт").use { server ->
            assertTrue(request(server.url).endsWith("Тестовый отчёт"))
            assertTrue(request(server.url, "/diagnostics.txt").startsWith("HTTP/1.1 404"))
            assertTrue(request(server.url, "/../diagnostics.txt").startsWith("HTTP/1.1 404"))
            assertTrue(request(server.url).contains("Cache-Control: no-store"))
        }
    }
    @Test fun expiryAndCloseRevokeDownload() {
        val server = DiagnosticShareServer(InetAddress.getByName("127.0.0.1") as Inet4Address, "report", 100)
        Thread.sleep(700)
        assertFalse(server.isOpen)
        assertThrows(Exception::class.java) { request(server.url) }
        server.close()
    }
    @Test fun versionedFilenameIsSafeAndDoesNotReadFiles() {
        DiagnosticShareServer(InetAddress.getByName("127.0.0.1") as Inet4Address, "report", filename = "LocProxyTV-v3.8.9-diagnostics-20261011-161155.txt").use { server ->
            assertTrue(request(server.url).contains("filename=LocProxyTV-v3.8.9-diagnostics-20261011-161155.txt"))
        }
        for (bad in listOf("../secret.txt", "x\r\nInjected: yes", ".hidden", "x".repeat(101))) {
            assertThrows(IllegalArgumentException::class.java) { DiagnosticShareServer(InetAddress.getByName("127.0.0.1") as Inet4Address, "report", filename = bad) }
        }
    }
    @Test fun reportSizeBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            DiagnosticShareServer(InetAddress.getByName("127.0.0.1") as Inet4Address, "x".repeat(524289))
        }
    }
}
