/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.proxydroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.util.Log
import org.proxydroid.utils.ProxyController

/**
 * Re-arms the proxy after the device boots (or after an app update), but only
 * when the current profile has the user-visible "Start on boot" option
 * ([Profile.autoStartOnBoot]) enabled.
 *
 * This is deliberately independent from [Profile.isAutoConnect]:
 *   - isAutoConnect  -> re-connect when a matching Wi-Fi network appears
 *   - autoStartOnBoot -> re-arm after BOOT_COMPLETED / MY_PACKAGE_REPLACED
 *
 * Registered in AndroidManifest.xml for BOOT_COMPLETED,
 * LOCKED_BOOT_COMPLETED, QUICKBOOT_POWERON and MY_PACKAGE_REPLACED.
 */
class ProxyDroidReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = Profile().apply { getProfile(settings) }

        if (profile.autoStartOnBoot) {
            Log.i(TAG, "Start-on-boot enabled: starting proxy for profile '${profile.name}' on $action")
            ProxyController.startWithConsent(context, profile)
        } else {
            Log.d(TAG, "Start-on-boot disabled for current profile; nothing to do on $action")
        }
    }

    companion object {
        private const val TAG = "ProxyDroidReceiver"

        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
