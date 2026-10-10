package org.proxydroid.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.proxydroid.BuildConfig

data class VerifiedDownload(val file: File, val signatures: SignatureReport)

/** No API token, silent installs, world-readable downloads or signing key in APK. */
class GitHubUpdater(private val context: Context) {
    companion object { private val downloadLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>() }
    private val allowedHosts = setOf("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")

    private fun connect(url: String, offset: Long = 0, download: Boolean = false): HttpURLConnection {
        var next = URL(url)
        repeat(6) {
            require(next.protocol == "https" && next.host in allowedHosts && next.userInfo == null) { "Untrusted update download URL" }
            val connection = next.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = if (download) 90_000 else 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "ProxyDroid/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (download && offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            if (next.host == "api.github.com") connection.setRequestProperty("Accept", "application/vnd.github+json")
            val status = try { connection.responseCode } catch (failure: Exception) { connection.disconnect(); throw failure }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank()) { "Invalid GitHub redirect" }
                next = URL(next, location)
            } else {
                if (status != 200 && !(download && status in listOf(206, 408, 429, 500, 502, 503, 504))) {
                    connection.disconnect()
                    error(if (status == 403 || status == 429) "GitHub rate limit or access restriction. Please try later." else "GitHub returned HTTP $status")
                }
                return connection
            }
        }
        error("Too many GitHub redirects")
    }

    private suspend fun text(url: String, limit: Int): String {
        val connection = connect(url)
        try {
            connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= limit) { "GitHub response exceeds the safe size limit" }
                    output.write(buffer, 0, n)
                }
                return output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }

    suspend fun check(channel: UpdateChannel): UpdateBuild {
        val catalog = text("https://api.github.com/repos/LeonidYasin/proxydroid/releases?per_page=100", 4 * 1024 * 1024)
        val abis = Build.SUPPORTED_ABIS.toList()
        val offer = ReleaseCatalog.offers(catalog, channel, abis).firstOrNull()
            ?: error("No compatible signed ${channel.label.lowercase()} release is published yet")
        val metadata = text(offer.metadata.url, 64 * 1024)
        return ReleaseCatalog.readBuild(offer, metadata, abis, Build.VERSION.SDK_INT, BuildConfig.VERSION_CODE.toLong())
    }

    fun partialFile(build: UpdateBuild): File = File(File(context.cacheDir, "update-downloads"), "partial-${build.sha256}.apk")

    suspend fun download(build: UpdateBuild, progress: (DownloadProgress) -> Unit): VerifiedDownload = downloadLocks.getOrPut(build.sha256) { Mutex() }.withLock {
        val dir = File(context.cacheDir, "updates").apply { kotlin.check(mkdirs() || isDirectory) }
        val partial = partialFile(build) // Outside the provider root; suffix remains .apk for Android parser.
        val downloaded = ResumableDownload({ url, offset -> connect(url, offset, download = true) })
            .download(build.offer.apk.url, build.offer.apk.size, build.sha256, partial, progress)
        currentCoroutineContext().ensureActive()
        val signatures = validatePackage(downloaded, build)
        currentCoroutineContext().ensureActive()
        val ready = File(dir, build.offer.apk.name)
        kotlin.check(downloaded.renameTo(ready)) { "Cannot move the verified APK into installer cache; saved complete bytes retained" }
        VerifiedDownload(ready, signatures)
    }

    @Suppress("DEPRECATION")
    fun installedSignatures(): Set<String> {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNATURES
        return signers(context.packageManager.getPackageInfo(context.packageName, flags))
    }

    @Suppress("DEPRECATION")
    private fun validatePackage(file: File, build: UpdateBuild): SignatureReport {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Downloaded file is not a valid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && version == build.versionCode && archive.versionName == build.version) { "Real APK package/version does not match the announced update" }
        require(archive.applicationInfo?.minSdkVersion?.let { it <= Build.VERSION.SDK_INT } == true) { "APK requires a newer Android version" }
        val expectedAbis = if (build.abi == "universal") setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") else setOf(build.abi)
        val nativeAbis = java.util.zip.ZipFile(file).use { zip ->
            zip.entries().asSequence().map { it.name }
                .filter { Regex("lib/[^/]+/libproxydroid_tun2socks\\.so").matches(it) }
                .map { it.split('/')[1] }.toSet()
        }
        require(nativeAbis == expectedAbis && nativeAbis.any { it in Build.SUPPORTED_ABIS }) { "Real APK native ABI differs from update metadata" }
        val report = SignatureReport(signers(installed), signers(archive))
        if (report.failure != null) throw SignatureValidationException(report)
        return report
    }

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val modern = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else null
        // OEM PackageManager may expose legacy signatures but omit signingInfo.
        // Still compare real certificate bytes; missing both is an explicit error.
        val signatures = modern?.takeIf { it.isNotEmpty() } ?: info.signatures
        return signatures.orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    }

    /** Re-check the cached bytes immediately before each installer attempt. */
    fun verifyReady(file: File, build: UpdateBuild) {
        require(file.isFile && file.canonicalFile.parentFile == File(context.cacheDir, "updates").canonicalFile) { "Downloaded APK is missing. Download it again." }
        require(file.length() == build.offer.apk.size) { "Cached APK size changed. Download it again." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(32768)
            while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        }
        require(hex(digest.digest()) == build.sha256) { "Cached APK checksum changed. Installation refused." }
        validatePackage(file, build)
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Use the visible Activity, not Application + NEW_TASK (restricted on TV/OEM). */
    fun install(activity: android.app.Activity, file: File): Boolean {
        if (!canInstall()) {
            try {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            } catch (missing: android.content.ActivityNotFoundException) {
                activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update-files", file)
        try {
            activity.startActivity(ApkInstallIntents.create(uri, Intent.ACTION_INSTALL_PACKAGE))
        } catch (missing: android.content.ActivityNotFoundException) {
            // Some TV installers only expose the generic APK VIEW handler.
            activity.startActivity(ApkInstallIntents.create(uri, Intent.ACTION_VIEW))
        }
        return true
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}
