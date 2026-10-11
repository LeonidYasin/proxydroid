package org.proxydroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.RouteInfo
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.*
import org.proxydroid.utils.NetworkUtils

class NetworkUtilsTest {
    private val context = mock(Context::class.java)
    private val cm = mock(ConnectivityManager::class.java)

    init {
        `when`(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(cm)
    }

    private fun network(vpn: Boolean, gateways: List<String>, transport: Int = NetworkCapabilities.TRANSPORT_WIFI): Network {
        val net = mock(Network::class.java)
        val caps = mock(NetworkCapabilities::class.java)
        `when`(caps.hasTransport(transport)).thenReturn(true)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true)
        `when`(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).thenReturn(!vpn)
        `when`(cm.getNetworkCapabilities(net)).thenReturn(caps)
        val lp = mock(LinkProperties::class.java)
        val link = mock(android.net.LinkAddress::class.java)
        `when`(link.address).thenReturn(InetAddress.getByName("192.168.1.20"))
        `when`(link.prefixLength).thenReturn(24)
        `when`(lp.linkAddresses).thenReturn(listOf(link))
        val routes = gateways.map { address ->
            mock(RouteInfo::class.java).also {
                `when`(it.isDefaultRoute).thenReturn(true)
                `when`(it.gateway).thenReturn(InetAddress.getByName(address))
            }
        }
        `when`(lp.routes).thenReturn(routes)
        `when`(cm.getLinkProperties(net)).thenReturn(lp)
        return net
    }

    @Test fun ignoresVpnAndUnspecifiedAndIpv6Gateways() {
        val vpn = network(true, listOf("10.0.0.1"))
        val physical = network(false, listOf("::", "0.0.0.0", "fe80::1", "192.168.43.1"))
        `when`(cm.activeNetwork).thenReturn(vpn)
        `when`(cm.allNetworks).thenReturn(arrayOf(vpn, physical))
        assertEquals("192.168.43.1", NetworkUtils.getGatewayIp(context))
    }

    @Test fun prefersPhysicalDefaultNetwork() {
        val ethernet = network(false, listOf("192.168.1.1"), NetworkCapabilities.TRANSPORT_ETHERNET)
        val wifi = network(false, listOf("192.168.43.1"))
        `when`(cm.activeNetwork).thenReturn(ethernet)
        `when`(cm.allNetworks).thenReturn(arrayOf(wifi, ethernet))
        assertEquals("192.168.1.1", NetworkUtils.getGatewayIp(context))
    }

    @Test fun cellularDefaultIsExcludedAndWifiGatewayRemainsUsable() {
        val cellular = network(false, listOf("10.20.0.1"), NetworkCapabilities.TRANSPORT_CELLULAR)
        val wifi = network(false, listOf("192.168.43.1"))
        `when`(cm.activeNetwork).thenReturn(cellular)
        `when`(cm.allNetworks).thenReturn(arrayOf(cellular, wifi))
        assertEquals(wifi, NetworkUtils.physicalGateway(context)?.network)
        assertEquals("192.168.43.1", NetworkUtils.getGatewayIp(context))
    }

    @Test fun gatewayWithoutLocalAddressIsNotAUsablePhysicalSnapshot() {
        val wifi = network(false, listOf("192.168.43.1"))
        `when`(cm.getLinkProperties(wifi)!!.linkAddresses).thenReturn(emptyList())
        `when`(cm.activeNetwork).thenReturn(wifi)
        `when`(cm.allNetworks).thenReturn(arrayOf(wifi))
        assertNull(NetworkUtils.physicalGateway(context))
    }

    @Test fun noUsableGatewayReturnsNull() {
        val physical = network(false, listOf("0.0.0.0"))
        `when`(cm.activeNetwork).thenReturn(physical)
        `when`(cm.allNetworks).thenReturn(arrayOf(physical))
        assertNull(NetworkUtils.getGatewayIp(context))
    }
}
