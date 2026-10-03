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

        // Credential-encrypted storage (where our SharedPreferences live) is
        // not readable until the first unlock. The receiver is not
        // directBootAware, so this action is normally never delivered; the
        // guard keeps it safe if that ever changes. BOOT_COMPLETED follows
        // after unlock.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val profile = Profile().apply { getProfile(settings) }

        if (profile.autoStartOnBoot) {
            Log.i(TAG, "Start-on-boot enabled: starting proxy for profile '${profile.name}' on $action")
            // The network (Wi-Fi / hotspot gateway) usually comes up well after
            // BOOT_COMPLETED, so ask the service to wait for the proxy.
            ProxyController.startWithConsent(context, profile, waitForNetwork = true)
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
