package org.proxydroid.ui

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.utils.*

class LanProfileSelectionTest {
    private class TestApp(base: Context) : Application() {
        private val store = "lan-test-${System.nanoTime()}"
        init { attachBaseContext(base) }
        override fun getSharedPreferences(name: String?, mode: Int) = super.getSharedPreferences(store, mode)
    }
    @Test fun selectionPreservesOldProfileAndRoutingWithoutLeakingItsPassword() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = TestApp(instrumentation.targetContext)
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        prefs.edit().putString("profile", "1").putString("host", "old-host").putString("user", "saved-user")
            .putString("password", "saved-secret").putBoolean("isAuth", true).putString("proxyApps", "org.example.player")
            .putBoolean("isBypassApps", false).apply()
        val proxy = LanProxy("192.168.50.1", 10808, "socks5", false)
        lateinit var vm: MainViewModel
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            vm = MainViewModel(app, { null }, false, { _, _, _ -> LanScanResult(listOf(proxy), 1, 1, 1, false) })
            store.put("test", vm); vm.scanLan()
        }
        try {
            val until = System.currentTimeMillis() + 5000
            while (vm.state.value.lanProxies.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(20)
            assertEquals(listOf(proxy), vm.state.value.lanProxies)
            instrumentation.runOnMainSync { assertTrue(vm.selectLanProxy(proxy)) }
            val selected = vm.state.value
            assertEquals("2", selected.currentProfileId)
            assertEquals(proxy.host, selected.profile.host); assertEquals(proxy.port, selected.profile.port)
            assertEquals("", selected.profile.password); assertEquals("", selected.profile.user)
            assertFalse(selected.profile.isAuth); assertFalse(selected.profile.useGatewayAsHost)
            assertEquals("org.example.player", selected.profile.proxyApps); assertFalse(selected.profile.isBypassApps)
            val old = org.proxydroid.Profile().apply { decodeJson(prefs.getString("1", "")!!) }
            assertEquals("old-host", old.host); assertEquals("saved-secret", old.password)
            assertTrue(selected.profiles.any { it.id == "1" }); assertTrue(selected.profiles.any { it.id == "2" })
            instrumentation.runOnMainSync { assertFalse(vm.selectLanProxy(proxy.copy(host = "192.168.50.99"))) }
        } finally { instrumentation.runOnMainSync { store.clear() }; prefs.edit().clear().apply() }
    }
}
