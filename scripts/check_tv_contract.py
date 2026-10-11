#!/usr/bin/env python3
"""Static regression checks; not a substitute for compilation/device tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
assert app.get(ANDROID + 'banner') == '@drawable/locproxy_tv_banner'
assert app.get(ANDROID + 'icon') == '@mipmap/locproxy_launcher'
features = {x.get(ANDROID + 'name'): x.get(ANDROID + 'required') for x in manifest.findall('uses-feature')}
assert features['android.software.leanback'] == 'false'
assert features['android.hardware.touchscreen'] == 'false'
activity = next(x for x in app.findall('activity') if x.get(ANDROID + 'name') == 'ProxyDroid')
alias = next(x for x in app.findall('activity-alias') if x.get(ANDROID + 'name') == '.LocProxyTV')
assert alias.get(ANDROID + 'targetActivity') == activity.get(ANDROID + 'name')
assert alias.get(ANDROID + 'label') == 'LocProxyTV'
assert alias.get(ANDROID + 'icon') == '@mipmap/locproxy_launcher'
assert app.get(ANDROID + 'label') == 'LocProxyTV'
assert activity.get(ANDROID + 'label') == 'LocProxyTV'
assert activity.get(ANDROID + 'exported') == 'true'  # legacy favourites remain callable
assert not activity.findall('intent-filter/category')  # no duplicate launcher entry
categories = {x.get(ANDROID + 'name') for x in alias.findall('intent-filter/category')}
assert 'android.intent.category.LAUNCHER' in categories
assert 'android.intent.category.LEANBACK_LAUNCHER' in categories
ET.parse(ROOT / 'app/src/main/res/drawable/locproxy_tv_banner.xml')
screen = (ROOT / 'app/src/main/java/org/proxydroid/ui/MainScreen.kt').read_text()
body = screen[screen.index('            ConnectionCard(state'):screen.index('            AutoStartOnBootCard(')]
assert body.index('ConnectionCard(') < body.index('AutoGatewayCard(') < body.index('ProxyEndpointCard(') < body.index('ProfileCard(') < body.index('ProxyForm(')
form = screen[screen.index('private fun ProxyEndpointCard('):screen.index('private fun ToggleRow(')]
assert 'OutlinedTextField(' not in form
assert form.count('EditableSettingField(') == 7
profile = (ROOT / 'app/src/main/java/org/proxydroid/Profile.kt').read_text()
assert profile.count('companion object') == 1
assert 'const val DEFAULT_PORT = 10808' in profile
assert 'var port: Int = DEFAULT_PORT' in profile
editor = (ROOT / 'app/src/main/java/org/proxydroid/ui/EditableSettingField.kt').read_text()
assert 'OutlinedButton(' in editor and 'onFocusChanged' not in editor
assert editor.index('if (editing)') < editor.index('OutlinedTextField(')
print('PASS: TV launch/manifest, banner XML, settings order, click-to-edit structure and default port')

assert 'onChange = onGatewayChange' in screen
assert 'testTag("connection-toggle")' in screen
assert 'focusRequester.requestFocus()' in screen
vm = (ROOT / 'app/src/main/java/org/proxydroid/ui/MainViewModel.kt').read_text()
assert 'fun setUseGatewayAsHost(enabled: Boolean)' in vm and 'updateProfile { host = gateway }' in vm
assert 'android.permission.REQUEST_INSTALL_PACKAGES' in (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
paths = ET.parse(ROOT / 'app/src/main/res/xml/update_file_paths.xml').getroot()
assert paths.find('cache-path').get('path') == 'updates/'
print('PASS: endpoint order, initial connection focus, fresh gateway callback and scoped update provider')

receiver = (ROOT / 'app/src/main/java/org/proxydroid/ProxyDroidReceiver.kt').read_text()
controller = (ROOT / 'app/src/main/java/org/proxydroid/utils/ProxyController.kt').read_text()
assert 'allowConsentUi = false' in receiver and 'waitForNetwork = true' in receiver
assert 'ContextCompat.startForegroundService' in controller
assert 'refreshGateway()' in vm and 'NetworkDiagnostics.run(kind, endpoint' in vm
assert 'internetAvailable == true' in screen and 'testTag("open-youtube")' in screen
for path in ['doh_client.rs', 'tun2socks.rs']:
    rust = (ROOT / 'app/src/main/rust/proxydroid-tun2socks/src' / path).read_text()
    assert 'danger_accept_invalid_certs(true)' not in rust and 'struct NoVerify' not in rust
assert 'info!("DoH client: proxy={}", proxy_url)' not in (ROOT / 'app/src/main/rust/proxydroid-tun2socks/src/doh_client.rs').read_text()
print('PASS: startup gateway, consent-safe foreground boot, verified YouTube gate and TLS/logging contracts')

assert 'exitUntilCollapsedScrollBehavior' not in screen and 'clipToBounds()' in screen
assert 'consumeWindowInsets(padding)' in screen and 'testTag("app-top-bar")' in screen
manager = (ROOT / 'app/src/main/java/org/proxydroid/update/ResumableDownload.kt').read_text()
assert 'Content-Range' in manager and 'Mutex' in manager and 'sha256' in manager
print('PASS: pinned/clipped header, serialized resumable download and whole-file checksum')

transport = (ROOT / 'app/src/main/java/org/proxydroid/utils/ProxyTransport.kt').read_text()
diagnostics = (ROOT / 'app/src/main/java/org/proxydroid/utils/NetworkDiagnostics.kt').read_text()
device = (ROOT / 'app/src/main/java/org/proxydroid/utils/DeviceDiagnostics.kt').read_text()
ui = (ROOT / 'app/src/main/java/org/proxydroid/ui/ConnectionDiagnostics.kt').read_text()
assert 'endpointIdentificationAlgorithm = "HTTPS"' in transport and 'ssl.startHandshake()' in transport
assert 'SOCKS5_REP_$reply' in transport and 'CONNECT_HTTP_$code' in transport
assert 'withTimeout(65_000)' in diagnostics and 'sockets.close()' in diagnostics and 'Semaphore(2)' in diagnostics
assert '!it.target.direct && !it.target.doh && it.success' in diagnostics
assert 'validateDnsReply' in diagnostics and 'application/dns-message' in diagnostics
assert 'safeEndpoint' in diagnostics and 'Raw' not in diagnostics.split('fun safeEndpoint')[1].split('suspend fun run')[0]
assert 'bindSocket(socket)' in device and 'ACTUAL routing' in device
assert 'testTag("check-proxy-internet")' in ui and 'testTag("check-youtube-proxy")' in ui
assert 'diagnosticRequest' in vm and 'copy(stale = true)' in vm
print('PASS: staged/auth/DoH diagnostics, separate direct reference, actual route, cancellation and two buttons')

assert 'testTag("connection-row")' in screen and 'onCheckedChange = null' in screen
assert 'toggleable(value = on' in screen
focus = (ROOT / "app/src/main/java/org/proxydroid/ui/TvRemoteFocus.kt").read_text()
assert "reveal.bringIntoView()" in focus and "Stroke(stroke)" in focus
print("PASS: unique LocProxyTV launcher/banner, whole-row toggle and full-control focus reveal")
