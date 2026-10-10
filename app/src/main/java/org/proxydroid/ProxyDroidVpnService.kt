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
import org.proxydroid.utils.NetworkUtils
import org.proxydroid.utils.Tun2SocksHelper
import org.proxydroid.utils.Utils

class ProxyDroidVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnJob: Job? = null


    companion object {
        private const val TAG = "ProxyDroidVpnService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "proxydroid_vpn_channel"
        private const val VPN_MTU = 1500
        private const val VPN_ADDRESS = "10.0.0.1"
        private const val VPN_ROUTE = "0.0.0.0"
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
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_STOP) {
            Log.d(TAG, "ACTION_STOP received")
            stopVpn()
            Utils.setWorking(false)
            Utils.setConnecting(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val bundle = intent.extras
        if (bundle != null) {
            host = bundle.getString("host", "")
            port = bundle.getInt("port", 0)
            val isAuth = bundle.getBoolean("isAuth", false)
            user = if (isAuth) bundle.getString("user", "") else ""
            password = if (isAuth) bundle.getString("password", "") else ""
            proxyType = bundle.getString("proxyType", "socks5")
            proxyApps = bundle.getString("proxyApps", "")
            isBypassApps = bundle.getBoolean("isBypassApps", false)

            // Auto-gateway: when set, ignore the manually configured host and
            // use the current network's default gateway as the upstream proxy
            // host instead. Useful when sharing a local proxy over a Wi-Fi
            // hotspot (e.g. proxy listening on the phone itself at the
            // hotspot gateway address like 192.168.43.1).
            if (bundle.getBoolean("useGatewayAsHost", false)) {
                val gw = NetworkUtils.getGatewayIp(this)
                if (!gw.isNullOrEmpty()) {
                    Log.i(TAG, "useGatewayAsHost: replacing host='$host' with gateway='$gw'")
                    host = gw
                } else if (host.isBlank()) {
                    // No gateway (typical on cellular) and no manual host:
                    // there is nothing to connect to. Fail fast with a clear
                    // message instead of starting a tunnel to an empty host.
                    val msg = "Auto-gateway is enabled, but this network has no " +
                        "gateway (cellular?). Set the proxy host manually or " +
                        "connect to Wi-Fi."
                    Log.e(TAG, msg)
                    Utils.setLastError(msg)
                    Utils.setConnecting(false)
                    Utils.setWorking(false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                } else {
                    Log.w(TAG, "useGatewayAsHost: gateway unavailable, keeping host='$host'")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        vpnJob?.cancel()
        vpnJob = serviceScope.launch { startVpn() }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
        Utils.setWorking(false)
        Utils.setConnecting(false)
        serviceScope.cancel()
        Log.d(TAG, "VPN Service destroyed")
    }

    override fun onRevoke() {
        super.onRevoke()
        stopVpn()
        stopSelf()
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ProxyDroid VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ProxyDroid VPN service notification"
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
            .setContentText(getString(R.string.vpn_running))
            .setSmallIcon(R.drawable.ic_stat_proxydroid)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startVpn() {
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
            val apps = proxyApps.split("|").filter { it.isNotBlank() && it != packageName }.distinct()
            if (proxyApps.isNotBlank() && !isBypassApps) {
                var added = 0
                for (app in apps) {
                    try {
                        builder.addAllowedApplication(app)
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
                        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                            Log.w(TAG, "Bypass app is no longer installed: $app", e)
                        }
                    }
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                Utils.setConnecting(false)
                Utils.setWorking(false)
                stopSelf()
                return
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
                return
            }

            Utils.setLastError(null)
            Utils.setConnecting(false)
            Utils.setWorking(true)
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

    private fun stopVpn() {
        try {
            tun2SocksHelper?.stop()
        } catch (t: Throwable) {
            Log.e(TAG, "tun2socks stop threw", t)
        }
        tun2SocksHelper = null

        try {
            vpnInterface?.close()
        } catch (t: Throwable) {
            Log.e(TAG, "vpn interface close threw", t)
        }
        vpnInterface = null
    }
}
