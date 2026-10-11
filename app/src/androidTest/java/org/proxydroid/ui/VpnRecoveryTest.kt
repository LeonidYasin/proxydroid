package org.proxydroid.ui

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.Profile
import org.proxydroid.ProxyDroidVpnService
import org.proxydroid.utils.*

class VpnRecoveryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun grantConsent() {
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set org.proxydroid ACTIVATE_VPN allow")
        ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        assertNull("Emulator must grant consent before exercising actual TUN", VpnService.prepare(context))
    }
    private fun waitFor(predicate: () -> Boolean) {
        val until = System.nanoTime() + 10_000_000_000L
        while (!predicate() && System.nanoTime() < until) Thread.sleep(50)
        assertTrue(predicate())
    }
    @Test fun repeatedReconnectCreatesFreshSessionAndStopIsAcknowledged() = runBlocking {
        grantConsent()
        try {
            repeat(3) {
                val profile = Profile().apply { init(); host = "10.0.2.2"; port = 1080 }
                androidx.core.content.ContextCompat.startForegroundService(context,
                    Intent(context, ProxyDroidVpnService::class.java).putExtras(ProxyController.buildExtras(profile)))
                waitFor { Utils.isWorking() }
                assertNotNull(Utils.activeRouting.value)
                assertTrue(VpnRecovery.stopAndAwait(context))
                assertFalse(Utils.isWorking()); assertFalse(Utils.isConnecting())
                assertNull(Utils.activeRouting.value); assertNull(Utils.activeUpstream.value)
            }
        } finally { VpnRecovery.stopAndAwait(context) }
        Unit
    }
    @Test fun stopDuringWaitingCannotEstablishLateVpn() = runBlocking {
        grantConsent()
        val profile = Profile().apply { init(); host = "127.0.0.1"; port = 9 }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context,
                Intent(context, ProxyDroidVpnService::class.java).putExtras(ProxyController.buildExtras(profile))
                    .putExtra(ProxyDroidVpnService.EXTRA_WAIT_NETWORK, true))
            waitFor { Utils.isConnecting() }
            assertTrue(VpnRecovery.stopAndAwait(context))
            Thread.sleep(2500)
            assertFalse(Utils.isWorking()); assertFalse(Utils.isConnecting()); assertNull(Utils.activeUpstream.value)
        } finally { VpnRecovery.stopAndAwait(context) }
        Unit
    }
}
