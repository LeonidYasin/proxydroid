/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proxydroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import android.preference.PreferenceManager
import org.proxydroid.utils.BootStatus
import org.proxydroid.utils.ProxyEndpoint
import org.proxydroid.utils.NetworkUtils
import org.proxydroid.utils.Tun2SocksHelper
import org.proxydroid.utils.Utils

class ProxyDroidVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnJob: Job? = null
    private val lifecycleLock = Any()
    private val generation = java.util.concurrent.atomic.AtomicLong(0)


    companion object {
        private const val TAG = "ProxyDroidVpnService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "proxydroid_vpn_channel"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_ROUTE = "0.0.0.0"
        const val EXTRA_WAIT_NETWORK = "wait_for_network"
        const val ACTION_AUTOSTART = "org.proxydroid.action.AUTOSTART"
        const val EXTRA_STOP_ACK = "stop_ack"
        const val ACTION_STOP = "org.proxydroid.action.STOP_VPN"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2SocksHelper: Tun2SocksHelper? = null
    private var host: String = ""
    private var port: Int = 0
    private var user: String = ""
    private var password: String = ""
    private var proxyType: String = "socks5"
    private var proxyApps: String = ""
    private var isBypassApps: Boolean = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "VPN Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Utils.recordVpnEvent("Stop requested")
            generation.incrementAndGet()
            val old = vpnJob
            old?.cancel()
            @Suppress("DEPRECATION")
            val acknowledgement = intent.getParcelableExtra<android.os.ResultReceiver>(EXTRA_STOP_ACK)
            serviceScope.launch {
                old?.join()
                val stopped = stopVpn()
                Utils.recordVpnEvent(if (stopped) "Stop complete: native tasks and TUN released" else "Stop incomplete; reconnect blocked")
                Utils.setWorking(false); Utils.setConnecting(false)
                if (stopped) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // Keep an acknowledged service idle for an immediate reconnect.
                    // A late onDestroy from stopSelf must not tear down the new session.
                    if (acknowledgement == null) stopSelf(startId)
                }
                acknowledgement?.send(if (stopped) 0 else 1, null)
            }
            return START_NOT_STICKY
        }
        // Duplicate boot/update events must not restart a live or waiting VPN.
        if (Utils.isWorking() || vpnJob?.isActive == true) return START_STICKY
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val saved = Profile().apply { getProfile(settings) }
        val profileId = settings.getString("profile", "1") ?: "1"
        val bundle = intent?.extras ?: org.proxydroid.utils.ProxyController.buildExtras(saved)
        host = bundle.getString("host", "")
        port = bundle.getInt("port", Profile.DEFAULT_PORT)
        val auth = bundle.getBoolean("isAuth", false)
        user = if (auth) bundle.getString("user", "") else ""
        password = if (auth) bundle.getString("password", "") else ""
        proxyType = bundle.getString("proxyType", "socks5")
        proxyApps = bundle.getString("proxyApps", "")
        isBypassApps = bundle.getBoolean("isBypassApps", false)
        val autoGateway = bundle.getBoolean("useGatewayAsHost", false)
        val waitNetwork = intent?.getBooleanExtra(EXTRA_WAIT_NETWORK, false) == true || intent == null
        // Promote immediately; network/gateway/proxy waiting happens ONLY afterwards.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createNotification("Connecting / waiting for network"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else startForeground(NOTIFICATION_ID, createNotification("Connecting / waiting for network"))
        } catch (failure: Exception) {
            Utils.setLastError("Android refused foreground VPN startup: ${failure.javaClass.simpleName}")
            Utils.setWorking(false); Utils.setConnecting(false); stopSelf()
            return START_NOT_STICKY
        }
        vpnJob?.cancel()
        val session = generation.incrementAndGet()
        Utils.recordVpnEvent("Start requested; generation=$session")
        Utils.setConnecting(true)
        vpnJob = serviceScope.launch {
            try {
                if (waitNetwork) BootStatus.record(this@ProxyDroidVpnService, "Waiting for network/proxy (up to 120 seconds)")
                val ready = withTimeoutOrNull(if (waitNetwork) 120_000L else 5000L) {
                    while (true) {
                        val gateway = if (autoGateway) NetworkUtils.getGatewayIp(this@ProxyDroidVpnService) else null
                        if (gateway != null) host = gateway
                        val networkReady = !waitNetwork || NetworkUtils.hasPhysicalNetwork(this@ProxyDroidVpnService)
                        val gatewayReady = !autoGateway || gateway != null
                        val proxyReady = !waitNetwork || (host.isNotBlank() && port in 1..65535 && NetworkUtils.isProxyAlive(host, port))
                        if (networkReady && gatewayReady && proxyReady) break
                        if (!waitNetwork) error("No usable IPv4 default gateway; Host was not changed")
                        delay(2000)
                    }
                    true
                } == true
                check(ready) { "Boot start timed out waiting for network/proxy. Open the app and reconnect." }
                if (autoGateway && settings.getString("profile", "1") == profileId && settings.getBoolean("useGatewayAsHost", false)) {
                    val current = Profile().apply { getProfile(settings); this.host = this@ProxyDroidVpnService.host }
                    current.setProfile(settings); settings.edit().putString(profileId, current.toString()).apply()
                }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                startVpn(session)
                if (waitNetwork) BootStatus.record(this@ProxyDroidVpnService,
                    if (Utils.isWorking()) "Boot VPN started successfully" else "Boot VPN failed; see connection error")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                Utils.setLastError(failure.message ?: "VPN start failed")
                if (waitNetwork) BootStatus.record(this@ProxyDroidVpnService, "Boot VPN failed: ${failure.message ?: "unknown error"}")
                Utils.setWorking(false); Utils.setConnecting(false)
                stopVpn(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        generation.incrementAndGet(); vpnJob?.cancel()
        stopVpn()
        super.onDestroy()
        Utils.setWorking(false)
        Utils.setConnecting(false)
        serviceScope.cancel()
        Log.d(TAG, "VPN Service destroyed")
    }

    override fun onRevoke() {
        Utils.recordVpnEvent("Android revoked VPN ownership")
        generation.incrementAndGet(); vpnJob?.cancel()
        stopVpn()
        Utils.setWorking(false); Utils.setConnecting(false)
        Utils.setLastError("VPN access revoked by Android (another VPN, system settings or always-on policy). Reconnect and confirm Android permission.")
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        super.onRevoke()
    }

    private fun createNotification(status: String = getString(R.string.vpn_running)): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "${getString(R.string.app_name)} VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "${getString(R.string.app_name)} VPN service notification"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ProxyDroid::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_stat_proxydroid)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startVpn(session: Long) = synchronized(lifecycleLock) {
        if (generation.get() != session) return@synchronized
        check(tun2SocksHelper == null && vpnInterface == null) { "Previous VPN cleanup is incomplete. Stop VPN before reconnecting." }
        check(VpnService.prepare(this) == null) { "Android VPN consent is missing/revoked; another always-on VPN or lockdown may block connection. Open Android VPN settings." }
        Log.d(TAG, "Starting VPN with proxy: $host:$port")
        Utils.setConnecting(true)

        try {
            require(host.isNotBlank()) { "Proxy host must not be blank" }
            require(port in 1..65535) { "Proxy port must be from 1 to 65535" }
            // Configure VPN. DNS server is in-tunnel; the Rust tun2socks
            // intercepts UDP/53 and forwards via DoH through the upstream SOCKS5.
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 24)
                .addRoute(VPN_ROUTE, 0)
                .addDnsServer("10.0.0.2")

            // Android forbids mixing allowed and disallowed application lists.
            val appliedAllowed = linkedSetOf<String>()
            val appliedExcluded = linkedSetOf(packageName)
            val apps = proxyApps.split("|").filter { it.isNotBlank() && it != packageName }.distinct()
            if (proxyApps.isNotBlank() && !isBypassApps) {
                var added = 0
                for (app in apps) {
                    try {
                        builder.addAllowedApplication(app)
                        appliedAllowed.add(app)
                        added++
                    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Selected app is no longer installed: $app", e)
                    }
                }
                // An empty allow-list means ALL apps. Never silently broaden it.
                check(added > 0) { "No selected proxy applications are installed" }
                // Our UID is not in the allow-list, so the upstream socket bypasses VPN.
            } else {
                builder.addDisallowedApplication(packageName)
                if (isBypassApps) {
                    for (app in apps) {
                        try {
                            builder.addDisallowedApplication(app)
                            appliedExcluded.add(app)
                        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                            Log.w(TAG, "Bypass app is no longer installed: $app", e)
                        }
                    }
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Utils.setLastError("Android returned no VPN interface. Check VPN consent, another always-on VPN and Block connections without VPN in system settings.")
                Log.e(TAG, "Failed to establish VPN interface")
                Utils.setConnecting(false)
                Utils.setWorking(false)
                stopSelf()
                return@synchronized
            }

            val fd = vpnInterface!!.fd
            val helper = Tun2SocksHelper()
            tun2SocksHelper = helper

            val started = helper.start(
                vpnService = this,
                tunFd = fd,
                mtu = VPN_MTU,
                proxyType = proxyType,
                socksHost = host,
                socksPort = port,
                socksUser = user.takeIf { it.isNotEmpty() },
                socksPassword = password.takeIf { it.isNotEmpty() },
            )

            if (!started) {
                val msg = "Failed to start tun2socks (proxy $proxyType://$host:$port). " +
                    "Check that the proxy is reachable from this network."
                Log.e(TAG, msg)
                Utils.setLastError(msg)
                Utils.setConnecting(false)
                Utils.setWorking(false)
                stopVpn()
                stopSelf()
                return@synchronized
            }

            Utils.setLastError(null)
            Utils.setConnecting(false)
            Utils.setActiveRouting(org.proxydroid.utils.ActiveRouting(if (proxyApps.isNotBlank() && !isBypassApps) appliedAllowed.toSet() else null, appliedExcluded.toSet()))
            Utils.setActiveUpstream(ProxyEndpoint(host, port, proxyType, user.takeIf { it.isNotEmpty() }, password.takeIf { it.isNotEmpty() }))
            Utils.setWorking(true)
            Utils.recordVpnEvent("VPN established; generation=$session" + if (Build.VERSION.SDK_INT >= 29) "; always-on=$isAlwaysOn; lockdown=$isLockdownEnabled" else "; always-on policy unavailable on this API")
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, createNotification())
            Log.i(TAG, "VPN established and tun2socks running")
        } catch (t: Throwable) {
            val msg = "Failed to establish VPN: ${t.javaClass.simpleName}: ${t.message ?: "no details"}"
            Log.e(TAG, msg, t)
            Utils.setLastError(msg)
            Utils.setConnecting(false)
            Utils.setWorking(false)
            stopVpn()
            stopSelf()
        }
    }

    private fun stopVpn(): Boolean = synchronized(lifecycleLock) {
        var stopped = true
        try { tun2SocksHelper?.stop(); tun2SocksHelper = null }
        catch (t: Throwable) {
            stopped = false
            Utils.setLastError("VPN cleanup incomplete: ${t.javaClass.simpleName}. Wait and retry Stop; do not reconnect yet.")
            Log.e(TAG, "tun2socks stop threw", t)
        }
        try { vpnInterface?.close(); vpnInterface = null }
        catch (t: Throwable) { stopped = false; Log.e(TAG, "VPN interface close threw", t) }
        stopped
    }
}
