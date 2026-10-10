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
assert body.index('ConnectionCard(') < body.index('AutoGatewayCard(') < body.index('ProfileCard(') < body.index('ProxyForm(')
form = screen[screen.index('private fun ProxyForm('):screen.index('private fun ToggleRow(')]
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
