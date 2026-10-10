# Android TV / proxy review

Baseline: `main` at `2751dc1332ebef21d42dd4fedc1c7138fe5a772d`.
Scope: static review of the Compose settings screen, profile persistence, manifest,
VPN service, network gateway helper, Rust transport and build workflow.
This is not an exhaustive security audit or a device-tested certification.

## Assessment

A promising prototype with a modern Kotlin/Compose UI and a Rust VPN transport.
There are host proxy integration tests and Rust tests, but the original JVM suite
only checks `2 + 2`, lint is non-blocking, and several legacy settings are not wired
to the current VPN implementation. Not yet production-ready for unattended TV use.

## Fixed in this change

- TV launcher discoverability: add `LEANBACK_LAUNCHER`, an application banner,
  optional Leanback support and an explicit non-required touchscreen. The same
  APK continues to support phone launchers and the existing four native ABIs.
- Place auto-gateway immediately after the connection card, before profiles/proxy.
- Use a focusable settings button for host, port, credentials, domain and SSIDs.
  Only click/DPAD OK opens a draft editor and the keyboard. OK commits; Cancel or
  Back discards the draft. Passwords stay masked. Port entry rejects invalid input
  instead of filtering/truncating it into a different number.
- Default port `10808` for new/reset profiles and absent preference/JSON values.
  Existing explicit ports (including invalid zero) are preserved; service startup
  now rejects blank hosts and ports outside `1..65535` with a visible error.
- Per-app allow-list previously followed `addDisallowedApplication(packageName)`.
  Android forbids mixing allow/disallow lists: selective routing could fail. Use
  one mode only; exclude the app from the allow-list, and fail closed if none of
  the selected packages exists rather than silently routing all apps.
- Disable upstream credentials when Authentication is off. Previously the switch
  did not prevent saved credentials reaching native code.
- Resolve IPv4 gateways only on physical Internet-capable networks, preferring
  the default physical network and ignoring VPN/unspecified/IPv6 gateways.
  Previously activeNetwork could refer to the VPN after connection.
- Pass the `specialUse` foreground type only on Android 14+ where it exists;
  use the two-argument foreground API on older Android versions.
- Run the existing Android workflow on pull requests to main/master as well as
  supported pushes, and add the static TV contract check to the build job.

## Open findings / limitations

| Priority | Finding | Evidence / next action |
| --- | --- | --- |
| High | Startup/stop/restart race and resource lifetime | `ProxyDroidVpnService`: cancelling `vpnJob` is not joined; `stopVpn` can race a queued `startVpn`; repeated starts can replace the interface/helper without stopping the previous instance. Serialize start/stop/restart and test rapid toggles on a device. |
| High | Background autostart can fail | `ProxyController.startWithConsent` calls `startService` from boot/network receivers on modern Android; requesting consent also launches an activity from the background. Rework within OS background/FGS restrictions, test reboot and granted/ungranted consent. |
| Medium | Profile persistence/list bookkeeping | `MainViewModel`: concurrent IO updates save under the current ID at execution time; rename writes `name` but list labels read `profile$id`; delete does not remove IDs from `profileValues`. Serialize persistence and keep ID/name/delete bookkeeping consistent. |
| Medium | UI options not implemented by VPN | PAC, NTLM, bypass addresses and DNS-proxy switch are serialized but not consumed by the current service/native bridge. Hide/disable unsupported controls or implement their semantics; currently a user can believe these controls work. |
| Medium | TCP liveness is not tunnel health | `NetworkUtils.probeProxy` opens only a TCP socket; green status cannot prove proxy authentication or forwarding. Add protocol-aware health checks. |
| Limitation | General UDP unavailable | Rust `tun2socks.rs` disables UDP in the stack, except its dedicated DNS interception path. QUIC and other non-DNS UDP traffic may fail; document this or implement supported UDP forwarding. |
| Quality | Weak mandatory checks/toolchain mismatch | Lint is ignored in Gradle and CI; emulator job is allowed to fail. AGP 8.1.2 is pinned while compile/target SDK is 36. Validate a supported upgrade separately with the Rust/native packaging regression tests. |

## Verification

- `python3 scripts/check_tv_contract.py`: passed (manifest categories/features,
  banner XML, settings order, seven click-to-edit controls, default port).
- `git diff --check`: passed.
- Isolated Kotlin/JVM run on JDK 17: all five profile/default-port and port-input
  tests passed against the actual source files (outside the Android build).
- Three gateway tests compiled in the isolated harness but could not execute:
  plain `android-all` requires Android runtime initialization and Mockito cannot
  initialize ConnectivityManager there. A mockable-jar conversion also failed;
  the gateway assertions remain unverified pending normal Android Gradle CI.
- Added JVM tests for profile defaults/custom-port preservation, port boundaries
  and physical gateway selection, including Ethernet and an active VPN.
- Added Compose instrumented tests for focus vs DPAD OK, invalid port rejection,
  cancelling drafts and saving valid edits.
- Android compilation/JVM/instrumented execution still requires the repository's
  JDK 17, Android SDK 36, NDK 25.1.8937393, CMake 3.22.1 and Android Rust targets.
  The local host initially has Java 25 (unsupported by Gradle 8.4), no Android SDK
  or Rust toolchain. The JDK 17 retry reached project configuration but failed
  because the Android SDK and NDK are absent. Android tests must be confirmed
  by CI and a physical TV before calling this device-tested.

## GitHub Actions follow-up

The initial limitation was local only. GitHub Actions run `38008256912` built the
debug APK and passed the unit-test job for commit `372b836bde362b0e4d82c79640e842c65be8473f`.
The SDK, NDK and Rust toolchain were available there. The gateway JVM tests therefore
passed in the normal Android Gradle environment. Emulator execution was still queued
at that snapshot. Later signing/build changes need their own run. See
`docs/RELEASE_SIGNING.md` for the permanent release signing setup.

## TV installation and device checklist

Install the regular APK through the package installer or `adb install -r`.
Do not place it in `/system/app` or `/system/priv-app`. A manifest cannot change
an existing system installation into a user installation; manufacturer launcher
filtering may also differ from standard Android TV behavior.

1. Fresh install: app visible in Android TV Apps and standard Android app lists.
2. Navigate with DPAD: focus alone never shows the keyboard; OK opens an editor.
3. Cancel/Back leaves settings unchanged; OK saves valid values; password masked.
4. New profile uses 10808; existing custom port survives updating the APK.
5. Wi-Fi hotspot and Ethernet: auto-gateway is the second card and still resolves
   the physical gateway after VPN connection. No usable gateway reports an error
   or uses the existing configured host fallback.
6. Test selected-app routing, removed selected packages, authentication off/on,
   rapid connect/stop, reboot, and each supported Android version/ABI.
7. Run `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`, then
   device tests using `./gradlew connectedDebugAndroidTest` on a suitable device.

## v3.7.3: TV controls and in-app updates

- Initial DPAD focus is the connection switch. Main screen order: connection,
  auto gateway, Host/Port, profile, other settings. Editor dialogs still open
  only on click/DPAD OK, never on focus.
- Enabling auto gateway performs a fresh lookup of the physical network's
  IPv4 default gateway and stores it in the selected profile's Host. Stale
  lookups are ignored after changing profile or disabling the option. No
  gateway produces a visible error rather than overwriting Host with blank.
- Menu → Update app offers separate Stable and Test / pre-release channels.
  It selects only published signed ProxyDroid releases with build metadata.
  ARM32-capable devices prefer armeabi-v7a; other devices use universal.
- Downloads stay in private cache. Before allowing installation, the updater
  verifies declared byte count, SHA-256, actual package/version/minSDK/native
  ABIs and the certificate against the currently installed app. Downgrades
  and foreign signing certificates are refused. No signing secrets are in APKs.
- Android asks the user to allow installs from this app, where required, and
  confirms installation. After granting permission, return and choose Install
  again. No silent installation. Cancellation stops downloads and cleans the
  pending file. GitHub/network restrictions are shown as an error.
- Release workflow publishes universal and compact armeabi-v7a APKs, each
  with its own checksum, signing report and build metadata. Both use the same
  versionCode and permanent signing certificate. Obsolete `armeabi` is not
  produced: ARM32 target is `armeabi-v7a`.
- Stable shows no candidate until a compatible signed non-prerelease release
  is published. Existing unsigned/debug releases are deliberately ignored.


### Instrumented regression gate

The connection focus request explicitly enters keyboard/DPAD input mode before
requesting focus. Dialog editor focus is requested inside the dialog text slot,
after the editor has been attached, not from the outer subcomposition.
The emulator suite starts local authenticated/unauthenticated SOCKS5 and HTTP
CONNECT proxies plus a local HTTP target. It is now blocking: focus/editor or
protocol regressions cannot be silently treated as a green build. Tagged release
publication waits for a successful Android workflow for the exact source commit.
