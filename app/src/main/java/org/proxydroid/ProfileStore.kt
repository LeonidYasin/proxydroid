package org.proxydroid

import android.content.SharedPreferences
import androidx.annotation.MainThread

/**
 * Legacy-compatible profile bookkeeping. Call on the main thread, as UI writes are.
 * Each operation applies flat fields, JSON, selection, labels and the index together.
 * Does not alter the immutable configuration of an already running VPN.
 */
@MainThread
internal class ProfileStore(private val settings: SharedPreferences) {
    data class Entry(val id: String, val name: String)
    data class Selection(val id: String, val profile: Profile, val entries: List<Entry>)

    private fun validId(raw: String?): String? = raw?.trim()?.toIntOrNull()
        ?.takeIf { it > 0 }?.toString()
    private fun currentId(): String = validId(settings.getString("profile", "1")) ?: "1"
    private fun ids(): List<String> = (settings.getString("profileValues", "").orEmpty()
        .split('|').mapNotNull(::validId) + currentId()).distinct()
    private fun flat(): Profile = Profile().apply { getProfile(settings) }
    private fun saved(id: String): Profile = Profile().apply {
        val raw = settings.getString(id, "").orEmpty()
        if (raw.isNotEmpty()) decodeJson(raw)
        if (name.isBlank()) name = settings.getString("profile$id", null)?.takeIf { it.isNotBlank() }
            ?: "Profile $id"
    }
    private fun labelled(id: String, p: Profile): Profile = p.apply {
        if (name.isBlank()) name = settings.getString("profile$id", null)?.takeIf { it.isNotBlank() }
            ?: "Profile $id"
    }
    private fun entries(ids: List<String>, selected: String, p: Profile,
                        previous: Pair<String, Profile>?): List<Entry> = ids.map {
        Entry(it, if (it == selected) p.name else
            previous?.takeIf { old -> old.first == it }?.second?.name ?: saved(it).name)
    }

    private fun write(selected: String, p: Profile, ids: List<String>,
                      removed: String? = null,
                      previous: Pair<String, Profile>? = null): Selection {
        labelled(selected, p)
        val entries = entries(ids, selected, p, previous)
        val editor = settings.edit()
        if (removed != null) editor.remove(removed).remove("profile$removed")
        previous?.let { editor.putString(it.first, it.second.toString()) }
        editor.putString("profile", selected).putString(selected, p.toString())
            .putString("profileValues", ids.joinToString("|", postfix = "|"))
            .putString("profileEntries", entries.joinToString("|", postfix = "|") { it.name })
        entries.forEach { editor.putString("profile${it.id}", it.name) }
        p.writeTo(editor)
        editor.apply()
        return Selection(selected, p, entries)
    }

    /** Preserve flat fields from legacy installs, even when their index is empty. */
    fun current(): Selection = write(currentId(), flat(), ids())
    fun save(p: Profile): Selection = write(currentId(), p, ids())

    fun select(id: String): Selection? {
        // Do not let a stale popup recreate an already deleted profile.
        if (id !in ids()) return null
        val old = currentId()
        if (id == old) return current()
        return write(id, saved(id), ids(), previous = old to labelled(old, flat()))
    }

    fun create(p: Profile): Selection {
        val old = currentId()
        val ids = ids()
        val maximum = ids.maxOf { it.toInt() }
        val next = if (maximum < Int.MAX_VALUE) maximum + 1 else {
            val used = ids.toHashSet()
            // A finite stored list cannot occupy every positive Int.
            generateSequence(1) { it + 1 }.first { it.toString() !in used }
        }
        return write(next.toString(), p, ids + next.toString(),
            previous = old to labelled(old, flat()))
    }

    fun deleteCurrent(): Selection {
        val removed = currentId()
        val remaining = ids().filter { it != removed }
        if (remaining.isEmpty()) {
            // Last deletion also clears credentials, routing and automation in flat fields.
            return write("1", Profile().apply { init(); name = "Profile 1" }, listOf("1"), removed)
        }
        return write(remaining.first(), saved(remaining.first()), remaining, removed)
    }
}
