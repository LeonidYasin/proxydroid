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
