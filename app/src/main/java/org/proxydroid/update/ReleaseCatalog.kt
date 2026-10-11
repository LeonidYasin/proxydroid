package org.proxydroid.update

import java.net.URI
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser

enum class UpdateChannel(val label: String) { STABLE("Stable"), TEST("Test / pre-release") }

data class ReleaseAsset(val name: String, val url: String, val size: Long, val digest: String?)
data class UpdateOffer(
    val tag: String,
    val channel: UpdateChannel,
    val title: String,
    val publishedAt: String,
    val apk: ReleaseAsset,
    val metadata: ReleaseAsset,
)
data class UpdateBuild(
    val offer: UpdateOffer,
    val version: String,
    val versionCode: Long,
    val runId: String,
    val commit: String,
    val abi: String,
    val minSdk: Int,
    val sha256: String,
)

object ReleaseCatalog {
    private val apkName = Regex("proxydroid-v[0-9]+\\.[0-9]+\\.[0-9]+-android-tv-phone-(universal|armeabi-v7a)-release-signed-(stable|prerelease)-build-[0-9]+-a[0-9]+-g[0-9a-f]{12}\\.apk")
    private val hash = Regex("[0-9a-f]{64}")

    fun trustedAssetUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null &&
            uri.port == -1 && uri.path.startsWith("/LeonidYasin/proxydroid/releases/download/")
    }.getOrDefault(false)

    fun offers(json: String, channel: UpdateChannel, supportedAbis: List<String>): List<UpdateOffer> {
        val releases = JSONParser().parse(json) as? JSONArray ?: error("Invalid GitHub release list")
        return releases.mapNotNull { raw ->
            val release = raw as? JSONObject ?: return@mapNotNull null
            if (release["draft"] != false || release["prerelease"] != (channel == UpdateChannel.TEST)) return@mapNotNull null
            val assets = (release["assets"] as? JSONArray).orEmpty().mapNotNull { entry ->
                val asset = entry as? JSONObject ?: return@mapNotNull null
                val name = asset["name"] as? String ?: return@mapNotNull null
                val url = asset["browser_download_url"] as? String ?: return@mapNotNull null
                if (!trustedAssetUrl(url) || asset["state"] != "uploaded") return@mapNotNull null
                ReleaseAsset(name, url, (asset["size"] as? Number)?.toLong() ?: 0L,
                    (asset["digest"] as? String)?.removePrefix("sha256:")?.takeIf { hash.matches(it) })
            }
            val channelName = if (channel == UpdateChannel.TEST) "prerelease" else "stable"
            val compatible = assets.filter { it.name.contains("-release-signed-$channelName-build-") && apkName.matches(it.name) && it.size in 1..200L * 1024 * 1024 }
            val apk = if ("armeabi-v7a" in supportedAbis) {
                compatible.firstOrNull { "-armeabi-v7a-" in it.name }
                    ?: compatible.firstOrNull { "-universal-" in it.name }
            } else compatible.firstOrNull { "-universal-" in it.name }
            if (apk == null || supportedAbis.none { it in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }) return@mapNotNull null
            val metadata = assets.firstOrNull { it.name == apk.name.removeSuffix(".apk") + ".build-info.json" }
                ?: return@mapNotNull null
            UpdateOffer(release["tag_name"] as? String ?: return@mapNotNull null, channel,
                release["name"] as? String ?: "ProxyDroid", release["published_at"] as? String ?: "", apk, metadata)
        }.sortedByDescending { it.publishedAt }
    }

    fun readBuild(offer: UpdateOffer, json: String, supportedAbis: List<String>, sdk: Int, installedCode: Long): UpdateBuild {
        val data = JSONParser().parse(json) as? JSONObject ?: error("Invalid build metadata")
        require(data["application_id"] == "org.proxydroid" && data["apk_filename"] == offer.apk.name) { "Update metadata does not match this application/APK" }
        require(data["variant"] == "release" && data["signing"] == "release") { "Not a signed release build" }
        require(data["release_channel"] == if (offer.channel == UpdateChannel.TEST) "prerelease" else "stable") { "Update channel metadata mismatch" }
        val code = (data["version_code"] as? Number)?.toLong() ?: error("Missing version code")
        require(code >= installedCode) { "This channel's latest version is older than the installed app; downgrade is not allowed" }
        val minSdk = (data["min_sdk"] as? Number)?.toInt() ?: error("Missing minimum Android version")
        require(minSdk <= sdk) { "This update requires a newer Android version" }
        val abi = data["abi_variant"] as? String ?: error("Missing ABI metadata")
        require((abi == "universal" || abi == "armeabi-v7a") && offer.apk.name.contains("-$abi-release-signed-")) { "APK filename/ABI metadata mismatch" }
        require(abi == "universal" || abi in supportedAbis) { "This APK is incompatible with the device CPU" }
        val abis = (data["verified_native_abis"] as? JSONArray).orEmpty().filterIsInstance<String>()
        require(abis.any { it in supportedAbis }) { "No compatible native library in update metadata" }
        val digest = data["apk_sha256"] as? String ?: error("Missing APK checksum")
        require(hash.matches(digest) && (offer.apk.digest == null || offer.apk.digest == digest)) { "Invalid or conflicting APK checksum" }
        require((data["apk_bytes"] as? Number)?.toLong() == offer.apk.size) { "APK size differs from release metadata" }
        val commit = data["commit"] as? String ?: error("Missing source commit")
        require(Regex("[0-9a-f]{40}").matches(commit)) { "Invalid source commit" }
        return UpdateBuild(offer, data["version"] as? String ?: error("Missing version"), code,
            data["run_id"] as? String ?: "", commit, abi, minSdk, digest)
    }
}
