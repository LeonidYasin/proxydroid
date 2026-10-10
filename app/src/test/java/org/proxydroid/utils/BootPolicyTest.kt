package org.proxydroid.utils
import org.junit.Assert.*
import org.junit.Test
class BootPolicyTest {
    @Test fun onlyEnabledUnlockedBootEventsStart() {
        assertTrue(BootPolicy.shouldStart("android.intent.action.BOOT_COMPLETED", true, true))
        assertTrue(BootPolicy.shouldStart("android.intent.action.MY_PACKAGE_REPLACED", true, true))
        assertFalse(BootPolicy.shouldStart("android.intent.action.BOOT_COMPLETED", false, true))
        assertFalse(BootPolicy.shouldStart("android.intent.action.BOOT_COMPLETED", true, false))
        assertFalse(BootPolicy.shouldStart("android.intent.action.LOCKED_BOOT_COMPLETED", true, false))
        assertFalse(BootPolicy.shouldStart("android.net.conn.CONNECTIVITY_CHANGE", true, true))
        assertFalse(BootPolicy.shouldStart(null, true, true))
    }
    @Test fun endpointNeverPrintsCredentials() {
        val endpoint = ProxyEndpoint("127.0.0.1", 10808, "socks5", "private-user", "private-password")
        assertEquals("socks5://127.0.0.1:10808", endpoint.toString())
    }
}
