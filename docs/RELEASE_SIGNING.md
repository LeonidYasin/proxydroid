# Signed APK release

Both `release-apk.yml` and the `publish-rc` testing path now build a real release
variant with the same persistent release signer. There is no unsigned/debug fallback.
A manual Release APK run without a tag builds verified downloadable artifacts only.
An existing matching version tag can publish a GitHub release; existing assets are
never silently overwritten. A signed PR test build is an artifact, not a rolling
release that replaces somebody else's test binary.

## One-time repository configuration

Use repository Settings → Secrets and variables → Actions. Never commit the
keystore, passwords or a Base64 representation of the keystore.

Repository secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64 of the existing permanent release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: store password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: private key password.

Repository variable (public certificate identifier, not a password):

- `ANDROID_SIGNING_CERT_SHA256`: SHA-256 fingerprint of the signing certificate,
  obtained locally with `keytool -list -v`. Colons/spaces are accepted. It can
  alternatively be stored as a secret with the same name.

Use the SAME private key for every future release and back it up offline. Do not
regenerate it per workflow run. Existing release-signed installations can upgrade
only if the certificate matches and versionCode increases.

If no permanent release key exists, the owner must first create and back up one
locally (e.g. RSA 4096, SHA256withRSA, validity 10000 days, with passwords entered
at keytool's interactive prompts), then configure the above values privately.
Do not use the Android debug certificate for a release identity.

## Existing installation migration

The previously published `v3.7.1-rc/app-debug.apk` was downloaded and verified:

- Signature scheme v2: verified.
- Certificate subject: `C=US, O=Android, CN=Android Debug`.
- Certificate SHA-256: `234e874643cc6479ae63aa576337d69b3ca16ade76557e34b5746cbfae0af8b7`.

That is a valid debug signature, not a production signing identity. Moving to a
new release certificate cannot silently update this installation. Export needed
settings first, uninstall the debug APK, then install the first release APK.
This may erase app data and requires re-granting VPN consent. Future release
updates must keep the permanent release certificate. Never uninstall anything
as part of an automated build.

Version 3.7.2 has versionCode 30702, above main's 3.7.0 and the published 3.7.1 RC.

## What the workflow verifies

1. All signing secrets and expected public certificate fingerprint exist.
2. The optional publication tag matches version.properties and the checkout commit.
3. Release unit tests pass and `assembleRelease` succeeds.
4. `apksigner verify --verbose --print-certs` succeeds; the certificate matches the
   configured SHA-256 and is not the default Android Debug identity.
5. APK is non-debuggable and passes `zipalign -c -v 4`.
6. APK, SHA-256 checksum, certificate report and source commit are uploaded together.
7. Temporary keystore is removed even if the build fails. It is not uploaded or cached.

Signed builds never run automatically on arbitrary fork PRs. The same-repository
`publish-rc` label is an explicit maintainer opt-in; normal PR/debug validation does
not receive signing credentials. Prefer protected tags/environments and trusted
maintainer review before running any branch with repository signing secrets.

This verifies APK signing and packaging, not Google Play eligibility or 16 KB
native page-size compatibility. Validate native binaries/toolchain and device
behavior separately if distributing to devices with 16 KB pages.


## ProxyDroid signing identity created for this project

A separate RSA-4096 / SHA256withRSA PKCS12 identity was created, not reused from
TunXBox. The public certificate fingerprint is pinned in `signing-certificate.sha256`:
`0749fd8dc3df58d0b268d95f7bd43110e8314fd07ad61edb5672ee1406977657`.
The CI fingerprint must match this reviewed pin before any signing build.

## Encrypted offline backup — separate owner-authorized operation

**A signed APK request is not permission to export the private signing key.**
No backup/export is performed as part of a build or release. Do not dispatch
`mode=backup` without separate explicit owner approval of the destination and
handling of private key material; an encrypted Actions artifact is still key
material disclosure. Prefer an offline owner-controlled backup and retain the
same signing identity. Lost private key cannot be reconstructed from its public
certificate; generating another key does not update existing signed installs.

The following is technical recovery documentation, not authorization to run it.

## Backup mechanics (only with separate approval)

Set a strong, unique 20-256 character password without line breaks in repository
secret `PROXYDROID_BACKUP_PASSWORD`. Save this password separately in a password
manager; GitHub cannot reveal a saved Actions Secret later.

Run **Release APK** with `mode=backup` on the configured branch. It verifies the
keystore certificate against the public pin, creates a private tar in memory,
encrypts it with GPG AES256 and a salted/iterated SHA512 S2K, and verifies a full
decrypt roundtrip before uploading **only** encrypted output. The artifact
`ProxyDroid-encrypted-signing-backup` is retained for 7 days: download and store it
offline in at least two safe places. Its password is not the signing key password.

On your trusted computer with GPG installed:

```bash
sha256sum -c SHA256SUMS.txt
gpg --output ProxyDroid-signing-backup.tar --decrypt ProxyDroid-signing-backup.tar.gpg
mkdir -m 700 restored-proxydroid-signing
tar -xf ProxyDroid-signing-backup.tar -C restored-proxydroid-signing
chmod 600 restored-proxydroid-signing/*
```

GPG prompts for the backup password. The restored `release.keystore` and
`github-secrets.json` are private signing credentials: never upload them to chat,
GitHub source, issues or release assets. Verify the restored certificate fingerprint
before restoring the same signing identity to Actions Secrets. The checksum detects
accidental file corruption; preserve the authenticated encrypted backup and password.


## APK / workflow / release naming contract

All exported APKs and Actions artifact names are generated by
`scripts/build_metadata.py` from the actual checkout and GitHub run metadata:

`proxydroid-v<VERSION>-android-tv-phone-universal-release-signed-<CHANNEL>-build-<RUN_ID>-a<ATTEMPT>-g<SHA12>.apk`

The channel is `stable`, `prerelease` or `ci`, so a PR build cannot be mistaken for
a stable release. Debug packages use `debug-ci` instead of `release-signed-<CHANNEL>`. The APK basename, asset name in Releases and
Actions artifact basename match exactly; there is no different display-name alias.
Renaming a byte-identical APK does not alter its cryptographic signature.

The publisher verifies APK applicationId, versionName, versionCode and the four
native ABI libraries before claiming a universal platform build. Adjacent checksum,
signing report and JSON/text metadata files have the same build-specific prefix.
Metadata contains the full commit SHA, run ID/number/attempt, workflow URL, UTC build
time, min/target SDK, actual ABI set, APK filename/size and checksum.
The installed app displays the version, build type, run ID/attempt and short commit.

GitHub release titles include platform, version, variant, run ID, attempt and SHA.
Release notes contain the complete build identity. Releases are created as drafts
and published only after all named assets upload; existing published releases are
not overwritten. Unmerged PR builds are published only as explicitly marked
pre-releases, not represented as final stable releases.


## 3.8 release / validation details

Exports: universal (arm64-v8a, armeabi-v7a, x86, x86_64) and compact
armeabi-v7a, with identical versionCode, source commit and signing certificate.
Each has its own artifact basename and `.apk.sha256`, `.signing.txt`,
`.build-info.json` / `.build-info.txt` assets. Real local outputs are
`app-universal-release.apk` and `app-armeabi-v7a-release.apk` before CI renaming.

Names include version, Android TV/Phone, ABI, release-signed, channel, run ID,
attempt and SHA12. Never equate the compressed artifact ZIP size with APK bytes.
Tag: `vVERSION-rc.gSHA12` (Test) or `vVERSION` (Stable). Version comes only from
version.properties. Reusing an existing released tag/binary is refused.

Tagged publication waits for successful Android workflow on the exact source
SHA, including blocking emulator tests. Manual untagged / PR-label calls produce
signed artifacts; they do not imply a published stable or successful device QA.
After merge, auto-tag workflow explicitly dispatches release because GitHub's
GITHUB_TOKEN tag push does not recursively trigger another workflow.

For build commands and prerequisites see README. For install-time checks and
unknown-source permission see docs/INTERNET_YOUTUBE.md and README Updates.
