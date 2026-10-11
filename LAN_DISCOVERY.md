# LAN proxy discovery (3.9.0 candidate)

The new card is below the main proxy/profile/auth/boot settings and above Advanced.

- One-off **Find proxies**, plus an explicit opt-in **Auto-search on opening/network change**. Automatic discovery is disabled until enabled by the user; the choice is saved separately from proxy profiles.
- Only the attached physical Wi-Fi/Ethernet IPv4 RFC1918 subnet (10/8, 172.16/12, 192.168/16), not VPN, mobile data, public IPv4, multicast or loopback. Every socket binds to the captured physical Network. Network changes invalidate results; backgrounding/recovery/Cancel closes all discovery sockets.
- Up to 256 neighbours, excluding self/network/broadcast. For prefixes larger than /24, only the current /24 plus the actual subnet's gateway/current numeric Host are checked; UI states that this is partial. No complete-network-map claim.
- Ports: current configured Port plus 10808, 1080, 7890, 8080, 3128, 8888, 8118. At most 8 ports, concurrency 16, connect 350ms/read 700ms, overall 90s. Fast responses are expected on a local network; congested/sleeping services may be missed.
- SOCKS5 method negotiation (no-auth or password-required), then HTTP CONNECT example.com:443/407 Proxy-Authenticate. Saved credentials are NEVER sent during scanning; no password attempts or bypass. CONNECT may ask the found proxy to contact example.com. A recognised handshake does NOT prove working internet, TLS, DNS, TUN or video.
- Open TCP ports without a recognised protocol are counted but not offered as proxies. SOCKS4, HTTPS-proxy, unsupported auth methods and non-listed ports remain manual; HTTP 407 may use auth schemes the app cannot support.
- Selecting a result requires confirmation. Current VPN must acknowledge native/TUN cleanup before switching; a NEW profile preserves the old endpoint/credentials and existing app routing policy, with auto-gateway/auth/boot off. Password-required proxies lead to settings, never an unauthenticated automatic start. No automatic connection to discovered devices.
- No scan history, credentials, cloud uploads or permanent LAN server. Treat LAN scan results as untrusted; choose only a proxy you recognise.

## QA

Unit coverage: subnet/prefix bounds, prioritisation, actual protocol replies, auth detection without passwords, ordinary HTTP non-proxy rejection, cancellation; gateway recovery state transitions.
Android coverage: profile preservation and routing, unknown-result rejection, DPAD result/confirmation, running/cancel UI, auth settings UI. Real multi-device Wi-Fi/client isolation and unplug/replug on Rombica remain physical-device checks before release.
