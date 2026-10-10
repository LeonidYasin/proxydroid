#!/usr/bin/env python3
"""Static regression checks; not a substitute for compilation/device tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
app = manifest.find('application')
assert app.get(ANDROID + 'banner') == '@drawable/tv_banner'
features = {x.get(ANDROID + 'name'): x.get(ANDROID + 'required') for x in manifest.findall('uses-feature')}
assert features['android.software.leanback'] == 'false'
assert features['android.hardware.touchscreen'] == 'false'
activity = next(x for x in app.findall('activity') if x.get(ANDROID + 'name') == 'ProxyDroid')
categories = {x.get(ANDROID + 'name') for x in activity.findall('intent-filter/category')}
assert 'android.intent.category.LAUNCHER' in categories
assert 'android.intent.category.LEANBACK_LAUNCHER' in categories
ET.parse(ROOT / 'app/src/main/res/drawable/tv_banner.xml')
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
assert 'refreshGateway()' in vm and 'InternetProbe.check(endpoint)' in vm
assert 'internetAvailable == true' in screen and 'testTag("open-youtube")' in screen
for path in ['doh_client.rs', 'tun2socks.rs']:
    rust = (ROOT / 'app/src/main/rust/proxydroid-tun2socks/src' / path).read_text()
    assert 'danger_accept_invalid_certs(true)' not in rust and 'struct NoVerify' not in rust
assert 'info!("DoH client: proxy={}", proxy_url)' not in (ROOT / 'app/src/main/rust/proxydroid-tun2socks/src/doh_client.rs').read_text()
print('PASS: startup gateway, consent-safe foreground boot, verified YouTube gate and TLS/logging contracts')
