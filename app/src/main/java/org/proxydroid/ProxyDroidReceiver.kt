package org.proxydroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.preference.PreferenceManager
import android.util.Log
import org.proxydroid.utils.BootPolicy
import org.proxydroid.utils.BootStatus
import org.proxydroid.utils.ProxyController

/** BOOT_COMPLETED after unlock; never tries to launch an Activity in background. */
class ProxyDroidReceiver : BroadcastReceiver() {
    internal var starter: (Context, Profile) -> Unit = { context, profile ->
        ProxyController.startWithConsent(context, profile, allowConsentUi = false, waitForNetwork = true)
        Unit
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BootPolicy.actions) return
        if ((context.getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked == false) return
        val profile = Profile().apply { getProfile(PreferenceManager.getDefaultSharedPreferences(context)) }
        if (!BootPolicy.shouldStart(intent.action, profile.autoStartOnBoot, true)) return
        try {
            BootStatus.record(context, "Boot event received; starting foreground VPN and waiting for network")
            starter(context, profile)
        } catch (failure: Exception) {
            Log.e("ProxyDroidReceiver", "Boot start failed", failure)
            BootStatus.record(context, "Android/OEM refused boot start. Open ProxyDroid, grant VPN consent and check background/autostart settings.")
        }
    }
}
