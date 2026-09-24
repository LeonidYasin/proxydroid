# Auto-gateway feature

Adds an option to use the current network's default gateway IP as the upstream proxy host.

## Motivation

When a phone shares its VPN/proxy connection over a Wi-Fi hotspot, the hotspot gateway IP (the phone itself, e.g. `192.168.43.1`) is where the local proxy is listening. Because Android hotspot gateways can change between sessions, entering the IP manually on every client is tedious.

## What this feature does

Adds a `useGatewayAsHost` flag to `Profile`. When enabled, the VPN service resolves the current default gateway via `ConnectivityManager.getLinkProperties().routes` and uses it as the upstream proxy host, instead of the manually-configured one.

## Files added in this branch

- `app/src/main/java/org/proxydroid/android/utils/NetworkUtils.kt` - `getGatewayIp(context)` helper (works on API 24+).
- `.github/workflows/build-auto-gateway.yml` - CI that builds a debug APK on every push/PR.

## Follow-up changes still needed (in the same branch, applied as a second commit)

1. `Profile.kt` - add `var useGatewayAsHost: Boolean = false`.
2. `ProxyDroidVpnService.kt` - before building tun2socks args, compute:
   ```kotlin
   val effectiveHost = if (profile.useGatewayAsHost)
       getGatewayIp(this) ?: profile.host else profile.host
   ```
   and pass `effectiveHost` where `profile.host` is used.
3. `ui/ProfileActivity.kt` - add a Switch bound to `profile.useGatewayAsHost`; disable the Host field when checked.

## Why those three are not in this commit

The Kotlin files use a Compose + generated-bindings layout that requires reading the current content of each file to produce a safe patch. The MCP transport was returning HTTP 404 for those paths during the automated session, so the patch is being applied in a follow-up commit once paths/content are confirmed.
