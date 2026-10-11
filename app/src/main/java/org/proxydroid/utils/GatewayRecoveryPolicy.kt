package org.proxydroid.utils

/** A network loss requires a fresh tunnel even if DHCP returns the same gateway/IP. */
class GatewayRecoveryPolicy(initialSignature: String) {
    enum class Action { KEEP, WAIT, RECONNECT }
    private var signature = initialSignature
    private var waiting = false
    fun next(currentSignature: String?): Action {
        if (currentSignature == null) {
            if (waiting) return Action.KEEP
            waiting = true
            return Action.WAIT
        }
        if (!waiting && signature == currentSignature) return Action.KEEP
        waiting = false; signature = currentSignature
        return Action.RECONNECT
    }
}
