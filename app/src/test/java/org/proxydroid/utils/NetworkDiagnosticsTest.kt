package org.proxydroid.utils

import java.io.DataInputStream
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test

class NetworkDiagnosticsTest {
    private val web = NetworkDiagnostics.internetTargets.first()
    private fun reply(): ByteArray {
        val query = NetworkDiagnostics.dnsQuery()
        val result = query.copyOf().apply { this[2] = 0x81.toByte(); this[3] = 0x80.toByte(); this[7] = 1 }
        return result + byteArrayOf(0xc0.toByte(), 0x0c, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 93, 184.toByte(), 216.toByte(), 34)
    }
    @Test fun dnsReplyMatchesQuestionAndHasRealAnswerRecord() { NetworkDiagnostics.validateDnsReply(reply(), NetworkDiagnostics.dnsQuery()) }
    @Test fun dnsIdMismatchIsRejected() { assertThrows(ProbeProblem::class.java) { NetworkDiagnostics.validateDnsReply(reply().apply { this[0] = 0 }, NetworkDiagnostics.dnsQuery()) } }
    @Test fun dnsServfailKeepsSpecificCode() {
        val e = assertThrows(ProbeProblem::class.java) { NetworkDiagnostics.validateDnsReply(reply().apply { this[3] = 0x82.toByte() }, NetworkDiagnostics.dnsQuery()) }
        assertEquals("DNS_RCODE_2", e.code)
    }
    @Test fun declaredAnswersWithoutRecordBytesAreRejected() { assertThrows(ProbeProblem::class.java) { NetworkDiagnostics.validateDnsReply(reply().copyOf(NetworkDiagnostics.dnsQuery().size + 1), NetworkDiagnostics.dnsQuery()) } }
    @Test fun boundedChunkedDnsBodyIsDecoded() {
        val bytes = reply()
        val wire = (bytes.size.toString(16) + "\r\n").toByteArray() + bytes + "\r\n0\r\n\r\n".toByteArray()
        assertArrayEquals(bytes, NetworkDiagnostics.readBody(DataInputStream(wire.inputStream()), mapOf("transfer-encoding" to "chunked")))
    }
    @Test fun ambiguousAndOversizedFramingAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NetworkDiagnostics.readBody(DataInputStream(byteArrayOf().inputStream()), mapOf("content-length" to "8193")) }
        assertThrows(IllegalArgumentException::class.java) { NetworkDiagnostics.readBody(DataInputStream(byteArrayOf().inputStream()), mapOf("content-length" to "12", "transfer-encoding" to "chunked")) }
    }
    @Test fun duplicateFramingHeadersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ProxyTransport.readHeaders(DataInputStream("Content-Length: 12\r\nContent-Length: 15\r\n\r\n".byteInputStream())) }
    }
    @Test fun directSuccessAndDohDoNotProveProxiedInternet() {
        val direct = TargetDiagnostic(web.copy(direct = true), emptyList(), true)
        val doh = TargetDiagnostic(NetworkDiagnostics.internetTargets.last(), emptyList(), true)
        val failed = TargetDiagnostic(web, emptyList(), false, "AUTH_REJECTED")
        val report = DiagnosticReport(DiagnosticKind.INTERNET, "hidden", emptyList(), listOf(direct, doh, failed))
        assertFalse(report.internetOk)
        assertTrue(report.text().contains("авторизацию"))
    }
    @Test fun successfulHttpsWithFailedDohPointsToDnsNotCache() {
        val results = listOf(TargetDiagnostic(web, emptyList(), true)) + NetworkDiagnostics.internetTargets.filter { it.doh }.map { TargetDiagnostic(it, emptyList(), false, "CONNECT_HTTP_403") }
        assertTrue(DiagnosticReport.recommendations(results, DiagnosticKind.INTERNET).any { it.contains("DNS внутри туннеля") })
    }
    @Test fun errorsAndEndpointNeverContainCredentialOrRawServerText() {
        val secret = "sentinel-password"
        assertFalse(NetworkDiagnostics.safeEndpoint(ProxyEndpoint("localhost", 10808, "http", "sentinel-user", secret)).contains("sentinel"))
        assertFalse(NetworkDiagnostics.safeEndpoint(ProxyEndpoint("user:$secret@host", 10808, "http")).contains(secret))
        val problem = ProxyTransport.classify(ProbeStage.CONNECT, java.io.IOException("Proxy-Authorization: $secret"))
        assertFalse(problem.safeDetail.contains(secret))
        val e = assertThrows(ProbeProblem::class.java) { ProxyTransport.statusCode("bad response $secret") }
        assertFalse(e.safeDetail.contains(secret))
    }
    @Test fun closedRegistryImmediatelyClosesNewSocket() {
        val registry = ProbeSockets(); val socket = Socket(); registry.close()
        assertThrows(java.net.SocketException::class.java) { registry.register(socket) }
        assertTrue(socket.isClosed)
    }
    @Test fun actualAllowAndBypassPoliciesAreDistinguished() {
        assertFalse(ActiveRouting(setOf("browser"), setOf("org.proxydroid")).routed("com.google.android.youtube.tv"))
        assertTrue(ActiveRouting(null, setOf("org.proxydroid")).routed("com.google.android.youtube.tv"))
        assertFalse(ActiveRouting(null, setOf("com.google.android.youtube.tv")).routed("com.google.android.youtube.tv"))
    }
}
