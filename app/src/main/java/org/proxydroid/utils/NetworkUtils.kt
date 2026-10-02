/* proxydroid - Global / Individual Proxy App for Android
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.proxydroid.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Returns the IP address of the current network's default gateway.
 *
 * When the device acts as a Wi-Fi hotspot (tethering), this is the hotspot
 * gateway address (e.g. 192.168.43.1) - i.e. the phone itself, which is
 * where a local proxy such as the one used by ProxyDroid listens. When the
 * device is a Wi-Fi client, this is the router address.
 *
 * Works on Android 7+ including API 29+ where WifiManager.dhcpInfo no longer
 * exposes the gateway to non-system apps.
 */
object NetworkUtils {
    @Suppress("DEPRECATION") // allNetworks is deprecated on API 31+ but still works
    fun getGatewayIp(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null

        // The *active* network turns into the VPN itself as soon as our own
        // tunnel is up (and it has no gateway), so look at every non-VPN
        // network, active one first. Cellular is only accepted when it is the
        // active network; Wi-Fi / Ethernet are always candidates.
        val active = cm.activeNetwork
        val candidates = LinkedHashSet<Network>()
        if (active != null) candidates.add(active)
        candidates.addAll(cm.allNetworks)

        for (network in candidates) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val local = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!local && network != active) continue

            val lp = cm.getLinkProperties(network) ?: continue
            // IPv4 only: an IPv6 default gateway is a link-local fe80:: address
            // with a scope id and is not usable as a proxy host.
            val gw = lp.routes
                .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
                ?.gateway
                ?.hostAddress
            if (!gw.isNullOrEmpty()) return gw
        }
        return null
    }

    /**
     * Waits until the upstream proxy is reachable. Meant for boot / Always-on
     * VPN starts, where the Wi-Fi link (and therefore the gateway) comes up
     * tens of seconds after BOOT_COMPLETED.
     *
     * Each round resolves the host (gateway when [useGateway] is set, falling
     * back to [manualHost]) and probes it with a TCP connect. Returns the
     * reachable host, or null when [timeoutMs] elapsed.
     *
     * Must run on a background dispatcher (it blocks in socket connect).
     */
    suspend fun awaitProxyReady(
        context: Context,
        manualHost: String,
        useGateway: Boolean,
        port: Int,
        timeoutMs: Long = 180_000L,
        intervalMs: Long = 2_000L,
    ): String? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val host = if (useGateway) {
                getGatewayIp(context)?.takeIf { it.isNotEmpty() } ?: manualHost
            } else {
                manualHost
            }
            if (host.isNotBlank() && probeProxy(host, port, 2000) == ProbeResult.ALIVE) {
                return host
            }
            if (SystemClock.elapsedRealtime() >= deadline) return null
            delay(intervalMs)
        }
    }

    /**
     * Quick liveness probe for an upstream proxy.
     *
     * Opens a TCP connection to [host]:[port] with the given timeout and
     * immediately closes it. Returns true if the connection was established
     * within the timeout, false otherwise (connection refused, timeout,
     * unknown host, no route).
     *
     * This is a lightweight check: it only verifies that *something* listens
     * on the proxy port. It does NOT perform a full proxy handshake (that
     * would require knowing the proxy type: socks5 / http / etc.), so a
     * positive result means "port is reachable", not necessarily "proxy
     * correctly forwards traffic".
     *
     * Must be called from a background thread / coroutine on Dispatchers.IO.
     */
    /**
     * Outcome of a liveness probe. Distinguishes failure modes so callers
     * can show a meaningful message instead of a bare false.
     */
    enum class ProbeResult { ALIVE, REFUSED, TIMEOUT, UNKNOWN_HOST, NO_ROUTE, BAD_INPUT }

    /**
     * Quick liveness probe for an upstream proxy. Opens a TCP connection to
     * [host]:[port] with the given timeout and immediately closes it.
     *
     * Returns [ProbeResult.ALIVE] on success, otherwise a reason-specific
     * failure value:
     *   - REFUSED      : nothing listening on the port
     *   - TIMEOUT      : no response within [timeoutMs]
     *   - UNKNOWN_HOST : DNS resolution failed
     *   - NO_ROUTE     : host/network unreachable
     *   - BAD_INPUT    : blank host or port out of range
     *
     * Must be called from a background thread / coroutine on Dispatchers.IO.
     */
    fun probeProxy(host: String, port: Int, timeoutMs: Int = 2000): ProbeResult {
        if (host.isBlank() || port !in 1..65535) return ProbeResult.BAD_INPUT
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                ProbeResult.ALIVE
            }
        } catch (_: java.net.ConnectException) {
            ProbeResult.REFUSED
        } catch (_: java.net.SocketTimeoutException) {
            ProbeResult.TIMEOUT
        } catch (_: java.net.UnknownHostException) {
            ProbeResult.UNKNOWN_HOST
        } catch (_: java.net.NoRouteToHostException) {
            ProbeResult.NO_ROUTE
        } catch (_: Exception) {
            ProbeResult.BAD_INPUT
        }
    }

    /** Convenience wrapper for callers that only need a boolean. */
    fun isProxyAlive(host: String, port: Int, timeoutMs: Int = 2000): Boolean =
        probeProxy(host, port, timeoutMs) == ProbeResult.ALIVE
}
