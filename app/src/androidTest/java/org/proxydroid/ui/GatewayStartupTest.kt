package org.proxydroid.ui

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.ProxyDroidReceiver
import android.content.Intent

class GatewayStartupTest {
    private class TestApp(base: Context) : Application() {
        private val store = "gateway-test-${System.nanoTime()}"
        init { attachBaseContext(base) }
        override fun getSharedPreferences(name: String?, mode: Int) = super.getSharedPreferences(store, mode)
    }
    private fun run(auto: Boolean, test: (TestApp, MainViewModel) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = TestApp(instrumentation.targetContext)
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        prefs.edit().putString("host", "old-host").putBoolean("useGatewayAsHost", auto).apply()
        val store = ViewModelStore()
        lateinit var vm: MainViewModel
        instrumentation.runOnMainSync { vm = MainViewModel(app, { "192.168.50.1" }, false); store.put("test", vm) }
        try {
            val until = System.currentTimeMillis() + 5000
            while ((vm.state.value.gatewayIp == null || (auto && vm.state.value.profile.host == "old-host")) && System.currentTimeMillis() < until) Thread.sleep(20)
            test(app, vm)
        } finally {
            instrumentation.runOnMainSync { store.clear() }; prefs.edit().clear().apply()
        }
    }
    @Test fun startupAutoGatewayImmediatelyPopulatesAndPersistsHost() = run(true) { app, vm ->
        assertEquals("192.168.50.1", vm.state.value.profile.host)
        assertEquals("192.168.50.1", PreferenceManager.getDefaultSharedPreferences(app).getString("host", null))
    }
    @Test fun startupDetectsGatewayButPreservesManualHostWhenDisabled() = run(false) { _, vm ->
        assertEquals("192.168.50.1", vm.state.value.gatewayIp); assertEquals("old-host", vm.state.value.profile.host)
    }
    @Test fun freshGatewayDoesNotHideVpnCleanupError() = run(true) { _, vm ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            vm.reportConnectionError("VPN cleanup incomplete: test failure")
            vm.refreshGateway()
        }
        Thread.sleep(300)
        assertEquals("VPN cleanup incomplete: test failure", vm.state.value.lastError)
        instrumentation.runOnMainSync { vm.clearError() }
    }
    @Test fun bootReceiverInvokesBackgroundStarterOnlyWhenEnabled() = run(false) { app, _ ->
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        var starts = 0
        val receiver = ProxyDroidReceiver().apply { starter = { _, profile -> assertTrue(profile.autoStartOnBoot); starts++ } }
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED)); assertEquals(0, starts)
        prefs.edit().putBoolean("autoStartOnBoot", true).apply()
        receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED)); assertEquals(1, starts)
        receiver.onReceive(app, Intent("android.net.conn.CONNECTIVITY_CHANGE")); assertEquals(1, starts)
    }
}
