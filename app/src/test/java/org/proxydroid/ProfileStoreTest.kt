package org.proxydroid

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class ProfileStoreTest {
    /** In-memory preference semantics, including atomic editor.apply, without Android runtime. */
    private class Memory {
        val values = mutableMapOf<String, Any>()
        val prefs = mock(SharedPreferences::class.java)
        var applies = 0
        init {
            doAnswer { values[it.arguments[0]] ?: it.arguments[1] }
                .`when`(prefs).getString(anyString(), nullable(String::class.java))
            doAnswer { values[it.arguments[0]] ?: it.arguments[1] }
                .`when`(prefs).getBoolean(anyString(), anyBoolean())
            doAnswer {
                val editor = mock(SharedPreferences.Editor::class.java)
                val pending = mutableMapOf<String, Any?>()
                doAnswer { pending[it.getArgument(0)] = it.arguments[1]; editor }
                    .`when`(editor).putString(anyString(), nullable(String::class.java))
                doAnswer { pending[it.getArgument(0)] = it.arguments[1]; editor }
                    .`when`(editor).putBoolean(anyString(), anyBoolean())
                doAnswer { pending[it.getArgument(0)] = null; editor }
                    .`when`(editor).remove(anyString())
                doAnswer {
                    pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                    applies++; null
                }.`when`(editor).apply()
                editor
            }.`when`(prefs).edit()
        }
        fun json(id: String) = Profile().apply { decodeJson(values[id] as String) }
        fun flat() = Profile().apply { getProfile(prefs) }
    }
    private fun profile(name: String, host: String = "fixture", password: String = "") = Profile().apply {
        this.name = name; this.host = host; this.password = password
    }

    @Test fun bootstrapKeepsLegacyFlatCredentialsAndCustomPort() {
        val m = Memory()
        m.values.putAll(mapOf("profile" to "7", "name" to "Hotspot", "host" to "legacy",
            "port" to "9050", "password" to "fixture-secret", "isAuth" to true))
        val selected = ProfileStore(m.prefs).current()
        assertEquals("7", selected.id); assertEquals("9050", m.values["port"])
        assertEquals("fixture-secret", m.json("7").password)
        assertEquals("7|", m.values["profileValues"]); assertEquals("Hotspot", m.values["profile7"])
        assertEquals(1, m.applies)
    }
    @Test fun createAndRenameUpdateEveryLabelWithoutLosingSavedCredentials() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        store.save(profile("Old", password = "fixture-secret"))
        val before = m.applies
        assertEquals("2", store.create(profile("Office")).id)
        assertEquals(before + 1, m.applies)
        store.save(profile("Renamed", host = "second"))
        assertEquals("Renamed", store.current().entries.last().name)
        assertEquals("Old|Renamed|", m.values["profileEntries"])
        assertEquals("Renamed", m.values["profile2"])
        assertEquals("Renamed", m.json("2").name)
        assertEquals("fixture-secret", store.select("1")!!.profile.password)
        assertEquals("second", store.select("2")!!.profile.host)
    }
    @Test fun deletionRemovesIndexJsonAndLabelAndStaysDeletedOnReload() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        store.save(profile("One")); store.create(profile("Two")); store.create(profile("Three"))
        store.select("2")
        val before = m.applies
        val selected = store.deleteCurrent()
        assertEquals(before + 1, m.applies)
        assertEquals("1", selected.id); assertEquals(listOf("1", "3"), selected.entries.map { it.id })
        assertFalse(m.values.containsKey("2")); assertFalse(m.values.containsKey("profile2"))
        assertEquals("1|3|", m.values["profileValues"])
        assertEquals("One|Three|", m.values["profileEntries"])
        assertEquals(listOf("1", "3"), ProfileStore(m.prefs).current().entries.map { it.id })
        assertNull(store.select("2"))
    }
    @Test fun lastDeletionCreatesCleanDefaultIncludingFlatPasswordRoutingAndAutomation() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        store.save(profile("Private", password = "fixture-secret").apply {
            user = "fixture-user"; isAuth = true; proxyApps = "org.example.player"
            isBypassApps = true; useGatewayAsHost = true; autoStartOnBoot = true; isAutoConnect = true
        })
        val selected = store.deleteCurrent()
        assertEquals("1", selected.id); assertEquals(listOf("Profile 1"), selected.entries.map { it.name })
        val p = m.flat()
        assertEquals("", p.password); assertEquals("", p.user); assertEquals("", p.host)
        assertEquals("", p.proxyApps); assertFalse(p.isAuth); assertFalse(p.isBypassApps)
        assertFalse(p.useGatewayAsHost); assertFalse(p.autoStartOnBoot); assertFalse(p.isAutoConnect)
        assertEquals(Profile.DEFAULT_PORT, p.port); assertEquals("", m.json("1").password)
        assertEquals("1|", m.values["profileValues"]); assertEquals("Profile 1|", m.values["profileEntries"])
    }
    @Test fun malformedDuplicateIndexIsNormalizedWithoutDiscardingCurrentProfile() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        m.values.putAll(mapOf("profile" to "3", "profileValues" to "2|2|junk|-1|0| 03 |",
            "name" to "Current", "host" to "keep-me", "2" to profile("Saved").toString()))
        val selected = store.current()
        assertEquals(listOf("2", "3"), selected.entries.map { it.id })
        assertEquals("2|3|", m.values["profileValues"]); assertEquals("keep-me", selected.profile.host)
        assertEquals("4", store.create(profile("New")).id)
    }
    @Test fun highestIdDoesNotOverflowOrOverwriteAnotherProfile() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        m.values.putAll(mapOf("profile" to "2147483647", "profileValues" to "1|2147483647|",
            "name" to "Maximum", "1" to profile("One", password = "keep-secret").toString()))
        val selected = store.create(profile("New"))
        assertEquals("2", selected.id); assertEquals("keep-secret", m.json("1").password)
        assertEquals("Maximum", m.json("2147483647").name)
    }
    @Test fun staleSelectionIsNoOpAndDoesNotChangeCurrentFlatFields() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        store.save(profile("Current", password = "keep-secret"))
        val snapshot = m.values.toMap(); val before = m.applies
        assertNull(store.select("99")); assertNull(store.select("name"))
        assertEquals(snapshot, m.values); assertEquals(before, m.applies)
    }
    @Test fun oneSelectionAtomicallyPersistsLatestFlatEditsAndSelectedFields() {
        val m = Memory(); val store = ProfileStore(m.prefs)
        store.save(profile("One")); store.create(profile("Two"))
        m.values["name"] = "Latest Two"; m.values["password"] = "latest-secret"
        val before = m.applies
        val selected = store.select("1")!!
        assertEquals(before + 1, m.applies); assertEquals("One", selected.profile.name)
        assertEquals("latest-secret", m.json("2").password)
        assertEquals("One|Latest Two|", m.values["profileEntries"])
    }
}
