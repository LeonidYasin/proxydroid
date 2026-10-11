package org.proxydroid.utils

import org.junit.Assert.*
import org.junit.Test

class GatewayRecoveryPolicyTest {
    @Test fun duplicateCallbacksDoNotReconnect() {
        val p = GatewayRecoveryPolicy("net1/192.168.1.5/24/192.168.1.1")
        repeat(10) { assertEquals(GatewayRecoveryPolicy.Action.KEEP, p.next("net1/192.168.1.5/24/192.168.1.1")) }
    }
    @Test fun lossThenSameGatewayRequiresFreshTunnel() {
        val p = GatewayRecoveryPolicy("same")
        assertEquals(GatewayRecoveryPolicy.Action.WAIT, p.next(null))
        assertEquals(GatewayRecoveryPolicy.Action.KEEP, p.next(null))
        assertEquals(GatewayRecoveryPolicy.Action.RECONNECT, p.next("same"))
        assertEquals(GatewayRecoveryPolicy.Action.KEEP, p.next("same"))
    }
    @Test fun dhcpOrNetworkOrGatewayChangeReconnectsOnce() {
        val p = GatewayRecoveryPolicy("net1/a/24/g")
        for (signature in listOf("net1/b/24/g", "net1/b/24/h", "net2/b/24/h")) {
            assertEquals(GatewayRecoveryPolicy.Action.RECONNECT, p.next(signature))
            assertEquals(GatewayRecoveryPolicy.Action.KEEP, p.next(signature))
        }
    }
}
