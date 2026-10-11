package org.proxydroid.utils

/** Actual service upstream snapshot. Credentials never appear in toString/logs. */
class ProxyEndpoint(val host: String, val port: Int, val type: String,
                    val user: String? = null, val password: String? = null) {
    override fun toString() = "$type://$host:$port"
}
