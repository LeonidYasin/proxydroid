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
import android.net.NetworkCapabilities
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
    fun hasPhysicalNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                cm.getLinkProperties(network)?.linkAddresses?.isNotEmpty() == true
        }
    }

    data class PhysicalGateway(val network: android.net.Network, val address: String, val prefix: Int, val gateway: String) {
        val signature get() = "${network.networkHandle}/$address/$prefix/$gateway"
    }

    /** Prefer the physical default, then Wi-Fi/Ethernet underneath our VPN. */
    fun physicalGateway(context: Context): PhysicalGateway? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val networks = (listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()).distinct()
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) ||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue
            val lp = cm.getLinkProperties(network) ?: continue
            val address = lp.linkAddresses.firstOrNull { it.address is Inet4Address && !it.address.isAnyLocalAddress && !it.address.isLoopbackAddress } ?: continue
            val gateway = lp.routes.asSequence().filter { it.isDefaultRoute }
                .mapNotNull { it.gateway }.firstOrNull { it is Inet4Address && !it.isAnyLocalAddress } ?: continue
            return PhysicalGateway(network, address.address.hostAddress ?: continue, address.prefixLength, gateway.hostAddress ?: continue)
        }
        return null
    }

    fun getGatewayIp(context: Context): String? = physicalGateway(context)?.gateway

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
