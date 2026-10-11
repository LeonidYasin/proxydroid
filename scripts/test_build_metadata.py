import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from build_metadata import ABIS, build_metadata, package_apk

ROOT = Path(__file__).resolve().parents[1]
SHA = '0123456789abcdef0123456789abcdef01234567'
ENV = {'GITHUB_RUN_ID': '12345678901', 'GITHUB_RUN_NUMBER': '42',
       'GITHUB_RUN_ATTEMPT': '2', 'GITHUB_REPOSITORY': 'LeonidYasin/proxydroid'}


class NamingContractTest(unittest.TestCase):
    def test_complete_and_consistent_release_name(self):
        value = build_metadata(ROOT, 'release', ENV, SHA)
        self.assertEqual(value['artifact_name'] + '.apk', value['apk_filename'])
        for part in ['android-tv-phone', 'universal', 'release-signed',
                     'build-12345678901', 'a2', 'g0123456789ab', 'v' + value['version']]:
            self.assertIn(part, value['apk_filename'])
        self.assertEqual(value['commit'], SHA)
        self.assertIn('12345678901', value['release_title'])

    def test_stable_prerelease_and_ci_are_explicit(self):
        self.assertEqual('ci', build_metadata(ROOT, 'release', ENV, SHA)['release_channel'])
        stable = build_metadata(ROOT, 'release', dict(ENV, INPUT_TAG='v3.7.2'), SHA)
        preview = build_metadata(ROOT, 'release', dict(ENV, INPUT_TAG='v3.7.2-rc.g0123456789ab'), SHA)
        self.assertEqual('stable', stable['release_channel'])
        self.assertEqual('prerelease', preview['release_channel'])
        self.assertIn('-prerelease-', preview['apk_filename'])

    def test_debug_and_release_names_are_distinct(self):
        self.assertNotEqual(build_metadata(ROOT, 'debug', ENV, SHA)['stem'],
                            build_metadata(ROOT, 'release', ENV, SHA)['stem'])

    def test_rerun_and_new_run_names_are_distinct(self):
        first = build_metadata(ROOT, 'release', ENV, SHA)['stem']
        self.assertNotEqual(first, build_metadata(ROOT, 'release', dict(ENV, GITHUB_RUN_ATTEMPT='3'), SHA)['stem'])
        self.assertNotEqual(first, build_metadata(ROOT, 'release', dict(ENV, GITHUB_RUN_ID='12345678902'), SHA)['stem'])

    def test_rejects_invalid_identifier(self):
        with self.assertRaises(ValueError):
            build_metadata(ROOT, 'release', dict(ENV, GITHUB_RUN_ID='../bad'), SHA)

    def test_packaged_bytes_names_checksum_and_info_match(self):
        metadata = build_metadata(ROOT, 'release', ENV, SHA)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'app-release.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                for abi in ABIS:
                    archive.writestr(f'lib/{abi}/libproxydroid_tun2socks.so', b'test')
            badging = root / 'badging.txt'
            badging.write_text(f"package: name='{metadata['application_id']}' versionCode='{metadata['version_code']}' versionName='{metadata['version']}'\nsdkVersion:'{metadata['min_sdk']}'\ntargetSdkVersion:'{metadata['target_sdk']}'\n")
            result = package_apk(apk, metadata, root / 'dist', badging)
            target = root / 'dist' / metadata['apk_filename']
            self.assertEqual(apk.read_bytes(), target.read_bytes())
            self.assertEqual((root / 'dist' / (target.name + '.sha256')).read_text(),
                             result['apk_sha256'] + '  ' + target.name + '\n')
            info = json.loads((root / 'dist' / (metadata['stem'] + '.build-info.json')).read_text())
            self.assertEqual(info['apk_filename'], target.name)
            badging.write_text("package: name='wrong' versionCode='1' versionName='wrong'\n")
            with self.assertRaises(ValueError):
                package_apk(apk, metadata, root / 'wrong-dist', badging)

    def test_arm32_has_distinct_name_and_exactly_one_abi(self):
        value = build_metadata(ROOT, 'release', ENV, SHA, abi='armeabi-v7a')
        self.assertEqual(['armeabi-v7a'], value['supported_abis'])
        self.assertIn('-armeabi-v7a-', value['apk_filename'])
        self.assertNotEqual(value['apk_filename'], build_metadata(ROOT, 'release', ENV, SHA)['apk_filename'])

    def test_rejects_missing_native_abi(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / 'partial.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/libproxydroid_tun2socks.so', b'test')
            with self.assertRaises(ValueError):
                package_apk(apk, build_metadata(ROOT, 'release', ENV, SHA), root / 'dist')


if __name__ == '__main__':
    unittest.main()
