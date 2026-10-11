# Release APK size

Starting with 3.8.6, release builds enable R8 optimization and resource shrinking.
Only `src/main/runtime-assets` is packaged. Archived root-era gost/cntlm/redsocks
executables in `src/main/assets` are not used by the current Rust VPN and remain
in source history, not in the APK. The attribution page is preserved unchanged.
The unused Rhino dependency is removed. Rust keeps existing ThinLTO/stripping
and panic unwinding; no TLS checks or proxy diagnostics are removed.

JNI symbol owners are explicitly retained in proguard-rules.pro. Signed release
CI runs three dedicated Android 11 smoke tests in an isolated CI-only APK (identity/assets, launcher,
native entry points), alongside the existing debug regression workflow.
Mapping files are retained in the release-test CI artifact for retracing.
These smoke tests do not replace physical Android TV/ARM VPN data-plane testing.

ARM32 APKs omit the other three native ABIs; universal APKs remain larger because
they support ARM32, ARM64, x86 and x86_64. Both share package ID and signer.

Rust task dependencies use lazy `configureEach`, not `whenTaskAdded`, to avoid
AGP 8.1 premature task-realization/resource-input wiring (Android issue 277166577).
The existing AGP, bundled R8 and Rust plugin versions are unchanged.

The isolated smoke APK packages its own test runtime and has the same signing
identity as the non-debuggable target. It is not published. Test dependencies
and their keep rules never inflate the production APK.
