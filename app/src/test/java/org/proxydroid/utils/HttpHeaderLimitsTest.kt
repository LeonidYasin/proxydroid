package org.proxydroid.utils
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.*
import org.junit.Test
class HttpHeaderLimitsTest {
    private fun input(s: String) = DataInputStream(ByteArrayInputStream(s.toByteArray(Charsets.US_ASCII)))
    @Test fun longCookieIsAcceptedButNeverRetained() {
        val headers = ProxyTransport.readHeaders(input("Set-Cookie: " + "x".repeat(6000) + "\r\nContent-Length: 0\r\n\r\n"))
        assertEquals(mapOf("content-length" to "0"), headers)
    }
    @Test fun oversizedHeaderStillRejected() {
        assertThrows(ProbeProblem::class.java) { ProxyTransport.readHeaders(input("X: " + "x".repeat(8192) + "\r\n\r\n")) }
    }
    @Test fun totalHeadersStillBounded() {
        assertThrows(ProbeProblem::class.java) { ProxyTransport.readHeaders(input(("X: " + "x".repeat(7000) + "\r\n").repeat(10) + "\r\n")) }
    }
    @Test fun statusLineKeepsSmallLimit() {
        assertThrows(ProbeProblem::class.java) { ProxyTransport.readLine(input("HTTP/1.1 200 " + "x".repeat(1200))) }
    }
    @Test fun duplicateFramingStillRejected() {
        assertThrows(ProbeProblem::class.java) { ProxyTransport.readHeaders(input("Content-Length: 0\r\nContent-Length: 1\r\n\r\n")) }
    }
}
