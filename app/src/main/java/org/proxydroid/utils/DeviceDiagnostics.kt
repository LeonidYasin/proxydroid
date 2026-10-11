package org.proxydroid.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import org.proxydroid.BuildConfig
import org.proxydroid.Profile
import java.net.Inet4Address
import java.net.NoRouteToHostException

/** Actual applied routing, not the currently edited profile. No credentials or mutable Profile references. */
data class ActiveRouting(val allowed: Set<String>?, val excluded: Set<String>, val startedAt: Long = System.currentTimeMillis()) {
    fun routed(packageName: String): Boolean = allowed?.contains(packageName) ?: !excluded.contains(packageName)
}
data class DiagnosticDevice(val lines: List<String>, val route: ProbeRoute, val network: Network?)
object DeviceDiagnostics {
    fun physicalNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return (listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()).distinct().firstOrNull {
            val caps = cm.getNetworkCapabilities(it)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }
    fun capture(context: Context, profile: Profile, endpoint: ProxyEndpoint, active: Boolean): DiagnosticDevice {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = physicalNetwork(context)
        val caps = network?.let { cm?.getNetworkCapabilities(it) }
        val lp = network?.let { cm?.getLinkProperties(it) }
        val routing = if (active) Utils.activeRouting.value else null
        val lines = mutableListOf("App: v${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_RUN_ID} · ${BuildConfig.BUILD_SHA.take(12)} · ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}",
            "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}; model ${Build.MANUFACTURER} ${Build.MODEL}",
            "Scope: ${if (active) "ACTUAL active service upstream" else "CONFIGURED profile; VPN is not running — no TUN test"}",
            "Physical network: ${if (network == null) "none found" else network.toString()}; interface ${lp?.interfaceName ?: "unknown"}; MTU ${lp?.mtu ?: 0}")
        lines += LauncherIdentity.lines(context)
        val transports = listOf(NetworkCapabilities.TRANSPORT_WIFI to "Wi-Fi", NetworkCapabilities.TRANSPORT_ETHERNET to "Ethernet", NetworkCapabilities.TRANSPORT_CELLULAR to "cellular").filter { caps?.hasTransport(it.first) == true }.joinToString { it.second }
        lines += "Transport: ${transports.ifEmpty { "unknown" }}; Android VALIDATED=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false}; CAPTIVE_PORTAL=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ?: false} (platform signals, not a definitive proxy test)"
        lines += "Addresses: ${lp?.linkAddresses?.joinToString { it.toString() } ?: "unknown"}; gateway ${NetworkUtils.getGatewayIp(context) ?: "unknown"}"
        lines += "Physical DNS: ${lp?.dnsServers?.joinToString { it.hostAddress ?: "unknown" } ?: "unknown"}"
        if (Build.VERSION.SDK_INT >= 28) lines += "Android Private DNS: active=${lp?.isPrivateDnsActive ?: false}; strict hostname configured=${!lp?.privateDnsServerName.isNullOrBlank()} (in-tunnel DNS uses separate native DoH)"
        val systemVpn = cm?.allNetworks?.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true } == true
        lines += "Android VPN network present=$systemVpn; LocProxyTV working=${Utils.isWorking()}. Another VPN identity/always-on policy is not generally readable by this app; inspect Android VPN settings. Switching uses prepare/establish, never force-stop of other apps."
        lines += "VPN consent granted=${VpnService.prepare(context) == null}; service flag working=${Utils.isWorking()}; native launch snapshot ${if (routing == null) "not available" else "recorded; not a runtime health probe"}"
        if (routing != null) {
            lines += "ACTUAL routing: ${if (routing.allowed == null) "all apps except exclusions (${routing.excluded.size})" else "allow-list (${routing.allowed.size} installed apps)"}; session age ${((System.currentTimeMillis() - routing.startedAt) / 1000).coerceAtLeast(0)} s"
            val appliedPackages = (routing.allowed ?: routing.excluded).sorted()
            lines += "ACTUAL ${if (routing.allowed == null) "excluded" else "allowed"} packages: ${appliedPackages.take(64).joinToString()}${if (appliedPackages.size > 64) " … (first 64 shown)" else ""}"
            lines += "App routing is a SESSION snapshot; edits to selected apps require reconnect, just like endpoint/auth edits."
            for (pkg in listOf("com.google.android.youtube.tv", "com.google.android.youtube")) {
                val installed = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
                lines += "${if (pkg.endsWith(".tv")) "YouTube TV" else "YouTube phone"}: ${if (!installed) "not found / not visible" else if (routing.routed(pkg)) "included in ACTUAL VPN routing" else "BYPASSED / not in active allow-list"}"
            }
        } else lines += "Actual app routing unavailable: configured mode ${if (profile.isBypassApps) "bypass list" else if (profile.proxyApps.isNotBlank()) "allow-list" else "all apps"}. Connect VPN to inspect applied YouTube routing."
        if (active && (profile.host != endpoint.host || profile.port != endpoint.port || profile.proxyType != endpoint.type || profile.user.takeIf { it.isNotEmpty() } != endpoint.user || profile.password.takeIf { it.isNotEmpty() } != endpoint.password))
            lines += "WARNING: edited profile differs from running upstream (including possible auth changes). Results use ACTUAL active upstream; reconnect VPN to apply edits."
        lines += "Limits: native IPv4 TCP + intercepted DNS; generic UDP/QUIC and complete IPv6 tunneling are not implemented. Runtime aggregate counters below do not prove end-to-end VPN or video health."
        lines += Tun2SocksHelper.readRuntimeHealth()?.lines() ?: listOf("Native runtime observation unavailable; do not infer stopped/healthy from missing JNI status")
        lines += "Recent VPN lifecycle (timestamps in epoch ms; no credentials):"
        lines += Utils.vpnHistory()
        val route = ProbeRoute(resolve = { host ->
            val physical = network ?: throw NoRouteToHostException("No physical network")
            val addresses = physical.getAllByName(host)
            addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: throw java.net.UnknownHostException()
        }, bind = { socket -> (network ?: throw NoRouteToHostException("No physical network")).bindSocket(socket) })
        return DiagnosticDevice(lines, route, network)
    }
}
