package org.proxydroid.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import org.proxydroid.Profile
import org.proxydroid.ProxyDroid
import org.proxydroid.ProxyDroidVpnService

object ProxyController {

    const val EXTRA_AUTO_START = "auto_start"

    fun buildExtras(profile: Profile): Bundle = Bundle().apply {
        putString("host", profile.host)
        putString("user", profile.user)
        putString("bypassAddrs", profile.bypassAddrs)
        putString("password", profile.password)
        putString("domain", profile.domain)
        putString("proxyType", profile.proxyType)
        putString("proxyApps", profile.proxyApps)
        putBoolean("isAutoSetProxy", profile.isAutoSetProxy)
        putBoolean("isBypassApps", profile.isBypassApps)
        putBoolean("isAuth", profile.isAuth)
        putBoolean("isNTLM", profile.isNTLM)
        putBoolean("isDNSProxy", profile.isDNSProxy)
        putBoolean("isPAC", profile.isPAC)
        putBoolean("useGatewayAsHost", profile.useGatewayAsHost)
        putInt("port", profile.port)
    }

    /** Background starts never open consent UI; foreground starts may request it. */
    fun startWithConsent(context: Context, profile: Profile,
                         allowConsentUi: Boolean = true, waitForNetwork: Boolean = false): Boolean {
        try {
            if (VpnService.prepare(context) != null) {
                if (!allowConsentUi) {
                    BootStatus.record(context, "VPN consent required. Open LocProxyTV and connect once before enabling Start on boot.")
                    return false
                }
                context.startActivity(Intent(context, ProxyDroid::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); putExtra(EXTRA_AUTO_START, true)
                })
            } else {
                val intent = Intent(context, ProxyDroidVpnService::class.java)
                    .putExtras(buildExtras(profile)).putExtra(ProxyDroidVpnService.EXTRA_WAIT_NETWORK, waitForNetwork)
                if (waitForNetwork) intent.action = ProxyDroidVpnService.ACTION_AUTOSTART
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
            return true
        } catch (failure: Exception) {
            Utils.setLastError("Android could not start VPN: ${failure.javaClass.simpleName}")
            if (waitForNetwork) BootStatus.record(context, "Android/OEM refused foreground VPN startup; check VPN consent and autostart restrictions.")
            return false
        }
    }

    fun stop(context: Context) {
        try {
            // Send an explicit STOP action to the service so it can close its tun
            // interface and call stopSelf. Calling context.stopService() alone is
            // not enough — the system holds VpnService alive while the tun is open.
            val intent = Intent(context, ProxyDroidVpnService::class.java).apply {
                action = ProxyDroidVpnService.ACTION_STOP
            }
            context.startService(intent)
        } catch (_: Exception) {
        }
    }
}
