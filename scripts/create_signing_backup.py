#!/usr/bin/env python3
"""Export an owner-password-encrypted backup without logging private material."""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile


def gpg(data, password, home, decrypt=False):
    reader, writer = os.pipe()
    try:
        os.write(writer, password.encode('utf-8') + b'\n')
        os.close(writer)
        writer = None
        args = ['gpg', '--batch', '--yes', '--no-symkey-cache', '--pinentry-mode',
                'loopback', '--passphrase-fd', str(reader), '--homedir', home,
                '--output', '-']
        args += ['--decrypt'] if decrypt else [
            '--symmetric', '--cipher-algo', 'AES256', '--s2k-digest-algo',
            'SHA512', '--s2k-count', '65011712']
        result = subprocess.run(args, input=data, capture_output=True, pass_fds=(reader,))
        if result.returncode:
            raise RuntimeError('Backup encryption/decryption failed; no private data logged')
        return result.stdout
    finally:
        os.close(reader)
        if writer is not None:
            os.close(writer)


def main():
    names = ['ANDROID_KEYSTORE_BASE64', 'ANDROID_KEYSTORE_PASSWORD',
             'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD']
    values = {name: os.environ.get(name, '') for name in names}
    if not all(values.values()):
        raise RuntimeError('Missing signing configuration')
    password = os.environ.get('PROXYDROID_BACKUP_PASSWORD', '')
    if not 20 <= len(password) <= 256 or '\n' in password or '\r' in password:
        raise RuntimeError('Backup password must be 20-256 characters without line breaks')
    pin = Path('signing-certificate.sha256').read_text().strip().lower()
    if len(pin) != 64 or any(c not in '0123456789abcdef' for c in pin):
        raise RuntimeError('Invalid public signing pin')
    key = base64.b64decode(values['ANDROID_KEYSTORE_BASE64'], validate=True)
    with tempfile.TemporaryDirectory(prefix='proxydroid-private-signing-') as private:
        store = Path(private) / 'release.keystore'
        store.write_bytes(key)
        store.chmod(0o600)
        cert = subprocess.run([
            'keytool', '-exportcert', '-keystore', str(store), '-alias',
            values['ANDROID_KEY_ALIAS'], '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD'],
            capture_output=True)
        if cert.returncode or hashlib.sha256(cert.stdout).hexdigest() != pin:
            raise RuntimeError('Backup keystore does not match the pinned certificate')
        # Private tar exists only in memory. All archive paths are fixed.
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode='w') as archive:
            for name, content in [
                ('release.keystore', key),
                ('github-secrets.json', json.dumps(values).encode('utf-8')),
                ('signing-certificate.sha256', (pin + '\n').encode()),
            ]:
                entry = tarfile.TarInfo(name)
                entry.size = len(content)
                entry.mode = 0o600
                archive.addfile(entry, io.BytesIO(content))
        plain = data.getvalue()
        encrypted = gpg(plain, password, private)
        if gpg(encrypted, password, private, decrypt=True) != plain:
            raise RuntimeError('Encrypted backup roundtrip check failed')
    output = Path('encrypted-signing-backup')
    output.mkdir(mode=0o700, exist_ok=True)
    target = output / 'ProxyDroid-signing-backup.tar.gpg'
    target.write_bytes(encrypted)
    target.chmod(0o600)
    (output / 'SHA256SUMS.txt').write_text(
        hashlib.sha256(encrypted).hexdigest() + '  ' + target.name + '\n')
    print('Encrypted backup created and decrypt/roundtrip verified; private values not logged.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        raise SystemExit('Signing backup failed safely. Check configuration/password/pinned certificate.')
