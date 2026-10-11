package org.proxydroid.update

/** Missing parser output is not a different certificate and never authorizes an update. */
data class SignatureReport(val installed: Set<String>, val downloaded: Set<String> = emptySet()) {
    val failure: String? get() = when {
        installed.isEmpty() -> "Android could not read the installed signing certificate. Update refused; no signature check bypass."
        downloaded.isEmpty() -> "Android could not read the downloaded APK signing certificate. Update refused; no signature check bypass."
        installed != downloaded -> "Downloaded APK signing certificate differs from the installed app. Update refused. A debug/upstream/other-key installation cannot be updated with this release key. Save settings before considering manual reinstall; do not delete the app automatically."
        else -> null
    }
}
class SignatureValidationException(val report: SignatureReport) : IllegalArgumentException(report.failure)
