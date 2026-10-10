#!/usr/bin/env python3
"""One naming contract for Actions artifacts, real APKs and GitHub Releases."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ABIS = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64']


def build_metadata(root, variant, env=None, sha=None):
    env = os.environ if env is None else env
    props = dict(line.strip().split('=', 1) for line in
                 (root / 'version.properties').read_text().splitlines()
                 if line.strip() and not line.lstrip().startswith('#'))
    parts = [int(props['VERSION_' + x]) for x in ['MAJOR', 'MINOR', 'PATCH']]
    if parts[0] < 0 or any(not 0 <= x < 100 for x in parts[1:]):
        raise ValueError('Invalid version components')
    version = '.'.join(map(str, parts))
    sha = sha or subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    if not re.fullmatch(r'[0-9a-f]{40}', sha):
        raise ValueError('Invalid source commit SHA')
    if variant not in ['debug', 'release']:
        raise ValueError('Unknown APK variant')
    run_id = env.get('GITHUB_RUN_ID') or 'local'
    number = env.get('GITHUB_RUN_NUMBER') or 'local'
    attempt = env.get('GITHUB_RUN_ATTEMPT') or '1'
    for value in [run_id, number, attempt]:
        if value != 'local' and not re.fullmatch(r'[0-9]+', value):
            raise ValueError('Invalid workflow run identifier')
    if attempt == 'local':
        raise ValueError('Invalid workflow attempt')
    label = 'release-signed' if variant == 'release' else 'debug'
    tag = env.get('INPUT_TAG', '') or (env.get('GITHUB_REF_NAME', '') if env.get('GITHUB_REF', '').startswith('refs/tags/') else '')
    channel = ('prerelease' if '-' in tag else 'stable') if tag and variant == 'release' else 'ci'
    stem = f'proxydroid-v{version}-android-tv-phone-universal-{label}-{channel}-build-{run_id}-a{attempt}-g{sha[:12]}'
    gradle = (root / 'app/build.gradle').read_text()
    application_id = re.search(r'applicationId\s+"([^"]+)"', gradle).group(1)
    min_sdk = int(re.search(r'minSdk\s+(\d+)', gradle).group(1))
    target_sdk = int(re.search(r'targetSdk\s+(\d+)', gradle).group(1))
    title = f'ProxyDroid v{version} · Android TV/Phone · universal · {label} · {channel} · build {run_id} · attempt {attempt} · g{sha[:12]}'
    repo = env.get('GITHUB_REPOSITORY', 'LeonidYasin/proxydroid')
    server = env.get('GITHUB_SERVER_URL', 'https://github.com')
    return dict(schema_version=1, app='ProxyDroid', version=version,
                version_code=parts[0] * 10000 + parts[1] * 100 + parts[2],
                application_id=application_id, platform='Android TV / Phone',
                abi_variant='universal', supported_abis=ABIS, variant=variant,
                signing='release' if variant == 'release' else 'debug',
                release_channel=channel, release_tag=tag or None,
                min_sdk=min_sdk, target_sdk=target_sdk, commit=sha,
                short_commit=sha[:12], run_id=run_id, run_number=number,
                run_attempt=attempt, source_ref=env.get('GITHUB_REF', 'local'),
                run_url=f'{server}/{repo}/actions/runs/{run_id}' if run_id != 'local' else None,
                built_at_utc=datetime.now(timezone.utc).isoformat(),
                stem=stem, apk_filename=stem + '.apk', artifact_name=stem,
                release_title=title)


def package_apk(apk, metadata, dist, badging=None):
    with zipfile.ZipFile(apk) as archive:
        actual_abis = sorted({name.split('/')[1] for name in archive.namelist()
                             if re.fullmatch(r'lib/[^/]+/libproxydroid_tun2socks\.so', name)})
    if actual_abis != sorted(metadata['supported_abis']):
        raise ValueError('APK native ABIs do not match the universal filename/metadata')
    if badging is not None:
        package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
                            badging.read_text(), re.MULTILINE)
        if not package or tuple(package.groups()) != (
                metadata['application_id'], str(metadata['version_code']), metadata['version']):
            raise ValueError('Actual APK package/version does not match filename/metadata')
        for key, prefix in [('min_sdk', 'sdkVersion'), ('target_sdk', 'targetSdkVersion')]:
            match = re.search(r"^" + prefix + r":'(\d+)'", badging.read_text(), re.MULTILINE)
            if not match or int(match.group(1)) != metadata[key]:
                raise ValueError('Actual APK SDK level does not match metadata')
    dist.mkdir(parents=True, exist_ok=True)
    target = dist / metadata['apk_filename']
    shutil.copyfile(apk, target)  # Rename bytes only: APK signing remains untouched.
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    result = dict(metadata, apk_sha256=digest, apk_bytes=target.stat().st_size,
                  verified_native_abis=actual_abis)
    (dist / (target.name + '.sha256')).write_text(digest + '  ' + target.name + '\n')
    (dist / (metadata['stem'] + '.build-info.json')).write_text(
        json.dumps(result, indent=2, ensure_ascii=False) + '\n')
    lines = [f'{key}: {value}' for key, value in result.items()]
    (dist / (metadata['stem'] + '.build-info.txt')).write_text('\n'.join(lines) + '\n')
    notes = [f"## {result['release_title']}", '',
             f"- Platform: {result['platform']}; min SDK {result['min_sdk']}, target SDK {result['target_sdk']}",
             f"- ABI: universal ({', '.join(result['verified_native_abis'])})",
             f"- Version: {result['version']} (versionCode {result['version_code']})",
             f"- Variant: {result['variant']}; signing: {result['signing']}; channel: {result['release_channel']}",
             f"- Release tag: {result['release_tag']}",
             f"- Build: run ID {result['run_id']}, workflow build #{result['run_number']}, attempt {result['run_attempt']}",
             f"- Source commit: `{result['commit']}`", f"- Workflow: {result['run_url']}",
             f"- Built at (UTC): {result['built_at_utc']}",
             f"- APK filename: `{result['apk_filename']}`", f"- APK SHA-256: `{result['apk_sha256']}`", '',
             'First migration from old debug-signed APKs requires saving settings and reinstalling once.',
             'Future release updates use the permanent release signing identity. No signing key is included.']
    (dist / (metadata['stem'] + '.release-notes.md')).write_text('\n'.join(notes) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--variant', choices=['debug', 'release'])
    parser.add_argument('--output', type=Path, default=Path('build-info.json'))
    parser.add_argument('--github-output', type=Path)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--metadata', type=Path)
    parser.add_argument('--dist', type=Path, default=Path('dist'))
    parser.add_argument('--badging', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.apk:
        if not args.metadata:
            parser.error('--apk requires --metadata')
        result = package_apk(args.apk, json.loads(args.metadata.read_text()), args.dist, args.badging)
        print('Packaged and checked:', result['apk_filename'])
    else:
        if not args.variant:
            parser.error('--variant is required to generate metadata')
        result = build_metadata(root, args.variant)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
        if args.github_output:
            with args.github_output.open('a') as output:
                for name, value in [('name', result['stem']), ('sha', result['commit']),
                                    ('version', result['version']), ('release_title', result['release_title'])]:
                    if '\n' in value or '\r' in value:
                        raise ValueError('Invalid multiline workflow output')
                    output.write(name + '=' + value + '\n')
        print('Build naming contract:', result['apk_filename'])


if __name__ == '__main__':
    main()
