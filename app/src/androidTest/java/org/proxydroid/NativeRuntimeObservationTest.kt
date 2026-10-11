package org.proxydroid

import android.os.ParcelFileDescriptor
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.utils.Tun2SocksHelper

/** Actual JNI root exit; a pipe fixture is explicitly NOT an end-to-end Android VPN test. */
class NativeRuntimeObservationTest {
    private fun waitFor(predicate: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000L
        while (!predicate() && System.nanoTime() < until) Thread.sleep(30)
        assertTrue(predicate())
    }
    @Test fun closedTunReadEndsNativeRootAndStopAllowsAnotherStart() {
        repeat(2) {
            val pipe = ParcelFileDescriptor.createPipe()
            val helper = Tun2SocksHelper()
            try {
                assertTrue(helper.start(ProxyDroidVpnService(), pipe[0].fd, 1500, "socks5", "127.0.0.1", 1080, null, null))
                pipe[1].close()
                waitFor { Tun2SocksHelper.readRuntimeHealth()?.let { h -> !h.running && h.taskFinished } == true }
                assertEquals(7L, Tun2SocksHelper.readRuntimeHealth()!!.coreExitCode)
                helper.stop()
                assertFalse(helper.isRunning())
                assertFalse(Tun2SocksHelper.readRuntimeHealth()!!.running)
            } finally { helper.stop(); pipe.forEach { runCatching { it.close() } } }
        }
    }
    @Test fun invalidDescriptorIsAnIoStartupFailureNotProxyUnreachable() {
        val helper = Tun2SocksHelper()
        try {
            assertFalse(helper.start(ProxyDroidVpnService(), -1, 1500, "socks5", "127.0.0.1", 1080, null, null))
            assertTrue(helper.lastStartFailure!!.contains("NATIVE_IO_INIT_FAILED"))
            assertFalse(Tun2SocksHelper.readRuntimeHealth()!!.running)
        } finally { helper.stop() }
    }
}
