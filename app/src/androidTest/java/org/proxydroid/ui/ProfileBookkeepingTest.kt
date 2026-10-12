package org.proxydroid.ui

import android.app.Application
import android.content.Context
import android.preference.PreferenceManager
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.proxydroid.Profile

class ProfileBookkeepingTest {
    private class TestApp(base: Context) : Application() {
        private val store = "profile-test-${System.nanoTime()}"
        init { attachBaseContext(base) }
        override fun getSharedPreferences(name: String?, mode: Int) = super.getSharedPreferences(store, mode)
    }
    private fun withModel(test: (MainViewModel, android.content.SharedPreferences) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = TestApp(instrumentation.targetContext)
        val prefs = PreferenceManager.getDefaultSharedPreferences(app)
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            val vm = MainViewModel(app, { null }, false)
            store.put("test", vm)
            try { test(vm, prefs) } finally { store.clear(); prefs.edit().clear().commit() }
        }
    }

    @Test fun namesPersistAcrossSwitchAndReloadWithoutLosingPassword() = withModel { vm, prefs ->
        vm.renameCurrent("Home")
        vm.updateProfile { host = "fixture-home"; user = "fixture-user"; password = "fixture-secret"; isAuth = true }
        vm.newProfile("Office")
        assertEquals("2", vm.state.value.currentProfileId)
        assertEquals(listOf("Home", "Office"), vm.state.value.profiles.map { it.name })
        vm.renameCurrent("Work")
        assertEquals("Home|Work|", prefs.getString("profileEntries", null))
        vm.selectProfile("1")
        assertEquals("fixture-secret", vm.state.value.profile.password)
        vm.selectProfile("2"); vm.reload()
        assertEquals("Work", vm.state.value.profile.name)
        assertEquals(listOf("Home", "Work"), vm.state.value.profiles.map { it.name })
        assertEquals("", vm.state.value.profile.password)
    }

    @Test fun deletionCannotReappearAfterReloadOrStaleSelection() = withModel { vm, prefs ->
        vm.renameCurrent("Home"); vm.newProfile("Delete me"); vm.newProfile("Keep me")
        vm.selectProfile("2"); vm.deleteCurrent(); vm.reload()
        assertEquals(listOf("1", "3"), vm.state.value.profiles.map { it.id })
        assertFalse(prefs.contains("2")); assertFalse(prefs.contains("profile2"))
        assertEquals("1", vm.state.value.currentProfileId)
        vm.selectProfile("2")
        assertEquals("1", vm.state.value.currentProfileId)
        vm.selectProfile("3")
        assertEquals("Keep me", vm.state.value.profile.name)
    }

    @Test fun deletingLastProfileClearsSecretsAndAutomationInActualPreferences() = withModel { vm, prefs ->
        vm.updateProfile {
            name = "Private"; host = "fixture-host"; password = "fixture-secret"; user = "fixture-user"
            isAuth = true; proxyApps = "org.example.player"; isBypassApps = true
            isAutoConnect = true; autoStartOnBoot = true
        }
        vm.deleteCurrent(); vm.reload()
        val p = vm.state.value.profile
        assertEquals("", p.password); assertEquals("", p.user); assertEquals("", p.host)
        assertFalse(p.isAuth); assertFalse(p.autoStartOnBoot); assertFalse(p.isAutoConnect)
        assertEquals("", p.proxyApps); assertFalse(p.isBypassApps)
        assertEquals(Profile.DEFAULT_PORT, p.port)
        assertEquals(listOf("1"), vm.state.value.profiles.map { it.id })
        val saved = Profile().apply { decodeJson(prefs.getString("1", "")!!) }
        assertEquals("", saved.password); assertEquals("Profile 1", saved.name)
    }

    @Test fun rapidCreateRenameSwitchSequenceKeepsNamesAndIdsConsistent() = withModel { vm, _ ->
        // No IO coroutine may later overwrite a newer selection or rename.
        vm.renameCurrent("One"); vm.newProfile("Two"); vm.renameCurrent("Second")
        vm.newProfile("Three"); vm.selectProfile("1"); vm.renameCurrent("First")
        assertEquals("1", vm.state.value.currentProfileId)
        assertEquals(listOf("First", "Second", "Three"), vm.state.value.profiles.map { it.name })
        vm.reload()
        assertEquals("First", vm.state.value.profile.name)
        assertEquals(listOf("1", "2", "3"), vm.state.value.profiles.map { it.id })
    }
}
