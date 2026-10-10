package org.proxydroid.utils

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DiagnosticTransportTest {
    private fun failure(type: String, handler: (DataInputStream, DataOutputStream) -> Unit): Pair<ProbeProblem, List<ProbeStep>> {
        ServerSocket(0).use { listener ->
            val task = FutureTask<Unit> { listener.accept().use { socket -> socket.soTimeout = 4000; handler(DataInputStream(socket.getInputStream()), DataOutputStream(socket.getOutputStream())) } }
            Thread(task).apply { isDaemon = true; start() }
            val steps = mutableListOf<ProbeStep>()
            val error = try { ProxyTransport.connect(ProxyEndpoint("127.0.0.1", listener.localPort, type), "example.com", 443, steps::add); fail("Failure expected"); error("unreachable") }
                catch (e: ProbeProblem) { e }
            task.get(5, TimeUnit.SECONDS)
            return error to steps
        }
    }
    @Test fun http407IsAuthFailureNotDeadPort() {
        val (error, steps) = failure("http") { input, output ->
            while (ProxyTransport.readLine(input).isNotEmpty()) { }
            output.writeBytes("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n"); output.flush()
        }
        assertEquals("CONNECT_HTTP_407", error.code); assertEquals(ProbeStage.AUTH, error.stage)
        assertTrue(steps.any { it.ok && it.stage == ProbeStage.TCP })
    }
    @Test fun http502IsSourceConnectFailureNotTargetHttp() {
        val (error, _) = failure("http") { input, output ->
            while (ProxyTransport.readLine(input).isNotEmpty()) { }
            output.writeBytes("HTTP/1.1 502 Bad Gateway\r\n\r\n"); output.flush()
        }
        assertEquals("CONNECT_HTTP_502", error.code); assertEquals(ProbeStage.CONNECT, error.stage)
    }
    @Test fun socks5PreservesRemoteHostUnreachableReply() {
        val (error, steps) = failure("socks5") { input, output ->
            input.readFully(ByteArray(3)); output.write(byteArrayOf(5, 0)); output.flush()
            input.readFully(ByteArray(4)); input.readFully(ByteArray(input.readUnsignedByte())); input.readUnsignedShort()
            output.write(byteArrayOf(5, 4, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()
        }
        assertEquals("SOCKS5_REP_4", error.code); assertEquals(ProbeStage.CONNECT, error.stage)
        assertTrue(steps.any { it.ok && it.stage == ProbeStage.AUTH })
    }
    @Test fun socks5MethodRejectionIsSpecific() {
        val (error, _) = failure("socks5") { input, output -> input.readFully(ByteArray(3)); output.write(byteArrayOf(5, 255.toByte())); output.flush() }
        assertEquals("AUTH_METHOD_REJECTED", error.code)
    }
    @Test fun untrustedRawHttpStatusIsNotIncludedInReport() {
        val (error, _) = failure("http") { input, output ->
            while (ProxyTransport.readLine(input).isNotEmpty()) { }
            output.writeBytes("sentinel-user:sentinel-password\r\n"); output.flush()
        }
        assertFalse(error.safeDetail.contains("sentinel"))
    }
}
