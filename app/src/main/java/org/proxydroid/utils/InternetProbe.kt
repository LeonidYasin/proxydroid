package org.proxydroid.utils

/** Our UID bypasses VPN to avoid loops, so check HTTPS explicitly THROUGH upstream. */
object InternetProbe {
    private val hosts = listOf("www.google.com", "connectivitycheck.gstatic.com")
    fun check(endpoint: ProxyEndpoint): Boolean {
        for (host in hosts) {
            try {
                ProxyTransport.connect(endpoint, host, 443).use { tunnel ->
                    ProxyTransport.tls(tunnel, host, 443).use { tls ->
                        tls.getOutputStream().apply {
                            write("GET /generate_204 HTTP/1.1\r\nHost: $host\r\nConnection: close\r\nUser-Agent: ProxyDroid-connectivity-check\r\n\r\n".toByteArray(Charsets.US_ASCII)); flush()
                        }
                        val status = ProxyTransport.readLine(ProxyTransport.input(tls))
                        if (status.matches(Regex("HTTP/1\\.[01] 204(?: .*|$)"))) return true
                    }
                }
            } catch (_: Exception) { /* Try independent fallback; never expose credentials. */ }
        }
        return false
    }
}
