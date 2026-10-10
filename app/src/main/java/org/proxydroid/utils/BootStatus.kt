package org.proxydroid.utils

import android.content.Context
import android.preference.PreferenceManager

object BootStatus {
    const val KEY = "boot_start_status"
    fun record(context: Context, message: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(KEY, message).putLong("boot_start_time", System.currentTimeMillis()).apply()
    }
}
