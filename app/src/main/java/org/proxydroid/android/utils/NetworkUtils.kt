package org.proxydroid.android.utils

import android.content.Context
import android.net.ConnectivityManager

/**
 * Returns the IP address of the current network's default gateway.
 *
 * When the device is acting as a Wi-Fi hotspot (tethering), this is the
 * hotspot gateway address (e.g. 192.168.43.1) - i.e. the phone itself.
 * When the device is a Wi-Fi client, this is the router address.
 *
 * Works on Android 7+ including API 29+ where WifiManager.dhcpInfo
 * no longer exposes the gateway to non-system apps.
 */
fun getGatewayIp(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        as? ConnectivityManager ?: return null
    val network = cm.activeNetwork ?: return null
    val lp = cm.getLinkProperties(network) ?: return null
    return lp.routes
        .firstOrNull { it.destination?.prefixLength == 0 }
        ?.gateway
        ?.hostAddress
}
