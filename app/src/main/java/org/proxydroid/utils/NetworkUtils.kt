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
}
