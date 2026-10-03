package org.proxydroid.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.proxydroid.Profile
import org.proxydroid.ProxyDroid
import org.proxydroid.ProxyDroidVpnService
import org.proxydroid.R

object ProxyController {

    private const val TAG = "ProxyController"

    const val EXTRA_AUTO_START = "auto_start"

    /** Set by background starters (boot, package update): the network may not
     *  be up yet, so the service must wait for the proxy to become reachable
     *  instead of failing on the first attempt. */
    const val EXTRA_WAIT_FOR_NETWORK = "wait_for_network"

    private const val CONSENT_CHANNEL_ID = "proxydroid_consent_channel"
    private const val CONSENT_NOTIFICATION_ID = 3

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

    /**
     * Start the VPN service if consent is already granted; otherwise ask the
     * user to grant it.
     *
     * Called from a BroadcastReceiver (no UI), so:
     *  - the service is started with startForegroundService(): a plain
     *    startService() from the background is rejected on Android 8+ once the
     *    short boot allow-list expires;
     *  - the consent dialog cannot be opened with startActivity() on Android
     *    10+ (background activity launches are blocked silently), so a
     *    notification with a tap-to-continue action is posted instead.
     */
    fun startWithConsent(context: Context, profile: Profile, waitForNetwork: Boolean = false) {
        val consent = VpnService.prepare(context)
        if (consent == null) {
            val intent = Intent(context, ProxyDroidVpnService::class.java)
                .putExtras(buildExtras(profile))
                .putExtra(EXTRA_WAIT_FOR_NETWORK, waitForNetwork)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.e(TAG, "Could not start VPN service from background", t)
            }
        } else {
            requestConsent(context)
        }
    }

    private fun requestConsent(context: Context) {
        val launch = Intent(context, ProxyDroid::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_AUTO_START, true)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                context.startActivity(launch)
                return
            } catch (t: Throwable) {
                Log.w(TAG, "startActivity from background failed, falling back to notification", t)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CONSENT_CHANNEL_ID,
                    "ProxyDroid start request",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CONSENT_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Tap to allow the VPN and start the proxy")
            .setSmallIcon(R.drawable.ic_stat_proxydroid)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(CONSENT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+).
            Log.w(TAG, "Cannot post consent notification: permission denied", e)
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
